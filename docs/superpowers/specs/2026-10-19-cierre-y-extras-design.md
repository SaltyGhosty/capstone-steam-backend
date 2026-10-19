# Cierre y extras del capstone: diseño

Fecha: 2026-10-19 · Rama de partida: `frontend-y-cierre` (commit `8e980c7`)

## 1. Intención

El capstone de Epicode (tienda de videojuegos tipo Steam: Spring Boot 4 + React) ya cumple todos los
requisitos del enunciado (ver `handoff.md` §2 y la nota «Requisitos del enunciado» de la bóveda).
Quedan **1-2 semanas** hasta la entrega. El usuario quiere repartir el tiempo **mitad y mitad**:
primero cerrar lo pendiente para que la demo no tenga sorpresas, luego sumar extras que luzcan ante
el tribunal y que respondan a limitaciones conocidas.

ARCADE es un **escaparate con compra simulada**: sirve para ver juegos y simular la compra, como
ahora. No es un lanzador.

## 2. Objetivos, no objetivos, anti-objetivos

**Objetivos** (en este orden; si falta tiempo se corta desde abajo):
1. Cierre: arreglos de seguridad menores, motivo de rechazo traducido, Newman, documentación al día.
2. Reseñas estilo Steam: 👍/👎 con porcentaje y etiqueta, y filtradas por idioma.
3. Catálogo ampliado a unos **60 juegos** importados de la tienda de Steam.
4. Chatbot que recomienda juegos del catálogo a partir de adjetivos, con **OpenRouter**.
5. Amigos y perfil (público o solo para amigos).
6. Refresh token.
7. Subida de portadas a **Cloudinary**.

**No objetivos**
- **Descargas**: el usuario las descarta expresamente. No hay instalador ni botón de descarga.
- Desplegar en Render/Vercel: lo hará el usuario más adelante. El plan solo deja todo listo
  (variables nuevas en `.env.example`, `render.yaml` y README).
- Traducir el texto de las reseñas. Steam no lo hace; se filtran por idioma.
- Historial persistente del chatbot.

**Anti-objetivos** (fallaría aunque funcione):
- Romper un requisito del enunciado (Criteria API, UPDATE atómicos, Flyway, AFTER_COMMIT, JWT stateless…).
- Bajar la calidad actual: hoy 158 tests de backend con ~96 % de cobertura y 21+ de frontend. Cada
  función llega con sus tests (JUnit 5 + Mockito + H2 en backend, Vitest en frontend).
- Que la app falle o muestre errores si falta una clave opcional (OpenRouter, Cloudinary).
- Secretos en git o en el navegador.
- Que el chatbot recomiende juegos que no están en el catálogo.

## 3. Restricciones

- Stack fijado: Java 25, Spring Boot 4.1.1, jjwt 0.13, Flyway, PostgreSQL 18 (H2 en `MODE=PostgreSQL`
  en tests), React 19 + Vite 8 + Tailwind v4 + React Router 7, npm. Ver `handoff.md` §1.
- Migraciones: ya existen V1-V7 (`common/` + `postgresql/`/`h2/` para V4). Las nuevas empiezan en
  **V8**. Si una migración necesita SQL distinto por vendor, usar las carpetas `postgresql/` y `h2/`
  como V4.
- Interfaz y correos en es/en/it (`frontend/src/i18n/messages.ts`, `MailTexts`). Todo texto nuevo
  va en los tres idiomas.
- Entorno local: backend en el **8081** (el 8080 está ocupado), JDK 25 vía `JAVA_HOME` (ver
  `handoff.md` §1), sin Docker.
- **Git: un commit por cada función o arreglo, y nunca Claude como co-author** (sin
  `Co-Authored-By`, sin «Generated with»). Exigencia del usuario.
- Cada bloque termina actualizando `handoff.md`, la colección de Postman, Swagger y la bóveda de
  Obsidian (`C:\Users\Salty\Desktop\CAPSTONE EPICODE\Capstone Epicode`, sin tocar `.obsidian`).

