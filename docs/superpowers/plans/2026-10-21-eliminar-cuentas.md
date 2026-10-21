# Eliminar cuentas — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** jugadores, estudios y admins pueden pedir la baja de su cuenta, y el admin la de cualquiera. Tras 14 días de pausa la cuenta se anonimiza, como en Steam.

**Architecture:**
- Tres columnas nuevas en `users` (V13) marcan los estados «en pausa» y «eliminada».
- `AccountDeletionService` (paquete `user`) programa y cancela bajas. `AccountAnonymizer` anonimiza cada cuenta en su propia transacción, y `AccountDeletionScheduler` lo lanza a diario y al arrancar.
- El login y un endpoint nuevo `recover-account` gestionan la pausa.
- Los juegos DELISTED pasan a ser visibles para quien los tiene.

**Tech Stack:** Spring Boot 4.1 / Java 25, JPA + Flyway (H2 en tests, PostgreSQL en dev/prod), JUnit 5 + MockMvc, React 19 + Vitest + Testing Library, Postman/Newman.

**Spec:** `docs/superpowers/specs/2026-10-21-eliminar-cuentas-design.md`. Léelo entero antes de empezar: este plan no repite el porqué.

## Global Constraints

- Sin dependencias nuevas, ni en Maven ni en npm.
- **Migración V13** en `src/main/resources/db/migration/common/`, con SQL portable (H2 y PostgreSQL), al estilo de V5, V10 y V12. Ninguna otra migración usa el número 13.
- Textos del backend en `messages.properties`, `messages_en.properties` y `messages_it.properties`, con las tres en las mismas claves. Textos del frontend en `frontend/src/i18n/messages.ts` (es/en/it): el test de «mismas claves» lo exige.
- Correos en `MailTexts` (ES/EN/IT) + `EmailTemplates`. Solo texto, **sin botones de vuelta a la tienda** (commit `9ca912f`). Se envían AFTER_COMMIT con `@Async(AsyncConfig.MAIL_EXECUTOR)`.
- Configuración nueva:
  - `app.account.deletion-grace: ${ACCOUNT_DELETION_GRACE:P14D}`
  - `app.account.deletion-cron: ${ACCOUNT_DELETION_CRON:0 15 4 * * *}`
- La hora se lee siempre del bean `Clock` (`SecurityConfig.clock()`), nunca con `Instant.now()` directo.
- Bloqueos pesimistas en el orden de siempre: primero el usuario y luego lo demás. Si son varios usuarios, por id ascendente.
- Errores como `ApiException` con `code` (ProblemDetail). Claves nuevas: `user.lastAdmin` (409), `user.cannotDeleteSelf` (400), `user.deletionWrongPassword` (400), `auth.accountPendingDeletion` (403), `auth.accountDeletionByAdmin` (403), `game.developerDeleted` (409).
- **Desviación del spec, decisión de Claude:** `POST /api/users/me/deletion` devuelve **200** `{ "deletionScheduledAt": "<ISO-8601>" }` en vez de 204, para que el frontend pueda mostrar la fecha.
- Commits en español, uno por función, **sin `Co-Authored-By`**.
- Verificación del backend: `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"; .\mvnw.cmd -q -B clean verify *> verify.log; exit $LASTEXITCODE` → exit 0, con JaCoCo ≥ 80 % de líneas.
- Verificación del frontend: `cd frontend; npx vitest run; npx tsc -b; npm run build`.

## Review Focus

1. **Un email o usuario liberado se registra de nuevo**: tiene que crear una cuenta nueva y limpia. La lógica D54, que sustituye cuentas sin verificar, no puede tocar la fila anonimizada (la fila `deleted_*` tiene `email_verified` = true). Test en la Task 5.
2. **Una cuenta pausada con un access token vigente** recibe 401 al instante (`AccessTokenRevocations`), no a los 15 minutos. Test en la Task 3.
3. **Dos admins que se eliminan el uno al otro a la vez**: uno de los dos recibe 409 `user.lastAdmin` y queda al menos uno. Test de concurrencia en la Task 2, al estilo de `EmailVerificationConcurrencyTest`.
4. **Un juego en el carrito o la wishlist de otro jugador cuando el estudio se elimina**: el checkout falla con el error de siempre de juego no disponible, no con un 500. Test en la Task 5.
5. **Login con el usuario o el email antiguos de una cuenta ya eliminada**: da 401 `error.badCredentials`, no 403. Así no se revela que existió. Test en la Task 4.

