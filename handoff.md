# HANDOFF: Capstone Steam (backend)

> Documento vivo del proyecto. **Hay que actualizarlo al cerrar cada bloque**: el estado, el registro de progreso y las decisiones.
> Para retomar el trabajo en una sesión nueva: "lee `handoff.md` y continúa con el siguiente bloque pendiente".

---

## 1. Resumen
Proyecto final (capstone) del curso Full Stack de Epicode: una plataforma de videojuegos inspirada en Steam, con escaparate, biblioteca, carrito, wishlist, reseñas y compras simuladas. Este repositorio es el **backend**.

| Campo | Valor |
|---|---|
| Paquete base | `com.capstone.steam` |
| Artifact | `capstone-steam-backend` |
| Estado actual | **✅ Bloques 0 a 11: backend (105 tests, 96 % de cobertura, Swagger) + frontend React (17 tests) + CI + despliegue preparado** |
| Siguiente paso | En GitHub: **https://github.com/SaltyGhosty/capstone-steam-backend** (privado). El primer commit (`0578f9e`) está subido a `main`, que sigue a `origin/main`. Pendiente (necesita cuentas del usuario): <br>• **Gmail**: `MAIL_USERNAME` + `MAIL_APP_PASSWORD` en `.env` y probar el envío real <br>• **Desplegar**: Render (Blueprint con `render.yaml`) + Vercel (`frontend/`), ver README → Despliegue <br>Opcional: refresh token, subir portadas (Cloudinary), amigos/descargas del diseño |
| Última actualización | 2026-09-30 |

### Entorno local (máquina del usuario, Windows 11)
- **JDK:**
  - JDK 25: `C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot` (instalado con winget).
  - El JDK 21 sigue siendo el de por defecto en el PATH, así que antes de `mvnw` hay que hacer:
    `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"`
- **Maven:** no hay `mvn` global; se usa `.\mvnw.cmd` (Maven 3.9.16).
- **Base de datos:** PostgreSQL 18 como servicio de Windows (`postgresql-x64-18`) en `localhost:5432`.
  - Usuario `postgres`; la contraseña está en `.env`.
  - La BD `capstone_steam` ya está creada y tiene aplicadas las migraciones V1 a V4.
- **`.env`:** ya existe (git lo ignora), con `DB_PASSWORD` y un `JWT_SECRET` aleatorio. Siguen vacíos los datos de Gmail (`MAIL_*`) y `ADMIN_PASSWORD`, así que en `dev` todavía no hay admin. Para crearlo basta con añadir `ADMIN_PASSWORD=...` y arrancar la app. `.env.example` ya incluye `ADMIN_*` y `SERVER_PORT`.
- **Datos de demo en `dev`** (cargados el 2026-09-30 a través de la API): <br>• `admin` / `Admin12345!` (creado desde `ADMIN_PASSWORD` en `.env`) <br>• Developers: `demo_dev` (Pixel Forge Studios) y `nebula_dev` (Nebula Games), con 8 juegos publicados <br>• Jugadores: `reviewer_ana`, `reviewer_leo` y `reviewer_sam`, con compras y 10 reseñas <br>• `demo_player`: 1 compra, 1 pago rechazado, 2 juegos en la wishlist y 1 en el carrito <br>• Contraseña de todos (salvo admin): `password123`
- **Tests de MockMvc en Boot 4.1:** `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`. En el perfil `test`, el admin es `admin` / `Admin12345!`.
- **Puerto 8080:** lo ocupa otra app Java del usuario (un JDK 25.0.3 en `AppData\Local\Programs`), así que no hay que cerrarla. Para arrancar esta app se usa otro puerto: `$env:SERVER_PORT="8081"` (o se añade `SERVER_PORT` a `.env`).
- **Docker:** no está instalado. `docker-compose.yml` queda como opción y usa el puerto **5433**.
- **Git:** repo propio en la carpeta del proyecto (antes estaba dentro del repo del home).

