# Verificación de email obligatoria Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Crear cuenta → correo con enlace → pulsar → cuenta verificada y sesión iniciada; sin verificar no se puede entrar. Además, el correo sale de forma fiable desde Render (timeouts más largos y reintentos).

**Architecture:** Campo `users.email_verified` + tabla `email_verification_tokens` (V12). `EmailVerificationService` emite tokens de un solo uso (SHA-256 en BD, 24 h), verifica y reenvía. `AuthService.register` deja de devolver tokens cuando la verificación está activa; `login` responde 403 `auth.emailNotVerified` tras validar la contraseña. Un interruptor `app.auth.email-verification.required` (true por defecto, false solo en tests) mantiene el resto de tests como están. El frontend añade `/check-email` y `/verify-email`; el seed y Postman verifican sus cuentas con un endpoint de admin.

**Tech Stack:** Spring Boot 4.1.1, Java 25, Spring Security + JWT (jjwt 0.13), Spring Data JPA, Flyway, Spring Mail, JUnit 5 + Mockito + H2; React 19 + Vitest; Node (seed) y Postman/Newman.

**Spec:** `docs/superpowers/specs/2026-10-20-demos-y-verificacion-design.md` (Parte 2)

## Global Constraints

- Migración nueva: **V12** en `src/main/resources/db/migration/common/` (SQL portable para PostgreSQL 18 y H2 `MODE=PostgreSQL`). No tocar V1-V11.
- Token: 32 bytes `SecureRandom` en Base64URL sin relleno; en BD solo SHA-256 en hex (64). Un solo uso. TTL `EMAIL_VERIFICATION_TTL` = `P1D` por defecto. Al emitir uno nuevo se anulan (`used_at = now`) los anteriores del usuario.
- Interruptor `EMAIL_VERIFICATION_REQUIRED` = `true` por defecto (dev y prod). `src/test/resources/application-test.yml` lo pone a `false`.
- Reenvío: máximo **3 por hora por email** (normalizado en minúsculas y `trim`), en memoria; siempre 204 aunque la cuenta no exista o ya esté verificada.
- Nunca revelar más que hoy si un email/usuario existe. Contraseña mala → 401 `error.badCredentials` esté verificada o no.
- Nunca escribir el token en claro en el log **salvo** con el correo desactivado (sin `MAIL_USERNAME`).
- Correo: `connectiontimeout`/`timeout`/`writetimeout` = `${MAIL_TIMEOUT:15000}`; hasta **3 intentos** con pausas de **2 s y 6 s** solo para fallos de conexión. `EmailService` sigue sin lanzar (D36).
- Textos nuevos del backend en `messages.properties`, `messages_en.properties`, `messages_it.properties`; correos en `MailTexts` (ES/EN/IT); frontend en `i18n/messages.ts` (es/en/it).
- Comandos: backend `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"; .\mvnw.cmd -q -B test "-Dtest=<Clase>" "-Djacoco.skip=true"`; al final `.\mvnw.cmd -q -B clean verify *> "$env:TEMP\v.log"; "exit $LASTEXITCODE"` → `exit 0` (los errores de correo a `localhost:3025` en los logs de test son normales). Frontend `cd frontend; npx vitest run`.
- Commits en español, uno por tarea, **sin `Co-Authored-By`** ni atribución a Claude. Trabajo en tu worktree; sin push ni merge.

## Review Focus

- El enlace se abre dos veces (doble clic, React en desarrollo, escáner de correo) → la primera inicia sesión; la segunda muestra «no válido o caducado» sin romper la sesión ya abierta. Test en Task 5 (frontend) y Task 3 (400 en el segundo uso).
- Registro repetido con el mismo email de una cuenta **sin verificar** → sustituye la cuenta y el primer enlace deja de valer. Test en Task 3.
- Email con mayúsculas o espacios en el reenvío (`" Ana@Mail.com "`) → cuenta igual que `ana@mail.com` para el límite y para encontrar la cuenta. Test en Task 4.
- El correo desactivado en desarrollo → el enlace aparece en el log y el usuario puede verificarse; con correo activo el token no aparece en ningún log. Test en Task 2.
- Una cuenta desactivada por un admin **y** sin verificar → el login sigue respondiendo como hoy para cuentas desactivadas (403 `error.disabled`, comprobado por Spring antes que la contraseña); la verificación no cambia ese caso. Test en Task 3.

