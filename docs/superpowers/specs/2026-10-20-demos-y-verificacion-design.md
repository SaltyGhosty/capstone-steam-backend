# Demos jugables y verificación de email: diseño

## Context
Dos mejoras pedidas por el usuario el 2026-10-20, con el capstone ya desplegado (Render) y el plan A-G cerrado:
1. **Demos**: una sección para probar algunos juegos dentro de la página antes de comprarlos.
2. **Verificación de email**: hoy no existe; al registrarse se entra directamente y llega un correo de bienvenida. El usuario quiere el flujo real: **crear cuenta → verificar el email → acceder a la cuenta**.

Son dos trabajos independientes (frontend puro y backend+frontend). Se pueden construir en paralelo, cada uno en su worktree (preferencia del usuario: Opus planifica, subagentes Sonnet construyen; commits sin co-author, un commit por función).

---

## Parte 1: demos jugables

### Decidido por el usuario
- Minijuegos **propios**, inspirados en juegos del catálogo (no demos reales ni juegos de terceros).
- Se juegan **en la página**.
- Cuatro demos: **Celeste** (plataformas), **Vampire Survivors** (sobrevivir), **Rocket League** (fútbol 2D), **Balatro** (cartas).
- Sitio: **sección nueva «Demos»** en el menú **y** botón **«Probar demo»** en la ficha de esos juegos; los dos abren el mismo reproductor.
- Al terminar: solo «¿Te gustó?» + **Añadir al carrito**. **No se guarda nada** (sin puntuaciones, sin ranking, sin backend).

### Decisiones de Claude (el usuario no las ha confirmado)
- Cualquiera puede jugar, también sin cuenta (probar antes de comprar).
- Solo frontend: un componente por minijuego con `<canvas>` y `requestAnimationFrame`, **sin librerías nuevas** y **sin cambios de backend ni de BD**.
- Qué juego tiene demo: un registro fijo en el frontend por *slug* (`celeste`, `vampire-survivors`, `rocket-league`, `balatro`). Si el juego no está publicado o no existe, su demo no aparece.
- Arte simple con la paleta de ARCADE; nada de sprites, música ni logos de los juegos reales.
- Móvil: Balatro con toques; los otros tres muestran «Demo disponible en ordenador» (necesitan teclado).
- Al acabar: si el usuario ya tiene el juego → «Ya lo tienes» (enlace a biblioteca); si está en el carrito → «En el carrito · Ir a pagar». Reutilizar la lógica de botones de la ficha (`/library/status`).
- Textos en es/en/it en `frontend/src/i18n/messages.ts`.

### Los cuatro minijuegos (1-2 min cada uno)
| Demo | Mecánica | Controles | Fin |
|---|---|---|---|
| Celeste | Plataformas: saltar y *dash* aéreo para subir 3 pantallas hasta la cumbre; caer reinicia la pantalla | ← → mover, Espacio saltar, X dash | Llegar a la cumbre (muestra tiempo y caídas) |
| Vampire Survivors | Vista cenital; el personaje ataca solo, el jugador solo se mueve; oleadas cada vez más densas | Flechas / WASD | Aguantar 60 s o morir |
| Rocket League | Campo 2D cenital; coche con inercia que empuja el balón contra un coche IA sencillo | Flechas, Espacio turbo | Partido de 60 s, marcador |
| Balatro | 5 manos de póquer: elegir hasta 5 cartas de 8, descartar 2 veces; puntuación por jugada × multiplicador | Ratón / toque | Superar el objetivo o quedarse sin manos |

### Piezas
- Ruta `/demos` (página con tarjetas: portada del juego, mecánica, «Jugar demo») y enlace en el menú lateral (`Layout.tsx`).
- `DemoPlayer`: diálogo a pantalla grande con el canvas, controles, pausa al perder el foco, cerrar con Esc y pantalla final común.
- Botón «Probar demo» en `GameDetail.tsx` cuando el slug está en el registro.
- Cada minijuego: un módulo con lógica pura (estado + `step(dt, input)`) separada del dibujo, para poder testearla con Vitest sin canvas.

### Tests
- Vitest: lógica pura de cada minijuego (salto y dash, colisiones, temporizador de 60 s, gol, puntuación de manos de póquer), registro de demos por slug, página `/demos` y botón «Probar demo» solo en juegos con demo.

---

## Parte 2: verificación de email obligatoria

### Decidido por el usuario
- Flujo: **crear cuenta → correo con enlace → pulsar → cuenta verificada → entra ya con su sesión iniciada** (sin volver a escribir la contraseña).
- **Un solo correo**: «¡Bienvenido a ARCADE! Confirma tu email», que sustituye al de bienvenida actual.
- Sin verificar **no se puede iniciar sesión**.