---

### Task 1: V13 y estado de la cuenta en `User`

**Files:**
- Create: `src/main/resources/db/migration/common/V13__account_deletion.sql`
- Create: `src/main/java/com/capstone/steam/user/DeletionRequester.java`
- Create: `src/main/java/com/capstone/steam/user/AccountDeletionProperties.java`
- Modify: `src/main/java/com/capstone/steam/user/User.java`, `user/dto/UserResponse.java`, `src/main/resources/application.yml`, `.env.example`
- Test: `src/test/java/com/capstone/steam/FlywayMigrationTest.java`

**Interfaces:**
- Produces:
  - `enum DeletionRequester { SELF, ADMIN }`.
  - En `User`:
    - `Instant getDeletionScheduledAt()`, `DeletionRequester getDeletionRequestedBy()`, `Instant getDeletedAt()`;
    - `void scheduleDeletion(Instant at, DeletionRequester by)`, `void cancelDeletion()`;
    - `boolean isPendingDeletion()` (fecha programada y no eliminada) y `boolean isDeleted()`;
    - `void anonymize(String unusablePasswordHash, Instant now)`: aplica todos los campos de «Anonimizar» del spec sobre la propia fila (`deleted_<id>`, `deleted_<id>@deleted.invalid`, NULLs, `enabled=false`, `profilePublic=false`, `deletedAt=now` y limpia la baja programada).
  - `record AccountDeletionProperties(Duration deletionGrace, String deletionCron)` con `@ConfigurationProperties("app.account")`, igual que `EmailVerificationProperties`.
  - `UserResponse` añade `Instant deletionScheduledAt` y `DeletionRequester deletionRequestedBy`.

- [ ] **Step 1: test que falla.** En `FlywayMigrationTest`, `v13AddsAccountDeletionColumns`: tras migrar existen `users.deletion_scheduled_at`, `users.deletion_requested_by` y `users.deleted_at`, todas NULL en las filas existentes. Además, un `UPDATE` con `deletion_requested_by = 'OTHER'` falla por el CHECK.
- [ ] **Step 2:** `.\mvnw.cmd -q -B test -Dtest=FlywayMigrationTest` → FAIL.
- [ ] **Step 3: migración.**
  - Columnas `deletion_scheduled_at TIMESTAMP WITH TIME ZONE`, `deletion_requested_by VARCHAR(10)` con `CONSTRAINT ck_users_deletion_requested_by CHECK (deletion_requested_by IN ('SELF','ADMIN'))`, y `deleted_at TIMESTAMP WITH TIME ZONE`.
  - `CREATE INDEX idx_users_deletion_scheduled_at ON users (deletion_scheduled_at)`.
  - Después, los campos y métodos de `User` (`@Enumerated(EnumType.STRING)` para el requester), los dos campos de `UserResponse` y la configuración en `application.yml` y `.env.example`.
- [ ] **Step 4:** el mismo test → PASS. Después, un test unitario corto de `User.anonymize` (en `src/test/java/com/capstone/steam/user/UserTest.java`) que comprueba cada campo.
- [ ] **Step 5: commit** «Cuentas: columnas de baja programada y anonimización (V13)».

### Task 2: Regla del último admin

**Files:**
- Create: `src/main/java/com/capstone/steam/user/AdminGuard.java`
- Modify: `user/UserRepository.java`, `user/UserService.java` (`updateRoles` y `setEnabled`), `messages*.properties`
- Test: `src/test/java/com/capstone/steam/admin/LastAdminIntegrationTest.java`

