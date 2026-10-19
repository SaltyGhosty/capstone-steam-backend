#!/usr/bin/env node
// Valida scripts/demo-data.json. Sale con código 1 y lista los errores si alguna regla falla.
import { readFile } from 'node:fs/promises';
import { assignReviews, gameReviewTexts, phraseText, reviewText, usablePhrases } from './demo-reviews.mjs';

const data = JSON.parse(await readFile(new URL('./demo-data.json', import.meta.url), 'utf8'));
const GENRES = ['action', 'adventure', 'horror', 'indie', 'platformer', 'puzzle', 'racing', 'rpg', 'shooter', 'simulation', 'sports', 'strategy'];
const LANGS = ['es', 'en', 'it'];
const errors = [];
const check = (ok, msg) => { if (!ok) errors.push(msg); };
const nonEmpty = (s) => typeof s === 'string' && s.trim().length > 0;

// ---- juegos
const games = data.games ?? [];
check(games.length === 60, `Debe haber 60 juegos y hay ${games.length}`);
const keys = new Set(games.map((g) => g.key));
const appIds = new Set(games.map((g) => g.steamAppId));
check(keys.size === games.length, 'Hay keys de juego repetidas');
check(appIds.size === games.length, 'Hay steamAppId repetidos');
const studios = new Set((data.studios ?? []).map((s) => s.username));
check(studios.size === (data.studios ?? []).length, 'Hay estudios repetidos');

const drafts = games.filter((g) => g.draft);
const published = games.filter((g) => !g.draft);
const perGenre = Object.fromEntries(GENRES.map((g) => [g, 0]));
for (const g of games) {
  const id = g.key ?? g.title;
  for (const slug of g.genres ?? []) {
    if (slug in perGenre) { if (!g.draft) perGenre[slug]++; } // los borradores no cuentan
    else errors.push(`${id}: género inexistente «${slug}»`);
  }
  check(nonEmpty(g.description) && nonEmpty(g.descriptionEn) && nonEmpty(g.descriptionIt), `${id}: faltan descripciones es/en/it`);
  check(studios.has(g.studio), `${id}: el estudio «${g.studio}» no existe`);
  check(Number.isInteger(g.steamAppId) && g.steamAppId > 0, `${id}: steamAppId no válido`);
  check(typeof g.steamPositivePercent === 'number' && g.steamPositivePercent >= 0 && g.steamPositivePercent <= 100, `${id}: steamPositivePercent fuera de 0-100`);
  check(Number.isInteger(g.reviewCount) && g.reviewCount >= 10 && g.reviewCount <= 60, `${id}: reviewCount fuera de 10-60`);
}
for (const [slug, n] of Object.entries(perGenre)) check(n >= 5, `Género ${slug}: ${n} juegos publicados, hacen falta al menos 5`);

// ---- variedad de etiquetas
const rated = games.filter((g) => typeof g.steamPositivePercent === 'number' && typeof g.reviewCount === 'number');
check(rated.filter((g) => g.reviewCount >= 50 && g.steamPositivePercent >= 80).length >= 10, 'Hacen falta al menos 10 juegos con reviewCount >= 50 y % >= 80');
check(rated.some((g) => g.steamPositivePercent >= 40 && g.steamPositivePercent <= 69), 'Falta un juego con % entre 40 y 69 (Variadas)');
check(rated.some((g) => g.steamPositivePercent < 40), 'Falta un juego con % por debajo de 40 (negativas)');

// ---- reseñadores y frases
const rv = data.reviewers;
const perLanguage = rv?.perLanguage;
check(rv && rv.prefix && Number.isInteger(perLanguage) && perLanguage > 0, 'reviewers: faltan prefix o perLanguage');
for (const lang of LANGS) {
  const names = rv?.names?.[lang] ?? [];
  check(names.length === perLanguage && new Set(names).size === perLanguage && names.every(nonEmpty), `reviewers.names.${lang}: hacen falta ${perLanguage} nombres únicos`);
  for (const vote of ['positive', 'negative']) {
    const bank = (data.reviewPhrases?.[lang]?.[vote] ?? []).map(phraseText);
    check(bank.length >= 15 && new Set(bank).size === bank.length && bank.every(nonEmpty), `reviewPhrases.${lang}.${vote}: hacen falta 15 frases únicas`);
    const generic = (data.reviewPhrases?.[lang]?.[vote] ?? []).filter((e) => typeof e === 'string');
    check(generic.length >= 15, `reviewPhrases.${lang}.${vote}: hacen falta 15 frases sin precio (las gratuitas no usan las de precio)`);
  }
}

