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
| `demo_player` | Jugador | 3 juegos con horas jugadas, 4 en la wishlist, Celeste en el carrito y un pago rechazado |
| `team_cherry` | Developer | Hollow Knight publicado y *Silksong* como borrador |
| `admin` | Administrador | Contraseña: el `ADMIN_PASSWORD` del `.env` del backend |

**Pago simulado:** una tarjeta que termina en `0000` siempre se rechaza.

Las portadas son las de Steam: `cardImage()` y `wideImage()` (en `lib/format.ts`) eligen la cápsula apaisada para las tarjetas y la imagen panorámica para el hero.

## Estructura

```
src/
├── api/          client.ts (fetch + JWT + ProblemDetail), endpoints.ts (una función por endpoint), types.ts (DTOs)
├── context/      StoreContext: sesión, carrito, ids de la wishlist y avisos (toast)
├── components/   Layout (barra lateral + barra superior del diseño), GameCard, Icon, ui (estados, precio, estrellas, RequireAuth)
├── lib/          format (€, fechas es-ES, tiempo jugado), useAsync
├── test/         setup de Vitest, doble de fetch y render con router + StoreProvider
├── pages/        Discover, Browse, GameDetail, Cart, Orders, Collection (biblioteca y wishlist), Auth, Settings, Developer, Admin
├── index.css     CSS original del diseño de Figma Make
└── app.css       estilos de las páginas nuevas, con la misma paleta
```

## Páginas

| Ruta | Acceso | Qué hace |
|---|---|---|
| `/` | Público | Hero con los mejor valorados, más vendidos, ofertas y novedades |
| `/browse` | Público | Catálogo con los filtros de la Criteria API (texto, géneros, precio, valoración, ofertas, orden y paginación); los filtros se guardan en la URL |
| `/games/:slug` | Público | Ficha del juego con los botones según `/library/status` y las reseñas (crear, editar y borrar) |
| `/login`, `/register` | Público | Registro como jugador o developer |
| `/cart` | Usuario | Carrito; «Continuar al pago» crea el pedido PENDING |
| `/orders`, `/orders/:id` | Usuario | Historial y pago (200 → PAID, 402 → FAILED) |
| `/library`, `/wishlist` | Usuario | Colecciones del usuario; «Jugar» simula una sesión de 30 a 90 minutos (`POST /library/{id}/play`) |
| `/settings` | Usuario | Perfil (`PATCH /users/me`) e idioma de los correos (es, en, it) |
| `/developer` | DEVELOPER | Sus juegos: publicar, retirar y aplicar un descuento masivo |
| `/developer/games/:id` | DEVELOPER | Formulario del juego (`new` para uno nuevo): crear o editar con `version` para el bloqueo optimista, descripción en es/en/it e importar desde Steam por AppID |
| `/admin` | ADMIN | Moderación de juegos, roles y activación de usuarios, regalar juegos y CRUD de géneros |

**Idioma de la interfaz:** español, inglés o italiano, con las banderas de la cabecera (`LanguageSwitcher`), también sin sesión. Se recuerda en `localStorage` (`arcade.lang`); la primera vez se usa el idioma del navegador, o español si no es uno de los tres. Los textos están en `src/i18n/messages.ts` y la API recibe el mismo idioma en `Accept-Language`.

El token se guarda en `localStorage`. Si la API responde 401 con un token guardado (caduca a las 2 h), se cierra la sesión y se abre el login.

## Tests

| Archivo | Qué comprueba |
|---|---|
| `api/client.test.ts` | Token en las cabeceras, query de los filtros, ProblemDetail → `ApiError` con errores por campo, 402 aceptado y 401 → cierre de sesión |
| `lib/format.test.ts` | Precios en €, tiempo jugado, portadas apaisadas e iniciales |
| `pages/Auth.test.tsx` | Login correcto (token guardado y redirección), credenciales incorrectas y errores de validación del registro |
| `pages/Checkout.test.tsx` | Carrito → pedido → pago aprobado; pago rechazado (402) con su motivo; validación de la tarjeta; carrito sin sesión → login |
| `pages/Settings.test.tsx` | Muestra el idioma de los correos del usuario y lo envía al guardar |
| `lib/locale.test.ts` | `detectLocale`: dos primeras letras del idioma del navegador; español si no está soportado |
| `i18n/i18n.test.tsx` | Los tres idiomas tienen las mismas claves, variables y plurales, las banderas cambian el idioma sin sesión y lo recuerdan, géneros traducidos y `Accept-Language` en las peticiones |

## Despliegue

`vercel.json` deja listo el despliegue en Vercel (*Root Directory* = `frontend`). Define `VITE_API_URL` con la URL pública del backend y añade la URL de Vercel a `CORS_ALLOWED_ORIGINS` en el backend. Los pasos completos están en el [README principal](../README.md#despliegue).
