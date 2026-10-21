# Eliminar cuentas (propia y por el admin)

Fecha: 2026-10-21. Estado: diseño pendiente de aprobación.

## Para qué

Hoy nadie puede borrar una cuenta: el admin solo puede desactivarla (`enabled`) y la base de datos no deja borrar usuarios con pedidos (`fk_orders_user ON DELETE RESTRICT`) ni estudios con juegos (`fk_games_developer ON DELETE RESTRICT`). Para limpiar las cuentas de prueba de producción hubo que preparar SQL a mano.

El usuario quiere:
1. Que **jugadores y estudios puedan eliminar su propia cuenta**.
2. Que **el admin pueda eliminar** jugadores, estudios y otros admins.
3. Que **siempre quede al menos un admin**.

Además, ante el tribunal tiene que defenderse como «derecho al olvido» (RGPD): se borran los datos personales, pero se conservan los registros de venta (en Italia, obligación fiscal).

## Decidido por el usuario

- **Modelo «como Steam»**: al eliminar un estudio, sus juegos se retiran de la tienda, pero quien los compró los conserva en su biblioteca. La cuenta no se borra físicamente: se **anonimiza**.
- Las **reseñas** del usuario eliminado **se borran** (no se quedan como «Usuario eliminado»). La valoración del juego se recalcula sin ellas.
- **Margen para arrepentirse**: la baja no es inmediata. La cuenta queda **en pausa** unos días y luego se elimina.
- **También cuando la pide el admin** hay margen.
- Si la baja la pidió **el dueño**, puede recuperar su cuenta iniciando sesión dentro del plazo. Si la pidió **el admin**, **solo el admin** puede cancelarla.
- Mientras la cuenta de un estudio está en pausa, **sus juegos siguen a la venta**. Se retiran al eliminarse de verdad.
- **Siempre tiene que haber un admin.**

## Decisiones de Claude (el usuario las vio en el resumen; marcadas como suposición)

- Plazo: **14 días**, configurable (`app.account.deletion-grace`, por defecto `P14D`; variable `ACCOUNT_DELETION_GRACE`).
- Para pedir la baja de la propia cuenta hay que escribir la **contraseña** (como al verificar el email): una sesión abierta no basta.
- El admin puede **cancelar** cualquier baja pendiente (la pidiera quien la pidiera).
- Mientras dura la pausa, las reseñas, amistades, carrito y wishlist **siguen en la BD sin tocar**: si recupera la cuenta, todo vuelve tal cual. Solo se ocultan el perfil público y la cuenta en las búsquedas de jugadores.
- «Admin que cuenta» para la regla del último admin: con rol ADMIN, `enabled`, sin baja pendiente y sin eliminar.
- Desde el panel, el admin **no** puede eliminarse a sí mismo (igual que hoy con `user.cannotDisableSelf`); su propia baja la pide desde Ajustes, como cualquiera, y ahí también rige la regla del último admin.
- Los juegos en **borrador** del estudio (nadie los tiene) se **borran** al eliminar; los publicados pasan a **DELISTED**.

## Estados de una cuenta

| Estado | Cómo se llega | Qué pasa |
|---|---|---|
| Activa | — | Como hoy |
| **En pausa** | El dueño (Ajustes + contraseña) o el admin (panel) piden la baja | No puede entrar (ver login). Sesiones cortadas al momento (`RefreshTokenService.revokeAll` + `AccessTokenRevocations.revokeUser`, como al desactivar). Fuera de `searchPlayers` y su perfil público da 404. Sus juegos siguen a la venta. Correo con la fecha. |
| **Eliminada** | Pasa el plazo (tarea programada) | Anonimizada. No se puede volver atrás. |

Cancelar la baja (el dueño al iniciar sesión si la pidió él; el admin siempre) devuelve la cuenta a **activa** sin perder nada.

`enabled` (desactivar) sigue existiendo y es independiente: una cuenta desactivada también se puede eliminar.

## Modelo de datos — migración V13 (`common/`, SQL portable)

En `users`:
- `deletion_scheduled_at TIMESTAMP WITH TIME ZONE NULL`: cuándo se eliminará. NULL = sin baja pendiente.
- `deletion_requested_by VARCHAR(10) NULL` con CHECK `IN ('SELF', 'ADMIN')`: quién la pidió (decide si el dueño puede recuperarla).
- `deleted_at TIMESTAMP WITH TIME ZONE NULL`: cuándo se anonimizó. No NULL = eliminada.