---

### Task 1: Esquema: V12, entidad de tokens, `emailVerified` y `TokenHashing`

**Files:**
- Create: `src/main/resources/db/migration/common/V12__email_verification.sql`, `src/main/java/com/capstone/steam/auth/TokenHashing.java`, `auth/EmailVerificationToken.java`, `auth/EmailVerificationTokenRepository.java`
- Modify: `user/User.java` (campo `emailVerified`), `user/dto/UserResponse.java` (`boolean emailVerified`), `user/UserRepository.java` (`searchPlayers` añade `u.emailVerified = true`), `user/AdminAccountInitializer.java` (admin verificado), `auth/RefreshTokenService.java` (usar `TokenHashing`), `src/test/.../FlywayMigrationTest.java`
- Test: `src/test/java/com/capstone/steam/auth/TokenHashingTest.java`, `FlywayMigrationTest`

**Interfaces:**
- Produces: `final class TokenHashing { static String newRawToken(); static String sha256Hex(String raw); }` (paquete `auth`, visibilidad pública para usarla desde `auth`).
- Produces: `User#isEmailVerified()`, `User#setEmailVerified(boolean)` (columna `email_verified`, valor Java por defecto `false`).
- Produces: entidad `EmailVerificationToken(Long userId, String tokenHash, Instant expiresAt)` con `getId()`, `getUserId()`, `getExpiresAt()`, `getUsedAt()`, `isUsed()`; repositorio:
  ```java
  Optional<EmailVerificationToken> findByTokenHash(String tokenHash);
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update EmailVerificationToken t set t.usedAt = :now where t.userId = :userId and t.usedAt is null")
  int invalidateActive(Long userId, Instant now);
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update EmailVerificationToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
  int markUsedIfUnused(Long id, Instant now);
  @Query("select max(t.expiresAt) from EmailVerificationToken t where t.userId = :userId")
  Optional<Instant> latestExpiry(Long userId);
  ```
- Produces: `UserResponse.emailVerified` (JSON `emailVerified`).

V12:
```sql
-- V12: verificación de email. Las cuentas que ya existen quedan verificadas.
ALTER TABLE users ADD COLUMN email_verified BOOLEAN DEFAULT FALSE NOT NULL;
UPDATE users SET email_verified = TRUE;
CREATE TABLE email_verification_tokens (
    id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    used_at    TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_email_verification_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX idx_email_verification_tokens_user ON email_verification_tokens (user_id);
```

- [ ] **Step 1: Tests que fallan**
  - `TokenHashingTest`: `newRawToken` tiene 43 caracteres Base64URL y no se repite en 1000 llamadas; `sha256Hex("abc")` = `ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad`.
  - `FlywayMigrationTest`: versión actual `"12"` y 12 migraciones aplicadas (renombrar el test a `appliesAllMigrationsUpToV12`); `existingUsersAreVerifiedAfterV12` (el admin de test sale con `email_verified = TRUE`); `emailVerificationTokenHashIsUnique` (insertar dos filas con el mismo hash → `DataIntegrityViolationException`).
  - En un test existente de búsqueda de jugadores (p. ej. `FriendIntegrationTest`), un jugador con `emailVerified = false` (puesto por repositorio) no sale en `GET /api/users/search`.
