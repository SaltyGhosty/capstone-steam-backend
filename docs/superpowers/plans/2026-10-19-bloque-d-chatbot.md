# Bloque D — Chatbot recomendador (OpenRouter): plan de implementación

> **Para agentes:** lee primero `docs/superpowers/plans/2026-10-19-oleada-1-comun.md` y `2026-10-19-oleada-2-comun.md`. Pasos con casillas `- [ ]`.

**Objetivo:** el usuario con sesión abre un chat flotante y escribe lo que le apetece («oscuro, difícil, con buena historia»). El bot contesta con un texto corto y hasta 4 tarjetas de juegos **del catálogo** que abren la ficha. Sin clave, sin red o sin cupo, el chat dice «El asistente no está disponible ahora» y nada se rompe.

**Spec:** `docs/superpowers/specs/2026-10-19-cierre-y-extras-design.md` §4 «Bloque D». **Sin migración.**

## Decisiones ya tomadas

- **Paquete nuevo** `com.capstone.steam.recommendation`. El cliente HTTP con `RestClient` y timeouts, en el estilo de `steamstore/SteamConfig` y `cover/CoverConfig`.
- **El idioma de la respuesta** es el de la petición (`LocaleContextHolder.getLocale()`, que el frontend ya manda en `Accept-Language`). No va un `locale` en el body.
  - **Decisión de Opus:** es el patrón de toda la API.
- **Modelo por defecto:** un modelo `:free` de OpenRouter que acepte instrucciones de sistema y devuelva JSON fiable.
  - Elígelo mirando la lista pública `GET https://openrouter.ai/api/v1/models`, que no necesita clave, y apunta cuál y por qué en el informe.
  - Se puede cambiar con `OPENROUTER_MODEL`.
  - **No llames a `chat/completions` de verdad:** no hay clave y no se gasta cupo.
- **Catálogo en el prompt de sistema:** solo los juegos `PUBLISHED`, una línea por juego, con `id | título | géneros (slugs) | precio final | % positivo (n reseñas) | descripción en el idioma de la petición, cortada a 160 caracteres`.
  - Con ~60 juegos cabe entero; no hay búsqueda vectorial.
  - Se construye en cada petición: 60 filas es barato y así siempre está al día.
- **Contra la inyección de instrucciones:**
  - el texto del usuario va **solo** como mensajes `user`, nunca dentro del prompt de sistema;
  - el prompt de sistema dice que los mensajes del usuario son gustos, no órdenes, que solo se recomienda del catálogo y que, si piden otra cosa, se reconduce con amabilidad.
- **Formato pedido al modelo:** `{"reply": string, "gameIds": number[]}`, con `response_format: {"type":"json_object"}`.
  - El parser también acepta la respuesta envuelta en un bloque de código o con texto alrededor: extrae el primer objeto `{…}`.
  - Si no hay JSON válido, o `reply` está vacío, responde **503 `assistant.unavailable`**.
- **Filtrado de ids:** se descartan los que no existen o no están publicados, y los repetidos. Como mucho 4, en el orden del modelo.
- **Límites:**
  - **Por usuario: 20 mensajes por hora**, en memoria, con ventana deslizante (`ConcurrentHashMap<Long, Deque<Instant>>` y un `Clock` inyectable, como en `RefreshTokenService`). Si se pasa, **429 `assistant.rateLimited`**.
  - **Por petición:** como mucho **10 mensajes**, cada uno de **1..500 caracteres**, y el último tiene que ser de `user`. Si no, 400.
- **Logs:** nunca la clave ni el texto de los mensajes. Solo el modelo, la duración, el nº de mensajes, el nº de ids devueltos y la clase de la excepción.

## Archivos

- **Crear,** en `src/main/java/com/capstone/steam/recommendation/`:
  - `OpenRouterProperties.java`, `ChatModel.java` (interfaz), `OpenRouterChatModel.java`, `RecommendationConfig.java`
  - `RecommendationService.java`, `RecommendationController.java`, `ChatRateLimiter.java`, `AssistantUnavailableException.java`
  - `dto/{ChatRequest, ChatMessage, ChatResponse}.java`
