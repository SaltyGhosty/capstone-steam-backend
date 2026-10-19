# Bloque G — Portadas en Cloudinary: plan de implementación

> **Para agentes:** lee primero `docs/superpowers/plans/2026-10-19-oleada-1-comun.md` (reglas obligatorias). Pasos con casillas `- [ ]`.

**Objetivo:** el developer dueño de un juego, o el admin, sube la portada como archivo. El backend la valida, la sube a Cloudinary y guarda la `secure_url` (https) en `coverUrl`. Pegar una URL sigue funcionando. Sin `CLOUDINARY_URL`, el botón no aparece y todo funciona igual.

**Spec:** `docs/superpowers/specs/2026-10-19-cierre-y-extras-design.md` §4 «Bloque G». **Sin migración.**

## Decisiones ya tomadas

- **Sin SDK:** API REST firmada de Cloudinary con `RestClient`, en el mismo estilo que `steamstore/SteamConfig` (cliente del JDK con timeouts).
- **`CLOUDINARY_URL`** tiene el formato `cloudinary://<api_key>:<api_secret>@<cloud_name>`. Si está vacía, la subida está desactivada.
- **Validación en el servidor:**
  - tipo real por bytes mágicos (JPEG `FF D8 FF`, PNG `89 50 4E 47 0D 0A 1A 0A`, WebP `RIFF`….`WEBP` en los bytes 8-11);
  - máximo **5 MB**: `spring.servlet.multipart.max-file-size: 5MB` y `max-request-size: 6MB`.
- La portada se sube con `public_id` = `game-<id>` en la carpeta `arcade/covers`, con `overwrite=true`: volver a subirla la reemplaza.
- Subir la portada cambia el juego: hace `saveAndFlush` y sube su `version`. El frontend recarga el formulario con el juego devuelto, igual que tras guardar.

## Archivos

- **Crear**, paquete `src/main/java/com/capstone/steam/cover/`:
  - `CloudinaryProperties.java`, `CoverStorage.java` (interfaz), `CloudinaryCoverStorage.java`, `ImageType.java` (detección por bytes mágicos)
  - `CoverService.java`, `CoverController.java`
  - `CoverUploadException.java` o claves sobre las excepciones existentes
- **Tests:** `cover/ImageTypeTest.java`, `cover/CloudinaryCoverStorageTest.java` (firma y parseo de la URL) y `cover/CoverIntegrationTest.java`.
- **Modificar:**
  - backend: `application.yml`, `common/exception/GlobalExceptionHandler.java` (413), `game/GameManagementService.java` (método para fijar la portada)
  - configuración: `.env.example`, `render.yaml` (`CLOUDINARY_URL`, `sync: false`)
  - frontend: `api/{client, endpoints, types}.ts`, `pages/Developer.tsx`, `pages/Admin.tsx`, `i18n/messages.ts`, y un test nuevo `pages/Developer.test.tsx`

## Interfaces que fija este plan

- **`@ConfigurationProperties("app.cloudinary") record CloudinaryProperties(String url, String folder, Duration timeout)`**, con valores por defecto en yml:
  - `url: ${CLOUDINARY_URL:}`
  - `folder: arcade/covers`
  - `timeout: 20s`
- **`interface CoverStorage`:**
  - `boolean enabled()`
  - `String upload(long gameId, byte[] bytes, ImageType type)`: devuelve la `secure_url` (https) o lanza `CoverUploadException` (502, clave `cover.uploadFailed`).
- **`CloudinaryCoverStorage`:**
  - hace `POST https://api.cloudinary.com/v1_1/{cloud}/image/upload` multipart con `file`, `api_key`, `timestamp`, `public_id`, `folder`, `overwrite=true` y `signature`;
  - **firma** = SHA-1 en hex de `"folder=<f>&overwrite=true&public_id=<id>&timestamp=<t>" + api_secret` (parámetros en orden alfabético; sin `file`, `api_key` ni `resource_type`);
  - lee `secure_url` del JSON y comprueba que empieza por `https://`.
- **`enum ImageType { JPEG, PNG, WEBP; static Optional<ImageType> detect(byte[] head) }`**
- **Endpoints**, con la parte multipart llamada `file`:
  - `POST /api/developer/games/{id}/cover` → 200 `GameDetailResponse`. La propiedad se comprueba como en `GameManagementService.findOwned`: 404 si no existe, 403 `game.notOwner` si es de otro.
  - `POST /api/admin/games/{id}/cover` → 200 `GameDetailResponse` (sin comprobar propiedad).
  - `GET /api/uploads/covers/status` → `{ "enabled": boolean }`, solo con sesión.
- **Errores:**
  - archivo vacío o de tipo no admitido → 400 `cover.invalidType`;
  - más de 5 MB → 413 `cover.tooLarge` (handler nuevo para `MaxUploadSizeExceededException`);
  - subida desactivada → 503 `cover.disabled`;
  - fallo de Cloudinary → 502 `cover.uploadFailed`.

  Todas las claves en es/en/it.

---

### Tarea 1: detección de tipo y firma

