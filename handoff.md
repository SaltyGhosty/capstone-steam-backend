# HANDOFF: Capstone Steam (backend)

> Documento vivo del proyecto. **Hay que actualizarlo al cerrar cada bloque**: el estado, el registro de progreso y las decisiones.
> Para retomar el trabajo en una sesión nueva: "lee `handoff.md` y continúa con el siguiente bloque pendiente".

---

## 1. Resumen
Proyecto final (capstone) del curso Full Stack de Epicode: una plataforma de videojuegos inspirada en Steam, con escaparate, biblioteca, carrito, wishlist, reseñas y compras simuladas. Este repositorio es el **backend** (con el frontend en `frontend/`).

| Campo | Valor |
|---|---|
| Paquete base | `com.capstone.steam` |
| Artifact | `capstone-steam-backend` |
| Estado actual | **✅ Bloques 0 a 16, bloque A (cierre) y los seis extras B a G, más la pasada de arreglos tras la revisión: backend (518 tests, JaCoCo exige el 80 % de líneas, Swagger) + frontend React en es/en/it (184 tests en 25 archivos) + CI + despliegue preparado. Migraciones V1 a V13. Eliminar cuentas (propia y por el admin, con 14 días de pausa) hecho. Último trabajo: Eliminar cuentas (2026-10-22), con su revisión final y Newman en verde (96 peticiones, 155 comprobaciones)** |
| Siguiente paso | En GitHub: **https://github.com/SaltyGhosty/capstone-steam-backend** (privado). <br>El plan `docs/superpowers/specs/2026-10-19-cierre-y-extras-design.md` está **entero en `main`** (bloques A a G). Cloudinary, OpenRouter y los correos en/it ya están probados con las cuentas reales. **Desplegado en Render** (2026-10-19): backend https://capstone-steam-api.onrender.com y frontend https://capstone-steam-web.onrender.com. Las rutas profundas van con la regla *Rewrite* `/*` → `/index.html` (puesta a mano por el usuario el 2026-10-20) y responden 200; el `404.html` del `postbuild` queda de respaldo |
| Última actualización | 2026-10-22 |

### Entorno local (máquina del usuario, Windows 11)
- **JDK:**
  - JDK 25: `C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot` (instalado con winget).
  - El JDK 21 sigue siendo el de por defecto en el PATH, así que antes de `mvnw` hay que hacer:
    `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"`
- **Maven:** no hay `mvn` global; se usa `.\mvnw.cmd` (Maven 3.9.16).
- **Base de datos:** PostgreSQL 18 como servicio de Windows (`postgresql-x64-18`) en `localhost:5432`.
  - Usuario `postgres`; la contraseña está en `.env`.
  - La BD `capstone_steam` ya está creada y tiene aplicadas las migraciones V1 a V4 (no se ha vuelto a tocar).
  - La BD `capstone_steam_demo` (la del `.env`) tenía aplicadas V1 a V7 el 2026-10-19 (comprobado en `flyway_schema_history`); V8 a V10 se aplicarán al arrancar el backend nuevo. En el bloque 15 los 31 usuarios que había quedaron con `locale = 'es'`.
- **`.env`:** ya existe (git lo ignora), con `DB_PASSWORD` y un `JWT_SECRET` aleatorio. Desde el 2026-10-09 también tiene `MAIL_USERNAME` y `MAIL_APP_PASSWORD` de Gmail (envío real probado, bloque 14); `MAIL_FROM` es igual a `MAIL_USERNAME` o está vacío. Sigue vacío `ADMIN_PASSWORD`, así que en `dev` todavía no hay admin. Para crearlo basta con añadir `ADMIN_PASSWORD=...` y arrancar la app. `.env.example` ya incluye `ADMIN_*` y `SERVER_PORT`.
- **Datos de demo en `dev`**: los carga `node scripts/seed-demo.mjs` en la BD `capstone_steam_demo` (ver bloque 12). Cuentas con contraseña `password123`: `demo_player`, `reviewer_ana`, `reviewer_leo`, `reviewer_sam`, `lucia_gamer`, `marco_rpg`, los 60 reseñadores `rev_es_01`… `rev_en_01`… `rev_it_01`… (20 por idioma) y 49 estudios (`team_cherry`, `cdprojektred`, `fromsoftware`…); `admin` / `Admin12345!`. El catálogo demo tiene 60 juegos (59 publicados y un borrador).
- **Tests de MockMvc en Boot 4.1:** `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`. En el perfil `test`, el admin es `admin` / `Admin12345!`.
- **Puerto 8080:** lo ocupa otra app Java del usuario (un JDK 25.0.3 en `AppData\Local\Programs`), así que no hay que cerrarla. Para arrancar esta app se usa otro puerto: `$env:SERVER_PORT="8081"` (o se añade `SERVER_PORT` a `.env`).
- **Docker:** no está instalado. `docker-compose.yml` queda como opción y usa el puerto **5433**.
- **Git:** repo propio en la carpeta del proyecto (antes estaba dentro del repo del home).

