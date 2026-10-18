#!/usr/bin/env node
// Carga el catálogo demo (juegos reales de Steam) a través de la API, como lo haría un usuario:
// estudios que publican sus juegos, jugadores que compran, juegan, reseñan y guardan en la wishlist.
//
//   node scripts/seed-demo.mjs                              # contra http://localhost:8081
//   node scripts/seed-demo.mjs --api https://mi-api.onrender.com
//
// Se puede repetir: lo que ya existe (usuarios, juegos, compras, reseñas…) se salta.
// Los datos están en scripts/demo-data.json. Las portadas se enlazan desde el CDN de Steam y cada juego
// queda enlazado por su AppID: el backend sincroniza después precio y descuento con la tienda de Steam.
import { readFile } from 'node:fs/promises';

const apiArg = process.argv.indexOf('--api');
const API = (apiArg > 0 ? process.argv[apiArg + 1] : 'http://localhost:8081').replace(/\/$/, '');
const data = JSON.parse(await readFile(new URL('./demo-data.json', import.meta.url), 'utf8'));
const VALID_CARD = '4242424242424242';
const DECLINED_CARD = '4242424242420000';

const coverUrl = (appId) => `https://cdn.cloudflare.steamstatic.com/steam/apps/${appId}/library_600x900_2x.jpg`;

class HttpError extends Error {
  constructor(status, detail) { super(`${status} ${detail}`); this.status = status; }
}