**Interfaces:**
- Produces:
  - `UserRepository.lockCountingAdmins()`: `@Lock(PESSIMISTIC_WRITE)` sobre los usuarios con rol `ROLE_ADMIN`, `enabled = true`, `deletionScheduledAt is null` y `deletedAt is null`, `order by u.id`. Devuelve `List<User>`.
  - `AdminGuard.requireAnotherAdmin(Long leavingUserId)`: bloquea los admins que cuentan y lanza `ConflictException("user.lastAdmin")` si, quitando `leavingUserId`, no queda ninguno.
- `UserService.updateRoles`: si el usuario era admin y los roles nuevos no incluyen ADMIN, llama a `requireAnotherAdmin(userId)` antes de cambiar nada. `setEnabled(false)` sobre un admin hace lo mismo. Se mantienen `user.cannotRemoveOwnAdmin` y `user.cannotDisableSelf`.

- [ ] **Step 1: tests que fallan.**
  - `removingAdminRoleFromAnotherAdminWorksWhileOneRemains`: dos admins; A le quita el rol a B → 200.
  - `concurrentMutualDisableLeavesOneAdmin`: dos admins y dos hilos. A desactiva a B mientras B desactiva a A, con `TransactionTemplate`, como en `EmailVerificationConcurrencyTest`. Exactamente un 200 y un 409 `user.lastAdmin`, y queda 1 admin que cuenta.
- [ ] **Step 2:** ejecutarlos → FAIL (el segundo, porque quedan 0 admins).
- [ ] **Step 3:** implementar `lockCountingAdmins`, `AdminGuard` y las llamadas en `UserService`, con `user.lastAdmin` en es/en/it.
  - es: «Tiene que quedar al menos un administrador activo.»
  - en: «At least one active administrator must remain.»
  - it: «Deve restare almeno un amministratore attivo.»
- [ ] **Step 4:** tests → PASS; `AdminIntegrationTest` sigue en verde.
- [ ] **Step 5: commit** «Admin: siempre queda al menos un administrador activo».

### Task 3: Programar y cancelar la baja

**Files:**
- Create: `src/main/java/com/capstone/steam/user/AccountDeletionService.java`
- Create: `user/dto/DeletionRequest.java` (`@NotBlank String password`) y `user/dto/AccountDeletionResponse.java` (`Instant deletionScheduledAt`)
- Create: `src/main/java/com/capstone/steam/notification/event/AccountDeletionScheduledEvent.java`, `AccountDeletionCancelledEvent.java`
- Modify: `user/UserController.java`, `user/AdminUserController.java`, `user/UserRepository.java` (`searchPlayers`), `friend/FriendService.java` (perfil público y solicitudes), `messages*.properties`
- Test: `src/test/java/com/capstone/steam/user/AccountDeletionIntegrationTest.java`

**Interfaces:**
- Consumes: `AdminGuard.requireAnotherAdmin`, `User.scheduleDeletion/cancelDeletion/isPendingDeletion/isDeleted`, `AccountDeletionProperties.deletionGrace()`, `RefreshTokenService.revokeAll(Long)`, `AccessTokenRevocations.revokeUser(Long)`.
- Produces:
  - `AccountDeletionService.requestOwn(Long userId, String password)` → `Instant`. Contraseña mala → `BadRequestException("user.deletionWrongPassword")`.
  - `AccountDeletionService.scheduleByAdmin(Long adminId, Long userId)` → `UserResponse`. Sobre sí mismo → `BadRequestException("user.cannotDeleteSelf")`. Cuenta ya eliminada → `NotFoundException.of("user", id)`. Ya pendiente → idempotente.
  - `AccountDeletionService.cancelByAdmin(Long userId)` → `UserResponse`, idempotente.
  - `AccountDeletionService.cancelBySelf(User user)`: lo usa la Task 4. Publica el evento de cancelación.
  - `record AccountDeletionScheduledEvent(String email, String displayName, String locale, Instant scheduledAt, DeletionRequester requestedBy)`.
  - `record AccountDeletionCancelledEvent(String email, String displayName, String locale)`.
- Endpoints:
  - `POST /api/users/me/deletion` `{password}` → 200 `AccountDeletionResponse`.
  - `DELETE /api/admin/users/{id}` → 200 `UserResponse`.
  - `POST /api/admin/users/{id}/deletion/cancel` → 200 `UserResponse`.
