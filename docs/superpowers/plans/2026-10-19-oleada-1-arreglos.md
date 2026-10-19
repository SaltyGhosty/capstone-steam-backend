# Oleada 1 — pasada de arreglos tras la revisión final

> **Para agentes:** las reglas de `2026-10-19-oleada-1-comun.md` y `2026-10-19-oleada-2-comun.md` siguen valiendo. Base: `main` en el commit que te da la sesión principal; si tu worktree no lo contiene, `git merge --ff-only main`. **Haz solo tu sección.**

Cada hallazgo de abajo es un arreglo con TDD:

1. escribe el test que lo reproduce;
2. míralo fallar;
3. arregla;
4. míralo pasar.

**Un commit por hallazgo**, con un mensaje corto en español y sin co-author. Al final, `.\mvnw.cmd clean verify` y/o `npm test; npm run build` → verde.

**Informe final:** por cada hallazgo, el commit, el nombre del test y si lo viste fallar antes de arreglar. Añade el nº de tests al final, las desviaciones y lo que no hiciste.

No toques docs, README, handoff, Postman ni la bóveda: los hace la sesión principal. **No hay migraciones en esta pasada**: si un arreglo parece pedir una, usa la alternativa que da cada punto.

---

## B · Reseñas

1. **Contadores descuadrados con un update y un delete simultáneos** (`review/ReviewService.java:96`, `:113-122`; `Review` no tiene `@Version`).
   - **Arreglo:** en `ReviewRepository`, `@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from Review r where r.id = :id") Optional<Review> findByIdForUpdate(Long id)`. Úsalo en `update` y `delete` en vez de la lectura actual, para que el voto que se lee para el delta sea el bloqueado.
   - **Test:** en `ReviewServiceTest` o en uno de integración, `update` y `delete` usan `findByIdForUpdate`. Si puedes, un test sin `@Transactional`, con dos hilos que hacen 👍→👎 y borrar a la vez: al final, `positive_count` = nº real de positivas. Limpia sus filas al acabar.
2. **La lista de reseñas no se refresca al editar** (`frontend/src/pages/GameDetail.tsx:106-109`, `:161`). `onSaved` y `remove` también llaman a `reviews.reload()`.
   - **Test:** en `GameDetail.test.tsx`, editar solo el texto vuelve a pedir `GET /api/games/<slug>/reviews` y se ve el texto nuevo.
3. **Editar cambia el idioma de la reseña al de la interfaz** (`GameDetail.tsx:209`). En el PUT se manda `language: review.language` (el que ya tenía), no `getLang()`. El POST sigue mandando `getLang()`.
   - **Test:** con la interfaz en `it`, editar una reseña `es` hace un PUT con `language: 'es'`.
4. **El % mostrado redondea por encima de los umbrales** (`game/Game.java:135`). `positivePercent` pasa a `floor`, es decir `(int) (100L * positive / total)`.
   - **Test:** en `ReviewScoreTest` o un test de `Game`, 35/44 → 79 y 946/1000 → 94. Ajusta los tests que esperaban redondeo.
5. **Cambiar de idioma no vuelve a la página 0** (`GameDetail.tsx:106-111`). Añadir `useEffect(() => setPage(0), [lang])`.
   - **Test:** en `GameDetail.test.tsx`, ir a la página 2, cambiar el idioma y comprobar que la petición siguiente lleva `page=0`.
6. **Restos de las estrellas:**
   - borrar `.stars` de `frontend/src/app.css` y la regla `.rating svg` de `index.css` si ya no se usa (compruébalo con grep);
   - `dev.rating` pasa a «Reseñas / Reviews / Recensioni».
   - Sin test: un solo commit `Reseñas: restos de las estrellas`.

**No toques `scripts/`:** el arreglo del seed lo hace el bloque C.

## E · Amigos

1. **Las solicitudes pendientes de cuentas que ya no son jugador se ven y se aceptan** (`friend/FriendService.java:88-94` y `:131-135`).
   - En `requests()`, filtra `incoming` y `outgoing` con `isPlayer(other)`.
   - En `accept` (y en `decline`), si el solicitante o quien acepta no es jugador activo → 404 `notFound.friendRequest`.
   - **Test** en `FriendIntegrationTest`: leo pide a ana y el admin desactiva a leo. Entonces `GET /api/friends/requests` de ana tiene `incoming` vacío y `accept` da 404. Lo mismo si leo pasa a ser DEVELOPER.
2. **«Mi perfil» y «Amigos» se enseñan a developer y admin** (`frontend/src/components/Layout.tsx:104`, `:143`, `:144`). Solo se muestran si la cuenta no tiene `ROLE_DEVELOPER` ni `ROLE_ADMIN`.
   - **Test:** con un usuario admin no hay enlace «Amigos» ni «Mi perfil»; con un jugador, sí.