async function call(method, path, { token, body, accept = [] } = {}) {
  const res = await fetch(API + path, {
    method,
    headers: {
      ...(body !== undefined && { 'Content-Type': 'application/json' }),
      ...(token && { Authorization: `Bearer ${token}` }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  const json = text ? JSON.parse(text) : null;
  if (!res.ok && !accept.includes(res.status)) throw new HttpError(res.status, json?.detail ?? json?.title ?? text);
  return json;
}

/** Registra la cuenta o, si ya existe, inicia sesión. Devuelve el token. */
async function account(username, displayName, accountType) {
  try {
    const res = await call('POST', '/api/auth/register', { body: {
      username, displayName, accountType, password: data.password, email: `${username}@demo.capstone-steam.local`,
    } });
    return { token: res.accessToken, created: true };
  } catch (e) {
    if (e.status !== 409) throw e;
    const res = await call('POST', '/api/auth/login', { body: { login: username, password: data.password } });
    return { token: res.accessToken, created: false };
  }
}

const count = { studios: 0, games: 0, orders: 0, reviews: 0 };
const log = (msg) => console.log(`  ${msg}`);

// ---------------------------------------------------------------- estudios y juegos
console.log(`Sembrando ${API}`);
const genres = Object.fromEntries((await call('GET', '/api/genres')).map((g) => [g.slug, g.id]));
const studioTokens = {};
for (const s of data.studios) {
  const { token, created } = await account(s.username, s.displayName, 'DEVELOPER');
  studioTokens[s.username] = token;
  if (created) count.studios++;
}

const gameIds = {};
for (const g of data.games) {
  const token = studioTokens[g.studio];
  const own = (await call('GET', '/api/developer/games?size=100', { token })).content;
  let game = own.find((x) => x.title === g.title);
  if (!game) {
    const missing = g.genres.filter((slug) => !genres[slug]);
    if (missing.length) throw new Error(`Géneros inexistentes en ${g.title}: ${missing.join(', ')}`);
    game = await call('POST', '/api/developer/games', { token, body: {
      title: g.title, description: g.description, descriptionEn: g.descriptionEn, descriptionIt: g.descriptionIt,
      price: g.price, discountPercent: g.discountPercent ?? 0,
      releaseDate: g.releaseDate, coverUrl: coverUrl(g.steamAppId), steamAppId: g.steamAppId,
      genreIds: g.genres.map((slug) => genres[slug]),
    } });
    count.games++;
    log(`+ ${g.title}${g.draft ? ' (borrador)' : ''}`);
  }
  else if (!game.steamAppId || (!game.descriptions?.en && g.descriptionEn)) {
    // juego cargado antes de las traducciones o del enlace con Steam: se completan sin tocar el resto
    game = await call('PUT', `/api/developer/games/${game.id}`, { token, body: {
      title: game.title, description: game.descriptions?.es ?? g.description,
      descriptionEn: game.descriptions?.en ?? g.descriptionEn, descriptionIt: game.descriptions?.it ?? g.descriptionIt, price: game.price, discountPercent: game.discountPercent,
      releaseDate: game.releaseDate, coverUrl: game.coverUrl, steamAppId: g.steamAppId,
      genreIds: game.genres.map((x) => x.id), version: game.version,
    } });
    log(`~ ${g.title}: traducciones y AppID de Steam`);
  }
  if (!g.draft && game.status === 'DRAFT') await call('POST', `/api/developer/games/${game.id}/publish`, { token });
  gameIds[g.key] = game.id;
}

// ---------------------------------------------------------------- jugadores
const playerTokens = {};
for (const p of data.players) playerTokens[p.username] = (await account(p.username, p.displayName, 'PLAYER')).token;

async function owned(token, key) {
  return (await call('GET', `/api/library/status/${gameIds[key]}`, { token })).owned;
}

/** Carrito → pedido → pago. Devuelve el estado final del pedido. */
async function buy(token, keys, card) {
  await call('DELETE', '/api/cart', { token });
  for (const key of keys) await call('PUT', `/api/cart/items/${gameIds[key]}`, { token });
  const order = await call('POST', '/api/orders', { token });
  const paid = await call('POST', `/api/orders/${order.id}/pay`, { token, body: { method: 'CREDIT_CARD', cardNumber: card }, accept: [402] });
  count.orders++;
  return paid.status;
}

for (const [player, keys] of Object.entries(data.purchases)) {
  const token = playerTokens[player];
  const pending = [];
  for (const key of keys) if (!(await owned(token, key))) pending.push(key);
  if (pending.length) {
    // de dos en dos, para que cada jugador tenga un historial con varios pedidos
    for (let i = 0; i < pending.length; i += 2) await buy(token, pending.slice(i, i + 2), VALID_CARD);
    log(`${player} compra ${pending.length} juegos`);
  }
}

for (const [player, keys] of Object.entries(data.failedPayments)) {
  const token = playerTokens[player];
  const orders = (await call('GET', '/api/orders?size=100', { token })).content;
  if (orders.some((o) => o.status === 'FAILED')) continue;
  const status = await buy(token, keys, DECLINED_CARD);
  await call('DELETE', '/api/cart', { token });
  log(`${player}: pago rechazado (${status})`);
}

for (const [player, minutesByGame] of Object.entries(data.playtime)) {
  const token = playerTokens[player];
  const library = (await call('GET', '/api/library?size=100', { token })).content;
  for (const [key, minutes] of Object.entries(minutesByGame)) {
    const entry = library.find((e) => e.gameId === gameIds[key]);
    if (!entry || entry.playtimeMinutes > 0) continue;
    // el endpoint admite como mucho 1440 min por sesión
    for (let left = minutes; left > 0; left -= 1440) {
      await call('POST', `/api/library/${gameIds[key]}/play`, { token, body: { minutes: Math.min(left, 1440) } });
    }
  }
}

for (const r of data.reviews) {
  try {
    await call('POST', '/api/reviews', { token: playerTokens[r.player], body: {
      gameId: gameIds[r.game], rating: r.rating, recommended: r.recommended, body: r.body,
    } });
    count.reviews++;
  } catch (e) {
    if (e.status !== 409) throw e; // ya la había escrito
  }
}

for (const [player, keys] of Object.entries(data.wishlists)) {
  for (const key of keys) await call('PUT', `/api/wishlist/${gameIds[key]}`, { token: playerTokens[player] });
}
for (const [player, keys] of Object.entries(data.carts)) {
  for (const key of keys) await call('PUT', `/api/cart/items/${gameIds[key]}`, { token: playerTokens[player] });
}

console.log(`Listo: ${count.studios} estudios, ${count.games} juegos, ${count.orders} pedidos y ${count.reviews} reseñas nuevos.`);
console.log(`Cuentas demo (contraseña "${data.password}"): demo_player (jugador), team_cherry (estudio con un borrador).`);