- Comportamiento de `requestOwn` y `scheduleByAdmin`, por orden:
  1. Bloquear la fila del usuario (`findByIdForUpdate`).
  2. Si es admin, `requireAnotherAdmin`.
  3. `scheduleDeletion(now + grace, requester)`.
  4. `revokeAll` + `revokeUser`.
  5. Publicar `AccountDeletionScheduledEvent`.
- `UserService.listUsers` (admin) deja fuera las cuentas con `deletedAt` (test `adminListHidesDeletedAccounts` en la Task 5, que es la que las crea).
- `searchPlayers` añade `and u.deletionScheduledAt is null and u.deletedAt is null`. En `FriendService`, la búsqueda del destino para solicitudes y perfiles trata como inexistentes (404) las cuentas pendientes o eliminadas.

- [ ] **Step 1: tests que fallan.**
  - `ownDeletionWithWrongPasswordIs400AndChangesNothing`.
  - `ownDeletionSchedulesInFourteenDaysAndCutsSessions`: con `Clock` fijo, la fecha es exactamente +14 días. El access token anterior da 401 en `GET /api/users/me` y el refresh token anterior da 401 (Review Focus 2).
  - `lastAdminCannotDeleteOwnAccount` → 409 `user.lastAdmin`.
  - `adminSchedulesAndCancelsDeletion`: `deletionRequestedBy` = `ADMIN` y luego null tras cancelar.
  - `adminCannotDeleteSelfFromPanel` → 400.
  - `pausedPlayerIsHiddenFromSearchAndProfile`: `GET /api/users/search` no lo devuelve y su perfil da 404.
- [ ] **Step 2:** ejecutarlos → FAIL.
- [ ] **Step 3:** implementar el servicio, los endpoints, la consulta, `FriendService` y los textos.
  - `user.cannotDeleteSelf`: es «No puedes eliminar tu propia cuenta desde el panel; hazlo desde Ajustes.»; en «You can't delete your own account from the panel; do it from Settings.»; it «Non puoi eliminare il tuo account dal pannello; fallo dalle Impostazioni.».
  - `user.deletionWrongPassword`: es «La contraseña no es correcta.»; en «The password is not correct.»; it «La password non è corretta.».
- [ ] **Step 4:** tests → PASS.
- [ ] **Step 5: commit** «Cuentas: pedir y cancelar la baja (propia y por el admin)».

### Task 4: Login en pausa y «Recuperar mi cuenta»

**Files:**
- Create: `src/main/java/com/capstone/steam/common/exception/AccountPendingDeletionException.java`
- Create: `src/main/java/com/capstone/steam/auth/dto/RecoverAccountRequest.java` (`@NotBlank login`, `@NotBlank password`, igual que `LoginRequest`)
- Modify: `auth/AuthService.java`, `auth/AuthController.java`, `common/exception/GlobalExceptionHandler.java`, `security/SecurityConfig.java` (permitAll en `/api/auth/recover-account` si `/api/auth/**` no lo cubre ya), `messages*.properties`
- Test: `src/test/java/com/capstone/steam/auth/AccountRecoveryIntegrationTest.java`

**Interfaces:**
- Consumes: `AccountDeletionService.cancelBySelf(User)`.
- Produces:
  - `AccountPendingDeletionException extends ForbiddenException`: constructor `(String code, Instant scheduledAt)` y `Instant getScheduledAt()`.
  - El handler específico añade `deletionScheduledAt` (ISO-8601) al ProblemDetail, además de `code`.
  - `AuthService.recoverAccount(String login, String password)` → `AuthResponse`.
  - Endpoint `POST /api/auth/recover-account`.
- `login`: después de comprobar la verificación del email, si `user.isPendingDeletion()`, lanza `AccountPendingDeletionException`. El código es `auth.accountPendingDeletion` si el requester es SELF y `auth.accountDeletionByAdmin` si es ADMIN.
- `recoverAccount`:
  1. Autentica igual que `login`.
  2. Si no hay baja pendiente → la misma respuesta que daría `login`, para no revelar más.
  3. Si la pidió ADMIN → 403 `auth.accountDeletionByAdmin`.
  4. Si la pidió SELF → `cancelBySelf` y `issueToken`.