### Frontend (hecho el 2026-09-30, en `frontend/`; ver `frontend/README.md`)
- **Cómo se hizo:** se partió del código exportado de Figma Make (`D:\Video Game Website`) y se quitaron los plugins de Figma. Stack: React 19, Vite 8, Tailwind v4, React Router 7 y npm (no hay pnpm instalado). La interfaz está en español, inglés e italiano (bloque 16) y los precios en €.
- **Cobertura de la API:** se usan todos los endpoints (tienda, carrito, checkout y pago, biblioteca, wishlist, reseñas, perfil, panel de developer y panel de admin).
- **Proxy de Vite:** `/api` → `localhost:8081`. El proxy **quita la cabecera `Origin`**: si no, el CORS del backend rechaza con 403 los POST cuando Vite no corre justo en el 5173.
- **Puertos:** el 5174 lo ocupa Agent Office; el 5173 lo usaba `sprout-capstone`, que se cerró a petición del usuario el 2026-09-30.
- **Probado en el navegador con Playwright:** portada, catálogo con filtros (también en móvil), login, pago rechazado (tarjeta `0000`) → 402, pago correcto → PAID, ficha con el formulario de reseña y panel de admin.
- **Datos de demo modificados por las pruebas:** `demo_player` ahora tiene Neon Drift en la biblioteca (pedido #11 PAID, #10 FAILED), el carrito vacío, una reseña de 5★ en Neon Drift y 55 min jugados. El juego de prueba «QA Test Game» (id 17) se creó y se borró. El 2026-09-30 se cambiaron por la API las portadas de los 8 juegos (de picsum a Unsplash/Wikimedia acordes a cada juego; créditos en el README); ahora están en `version` 2.
- **Datos de prueba del correo en `capstone_steam_demo`** (2026-10-09, contraseña `password123`): `mailtest_arcade` (pedido #22 PAID, Baldur's Gate 3; su correo falló por credenciales) y `mailtest_arcade2` (pedido #23 PAID, Cyberpunk 2077 + Celeste, 63,98 €). Sus emails son direcciones `+alias` de la cuenta del usuario. Se pueden borrar o dejar; no afectan al resto de la demo.
- **Diseño original en Figma Make:** https://www.figma.com/make/dCxjFlqDGURAtW8CbXbMUu/Video-Game-Website (privado). El código exportado se guardó en `D:\Video Game Website`.
- **Plugins instalados** (ámbito user, marketplace oficial): `frontend-design`, `figma`, `context7`, `playwright`, `modern-web-guidance` y `playground`. La skill `dataviz` ya venía incluida.
- **La API espera el frontend en `http://localhost:5173`** (CORS). El backend corre en el 8081 y la colección de Postman sirve de contrato.

### Limitaciones conocidas
- **El refresh token vive en `localStorage`** (como el de acceso): un XSS podría leerlo. Una cookie `httpOnly` sería más segura, pero entre dominios (Vercel y Render) complica CORS y SameSite. Decisión de la spec (bloque F).
- **El límite del chatbot es en memoria** (`ChatRateLimiter`, 20 mensajes por hora y usuario): se pierde al reiniciar y no sirve con varias instancias del backend.
- **Las etiquetas «Extremadamente positivas/negativas» necesitan 500 reseñas** y no salen en la demo (el juego con más reseñas tiene 60). Se explica en la defensa.
- La purga de `refresh_tokens` (D52) correría en cada instancia si hubiera varias: es idempotente, solo duplica trabajo.
- Las marcas de revocación del access token (D16) viven en memoria: tras reiniciar el backend, el token de una cuenta desactivada vuelve a valer hasta que caduca (15 min); sus refresh tokens siguen revocados en la BD.
- Las portadas **pegadas como URL** siguen cargándose desde un servidor ajeno, también en el correo (D43). Las subidas a Cloudinary no.
- Amistades: dos solicitudes cruzadas simultáneas dan un 409 genérico porque lo resuelve la UNIQUE del par.

### Pendientes manuales del usuario
- La Postgres free de Render (`capstone-steam-db`) **caduca el 2026-11-18**: pasarla a pago o recrearla y volver a sembrar.
- Regenerar las claves de OpenRouter, Cloudinary y la contraseña de aplicación de Gmail (salieron en el chat), y cambiarlas en `.env` y en Render.
- La contraseña del admin de producción no es `Admin12345!`: la tiene el usuario.
- Hecho el 2026-10-19: pruebas reales de Cloudinary, OpenRouter y correos en en/it; Newman (73 peticiones, 0 fallos); V8 a V10 en `capstone_steam_demo` con el seed relanzado.

### Versiones fijadas
Spring Boot **4.1.1** · jjwt **0.13.0** · Java **25** · Flyway (gestionado por Boot) · H2 2.4 (solo tests).

---

## 2. Requisitos obligatorios (no negociables)
- **Lenguaje y framework:** Java 25 + Spring Boot 4, con Maven (`mvnw`).
- **Seguridad:** Spring Security + JWT (stateless).
- **Persistencia:** Spring Data JPA / Hibernate.
  - **Criteria API** para los filtros avanzados del catálogo.
  - **UPDATE atómicos escritos a mano** (`@Modifying @Query`).
- **Migraciones:** Flyway, con **V1 a V4**:
  - **V1:** usuarios, roles, juegos y géneros.
  - **V2:** biblioteca, carrito, wishlist y reseñas.
  - **V3:** compras, transacciones y estados de pago.
  - **V4:** índices y optimizaciones de búsqueda.
  - V1 a V4 es el mínimo exigido. Además existen **V5 a V11** (ver §5).
- **Correo:** Spring Mail con SMTP de Gmail.
- **Eventos:** Spring Events en fase **AFTER_COMMIT** y `@Async` con un **thread pool propio** para el correo.
- **Tests:** JUnit 5 (Jupiter), Mockito y H2, con pruebas unitarias y de integración.
- **Base de datos:** PostgreSQL, con Docker Compose.

## 3. Decisiones tomadas
| # | Decisión | Origen | Estado |
|---|---|---|---|
| D1 | Roles: USER, DEVELOPER y ADMIN | Usuario | ✅ Cerrada |
| D2 | Pagos simulados (`PaymentGateway` implementada por `SimulatedPaymentGateway`), sin pasarela real | Usuario | ✅ Cerrada |
| D3 | Carrito y wishlist | Usuario | ✅ Cerrada |
| D4 | Paquete `com.capstone.steam` | Usuario | ✅ Cerrada |
| D5 | JWT con jjwt 0.13 (`JwtService`) y un `OncePerRequestFilter` propio (`JwtAuthFilter`) | Propuesta | ✅ Aplicada |
| D6 | Paquetes por feature; DTOs como `record` con mappers manuales | Propuesta | ⏳ Por validar |
| D7 | Flyway con las ubicaciones `common` + `{vendor}`: una V4 para PostgreSQL y otra para H2 | Propuesta | ⏳ Por validar |
| D8 | Tests con H2 en `MODE=PostgreSQL` | Propuesta | ⏳ Por validar |
| D9 | Sin Lombok: getters y setters escritos a mano, y constructor `protected` sin argumentos para JPA | Bloque 2 | ✅ Aplicada |
| D10 | Timestamps con `@CreationTimestamp`/`@UpdateTimestamp` de Hibernate, sin `JpaAuditingConfig` | Bloque 2 | ✅ Aplicada |
| D11 | En `Game`, los contadores (`sales_count`, `review_count`, `positive_count`; antes `rating_sum` y `rating_count`, ver V8) son `insertable=false, updatable=false`: solo cambian con UPDATE atómicos, así que un `save()` nunca pisa ventas o reseñas concurrentes. Estos UPDATE no suben `@Version`; `bulkDiscountForDeveloper` sí la sube | Bloque 2 | ✅ Aplicada |
| D12 | Pedidos: `markPaid` y `closePending(FAILED/CANCELLED)` solo actúan si el estado es `PENDING`. Si devuelven 0 → 409 (evita el doble pago) | Bloque 2 | ✅ Aplicada |
| D13 | Login con username **o** email (`UserRepository.findByLogin`), con el email guardado en minúsculas | Bloque 2 | ✅ Aplicada |
| D14 | Tras una compra, los juegos comprados se quitan también de la wishlist (`deleteByUserIdAndGameIdIn`) | Bloque 2 | ✅ Aplicada |
| D15 | Registro con `accountType` (PLAYER o DEVELOPER). ADMIN nunca se autoasigna: el primer admin lo crea `AdminAccountInitializer` desde `ADMIN_*` y los siguientes los nombra un admin (`PUT /api/admin/users/{id}/roles`) | Bloque 3 | ✅ Aplicada |
| D16 | El JWT lleva el id, el username y los roles, y cada petición se autentica **sin consultar la BD**. Antes, desactivar a un usuario o cambiarle los roles no aplicaba hasta que caducaba su token. **Desde el 2026-10-20 aplica al instante**: `AccessTokenRevocations` guarda en memoria `userId → instante` al desactivar una cuenta o cambiarle los roles; `JwtAuthFilter` rechaza (401) los tokens con `iat` en ese segundo o antes. Sin consultar la BD por petición. Las marcas se olvidan pasada la vida del token (15 min). Queda: se pierden al reiniciar y no se comparten entre instancias | Bloque 3 · 2026-10-20 | ✅ Aplicada |
| D17 | `JwtAuthFilter` no es un `@Component` (evita que se registre dos veces). Un token inválido deja la petición como anónima y el entry point responde 401 | Bloque 3 | ✅ Aplicada |
| D18 | Errores: `GlobalExceptionHandler` extiende `ResponseEntityExceptionHandler` y devuelve ProblemDetail; los errores de validación salen en `errors: {campo: mensaje}`. Los 401 y 403 del filtro los escribe `ProblemResponseWriter` con el `JsonMapper` de Jackson 3 | Bloque 3 | ✅ Aplicada |
| D19 | Las respuestas paginadas usan `PageResponse<T>` (`common/dto`), no `Page` de Spring Data. `hibernate.default_batch_fetch_size=50` evita el N+1 sin paginar en memoria | Bloque 3 | ✅ Aplicada |
| D20 | `JwtService` falla al arrancar si `JWT_SECRET` falta o tiene menos de 32 bytes | Bloque 3 | ✅ Aplicada |
| D21 | **Criteria API** (`GameCriteriaRepository`), cómo está construida: <br>• Los predicados solo se añaden para los filtros presentes y se hacen 2 queries (página y count) con los mismos predicados. <br>• El filtro de géneros es un **subquery `IN`** y no un join con distinct, para no duplicar filas y paginar en la BD. <br>• El precio se compara como `price*(100-discount)` frente a `max*100`, sin dividir. <br>• `minPositivePercent` (antes `minRating`) se comprueba como `positive_count * 100 >= min * review_count`, sin dividir (nunca divide entre 0); ver D47. <br>• Siempre se desempata por `id`. | Bloque 4 | ✅ Aplicada |
| D22 | NULLS LAST con `CASE WHEN x IS NULL THEN 1 ELSE 0 END`: el `Nulls.LAST` de JPA 3.2 se ignoraba con H2 | Bloque 4 | ✅ Aplicada |
| D23 | El slug se genera a partir del título con `Slugs`, y si ya existe se le añade `-2`, `-3`… **No cambia al renombrar el juego** | Bloque 4 | ✅ Aplicada |
| D24 | Estados del juego: <br>• El developer puede hacer DRAFT → PUBLISHED (con al menos 1 género) y PUBLISHED → DRAFT. <br>• Solo el admin puede retirar un juego (→ DELISTED) y volver a publicarlo (DELISTED → PUBLISHED). <br>• Solo se pueden borrar juegos en DRAFT sin ventas. <br>• El PUT acepta `version` y responde 409 si está desactualizada (bloqueo optimista expuesto al cliente). <br>• Un juego ajeno da 403. | Bloque 4 | ✅ Aplicada |
| D25 | Carrito y wishlist: <br>• Se añade y se quita con **PUT/DELETE idempotentes** (repetir no da error). <br>• `StorefrontRules.requireAcquirable` exige que el juego esté PUBLISHED (si no, 404), que no sea tuyo como developer (409) y que no lo tengas ya (409). <br>• Si un juego deja de estar publicado, sigue en el carrito con `available=false` y no cuenta en el total. | Bloque 5 | ✅ Aplicada |
| D26 | Reseñas: <br>• Solo puede reseñar quien tiene el juego en su biblioteca (403) y hay una por usuario y juego (409). <br>• Los contadores se actualizan con `applyReviewDelta(gameId, positiveDelta, countDelta)` en la misma transacción, usando el delta de crear, editar el voto o borrar (antes `applyRatingDelta`; ver D46). <br>• La respuesta se construye **antes** del UPDATE atómico, porque `clearAutomatically` separa las entidades del contexto. <br>• Borran el autor o un ADMIN. | Bloque 5 | ✅ Aplicada |
| D27 | Los listados de usuario (wishlist, biblioteca y reseñas) usan un **orden fijo en el servidor**: solo se aceptan `page` y `size` del `Pageable`, así el cliente no puede ordenar por campos arbitrarios | Bloque 5 | ✅ Aplicada |
| D28 | `@DataJpaTest(showSql = false)`: por defecto `@DataJpaTest` activa `show-sql` e inunda la salida | Bloque 5 | ✅ Aplicada |
| D29 | Checkout en 2 pasos: <br>• **Crear el pedido** congela el carrito: precios snapshot, rechaza juegos no publicados o ya en propiedad, y no vacía el carrito. <br>• **Pagar** vuelve a validar y cobra con `PaymentGateway`. <br>• Si se aprueba: `payment.markSucceeded`, `markPaid` (si da 0 → 409 y rollback), entradas de biblioteca con `order_id`, `incrementSalesCount`, se quitan **solo los juegos comprados** del carrito y la wishlist, y se publica `OrderPaidEvent`. <br>• Si se rechaza: `markFailed`, `closePending(FAILED)`, se publica `PaymentFailedEvent`; la transacción **hace commit** y el controller responde **402**. | Bloque 6 | ✅ Aplicada |
| D30 | Pago simulado (`SimulatedPaymentGateway`, detrás de la interfaz `PaymentGateway`): <br>• Una tarjeta que acaba en `0000` se rechaza siempre. <br>• `PAYMENT_FAILURE_RATE` (0.0 por defecto) permite rechazos aleatorios en demos. <br>• El número de tarjeta **nunca se persiste**. <br>• La referencia es `SIM-<uuid>`. | Bloque 6 | ✅ Aplicada |
| D31 | Un pedido ajeno responde **404, no 403**, para no revelar que existe | Bloque 6 | ✅ Aplicada |
| D32 | Tras un UPDATE atómico con `clearAutomatically`, los proxies LAZY ya no se pueden inicializar: los datos del evento (email, nombre y total) se leen **antes**, y la respuesta se vuelve a consultar al final (`load`) | Bloque 6 | ✅ Aplicada |
| D33 | Pagos simultáneos del mismo pedido. Antes llamaban **dos veces** al gateway (el segundo daba 409 y rollback). Resuelta (2026-10-20): `pay()` lee el pedido con `findOwnForUpdate` (`SELECT … FOR UPDATE`). Un segundo pago concurrente espera al commit del primero, ve el estado final y da 409 **sin llamar a la pasarela** (test con 16 hilos: `charge` se llama 1 vez). Queda para un proveedor real: una *idempotency key* para reintentos tras un timeout de red; el bloqueo dura lo que tarde `charge` | Bloque 6 · 2026-10-20 | ✅ Resuelta |
| D34 | Un componente con 2 constructores (uno solo para tests) necesita `@Autowired` en el que debe usar Spring (visto en `SimulatedPaymentGateway`) | Bloque 6 | ✅ Aplicada |
| D35 | Pool `mailExecutor` (2 a 5 hilos, cola 100, prefijo `mail-`): <br>• Con el pool saturado se usa **CallerRunsPolicy**: el correo se envía en el hilo que publicó el evento, ya después del commit. Es más lento, pero no se pierde. <br>• Al apagar la app se espera hasta 30 s a que salgan los correos en cola. <br>• Todos los `@Async` indican el pool por su nombre (`@Async("mailExecutor")`). | Bloque 7 | ✅ Aplicada |
| D36 | `EmailService` **nunca lanza excepciones**: un fallo de SMTP se registra en el log y no afecta a la operación, que ya hizo commit. Si `MAIL_USERNAME` está vacío, el correo queda **desactivado** y solo se registra en el log, para poder desarrollar sin cuenta de Gmail. Si `MAIL_FROM` está vacío, se usa el usuario SMTP como remitente | Bloque 7 | ✅ Aplicada |
| D37 | El test de correo **no es `@Transactional`** (AFTER_COMMIT necesita commits reales) y usa su propio H2 (`jdbc:h2:mem:mail_events`, con `@TestPropertySource`) para no dejar datos en la BD de los demás tests. Comprueba que el envío ocurre en un hilo `mail-*` y que, si la transacción hace rollback, no se envía nada | Bloque 7 | ✅ Aplicada |
| D38 | En surefire, `argLine` es `@{argLine} -javaagent:mockito`, con `<argLine></argLine>` vacío en las properties. Así funciona con JaCoCo, que rellena argLine, y también con `-Djacoco.skip` | Bloque 8 | ✅ Aplicada |
| D39 | Swagger UI y `/v3/api-docs` son públicos también en `prod` (sirven para la presentación). Para ocultarlos: `springdoc.api-docs.enabled=false` y `springdoc.swagger-ui.enabled=false` | Bloque 11 | ✅ Aplicada |
| D40 | Tiempo de juego simulado: no hay cliente real, así que «Jugar» suma 30-90 min con un UPDATE atómico (igual que ventas y rating), sin leer antes la fila | Bloque 11 | ✅ Aplicada |
| D41 | Correos con el aspecto de ARCADE: <br>• `EmailTemplates` usa la paleta del frontend, con tablas y estilos en línea (lo único que los clientes de correo muestran igual). <br>• El remitente lleva nombre: `MAIL_FROM_NAME` (por defecto ARCADE), que también es la marca dentro del correo y en el asunto de bienvenida («¡Bienvenido a ARCADE!»). <br>• Los botones apuntan a `FRONTEND_URL`; si falta, al primer origen de `CORS_ALLOWED_ORIGINS`; sin URL, el correo sale sin botones. <br>• El recibo lleva portada y precio por juego desde el bloque 13 (ver D42). | 2026-10-09 | ✅ Aplicada (validada en el bloque 13: `EmailServiceTest` 21 y `MailEventIntegrationTest` 4 en verde) |
| D42 | Recibo con portada y precio por juego: <br>• `OrderPaidEvent` lleva `List<Line(title, coverUrl, unitPrice)>` en lugar de solo títulos. `unitPrice` es el precio pagado con descuento (`OrderItem.unitPrice`). <br>• Las líneas se construyen **dentro de la transacción** de `OrderService.pay()`: `Game` es LAZY y el listener corre después del commit (`AFTER_COMMIT` + `@Async`) en otro hilo, donde ya no se puede cargar. <br>• `EmailTemplates.safeImageUrl` solo acepta URLs que empiezan por `http://` o `https://` (sin distinguir mayúsculas, tras `trim`). Si no, deja una celda vacía de 76 px para no descuadrar las filas. <br>• Miniatura de 64×36 con `alt` = título; todo escapado con `HtmlUtils`. Las portadas son la única imagen externa de los correos. | Bloque 13 | ✅ Aplicada |
| D43 | Limitación conocida (aceptada por alcance académico): la portada del recibo se carga desde la URL del developer, que puede saber cuándo se abre el correo (como un píxel de seguimiento). Ya pasa igual en la tienda web y Gmail lo atenúa con su proxy de imágenes. Solución: alojar las portadas en un almacenamiento propio (p. ej. Cloudinary). **Resuelta solo para las portadas subidas (bloque G, D50): van por Cloudinary con https. Las URLs pegadas siguen igual** | Bloque 13 | 📝 Documentada (parcial) |
| D44 | Idioma de los correos guardado en la BD: <br>• V5 añade `users.locale VARCHAR(5) NOT NULL DEFAULT 'es'` con `CHECK ck_users_locale IN ('es','en','it')`. <br>• La API es estricta: `locale` es opcional en `POST /api/auth/register` y `PATCH /api/users/me`, con `@Pattern ^(es\|en\|it)$` (solo minúsculas). Un valor inválido → 400 con `errors.locale`. Sin valor: `es` al registrarse, sin cambios en el perfil. <br>• Sale siempre en `UserResponse` (y en `AuthResponse.user`). **No va en el JWT.** Cada usuario solo cambia el suyo (el id sale del JWT). <br>• Añadir un idioma exige una migración que amplíe el CHECK, además de sus textos (D45). | Bloque 15 | ✅ Aplicada |
| D45 | Textos de los correos por idioma: <br>• Están en el record `notification/MailTexts`, con una constante por idioma (ES, EN, IT). Se eligió frente a `MessageSource` porque **el compilador obliga** a que cada idioma tenga todos los textos. <br>• `EmailTemplates` y `EmailService` reciben el `locale`; el HTML lleva `<html lang>`. <br>• Importes: `es-ES`, `en-IE` (para que el inglés siga en €) e `it-IT`. <br>• `MailTexts.of` es tolerante (trim y minúsculas); `null` o un idioma desconocido → `es`. Un test fija esta diferencia con la API estricta. <br>• El `locale` viaja en `UserRegisteredEvent`, `OrderPaidEvent` y `PaymentFailedEvent`, y se lee del `User` **dentro de la transacción** (mismo motivo que D42). | Bloque 15 | ✅ Aplicada |
| D46 | Reseñas estilo Steam: <br>• Una reseña es solo 👍/👎 (`recommended`) más texto y `language` (`es`, `en` o `it`). Se borró `rating`. Si el idioma no llega, se usa `users.locale` del autor; al editar, la reseña conserva el suyo. <br>• `Game` lleva `review_count` y `positive_count`, que cambian con el UPDATE atómico `applyReviewDelta`. <br>• `ReviewScore.of(positive, total)` aplica las reglas de Steam; con menos de 10 reseñas es `NONE`. `positivePercent` redondea **hacia abajo** (35/44 → 79), para no pasar un umbral que aún no se alcanza. <br>• Editar o borrar lee la reseña con `findByIdForUpdate` (bloqueo pesimista) para que el voto del que sale el delta sea el bloqueado. <br>• La puntuación cuenta todos los idiomas; la lista se filtra con `?language=`. | Bloque B | ✅ Aplicada |
| D47 | Catálogo por porcentaje: `minPositivePercent` (0 a 100) y el orden `top_rated` usan el porcentaje de positivas. Los juegos con menos de 10 reseñas no pasan el filtro y van al final del orden, para que 1 reseña al 100 % no supere a 30 al 95 %. | Bloque B | ✅ Aplicada |
| D48 | Refresh token: <br>• Acceso de 15 min (`JWT_EXPIRATION=PT15M`) y refresh de 7 días (`JWT_REFRESH_EXPIRATION=P7D`). <br>• `refresh_tokens` guarda el SHA-256 del token (nunca el token), su **familia**, la caducidad y la revocación. <br>• Cada `POST /api/auth/refresh` rota: revoca el token usado y emite otro de la misma familia. La revocación es un UPDATE condicional atómico (`revokeIfActive`): de varios refresh simultáneos gana uno solo y los demás cuentan como reutilización. <br>• Reutilizar un token revocado revoca **toda su familia** (no todos los del usuario, como decía la spec; la spec se corrigió). Esa revocación solo se guarda porque todas las transacciones de la cadena llevan `noRollbackFor = UnauthorizedException`. <br>• `POST /api/auth/logout` revoca la familia y es idempotente. Desactivar a un usuario o cambiarle los roles revoca todos sus refresh. <br>• El filtro JWT sigue sin consultar la BD. El frontend lo guarda en `localStorage` (limitación documentada). | Bloque F | ✅ Aplicada |
| D49 | Amigos y perfil: <br>• `friendships` tiene una fila por pareja sea cual sea la dirección, con `user_low_id < user_high_id` y `UNIQUE(low, high)`. <br>• Solo participan cuentas **jugador** (activas, sin DEVELOPER ni ADMIN); `FriendService.isPlayer` lo comprueba también al listar y aceptar solicitudes. <br>• Solicitud cruzada = se aceptan las dos. Cancelar solo borra solicitudes aún `PENDING`. <br>• `users.profile_public` (falso por defecto): la biblioteca y los amigos de un perfil solo se devuelven si es público, el que mira es amigo o es él mismo. La regla vive en el backend; el email, los pedidos, el carrito y la wishlist nunca salen. | Bloque E | ✅ Aplicada |
| D50 | Portadas en Cloudinary: <br>• `CoverStorage` (interfaz) con `CloudinaryCoverStorage`, que sube con la API firmada (SHA-1) usando `RestClient`; en tests se simula. <br>• `CoverService` valida dueño, tamaño (5 MB) y tipo **por los bytes mágicos** (`ImageType`: JPEG, PNG o WebP) antes de llamar al almacén, y guarda la `secure_url` (https) en `coverUrl`. <br>• Un 400 de Cloudinary responde 400 `cover.invalidType`; 5xx, red y timeout, 502. Sin `CLOUDINARY_URL` responde 503 `cover.disabled` y `GET /api/uploads/covers/status` da `{enabled:false}`, con lo que el frontend oculta el botón. <br>• `server.tomcat.max-swallow-size=10MB` para que más de 6 MB dé 413 y no un corte de conexión. | Bloque G | ✅ Aplicada |
| D51 | Asistente de recomendaciones: <br>• `ChatModel` (interfaz) con `OpenRouterChatModel` (`RestClient` con tiempos máximos); en tests se simula, así que el CI no llama a la red. <br>• `RecommendationService` pone el catálogo publicado completo (id, título, géneros, precio, % positivo, descripción corta) en el prompt de sistema; el texto del usuario va solo como mensaje de usuario. Pide `{reply, gameIds}` y **descarta los ids** que no existan o no estén publicados (máximo 4 juegos). <br>• Sin clave, sin red, sin cupo o con una respuesta inservible: 503 `assistant.unavailable` (ProblemDetail). <br>• `ChatRateLimiter`: 20 mensajes por hora y usuario, en memoria (429). Máximo 10 mensajes por petición y 500 caracteres por mensaje; el último tiene que ser del usuario (400). | Bloque D | ✅ Aplicada |
| D52 | Cierre de dos limitaciones: <br>• **V11** añade `ck_friendships_pair`: `(low, high)` tiene que ser `(requester, addressee)` o al revés. SQL portable (sin `LEAST`/`GREATEST`) para PostgreSQL y H2. <br>• **Purga de `refresh_tokens`**: `RefreshTokenPurgeScheduler` (`REFRESH_PURGE_CRON`, por defecto a las 03:30) borra **familias enteras** cuyo `max(expires_at)` es anterior a `now - REFRESH_PURGE_GRACE` (1 día). Nunca borra un token suelto de una familia con alguno vigente: así reutilizar un token robado sigue tirando la familia viva (D48) | 2026-10-20 | ✅ Aplicada |
| D53 | Demos jugables (solo frontend): <br>• Cuatro minijuegos propios inspirados en juegos del catálogo: Celeste (plataformas, 3 salas), Vampire Survivors (sobrevivir 60 s), Rocket League (fútbol 2D, 60 s) y Balatro (5 manos de póquer, objetivo 300, tabla de puntos de Balatro). <br>• Registro fijo por slug en `frontend/src/demos/registry.ts`; cada juego separa la lógica pura (`logic.ts`, testeada sin canvas) del dibujo. Si el juego no está en el catálogo, su demo no aparece. <br>• `DemoPlayer`: diálogo con foco atrapado y devuelto, pausa al perder el foco, `clampDt` a 0,05 s, ErrorBoundary para el chunk perezoso y pantalla final según propiedad (estudio propio / ya lo tienes / en el carrito / añadir). <br>• Cualquiera juega sin sesión; no se guarda nada. En móvil solo Balatro (las otras piden teclado). Arte propio, sin sprites ni música de los juegos reales | 2026-10-20 | ✅ Aplicada |
| D54 | Verificación de email obligatoria: <br>• Flujo: crear cuenta → correo «Confirma la cuenta «usuario»» → el enlace abre `/verify-email`, **pide la contraseña de ese registro** y entra con sesión. Sin verificar no se puede iniciar sesión (403 `auth.emailNotVerified`, solo con la contraseña correcta). <br>• V12: `users.email_verified` (las cuentas existentes quedan verificadas) y `email_verification_tokens` con el SHA-256 del token, caducidad de 24 h y **los datos del registro** (usuario, hash de la contraseña, nombre, idioma, estudio). Al verificar se aplican los datos de ese enlace. V12.1: usuario único sin distinguir mayúsculas (índice `lower(username)` en PostgreSQL, columna generada en H2). <br>• Por qué la contraseña: la revisión de seguridad encontró dos formas de robar una cuenta sin verificar (sustituir la cuenta con el mismo email; dos registros con el mismo usuario y correos idénticos). Atar los datos al enlace y pedir su contraseña las cierra; decidido por el usuario el 2026-10-21. <br>• 5 fallos de contraseña por registro anulan sus enlaces. Reenvío: todos los registros pendientes (máx. 3), 3 correos por hora y email. Límite por IP de 60/min en registro, reenvío y verificación (`AUTH_IP_LIMIT_PER_MINUTE`, `Retry-After`), con la IP real tras el proxy de Render (`forward-headers-strategy: native`). <br>• Cuentas sin verificar: un usuario ocupado por una cuenta sin verificar de más de 24 h y sin datos se libera; el admin puede verificar a mano (`POST /api/admin/users/{id}/verify-email`, 409 si hay varios registros pendientes). Amigos y búsqueda las ignoran. <br>• Correo: timeouts de 15 s y hasta 3 intentos ante fallos de conexión (Gmail fallaba a veces desde Render); pool de correo con 4 hilos. Sin `MAIL_USERNAME`, el enlace se escribe en el log. <br>• Interruptor `EMAIL_VERIFICATION_REQUIRED` (true; solo los tests lo apagan). El seed pide `--admin-password` y verifica sus cuentas con el admin | 2026-10-21 | ✅ Aplicada |
| D55 | Eliminar cuentas (propia y por el admin): <br>• **Anonimizar en vez de borrar:** la fila de `users` se queda (`fk_orders_user` es RESTRICT) y se pierden los datos personales (`deleted_<id>`, sin contraseña, nombre ni avatar; se borran reseñas, amistades, carrito, wishlist y tokens). Se conservan pedidos y biblioteca (obligación fiscal en Italia; defendible como «derecho al olvido»). V13: `deletion_scheduled_at`, `deletion_requested_by` (SELF/ADMIN) y `deleted_at`. <br>• **14 días de pausa** (`ACCOUNT_DELETION_GRACE`, `P14D`): no se puede entrar (403 `auth.accountPendingDeletion` o `auth.accountDeletionByAdmin`, con la fecha) y las sesiones se cortan al momento. Los juegos del estudio siguen a la venta durante la pausa. <br>• **SELF se recupera entrando** (`POST /api/auth/recover-account`, 200 con sesión); **ADMIN solo lo cancela un admin** (`POST /api/admin/users/{id}/deletion/cancel`). `POST /api/users/me/deletion` pide la contraseña y devuelve **200** con la fecha (el spec decía 204; el frontend necesita la fecha). Baja del admin: `DELETE /api/admin/users/{id}` (400 `user.cannotDeleteSelf`). <br>• **Siempre queda un admin activo** (409 `user.lastAdmin`): al programar la baja, cambiar roles o desactivar, se bloquean las filas de los admins por id y se **recuenta con datos frescos** tras el bloqueo. `AdminAccountInitializer` es la red de seguridad (recrea el admin del `.env` al reiniciar). <br>• **DELISTED visible para sus dueños:** quien tiene el juego en la biblioteca, su estudio y el admin abren la ficha (aviso «Ya no está a la venta»); el resto, 404. Con estudio eliminado se muestra **«Estudio eliminado»** y no se puede republicar (409 `game.developerDeleted`). Los PUBLISHED del estudio pasan a DELISTED y los DRAFT se borran. <br>• **4 correos** (es/en/it, solo texto, AFTER_COMMIT): baja programada por ti, programada por un admin, cancelada y cuenta eliminada. <br>• **Tarea programada** diaria (`ACCOUNT_DELETION_CRON`, `0 15 4 * * *`) **y al arrancar** (Render free duerme); una transacción por cuenta, con `Clock`; puede eliminar tarde, nunca antes de la fecha. <br>• **Arreglos de la revisión final:** verify-email y refresh no dan sesión a una cuenta en pausa (403 / 401); los nombres `deleted_*` y `@deleted.invalid` quedan reservados en el registro (400) y D54 ignora las filas eliminadas; `@DynamicUpdate` en `User` y 404 del admin sobre cuentas eliminadas; en la baja propia la contraseña se comprueba antes de bloquear a los admins. | 2026-10-22 | ✅ Aplicada |
**Forma de trabajar:**
- Se construye bloque a bloque y cada bloque se valida antes de pasar al siguiente.
- Las respuestas van en español.

---

## 4. Arquitectura
```
com.capstone.steam
├── config/        AsyncConfig (mailExecutor), MailExecutorProperties, OpenApiConfig
├── security/      SecurityConfig, JwtService, JwtAuthFilter, JwtProperties, UserDetailsServiceImpl, AppUserPrincipal, ProblemResponseWriter (401/403), CorsProperties
├── auth/          AuthController (register, login, refresh, logout), AuthService, RefreshToken, RefreshTokenRepository, RefreshTokenService (rotación y familias), dto (RegisterRequest, LoginRequest, RefreshRequest, AuthResponse)
├── user/          User (con profilePublic), Role, RoleName, UserRepository, RoleRepository, AdminProperties, UserService, UserController (/me), AdminUserController, AdminAccountInitializer
├── genre/         Genre, GenreRepository, GenreService, GenreController (CRUD de ADMIN)
├── game/          Game, GameStatus, ReviewScore (etiquetas de Steam), GameRepository (UPDATE atómicos), CatalogService, GameManagementService, StorefrontRules,
│                  CatalogController, DeveloperGameController, AdminGameController
│   └── search/    GameSearchCriteria (record), GameSort, GameCriteriaRepository (Criteria API)
├── library/       LibraryEntry, LibraryRepository, LibraryService, LibraryController
├── cart/          CartItem, CartItemRepository, CartService, CartController
├── wishlist/      WishlistItem, WishlistItemRepository, WishlistService, WishlistController
├── review/        Review, ReviewRepository (findByIdForUpdate), ReviewService, ReviewController
├── friend/        Friendship, FriendshipStatus, FriendshipRepository, FriendService, FriendController (/api/friends), ProfileController (/api/users/search y /{username}/profile), dto (Relation, UserCard, PublicProfileResponse…)
├── cover/         CoverController, CoverService, CoverStorage, CloudinaryCoverStorage, CloudinaryProperties, CoverConfig, ImageType (bytes mágicos), CoverDisabledException, CoverUploadException
├── recommendation/ RecommendationController, RecommendationService, ChatModel, OpenRouterChatModel, OpenRouterProperties, RecommendationConfig, ChatRateLimiter, AssistantUnavailableException, dto (ChatRequest, ChatMessage, ChatResponse)
├── order/         Order, OrderItem, OrderStatus, Payment, PaymentStatus, PaymentMethod, OrderRepository, PaymentRepository, OrderService, OrderController
│   └── payment/   PaymentGateway, SimulatedPaymentGateway, PaymentProperties
├── notification/  event (UserRegisteredEvent, OrderPaidEvent, PaymentFailedEvent, GameOnSaleEvent, SaleRecipient), MailListener, EmailService, EmailTemplates, MailTexts (es/en/it)
├── steamstore/    SteamStoreClient, SteamImportService, SteamPriceSyncService, SteamPriceScheduler, SteamController
└── common/        dto/PageResponse, util/Slugs, exception (GlobalExceptionHandler con ProblemDetail, excepciones de dominio), i18n/Messages (errores en es/en/it)
```
- **Capas:** Controller → Service (`@Transactional`) → Repository.
- **Nunca se exponen entidades**: la API solo trabaja con DTOs.

**Perfiles:**
- `dev`: PostgreSQL local (o con Docker Compose, puerto 5433).
- `test`: H2.
- `prod`.

**Configuración:**
- `ddl-auto=validate`: el esquema solo lo gestiona Flyway.
- **Secretos por variables de entorno:** `JWT_SECRET`, `MAIL_USERNAME`, `MAIL_APP_PASSWORD` (contraseña de aplicación de Gmail), `DB_URL`, `DB_USER`, `DB_PASSWORD`, `CLOUDINARY_URL` y `OPENROUTER_API_KEY` (las dos últimas son opcionales: sin ellas la subida de portadas y el asistente se desactivan solos). También `JWT_EXPIRATION` (`PT15M`), `JWT_REFRESH_EXPIRATION` (`P7D`) y `OPENROUTER_MODEL`.
- Se guardan en un `.env` incluido en `.gitignore`.

---

## 5. Base de datos: migraciones Flyway
**Ubicaciones:** `classpath:db/migration/common` y `classpath:db/migration/{vendor}`.

**V1__base_schema.sql** (en `common`)
- **`roles`:** `id`, `name` UNIQUE, con un seed de ROLE_USER, ROLE_DEVELOPER y ROLE_ADMIN.
- **`users`:** `id`, `username` UNIQUE, `email` UNIQUE, `password_hash`, `display_name`, `avatar_url`, `enabled`, `created_at`, `updated_at`.
- **`user_roles`:** `(user_id, role_id)` como PK.
- **`genres`:** `id`, `name` UNIQUE, `slug` UNIQUE, con un seed de géneros.
- **`games`:**
  - Datos del juego: `id`, `title`, `slug` UNIQUE, `description`, `developer_id` (FK a users), `price NUMERIC(10,2)`, `discount_percent` (CHECK 0-100), `release_date`, `cover_url`.
  - `status`: DRAFT, PUBLISHED o DELISTED.
  - Contadores: `sales_count`, `rating_sum`, `rating_count` (V8 los cambió: ver abajo).
  - `version` (bloqueo optimista), `created_at` y `updated_at`.
- **`game_genres`:** `(game_id, genre_id)`.

**V2__storefront.sql** (en `common`)
- **`library_entries`:** `id`, `user_id`, `game_id`, `acquired_at`, `playtime_minutes`, `UNIQUE(user_id, game_id)`.
- **`cart_items`:** `user_id`, `game_id`, `added_at`, `UNIQUE(user_id, game_id)`.
- **`wishlist_items`:** `user_id`, `game_id`, `added_at`, `UNIQUE(user_id, game_id)`.
- **`reviews`:** `id`, `user_id`, `game_id`, `rating` (CHECK 1-5; V8 lo quitó), `recommended`, `body`, timestamps, `UNIQUE(user_id, game_id)`.

**V3__orders_payments.sql** (en `common`)
- **`orders`:**
  - `id`, `user_id`, `total NUMERIC(10,2)`, `created_at`, `paid_at`.
  - `status`: PENDING, PAID, FAILED o CANCELLED.
- **`order_items`:** `order_id`, `game_id`, `unit_price` (snapshot).
- **`payments`:**
  - `id`, `order_id`, `amount`, `method`, `provider_ref`, `failure_reason`, `created_at`.
  - `status`: INITIATED, SUCCEEDED o FAILED.
- **Cambio en la biblioteca:** `ALTER TABLE library_entries ADD order_id` (FK nullable).

**V4__catalog_indexes.sql** (una versión en `postgresql/` y otra en `h2/`)
- **Índices B-tree:**
  - `games(status, release_date DESC)`, `games(status, price)`, `games(developer_id)`
  - `game_genres(genre_id, game_id)`, `reviews(game_id, created_at DESC)`, `orders(user_id, created_at DESC)`, `library_entries(user_id)`
- **Solo PostgreSQL:**
  - `pg_trgm` con un índice GIN sobre `lower(title)`.
  - Un índice parcial `WHERE status='PUBLISHED'`.

**V5__user_locale.sql** (en `common`, bloque 15)
- **`users.locale`:** `VARCHAR(5) NOT NULL DEFAULT 'es'`, con `CHECK ck_users_locale` (`es`, `en` o `it`). Los usuarios existentes quedan en `es` (D44).

**V6__game_description_translations.sql** (en `common`, bloque 16)
- **`games.description_en`** y **`games.description_it`** (`VARCHAR(10000)`). `description` es la española y hace de respaldo.

**V7__steam_link.sql** (en `common`, bloque 16)
- **`games.steam_app_id`** (`INTEGER`, índice único) y **`games.price_synced_at`**. Los juegos sin AppID (creados a mano) no se sincronizan.

**V8__steam_style_reviews.sql** (en `common`, bloque B)
- **`reviews.language`** (`VARCHAR(5)`, `CHECK` es/en/it): las reseñas existentes toman el `locale` de su autor.
- Se borran `reviews.rating` (y su CHECK) y `games.rating_sum`. `games.rating_count` pasa a **`review_count`** y se añade **`positive_count`**; la migración recalcula los dos desde las reseñas. Nuevo `CHECK` `positive_count <= review_count`.
- Índice `idx_reviews_game_language_created (game_id, language, created_at DESC)` para la lista filtrada por idioma.

**V9__friendships.sql** (en `common`, bloque E)
- **`users.profile_public`** (`BOOLEAN`, falso por defecto).
- **`friendships`:** `requester_id`, `addressee_id`, `user_low_id`, `user_high_id`, `status` (`PENDING` o `ACCEPTED`), `created_at`, `responded_at`. `UNIQUE(user_low_id, user_high_id)` deja una sola fila por pareja, y los CHECK impiden pedirse a uno mismo y exigen `low < high`. Índices por destinatario y por solicitante.

**V10__refresh_tokens.sql** (en `common`, bloque F)
- **`refresh_tokens`:** `user_id` (FK con `ON DELETE CASCADE`), `token_hash` (`UNIQUE`, el SHA-256 en hexadecimal), `family_id`, `expires_at`, `created_at`, `revoked_at` y `replaced_by_id`. Índices por usuario y por familia.

**V11__friendships_pair_check.sql** (en `common`, 2026-10-20)
- **`ck_friendships_pair`:** `(user_low_id, user_high_id)` tiene que ser `(requester_id, addressee_id)` o `(addressee_id, requester_id)`. Antes solo lo garantizaba la entidad `Friendship` (D52).

---

## 6. Funcionalidad por fases
### Seguridad JWT
- **Configuración:**
  - Stateless y sin CSRF, con CORS configurado.
  - BCrypt, `JwtAuthFilter` y `@EnableMethodSecurity`.
- **Reglas de acceso:**
  - Públicas: `POST /api/auth/register`, `/login`, `/refresh` y `/logout`, `GET /api/games/**` y `GET /api/genres/**`.
  - `/api/admin/**` exige ADMIN.
  - `/api/developer/**` exige DEVELOPER.
  - El resto requiere estar autenticado.
- **Token:** HS256; los claims son `sub` = userId y `roles`; el acceso dura 15 min (`JWT_EXPIRATION`) y el refresh token 7 días (`JWT_REFRESH_EXPIRATION`).
- **Endpoints:** `POST /api/auth/register` (publica un `UserRegisteredEvent`), `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout` (los cuatro públicos y con `refreshToken` en la respuesta, salvo el logout, que da 204) y `GET`/`PATCH /api/users/me` (el `PATCH` acepta también `profilePublic`). El registro y el `PATCH` aceptan un `locale` opcional (`es`, `en` o `it`), que sale en `UserResponse` y no va en el JWT (D44).
- **Ownership:** un DEVELOPER solo edita sus propios juegos; si no es el dueño, recibe un 403.
- **Errores:** en formato `ProblemDetail` (RFC 9457), con los 401 y 403 en JSON.

### Catálogo (Criteria API)
- **`GET /api/games`:**
  - Filtros: `q`, `genres`, `minPrice`/`maxPrice` (sobre el precio final con descuento), `onSale`, `developerId`, `releasedAfter`, `releasedBefore` y `minPositivePercent`.
  - Paginación y orden `newest`, `price_asc`, `price_desc`, `top_rated` (por % de positivas), `best_selling` o `title`.
- **Implementación:**
  - Predicados dinámicos; los géneros se filtran con un subquery `IN` (sin join ni `distinct`, D21).
  - Una query aparte para el count.
  - Los géneros se cargan con un fetch en una segunda query para evitar el N+1.

### UPDATE atómicos
- **`incrementSalesCount(gameId)`**
- **`applyReviewDelta(gameId, positiveDelta, countDelta)`**
- **`markOrderPaid(orderId)`:** `... WHERE status='PENDING'`. Si devuelve 0, lanza un 409 (evita el doble pago).
- **`bulkDiscountForDeveloper(devId, pct)`**

### Reglas del escaparate
- Solo se puede reseñar un juego que está en tu biblioteca. La reseña es 👍/👎 con idioma (D46).
- No se puede añadir al carrito un juego que ya tienes.
- Un ADMIN puede retirar juegos de la tienda (DELISTED) y gestionar los géneros.

### Checkout, eventos y correo
- **Checkout:**
  1. Carrito → `Order` PENDING (con snapshot de precios) → `Payment` → `PaymentGateway` (`SimulatedPaymentGateway`), todo en `OrderService`.
  2. Si el pago tiene éxito: `markPaid`, se crean las entradas de biblioteca, `incrementSalesCount`, se quitan los juegos comprados del carrito y la wishlist y se publica el `OrderPaidEvent` (D29).
  3. Si falla: el pedido pasa a FAILED y se publica el `PaymentFailedEvent`.
- **`AsyncConfig`:** `ThreadPoolTaskExecutor mailExecutor` (core 2, max 5, cola 100, prefijo `mail-`) y un `AsyncUncaughtExceptionHandler`.
- **`MailListener`:**
  - `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async("mailExecutor")`.
  - Los eventos no llevan entidades: solo IDs, textos y números. `OrderPaidEvent` incluye las líneas del recibo (título, portada y precio pagado), resueltas antes del commit (D42).
- **`EmailService`:**
  - Gmail: `smtp.gmail.com:587` con STARTTLS.
  - Plantillas (`EmailTemplates`): bienvenida, recibo de compra (miniatura de portada, título y precio de cada juego, más el total) y pago fallido.
  - Idioma: cada correo sale en el `locale` del usuario (es, en o it), con los textos de `MailTexts` (D45). Asuntos:

    | Correo | es | en | it |
    |---|---|---|---|
    | Bienvenida | ¡Bienvenido a ARCADE! | Welcome to ARCADE! | Benvenuto su ARCADE! |
    | Recibo | Recibo de tu compra #N | Receipt for your purchase #N | Ricevuta del tuo acquisto #N |
    | Rechazo | No hemos podido cobrar tu pedido #N | We couldn't charge your order #N | Non siamo riusciti ad addebitare il tuo ordine #N |

### Reseñas, amigos, portadas y asistente (bloques B a G)
- **Reseñas (B):** `POST/PUT/DELETE /api/reviews` con `{gameId, recommended, body?, language?}`; `GET /api/games/{slug}/reviews?language=`. Cada juego devuelve `reviewCount`, `positivePercent` y `reviewScore` (D46, D47).
- **Amigos (E):** `GET /api/users/search?q=`, `GET /api/users/{username}/profile`, `GET /api/friends`, `GET /api/friends/requests`, `POST /api/friends/requests`, `POST /api/friends/requests/{id}/accept|reject`, `DELETE /api/friends/requests/{id}` y `DELETE /api/friends/{username}` (D49).
- **Refresh token (F):** D48.
- **Portadas (G):** `POST /api/developer/games/{id}/cover`, `POST /api/admin/games/{id}/cover` (multipart, campo `file`) y `GET /api/uploads/covers/status` (D50).
- **Catálogo demo (C):** `scripts/demo-data.json` tiene 60 juegos reales (59 publicados y un borrador), 49 estudios, 6 jugadores y 60 reseñadores (20 por idioma, `rev_<idioma>_NN`) que compran por el checkout simulado y reseñan; unas 2.100 reseñas generadas con un reparto determinista (`scripts/demo-reviews.mjs`) y frases de un banco propio. `node scripts/check-demo-data.mjs` valida el JSON.
- **Asistente (D):** `POST /api/recommendations/chat` (D51).

### Testing
Total: **518 tests de backend** (`.\mvnw.cmd clean verify`, 0 fallos) y **201 de frontend** (`npm test`, 25 archivos).
- **Unitarios (Mockito):** `JwtServiceTest`, `AuthServiceTest`, `OrderServiceTest`, `ReviewServiceTest`, `ReviewScoreTest`, `CartServiceTest`, `GameManagementServiceTest`, `SimulatedPaymentGatewayTest`, `EmailServiceTest`, `SteamStoreClientTest`, `RefreshTokenServiceTest`, `FriendServiceTest`, `ImageTypeTest`, `CloudinaryCoverStorageTest` (con `MockRestServiceServer`), `OpenRouterChatModelTest`, `RecommendationServiceTest` y `ChatRateLimiterTest`.
- **`@DataJpaTest` con H2 y Flyway real:** `GameCriteriaRepositoryTest` y `RepositoryIntegrationTest` (UPDATE atómicos y doble pago). Concurrencia con 16 hilos en `AtomicUpdateConcurrencyTest`, y con varios refresh simultáneos en `RefreshTokenConcurrencyTest` (solo uno gana).
- **`@SpringBootTest` + MockMvc:**
  - `AuthIntegrationTest`
  - `CheckoutIntegrationTest`
  - `RefreshTokenIntegrationTest`, `FriendIntegrationTest`, `CoverIntegrationTest`, `CoverUploadLimitTest` (Tomcat real: más de 6 MB da 413) y `RecommendationIntegrationTest`
  - `MailEventIntegrationTest`: con `@MockitoBean JavaMailSender` y Awaitility. El correo solo se envía tras el commit, nunca si hay rollback.

---

## 7. Registro de progreso
| Bloque | Contenido | Estado | Fecha | Notas |
|---|---|---|---|---|
| 0 | Planificación y handoff | ✅ Hecho | 2026-09-30 | Plan aprobado |
| 1 | `pom.xml`, `mvnw`, `docker-compose.yml`, `application*.yml`, Flyway V1 a V4 | ✅ Hecho | 2026-09-30 | Resultados: <br>• `FlywayMigrationTest`: 4 tests en verde sobre H2 <br>• En Postgres 18: V1 a V4 aplicadas y 11 índices `idx_*` <br>• App arriba en el 8081 con `/actuator/health` UP <br><br>Configuración añadida: <br>• Mockito se carga como agente en surefire <br>• Health de mail desactivado <br>• `server.port` configurable con `SERVER_PORT` |
| 2 | Entidades JPA y repositorios (validados con `ddl-auto=validate`) | ✅ Hecho | 2026-09-30 | Qué hay: <br>• 11 entidades y 9 repositorios <br>• `RepositoryIntegrationTest` (8 tests), 12 tests en total en verde <br>• Postgres valida el esquema al arrancar <br><br>UPDATE atómicos ya hechos: <br>• `GameRepository`: `incrementSalesCount`, `applyRatingDelta`, `bulkDiscountForDeveloper` <br>• `OrderRepository`: `markPaid`, `closePending` |
| 3 | Seguridad JWT y auth | ✅ Hecho | 2026-09-30 | Endpoints: <br>• `POST /api/auth/register`, `POST /api/auth/login` <br>• `GET` y `PATCH /api/users/me` <br>• `GET /api/admin/users`, `PUT /api/admin/users/{id}/roles`, `PATCH /api/admin/users/{id}/enabled` <br><br>Tests: `JwtServiceTest` (5), `AuthServiceTest` (2) y `AuthIntegrationTest` (7); en total 26 en verde. <br><br>Prueba HTTP manual en Postgres: 201, 200, 401 y 403 según lo esperado. |
| 4 | Géneros, juegos y búsqueda con Criteria API | ✅ Hecho | 2026-09-30 | Endpoints: <br>• Público: `GET /api/games` (filtros y orden), `GET /api/games/{slug}`, `GET /api/genres` <br>• Developer: CRUD en `/api/developer/games`, más `/publish`, `/unpublish` y `/discount` <br>• Admin: `/api/admin/genres` (CRUD), `GET /api/admin/games` y `PATCH /api/admin/games/{id}/status` <br><br>Tests nuevos: `GameCriteriaRepositoryTest` (8), `GameManagementServiceTest` (5), `CatalogIntegrationTest` (7) y `SlugsTest` (5); en total 51 en verde. <br><br>En Postgres se probaron los filtros y órdenes por HTTP. |
| 5 | Carrito, wishlist, biblioteca y reseñas | ✅ Hecho | 2026-09-30 | Endpoints: <br>• Carrito: `GET/DELETE /api/cart`, `PUT/DELETE /api/cart/items/{gameId}` <br>• Wishlist: `GET /api/wishlist`, `PUT/DELETE /api/wishlist/{gameId}` <br>• Biblioteca: `GET /api/library`, `GET /api/library/status/{gameId}`, `POST /api/admin/users/{userId}/library/{gameId}` (regalo del admin) <br>• Reseñas: `GET /api/games/{slug}/reviews` (público), `POST /api/reviews`, `PUT/DELETE /api/reviews/{id}` <br><br>Tests nuevos: `CartServiceTest` (5), `ReviewServiceTest` (7) y `StorefrontIntegrationTest` (5); en total 68 en verde. |
| 6 | Checkout y pagos simulados | ✅ Hecho | 2026-09-30 | Endpoints: <br>• `POST /api/orders` (carrito → PENDING) <br>• `POST /api/orders/{id}/pay` (200 PAID, o 402 FAILED) <br>• `POST /api/orders/{id}/cancel` <br>• `GET /api/orders` y `GET /api/orders/{id}` <br><br>Tests nuevos: `SimulatedPaymentGatewayTest` (4), `OrderServiceTest` (8) y `CheckoutIntegrationTest` (4, con `@RecordApplicationEvents`); en total 84 en verde. <br><br>Prueba HTTP en Postgres: <br>• 402 con la tarjeta `0000` <br>• PAID con la tarjeta válida <br>• 409 en el doble pago <br>• biblioteca con `orderId`, carrito vacío y `sales_count=1` |
| 7 | Eventos y correo asíncrono | ✅ Hecho | 2026-09-30 | Componentes: <br>• `AsyncConfig`: pool `mailExecutor` con hilos `mail-*` y `AsyncUncaughtExceptionHandler` <br>• `MailListener`: `AFTER_COMMIT` + `@Async` para los 3 eventos <br>• `EmailService` y `EmailTemplates`: HTML con los datos del usuario escapados <br><br>Tests nuevos: `EmailServiceTest` (4) y `MailEventIntegrationTest` (4, con su propio H2 y sin `@Transactional`); en total 92 en verde. <br><br>En Postgres: el registro responde en 480 ms y el correo sale en el hilo `mail-1`. **El envío real a Gmail aún no se ha probado**: faltan las credenciales. |
| 8 | Suite de tests | ✅ Hecho | 2026-09-30 | JaCoCo 0.8.15: `./mvnw verify` genera `target/site/jacoco/index.html` y **falla si la cobertura de líneas baja del 80 %**. <br><br>Cobertura: **96,0 % de líneas, 78,2 % de ramas y 91,8 % de métodos** (antes: 90,5 % y 70,7 %). <br><br>Tests nuevos: <br>• `AdminIntegrationTest` (5): CRUD de géneros, cuenta desactivada → 403 en el login, moderación y 404 <br>• `AtomicUpdateConcurrencyTest` (2): **16 hilos reales**; ninguna venta se pierde y exactamente 1 pago gana el pedido <br>• 3 casos más en Catalog y Storefront <br><br>**102 tests en verde.** |
| 9 | README y colección de Postman | ✅ Hecho | 2026-09-30 | `README.md`: incluye la tabla "requisito → dónde se cumple", la puesta en marcha, la arquitectura, la API y los tests. <br><br>`postman/Capstone-Steam.postman_collection.json`: 47 peticiones en 8 carpetas, como un flujo completo que guarda tokens e ids solo. **Ejecutada con newman contra Postgres: 47 peticiones y 60 comprobaciones, 0 fallos.** |
| 10 | Frontend React (`frontend/`) | ✅ Hecho | 2026-09-30 | Desde la exportación de Figma Make: 10 páginas, toda la API conectada, en español y en €. Probado con Playwright (compra aprobada/rechazada, reseña, wishlist, panel developer y admin). Arreglos: el proxy quita `Origin` (403 de CORS), celdas de tabla sin flex, cierre de sesión con recarga en `/`. |
| 11 | Cierre: Swagger, tiempo de juego, tests del frontend, CI y despliegue | ✅ Hecho | 2026-09-30 | • springdoc 3.1.1 → `/swagger-ui.html` (público, esquema bearer) <br>• `POST /api/library/{gameId}/play` con UPDATE atómico `addPlaytime` (404 si no lo tienes) y botón «Jugar» <br>• Backend: **105 tests** (+ `OpenApiIntegrationTest` y play), 96,1 % de líneas <br>• Frontend: Vitest + Testing Library, **17 tests** <br>• `.github/workflows/ci.yml` (verify + test/build del frontend) <br>• `Dockerfile`, `render.yaml`, `frontend/vercel.json`; `application-prod.yml` acepta `DB_URL` o `DB_HOST/DB_PORT/DB_NAME`; `server.port` usa `PORT` si existe |
| 12 | Catálogo con juegos reales | ✅ Hecho | 2026-09-30 | • `scripts/seed-demo.mjs` + `scripts/demo-data.json`: siembra por la API (idempotente) 21 estudios, 23 juegos reales de Steam (Silksong en borrador), 6 jugadores, 20 pedidos (1 FAILED), 32 reseñas, wishlists, carrito y horas <br>• Portadas del CDN de Steam: `library_600x900_2x` (vertical), `capsule_616x353` (tarjetas, `cardImage`) y `library_hero` (hero, `wideImage`) <br>• **BD local nueva `capstone_steam_demo`** (el `.env` apunta a ella). La antigua `capstone_steam` sigue intacta y además hay copia en `backups/capstone_steam-antes-juegos-reales.dump` (borrarla la bloqueó el permiso de auto mode; queda a decisión del usuario) |
| 13 | Recibo de compra con portada y precio | ✅ Hecho | 2026-10-09 | • Correos con el estilo de ARCADE (D41) y recibo con miniatura de portada, título y precio pagado por juego, más el total (D42) <br>• `OrderPaidEvent` pasa a `List<Line(title, coverUrl, unitPrice)>`; nuevo `EmailTemplates.safeImageUrl` <br>• La página de pedidos del frontend ya mostraba portada y precio: sin cambios <br>• `.\mvnw.cmd verify`: **122 tests, 0 fallos, 0 errores**, BUILD SUCCESS. Cobertura: **96,28 % de líneas (1.192 de 1.238)** y 80 % de ramas. Frontend no ejecutado (sin cambios; siguen 18 tests) <br>• Revisión de seguridad sin hallazgos bloqueantes; 3 mejoras menores pendientes (ver «Siguiente paso») y una limitación aceptada (D43) <br>• **El envío real a Gmail sigue sin probarse**: `MAIL_USERNAME`, `MAIL_APP_PASSWORD` y `MAIL_FROM` están vacíos en `.env` |
| 14 | Prueba real del correo con Gmail | ✅ Hecho | 2026-10-09 | Backend en el 8081, perfil `dev`, BD `capstone_steam_demo`. <br>• **1.ª prueba (fallida):** `535 5.7.8 BadCredentials`, porque la contraseña de aplicación era de otra cuenta distinta de `MAIL_USERNAME`. La app degradó bien (D36): pedido #22 PAID y el error solo en el log. El usuario corrigió `.env`. <br>• **2.ª prueba (correcta):** usuario nuevo `mailtest_arcade2`. Pedido #23 PAID: Cyberpunk 2077 (id 7) a 44,99 € con 25 % de descuento + Celeste (id 3) a 18,99 €; total **63,98 €**. Log: «¡Bienvenido a ARCADE!» en `mail-1` y «Recibo de tu compra #23» en `mail-2`, sin excepciones. **El usuario confirmó que los dos correos llegaron a su bandeja.** <br>• Sin cambios de código ni tests ejecutados en este bloque. Lecciones del entorno en la sección 8 |
| 15 | Correos en el idioma del usuario (es, en, it) | ✅ Hecho | 2026-10-09 | Pedido por el usuario antes del commit. <br>• **BD:** `V5__user_locale.sql` (`users.locale` + CHECK), aplicada en `capstone_steam_demo` (31 usuarios en `es`) (D44) <br>• **API:** `locale` opcional en el registro y en `PATCH /api/users/me`; sale en `UserResponse`; no va en el JWT. Postman: `locale` en el registro del developer (`en`) y al actualizar el perfil (`it`) <br>• **Correo:** `MailTexts` con ES, EN e IT; el `locale` viaja en los 3 eventos; `<html lang>` e importes por idioma (D45). Hecho también el `trim()` de `MAIL_FROM` <br>• **Frontend:** `lib/locale.ts` (`detectLocale` con el idioma del navegador; si no es es/en/it → es). El registro lo envía sin campo visible. En Ajustes, «Perfil público» tiene el selector «Idioma de los correos» (Español, English, Italiano), que se guarda con «Guardar cambios». La interfaz web sigue solo en español <br>• `.\mvnw.cmd verify`: **158 tests, 0 fallos, 0 errores, 0 omitidos**, coverage checks met, BUILD SUCCESS. Cobertura: **96,35 % de líneas (1.213 de 1.259)** y 81 % de ramas. Frontend: **21 tests en 6 archivos** y build correcto <br>• **Sin probar:** Newman con los nuevos bodies, el selector en el navegador y un envío real en inglés o italiano. El motivo de rechazo sigue en español (ver «Siguiente paso») |
| 16 | Interfaz en es/en/it, importación de Steam y alojamiento local | ✅ Hecho | 2026-10-18 | Commits `245570c` a `8e980c7`. <br>• **Frontend:** `i18n/` (`messages.ts`, `LanguageSwitcher` con banderas en la cabecera, `localStorage` `arcade.lang`, por defecto el idioma del navegador o `es`); todas las páginas traducidas; la tarjeta entera abre el detalle; Swagger accesible desde el frontend (proxy de `/swagger-ui` y `/v3/api-docs`); móvil con «Accedi» y «Ordini»; footer con GitHub, LinkedIn y API Docs <br>• **Backend:** errores de la API traducidos con `common/i18n/Messages` y `Accept-Language`; descripciones en/it (V6); integración con la tienda de Steam (V7): `POST /api/developer/games/import` y sincronización de precios al arrancar y cada 6 h (`POST /api/admin/steam/sync`); si la sincronización detecta que empieza una oferta, correo a quien tiene el juego en la wishlist, en su idioma (`GameOnSaleEvent`) <br>• **Datos demo:** reseñas en italiano <br>• **Alojamiento local:** `deploy/local/` (Caddy + scripts `start-arcade`/`stop-arcade`) |
| A | Cierre: seguridad y verificación | ✅ Hecho | 2026-10-19 | Diseño: `docs/superpowers/specs/2026-10-19-cierre-y-extras-design.md`. <br>• `coverUrl` y `avatarUrl` solo http(s) (`010677a`) <br>• Email del destinatario enmascarado en los logs, también en el error SMTP (`e9ee925`) <br>• Portadas del correo solo por https (`2ec4015`) <br>• Tras la revisión final: la cadena vacía vuelve a valer en `avatarUrl` y `coverUrl`, para poder quitar el avatar (`4c34a25`), y el log de errores `@Async` ya no vuelca los eventos con emails (`17563e2`) <br>• El motivo de rechazo ya se traducía (claves `payment.declined.*`) <br>• `.\mvnw.cmd verify`: **180 tests**, 0 fallos, cobertura **95,77 %** de líneas (1.561 de 1.630) y 81 % de ramas <br>• Newman: 47 peticiones, 60 comprobaciones, 0 fallos <br>• Navegador: selector de idioma de la interfaz y de los correos; pago rechazado en inglés por API <br>• Pendiente del usuario: envío real en en/it |
| B | Reseñas estilo Steam (oleada 1) | ✅ Hecho | 2026-10-19 | Commits `e4c22b2` (V8) a `a83e71d`. <br>• 👍/👎 con idioma; fuera las estrellas; `applyReviewDelta`; `ReviewScore` con las reglas de Steam; `positivePercent` redondea hacia abajo <br>• Catálogo: `minPositivePercent` y `top_rated` por porcentaje (D46, D47) <br>• Frontend: `ReviewScoreBadge`, lista filtrada por idioma con «Todos los idiomas», tarjetas con la etiqueta <br>• Postman: `rating` → `recommended` + `language` |
| E | Amigos y perfil (oleada 1) | ✅ Hecho | 2026-10-19 | Commits `8a018ed` (V9) a `f089c06`. <br>• Búsqueda, solicitudes, amistades y perfil público o solo amigos (D49) <br>• Frontend: páginas Amigos y Perfil, interruptor «Perfil público» en Ajustes, enlaces ocultos a developer y admin |
| F | Refresh token (oleada 1) | ✅ Hecho | 2026-10-19 | Commits `c0f29bb` a `3dff325`. <br>• V10, `RefreshTokenService`, `POST /api/auth/refresh` y `/logout`; acceso de 15 min y refresh de 7 días con rotación y familias (D48) <br>• Frontend: `api/client.ts` renueva la sesión ante un 401, una sola vez y compartida <br>• La spec decía «se revocan todos los del usuario»; el código revoca la familia y la spec se corrigió |
| G | Portadas en Cloudinary (oleada 1) | ✅ Hecho | 2026-10-19 | Commits `ebf034a` a `d84acec`. <br>• Subida multipart para developer y admin, validación por bytes y límite de 5 MB (D50) <br>• Frontend: `CoverUpload`, oculto si el servidor no tiene Cloudinary <br>• **Prueba real con una cuenta de Cloudinary: pendiente del usuario** |
| C | Catálogo de 60 juegos y reseñas de demo (oleada 2) | ✅ Hecho | 2026-10-19 | Commits `95fa8d1`, `8129c70`, `854792d` y la fusión `cc2e2d2`. <br>• `scripts/demo-data.json` con 60 juegos de Steam (descripción es/en/it, al menos 5 por género) y 49 estudios <br>• 60 reseñadores que compran y reseñan en tres idiomas: unas 2.100 reseñas, con etiquetas hasta «Muy positivas» <br>• Sin SQL directo: todo por la API; el seed sigue siendo idempotente |
| D | Chatbot recomendador (oleada 2) | ✅ Hecho | 2026-10-19 | Commits `3c3e362`, `fbe9d28`, `83ff6b4` y la fusión `0cd67fa`. <br>• Paquete `recommendation`: OpenRouter con `RestClient`, catálogo en el prompt, ids inventados descartados, límite de 20 mensajes por hora en memoria (D51) <br>• Frontend: botón flotante y panel de chat con tarjetas <br>• **Prueba real con la clave de OpenRouter: pendiente del usuario** |
| Arreglos | Pasada tras la revisión de la oleada 1 | ✅ Hecho | 2026-10-19 | Plan: `docs/superpowers/plans/2026-10-19-oleada-1-arreglos.md`; hasta `44136e1`. <br>• B: bloqueo pesimista al editar o borrar reseñas, la lista se refresca al editar, editar conserva el idioma, el % redondea hacia abajo, cambiar de idioma vuelve a la página 0, fuera los restos de las estrellas <br>• E: solicitudes de cuentas que ya no son jugador no se ven ni se aceptan, enlaces ocultos a developer y admin, cancelar solo borra `PENDING`, búsqueda de 50 caracteres como máximo y respuestas tardías descartadas <br>• F: refresh con UPDATE condicional atómico (`revokeIfActive`) y test de concurrencia, un fallo 5xx o 429 del refresh ya no borra la sesión, `RefreshRequest` limitado a 128 caracteres <br>• G: `max-swallow-size` para el 413, un 400 de Cloudinary da 400 y no 502, logs con el código de Cloudinary y archivo con `type` vacío aceptado <br>• `.\mvnw.cmd clean verify`: **296 tests**, 0 fallos, coverage checks met. Frontend: **65 tests** |
| Arreglos 2 | Pasada tras la revisión de la oleada 2 | ✅ Hecho | 2026-10-19 | Plan: `docs/superpowers/plans/2026-10-19-oleada-2-arreglos.md`. <br>• C: reseña a mano por id de autor, pago rechazado reintentado hasta 3 veces, se saltan los juegos no publicados, índice de reseñas (un relanzamiento pasa de 2627 a 275 llamadas), sin textos repetidos ni frases de precio en los juegos gratis, validador más estricto, 14 descripciones antiguas reescritas <br>• D: ids enormes y `null` en `messages` dan 503/400 y no 500, el catálogo se carga en una transacción corta y el modelo se llama sin transacción, `reply` recortado a 500, catálogo como líneas JSON entre `<catalog>` (contra la inyección), tope global por hora y cooldown tras un 429, chat por usuario (`key`), parser más tolerante, `reasoning` desactivado, catálogo de 200 juegos como máximo y mejoras de accesibilidad <br>• BD demo con V8-V10 aplicadas y el seed relanzado; Newman: 72 peticiones, 113 comprobaciones, 0 fallos <br>• `.mvnw.cmd clean verify`: **311 tests**, 0 fallos, cobertura de instrucciones 95,9 %. Frontend: **69 tests** |
| OpenRouter real | Prueba con la clave del usuario | ✅ Hecho | 2026-10-19 | `google/gemma-4-31b-it:free` daba 429 (proveedor gratuito saturado) y `minimax/minimax-m3.1-flash:free` daba 400 porque exige razonamiento. Arreglo `ab84673`/`5a1341c`: `OPENROUTER_MODEL` admite una lista de reserva (`models`), `reasoning: {effort: low, exclude: true}`, `max_tokens` 1200 y log con el motivo del error, sin la clave. Prueba real: respuestas en es/it en unos 3 s, con juegos del catálogo. **315 tests**, 0 fallos |
| Cloudinary real | Prueba con la cuenta del usuario | ✅ Hecho | 2026-10-19 | La primera subida dio 403 `missing permissions (actions=["create"])`: la API key no tenía permiso de subida. El usuario se lo dio en Cloudinary. Después, subida real de 148 KB en 1,4 s, con `secure_url` https en `arcade/covers/game-<id>`. El botón «Subir portada» y la vista previa funcionan en el formulario del estudio. Ojo: una API key de Cloudinary con permisos limitados no puede subir; hay que usar una con la acción *create* |
| Limpieza Newman | Restos de pruebas en la BD demo | ✅ Hecho | 2026-10-19 | Se borraron los 3 géneros «Roguelike <sufijo>» que habían dejado las pasadas de Newman; no tenían juegos. Ahora la colección borra al final el género que crea (73 peticiones, 114 comprobaciones, 0 fallos) y tras cada pasada siguen 12 géneros. Quedan, sin verse en la tienda, los juegos «Postman Quest <sufijo>» en DELISTED y algunas cuentas `pm_*` |
| Correos en/it reales | Prueba con la cuenta Gmail del usuario | ✅ Hecho | 2026-10-19 | Script por la API contra el backend del 8081: dos cuentas con alias `+` (`mail_en_*` con `locale: en`, `mail_it_*` con `locale: it`). Cada una: registro, pago con tarjeta `…0000` → 402 y pago correcto → PAID (Undertale y XCOM 2). **El usuario confirmó que llegaron los 6 correos** (bienvenida, pago rechazado y recibo) en su idioma y con importes en €. Las dos cuentas se quedan en `capstone_steam_demo` |
| Despliegue | Render: backend, frontend y BD | ✅ Hecho | 2026-10-19 | Con el conector de Render, no con el Blueprint. `capstone-steam-api` (Docker, perfil `prod`), `capstone-steam-web` (*static site*: `cd frontend && npm ci && npm run build`, publica `frontend/dist`, `VITE_API_URL`) y `capstone-steam-db` (PostgreSQL 18 free, caduca el 2026-11-18), todo en Frankfurt. Vercel descartado: no había conector ni CLI. Render solo deja una Postgres free por cuenta; la de car-catalog quedó suspendida. `DB_PASSWORD` la puso el usuario a mano. Admin de producción con contraseña propia. Comprobado: health UP, Swagger, CORS, login, Cloudinary activo, chatbot con juegos del catálogo. Seed: 60 juegos, 80 pedidos, 2.101 reseñas. Sin la regla *Rewrite* (el conector no la crea y no se pudo entrar al panel), `frontend/package.json` tiene un `postbuild` que copia `index.html` como `404.html`: Render lo sirve en las rutas que no existen y React Router las resuelve (`34b97ae`) |
| Remate A | Dos menores del bloque A | ✅ Hecho | 2026-10-19 | `coverUrl` vacía o en blanco se guarda como `null`, como `avatarUrl` (`da83628`). Tests de URLs con espacios alrededor en `coverUrl` y `avatarUrl`: ya daban 400, ahora está cubierto (`5558597`). `clean verify`: **315 tests**, 0 fallos, 95 % de instrucciones |
| Limitaciones | V11 y purga de refresh tokens | ✅ Hecho | 2026-10-20 | Dos subagentes Sonnet en paralelo, cada uno en su worktree, revisados y fusionados (D52). • V11 `ck_friendships_pair` + test en `FlywayMigrationTest` (`94ef6f2`) • Purga programada por familias caducadas + 5 tests (`b22ccce`) • `clean verify`: **321 tests**, 0 fallos, 95 % de instrucciones • Se limpiaron los 16 worktrees ya fusionados |
| D33 | Bloqueo del pedido al pagar | ✅ Hecho | 2026-10-20 | Subagente Sonnet en worktree, revisado y fusionado (`b4e0259`, `5d58773`). `OrderRepository.findOwnForUpdate` (`@Lock(PESSIMISTIC_WRITE)`) en `pay()`; el 404 de pedido ajeno se mantiene (D31). Tests: 16 pagos concurrentes → 1 OK, 15 × 409 y `charge` 1 vez; pedido ajeno → 404. `clean verify`: **323 tests**, 0 fallos, 95 % |
| D16 | Revocación inmediata del access token | ✅ Hecho | 2026-10-20 | Subagente Sonnet en worktree, revisado y fusionado (`a6222f5`, `a775314`). `AccessTokenRevocations` guarda en memoria `userId → instante` al desactivar una cuenta o cambiarle los roles; `JwtAuthFilter` rechaza (401) los tokens con `iat` en ese segundo o antes. Sin consultar la BD por petición. Las marcas se olvidan pasada la vida del token (15 min). Queda: se pierden al reiniciar y no se comparten entre instancias. Bean `Clock` global (`@ConditionalOnMissingBean`) en `SecurityConfig`. Tests: 7 unitarios (antes, después, mismo segundo, caducidad) y 2 de integración (desactivar y cambiar roles → 401 con el token viejo; login nuevo → 200). `clean verify`: **332 tests**, 0 fallos, 95 % |
| Cierre 2026-10-20 | Blueprint, Newman y pruebas en producción | ✅ Hecho | 2026-10-20 | • `render.yaml` refleja el despliegue real: Frankfurt, API + *static site* con la regla *Rewrite* + BD (`232e803`) • Backend local reiniciado en el 8081 con el jar actual: Flyway aplicó **V11** en `capstone_steam_demo` • Postman: nueva petición «Token viejo tras cambiar roles → 401 (D16)». Newman contra el local: **74 peticiones, 115 comprobaciones, 0 fallos** (con `--env-var adminPassword=...`) • Producción: **D33 confirmada** (5 pagos simultáneos → 200 + 4 × 409). **D16 confirmada** en local y en producción con el servicio estable (token viejo → 401). Una primera prueba en producción dio 200 porque coincidió con el cambio de instancia del deploy: la marca vive en la memoria de una sola instancia • Ojo: el deploy de D16 en Render tardó ~15 min y la API dio 503 mientras tanto, sin evento de fallo |
| Demos | Cuatro demos jugables | ✅ Hecho | 2026-10-20 | Plan `docs/superpowers/plans/2026-10-20-demos.md`, subagente Sonnet en worktree, revisión de Opus (5 importantes y 6 menores arreglados en una ronda) y re-revisión limpia; fusión `5bcffa9`. Frontend: **145 tests** en 22 archivos y build correcto. Probadas en el navegador: las cuatro arrancan, Celeste salta y reaparece, Balatro puntúa según la tabla, Vampire Survivors cuenta atrás y elimina enemigos, Rocket League con marcador y tiempo. **Pendiente:** jugarlas enteras para afinar la dificultad (D53) |
| Verificación de email | Registro con verificación obligatoria | ✅ Hecho | 2026-10-21 | Plan `docs/superpowers/plans/2026-10-20-verificacion-email.md`, subagente Sonnet en worktree. Cinco rondas de revisión de seguridad con Opus: dos robos de cuenta corregidos (D54), carreras con bloqueo pesimista, límites por email y por IP, enumeración, borrado seguro de cuentas sin verificar, seed que se recupera de una ejecución a medias. Fusión `7e4e06c`. **432 tests de backend** (96 % de instrucciones), **165 de frontend**, Newman 132 comprobaciones sin fallos, seed probado en BD vacía. Prueba real con el Gmail del usuario en local: correo recibido, enlace con contraseña y sesión iniciada |
| Revisión 2026-10-21 | SQL injection, velocidad y correos | ✅ Hecho | 2026-10-21 | • **SQL injection**: revisadas todas las consultas (JPQL con parámetros, Criteria API, `JdbcTemplate` con `?`, LIKE con comodines escapados, orden del catálogo como enum) y atacada la API local con 15 cargas (`' OR '1'='1`, `; DROP TABLE`, comodines, `sort` con SQL): **ninguna inyección**, los datos intactos. Encontrado un fallo de robustez: un `sort` inventado en los listados de admin y de estudio daba 500. Arreglo `d06dcd9`: lista blanca `SortWhitelist` (400 `catalog.invalidSort`) y `PropertyReferenceException` → 400. • **Correos** sin botones de vuelta a la tienda (bienvenida, recibo, pago rechazado, oferta); solo queda el de verificar el email (`9ca912f`). • **Velocidad en Render**: primera pintura 0,3-0,45 s; portada completa ~1,6 s con el backend despierto; JS 128 KB con brotli; arranque en frío ~200 s. Arreglo `bd93f8a`: caché AOT de Java 25 generada en el `docker build` (entrenamiento sin BD) y `-Xms128m -Xmx384m -XX:+UseSerialGC -XX:TieredStopAtLevel=1` (`bd93f8a`, `fffb333`, `eec9a90`). **En Render el arranque bajó de ~200 s a 59 s** (los ajustes de la JVM solos: 83 s). Dos tropiezos: con `MaxRAMPercentage` la build (mucha RAM) entrenaba sin punteros comprimidos y la caché no mapeaba → heap fijo; y con el heap inicial por defecto (8 MB) la JVM no arrancaba → `-Xms128m` (ese deploy falló y Render mantuvo el anterior). **434 tests** |
| Prueba del sitio 2026-10-21 | Todo el sitio en producción (Playwright) | ✅ Hecho | 2026-10-21 | Todos los flujos bien (público, registro y verificación, compra aprobada #88 y rechazada, biblioteca, reseña, amigos, asistente, estudio, admin, Swagger). Un fallo en el móvil: el menú de arriba no tenía Demos ni Amigos y el botón del perfil estaba oculto (sin «Mi perfil», «Cerrar sesión» ni paneles de estudio y admin). Arreglo `0413628`: Demos, Amigos, Mis juegos y Moderación en el menú móvil (se desplaza si no cabe) y avatar visible que abre el menú de la cuenta. **168 tests de frontend** |
| Eliminar cuentas | Baja propia y por el admin, con anonimización | ✅ Hecho | 2026-10-22 | Spec `docs/superpowers/specs/2026-10-21-eliminar-cuentas-design.md`; subagentes Sonnet en worktree, revisión de Opus (D55). • V13 y regla del último admin con bloqueo y recuento nuevo • Pedir y cancelar la baja, login en pausa y `recover-account` • Anonimización diaria y al arrancar • Ficha DELISTED para los dueños y «Estudio eliminado» • 4 correos • Frontend: Ajustes, login, panel de admin y biblioteca • `ACCOUNT_DELETION_GRACE` y `ACCOUNT_DELETION_CRON` en `.env.example` y `render.yaml` • Revisión final y tanda de arreglos (D55) • `clean verify`: **489 tests**, 0 fallos, 96,3 % de líneas • Frontend: **184 tests** en 25 archivos • Newman: 96 peticiones, 155 comprobaciones, 0 fallos (carpeta nueva «11. Baja de cuenta») |
| CI en rojo desde el 2026-10-21 | Un test fallaba solo en el runner Linux | ✅ Hecho | 2026-10-22 | Desde la fusión de la verificación de email, `GlobalExceptionHandlerTest` fallaba en GitHub Actions: fuera de una petición, `LocaleContextHolder.getLocale()` devuelve el idioma de la JVM (inglés en el runner, español en Windows) y el texto salía en inglés. Arreglo: `Messages.currentLocale()` devuelve el español cuando no hay petición, y lo usan todos los sitios que leían el idioma. `MessagesTest` lo reproduce con `Locale.setDefault(Locale.US)`; la suite entera pasa con `JAVA_TOOL_OPTIONS=-Duser.language=en`. **491 tests** |
| Correo en producción por Brevo | Render free bloquea el SMTP | ✅ Hecho | 2026-10-22 | El correo de verificación no llegaba: desde el 26/09/2025 Render free bloquea el tráfico saliente a los puertos SMTP (25, 465, 587) y cada envío acababa en «Couldn't connect to host smtp.gmail.com:587» tras 3 reintentos. Arreglo: `BrevoMailClient` envía por la API HTTPS de Brevo (`POST /v3/smtp/email`, cabecera `api-key`) cuando hay `BREVO_API_KEY`; sin ella, Gmail SMTP como antes (local). Reintenta red, 429 y 5xx; no reintenta 4xx. Los tests fuerzan la clave vacía (`application-test.yml`) para no enviar nunca correos reales. **499 tests** |
| Notificaciones y chat | Número en «Amigos», avisos y chat uno a uno (D56) | ✅ Hecho | 2026-10-22 | Spec `docs/superpowers/specs/2026-10-22-notificaciones-y-chat-design.md`, plan `...-notificaciones-y-chat.md`. V14 `friend_messages`. `ChatService` (enviar/leer/marcar leído, 30 por minuto en memoria), `GET /api/friends/activity` (dos consultas agregadas) y `conversations` (máx. 50). Frontend: número en los tres menús, aviso pulsable (un solo aviso por consulta; la primera no avisa), `FriendChat` con consulta de 4 s. **D56**: consulta periódica (30 s y al volver a la pestaña) y no WebSocket: más simple y fiable en Render free. Historial guardado al dejar de ser amigos (solo lectura); cuentas en pausa no cuentan; la anonimización borra las conversaciones. Revisión: GET con el otro en pausa daba 404 y el spec dice que se lee (arreglado). **518 tests de backend, 201 de frontend**. Sin Postman (decisión del usuario) |
| Docs | README, handoff, Postman y agentes al día | ✅ Hecho | 2026-10-19 | `README.md` y `frontend/README.md` con las funciones nuevas, endpoints, variables y cifras; spec corregida (familia en vez de todos); Postman reescrita a 73 peticiones (reseñas 👍/👎, refresh y logout, amigos, estado de portadas y asistente con 200 o 503); cifras de los agentes. **Newman sin ejecutar** con la colección nueva |

---

## 8. Riesgos y notas técnicas
- **Cambios de Spring Boot 4:**
  - Los starters son modulares: Flyway necesita `spring-boot-starter-flyway` y `flyway-database-postgresql`.
  - Jackson 3 usa el paquete `tools.jackson`.
  - Hibernate 7.
  - `@MockitoBean` sustituye a `@MockBean`.
  - Los test slices están en módulos separados.
- **Compatibilidad con H2:** H2 no tiene `pg_trgm` ni índices por expresión, por eso la V4 va por vendor. Hay que mantener V1 a V3 en SQL portable.
- **Secretos:** nunca subir el secreto de JWT, la contraseña de Gmail, `CLOUDINARY_URL` (lleva el `api_secret`) ni `OPENROUTER_API_KEY`. Para Gmail hace falta una **contraseña de aplicación**, con la verificación en 2 pasos activada.
- **Gmail SMTP (probado el 2026-10-09):**
  - La contraseña de aplicación solo vale para la cuenta de Google donde se creó: `MAIL_USERNAME` tiene que ser esa misma cuenta. Si no, Gmail responde `535 5.7.8 BadCredentials`.
  - `MAIL_FROM` distinto de `MAIL_USERNAME` solo funciona si es un alias configurado en Gmail como «Enviar como». Lo seguro es dejarlo vacío o igual a `MAIL_USERNAME`.
  - En el puerto 587, Gmail corta la conexión si se autentica con AUTH PLAIN (p. ej. desde una prueba manual). JavaMail usa AUTH LOGIN y funciona; para probar credenciales, mejor hacerlo desde la app.

- **Tipos SQL compatibles con `ddl-auto=validate`:**
  - Se usa `INTEGER` en lugar de `SMALLINT` y `VARCHAR(n)` en lugar de `TEXT`, para que Hibernate 7 valide igual en Postgres y en H2.
  - Las entidades del bloque 2 deben usar `Integer`/`Long`, `BigDecimal` e `Instant`, y String con `length` sin `@Lob`.
- **Rutas de las anotaciones de test en Boot 4.1:**
  - `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`
  - `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase` (con `replace = NONE` para que use el H2 del perfil `test` y las migraciones Flyway)
  - `org.springframework.boot.jpa.test.autoconfigure.TestEntityManager`
- **`pg_trgm`:** `CREATE EXTENSION` requiere un usuario con permisos (en local, `postgres` los tiene).
- **`.env`:** se carga con `spring.config.import: optional:file:.env[.properties]`, así que el archivo tiene que estar en el directorio desde el que se arranca la app.

## 9. Cómo verificar
0. Crear la BD (solo la primera vez):
   `& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -h localhost -c "CREATE DATABASE capstone_steam;"`
1. `docker compose up -d` (solo si se usa Docker en lugar de Postgres local)
2. `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`: Flyway aplica V1 a V13 y Hibernate valida el esquema.
3. `./mvnw test`: todo en verde sobre H2 (518 tests). Frontend: `cd frontend && npm test` (201 tests).
4. Prueba manual: register → login → buscar con filtros → carrito → checkout → biblioteca → reseña.
5. Comprobar que llega el correo y que en los logs el envío aparece en un hilo `mail-*`.
