# Bloque B — Reseñas estilo Steam: plan de implementación

> **Para agentes:** lee primero `docs/superpowers/plans/2026-10-19-oleada-1-comun.md` (reglas obligatorias). Pasos con casillas `- [ ]`.

**Objetivo:** cambiar las estrellas por el modelo de Steam: cada reseña es 👍/👎 y guarda su idioma. Cada juego muestra el % positivo y una etiqueta («Muy positivas»…), y la ficha filtra las reseñas por idioma.

**Spec:** `docs/superpowers/specs/2026-10-19-cierre-y-extras-design.md` §4 «Bloque B». **Migración: V8.**

## Decisiones ya tomadas

- Se reutiliza el `recommended` que ya existe. **`rating` desaparece** de la BD, de la API y del frontend, sin conversión.
- El contador sigue siendo un **UPDATE atómico** (`@Modifying @Query`), porque es requisito del enunciado.
- La puntuación global cuenta **todas** las reseñas, de todos los idiomas.
- Un juego con **menos de 10 reseñas** tiene `reviewScore = NONE`. No pasa el filtro `minPositivePercent` y va al final en `top_rated`.
- El idioma de una reseña lo manda el frontend (`language`, el idioma de la interfaz). Si no llega, se usa el `locale` del autor.

## Archivos

- **Crear:**
  - `src/main/resources/db/migration/common/V8__steam_style_reviews.sql`
  - `src/main/java/com/capstone/steam/game/ReviewScore.java`
  - `src/test/java/com/capstone/steam/game/ReviewScoreTest.java`
  - `frontend/src/components/ReviewScoreBadge.tsx`
  - `frontend/src/pages/GameDetail.test.tsx`
- **Modificar:**
  - backend: `review/{Review, ReviewService, ReviewController, ReviewRepository}.java`, `review/dto/{ReviewRequest, ReviewResponse}.java`, `game/{Game, GameRepository}.java`, `game/dto/{GameSummaryResponse, GameDetailResponse}.java`, `game/search/{GameSearchCriteria, GameCriteriaRepository}.java`
  - frontend: `api/{types, endpoints}.ts`, `components/{GameCard, ui}.tsx`, `pages/{GameDetail, Browse, Developer}.tsx`, `i18n/messages.ts`, `app.css`
  - `scripts/seed-demo.mjs` y `scripts/demo-data.json`
  - tests existentes: `ReviewServiceTest`, `StorefrontIntegrationTest`, `GameCriteriaRepositoryTest`, `RepositoryIntegrationTest`, `FlywayMigrationTest`

## Interfaces que fija este plan

- `enum ReviewScore { OVERWHELMINGLY_POSITIVE, VERY_POSITIVE, POSITIVE, MOSTLY_POSITIVE, MIXED, MOSTLY_NEGATIVE, NEGATIVE, VERY_NEGATIVE, OVERWHELMINGLY_NEGATIVE, NONE; static ReviewScore of(int positive, int total) }`
- `Game`:
  - `int getReviewCount()` (columna `review_count`) y `int getPositiveCount()` (columna `positive_count`), ambas `insertable=false, updatable=false`;
  - `int getPositivePercent()`: `Math.round(100.0 * positive / total)`, o 0 si no hay reseñas;
  - `ReviewScore getReviewScore()`.
- `GameRepository`: `int applyReviewDelta(Long gameId, int positiveDelta, int countDelta)`. Sustituye a `applyRatingDelta`, con el mismo `@Modifying(flushAutomatically = true, clearAutomatically = true)`.
- `GameSummaryResponse` y `GameDetailResponse`: cambian `averageRating, ratingCount` por `int reviewCount, int positivePercent, ReviewScore reviewScore`, en esa posición.
- `ReviewRequest(Long gameId, @NotNull Boolean recommended, @Size(max=5000) String body, @Pattern(regexp="^(es|en|it)$", message="{validation.locale}") String language)`
- `ReviewResponse(Long id, Long gameId, boolean recommended, String body, String language, Author author, Instant createdAt, Instant updatedAt)`
- `GET /api/games/{slug}/reviews?language=es|en|it&page&size`: `language` es opcional; sin él se devuelven todos. Un valor fuera de es/en/it da 400 con la clave `review.invalidLanguage`.
- `GET /api/games?minPositivePercent=0..100`: sustituye a `minRating`. Fuera de rango da 400.

---

### Tarea 1: `ReviewScore` con las reglas de Steam