- [ ] **Step 1: tests que fallan.**
  - `loginWhilePausedBySelfIs403WithDate`: `code` y `deletionScheduledAt`.
  - `loginWhilePausedByAdminIs403AdminCode`.
  - `wrongPasswordWhilePausedIs401`.
  - `recoverBySelfCancelsAndLogsIn`: después el login normal da 200 y se publica `AccountDeletionCancelledEvent`.
  - `recoverWhenAdminRequestedIs403`.
  - `recoverWithWrongPasswordIs401`.
  - `oldLoginAfterAnonymizationIs401`: anonimiza a mano con `User.anonymize` (Review Focus 5).
- [ ] **Step 2:** ejecutarlos → FAIL.
- [ ] **Step 3:** implementar.
  - es: `auth.accountPendingDeletion` = «Tu cuenta se eliminará el {0}. Puedes recuperarla ahora.»; `auth.accountDeletionByAdmin` = «Un administrador ha programado la eliminación de tu cuenta para el {0}.»
  - en: «Your account will be deleted on {0}. You can recover it now.» y «An administrator has scheduled your account for deletion on {0}.»
  - it: «Il tuo account verrà eliminato il {0}. Puoi recuperarlo adesso.» y «Un amministratore ha programmato l'eliminazione del tuo account per il {0}.»
  - `{0}` = la fecha en formato corto del idioma, zona `Europe/Rome`.
- [ ] **Step 4:** tests → PASS, y `AuthIntegrationTest` sigue en verde.
- [ ] **Step 5: commit** «Login: cuenta en pausa y recuperar la cuenta».

### Task 5: Anonimizar y la tarea programada

**Files:**
- Create: `src/main/java/com/capstone/steam/user/AccountAnonymizer.java`
- Create: `src/main/java/com/capstone/steam/user/AccountDeletionScheduler.java`
- Create: `src/main/java/com/capstone/steam/notification/event/AccountDeletedEvent.java`
- Modify, con métodos de borrado por usuario (`@Modifying(flushAutomatically = true, clearAutomatically = true)`, como en los repos de tokens):
  - `cart/CartItemRepository.java`, `wishlist/WishlistItemRepository.java`, `friend/FriendshipRepository.java` (las dos direcciones);
  - `auth/RefreshTokenRepository.java`, `auth/EmailVerificationTokenRepository.java`;
  - `review/ReviewRepository.java` (`findByUserId`);
  - `game/GameRepository.java` (`findByDeveloperId`), `user/UserRepository.java` (`findIdsDueForDeletion(Instant now)`).
- Test: `src/test/java/com/capstone/steam/user/AccountAnonymizationIntegrationTest.java`

**Interfaces:**
- Produces:
  - `AccountAnonymizer.anonymize(Long userId)`, `@Transactional(propagation = REQUIRES_NEW)`:
    1. Bloquea el usuario.
    2. Si ya no está pendiente o la fecha aún no llegó, no hace nada.
    3. Captura email, nombre e idioma.
    4. Borra, para cada reseña, su delta con `gameRepository.applyReviewDelta(gameId, -positive, -1)` y después la reseña.
    5. Borra carrito, wishlist, amistades y tokens.
    6. Pasa los juegos PUBLISHED del estudio a DELISTED. Borra los DRAFT si no están en ninguna biblioteca ni en `order_items`; si lo están, los pasa a DELISTED.
    7. `user.anonymize(passwordEncoder.encode(<UUID aleatorio>), now)`.
    8. Publica `AccountDeletedEvent(email, displayName, locale)`.
  - `AccountDeletionScheduler`:
    - `int runDue()`: recorre `findIdsDueForDeletion(now)` y llama a `anonymize` en cada uno con try/catch y `log.warn` (como `RefreshTokenPurgeScheduler`). Devuelve cuántas cuentas hizo.
    - Se dispara con `@Scheduled(cron = "${app.account.deletion-cron}")` y con `@EventListener(ApplicationReadyEvent.class)`.