- [ ] **Tests:**
  - `ImageTypeTest`: cabeceras JPEG, PNG y WebP reconocidas; GIF (`47 49 46 38`), un texto y un array vacío → `Optional.empty()`; un `RIFF` sin `WEBP` → vacío.
  - `CloudinaryCoverStorageTest`:
    - parseo de `cloudinary://key:secret@demo` → cloud `demo`, key `key`, secret `secret`;
    - una URL vacía → `enabled()` false;
    - la firma con `timestamp=1700000000`, `public_id=game-7`, `folder=arcade/covers` y secret `abc` es igual a `sha1Hex("folder=arcade/covers&overwrite=true&public_id=game-7&timestamp=1700000000abc")`, calculado en el test con `MessageDigest`.
- [ ] **Implementar** `ImageType`, `CloudinaryProperties` y `CloudinaryCoverStorage`. La firma va en un método package-private `static String signature(Map<String,String> params, String secret)`, y el reloj se inyecta para el timestamp. Un `@Bean RestClient cloudinaryRestClient(...)` en una `@Configuration` del paquete `cover`.
- [ ] Verlos pasar. Commit `Portadas: firma de Cloudinary y detección de imagen`.

### Tarea 2: endpoints

- [ ] **Tests** `CoverIntegrationTest`, con `@MockitoBean CoverStorage storage`: `enabled()` devuelve true y `upload` devuelve `"https://res.cloudinary.com/demo/image/upload/arcade/covers/game-1.png"`.
  1. El developer dueño sube un PNG mínimo (los 8 bytes mágicos + relleno) → 200, `$.coverUrl` es la URL del mock y `$.version` sube.
  2. Otro developer → 403. Un jugador → 403.
  3. Un `.txt` → 400 `$.status` 400.
  4. 5 MB + 1 byte → 413.
  5. Con `enabled()` en false → 503. `GET /api/uploads/covers/status` → `{"enabled":false}`.
  6. El admin sube a un juego ajeno → 200.
  7. `upload` lanza `CoverUploadException` → 502.
- [ ] **Implementar** `CoverService` (lee los primeros bytes, detecta el tipo, sube y llama a `GameManagementService.setCover(...)`) y `CoverController`.
  - `GameManagementService` añade `GameDetailResponse setCover(Long developerId, Long gameId, String url)`, que usa `findOwned`, y `GameDetailResponse setCoverAsAdmin(Long gameId, String url)`. Los dos hacen `saveAndFlush`.
  - Configuración de multipart en `application.yml` y el handler 413 en `GlobalExceptionHandler`.
- [ ] `.env.example`: `CLOUDINARY_URL=` con el comentario `# cloudinary://<api_key>:<api_secret>@<cloud_name> (vacío = subida desactivada)`. `render.yaml`: `CLOUDINARY_URL` con `sync: false`.
- [ ] `.\mvnw.cmd clean verify` → verde. Commit `Portadas: subida a Cloudinary para developer y admin`.

### Tarea 3: frontend

- [ ] **`client.ts`:** si `body instanceof FormData`, no pone `Content-Type` y no hace `JSON.stringify`. Añade un test en `client.test.ts`.
- [ ] **`endpoints.ts`:** `developerApi.uploadCover(id, file: File)`, `adminApi.uploadCover(id, file)` y `uploadsApi.coverStatus()`.
- [ ] **`Developer.tsx` (`GameForm`):** si `coverStatus().enabled` y el juego ya existe, junto al campo URL va el botón «Subir portada» (`<input type="file" accept="image/jpeg,image/png,image/webp">`).
  - En el cliente, antes de subir: menos de 5 MB y tipo admitido, o se muestra el error.
  - Durante la subida, «Subiendo…».
  - Si va bien, se recarga el formulario con el juego devuelto, por el mismo camino que `onSaved`.
  - Si el juego es nuevo, el texto `t('cover.saveFirst')`.
- [ ] **`Admin.tsx`:** en cada fila de juego, «Subir portada» con el mismo selector, solo si está habilitado. Si va bien, se actualiza la portada de esa fila.
- [ ] **i18n** en es/en/it: `cover.upload`, `cover.uploading`, `cover.uploaded`, `cover.saveFirst`, `cover.tooLarge`, `cover.invalidType`.
- [ ] **Test** `Developer.test.tsx`: con `GET /api/uploads/covers/status` → `{enabled:true}`, elegir un archivo PNG hace `POST /api/developer/games/1/cover` con un `FormData` que tiene `file`. Un archivo de 6 MB muestra el error sin llamar al servidor.
- [ ] `npm test; npm run build` → verde. Commit `Frontend: subir portada de un juego`.

## Foco de revisión

1. El `api_secret` no sale nunca en logs, respuestas ni errores: el error de Cloudinary se registra sin la URL de configuración.
2. No te fíes de la extensión ni del `Content-Type` del cliente: solo de los bytes.
3. La `secure_url` tiene que ser https (lo exige `safeImageUrl` de los correos).
4. Un 413 tiene que llegar como ProblemDetail, no como página de error de Tomcat.
5. Sin `CLOUDINARY_URL`, el arranque y todos los tests siguen verdes, y el botón no aparece.