- [ ] **Step 2:** Run `.\mvnw.cmd -q -B test "-Dtest=TokenHashingTest,FlywayMigrationTest,FriendIntegrationTest" "-Djacoco.skip=true"` → FAIL.
- [ ] **Step 3:** Implementar. `RefreshTokenService` pasa a llamar a `TokenHashing.newRawToken()` / `sha256Hex()` y borra sus helpers privados. `AdminAccountInitializer` hace `setEmailVerified(true)`.
- [ ] **Step 4:** Run lo mismo + `RefreshTokenServiceTest,RefreshTokenIntegrationTest` → PASS.
- [ ] **Step 5:** `git commit -m "Verificación: V12, tokens de verificación, emailVerified y TokenHashing"`

---

### Task 2: Correo: plantilla de bienvenida con verificación, reintentos y timeouts

**Files:**
- Modify: `notification/MailTexts.java` (campos nuevos), `notification/EmailTemplates.java` (`welcome` con token), `notification/EmailService.java` (`sendWelcome` con token, reintentos), `notification/MailListener.java`, `notification/event/UserRegisteredEvent.java`, `src/main/resources/application.yml` (timeouts)
- Create: `notification/event/VerificationEmailEvent.java`
- Test: `EmailServiceTest`, `EmailTemplatesI18nTest`, `MailEventIntegrationTest`

**Interfaces:**
- Produces: `record UserRegisteredEvent(Long userId, String email, String displayName, String locale, String verificationToken)` (`verificationToken` es `null` si la verificación está desactivada).
- Produces: `record VerificationEmailEvent(String email, String displayName, String locale, String verificationToken)` (reenvío).
- Produces: `EmailService#sendWelcome(String to, String displayName, String locale, String verificationToken)`; `EmailTemplates.welcome(Brand brand, String displayName, String locale, String verificationToken)`.
- Produces (constructor de `EmailService`, para tests): parámetro opcional `Sleeper` (`interface Sleeper { void sleep(Duration d) throws InterruptedException; }`) con la implementación real `Thread::sleep`; constructor `@Autowired` sin él, como en `SimulatedPaymentGateway` (D34).

Textos nuevos en `MailTexts` (ES; EN/IT equivalentes): `verifySubject` «¡Bienvenido a %s! Confirma tu email», `verifyPreheader` «Confirma tu email para empezar a usar tu cuenta.», `verifyBody` «Tu cuenta de %s está casi lista. Confirma tu email para iniciar sesión.», `verifyButton` «Verificar mi email», `verifyExpiry` «El enlace caduca en 24 horas. Si no has creado esta cuenta, ignora este correo.».

- [ ] **Step 1: Tests que fallan**
  - `EmailTemplatesI18nTest`: con token `"abc_DEF-123"`, el HTML de `welcome` contiene `href="https://arcade.test/verify-email?token=abc_DEF-123"`, el botón «Verificar mi email» y la nota de 24 h; el asunto es `¡Bienvenido a ARCADE! Confirma tu email`; en `en` «Welcome to ARCADE! Confirm your email», en `it` el equivalente. Con token `null` sale el correo de bienvenida actual (botón a `/browse`).
  - `EmailServiceTest`: `retriesConnectionFailuresTwiceThenSends` (mock `JavaMailSender.send` lanza `MailSendException` con causa `MailConnectException`… dos veces y luego OK → 3 llamadas, `Sleeper` recibe 2 s y 6 s); `givesUpAfterThreeConnectionFailuresWithoutThrowing`; `doesNotRetryAuthenticationFailures` (`MailAuthenticationException` → 1 llamada); `logsVerificationLinkOnlyWhenMailIsDisabled` (con `smtpUsername` vacío el log contiene `/verify-email?token=`; con correo activo ningún log contiene el token; usar un `ListAppender` de Logback como en los tests de log existentes, si los hay, o capturar con `OutputCaptureExtension`).
  - `MailEventIntegrationTest`: registrar con `@TestPropertySource(properties = "app.auth.email-verification.required=true")` → el correo enviado (GreenMail/mock actual) tiene el asunto con «Confirma tu email».