- `findIdsDueForDeletion`: `deletionScheduledAt <= :now and deletedAt is null`, ordenadas por id.

- [ ] **Step 1: tests que fallan.** Con un `Clock` mutable en la configuración de test.
  - `notAnonymizedBeforeTheDate`: a +14 días menos 1 minuto, `runDue()` = 0.
  - `anonymizesAfterTheDate`: username y email `deleted_<id>`, sin login posible, reseñas borradas y `review_count`/`positive_count` del juego recalculados, sin carrito, wishlist, amistades ni tokens. Los pedidos y su biblioteca siguen.
  - `studioGamesDelistedDraftsDeletedBuyersKeepThem`: el comprador sigue viendo el juego en `GET /api/library`.
  - `freedEmailAndUsernameCanRegisterAgain` (Review Focus 1).
  - `cartWithDelistedGameFailsCheckoutCleanly`: 4xx, no 500 (Review Focus 4).
  - `oneFailingAccountDoesNotStopTheOthers`: un `AccountAnonymizer` espía que lanza para el primer id; el segundo se anonimiza igual.
- [ ] **Step 2:** ejecutarlos → FAIL.
- [ ] **Step 3:** implementar.
- [ ] **Step 4:** tests → PASS. Ejecutar también `ReviewServiceTest`, `CheckoutIntegrationTest` y `FriendIntegrationTest`.
- [ ] **Step 5: commit** «Cuentas: anonimizar al acabar el plazo (tarea diaria y al arrancar)».

### Task 6: Juegos retirados visibles para sus dueños y «Estudio eliminado»

**Files:**
- Create: `src/main/java/com/capstone/steam/game/GameVisibility.java`
- Modify:
  - `game/CatalogService.java`, `game/CatalogController.java`, `review/ReviewService.java` + `review/ReviewController.java` (`listForGame`);
  - `game/dto/GameDetailResponse.java` (`DeveloperInfo`), `game/dto/GameSummaryResponse.java`, `library/dto/LibraryEntryResponse.java`;
  - `game/GameManagementService.java` (`moderate`), `messages*.properties`.
- Test: `src/test/java/com/capstone/steam/game/DelistedVisibilityIntegrationTest.java`

**Interfaces:**
- Produces:
  - `GameVisibility.canView(Game game, Long viewerId /* null sin sesión */, boolean viewerIsAdmin)` → `boolean`: PUBLISHED, sí; DELISTED, solo admin, su developer o quien lo tiene en la biblioteca; DRAFT, no.
  - `CatalogService.getBySlug(String slug, Long viewerId, boolean viewerIsAdmin)` y `ReviewService.listForGame(String slug, String language, Pageable pageable, Long viewerId, boolean viewerIsAdmin)` usan esa regla. El controller lee el principal opcional (`@AuthenticationPrincipal AppUserPrincipal`, que puede ser null).
  - `DeveloperInfo(Long id, String displayName, boolean deleted)`, con `displayName` null si está eliminado.
  - `GameSummaryResponse`: `developerName` null y `boolean developerDeleted`.
  - `LibraryEntryResponse` añade `String developerName` y `boolean developerDeleted`.
  - `moderate` de DELISTED a PUBLISHED con el developer eliminado → `ConflictException("game.developerDeleted")`. es «Este juego es de un estudio eliminado y no se puede volver a publicar.»; en «This game belongs to a deleted studio and can't be published again.»; it «Questo gioco appartiene a uno studio eliminato e non può essere ripubblicato.».

- [ ] **Step 1: tests que fallan.**
  - `ownerSeesDelistedGameAndItsReviews`.
  - `othersGet404ForDelistedGame`: anónimo y otro jugador.
  - `delistedGameNotInCatalogSearch`.
  - `ownerCanReviewDelistedGame`.
  - `deletedStudioShowsAsDeletedInDetailAndLibrary`.
  - `adminCannotRepublishGameOfDeletedStudio` → 409.