## 4. Diseño por bloque

### Bloque A: cierre

Arreglos (todos verificados abiertos el 2026-10-19):
- `game/dto/GameRequest.java`: `@URL` en `coverUrl` acepta cualquier esquema → solo `http`/`https`
  (p. ej. `@URL(regexp = "^https?://.*")` o `protocol`), con test.
- `notification/EmailTemplates.safeImageUrl`: acepta `http://` → solo `https://`.
- `notification/EmailService` (líneas ~90-107): escribe el email del destinatario en el log →
  enmascararlo (`j***@gmail.com`).
- `order/payment/SimulatedPaymentGateway`: el motivo de rechazo va en español en los correos en/it
  → devolver un código de motivo (enum) y traducirlo en `MailTexts`.
- Ejecutar Newman con la colección de Postman actual (bodies con `locale`).
- Probar en el navegador (Playwright) el selector de idioma de la interfaz y el de idioma de los correos.

Documentación desactualizada que corregir:
- La bóveda dice «interfaz solo en español»: falso, ya hay es/en/it con banderas
  (`LanguageSwitcher`). Corregir «Limitaciones conocidas», «Requisitos del enunciado», etc.
- `handoff.md` §4 y §6 y decisiones D2 y D5; `frontend/README.md`; cifras viejas en
  `equipo-agentes/*.md` (ver nota «Pendientes» de la bóveda).

### Bloque B: reseñas estilo Steam

Estado actual: `Review` tiene `rating` (1-5) **y** `recommended` (boolean). `Game` tiene
`rating_sum`/`rating_count` mantenidos con UPDATE atómico (`GameRepository`, requisito del
enunciado). El catálogo filtra por `minRating` y ordena por media (`GameCriteriaRepository`).

Cambios:
- **Quitar las estrellas.** Una reseña es solo 👍 Recomendado / 👎 No recomendado + texto. Se usa el
  `recommended` que ya existe; `rating` desaparece del API y de la BD.
- **Contadores**: `games.positive_count` + el `review_count` existente (renombrar `rating_count` o
  mantenerlo; a criterio del builder). Se siguen actualizando con **UPDATE atómico** `@Modifying
  @Query` (crear, editar cambiando el voto, borrar). La migración recalcula los contadores desde
  las reseñas existentes.
- **Idioma**: columna `reviews.language` (`es`/`en`/`it`). Al crear, la manda el frontend con el
  idioma actual de la interfaz; si no llega, se usa `users.locale`. La migración rellena las
  existentes con el `locale` del autor.
- **Puntuación**: el backend calcula `positivePercent` y un código `reviewScore` con las reglas de Steam:

  | Código | % positivo | Nº reseñas |
  |---|---|---|
  | `OVERWHELMINGLY_POSITIVE` | 95-100 | 500+ |
  | `VERY_POSITIVE` | 80-100 | 50+ (o 500+ con 80-94) |
  | `POSITIVE` | 80-100 | 10-49 |
  | `MOSTLY_POSITIVE` | 70-79 | 10+ |
  | `MIXED` | 40-69 | 10+ |
  | `MOSTLY_NEGATIVE` | 20-39 | 10+ |
  | `NEGATIVE` | 0-19 | 10-49 |
  | `VERY_NEGATIVE` | 0-19 | 50-499 |
  | `OVERWHELMINGLY_NEGATIVE` | 0-19 | 500+ |
  | `NONE` | — | menos de 10 |

  El frontend traduce el código con los nombres oficiales de Steam (es: «Extremadamente positivas»,
  «Muy positivas», «Positivas», «Mayormente positivas», «Variadas», «Mayormente negativas»,
  «Negativas», «Muy negativas», «Extremadamente negativas»; it: «Estremamente positive», «Molto
  positive», «Positive», «Perlopiù positive», «Nella media», «Perlopiù negative», «Negative»,
  «Molto negative», «Estremamente negative»; en: «Overwhelmingly Positive»…). Con `NONE` muestra
  «N reseñas de usuarios». Color: azul para positivas, amarillo para variadas, rojo para negativas,
  como Steam.