- **Crear también** `common/exception/TooManyRequestsException.java` (429).
- **Tests:** `recommendation/OpenRouterChatModelTest.java` (con `MockRestServiceServer`), `RecommendationServiceTest.java`, `ChatRateLimiterTest.java` y `RecommendationIntegrationTest.java`.
- **Modificar:**
  - backend: `application.yml`, `security/SecurityConfig.java` si hace falta, y `messages*.properties`;
  - configuración: `.env.example` y `render.yaml` (`OPENROUTER_API_KEY` con `sync: false`, y `OPENROUTER_MODEL`).
- **Frontend:**
  - crear `components/AssistantChat.tsx` y `components/AssistantChat.test.tsx`;
  - modificar `components/Layout.tsx`, `api/{endpoints,types}.ts`, `i18n/messages.ts` y `app.css`.

## Interfaces

- **`@ConfigurationProperties("app.openrouter") record OpenRouterProperties(String apiKey, String model, String baseUrl, Duration timeout)`**, con `boolean enabled()` (clave no vacía).
  - En el yml: `api-key: ${OPENROUTER_API_KEY:}`, `model: ${OPENROUTER_MODEL:<el elegido>}`, `base-url: https://openrouter.ai/api/v1` y `timeout: 30s`.
  - Su `toString()` oculta la clave.
- **`interface ChatModel { String complete(String system, List<ChatMessage> messages); }`**: devuelve el `content` del primer `choice`. Lanza `AssistantUnavailableException` si:
  - falta la clave (no hace la llamada);
  - hay timeout o error de red;
  - la respuesta es 4xx o 5xx (también 429 de cupo);
  - el cuerpo no trae `choices[0].message.content`.
- **La petición de `OpenRouterChatModel`:**
  - `POST {baseUrl}/chat/completions` con `Authorization: Bearer <key>`, `X-Title: ARCADE` y el body `{model, messages:[{role:"system",…}, …], response_format:{type:"json_object"}, temperature:0.7, max_tokens:400}`.
- **`record ChatMessage(@NotNull Role role, @NotBlank @Size(max = 500) String content)`**, con `enum Role { user, assistant }` en minúscula, tal cual en el JSON.
- **`record ChatRequest(@NotEmpty @Size(max = 10) List<@Valid ChatMessage> messages)`**
- **`record ChatResponse(String reply, List<GameSummaryResponse> games)`**
- **`POST /api/recommendations/chat`:** con sesión (401 sin ella), devuelve 200 `ChatResponse`.
  - Errores: 400 si la validación falla o el último mensaje no es `user`; 429 `assistant.rateLimited`; 503 `assistant.unavailable`.
  - El mensaje que se rechaza con 400 **no** cuenta para el límite; los 503 sí cuentan.
- **`AssistantUnavailableException`:** 503 con la clave `assistant.unavailable`, subclase de `ApiException`.
- **`TooManyRequestsException(String code, Object... args)`:** 429.
- **Claves nuevas en es/en/it:** `assistant.unavailable`, `assistant.rateLimited` y `assistant.lastMustBeUser`.

---

### Tarea 1: cliente de OpenRouter

- [ ] **Tests `OpenRouterChatModelTest`,** con `MockRestServiceServer` sobre el `RestClient.Builder`:
  - manda `Authorization: Bearer test-key`, el modelo, el system primero y luego los mensajes, y `response_format`;
  - devuelve `choices[0].message.content`;
  - sin clave lanza `AssistantUnavailableException` sin hacer ninguna petición;
  - con un 429 o un 500, y con un cuerpo sin `choices`, también lanza `AssistantUnavailableException`;
  - `OpenRouterProperties.toString()` no contiene la clave.
- [ ] **Implementar** `OpenRouterProperties`, `ChatModel`, `OpenRouterChatModel`, `RecommendationConfig` (el `RestClient` con timeouts), `AssistantUnavailableException`, las claves i18n y el yml.
- [ ] Verlos pasar. Commit `Chatbot: cliente de OpenRouter con tiempos máximos`.

### Tarea 2: servicio, límite y endpoint