- [ ] **Step 2:** Run `.\mvnw.cmd -q -B test "-Dtest=EmailServiceTest,EmailTemplatesI18nTest,MailEventIntegrationTest" "-Djacoco.skip=true"` → FAIL.
- [ ] **Step 3:** Implementar. El token va en la URL con `URLEncoder.encode(token, UTF_8)`. Fallo de conexión = `MailSendException` cuya cadena de causas contiene `MailConnectException`, `ConnectException` o `SocketTimeoutException`. Log por intento: `"Reintento {}/3 del correo a {} (\"{}\")"` con el email enmascarado. `MailListener` añade `onVerificationEmail(VerificationEmailEvent)` con `@Async(AsyncConfig.MAIL_EXECUTOR)` y `@TransactionalEventListener(AFTER_COMMIT)`. `application.yml`: los tres timeouts a `${MAIL_TIMEOUT:15000}`.
- [ ] **Step 4:** Run lo mismo → PASS.
- [ ] **Step 5:** `git commit -m "Correo: bienvenida con enlace de verificación, reintentos y timeouts de 15 s"`

---

### Task 3: Registro, verificación y login

**Files:**
- Create: `auth/EmailVerificationService.java`, `auth/EmailVerificationProperties.java`, `auth/RegistrationResult.java`, `auth/dto/RegisterResponse.java`, `auth/dto/VerifyEmailRequest.java`, `src/test/java/com/capstone/steam/auth/EmailVerificationIntegrationTest.java`, `auth/EmailVerificationServiceTest.java`
- Modify: `auth/AuthService.java` (`register`, `login`, nuevo `verifyEmail`), `auth/AuthController.java`, `auth/dto/AuthResponse.java` (`implements RegistrationResult`), `security/SecurityConfig.java:45-46` (`/api/auth/verify-email`, `/api/auth/resend-verification` en `permitAll`), `common/exception/ApiException.java` (`getCode()`), `common/exception/GlobalExceptionHandler.java` (`handleApi` añade la propiedad `code`), `src/main/resources/application.yml` (`app.auth.email-verification`), `src/test/resources/application-test.yml` (`required: false`), `messages*.properties`, `user/UserRepository.java` (`Optional<User> findByUsernameIgnoreCase(String)` y `Optional<User> findByEmail(String)` si no existen)

**Interfaces:**
- Consumes: Task 1 (repositorio de tokens, `TokenHashing`, `emailVerified`), Task 2 (`UserRegisteredEvent` con token).
- Produces:
  ```java
  @ConfigurationProperties("app.auth.email-verification")
  record EmailVerificationProperties(boolean required, Duration ttl, int resendPerHour) {}
  // application.yml: required: ${EMAIL_VERIFICATION_REQUIRED:true}, ttl: ${EMAIL_VERIFICATION_TTL:P1D}, resend-per-hour: 3

  public sealed interface RegistrationResult permits AuthResponse, RegisterResponse {}
  public record RegisterResponse(Long id, String email, boolean verificationRequired) implements RegistrationResult {}
  public record VerifyEmailRequest(@NotBlank @Size(max = 200) String token) {}

  // EmailVerificationService
  String issue(User user);          // anula los anteriores, guarda el hash, devuelve el token en claro
  User verify(String rawToken);     // 400 BadRequestException("auth.verificationInvalid") si no existe, usado o caducado; usa markUsedIfUnused (0 → 400)
  boolean hasLiveToken(Long userId);// latestExpiry > now
  // AuthService
  RegistrationResult register(RegisterRequest request);
  AuthResponse verifyEmail(String rawToken);
  ```
- Produces (HTTP): `POST /api/auth/register` → 201 `RegisterResponse` o `AuthResponse`; `POST /api/auth/verify-email` → 200 `AuthResponse`; login de cuenta sin verificar → 403 con `{"code":"auth.emailNotVerified", ...}`.