- **Ficha del juego**: puntuación global (todas las reseñas, todos los idiomas, como Steam) +
  lista de reseñas filtrada por el idioma de la interfaz, con un control «Todos los idiomas».
  Endpoint de reseñas con parámetro `language` opcional.
- **Catálogo**: `minRating` pasa a `minPositivePercent`; el orden «mejor valorados» usa el porcentaje
  (Criteria API, sin dividir entre 0, como hoy). Los juegos con **menos de 10 reseñas** no pasan el
  filtro `minPositivePercent` y van al final en «mejor valorados» (así 1 reseña al 100 % no supera
  a 30 al 95 %). Decisión de Claude.
- Tarjetas (`GameCard`) muestran la etiqueta o el porcentaje en vez de estrellas.
- Datos de demo: cada reseña con su idioma; mezcla es/en/it.

### Bloque C: catálogo a ~60 juegos

Estado actual: 23 juegos en `scripts/demo-data.json`, sembrados por `scripts/seed-demo.mjs`, cada
uno con `steamAppId` y un estudio (cuenta DEVELOPER). Ya existe `POST /api/developer/games/import`
(`steamstore/SteamImportService`), que importa un juego desde la tienda de Steam por AppID.

Cambios:
- Ampliar `demo-data.json` hasta **~60 juegos** reales, variados, que cubran todos los géneros
  (al menos 5 por género) y con descripción en es/en/it en Steam. Cada juego nuevo con su estudio.
- El seed importa los juegos nuevos con el endpoint de importación existente (respetando el
  límite de peticiones de Steam con una pausa entre llamadas). `SteamImportService` solo mapea 8
  géneros de Steam (Platformer, Puzzle, Horror, Shooter no existen allí), así que después del import
  el seed **corrige géneros y precio con un `PUT`** según `demo-data.json`. Decisión de Claude.
- Correo: las cuentas de demo usan `@demo.capstone-steam.local`, que `EmailService` ya descarta como
  dominio reservado. El seed ampliado no manda correos reales ni gasta cupo de Gmail.
- **Reseñas de demo**: para que salgan etiquetas hacen falta 10+ reseñas por juego. Solo un dueño
  puede reseñar, una vez por juego, así que **todo pasa por el API** (decisión del usuario: nada de
  SQL directo). El seed crea **~60 cuentas de reseñadores** nuevas (contraseña `password123` como el
  resto) que compran por el checkout simulado y luego reseñan. Así se llega a «Muy positivas»
  (50+); las «Extremadamente…» (500+) no salen en la demo y se explica en la defensa. Genera reseñas con
  una proporción positiva verosímil por juego (idea: tomarla del `query_summary` de
  `store.steampowered.com/appreviews/{appid}?json=1`, que da el total real de positivas/negativas).
  Los textos salen de un banco de frases cortas por idioma y por voto, guardado en
  `demo-data.json`. No se copian reseñas reales de usuarios de Steam.
- El seed sigue siendo idempotente (relanzarlo no duplica nada).

### Bloque D: chatbot recomendador (OpenRouter)

Momento de uso: el usuario abre el chat y escribe adjetivos («oscuro, difícil, con buena
historia»); el bot contesta con un texto corto y tarjetas de juegos del catálogo.

- **Frontend**: botón flotante en la tienda que abre un panel de chat. Respuesta = texto + tarjetas
  (`GameCard` o una versión compacta: portada, título, precio, etiqueta de reseñas) que abren la
  ficha. Responde en el idioma de la interfaz. La conversación vive en memoria: se pierde al recargar.