- [ ] **Step 2:** ejecutarlos → FAIL.
- [ ] **Step 3:** implementar.
- [ ] **Step 4:** tests → PASS, y `CatalogIntegrationTest` sigue en verde.
- [ ] **Step 5: commit** «Catálogo: los dueños ven la ficha de un juego retirado; Estudio eliminado».

### Task 7: Los cuatro correos

**Files:**
- Modify: `notification/MailTexts.java`, `notification/EmailTemplates.java`, `notification/EmailService.java`, `notification/MailListener.java`
- Test: `src/test/java/com/capstone/steam/notification/EmailTemplatesI18nTest.java`, `MailEventIntegrationTest.java`

**Interfaces:**
- Consumes: `AccountDeletionScheduledEvent`, `AccountDeletionCancelledEvent` y `AccountDeletedEvent` (Tasks 3 y 5).
- Produces:
  - `EmailService.sendDeletionScheduled(String to, String displayName, String locale, Instant scheduledAt, DeletionRequester by)`.
  - `EmailService.sendDeletionCancelled(String to, String displayName, String locale)`.
  - `EmailService.sendAccountDeleted(String to, String displayName, String locale)`.
  - Un listener AFTER_COMMIT + `@Async` por evento.
- Textos ES, copiados del spec. EN e IT, traducción fiel:
  1. Programada por ti: «Tu cuenta se eliminará el {fecha}. Si cambias de idea, inicia sesión antes de esa fecha y pulsa Recuperar mi cuenta.»
  2. Programada por un admin: «Un administrador ha programado la eliminación de tu cuenta para el {fecha}. Si crees que es un error, contesta a este correo.»
  3. Cancelada: «Tu cuenta vuelve a estar activa.»
  4. Eliminada: «Tu cuenta y tus datos personales se han eliminado. Conservamos los pedidos por obligación legal, sin datos que te identifiquen.»
- La fecha va en formato largo del idioma, zona `Europe/Rome`.

- [ ] **Step 1: tests que fallan.**
  - `deletionMailsInThreeLanguagesWithoutStoreButtons`: los 4 × 3, sin `<a` que apunte a la tienda salvo el logo.
  - En `MailEventIntegrationTest`: pedir la baja → 1 correo; cancelar → 1; anonimizar → 1, al email antiguo.
- [ ] **Step 2:** ejecutarlos → FAIL.
- [ ] **Step 3:** implementar.
- [ ] **Step 4:** `clean verify` completo → exit 0 y cobertura ≥ 80 %.
- [ ] **Step 5: commit** «Correos: baja programada, cancelada y cuenta eliminada».

### Task 8: Frontend — API, Ajustes y login

**Files:**
- Modify: `frontend/src/api/types.ts`, `api/endpoints.ts`, `pages/Settings.tsx`, `pages/Auth.tsx`, `context/StoreContext.tsx` (solo si hace falta para cerrar la sesión), `i18n/messages.ts`
- Test: `frontend/src/pages/Settings.test.tsx`, `pages/Auth.test.tsx`

**Interfaces:**
- Produces:
  - `User` añade `deletionScheduledAt: string | null` y `deletionRequestedBy: 'SELF' | 'ADMIN' | null`.
  - `userApi.requestDeletion(password: string)` → `Promise<{ deletionScheduledAt: string }>`.
  - `authApi.recoverAccount(login: string, password: string)` → `Promise<AuthResponse>`.
- **Ajustes:**
  - Sección final «Eliminar mi cuenta» con un botón que abre un diálogo: el texto del spec (variante para estudios si `roles` incluye `ROLE_DEVELOPER`), un campo de contraseña y un botón rojo.
  - Si va bien: aviso con la fecha y `logout()`.
  - Muestra el error del backend tal cual, incluidos `user.lastAdmin` y `user.deletionWrongPassword`.
- **Login:**
  - `code === 'auth.accountPendingDeletion'` → mensaje con la fecha (de `body.deletionScheduledAt`) y el botón «Recuperar mi cuenta», que llama a `recoverAccount` con lo escrito y abre la sesión igual que el login.
  - `code === 'auth.accountDeletionByAdmin'` → solo el mensaje.