### Decisiones de Claude (el usuario las ha visto en el resumen y las ha aceptado)
- **Campo propio** `users.email_verified`, separado de `enabled`: «pendiente» y «desactivada por un admin» no se mezclan (si se reutilizara `enabled`, un admin podría «reactivar» sin verificar y `searchPlayers`/amigos se confundirían).
- **V12** (`common/`, SQL portable, modelo V5 + V10):
  - `ALTER TABLE users ADD COLUMN email_verified BOOLEAN DEFAULT FALSE NOT NULL;` y `UPDATE users SET email_verified = TRUE;` → **todas las cuentas existentes quedan verificadas** (demo, reseñadores, estudios, admin).
  - Tabla `email_verification_tokens(id, user_id FK ON DELETE CASCADE, token_hash VARCHAR(64) UNIQUE, expires_at, created_at, used_at)` + índice por `user_id`.
- **Token**: 32 bytes aleatorios en Base64URL; en BD solo el SHA-256. Extraer `newRawToken()` y `hash()` de `RefreshTokenService` (hoy `private static`) a una utilidad común `auth/TokenHashing` y usarla en los dos sitios. **Un solo uso, caduca a las 24 h** (`EMAIL_VERIFICATION_TTL`, por defecto `P1D`); al emitir uno nuevo se anulan los anteriores del usuario.
- **Interruptor** `app.auth.email-verification-required` (`EMAIL_VERIFICATION_REQUIRED`, por defecto **true**; dev y prod lo dejan en true). Solo el `application.yml` de tests lo pone a false para que las 14 clases que se registran y usan el token al momento sigan igual; una clase nueva lo activa con `@TestPropertySource` y prueba el flujo entero.

### API
| Endpoint | Comportamiento |
|---|---|
| `POST /api/auth/register` | Con verificación: **201** con `RegisterResponse { id, email, verificationRequired: true }` (sin tokens, sin refresh en BD; el `id` lo necesitan el seed y Postman para el endpoint de admin). Sin verificación: igual que hoy (`AuthResponse`) y la cuenta se crea ya verificada. Publica `UserRegisteredEvent` con el token en claro para el correo. |
| `POST /api/auth/verify-email` `{ token }` | Token válido, sin usar y no caducado → marca `used_at`, `email_verified = true` y devuelve **`AuthResponse`** (sesión iniciada). Token usado, caducado o inexistente → **400** `auth.verificationInvalid` (el frontend ofrece reenviar). Cuenta ya verificada con token usado → 400 igual (no revela más). |
| `POST /api/auth/resend-verification` `{ email }` | **Siempre 204** (no revela si la cuenta existe ni si ya está verificada). Si existe y no está verificada: anula tokens previos, emite uno nuevo y envía el correo. Límite **3 por hora por email** (en memoria, patrón de `ChatRateLimiter` con clave `String`) → 429 `auth.resendRateLimited`. |
| `POST /api/auth/login` | Tras autenticar la contraseña **con éxito**, si `!emailVerified` → **403** `auth.emailNotVerified` (con un campo `code` en el ProblemDetail para que el frontend lo distinga de `error.disabled`). Contraseña mala → 401 como hoy, esté verificada o no. |
| `POST /api/admin/users/{id}/verify-email` | Admin marca la cuenta como verificada (soporte y seed). Idempotente. |

- **Cuentas que nunca se verifican** *(decisión de Claude, tras la revisión del plan)*: que nadie pueda «secuestrar» un email o un usuario.
  - **Corregido tras la revisión de seguridad (2026-10-20).** La primera versión («mismo email sin verificar → la cuenta se sustituye») permitía robar cuentas: el atacante se registraba con el email de la víctima, la víctima pulsaba su enlace nuevo y la cuenta quedaba verificada con la contraseña del atacante. Regla nueva:
  - **Cada enlace lleva los datos de su registro**: `email_verification_tokens` guarda `username`, `password_hash`, `display_name`, `locale` y `developer` de la petición que lo generó. **Al verificar, se aplican a la cuenta los datos de ese enlace** (no los del último registro) y se anulan todos los demás enlaces del usuario. El correo nombra la cuenta: «Confirma la cuenta «<usuario>»», así que quien no la pidió lo ve.
  - Registrarse con un **email** de una cuenta **sin verificar** → 201 igual que un registro nuevo, **sin tocar la fila** de `users` ni anular enlaces anteriores: solo se emite un enlace nuevo con los datos de esta petición. Con el mismo límite que el reenvío (3 por hora por email).
  - Registrarse con un **usuario** que ocupa una cuenta **sin verificar creada hace más de 24 h** (se mide desde `created_at`, no desde el último enlace, para que reenviar no retenga el nombre) → esa cuenta se borra y el nombre queda libre, **solo si no tiene datos** (pedidos, biblioteca, reseñas, juegos, amistades, refresh tokens) **ni más roles que USER/DEVELOPER**. Si no se cumple → 409 `auth.usernameTaken`.
  - Al verificar, si el usuario del enlace lo ha cogido otra cuenta mientras tanto → 409 `auth.usernameTaken` (el enlace sigue sin usarse).
  - Una cuenta verificada nunca se toca: email o usuario repetidos → 409 como hoy (no se revela más que ahora).
  - Una cuenta **desactivada** por un admin que verifica su email queda verificada, pero recibe 403 `error.disabled` y **no** se le emiten tokens.
  - Registro, sustitución y verificación leen la fila de `users` con bloqueo pesimista para que no se pisen.
  - El login de una cuenta sin verificar se bloquea **siempre**, esté el interruptor como esté (con el interruptor apagado, las cuentas nuevas se crean ya verificadas).