- **Backend**: paquete nuevo (p. ej. `recommendation`). `POST /api/recommendations/chat` con los
  últimos mensajes (máx. ~10) y el `locale`. El servicio:
  1. Arma un prompt de sistema con el catálogo compacto (id, título, géneros, descripción corta,
     precio, % positivo). Con 60 juegos cabe entero; no hace falta búsqueda vectorial.
  2. Llama a OpenRouter (`https://openrouter.ai/api/v1/chat/completions`, formato OpenAI) con
     `RestClient` y tiempos máximos, como `SteamConfig`.
  3. Pide respuesta JSON `{ "reply": "...", "gameIds": [..] }`, la valida y **descarta los ids que
     no existen** o no están publicados. Devuelve `reply` + los `GameSummaryResponse`.
- **Configuración**: `OPENROUTER_API_KEY` y `OPENROUTER_MODEL` (por defecto un modelo `:free`)
  en `.env`, `.env.example` y `render.yaml`. La clave nunca llega al navegador.
- **Sin clave, sin red o sin cupo** (los modelos gratis tienen límite diario): el endpoint responde
  un error controlado (503 con ProblemDetail) y el chat muestra «El asistente no está disponible
  ahora». La página no se rompe.
- **Abuso**: solo usuarios con sesión; límite por usuario (p. ej. 20 mensajes/hora, en memoria);
  tamaño máximo de cada mensaje. El texto del usuario va como mensaje de usuario, nunca dentro del
  prompt de sistema.
- **Tests**: el cliente de OpenRouter detrás de una interfaz, simulado con Mockito. El CI no llama
  a la red ni gasta cupo.
- **Prueba real**: necesita la clave del usuario en `.env`. Si no está al cerrar el bloque, el bloque
  se cierra con mocks y queda un paso manual en «Pendientes». Ojo: los modelos `:free` suelen exigir
  activar una opción de privacidad en la cuenta de OpenRouter; documentarlo en el README. Lo mismo
  vale para Cloudinary en el bloque G.

### Bloque E: amigos y perfil

- **Modelo**: tabla `friendships` (solicitante, destinatario, estado `PENDING`/`ACCEPTED`, fechas).
  Una sola fila por pareja sin importar la dirección (índice único sobre el par ordenado; puede
  necesitar SQL por vendor). Columna `users.profile_public BOOLEAN NOT NULL DEFAULT FALSE`.
- **Flujo como Steam**: buscar usuario por nombre → enviar solicitud → el otro acepta o rechaza.
  Se puede cancelar una solicitud enviada y borrar un amigo. Si A pide a B y B ya tenía una
  solicitud pendiente a A, se aceptan las dos (decisión de Claude). No se puede pedirse a uno mismo
  ni duplicar. Los usuarios desactivados no salen en la búsqueda.
- **Solo cuentas de jugador** (rol USER sin DEVELOPER ni ADMIN) participan: salen en la búsqueda,
  envían y reciben solicitudes. Los ~45 estudios y el admin quedan fuera, para no inundar la
  búsqueda. Decisión de Claude.
- **Endpoints** (orientativos): `GET /api/users/search?q=`, `GET /api/users/{username}/profile`,
  `GET /api/friends`, `GET /api/friends/requests`, `POST /api/friends/requests`,
  `POST /api/friends/requests/{id}/accept|reject`, `DELETE /api/friends/requests/{id}`,
  `DELETE /api/friends/{username}`. `profilePublic` se cambia con la actualización de perfil existente.
- **Perfil `/users/:username`**:
  - Siempre visible: nombre, avatar, fecha de alta, botón de amistad según el estado.
  - Visible si el perfil es **público** o si el que mira es **amigo** (o es él mismo): biblioteca
    con horas jugadas y lista de amigos (cada amigo enlaza a su perfil).
  - Nunca visible: email, pedidos, carrito, wishlist.
  - La regla de visibilidad se aplica en el backend, no solo en el frontend.