Índice por `deletion_scheduled_at` para la tarea. No hacen falta cambios en las claves foráneas: la anonimización mantiene la fila.

## Anonimizar (al acabar el plazo)

Una transacción por cuenta, con bloqueo pesimista de la fila del usuario (orden habitual: usuario primero):

- `username` → `deleted_<id>`, `email` → `deleted_<id>@deleted.invalid` (cumplen NOT NULL y UNIQUE y liberan los originales para otro registro).
- `password_hash` → un hash de un valor aleatorio que nadie conoce; `display_name`, `avatar_url` → NULL; `enabled` → false; `profile_public` → false; `deleted_at` → ahora; se limpian `deletion_scheduled_at` y `deletion_requested_by`.
- Roles: se dejan (un estudio eliminado sigue siendo el developer de sus juegos).
- Se **borran**: refresh tokens, tokens de verificación de email, carrito, wishlist, amistades (en las dos direcciones) y **reseñas** (actualizando `review_count` y `positive_count` de cada juego con los UPDATE atómicos que ya usa `ReviewService` al borrar una reseña).
- Se **conservan**: pedidos, pagos y la biblioteca (para la anonimización da igual; la fila del usuario ya no identifica a nadie).
- Si es estudio: sus juegos **PUBLISHED → DELISTED**; sus **DRAFT** se borran (si alguno tuviera filas que lo impidan, se pasa a DELISTED en vez de fallar). Los compradores los siguen teniendo y jugando (comprobar que la biblioteca muestra juegos DELISTED; si no, arreglarlo).
- Correo final «Tu cuenta se ha eliminado» al email que tenía **antes** de anonimizar (el evento lleva el email y el idioma capturados en la transacción; se envía AFTER_COMMIT como el resto).

### Ficha de un juego retirado (DELISTED) — decidido por el usuario: «como Steam»

Hoy `CatalogService.getBySlug` solo devuelve juegos PUBLISHED, así que la ficha de un juego retirado da 404 incluso a quien lo compró. Cambia para **todos** los DELISTED (los de estudios eliminados y los que retira el admin a mano):

- **Quien lo tiene en su biblioteca** (y su developer, y el admin) puede abrir la ficha. Aviso «Ya no está a la venta», sin botón de compra ni de wishlist, y si el estudio está eliminado, «Estudio eliminado» como autor.
- **Cualquier otro** (incluido sin sesión) sigue recibiendo 404, igual que hoy. No aparece en catálogo, búsquedas ni portada.
- *(Decisión de Claude)* En esa ficha se ven las reseñas, y el dueño puede escribir o editar la suya (en Steam se puede reseñar un juego retirado que tienes). `ReviewService` hoy filtra PUBLISHED: se amplía con la misma regla de acceso.
- Carrito, wishlist y checkout no cambian: `StorefrontRules.requireAcquirable` sigue exigiendo PUBLISHED.
- En la biblioteca, la tarjeta sigue enlazando a la ficha y muestra el developer (añadirlo a `LibraryEntryResponse`), con «Estudio eliminado» cuando toque.

### Cómo se muestran los eliminados

- En la API, el nombre del developer de un juego y el autor en vistas de admin salen como `null` junto a un indicador `deleted: true` (o equivalente); el frontend pinta «Estudio eliminado» / «Usuario eliminado» en es/en/it. No se guarda texto traducido en la BD.
- No hay vista de pedidos para el admin y no se crea ninguna. «Usuario eliminado» / «Estudio eliminado» solo se pinta en las vistas que ya existen (p. ej. la columna developer de Admin → Juegos).
- *(Decisión de Claude, tras la revisión)* Un juego de un estudio eliminado **no se puede volver a publicar**: `GameManagementService.moderate` responde 409 `game.developerDeleted` al pasar de DELISTED a PUBLISHED (texto en es/en/it). Así ningún juego vuelve a la tienda sin dueño.
- Las cuentas eliminadas **no aparecen** en la lista de usuarios del admin por defecto (filtro opcional para verlas, a criterio del builder).

## Tarea programada

