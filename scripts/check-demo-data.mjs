#!/usr/bin/env node
// Valida scripts/demo-data.json. Sale con código 1 y lista los errores si alguna regla falla.
import { readFile } from 'node:fs/promises';
import { assignReviews, reviewText } from './demo-reviews.mjs';

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

const perGenre = Object.fromEntries(GENRES.map((g) => [g, 0]));
for (const g of games) {
  const id = g.key ?? g.title;
  for (const slug of g.genres ?? []) {
    if (slug in perGenre) perGenre[slug]++;
    else errors.push(`${id}: género inexistente «${slug}»`);
  }
  check(nonEmpty(g.description) && nonEmpty(g.descriptionEn) && nonEmpty(g.descriptionIt), `${id}: faltan descripciones es/en/it`);
  check(studios.has(g.studio), `${id}: el estudio «${g.studio}» no existe`);
  check(Number.isInteger(g.steamAppId) && g.steamAppId > 0, `${id}: steamAppId no válido`);
  check(typeof g.steamPositivePercent === 'number' && g.steamPositivePercent >= 0 && g.steamPositivePercent <= 100, `${id}: steamPositivePercent fuera de 0-100`);
  check(Number.isInteger(g.reviewCount) && g.reviewCount >= 10 && g.reviewCount <= 60, `${id}: reviewCount fuera de 10-60`);
}
for (const [slug, n] of Object.entries(perGenre)) check(n >= 5, `Género ${slug}: ${n} juegos, hacen falta al menos 5`);

// ---- variedad de etiquetas
const rated = games.filter((g) => typeof g.steamPositivePercent === 'number' && typeof g.reviewCount === 'number');
check(rated.filter((g) => g.reviewCount >= 50 && g.steamPositivePercent >= 80).length >= 10, 'Hacen falta al menos 10 juegos con reviewCount >= 50 y % >= 80');
check(rated.some((g) => g.steamPositivePercent >= 40 && g.steamPositivePercent <= 69), 'Falta un juego con % entre 40 y 69 (Variadas)');
check(rated.some((g) => g.steamPositivePercent < 40), 'Falta un juego con % por debajo de 40 (negativas)');

// ---- reseñadores y frases
const rv = data.reviewers;
check(rv && rv.prefix && rv.perLanguage === 20, 'reviewers: faltan prefix o perLanguage = 20');
for (const lang of LANGS) {
  const names = rv?.names?.[lang] ?? [];
  check(names.length === 20 && new Set(names).size === 20 && names.every(nonEmpty), `reviewers.names.${lang}: hacen falta 20 nombres únicos`);
  for (const vote of ['positive', 'negative']) {
    const bank = data.reviewPhrases?.[lang]?.[vote] ?? [];
    check(bank.length >= 15 && new Set(bank).size === bank.length && bank.every(nonEmpty), `reviewPhrases.${lang}.${vote}: hacen falta 15 frases únicas`);
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
const usernames = LANGS.flatMap((lang) => Array.from({ length: 20 }, (_, i) => `${rv?.prefix ?? 'rev'}_${lang}_${String(i + 1).padStart(2, '0')}`));
for (const g of rated) {
  const a = assignReviews(g, usernames);
  check(a.length === g.reviewCount && new Set(a.map((x) => x.username)).size === g.reviewCount, `${g.key}: assignReviews no da ${g.reviewCount} usuarios distintos`);
  check(a.filter((x) => x.recommended).length === Math.round((g.reviewCount * g.steamPositivePercent) / 100), `${g.key}: nº de positivas incorrecto`);
  check(JSON.stringify(a) === JSON.stringify(assignReviews(g, usernames)), `${g.key}: assignReviews no es determinista`);
  if (data.reviewPhrases) {
    const t = reviewText(data.reviewPhrases, g, 'es', true, 'x');
    check(nonEmpty(t) && t === reviewText(data.reviewPhrases, g, 'es', true, 'x'), `${g.key}: reviewText vacío o no determinista`);
  }
}

if (errors.length) {
  console.error(`Datos demo NO válidos (${errors.length} errores):`);
  for (const e of errors) console.error(`  - ${e}`);
  process.exit(1);
}
console.log(`Datos demo OK: ${games.length} juegos, ${data.studios.length} estudios.`);