Textos backend (ES; EN/IT equivalentes): `auth.emailNotVerified` «Tu email no está verificado. Revisa tu correo o pide otro enlace.», `auth.verificationInvalid` «El enlace no es válido o ha caducado.», `auth.resendRateLimited` «Has pedido demasiados correos. Inténtalo dentro de una hora.».

Reglas de registro con verificación activa (en este orden, dentro de la misma transacción):
1. Email de una cuenta **verificada** → 409 `auth.emailTaken`. Usuario de **otra** cuenta verificada → 409 `auth.usernameTaken`.
2. Usuario ocupado por otra cuenta **sin verificar** con token vivo (`hasLiveToken`) → 409 `auth.usernameTaken`; sin token vivo → borrar esa cuenta (`userRepository.delete`, los tokens caen por `ON DELETE CASCADE`) y seguir.
3. Email de una cuenta **sin verificar** → reutilizar esa fila: nuevo username, contraseña, `displayName`, `locale` y roles (`ROLE_USER` + `ROLE_DEVELOPER` según `accountType`).
4. Si no, crear el usuario con `emailVerified = false`.
5. `issue(user)`, publicar `UserRegisteredEvent(..., token)` y devolver `RegisterResponse(id, email, true)`.

Con verificación desactivada: como hoy, pero el usuario se guarda con `emailVerified = true`, el evento lleva `verificationToken = null` y se devuelve `AuthResponse`.

Login: tras `authenticationManager.authenticate(...)` con éxito, si `!user.isEmailVerified()` → `throw new ForbiddenException("auth.emailNotVerified")` (no se emite ningún refresh token).

- [ ] **Step 1: Tests que fallan** (`EmailVerificationIntegrationTest` con `@TestPropertySource(properties = "app.auth.email-verification.required=true")` y un `Clock` controlable como en `AccessTokenRevocationIntegrationTest`; el token en claro se obtiene capturando el `UserRegisteredEvent` con `@RecordApplicationEvents`)
  - `registerReturnsNoTokensAndRequiresVerification` → 201, `$.verificationRequired == true`, `$.id` presente, `$.accessToken` ausente.
  - `loginBeforeVerifyingIsForbiddenOnlyWithTheRightPassword` → contraseña buena: 403 y `$.code == "auth.emailNotVerified"`; contraseña mala: 401.
  - `verifyEmailLogsTheUserIn` → 200 con `accessToken` y `refreshToken`; luego `GET /api/users/me` 200 con `emailVerified == true`; login normal 200.
  - `verificationTokenIsSingleUse` → segunda llamada 400 `auth.verificationInvalid`.
  - `expiredTokenIsRejected` → avanzar el reloj 24 h + 1 s → 400.
  - `registeringAgainWithTheSameUnverifiedEmailReplacesTheAccount` → segundo registro (otro username) 201 con el mismo `id`; el primer token da 400; el nuevo, 200 con el username nuevo.
  - `verifiedEmailIsStillTaken` → 409 `auth.emailTaken`.
  - `usernameOfAnUnverifiedAccountIsFreedOnlyAfterItsLinkExpires` → antes de 24 h 409; después, 201 (la cuenta vieja ya no existe).
  - `disabledAndUnverifiedAccountKeepsTheDisabledResponse` → admin desactiva (PATCH enabled false) → login 403 con texto «La cuenta está desactivada».
  - `EmailVerificationServiceTest` (Mockito + `Clock` fijo): `issue` invalida los anteriores y guarda solo el hash; `verify` con token usado → `BadRequestException`.
  - Los tests existentes de registro siguen en verde sin cambios (verificación desactivada en `application-test.yml`).