- `AccountDeletionScheduler` con `@Scheduled(cron = "${app.account.deletion-cron}")` (por defecto una vez al día, y `@EnableScheduling` ya existe en `SteamConfig`), y **también al arrancar** (`ApplicationReadyEvent`), porque Render free duerme y el cron puede no tocar nunca con el servidor despierto.
- Procesa las cuentas con `deletion_scheduled_at <= ahora` y `deleted_at IS NULL`, por lotes, una transacción por cuenta. Un fallo en una se registra en el log y sigue con las demás (como `RefreshTokenPurgeScheduler`).
- Se usa el `Clock` inyectable que ya usa el proyecto para poder probarlo.
- Puede eliminar **tarde** (horas, si el servidor dormía), **nunca antes** de la fecha.

## API

| Endpoint | Quién | Comportamiento |
|---|---|---|
| `POST /api/users/me/deletion` `{ password }` | Cualquier usuario con sesión | Contraseña mala → 400/401 como en el resto del proyecto (`auth.badCredentials` o equivalente). Último admin → 409 `user.lastAdmin`. Si va bien: en pausa, `requested_by = SELF`, sesiones cortadas, correo, **204**. El frontend cierra la sesión. |
| `POST /api/auth/recover-account` `{ login, password }` | Público (permitAll) | Contraseña buena + baja pendiente pedida por **SELF** → cancela la baja y devuelve `AuthResponse` (entra). Pedida por ADMIN → 403 `auth.accountDeletionByAdmin`. Sin baja pendiente o contraseña mala → mismo error que el login (no revelar más). Con el límite por IP que ya existe en `/api/auth/**`. |
| `DELETE /api/admin/users/{id}` | ADMIN | Programa la baja con `requested_by = ADMIN`. Sobre sí mismo → 400 `user.cannotDeleteSelf`. Último admin → 409 `user.lastAdmin`. Ya pendiente → idempotente (devuelve el estado). Ya eliminada → 404 o 409 a criterio del builder. Devuelve `UserResponse` actualizado. |
| `POST /api/admin/users/{id}/deletion/cancel` | ADMIN | Cancela cualquier baja pendiente. Idempotente. Devuelve `UserResponse`. |
| `POST /api/auth/login` | — | Contraseña buena y baja pendiente → **403** con `code`: `auth.accountPendingDeletion` (SELF, incluye la fecha `deletionScheduledAt` en el ProblemDetail para mostrarla y ofrecer «Recuperar mi cuenta») o `auth.accountDeletionByAdmin` (ADMIN, con la fecha, sin recuperar). Contraseña mala → 401 como hoy. Cuenta eliminada → su username/email ya no existen: 401 como hoy. |
| `POST /api/auth/refresh` | — | Una cuenta en pausa no puede renovar (sus refresh tokens se revocaron al pausarla; comprobar igualmente). |

- `UserResponse` añade `deletionScheduledAt`, `deletionRequestedBy` (y lo que haga falta para pintar el estado en el panel).
- Orden del 403 de verificación de email frente al de baja pendiente: a criterio del builder (una cuenta sin verificar no puede pedir su baja porque no puede entrar).

### Regla del último admin

- Se comprueba al **programar** una baja (propia o por el admin) de una cuenta con rol ADMIN.
- Para que dos admins no se eliminen a la vez el uno al otro y quede cero: bloqueo pesimista de **todas las filas de admins que cuentan** (en orden de id) antes de contar, dentro de la misma transacción. Encaja con los bloqueos pesimistas que ya hay (D33, D54).
- `updateRoles` y `setEnabled` hoy solo protegen al propio admin; aplicar la misma regla («no dejar cero admins que cuentan») también ahí, ya que es la misma garantía.
- Red de seguridad conocida: `AdminAccountInitializer` crea el admin de `ADMIN_USERNAME`/`ADMIN_EMAIL` al arrancar si no existe. Si ese admin se eliminara, al reiniciar volvería a crearse con `ADMIN_PASSWORD`. Se deja así y se documenta.

## Correos (es/en/it, `MailTexts` + `EmailTemplates`, sin botones de vuelta a la tienda)