- `SecurityConfig`: `verify-email` y `resend-verification` en `permitAll`.
- `UserResponse` expone `emailVerified`. `UserRepository.searchPlayers` y amigos (`FriendService.isPlayer`: solicitudes y perfiles) ignoran cuentas sin verificar.
- `AdminAccountInitializer` crea al admin ya verificado.
- Refresh: un usuario sin verificar no tiene refresh tokens; no hace falta tocar `RefreshTokenService` salvo la extracción de `TokenHashing`.

### Correo
- `MailTexts`: los textos de bienvenida pasan a «Bienvenido + confirma tu email» en ES/EN/IT (asunto «¡Bienvenido a ARCADE! Confirma tu email» y equivalentes), botón «Verificar mi email», nota «El enlace caduca en 24 horas». El compilador obliga a los tres idiomas.
- `EmailTemplates.welcome` recibe la URL de verificación: `brand.link("/verify-email?token=" + token)`. Sin `storeUrl`, sin botón (como hoy) → registrar aviso en el log.
- `UserRegisteredEvent` + un evento para el reenvío (o el mismo con un flag) llevan el token en claro; `MailListener` (AFTER_COMMIT + `@Async("mailExecutor")`) los envía.
- **Envío fiable desde Render** (decidido por el usuario: Gmail + reintentos). En los logs de producción del 2026-10-20, Gmail falló 2 de 3 envíos con `MailConnectException: Couldn't connect to host smtp.gmail.com:587; timeout 5000`. Cambios en `EmailService` / `application.yml`, para **todos** los correos:
  - `spring.mail.properties.mail.smtp.connectiontimeout` y `timeout` de 5 s a **15 s** (configurable con `MAIL_TIMEOUT`).
  - **Hasta 3 intentos** con pausa creciente (p. ej. 2 s y 6 s) cuando el fallo es de conexión (`MailSendException` con `MailConnectException` o `SocketTimeoutException` como causa). Los errores de autenticación o de destinatario no se reintentan. Ocurre en el hilo `mail-*`, así que no frena la petición. Log con el número de intento, sin datos sensibles (email enmascarado como hoy).
  - El botón «Reenviar correo» cubre el caso de que fallen los 3 intentos.
  - Tests con un `JavaMailSender` simulado que falla 2 veces y acierta a la tercera, y otro que falla siempre (3 intentos y no lanza, D36).
- **Correo desactivado** (sin `MAIL_USERNAME`, desarrollo): el enlace de verificación se escribe en el log a INFO para poder probarlo. Con correo activo nunca se escribe el token en el log.

### Frontend (`StoreContext`, `pages/Auth.tsx`, `App.tsx`, `i18n/messages.ts`)
- **Registro**: si la respuesta trae `verificationRequired` → ir a `/check-email?email=…` («Revisa tu correo: te hemos enviado un enlace a x@…» + «Reenviar correo» + «¿Email equivocado? Crea la cuenta de nuevo»). Si trae tokens (verificación desactivada) → como hoy.
- **`/verify-email?token=…`**: al montar llama a `verify-email`; éxito → `startSession(...)`, aviso «¡Email verificado!» y navega a `/` (o `/developer` si es estudio). Error → «El enlace no es válido o ha caducado» + campo email y «Reenviar correo». Evitar la doble llamada de React en desarrollo (guardar la promesa).
- **Login**: si el error es `auth.emailNotVerified` → mensaje + botón «Reenviar correo» con el email/usuario escrito (si escribió usuario, el reenvío necesita el email: pedirlo en un campo).
- Panel de admin: insignia «Sin verificar» y botón «Verificar».
- Cambiar `auth.created` («Te hemos enviado un correo de bienvenida») y añadir las claves nuevas en es/en/it.