// ---- integridad de referencias
const players = new Set((data.players ?? []).map((p) => p.username));
const refs = (section, byPlayer) => {
  for (const [player, list] of Object.entries(byPlayer ?? {})) {
    check(players.has(player), `${section}: el jugador «${player}» no existe`);
    for (const key of Array.isArray(list) ? list : Object.keys(list)) check(keys.has(key), `${section}: el juego «${key}» no existe`);
  }
};
for (const s of ['purchases', 'wishlists', 'carts', 'playtime', 'failedPayments']) refs(s, data[s]);
for (const r of data.reviews ?? []) {
  check(players.has(r.player), `reviews: el jugador «${r.player}» no existe`);
  check(keys.has(r.game), `reviews: el juego «${r.game}» no existe`);
}

// ---- assignReviews
const usernames = LANGS.flatMap((lang) => Array.from({ length: perLanguage ?? 0 }, (_, i) => `${rv?.prefix ?? 'rev'}_${lang}_${String(i + 1).padStart(2, '0')}`));
for (const g of rated) {
  check(g.reviewCount <= usernames.length, `${g.key}: reviewCount ${g.reviewCount} mayor que los ${usernames.length} reseñadores`);
  const a = assignReviews(g, usernames);
  check(a.length === g.reviewCount && new Set(a.map((x) => x.username)).size === g.reviewCount, `${g.key}: assignReviews no da ${g.reviewCount} usuarios distintos`);
  check(a.filter((x) => x.recommended).length === Math.round((g.reviewCount * g.steamPositivePercent) / 100), `${g.key}: nº de positivas incorrecto`);
  check(JSON.stringify(a) === JSON.stringify(assignReviews(g, usernames)), `${g.key}: assignReviews no es determinista`);
  if (data.reviewPhrases) {
    for (const lang of LANGS) {
      for (const recommended of [true, false]) {
        const t = reviewText(data.reviewPhrases, g, lang, recommended, 'x');
        check(nonEmpty(t) && t === reviewText(data.reviewPhrases, g, lang, recommended, 'x'), `${g.key}: reviewText ${lang}/${recommended ? 'positivo' : 'negativo'} vacío o no determinista`);
      }
    }
  }
}

// ---- textos: sin repetir en un mismo juego y sin frases de precio en los gratuitos
const PRICE_WORDS = /euro|€|precio|prezzo|price|cuesta|cost|compr|bought|buying|acquist|centesim|cent/i;
const langOf = (username) => username.split('_')[1];
for (const g of rated.filter((x) => !x.draft)) {
  const texts = gameReviewTexts(data.reviewPhrases, g, assignReviews(g, usernames), langOf);
  const all = texts.map((t) => t.text);
  check(all.every(nonEmpty), `${g.key}: hay reseñas sin texto`);
  check(new Set(all).size === all.length, `${g.key}: ${all.length - new Set(all).size} textos repetidos en el mismo juego`);
  if (g.price === 0) {
    const priced = all.filter((t) => PRICE_WORDS.test(t));
    check(priced.length === 0, `${g.key}: es gratuito y ${priced.length} reseñas hablan de precio o compra (p. ej. «${priced[0]}»)`);
    for (const lang of LANGS) for (const vote of [true, false]) {
      check(usablePhrases(data.reviewPhrases, g, lang, vote).every((t) => !PRICE_WORDS.test(t)), `${g.key}: las frases usables (${lang}) incluyen precio`);
    }
  }
}

if (errors.length) {
  console.error(`Datos demo NO válidos (${errors.length} errores):`);
  for (const e of errors) console.error(`  - ${e}`);
  process.exit(1);
}
console.log(`Datos demo OK: ${games.length} juegos (${published.length} publicados + ${drafts.length} borrador${drafts.length === 1 ? '' : 'es'}), ${data.studios.length} estudios.`);