- [ ] **Test** `ReviewScoreTest`, con `ReviewScore.of(positive, total)`:
  - `of(9,9)` → NONE
  - `of(10,10)` → POSITIVE
  - `of(49,49)` → POSITIVE
  - `of(40,50)` → VERY_POSITIVE (80 %)
  - `of(474,500)` → VERY_POSITIVE
  - `of(475,500)` → OVERWHELMINGLY_POSITIVE (95 %)
  - `of(7,10)` → MOSTLY_POSITIVE
  - `of(4,10)` → MIXED
  - `of(2,10)` → MOSTLY_NEGATIVE
  - `of(1,10)` → NEGATIVE
  - `of(5,50)` → VERY_NEGATIVE
  - `of(50,500)` → OVERWHELMINGLY_NEGATIVE
  - `of(0,0)` → NONE
- [ ] Verlo fallar.
- [ ] **Implementar.** Con `p` = porcentaje exacto (double) y `n` = total, en este orden:
  1. `n < 10` → NONE
  2. `p ≥ 95` y `n ≥ 500` → OVERWHELMINGLY_POSITIVE
  3. `p ≥ 80` y `n ≥ 50` → VERY_POSITIVE
  4. `p ≥ 80` → POSITIVE
  5. `p ≥ 70` → MOSTLY_POSITIVE
  6. `p ≥ 40` → MIXED
  7. `p ≥ 20` → MOSTLY_NEGATIVE
  8. `n ≥ 500` → OVERWHELMINGLY_NEGATIVE
  9. `n ≥ 50` → VERY_NEGATIVE
  10. si no → NEGATIVE
- [ ] Verlo pasar. Commit `Reseñas: puntuación con las reglas de Steam`.

### Tarea 2: migración V8 y entidades

- [ ] **V8** (SQL portable, PostgreSQL y H2), en este orden:
  1. `ALTER TABLE reviews ADD COLUMN language VARCHAR(5) DEFAULT 'es' NOT NULL;`
  2. `UPDATE reviews SET language = (SELECT u.locale FROM users u WHERE u.id = reviews.user_id);`
  3. `ALTER TABLE reviews ADD CONSTRAINT ck_reviews_language CHECK (language IN ('es','en','it'));`
  4. `ALTER TABLE reviews DROP CONSTRAINT ck_reviews_rating;` y `ALTER TABLE reviews DROP COLUMN rating;`
  5. `ALTER TABLE games DROP CONSTRAINT ck_games_counters;`, `ALTER TABLE games DROP COLUMN rating_sum;` y `ALTER TABLE games RENAME COLUMN rating_count TO review_count;`
  6. `ALTER TABLE games ADD COLUMN positive_count INTEGER DEFAULT 0 NOT NULL;`
  7. Recalcular desde `reviews`: `UPDATE games SET review_count = (SELECT COUNT(*) FROM reviews r WHERE r.game_id = games.id), positive_count = (SELECT COUNT(*) FROM reviews r WHERE r.game_id = games.id AND r.recommended = TRUE);`
  8. `ALTER TABLE games ADD CONSTRAINT ck_games_counters CHECK (sales_count >= 0 AND review_count >= 0 AND positive_count >= 0 AND positive_count <= review_count);`
  9. `CREATE INDEX idx_reviews_game_language_created ON reviews (game_id, language, created_at DESC);`
- [ ] **Tests** en `FlywayMigrationTest`:
  - versión 8 (renombrar el test a `appliesAllMigrationsUpToV8`);
  - `rejectsInvalidDiscountAndRating` pasa a `rejectsInvalidDiscountAndReviewLanguage`: un INSERT de reseña con `language = 'fr'` falla;
  - `positive_count > review_count` falla.
- [ ] **Entidades:**
  - `Review`: quitar `rating` y añadir `@Column(nullable=false, length=5) String language`. Constructor `Review(User user, Game game, boolean recommended, String body, String language)`.
  - `Game`: quitar `ratingSum` y `getAverageRating`; añadir `reviewCount`, `positiveCount`, `getPositivePercent()` y `getReviewScore()`.
- [ ] `RepositoryIntegrationTest.applyRatingDeltaHandlesCreateEditAndDelete` pasa a `applyReviewDeltaHandlesCreateEditAndDelete`, con `applyReviewDelta`. Comprueba `reviewCount`, `positiveCount` y `positivePercent`.
- [ ] `.\mvnw.cmd -q test "-Dtest=FlywayMigrationTest,RepositoryIntegrationTest,ReviewScoreTest" "-Djacoco.skip=true"` → PASS. Commit `Reseñas: V8 con idioma y contador de positivas`.

### Tarea 3: servicio, API y filtro por idioma