- **Frontend**: página «Amigos» (lista, solicitudes recibidas/enviadas, buscador), página de perfil,
  interruptor «Perfil público» en Ajustes, enlace en la cabecera y en la barra inferior móvil.

### Bloque F: refresh token

- Token de acceso a **15 min** (hoy `JWT_EXPIRATION=PT2H`); refresh de **7 días**.
- Tabla `refresh_tokens` (usuario, hash del token —nunca el token en claro—, caducidad, revocado,
  reemplazado por). **Rotación**: cada uso devuelve un refresh nuevo y revoca el anterior. Si llega
  un refresh ya revocado (posible robo), se revocan todos los del usuario
  (cambio tras la revisión: se revoca la **familia** del token, es decir, los encadenados desde el mismo login,
  que es la práctica recomendada; así un robo en un dispositivo no cierra las sesiones de los demás.
  Revocar todos los del usuario solo ocurre al desactivarlo o cambiarle los roles).
- `POST /api/auth/refresh` y `POST /api/auth/logout` (revoca). Login y registro devuelven también el
  refresh. Desactivar un usuario o cambiarle los roles (admin) revoca sus refresh.
- El filtro JWT sigue sin consultar la BD (stateless, como pide el enunciado).
- **Frontend** (`api/client.ts`): ante un 401, intenta un refresh una sola vez y repite la petición;
  peticiones simultáneas comparten el mismo refresh; si falla, cierra sesión. El refresh se guarda
  en `localStorage` como el token actual (decisión de Claude: una cookie httpOnly sería más segura
  frente a XSS, pero entre dominios Vercel/Render complica CORS y SameSite; se documenta como
  limitación).
- **Honestidad para la defensa**: D16 no desaparece del todo; el retraso máximo baja de 2 h a 15 min
  y el refresh se corta al instante. Actualizar «Limitaciones conocidas».

### Bloque G: portadas en Cloudinary

- Cuenta gratuita de Cloudinary (sin tarjeta), del usuario. Variable `CLOUDINARY_URL`.
- `POST` multipart para subir la portada de un juego, permitido al developer dueño del juego y al
  admin. El backend valida tipo real del archivo (JPEG/PNG/WebP por bytes mágicos, no por extensión)
  y tamaño máx. **5 MB**, sube a Cloudinary (carpeta tipo `arcade/covers`) y guarda la `secure_url`
  (https) en `coverUrl`.
- Pegar una URL sigue funcionando.
- Sin `CLOUDINARY_URL`: el frontend oculta el botón (el backend expone si la subida está activa) y
  la app funciona igual. En tests, Cloudinary simulado.
- Resuelve la limitación D43 para las portadas subidas; actualizar «Limitaciones conocidas».

## 5. Lo que deja al builder

- Nombres exactos de clases, DTOs, endpoints y claves de traducción, siguiendo las convenciones
  actuales (paquetes por feature, DTOs `record`, mappers manuales, ProblemDetail para errores).
- Numeración exacta de migraciones a partir de V8, una por bloque que la necesite.
- Usar el SDK de Cloudinary o su API REST firmada con `RestClient`.
- Diseño visual del chat, la página de amigos y el perfil, dentro del estilo actual de ARCADE.
- Qué agentes del equipo (`equipo-agentes/`) hacen cada parte.
- Elección de los ~37 juegos nuevos y del modelo `:free` de OpenRouter por defecto.

## 6. Decisiones de Claude (no confirmadas por el usuario)

- Se reutiliza el `recommended` existente; las estrellas se borran sin convertirlas.
- Puntuación global con reseñas de todos los idiomas.
- Chatbot solo con sesión, con límite por usuario, sin historial guardado.
- Solicitud cruzada de amistad = aceptación automática.
- Refresh en `localStorage`; acceso de 15 min, refresh de 7 días con rotación.
- Reseñas de demo con textos de un banco propio, no copiadas de Steam.
