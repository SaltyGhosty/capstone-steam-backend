# Capstone Steam

Tienda de videojuegos inspirada en Steam: una API REST en Spring Boot y un frontend en React ([`frontend/`](frontend/)) hecho a partir de un diseño de Figma Make. Tiene catálogo con búsqueda avanzada, panel para desarrolladores, carrito, lista de deseos, biblioteca, reseñas y compras con pago simulado. Los correos de confirmación se envían de forma asíncrona.

Es el proyecto final (capstone) del curso Full Stack de **Epicode**.

| | |
|---|---|
| **Backend** | Java 25 · Spring Boot 4.1 · Spring Security + JWT · Spring Data JPA / Hibernate 7 · Flyway · PostgreSQL · Spring Mail (Gmail) · JUnit 5 + Mockito + H2 · Maven |
| **Frontend** | React 19 · Vite 8 · Tailwind CSS v4 · React Router 7 · TypeScript · Vitest + Testing Library |
| **Tests** | Backend: 158 tests, 96 % de líneas cubiertas (JaCoCo) · Frontend: 21 tests · CI en GitHub Actions |
| **API** | Swagger UI en `/swagger-ui.html` · 47 peticiones en [la colección de Postman](postman/Capstone-Steam.postman_collection.json) |

---

## Índice
1. [Funcionalidades](#funcionalidades)
2. [Requisitos técnicos y dónde se cumplen](#requisitos-técnicos-y-dónde-se-cumplen)
3. [Puesta en marcha](#puesta-en-marcha)
4. [Frontend](#frontend)
5. [Arquitectura](#arquitectura)
6. [Base de datos (Flyway V1 a V5)](#base-de-datos-flyway-v1-a-v5)
7. [API](#api)
8. [Tests](#tests)
9. [Despliegue](#despliegue)
10. [Decisiones técnicas](#decisiones-técnicas)

---

## Funcionalidades

**Tres roles.** Nadie puede registrarse como ADMIN: el primero se crea al arrancar y los siguientes los nombra un admin.

| Rol | Puede… |
|---|---|
| **Jugador** (`ROLE_USER`) | Buscar en el catálogo, gestionar su wishlist y su carrito, comprar, ver su biblioteca y reseñar los juegos que tiene |
| **Developer** (`ROLE_DEVELOPER`) | Todo lo anterior, más crear, editar, publicar y retirar **sus** juegos, y aplicar descuentos masivos |
| **Admin** (`ROLE_ADMIN`) | Moderar el catálogo (retirar juegos), gestionar los géneros y los usuarios (roles y activación), regalar juegos y borrar reseñas |

**Flujo de compra:**

1. Se añaden juegos al carrito.
2. `POST /api/orders` crea un pedido **PENDING** con los precios congelados en ese momento.
3. `POST /api/orders/{id}/pay`:
   - Si el pago se aprueba: el pedido pasa a **PAID**, los juegos entran en la biblioteca, suben las ventas, se limpian el carrito y la wishlist y se envía un correo de recibo con la portada y el precio pagado de cada juego, más el total.
   - Si se rechaza: el pedido pasa a **FAILED** (HTTP 402) y se envía un correo de aviso.

Los pagos son **simulados**: una tarjeta terminada en `0000` siempre se rechaza, y así se puede demostrar el caso de error.

**Correos en el idioma del usuario.** Los tres correos (bienvenida, recibo y pago rechazado) salen en español, inglés o italiano, según el idioma guardado en el perfil (`locale`).
- Al registrarse, el frontend envía el idioma del navegador (si no es `es`, `en` ni `it`, usa `es`).
- En *Ajustes → Perfil público*, el selector «Idioma de los correos» permite cambiarlo.
- Los importes siguen en euros, con el formato de cada idioma.
- La interfaz web sigue solo en español.

## Requisitos técnicos y dónde se cumplen

| Requisito | Implementación |
|---|---|
| Spring Security + JWT stateless | [SecurityConfig](src/main/java/com/capstone/steam/security/SecurityConfig.java), [JwtService](src/main/java/com/capstone/steam/security/JwtService.java) (HS256, jjwt 0.13) y [JwtAuthFilter](src/main/java/com/capstone/steam/security/JwtAuthFilter.java) |
| **Criteria API** para los filtros del catálogo | [GameCriteriaRepository](src/main/java/com/capstone/steam/game/search/GameCriteriaRepository.java): 9 filtros opcionales y 6 órdenes, con un subquery para los géneros y paginación en la base de datos |
| **UPDATE atómicos escritos a mano** | [GameRepository](src/main/java/com/capstone/steam/game/GameRepository.java) (`incrementSalesCount`, `applyRatingDelta`, `bulkDiscountForDeveloper`), [OrderRepository](src/main/java/com/capstone/steam/order/OrderRepository.java) (`markPaid`, `closePending`) y [LibraryRepository](src/main/java/com/capstone/steam/library/LibraryRepository.java) (`addPlaytime`) |
| Flyway V1 a V4 (y V5) | [db/migration](src/main/resources/db/migration) (ver [abajo](#base-de-datos-flyway-v1-a-v5)) |
| Spring Mail + Gmail SMTP | [EmailService](src/main/java/com/capstone/steam/notification/EmailService.java), [EmailTemplates](src/main/java/com/capstone/steam/notification/EmailTemplates.java) y [MailTexts](src/main/java/com/capstone/steam/notification/MailTexts.java) (textos en es, en e it) |
| Spring Events **AFTER_COMMIT** + `@Async` con **pool propio** | [MailListener](src/main/java/com/capstone/steam/notification/MailListener.java) y [AsyncConfig](src/main/java/com/capstone/steam/config/AsyncConfig.java) (`mailExecutor`, hilos `mail-*`) |
| JUnit 5 + Mockito + H2 | [src/test](src/test/java/com/capstone/steam): tests unitarios, de repositorio, de integración HTTP y de concurrencia |
| PostgreSQL + Docker Compose | [docker-compose.yml](docker-compose.yml) (o un PostgreSQL local) |
| Frontend | [frontend/](frontend/): React consumiendo toda la API ([detalles](frontend/README.md)) |

## Puesta en marcha

### Requisitos
- **JDK 25.** Si tienes otro JDK por defecto, apunta `JAVA_HOME` al 25 antes de usar `mvnw`.
- **PostgreSQL 15 o superior**, local o con Docker. No hace falta instalar Maven: se usa `mvnw`.

### 1. Base de datos
```bash
# Opción A: PostgreSQL local
psql -U postgres -c "CREATE DATABASE capstone_steam;"

# Opción B: Docker (expone el puerto 5433 para no chocar con un Postgres local)
docker compose up -d
```

### 2. Configuración
```bash
cp .env.example .env    # y rellena los valores
```

| Variable | Obligatoria | Descripción |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | ✅ | Conexión a PostgreSQL (con Docker: `jdbc:postgresql://localhost:5433/capstone_steam`) |
| `JWT_SECRET` | ✅ | Clave en Base64 de al menos 32 bytes. La app no arranca sin ella |
| `ADMIN_USERNAME`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Recomendada | Si `ADMIN_PASSWORD` tiene valor, se crea el admin inicial al arrancar |
| `MAIL_USERNAME`, `MAIL_APP_PASSWORD` | Opcional | Cuenta de Gmail y [contraseña de aplicación](https://myaccount.google.com/apppasswords). Si están vacías, los correos solo se registran en el log |
| `MAIL_FROM_NAME` | Opcional | Nombre con el que llegan los correos y marca que aparece en ellos (por defecto `ARCADE`) |
| `FRONTEND_URL` | Opcional | URL de la tienda para los botones de los correos. Si falta, se usa el primer origen de `CORS_ALLOWED_ORIGINS` |
| `SERVER_PORT` | Opcional | Por defecto `PORT` (lo fija Render) o 8080 (`.env.example` usa 8081) |
| `PAYMENT_FAILURE_RATE` | Opcional | Probabilidad de rechazo aleatorio en las demos (0.0 a 1.0, por defecto 0) |
| `CORS_ALLOWED_ORIGINS` | Opcional | URL del frontend (por defecto `http://localhost:5173`) |

### 3. Arrancar
```bash
./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run
```
Flyway crea el esquema al arrancar y Hibernate lo valida (`ddl-auto=validate`). Para comprobar que funciona: `GET http://localhost:8081/actuator/health` debe devolver `{"status":"UP"}`.

### 4. Probar
Con la app arrancada, **Swagger UI** está en `http://localhost:8081/swagger-ui.html`: el botón *Authorize* acepta el `accessToken` del login.

También puedes importar [`postman/Capstone-Steam.postman_collection.json`](postman/Capstone-Steam.postman_collection.json) y ejecútala con el **Collection Runner**. Es un flujo completo: registro, publicación de un juego, compra y reseña. Los tokens y los ids se guardan solos. Para la carpeta *Admin*, pon tu `ADMIN_PASSWORD` en la variable `adminPassword`.

## Frontend

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173 (el backend debe estar en el 8081)
```

ARCADE es la tienda completa: portada con destacados, catálogo con todos los filtros de la Criteria API, ficha del juego con reseñas, carrito y pago simulado, pedidos, biblioteca (con sesiones de juego simuladas), wishlist, ajustes, panel del estudio para developers y panel de moderación para admins. Todo se explica en [frontend/README.md](frontend/README.md).

**Usuarios demo** (tras ejecutar `node scripts/seed-demo.mjs`): `demo_player` (jugador con biblioteca, wishlist, carrito y un pago rechazado) y las cuentas de los estudios, como `team_cherry` (tiene un borrador para publicar), todas con la contraseña `password123`; y `admin` / el `ADMIN_PASSWORD` de tu `.env`.

## Arquitectura

El código se organiza **por funcionalidad** y cada una sigue las capas `Controller → Service (@Transactional) → Repository`. Los controllers solo devuelven DTOs (`record`), nunca entidades.

```
com.capstone.steam
├── auth/          registro y login
├── security/      JWT, filtro, SecurityConfig y respuestas 401/403
├── user/          perfil, administración de usuarios y admin inicial
├── genre/         géneros
├── game/          CRUD de developer, moderación de admin y catálogo público
│   └── search/    Criteria API (GameCriteriaRepository, GameSearchCriteria, GameSort)
├── cart/  wishlist/  library/  review/
├── order/         pedidos, checkout y pagos
│   └── payment/   PaymentGateway + SimulatedPaymentGateway
├── notification/  eventos, MailListener, EmailService, EmailTemplates y MailTexts
├── config/        AsyncConfig (pool mailExecutor) y OpenApiConfig (Swagger)
└── common/        GlobalExceptionHandler (ProblemDetail), excepciones, PageResponse y Slugs
```

**Flujo de un pago aprobado:**
```
POST /api/orders/{id}/pay
 └─ OrderService.pay()                               ── una transacción ──
     ├─ PaymentGateway.charge()                          (simulado)
     ├─ OrderRepository.markPaid()   UPDATE … WHERE status='PENDING'   → 0 filas = 409
     ├─ biblioteca (INSERT) · GameRepository.incrementSalesCount()     (UPDATE atómico)
     ├─ se limpian el carrito y la wishlist
     └─ publishEvent(OrderPaidEvent)
                                                      ── COMMIT ──
 MailListener.onOrderPaid()   @TransactionalEventListener(AFTER_COMMIT)
                              @Async("mailExecutor")  → hilo mail-N → Gmail SMTP
```
La respuesta HTTP no espera al SMTP. Si la transacción hace rollback, no se envía ningún correo. Y si falla el correo, se registra en el log sin afectar a la compra.

**El recibo de compra** lista cada juego con una miniatura de su portada (64×36), el título y el precio pagado con descuento (`OrderItem.unitPrice`), y debajo el total. `OrderPaidEvent` lleva esas líneas ya resueltas: se construyen dentro de la transacción de `pay()`, porque el listener corre después del commit y en otro hilo, donde `Game` (LAZY) ya no se puede cargar. La portada solo se enlaza si la URL empieza por `http://` o `https://` (`EmailTemplates.safeImageUrl`); si no, queda una celda vacía del mismo ancho. Todo el texto se escapa con `HtmlUtils`.

**El idioma de los correos** sale de `users.locale`. Los textos de cada idioma están en el record `MailTexts` (una constante por idioma), así el compilador obliga a que ninguno se quede sin traducir. Los tres eventos llevan el `locale`, leído del usuario dentro de la transacción. Si falta o no se reconoce, el correo sale en español.

## Base de datos (Flyway V1 a V5)

| Migración | Contenido |
|---|---|
| **V1** `base_schema` | `roles` (con datos iniciales), `users`, `user_roles`, `genres` (12 iniciales), `games` (precio, descuento, estado, contadores y `version`) y `game_genres` |
| **V2** `storefront` | `library_entries`, `cart_items`, `wishlist_items` y `reviews` (con `UNIQUE(user, game)` en todas) |
| **V3** `orders_payments` | `orders` (PENDING, PAID, FAILED o CANCELLED), `order_items` (precio congelado), `payments` y `library_entries.order_id` |
| **V4** `catalog_indexes` | Índices del catálogo. En PostgreSQL: **trigramas `pg_trgm` (GIN)** sobre `lower(title)` para `LIKE '%texto%'` y un índice parcial para "más vendidos" |
| **V5** `user_locale` | `users.locale` (idioma de los correos): `es` por defecto, con un `CHECK` que solo admite `es`, `en` o `it` |

El enunciado pide V1 a V4; V5 se añadió después para el idioma de los correos. V1 a V3 y V5 son SQL portable. V4 tiene dos versiones ([postgresql](src/main/resources/db/migration/postgresql) y [h2](src/main/resources/db/migration/h2)), porque H2 no soporta los índices por trigramas ni los parciales. Flyway elige la adecuada con `locations: …/common, …/{vendor}`.

Las reglas de integridad también las garantiza la base de datos, no solo el código Java: `CHECK` (precio ≥ 0, descuento 0 a 100, nota 1 a 5, estados válidos), `UNIQUE` y claves foráneas con `ON DELETE` pensado caso por caso.

## API

La autenticación es `Authorization: Bearer <accessToken>`. El token se obtiene en el registro o el login y dura 2 h.

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/api/auth/register` | público | Registro (`accountType`: PLAYER o DEVELOPER; `locale` opcional: `es`, `en` o `it`, por defecto `es`) → 201 + token |
| POST | `/api/auth/login` | público | Login con username **o** email |
| GET / PATCH | `/api/users/me` | usuario | Ver y editar el perfil (incluido `locale`, el idioma de los correos) |
| GET | `/api/genres` | público | Géneros |
| GET | `/api/games` | público | Catálogo con filtros (ver abajo) |
| GET | `/api/games/{slug}` | público | Ficha del juego |
| GET | `/api/games/{slug}/reviews` | público | Reseñas paginadas |
| GET / POST | `/api/developer/games` | developer | Mis juegos / crear (DRAFT) |
| GET / PUT / DELETE | `/api/developer/games/{id}` | developer (dueño) | Ver, editar (con `version` → 409 si está desactualizada) y borrar (solo borradores sin ventas) |
| POST | `/api/developer/games/{id}/publish` · `/unpublish` | developer (dueño) | DRAFT ↔ PUBLISHED |
| POST | `/api/developer/games/discount` | developer | Descuento masivo a los juegos publicados |
| GET / DELETE | `/api/cart` | usuario | Ver y vaciar el carrito |
| PUT / DELETE | `/api/cart/items/{gameId}` | usuario | Añadir y quitar (idempotentes) |
| GET | `/api/wishlist` · PUT / DELETE `/api/wishlist/{gameId}` | usuario | Lista de deseos |
| GET | `/api/library` | usuario | Mi biblioteca |
| GET | `/api/library/status/{gameId}` | usuario | ¿Lo tengo? ¿Está en el carrito o la wishlist? ¿Lo he reseñado? |
| POST | `/api/library/{gameId}/play` | usuario | Sesión de juego simulada: suma `minutes` (1 a 1440) al tiempo jugado. 404 si no lo tienes |
| POST | `/api/orders` | usuario | Checkout: carrito → pedido PENDING |
| POST | `/api/orders/{id}/pay` | usuario | Pagar → 200 PAID · **402** FAILED · 409 si ya está procesado |
| POST | `/api/orders/{id}/cancel` | usuario | Cancelar un pedido pendiente |
| GET | `/api/orders` · `/api/orders/{id}` | usuario | Historial y detalle |
| POST | `/api/reviews` · PUT / DELETE `/api/reviews/{id}` | usuario | Reseñar un juego de mi biblioteca (autor; el admin también puede borrar) |
| GET | `/api/admin/users` | admin | Usuarios paginados |
| PUT | `/api/admin/users/{id}/roles` | admin | Cambiar roles |
| PATCH | `/api/admin/users/{id}/enabled` | admin | Activar o desactivar cuenta |
| POST | `/api/admin/users/{userId}/library/{gameId}` | admin | Regalar un juego |
| POST / PUT / DELETE | `/api/admin/genres[/{id}]` | admin | CRUD de géneros |
| GET | `/api/admin/games?status=` | admin | Juegos por estado |
| PATCH | `/api/admin/games/{id}/status` | admin | Retirar (DELISTED) o volver a publicar |

**Búsqueda del catálogo**: todos los filtros son opcionales y combinables.
```
GET /api/games?q=hollow&genres=indie,platformer&minPrice=5&maxPrice=20&onSale=true
              &minRating=4&releasedAfter=2017-01-01&sort=price_asc&page=0&size=20
```
Valores de `sort`: `newest` (por defecto), `price_asc`, `price_desc`, `top_rated`, `best_selling` y `title`. Los filtros de precio usan el precio con descuento.

**Idioma de los correos (`locale`)**: acepta solo `es`, `en` o `it`, en minúsculas. Otro valor responde 400 con `errors.locale`. Sin `locale`, el registro usa `es` y el `PATCH` no lo cambia. Sale siempre en el usuario de la respuesta (`UserResponse`, también dentro de la respuesta del login y el registro) y no va dentro del JWT.

**Errores**: todos siguen el formato estándar ProblemDetail (RFC 9457).
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "La petición contiene campos no válidos",
  "instance": "/api/auth/register",
  "errors": { "email": "must be a well-formed email address" }
}
```

## Tests

```bash
./mvnw test      # 158 tests (H2 en memoria, no necesita PostgreSQL)
./mvnw verify    # + informe de cobertura en target/site/jacoco/index.html (falla si baja del 80 % de líneas)
```

| Tipo | Ejemplos | Qué comprueban |
|---|---|---|
| Unitarios (Mockito) | `JwtServiceTest`, `AuthServiceTest`, `OrderServiceTest`, `ReviewServiceTest`, `CartServiceTest` | Reglas de negocio aisladas: tokens manipulados o caducados, delta de rating, pago aprobado o rechazado |
| Repositorio (`@DataJpaTest` + Flyway real) | `GameCriteriaRepositoryTest`, `RepositoryIntegrationTest` | Filtros y órdenes de Criteria API, UPDATE atómicos y restricciones de la base de datos |
| Integración HTTP (MockMvc) | `AuthIntegrationTest`, `CatalogIntegrationTest`, `CheckoutIntegrationTest`… | Flujos completos con JWT real, 401/403/404/409/402 y el formato ProblemDetail |
| Correo asíncrono | `MailEventIntegrationTest`, `EmailServiceTest` | El correo sale **después del commit**, en un hilo `mail-*`, y **no** sale si hay rollback. Las plantillas escapan el HTML, el recibo solo enlaza portadas `http(s)` y cada correo sale en el idioma del usuario (es, en o it, con español si falta) |
| Concurrencia | `AtomicUpdateConcurrencyTest` | 16 hilos reales: ninguna venta se pierde y **exactamente 1** pago gana el pedido |

Los tests usan H2 en modo PostgreSQL **con las mismas migraciones de Flyway** que producción; no hay `ddl-auto=create`.

**Frontend** (`cd frontend && npm test`): 21 tests en 6 archivos, con Vitest y Testing Library. Cubren el cliente HTTP (token, errores ProblemDetail, 401 y 402), los formateadores, la detección del idioma del navegador y los flujos de login, registro, checkout (pedido → pago aprobado o rechazado) y ajustes (selector del idioma de los correos) sobre las páginas reales con `fetch` simulado.

**CI**: [`.github/workflows/ci.yml`](.github/workflows/ci.yml) ejecuta `./mvnw verify` y los tests y el build del frontend en cada push a `main`.

## Despliegue

El repositorio incluye todo lo necesario; solo faltan las cuentas.

1. **Backend + PostgreSQL en Render.** Ve a *New → Blueprint* y elige este repositorio: [`render.yaml`](render.yaml) crea la base de datos y el servicio web a partir del [`Dockerfile`](Dockerfile). `JWT_SECRET` se genera solo. Al crearlo se piden `CORS_ALLOWED_ORIGINS` (la URL del frontend), `ADMIN_PASSWORD` y, opcionalmente, `MAIL_USERNAME` y `MAIL_APP_PASSWORD`.
2. **Frontend en Vercel.** Importa el repositorio con *Root Directory* = `frontend` (el [`vercel.json`](frontend/vercel.json) ya configura el build y las rutas) y define `VITE_API_URL` con la URL del backend en Render.
3. Vuelve a Render y pon en `CORS_ALLOWED_ORIGINS` la URL final de Vercel.
4. Carga el catálogo demo en la base de datos nueva: `node scripts/seed-demo.mjs --api https://<tu-servicio>.onrender.com`.

En el plan gratuito de Render, el backend se duerme tras 15 minutos sin tráfico y la primera petición tarda unos 30-60 s en despertarlo.

## Datos demo y créditos

[`scripts/seed-demo.mjs`](scripts/seed-demo.mjs) carga, **a través de la API**, un catálogo de 23 juegos reales (22 publicados y *Hollow Knight: Silksong* como borrador), 21 estudios, 6 jugadores, 20 pedidos (uno rechazado), 32 reseñas, wishlists y tiempo de juego. Los datos están en [`scripts/demo-data.json`](scripts/demo-data.json) y se puede ejecutar varias veces sin duplicar nada:

```bash
node scripts/seed-demo.mjs                                   # contra http://localhost:8081
node scripts/seed-demo.mjs --api https://tu-api.onrender.com # contra el despliegue
```

Las portadas, cápsulas e imágenes de cabecera se enlazan desde el CDN de Steam (`cdn.cloudflare.steamstatic.com`), tanto en la tienda como en el correo de recibo. Los nombres, las imágenes y las marcas pertenecen a sus respectivos estudios y editores; se usan solo como datos de demostración en este proyecto educativo. Los textos de las descripciones y las reseñas son propios.

## Decisiones técnicas

La justificación de cada decisión (D1 a D45) está en [handoff.md](handoff.md). Estas son las más relevantes:

- **Contadores protegidos.** `sales_count`, `rating_sum` y `rating_count` son de solo lectura para JPA (`insertable/updatable = false`) y solo cambian con UPDATE atómicos. Así, un developer que edita su juego nunca pisa una venta o una reseña concurrente.
- **Máquina de estados del pedido en SQL.** `WHERE status = 'PENDING'` garantiza que un pedido solo se paga una vez, aunque se haga doble clic o lleguen peticiones concurrentes. Además, `UNIQUE(user_id, game_id)` impide tener el mismo juego dos veces en la biblioteca.
- **Pago rechazado = commit.** El pedido FAILED y el intento de pago se guardan para dejar constancia; el controller responde 402 sin lanzar una excepción.
- **Criteria sin trampas.**
  - El filtro de géneros usa un subquery `IN`, sin join ni `DISTINCT`.
  - Los precios y las notas se comparan sin dividir, así que nunca hay una división entre cero.
  - Los NULL van al final con un `CASE` portable.
  - Siempre se desempata por `id` para que la paginación sea estable.
- **Bloqueo optimista expuesto al cliente.** El `PUT` de un juego acepta `version`; si está desactualizada, responde 409 en lugar de sobrescribir los cambios de otra pestaña.
- **Sin filtraciones de información.** Un pedido ajeno devuelve 404 (no 403), los listados usan un orden fijo en el servidor y los datos del usuario se escapan en los correos HTML.

### Limitaciones conocidas
- El JWT no consulta la base de datos en cada petición. Si se desactiva un usuario o cambian sus roles, el cambio no se aplica hasta que caduca su token (2 h). No hay refresh tokens.
- Si llegan dos pagos simultáneos del mismo pedido, la pasarela simulada se llama dos veces, aunque solo uno se completa y el otro hace rollback. Con un proveedor real habría que usar una idempotency key.
- En los correos en inglés e italiano, el motivo de un pago rechazado todavía sale en español (lo genera la pasarela simulada).
- La portada del recibo se carga desde la URL que puso el developer, así que ese servidor puede saber cuándo se abre el correo (como un píxel de seguimiento). Pasa lo mismo en la tienda web. Gmail lo atenúa porque sirve las imágenes a través de su proxy. La solución sería alojar las portadas en un almacenamiento propio.