- [ ] **Tests:**
  - `ReviewServiceTest`:
    - create con `recommended=true` llama a `applyReviewDelta(gameId, 1, 1)`, y con `false` a `(gameId, 0, 1)`;
    - update de 👍 a 👎 → `(gameId, -1, 0)`; sin cambio de voto, sin llamada;
    - delete de una 👍 → `(gameId, -1, -1)`;
    - create sin `language` usa el `locale` del autor.
  - `StorefrontIntegrationTest`:
    - `reviewLifecycleKeepsGameCountersInSync`: tras crear 👍 → `reviewCount` 1, `positivePercent` 100, `reviewScore` "NONE"; editar a 👎 → `positivePercent` 0; borrar → `reviewCount` 0.
    - `reviewsCanBeFilteredByLanguage`: tres jugadores con el juego (regalado por el admin con `POST /api/admin/users/{u}/library/{g}`) reseñan en es, en e it. `?language=en` devuelve 1, sin parámetro 3 y `?language=fr` da 400.
    - `invalidReviewReturns400`: sin `recommended` → `$.errors.recommended`.
- [ ] Verlos fallar.
- [ ] **Implementar:**
  - `ReviewService.create`, `update` y `delete` con `applyReviewDelta`. La respuesta se construye antes de llamarlo, por el `clearAutomatically`.
  - `listForGame(String slug, String language, Pageable)`.
  - `ReviewRepository.findByGameIdAndLanguage(...)`, con el mismo `join fetch` y su `countQuery`.
  - `ReviewController`: `@RequestParam(required = false) String language`. La validación la hace el service con `BadRequestException("review.invalidLanguage")`, con la clave en los 3 idiomas.
- [ ] Verlos pasar. Commit `Reseñas: voto 👍/👎 con idioma y filtro por idioma`.

### Tarea 4: catálogo con % positivo

- [ ] **Tests** en `GameCriteriaRepositoryTest`. El seed usa `applyReviewDelta`; datos: juego A con 30 reseñas y 28 👍, juego B con 20 y 16 👍, juego C con 2 y 2 👍.
  - `minPositivePercent=85` → solo A;
  - `minPositivePercent=0` → A y B, **no** C (menos de 10);
  - `TOP_RATED` → A, B, C.
- [ ] **Implementar:**
  - `GameSearchCriteria`: `minRating` pasa a `@Min(0) @Max(100) Integer minPositivePercent`. Ajusta todos los constructores y builders de tests.
  - Predicado: `reviewCount >= 10` y `positiveCount * 100 >= minPositivePercent * reviewCount`.
  - `TOP_RATED`:
    1. `selectCase` 0 si `reviewCount >= 10` y 1 si no, ASC;
    2. `positiveCount * 1.0 / nullif(reviewCount, 0)` DESC, con nulls al final;
    3. `reviewCount` DESC;
    4. `id` ASC (el desempate que ya existe).
- [ ] `GameSummaryResponse` y `GameDetailResponse` con `reviewCount`, `positivePercent` y `reviewScore`; borrar `roundRating`.
- [ ] Ajustar el resto de tests que lean `averageRating` o `ratingCount` (`grep -rn "averageRating\|ratingCount\|minRating\|applyRatingDelta" src`). Al terminar, el grep debe salir vacío.
- [ ] `.\mvnw.cmd clean verify` → BUILD SUCCESS. Commit `Catálogo: filtro y orden por % de reseñas positivas`.

### Tarea 5: frontend

- [ ] **Tipos y llamadas:**
  - `types.ts`: `type ReviewScore = 'OVERWHELMINGLY_POSITIVE' | … | 'NONE'`. `GameSummary` y `GameDetail` llevan `reviewCount`, `positivePercent` y `reviewScore`. `Review` quita `rating` y añade `language: Locale`. `ReviewRequest`: `{ gameId?, recommended: boolean, body?, language: Locale }`. `CatalogQuery`: `minPositivePercent?: number`.
  - `endpoints.ts`: `catalogApi.reviews(slug, p = 0, size = 10, language?: Locale)`.
- [ ] **`ReviewScoreBadge.tsx`**: `({ game, compact }: { game: Pick<GameSummary,'reviewCount'|'positivePercent'|'reviewScore'>; compact?: boolean })`.
  - Pinta la etiqueta `t('reviewScore.' + code)` con clase de color: `score-positive` para las 4 positivas, `score-mixed` para MIXED y `score-negative` para las 4 negativas.
  - Sin `compact`, añade `t('game.positiveOf', { percent, count })`.
  - Con NONE: si hay reseñas, `t('game.reviews', { count })`; si no, `t('game.noReviews')`; en `compact` no pinta nada.
  - Colores en `app.css` desde tokens existentes, en la línea de Steam: azul para positivas, ámbar para variadas y rojo para negativas.