- [ ] **Step 1: tests que fallan.**
  - Settings: `pide la contraseña y cierra la sesión con la fecha`; `muestra el error de último admin`.
  - Auth: `cuenta en pausa ofrece Recuperar mi cuenta y entra`; `baja del admin no ofrece recuperar`.
- [ ] **Step 2:** `npx vitest run src/pages/Settings.test.tsx src/pages/Auth.test.tsx` → FAIL.
- [ ] **Step 3:** implementar, con las claves en es/en/it.
- [ ] **Step 4:** `npx vitest run` y `npx tsc -b` → todo en verde.
- [ ] **Step 5: commit** «Frontend: eliminar mi cuenta y recuperar la cuenta en el login».

### Task 9: Frontend — panel de admin y juegos retirados

**Files:**
- Modify: `frontend/src/api/types.ts`, `api/endpoints.ts`, `pages/Admin.tsx`, `pages/GameDetail.tsx`, `pages/Collection.tsx`, `pages/Discover.tsx` (y cualquier sitio que lea `developer.displayName` o `developerName`), `i18n/messages.ts`
- Test: `frontend/src/pages/Admin.test.tsx` (nuevo), `pages/GameDetail.test.tsx`

**Interfaces:**
- Produces:
  - `adminApi.deleteUser(id: number)` → `Promise<User>` (DELETE).
  - `adminApi.cancelDeletion(id: number)` → `Promise<User>`.
  - Tipos: `GameDetail.developer: { id: number; displayName: string | null; deleted: boolean }`; `GameSummary.developerDeleted: boolean`; `LibraryEntry.developerName: string | null` y `developerDeleted: boolean`.
- **Admin → Usuarios:**
  - «Eliminar», salvo en la fila propia, con `confirm`.
  - Insignia «Se elimina el {fecha}» con «(lo pidió el usuario)» o «(lo pidió un admin)».
  - «Cancelar eliminación».
- **Ficha con `status === 'DELISTED'`:** aviso «Ya no está a la venta», sin botones de compra ni de wishlist.
- **Autor eliminado:** «Estudio eliminado» en vez del nombre en la ficha, las tarjetas, la biblioteca y la columna developer del admin. Ningún `toUpperCase()` sobre null.

- [ ] **Step 1: tests que fallan.**
  - Admin: `elimina y cancela la baja de un usuario`; `no ofrece eliminar la propia cuenta`.
  - GameDetail: `juego retirado sin botón de compra y con Estudio eliminado`.
- [ ] **Step 2:** ejecutarlos → FAIL.
- [ ] **Step 3:** implementar.
- [ ] **Step 4:** `npx vitest run`, `npx tsc -b` y `npm run build` → verde.
- [ ] **Step 5: commit** «Frontend: eliminar cuentas desde el panel y juegos retirados».

### Task 10: Postman y documentación

**Files:**
- Modify: `postman/Capstone-Steam.postman_collection.json`, `README.md` (en **italiano**), `frontend/README.md` (en italiano, la cifra de tests), `handoff.md` (D55 y fila de progreso), `render.yaml` (las dos variables con sus valores por defecto)

- [ ] **Step 1: Postman.** Con una cuenta nueva de la propia colección:
  - pedir la baja (200 con fecha);
  - login → 403 `auth.accountPendingDeletion`;
  - `recover-account` → 200 con tokens;
  - admin la elimina → 200;
  - login → 403 `auth.accountDeletionByAdmin`;
  - admin cancela → 200.
- [ ] **Step 2: Newman** contra el backend local en 8081: `npx newman run postman/Capstone-Steam.postman_collection.json --env-var baseUrl=http://localhost:8081 --env-var adminPassword=<del .env>` → 0 fallos.
- [ ] **Step 3: documentación.**
  - README en italiano: los endpoints nuevos, `ACCOUNT_DELETION_GRACE` y `ACCOUNT_DELETION_CRON`, y las cifras de tests.
  - `handoff.md`: decisión D55 (anonimizar en vez de borrar; 14 días; último admin; DELISTED visible para dueños).
- [ ] **Step 4: commit** «Postman y documentación: eliminar cuentas».