3. **Cancelar puede borrar una amistad ya aceptada** (`FriendService.java:139-147`). Borra con `@Modifying @Query("delete from Friendship f where f.id = :id and f.status = 'PENDING'")` (o el equivalente con el enum como parámetro). Si borra 0 filas → 404 `notFound.friendRequest`.
   - **Test:** aceptar y luego cancelar → 404, y la amistad sigue.
4. **Búsqueda:**
   - Backend: `q` de más de 50 caracteres → 400 (`@Size(max = 50)`, o la comprobación junto a la del mínimo).
   - Frontend (`Friends.tsx:43-53`): descarta la respuesta si el término ya cambió (guarda el término de cada petición o usa `AbortController`), y no rellenes `hits` si el término bajó de 2 caracteres.
   - **Tests:** 51 caracteres → 400. En vitest, dos respuestas desordenadas («le» llega después de «leo») → se ven los resultados de «leo».

## F · Refresh token

1. **Dos refresh simultáneos con el mismo token dejan dos hijos vivos** (`auth/RefreshTokenService.java:76`, `:94`; el comentario de la `:28` es falso).
   - **Arreglo:** revoca con `@Modifying @Query("update RefreshToken t set t.revokedAt = :now where t.id = :id and t.revokedAt is null") int revokeIfActive(Long id, Instant now)`.
     - Si devuelve 0 → reutilización: `revokeFamily` y `UnauthorizedException("auth.refreshInvalid")`.
     - Si devuelve 1 → emite el hijo y rellena `replaced_by_id`.
     - Corrige el comentario.
   - **Test sin `@Transactional`** (`@SpringBootTest`, que limpie sus filas): N hilos (p. ej. 8) rotan el mismo token a la vez. Exactamente uno recibe tokens; el resto, 401. Al final, en la BD, 0 tokens vivos en la familia, porque la reutilización la cierra.
   - El mismo test (o uno hermano) comprueba que una reutilización deja la familia revocada **en la BD**. Hoy eso solo se ha visto a mano, con `noRollbackFor`.
2. **La sesión se borra por fallos que no son de autenticación:**
   - `frontend/src/api/client.ts:121-130`: `doRefresh` solo borra los tokens y llama a `onUnauthorized` si la respuesta es 400 o 401. Con 5xx o 429 lanza `ApiError(res.status)` sin tocar nada.
   - `frontend/src/context/StoreContext.tsx:83`: al arrancar, `clearSession()` solo si `e instanceof ApiError && e.status === 401`.
   - **Tests** en `client.test.ts`: un refresh que da 502 → los tokens siguen guardados y no se llama a `onUnauthorized`. Y si hay test de StoreContext, el arranque con `ApiError(0)` no borra la sesión.
3. **Petición repetida sin token** (`client.ts:169`): `const current = getToken(); const renewed = (current !== null && current !== token) || await refreshSession();`.
   - **Test:** si el refresh ya borró la sesión, una petición cuyo 401 llega tarde no se repite.
4. **`RefreshRequest.refreshToken`** lleva también `@Size(max = 128)`.
   - **Test:** 129 caracteres → 400.
5. **Comentario de aviso** en `rotate` y en `AuthService.refresh`: la revocación solo se guarda porque **todas** las transacciones de la cadena llevan `noRollbackFor = UnauthorizedException.class`. Va junto con el commit del punto 1.

## G · Portadas

1. **Las peticiones de más de 6 MB llegan como error de red, no como 413** (`src/main/resources/application.yml`). Añade `server.tomcat.max-swallow-size: 10MB`.
   - **Test nuevo** `cover/CoverUploadLimitTest`: `@SpringBootTest(webEnvironment = RANDOM_PORT)`, perfil test y `@MockitoBean CoverStorage` con `enabled()` true.
     - Un developer sube 7 MB por HTTP real (`RestClient` o `HttpClient` del JDK) y recibe 413 `application/problem+json`.
     - Lo mismo con 5 MB + 1 byte.
     - Limpia lo que crees.
2. **Un 4xx de Cloudinary sale como 502 «inténtalo más tarde»** (`cover/CloudinaryCoverStorage.java:126-129`). Si es `HttpClientErrorException` con 400, responde 400 `cover.invalidType`. El 502 queda para 5xx, red y timeout.
   - **Test** en `CloudinaryCoverStorageTest` con `MockRestServiceServer`: un 400 da `BadRequestException` con la clave `cover.invalidType`, y un 500 sigue siendo `CoverUploadException`.
3. **Logs útiles:**
   - registra el código de estado y `error.message` del JSON de Cloudinary, que no contiene el secreto (compruébalo en el test: el log no contiene el `api_secret`);
   - añade un `log.warn` cuando la `secure_url` no es https.
   - **Test** con `OutputCaptureExtension`.
4. **Un archivo con `file.type` vacío se rechaza en el cliente** (`frontend/src/components/CoverUpload.tsx:33`). Cámbialo a `if (file.type && !TYPES.includes(file.type))`.
   - **Test:** en `Developer.test.tsx`, un archivo con `type: ''` sí se envía.