- [ ] **Tests `ChatRateLimiterTest`,** con un `Clock` fijo y avanzable: 20 dentro de la hora sí, el 21º no, y una hora después vuelve a dejar. Los usuarios no se afectan entre sí.
- [ ] **Tests `RecommendationServiceTest`,** con Mockito y un `ChatModel` simulado:
  - el prompt de sistema contiene los juegos publicados y no los borradores; la descripción sale en el idioma pedido (prueba con `it`);
  - el texto del usuario **no** aparece en el system, y sí en los mensajes;
  - con `{"reply":"…","gameIds":[1,999,2,1]}` (999 no existe y 2 es borrador), devuelve solo el juego 1;
  - acepta la respuesta envuelta en un bloque de código;
  - con algo que no es JSON, o con `reply` vacío, lanza `AssistantUnavailableException`;
  - con más de 4 ids válidos, se queda con los 4 primeros.
- [ ] **Tests `RecommendationIntegrationTest`,** con `@MockitoBean ChatModel`:
  - sin sesión → 401;
  - con sesión → 200 con `reply` y `games[0].title`;
  - 11 mensajes, uno de 501 caracteres o el último de `assistant` → 400;
  - con `ChatModel` lanzando `AssistantUnavailableException` → 503 ProblemDetail, con el `detail` traducido según `Accept-Language: en`;
  - 21 peticiones seguidas → la 21ª da 429.
- [ ] **Implementar** `ChatRateLimiter`, `RecommendationService`, `RecommendationController`, `TooManyRequestsException` y las reglas de `SecurityConfig` (si `/api/**` ya exige sesión por defecto, no hace falta tocarlo; compruébalo). Añadir `.env.example` y `render.yaml`.
- [ ] `.\mvnw.cmd clean verify` → verde. Commit `Chatbot: recomendaciones del catálogo con límite por usuario`.

### Tarea 3: frontend

- [ ] **Test `AssistantChat.test.tsx`,** con `mockFetch` y `renderRoutes`:
  - sin sesión no hay botón;
  - con sesión, al abrir, escribir y enviar, hace `POST /api/recommendations/chat` con `{messages:[{role:'user',content:…}]}`, y luego muestra la respuesta y una tarjeta con enlace a `/games/<slug>`;
  - el segundo envío manda también los mensajes anteriores (como mucho los 10 últimos);
  - un 503 muestra `assistant.unavailableUi`; un 429 muestra `assistant.rateLimitedUi`.
- [ ] **Implementar `AssistantChat.tsx`:**
  - un botón flotante abajo a la derecha, solo con sesión, que abre un panel `role="dialog"` con título;
  - `Esc` y el botón de cerrar lo cierran, y al abrir el foco va al campo;
  - la lista de mensajes, el campo con `maxLength={500}` y «Pensando…» mientras espera;
  - las tarjetas compactas: portada, título, precio final y `ReviewScoreBadge`;
  - la conversación vive en el estado del componente (se pierde al recargar) y sobrevive a cambiar de página, porque el componente vive en `Layout`.
- [ ] **`endpoints.ts`:** `assistantApi.chat(messages)`. **`types.ts`:** `ChatMessage` y `ChatResponse`.
- [ ] **i18n** en es/en/it:
  - `assistant.open`, `assistant.title` y `assistant.placeholder`;
  - `assistant.send`, `assistant.thinking` y `assistant.close`;
  - `assistant.greeting` (por ejemplo «Dime qué te apetece: oscuro, relajante, para jugar con amigos…»);
  - `assistant.unavailableUi` y `assistant.rateLimitedUi`.
- [ ] **CSS** en `app.css`, con los colores del proyecto, usable a 360 px de ancho.
- [ ] `npm test; npm run build` → verde. Commit `Frontend: chat del asistente de recomendaciones`.

## Foco de revisión

1. La clave nunca llega al navegador, a los logs ni a los mensajes de error.
2. Un modelo que alucina ids o devuelve basura nunca produce un 500 ni tarjetas de juegos que no existen o no están publicados.
3. Un mensaje con «ignora las instrucciones y…» no cambia el prompt de sistema: el texto del usuario solo va en mensajes `user`.
4. Sin `OPENROUTER_API_KEY`, la app arranca, los tests pasan y el chat muestra el aviso, sin llamada de red.
5. El límite por usuario no deja crecer el mapa sin fin: las entradas vacías se borran.