### Frontend (hecho el 2026-09-30, en `frontend/`; ver `frontend/README.md`)
- **Cómo se hizo:** se partió del código exportado de Figma Make (`D:\Video Game Website`) y se quitaron los plugins de Figma. Stack: React 19, Vite 8, Tailwind v4, React Router 7 y npm (no hay pnpm instalado). La interfaz está en español y los precios en €.
- **Cobertura de la API:** se usan todos los endpoints (tienda, carrito, checkout y pago, biblioteca, wishlist, reseñas, perfil, panel de developer y panel de admin).
- **Proxy de Vite:** `/api` → `localhost:8081`. El proxy **quita la cabecera `Origin`**: si no, el CORS del backend rechaza con 403 los POST cuando Vite no corre justo en el 5173.
- **Puertos:** el 5174 lo ocupa Agent Office; el 5173 lo usaba `sprout-capstone`, que se cerró a petición del usuario el 2026-09-30.
- **Probado en el navegador con Playwright:** portada, catálogo con filtros (también en móvil), login, pago rechazado (tarjeta `0000`) → 402, pago correcto → PAID, ficha con el formulario de reseña y panel de admin.
- **Datos de demo modificados por las pruebas:** `demo_player` ahora tiene Neon Drift en la biblioteca (pedido #11 PAID, #10 FAILED), el carrito vacío, una reseña de 5★ en Neon Drift y 55 min jugados. El juego de prueba «QA Test Game» (id 17) se creó y se borró. El 2026-09-30 se cambiaron por la API las portadas de los 8 juegos (de picsum a Unsplash/Wikimedia acordes a cada juego; créditos en el README); ahora están en `version` 2.
- **Diseño original en Figma Make:** https://www.figma.com/make/dCxjFlqDGURAtW8CbXbMUu/Video-Game-Website (privado). El código exportado se guardó en `D:\Video Game Website`.
- **Plugins instalados** (ámbito user, marketplace oficial): `frontend-design`, `figma`, `context7`, `playwright`, `modern-web-guidance` y `playground`. La skill `dataviz` ya venía incluida.
- **La API espera el frontend en `http://localhost:5173`** (CORS). El backend corre en el 8081 y la colección de Postman sirve de contrato.

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
- **Correo:** Spring Mail con SMTP de Gmail.
- **Eventos:** Spring Events en fase **AFTER_COMMIT** y `@Async` con un **thread pool propio** para el correo.
- **Tests:** JUnit 5 (Jupiter), Mockito y H2, con pruebas unitarias y de integración.
- **Base de datos:** PostgreSQL, con Docker Compose.

## 3. Decisiones tomadas
| # | Decisión | Origen | Estado |
|---|---|---|---|
| D1 | Roles: USER, DEVELOPER y ADMIN | Usuario | ✅ Cerrada |
| D2 | Pagos simulados (PaymentSimulator), sin pasarela real | Usuario | ✅ Cerrada |
| D3 | Carrito y wishlist | Usuario | ✅ Cerrada |
| D4 | Paquete `com.capstone.steam` | Usuario | ✅ Cerrada |
| D5 | JWT con jjwt 0.12 y un `OncePerRequestFilter` propio | Propuesta | ⏳ Por validar |
| D6 | Paquetes por feature; DTOs como `record` con mappers manuales | Propuesta | ⏳ Por validar |
| D7 | Flyway con las ubicaciones `common` + `{vendor}`: una V4 para PostgreSQL y otra para H2 | Propuesta | ⏳ Por validar |
| D8 | Tests con H2 en `MODE=PostgreSQL` | Propuesta | ⏳ Por validar |
| D9 | Sin Lombok: getters y setters escritos a mano, y constructor `protected` sin argumentos para JPA | Bloque 2 | ✅ Aplicada |
| D10 | Timestamps con `@CreationTimestamp`/`@UpdateTimestamp` de Hibernate, sin `JpaAuditingConfig` | Bloque 2 | ✅ Aplicada |
| D11 | En `Game`, los contadores (`sales_count`, `rating_sum`, `rating_count`) son `insertable=false, updatable=false`: solo cambian con UPDATE atómicos, así que un `save()` nunca pisa ventas o reseñas concurrentes. Estos UPDATE no suben `@Version`; `bulkDiscountForDeveloper` sí la sube | Bloque 2 | ✅ Aplicada |
| D12 | Pedidos: `markPaid` y `closePending(FAILED/CANCELLED)` solo actúan si el estado es `PENDING`. Si devuelven 0 → 409 (evita el doble pago) | Bloque 2 | ✅ Aplicada |
| D13 | Login con username **o** email (`UserRepository.findByLogin`), con el email guardado en minúsculas | Bloque 2 | ✅ Aplicada |
| D14 | Tras una compra, los juegos comprados se quitan también de la wishlist (`deleteByUserIdAndGameIdIn`) | Bloque 2 | ✅ Aplicada |
| D15 | Registro con `accountType` (PLAYER o DEVELOPER). ADMIN nunca se autoasigna: el primer admin lo crea `AdminAccountInitializer` desde `ADMIN_*` y los siguientes los nombra un admin (`PUT /api/admin/users/{id}/roles`) | Bloque 3 | ✅ Aplicada |
| D16 | El JWT lleva el id, el username y los roles, y cada petición se autentica **sin consultar la BD**. Consecuencia: si se desactiva un usuario o cambian sus roles, el cambio no aplica hasta que caduque su token (2 h) | Bloque 3 | ✅ Aplicada |
| D17 | `JwtAuthFilter` no es un `@Component` (evita que se registre dos veces). Un token inválido deja la petición como anónima y el entry point responde 401 | Bloque 3 | ✅ Aplicada |
| D18 | Errores: `GlobalExceptionHandler` extiende `ResponseEntityExceptionHandler` y devuelve ProblemDetail; los errores de validación salen en `errors: {campo: mensaje}`. Los 401 y 403 del filtro los escribe `ProblemResponseWriter` con el `JsonMapper` de Jackson 3 | Bloque 3 | ✅ Aplicada |
| D19 | Las respuestas paginadas usan `PageResponse<T>` (`common/dto`), no `Page` de Spring Data. `hibernate.default_batch_fetch_size=50` evita el N+1 sin paginar en memoria | Bloque 3 | ✅ Aplicada |
| D20 | `JwtService` falla al arrancar si `JWT_SECRET` falta o tiene menos de 32 bytes | Bloque 3 | ✅ Aplicada |
| D21 | **Criteria API** (`GameCriteriaRepository`), cómo está construida: <br>• Los predicados solo se añaden para los filtros presentes y se hacen 2 queries (página y count) con los mismos predicados. <br>• El filtro de géneros es un **subquery `IN`** y no un join con distinct, para no duplicar filas y paginar en la BD. <br>• El precio se compara como `price*(100-discount)` frente a `max*100`, sin dividir. <br>• `minRating` se comprueba como `rating_sum >= min*rating_count`, sin dividir (nunca divide entre 0). <br>• Siempre se desempata por `id`. | Bloque 4 | ✅ Aplicada |
| D22 | NULLS LAST con `CASE WHEN x IS NULL THEN 1 ELSE 0 END`: el `Nulls.LAST` de JPA 3.2 se ignoraba con H2 | Bloque 4 | ✅ Aplicada |
| D23 | El slug se genera a partir del título con `Slugs`, y si ya existe se le añade `-2`, `-3`… **No cambia al renombrar el juego** | Bloque 4 | ✅ Aplicada |
| D24 | Estados del juego: <br>• El developer puede hacer DRAFT → PUBLISHED (con al menos 1 género) y PUBLISHED → DRAFT. <br>• Solo el admin puede retirar un juego (→ DELISTED) y volver a publicarlo (DELISTED → PUBLISHED). <br>• Solo se pueden borrar juegos en DRAFT sin ventas. <br>• El PUT acepta `version` y responde 409 si está desactualizada (bloqueo optimista expuesto al cliente). <br>• Un juego ajeno da 403. | Bloque 4 | ✅ Aplicada |
| D25 | Carrito y wishlist: <br>• Se añade y se quita con **PUT/DELETE idempotentes** (repetir no da error). <br>• `StorefrontRules.requireAcquirable` exige que el juego esté PUBLISHED (si no, 404), que no sea tuyo como developer (409) y que no lo tengas ya (409). <br>• Si un juego deja de estar publicado, sigue en el carrito con `available=false` y no cuenta en el total. | Bloque 5 | ✅ Aplicada |
| D26 | Reseñas: <br>• Solo puede reseñar quien tiene el juego en su biblioteca (403) y hay una por usuario y juego (409). <br>• El rating se actualiza con `applyRatingDelta` en la misma transacción, usando el delta de crear, editar o borrar. <br>• La respuesta se construye **antes** del UPDATE atómico, porque `clearAutomatically` separa las entidades del contexto. <br>• Borran el autor o un ADMIN. | Bloque 5 | ✅ Aplicada |
| D27 | Los listados de usuario (wishlist, biblioteca y reseñas) usan un **orden fijo en el servidor**: solo se aceptan `page` y `size` del `Pageable`, así el cliente no puede ordenar por campos arbitrarios | Bloque 5 | ✅ Aplicada |
| D28 | `@DataJpaTest(showSql = false)`: por defecto `@DataJpaTest` activa `show-sql` e inunda la salida | Bloque 5 | ✅ Aplicada |
| D29 | Checkout en 2 pasos: <br>• **Crear el pedido** congela el carrito: precios snapshot, rechaza juegos no publicados o ya en propiedad, y no vacía el carrito. <br>• **Pagar** vuelve a validar y cobra con `PaymentGateway`. <br>• Si se aprueba: `payment.markSucceeded`, `markPaid` (si da 0 → 409 y rollback), entradas de biblioteca con `order_id`, `incrementSalesCount`, se quitan **solo los juegos comprados** del carrito y la wishlist, y se publica `OrderPaidEvent`. <br>• Si se rechaza: `markFailed`, `closePending(FAILED)`, se publica `PaymentFailedEvent`; la transacción **hace commit** y el controller responde **402**. | Bloque 6 | ✅ Aplicada |
| D30 | Pago simulado (`SimulatedPaymentGateway`, detrás de la interfaz `PaymentGateway`): <br>• Una tarjeta que acaba en `0000` se rechaza siempre. <br>• `PAYMENT_FAILURE_RATE` (0.0 por defecto) permite rechazos aleatorios en demos. <br>• El número de tarjeta **nunca se persiste**. <br>• La referencia es `SIM-<uuid>`. | Bloque 6 | ✅ Aplicada |
| D31 | Un pedido ajeno responde **404, no 403**, para no revelar que existe | Bloque 6 | ✅ Aplicada |
| D32 | Tras un UPDATE atómico con `clearAutomatically`, los proxies LAZY ya no se pueden inicializar: los datos del evento (email, nombre y total) se leen **antes**, y la respuesta se vuelve a consultar al final (`load`) | Bloque 6 | ✅ Aplicada |
| D33 | Limitación conocida: dos "pagar" simultáneos sobre el mismo pedido llaman **dos veces** al gateway. El segundo recibe 409 y su transacción hace rollback, pero con un proveedor real habría que usar una idempotency key | Bloque 6 | 📝 Documentada |
| D34 | Un componente con 2 constructores (uno solo para tests) necesita `@Autowired` en el que debe usar Spring (visto en `SimulatedPaymentGateway`) | Bloque 6 | ✅ Aplicada |
| D35 | Pool `mailExecutor` (2 a 5 hilos, cola 100, prefijo `mail-`): <br>• Con el pool saturado se usa **CallerRunsPolicy**: el correo se envía en el hilo que publicó el evento, ya después del commit. Es más lento, pero no se pierde. <br>• Al apagar la app se espera hasta 30 s a que salgan los correos en cola. <br>• Todos los `@Async` indican el pool por su nombre (`@Async("mailExecutor")`). | Bloque 7 | ✅ Aplicada |
| D36 | `EmailService` **nunca lanza excepciones**: un fallo de SMTP se registra en el log y no afecta a la operación, que ya hizo commit. Si `MAIL_USERNAME` está vacío, el correo queda **desactivado** y solo se registra en el log, para poder desarrollar sin cuenta de Gmail. Si `MAIL_FROM` está vacío, se usa el usuario SMTP como remitente | Bloque 7 | ✅ Aplicada |
| D37 | El test de correo **no es `@Transactional`** (AFTER_COMMIT necesita commits reales) y usa su propio H2 (`jdbc:h2:mem:mail_events`, con `@TestPropertySource`) para no dejar datos en la BD de los demás tests. Comprueba que el envío ocurre en un hilo `mail-*` y que, si la transacción hace rollback, no se envía nada | Bloque 7 | ✅ Aplicada |
| D38 | En surefire, `argLine` es `@{argLine} -javaagent:mockito`, con `<argLine></argLine>` vacío en las properties. Así funciona con JaCoCo, que rellena argLine, y también con `-Djacoco.skip` | Bloque 8 | ✅ Aplicada |
| D39 | Swagger UI y `/v3/api-docs` son públicos también en `prod` (sirven para la presentación). Para ocultarlos: `springdoc.api-docs.enabled=false` y `springdoc.swagger-ui.enabled=false` | Bloque 11 | ✅ Aplicada |
| D40 | Tiempo de juego simulado: no hay cliente real, así que «Jugar» suma 30-90 min con un UPDATE atómico (igual que ventas y rating), sin leer antes la fila | Bloque 11 | ✅ Aplicada |
**Forma de trabajar:**
- Se construye bloque a bloque y cada bloque se valida antes de pasar al siguiente.
- Las respuestas van en español.

---

## 4. Arquitectura
```
com.capstone.steam
├── config/        AsyncConfig (mailExecutor), JpaAuditingConfig, OpenApi (opcional)
├── security/      SecurityConfig, JwtService, JwtAuthFilter, UserDetailsServiceImpl, RestAuthEntryPoint
├── auth/          AuthController, AuthService, dto (RegisterRequest, LoginRequest, AuthResponse)
├── user/          User, Role, UserRepository, UserController (/me)
├── genre/         Genre, GenreRepository, GenreService, GenreController (ADMIN CRUD)
├── game/          Game, GameStatus, GameRepository (UPDATE atómicos), GameService, GameController
│   └── search/    GameSearchCriteria (record), GameCriteriaRepository (Criteria API), PageResponse
├── library/       LibraryEntry, LibraryRepository, LibraryController
├── cart/          CartItem, CartService, CartController
├── wishlist/      WishlistItem, WishlistService, WishlistController
├── review/        Review, ReviewService, ReviewController
├── order/         Order, OrderItem, OrderStatus, Payment, PaymentStatus, CheckoutService, PaymentSimulator
├── notification/  events (UserRegisteredEvent, OrderPaidEvent, PaymentFailedEvent), MailListener, EmailService
└── common/        GlobalExceptionHandler (ProblemDetail), excepciones de dominio, BaseEntity
```
- **Capas:** Controller → Service (`@Transactional`) → Repository.
- **Nunca se exponen entidades**: la API solo trabaja con DTOs.

**Perfiles:**
- `dev`: PostgreSQL con Docker.
- `test`: H2.
- `prod`.

**Configuración:**
- `ddl-auto=validate`: el esquema solo lo gestiona Flyway.
- **Secretos por variables de entorno:** `JWT_SECRET`, `MAIL_USERNAME`, `MAIL_APP_PASSWORD` (contraseña de aplicación de Gmail) y `DB_URL`, `DB_USER`, `DB_PASSWORD`.
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
  - Contadores: `sales_count`, `rating_sum`, `rating_count`.
  - `version` (bloqueo optimista), `created_at` y `updated_at`.
- **`game_genres`:** `(game_id, genre_id)`.

**V2__storefront.sql** (en `common`)
- **`library_entries`:** `id`, `user_id`, `game_id`, `acquired_at`, `playtime_minutes`, `UNIQUE(user_id, game_id)`.
- **`cart_items`:** `user_id`, `game_id`, `added_at`, `UNIQUE(user_id, game_id)`.
- **`wishlist_items`:** `user_id`, `game_id`, `added_at`, `UNIQUE(user_id, game_id)`.
- **`reviews`:** `id`, `user_id`, `game_id`, `rating` (CHECK 1-5), `recommended`, `body`, timestamps, `UNIQUE(user_id, game_id)`.

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

---

## 6. Funcionalidad por fases
### Seguridad JWT
- **Configuración:**
  - Stateless y sin CSRF, con CORS configurado.
  - BCrypt, `JwtAuthFilter` y `@EnableMethodSecurity`.
- **Reglas de acceso:**
  - Públicas: `POST /api/auth/**`, `GET /api/games/**` y `GET /api/genres/**`.
  - `/api/admin/**` exige ADMIN.
  - `/api/developer/**` exige DEVELOPER.
  - El resto requiere estar autenticado.
- **Token:** HS256; los claims son `sub` = userId y `roles`; la expiración es configurable.
- **Endpoints:** `POST /api/auth/register` (publica un `UserRegisteredEvent`), `POST /api/auth/login` y `GET /api/users/me`.
- **Ownership:** un DEVELOPER solo edita sus propios juegos; si no es el dueño, recibe un 403.
- **Errores:** en formato `ProblemDetail` (RFC 9457), con los 401 y 403 en JSON.

### Catálogo (Criteria API)
- **`GET /api/games`:**
  - Filtros: `q`, `genres`, `minPrice`/`maxPrice` (sobre el precio final con descuento), `onSale`, `developerId`, `releasedAfter` y `minRating`.
  - Paginación y orden por price, releaseDate, rating, salesCount o title.
- **Implementación:**
  - Predicados dinámicos y un join a los géneros con `distinct`.
  - Una query aparte para el count.
  - Los géneros se cargan con un fetch en una segunda query para evitar el N+1.

### UPDATE atómicos
- **`incrementSalesCount(gameId)`**
- **`applyRatingDelta(gameId, sumDelta, countDelta)`**
- **`markOrderPaid(orderId)`:** `... WHERE status='PENDING'`. Si devuelve 0, lanza un 409 (evita el doble pago).
- **`bulkDiscountForDeveloper(devId, pct)`**

### Reglas del escaparate
- Solo se puede reseñar un juego que está en tu biblioteca.
- No se puede añadir al carrito un juego que ya tienes.
- Un ADMIN puede retirar juegos de la tienda (DELISTED) y gestionar los géneros.

### Checkout, eventos y correo
- **Checkout:**
  1. Carrito → `Order` PENDING (con snapshot de precios) → `Payment` → `PaymentSimulator`.
  2. Si el pago tiene éxito: `markOrderPaid`, se crean las entradas de biblioteca, `incrementSalesCount`, se vacía el carrito y se publica el `OrderPaidEvent`.
  3. Si falla: el pedido pasa a FAILED y se publica el `PaymentFailedEvent`.
- **`AsyncConfig`:** `ThreadPoolTaskExecutor mailExecutor` (core 2, max 5, cola 100, prefijo `mail-`) y un `AsyncUncaughtExceptionHandler`.
- **`MailListener`:**
  - `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async("mailExecutor")`.
  - Los eventos llevan solo IDs y datos primitivos.
- **`EmailService`:**
  - Gmail: `smtp.gmail.com:587` con STARTTLS.
  - Plantillas: bienvenida, recibo de compra y pago fallido.

### Testing
- **Unitarios (Mockito):** `JwtServiceTest`, `CheckoutServiceTest`, `ReviewServiceTest` y `CartServiceTest`.
- **`@DataJpaTest` con H2 y Flyway real:** `GameCriteriaRepositoryTest` y `GameRepositoryTest` (UPDATE atómicos y doble pago).
- **`@SpringBootTest` + MockMvc:**
  - `AuthIntegrationTest`
  - `CheckoutIntegrationTest`
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

---

## 8. Riesgos y notas técnicas
- **Cambios de Spring Boot 4:**
  - Los starters son modulares: Flyway necesita `spring-boot-starter-flyway` y `flyway-database-postgresql`.
  - Jackson 3 usa el paquete `tools.jackson`.
  - Hibernate 7.
  - `@MockitoBean` sustituye a `@MockBean`.
  - Los test slices están en módulos separados.
- **Compatibilidad con H2:** H2 no tiene `pg_trgm` ni índices por expresión, por eso la V4 va por vendor. Hay que mantener V1 a V3 en SQL portable.
- **Secretos:** nunca subir el secreto de JWT ni la contraseña de Gmail. Para Gmail hace falta una **contraseña de aplicación**, con la verificación en 2 pasos activada.

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
2. `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`: Flyway aplica V1 a V4 y Hibernate valida el esquema.
3. `./mvnw test`: todo en verde sobre H2.
4. Prueba manual: register → login → buscar con filtros → carrito → checkout → biblioteca → reseña.
5. Comprobar que llega el correo y que en los logs el envío aparece en un hilo `mail-*`.
