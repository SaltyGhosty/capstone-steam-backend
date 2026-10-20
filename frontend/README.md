# ARCADE: frontend de Capstone Steam

Tienda de videojuegos en React 19 + Vite 8 + Tailwind CSS v4 + React Router 7. Parte del diseño de Figma Make *Video-Game-Website* y consume la API Spring Boot de este mismo repositorio.

## Puesta en marcha

1. Arranca el backend en el puerto 8081. Consulta el [README principal](../README.md).
2. Instala las dependencias y arranca el frontend:

   ```bash
   cd frontend
   npm install
   npm run dev        # http://localhost:5173
   ```

   En desarrollo, las peticiones a `/api` pasan por el proxy de Vite y llegan a `http://localhost:8081`. Para usar otra URL: `API_TARGET=http://localhost:8080 npm run dev`.

3. `npm test` ejecuta los tests (Vitest + Testing Library, sin backend: `fetch` está simulado).
4. `npm run build` comprueba los tipos con `tsc` y genera `dist/`. Si el frontend se sirve desde otro dominio, define `VITE_API_URL` (ver `.env.example`) y añade ese origen a `CORS_ALLOWED_ORIGINS` en el backend.

**Usuarios demo** (tras `node scripts/seed-demo.mjs` en la raíz del repositorio; contraseña `password123`):

| Usuario | Rol | Qué tiene |
|---|---|---|
| `demo_player` | Jugador | Juegos con horas jugadas, wishlist, un juego en el carrito y un pago rechazado |
| `rev_es_01`… `rev_en_01`… `rev_it_01`… | Jugador | 60 reseñadores (20 por idioma) que compraron y reseñaron en su idioma |
| `team_cherry` | Developer | Hollow Knight publicado y *Silksong* como borrador |
| `admin` | Administrador | Contraseña: el `ADMIN_PASSWORD` del `.env` del backend |

**Pago simulado:** una tarjeta que termina en `0000` siempre se rechaza.

Las portadas de la demo son las de Steam: `cardImage()` y `wideImage()` (en `lib/format.ts`) eligen la cápsula apaisada para las tarjetas y la imagen panorámica para el hero. Los juegos con una portada subida a Cloudinary usan esa URL tal cual.