1. **Baja programada por ti**: «Tu cuenta se eliminará el {fecha}. Si cambias de idea, inicia sesión antes de esa fecha y pulsa Recuperar mi cuenta.»
2. **Baja programada por un administrador**: «Un administrador ha programado la eliminación de tu cuenta para el {fecha}. Si crees que es un error, contesta a este correo.»
3. **Baja cancelada** (por el admin o por el dueño): «Tu cuenta vuelve a estar activa.»
4. **Cuenta eliminada**: «Tu cuenta y tus datos personales se han eliminado. Conservamos los pedidos por obligación legal, sin datos que te identifiquen.»

Todos AFTER_COMMIT + `@Async("mailExecutor")` con los reintentos ya existentes. La fecha formateada en el idioma de la cuenta.

## Frontend

- **Ajustes → «Eliminar mi cuenta»** al final, en una zona de peligro: diálogo que explica qué pasa (14 días, se puede recuperar entrando, qué se pierde: reseñas, amigos, wishlist, carrito; se conservan los pedidos sin datos personales). Para un estudio: «tus juegos siguen a la venta hasta esa fecha; después se retiran de la tienda y quien los compró los conserva». Campo contraseña + botón rojo. Al confirmar: aviso con la fecha y cierre de sesión. Error de último admin con su mensaje.
- **Login**: con `auth.accountPendingDeletion` → «Tu cuenta se eliminará el {fecha}» + botón **«Recuperar mi cuenta»** (llama a `recover-account` con lo escrito y entra). Con `auth.accountDeletionByAdmin` → «Un administrador ha programado la eliminación de tu cuenta para el {fecha}», sin botón.
- **Admin → Usuarios**: botón «Eliminar» (no en su propia fila) con confirmación; insignia «Se elimina el {fecha}» (y si la pidió el dueño o el admin); botón «Cancelar eliminación».
- «Estudio eliminado» en ficha y tarjetas de juegos de un estudio eliminado (visible en la biblioteca del comprador); «Usuario eliminado» donde aparezca un autor eliminado en el panel de admin.
- Textos nuevos en es/en/it en `i18n/messages.ts`.

## Tests

- **Backend**: programar baja (propia con contraseña buena/mala, por admin), regla del último admin (incluido el caso concurrente o, como mínimo, que el bloqueo se toma), login en pausa SELF/ADMIN, recuperar (SELF sí, ADMIN no, contraseña mala igual que login), cancelar por admin, tarea programada con `Clock` (no antes de la fecha, sí después, una cuenta que falla no frena las demás), anonimización completa (username/email liberados y reutilizables en un registro nuevo, reseñas borradas y contadores recalculados, amistades/carrito/wishlist/tokens fuera, pedidos y biblioteca conservados, juegos del estudio DELISTED y borradores borrados, el comprador sigue viendo el juego en su biblioteca), correos (los cuatro, en los tres idiomas), test de V13 en `FlywayMigrationTest`. JaCoCo ≥ 80 % de líneas como siempre.
- **Frontend**: Ajustes (diálogo, contraseña, cierre de sesión), login con los dos códigos y «Recuperar mi cuenta», panel de admin (eliminar, insignia, cancelar), «Estudio eliminado».
- **Postman**: pedir la baja de una cuenta de prueba, login 403, recuperar, baja por admin y cancelar.

## No objetivos

- Exportar los datos del usuario (portabilidad RGPD).
- Eliminación inmediata sin plazo (ni siquiera para el admin).
- Borrar físicamente filas de `users`.
- Cambiar cómo funciona `enabled`.

## Anti-objetivos

- Que alguien pierda un juego que compró.
- Quedarse sin ningún admin.
- Que una sesión robada o abierta baste para borrar una cuenta.
- Revelar por el login o `recover-account` si una cuenta existe o está en pausa a quien no tiene la contraseña.
- Que la eliminación ocurra **antes** de la fecha anunciada.

## Ejecución (preferencias del usuario)

- Plan con `superpowers:writing-plans` en `docs/superpowers/plans/2026-10-21-eliminar-cuentas.md`.
- Opus planifica y revisa; un subagente Sonnet construye en su worktree; revisión de seguridad antes de fusionar.
- Commits por función, en español, **sin Co-Authored-By**.
- Al cerrar: `handoff.md` (decisión D55), README **en italiano**, `.env.example`, `render.yaml` si hace falta, Obsidian y tablero.
- Un solo push al final. No hacer push el día de la defensa.