- [ ] **`GameCard`**: sustituye la estrella por `<ReviewScoreBadge game={game} compact />`.
- [ ] **`Developer.tsx` l.114**: `t('dev.positiveSummary', { percent, count })`.
- [ ] **`GameDetail`:**
  - Cabecera con `<ReviewScoreBadge game={game} />`.
  - Lista de reseñas con un control de dos botones: «Tu idioma (Español)», con el nombre sacado de `LOCALES` de `lib/locale.ts`, y «Todos los idiomas». Por defecto, el idioma de la interfaz (`getLang()`).
  - Si tu idioma no tiene reseñas pero el juego sí: `t('review.noneInLanguage')` y un botón a «Todos los idiomas».
  - Cada reseña muestra 👍/👎, con el veredicto de siempre, y la etiqueta del idioma.
  - `ReviewForm`: sin estrellas. Los dos botones Recomendado/No recomendado son obligatorios (`review.pickVerdict` si falta). Envía `language: getLang()`.
  - Borrar `Stars` de `ui.tsx` si ya no se usa.
- [ ] **`Browse`**: los chips de `minRating` pasan a `minPositivePercent`, con 70, 80 y 95 y el texto `t('browse.minPositiveChip', { percent })`, bajo `browse.minPositive`. Param de URL `minPositivePercent`.
- [ ] **i18n:** quitar `common.ofFive`, `review.pickRating`, `review.stars`, `review.score` y `browse.minRating` si ya no se usan. Añadir en es/en/it:
  - `reviewScore.*` (10 claves) con los nombres oficiales de Steam:
    - es: «Extremadamente positivas», «Muy positivas», «Positivas», «Mayormente positivas», «Variadas», «Mayormente negativas», «Negativas», «Muy negativas», «Extremadamente negativas», «Sin puntuación».
    - it: «Estremamente positive», «Molto positive», «Positive», «Perlopiù positive», «Nella media», «Perlopiù negative», «Negative», «Molto negative», «Estremamente negative», «Nessun punteggio».
    - en: «Overwhelmingly Positive», «Very Positive», «Positive», «Mostly Positive», «Mixed», «Mostly Negative», «Negative», «Very Negative», «Overwhelmingly Negative», «No score».
  - `game.positiveOf` (es: «{percent} % de {count} reseñas son positivas»)
  - `review.pickVerdict`, `review.mineLanguage` («Tu idioma ({language})»), `review.allLanguages`, `review.noneInLanguage`
  - `browse.minPositive`, `browse.minPositiveChip` («≥ {percent} %»)
  - `dev.positiveSummary`
- [ ] **Test** `GameDetail.test.tsx`:
  - (a) Un juego con `reviewScore` VERY_POSITIVE, 85 % y 60 reseñas muestra «Muy positivas» y «85 %».
  - (b) La primera petición de reseñas lleva `language=es`; al pulsar «Todos los idiomas», otra sin `language`.
  - (c) El formulario envía `{ recommended: true, language: 'es', … }` sin `rating`.
- [ ] `cd frontend; npm test; npm run build` → verde. Commit `Frontend: reseñas estilo Steam con filtro por idioma`.

### Tarea 6: datos demo

- [ ] `scripts/demo-data.json`: en cada reseña, quitar `rating` y añadir `"language": "it"` (los textos actuales están en italiano). Las mezclas de idiomas llegan en el bloque C.
- [ ] `scripts/seed-demo.mjs` (l.151-170): enviar `{ gameId, recommended, body, language }`, también en el PUT de actualización.
- [ ] Comprobar la sintaxis con `node --check scripts/seed-demo.mjs`. **No ejecutar el seed.**
- [ ] Commit `Datos demo: reseñas con idioma y sin estrellas`.

## Foco de revisión

1. Editar una reseña sin cambiar el voto no debe tocar los contadores; cambiarlo suma o resta 1 a las positivas.
2. Ningún `averageRating`, `minRating` ni `rating` en backend, frontend ni tests: el grep tiene que salir vacío.
3. Con 0 reseñas no hay división entre 0, ni en Java ni en Criteria.
4. Reseña con `language` inválido → 400 con `errors.language`.
5. En GameDetail, las dependencias de `useAsync` (l.104) usaban `averageRating`: pasan a `reviewCount` y `positivePercent`, o la cabecera no se refresca al reseñar.