### Seed, Postman y tests que dependen de «registrarse y entrar»
- **`scripts/seed-demo.mjs`**: nuevo argumento `--admin-password` (o `ADMIN_PASSWORD` del entorno). `account()` pasa a: login → 401 → registro → si la respuesta pide verificación, login de admin + `POST /api/admin/users/{id}/verify-email` → login. Tratar el **403 `auth.emailNotVerified`** como «falta verificar», no como error (hoy el `catch` solo acepta 401 y abortaría). Sin contraseña de admin y con verificación activa → mensaje claro y salir.
- **Postman**: mover «Login admin» al principio (ya requiere `adminPassword`), y tras cada registro: comprobar 201 + `verificationRequired`, «Login sin verificar → 403», verificar con el endpoint de admin y hacer login para obtener los tokens (`devToken`, `playerToken`, `player2Token`). Añadir peticiones de `resend-verification` (204) y `verify-email` con token falso (400).
- **Backend**: `src/test/resources/application*.yml` con `email-verification-required: false`; nueva `EmailVerificationIntegrationTest` (registro sin tokens, login 403 solo con contraseña correcta, verify → tokens, token reutilizado/caducado → 400 con `Clock`, resend 204 y 429, admin verify, cuentas sin verificar fuera de `searchPlayers`); `MailEventIntegrationTest` y `EmailTemplatesI18nTest` ajustados al correo nuevo; test de V12 en `FlywayMigrationTest` (cuentas existentes verificadas).
- **Frontend**: tests de `/check-email`, `/verify-email` (éxito y error), login con `emailNotVerified` y registro correcto.

### No objetivos
- Una tarea programada que borre cuentas sin verificar (solo se liberan al reutilizar su email o usuario, ver arriba).
- Cambiar a un proveedor de correo por HTTP (Brevo, Resend): descartado por ahora; si los reintentos no bastan, es el siguiente paso.
- Cambiar el email de una cuenta ya creada.
- Recuperar contraseña (otro flujo; queda fuera).

### Anti-objetivos
- Revelar qué emails/usuarios existen (registro, login y reenvío no deben filtrarlo más que hoy).
- Romper el seed o Newman contra producción.
- Guardar el token en claro en BD o en logs con el correo activo.

---

## Ejecución
- Al aprobarse: copiar este diseño a `docs/superpowers/specs/2026-10-20-demos-y-verificacion-design.md` (commit) y escribir un plan por parte con `superpowers:writing-plans` (`docs/superpowers/plans/2026-10-20-demos.md` y `...-verificacion-email.md`), como en los bloques B-G.
- Dos subagentes Sonnet en paralelo, cada uno en su worktree: **demos** (solo `frontend/`) y **verificación** (backend + frontend + seed + Postman). Solo choca `i18n/messages.ts` y `App.tsx`/`Layout.tsx`: se fusiona primero verificación y luego demos, resolviendo esos dos ficheros a mano.
- Migración reservada: **V12** para verificación; demos no toca la BD.
- Commits por función, en español, **sin Co-Authored-By**. Revisión de Opus antes de cada fusión.
- Al cerrar: `handoff.md` (decisiones D53 demos y D54 verificación), README (API, variables `EMAIL_VERIFICATION_REQUIRED`, `EMAIL_VERIFICATION_TTL`), `.env.example`, `render.yaml` si hace falta, Obsidian (notas nuevas + `Pendientes`, `Línea de tiempo`, defensa) y tarjetas en el tablero.
- Despliegue: un solo push al final (cada deploy de Render tarda ~15 min). El seed **no** se relanza en producción: V12 marca las cuentas actuales como verificadas.

## Verificación
1. `.\mvnw.cmd -q -B clean verify` → exit 0, todos los tests (hoy 332) más los nuevos, cobertura ≥ 80 % de líneas.
2. `cd frontend; npx vitest run` y `npm run build`.
3. Backend local (8081) con `.env` real: registrar con un alias `+` del Gmail del usuario → llega un solo correo en el idioma elegido → el enlace abre `/verify-email` y entra con sesión iniciada; login antes de verificar → 403 + reenviar; enlace reutilizado → «no válido».
4. Newman contra el local con `--env-var adminPassword=…` → 0 fallos.
5. `node scripts/seed-demo.mjs --admin-password …` contra el local en una BD nueva funciona de principio a fin.
6. Navegador (Playwright) en local: `/demos`, jugar las cuatro demos, pantalla final con «Añadir al carrito», botón «Probar demo» en las fichas, aviso en móvil; flujo de registro → `/check-email` → `/verify-email`.
7. Tras el push: producción con un registro real (alias `+`) y una demo jugada en `https://capstone-steam-web.onrender.com/demos`.