- [ ] **Step 2:** Run `.\mvnw.cmd -q -B test "-Dtest=EmailVerificationIntegrationTest,EmailVerificationServiceTest,AuthIntegrationTest,AuthServiceTest" "-Djacoco.skip=true"` → FAIL.
- [ ] **Step 3:** Implementar. `AuthController.register` devuelve `RegistrationResult` con `@ResponseStatus(CREATED)`; nuevo `@PostMapping("/verify-email")`. `GlobalExceptionHandler.handleApi`: `ProblemDetail p = problem(...); p.setProperty("code", ex.getCode()); return p;`. Registrar `EmailVerificationProperties` con `@EnableConfigurationProperties` donde estén las demás (o `@ConfigurationPropertiesScan` si ya existe).
- [ ] **Step 4:** Run lo mismo → PASS. Run también `AdminIntegrationTest,RefreshTokenIntegrationTest,MailEventIntegrationTest` → PASS.
- [ ] **Step 5:** `git commit -m "Verificación: registro sin sesión, enlace de un solo uso y login bloqueado hasta verificar"`

---

### Task 4: Reenvío del correo y verificación por el admin

**Files:**
- Create: `auth/ResendRateLimiter.java`, `auth/dto/ResendVerificationRequest.java`, `src/test/.../auth/ResendRateLimiterTest.java`
- Modify: `auth/EmailVerificationService.java` (`resend`), `auth/AuthController.java` (`POST /resend-verification`, 204), `user/AdminUserController.java` (`POST /{id}/verify-email`), `user/UserService.java` (`verifyEmail(Long id)`), `EmailVerificationIntegrationTest`

**Interfaces:**
- Produces: `ResendRateLimiter(Clock clock, int perHour)` con `void check(String emailKey)` → `TooManyRequestsException("auth.resendRateLimited")` al cuarto intento en una hora (ventana deslizante en memoria con barrido, como `recommendation/ChatRateLimiter`, pero con clave `String`); constructor `@Autowired` que toma `EmailVerificationProperties.resendPerHour()` y `Clock`.
- Produces: `EmailVerificationService#resend(String email)` (normaliza `trim().toLowerCase(Locale.ROOT)`, llama a `check`, y solo si hay cuenta sin verificar: `issue` + publicar `VerificationEmailEvent`).
- Produces: `record ResendVerificationRequest(@NotBlank @Email @Size(max = 254) String email)`.
- Produces: `UserService#verifyEmail(Long userId): UserResponse` (idempotente; 404 si no existe) y `POST /api/admin/users/{id}/verify-email` → 200 `UserResponse`.

- [ ] **Step 1: Tests que fallan**
  - `ResendRateLimiterTest`: 3 llamadas pasan, la cuarta lanza; tras avanzar 1 h vuelve a pasar; claves distintas no se afectan.
  - `EmailVerificationIntegrationTest`: `resendAlwaysReturns204` (email inexistente, verificado y sin verificar → 204; solo el sin verificar publica `VerificationEmailEvent`); `resendInvalidatesThePreviousLink`; `resendNormalizesTheEmail` (`" Ana@Test.com "` cuenta para `ana@test.com` en el límite); `fourthResendInAnHourIs429`; `adminCanVerifyAnAccount` (luego login 200); `nonAdminCannotVerify` → 403.
- [ ] **Step 2:** Run `.\mvnw.cmd -q -B test "-Dtest=ResendRateLimiterTest,EmailVerificationIntegrationTest" "-Djacoco.skip=true"` → FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Run lo mismo → PASS.
- [ ] **Step 5:** `git commit -m "Verificación: reenvío con límite por email y verificación manual del admin"`

---

### Task 5: Frontend: registro, `/check-email`, `/verify-email`, login y panel de admin

**Files:**
- Create: `frontend/src/pages/EmailVerification.tsx` (`CheckEmail`, `VerifyEmail`), `frontend/src/pages/EmailVerification.test.tsx`
- Modify: `frontend/src/api/types.ts` (`User.emailVerified`, `RegisterResponse`), `frontend/src/api/endpoints.ts` (`authApi.register` tipado `AuthResponse | RegisterResponse`, `authApi.verifyEmail`, `authApi.resendVerification`, `adminApi.verifyEmail`), `frontend/src/api/client.ts` (`ApiError.code` leído de `problem.code`), `frontend/src/context/StoreContext.tsx` (`register` y nuevo `verifyEmail`), `frontend/src/pages/Auth.tsx` (registro y login), `frontend/src/pages/Admin.tsx` (insignia y botón), `frontend/src/App.tsx` (rutas `check-email`, `verify-email`), `frontend/src/i18n/messages.ts`, `frontend/src/pages/Auth.test.tsx`, `frontend/src/test/render.tsx` (`demoUser.emailVerified = true`)