**Funciones opcionales:** el frontend no lleva claves. La subida de portadas (`CoverUpload`) se oculta sola si `GET /api/uploads/covers/status` responde `enabled: false`, y el chat del asistente enseña «El asistente no está disponible ahora» si el backend responde 503 (sin `OPENROUTER_API_KEY`). Ver las variables en el [README principal](../README.md#2-configuración).

## Estructura

```
src/
├── api/          client.ts (fetch + JWT + renovación con refresh token + ProblemDetail), endpoints.ts (una función por endpoint), types.ts (DTOs)
├── context/      StoreContext: sesión, carrito, ids de la wishlist y avisos (toast)
├── components/   Layout (barra lateral + barra superior del diseño), GameCard, ReviewScoreBadge (etiqueta y % de reseñas), AssistantChat (chat del asistente), CoverUpload (subir portada), UserAvatar, Icon, ui (estados, precio, RequireAuth)
├── lib/          format (€, fechas es-ES, tiempo jugado), useAsync
├── test/         setup de Vitest, doble de fetch y render con router + StoreProvider
├── pages/        Discover, Browse, GameDetail, Cart, Orders, Collection (biblioteca y wishlist), Friends, Profile, Auth, Settings, Developer, Admin
├── index.css     CSS original del diseño de Figma Make
└── app.css       estilos de las páginas nuevas, con la misma paleta
```

## Páginas

| Ruta | Acceso | Qué hace |
|---|---|---|
| `/` | Público | Hero con los mejor valorados (por % de reseñas positivas), más vendidos, ofertas y novedades |
| `/browse` | Público | Catálogo con los filtros de la Criteria API (texto, géneros, precio, % mínimo de reseñas positivas, ofertas, orden y paginación); los filtros se guardan en la URL |
| `/games/:slug` | Público | Ficha del juego con los botones según `/library/status`, la etiqueta y el % de reseñas positivas y la lista de reseñas 👍/👎 (crear, editar y borrar), filtrada por el idioma de la interfaz con un control «Todos los idiomas» |
| `/login`, `/register` | Público | Registro como jugador o developer. Crear la cuenta no inicia sesión: lleva a `/check-email`. Un login con el email sin verificar ofrece «Reenviar correo» |
| `/check-email?email=` | Público | «Revisa tu correo»: avisa de que el enlace va de camino, con «Reenviar correo» y «¿Email equivocado?» |
| `/verify-email?token=` | Público | El enlace del correo: verifica el email, inicia la sesión y lleva a `/` (o a `/developer` si es un estudio). Un enlace usado o caducado ofrece pedir otro; la llamada se hace una sola vez aunque React monte el efecto dos veces |
| `/cart` | Usuario | Carrito; «Continuar al pago» crea el pedido PENDING |
| `/orders`, `/orders/:id` | Usuario | Historial y pago (200 → PAID, 402 → FAILED) |
| `/library`, `/wishlist` | Usuario | Colecciones del usuario; «Jugar» simula una sesión de 30 a 90 minutos (`POST /library/{id}/play`) |
| `/friends` | Jugador | Buscador (a partir de 2 caracteres), solicitudes recibidas y enviadas y lista de amigos. Se oculta a developers y admins |
| `/users/:username` | Usuario | Perfil de un jugador: nombre, avatar y botón de amistad según la relación. La biblioteca con horas jugadas y los amigos solo salen si el perfil es público, si sois amigos o si es el tuyo |
| `/settings` | Usuario | Perfil (`PATCH /users/me`), interruptor «Perfil público» e idioma de los correos (es, en, it) |
| `/developer` | DEVELOPER | Sus juegos: publicar, retirar y aplicar un descuento masivo |
| `/developer/games/:id` | DEVELOPER | Formulario del juego (`new` para uno nuevo): crear o editar con `version` para el bloqueo optimista, descripción en es/en/it, importar desde Steam por AppID y subir la portada como archivo (JPEG, PNG o WebP de hasta 5 MB, si el servidor tiene Cloudinary) |
| `/admin` | ADMIN | Moderación de juegos (también puede subir portadas), roles y activación de usuarios, regalar juegos y CRUD de géneros |

**Idioma de la interfaz:** español, inglés o italiano, con las banderas de la cabecera (`LanguageSwitcher`), también sin sesión. Se recuerda en `localStorage` (`arcade.lang`); la primera vez se usa el idioma del navegador, o español si no es uno de los tres. Los textos están en `src/i18n/messages.ts` y la API recibe el mismo idioma en `Accept-Language`.

**Asistente.** Con sesión, un botón flotante abre el chat (`AssistantChat`): texto más hasta 4 tarjetas de juegos que abren su ficha. La conversación vive en memoria y se pierde al recargar; se mandan como mucho los últimos 10 mensajes. Un 503 muestra «El asistente no está disponible ahora» y un 429, el aviso del límite (20 mensajes por hora).

**Sesión.** El token de acceso (15 min) y el refresh token (7 días) se guardan en `localStorage` (`arcade.refresh` es la clave del segundo). Si la API responde 401 con un token guardado, `api/client.ts` pide **un solo** refresh (varias peticiones simultáneas comparten el mismo), guarda los dos tokens nuevos y repite la petición. Si el backend rechaza el refresh (400 o 401), borra los dos tokens, cierra la sesión y abre el login; un fallo temporal (5xx, 429 o red) no la borra. Guardar el refresh en `localStorage` deja una limitación conocida frente a XSS (ver el [README principal](../README.md#limitaciones-conocidas)).

## Tests

`npm test`: 87 tests en 15 archivos.

| Archivo | Qué comprueba |
|---|---|
| `api/client.test.ts` | Token en las cabeceras, query de los filtros, `FormData` sin `Content-Type`, ProblemDetail → `ApiError` con errores por campo, 402 aceptado, 401 → cierre de sesión y la renovación con refresh token (un solo refresh compartido, cierre de sesión si falla con 400 o 401, un 5xx no la borra, un 401 de `/auth/login` no refresca ni se repite una petición sin token) |
| `lib/format.test.ts` | Precios en €, tiempo jugado, portadas apaisadas e iniciales |
| `pages/Auth.test.tsx` | Login correcto (token guardado y redirección), credenciales incorrectas y errores de validación del registro |
| `pages/EmailVerification.test.tsx` | Registro → `/check-email`, reenviar, `/verify-email` (sesión iniciada, estudio a `/developer`, una sola llamada con `StrictMode`, enlace inválido y sin token) |
| `pages/Checkout.test.tsx` | Carrito → pedido → pago aprobado; pago rechazado (402) con su motivo; validación de la tarjeta; carrito sin sesión → login |
| `pages/Settings.test.tsx` | Muestra el idioma de los correos del usuario y lo envía al guardar |
| `pages/GameDetail.test.tsx` | Etiqueta y % de reseñas positivas, filtro por idioma y «Todos los idiomas», voto 👍/👎 con idioma y sin puntuación, la lista se refresca al editar, editar conserva el idioma de la reseña y cambiar de idioma vuelve a la página 0 |
| `pages/Friends.test.tsx` | Buscar con 2 caracteres y enviar la solicitud, aceptar, eliminar un amigo, descartar una respuesta de búsqueda tardía y no rellenar resultados si el término baja de 2 caracteres |
| `pages/Profile.test.tsx` | Perfil privado (aviso, sin biblioteca) y visible (juegos con horas jugadas) |
| `pages/Developer.test.tsx` | Subir portada: `FormData` con el campo `file`, archivo con `type` vacío, más de 5 MB sin llamar al servidor, sin Cloudinary no hay botón y un juego nuevo pide guardarse antes |
| `components/AssistantChat.test.tsx` | Sin sesión no hay botón, envío y respuesta con tarjeta, máximo 10 mensajes previos, 503 y 429, y cerrar con Esc |
| `components/Layout.test.tsx` | Un jugador ve «Amigos» y «Mi perfil»; un developer y un admin no |
| `context/StoreContext.test.tsx` | Un fallo de red al validar la sesión no la borra; un 401 sí |
| `lib/locale.test.ts` | `detectLocale`: dos primeras letras del idioma del navegador; español si no está soportado |
| `i18n/i18n.test.tsx` | Los tres idiomas tienen las mismas claves, variables y plurales, las banderas cambian el idioma sin sesión y lo recuerdan, géneros traducidos y `Accept-Language` en las peticiones |

## Despliegue

`vercel.json` deja listo el despliegue en Vercel (*Root Directory* = `frontend`). Define `VITE_API_URL` con la URL pública del backend y añade la URL de Vercel a `CORS_ALLOWED_ORIGINS` en el backend. Los pasos completos están en el [README principal](../README.md#despliegue).