**Interfaces:**
- Consumes (HTTP): Task 3 y Task 4.
- Produces: `interface RegisterResponse { id: number; email: string; verificationRequired: true }`; `ApiError` con `readonly code?: string`; en `StoreValue`: `register(data): Promise<{ verificationRequired: boolean; email: string }>` y `verifyEmail(token: string): Promise<User>`.

Textos (es; en/it equivalentes): `auth.created` «Cuenta creada», `verify.checkTitle` «Revisa tu correo», `verify.checkText` «Te hemos enviado un enlace a {email}. Púlsalo para activar tu cuenta.», `verify.resend` «Reenviar correo», `verify.resent` «Si la cuenta está pendiente, te hemos enviado otro enlace.», `verify.wrongEmail` «¿Email equivocado? Crea la cuenta de nuevo», `verify.verifying` «Verificando tu email…», `verify.done` «¡Email verificado! Bienvenido a ARCADE.», `verify.invalid` «El enlace no es válido o ha caducado.», `verify.notVerified` «Tu email no está verificado.», `verify.emailLabel` «Tu email», `admin.unverified` «Sin verificar», `admin.verify` «Verificar», `admin.verified` «{user} verificado».

- [ ] **Step 1: Tests que fallan** (`mockFetch` de `frontend/src/test/http.ts`)
  - `EmailVerification.test.tsx`:
    - `registro con verificación lleva a /check-email` → `POST /api/auth/register` responde `{ id: 9, email: 'ana@test.com', verificationRequired: true }`; la ruta pasa a `/check-email` y se ve «Te hemos enviado un enlace a ana@test.com».
    - `reenviar desde /check-email` → `POST /api/auth/resend-verification` con `{ email: 'ana@test.com' }` y aviso «Si la cuenta está pendiente…».
    - `/verify-email?token=abc inicia sesión` → `POST /api/auth/verify-email` con `{ token: 'abc' }` devuelve `AuthResponse`; aviso «¡Email verificado!» y ruta `/`; un estudio va a `/developer`.
    - `/verify-email llama una sola vez aunque se monte dos veces` → `StrictMode` alrededor; una sola petición.
    - `/verify-email con enlace inválido` → 400; se ve «El enlace no es válido o ha caducado.», un campo email y «Reenviar correo».
    - `/verify-email sin token` → mensaje de enlace inválido sin llamar a la API.
  - `Auth.test.tsx`:
    - `login de cuenta sin verificar ofrece reenviar` → 403 con `{ code: 'auth.emailNotVerified', detail: '...' }`; aparece «Tu email no está verificado.» y «Reenviar correo»; si lo escrito era un email se usa tal cual, si era un usuario se pide el email.
    - `registro sin verificación (respuesta con tokens) inicia sesión como hoy`.
  - `client.test.ts`: `ApiError.code` toma `problem.code`.
- [ ] **Step 2:** Run `cd frontend; npx vitest run src/pages/EmailVerification.test.tsx src/pages/Auth.test.tsx src/api/client.test.ts` → FAIL.
- [ ] **Step 3:** Implementar. `VerifyEmail` guarda la promesa en un `useRef` para no repetir la llamada. Admin: si `!u.emailVerified`, insignia `badge pending` «Sin verificar» y botón «Verificar» (`adminApi.verifyEmail`), junto a la de activo/inactivo.
- [ ] **Step 4:** Run `cd frontend; npx vitest run` → PASS (todo el frontend).
- [ ] **Step 5:** `git commit -m "Frontend: registro con verificación, /check-email, /verify-email y reenvío"`

---

### Task 6: Seed y Postman

**Files:**
- Modify: `scripts/seed-demo.mjs` (`account()` y argumentos), `postman/Capstone-Steam.postman_collection.json`

**Interfaces:**
- Consumes (HTTP): `RegisterResponse`, `POST /api/admin/users/{id}/verify-email`, `403 code auth.emailNotVerified`.
- Produces: seed con `--admin-password <pwd>` (o `ADMIN_PASSWORD` del entorno; `--admin-login` opcional, por defecto `admin`).

- [ ] **Step 1:** Seed: `account()` = login → si 401 **o 403 con `code === 'auth.emailNotVerified'`** → registro → si la respuesta trae `verificationRequired`, token de admin (pedido una vez y reutilizado) + `POST /api/admin/users/{id}/verify-email` + login → devuelve `{ token, created, id, user }` como hoy. Sin contraseña de admin y con verificación requerida → `console.error('Falta --admin-password: la API exige verificar el email')` y `process.exit(1)`. Actualizar el comentario de uso de la cabecera.
- [ ] **Step 2:** Verificar el seed: backend local (8081, `EMAIL_VERIFICATION_REQUIRED=true`) contra una BD **vacía** de prueba (`DB_URL` a una base `capstone_steam_verify` creada a mano) → `node scripts/seed-demo.mjs --admin-password <ADMIN_PASSWORD>` termina con «Listo»; segunda ejecución idempotente (sin cuentas nuevas).
- [ ] **Step 3:** Postman: mover «Login admin» al principio de «1. Auth»; tras «Registro developer», «Registro jugador» y «Registro del segundo jugador»: test 201 + `verificationRequired` y guardar `*Id`; «Login sin verificar → 403» (comprobar `code`); «Verificar (admin)»; «Login» que guarda `devToken` / `playerToken` (+ `playerRefreshToken`) / `player2Token`. Añadir «Reenviar verificación → 204» y «Verificar con token falso → 400». Ajustar el test que comprueba `refreshToken` en el registro para hacerlo en el login.
- [ ] **Step 4:** Run: `npx --yes newman run postman/Capstone-Steam.postman_collection.json --env-var baseUrl=http://localhost:8081 --env-var adminPassword=<ADMIN_PASSWORD> --reporters cli --color off` contra el backend local con verificación activa → 0 fallos.
- [ ] **Step 5:** `git commit -m "Seed y Postman: verificar las cuentas nuevas con el endpoint de admin"`

---

### Task 7: Verificación final y documentación

- [ ] **Step 1:** `.\mvnw.cmd -q -B clean verify *> "$env:TEMP\v.log"; "exit $LASTEXITCODE"` → `exit 0`; sumar tests de `target/surefire-reports` (antes 332) y confirmar cobertura ≥ 80 % de líneas.
- [ ] **Step 2:** `cd frontend; npx vitest run; npm run build` → verde.
- [ ] **Step 3:** Prueba manual en local con el `.env` real: registro con un alias `+` del Gmail del usuario → un solo correo «Confirma tu email» en el idioma elegido → el enlace abre `/verify-email` y entra con sesión; login antes de verificar → 403 + reenviar; enlace reutilizado → «no válido». Con `MAIL_USERNAME` vacío, el enlace aparece en el log.
- [ ] **Step 4:** Documentación: `README.md` (API: `verify-email`, `resend-verification`, admin `verify-email`; variables `EMAIL_VERIFICATION_REQUIRED`, `EMAIL_VERIFICATION_TTL`, `MAIL_TIMEOUT`; uso del seed con `--admin-password`; tabla de Flyway con V12), `.env.example` (las tres variables, comentadas con sus valores por defecto), `frontend/README.md` (rutas nuevas).
- [ ] **Step 5:** `git commit -m "Documentación: verificación de email"`
