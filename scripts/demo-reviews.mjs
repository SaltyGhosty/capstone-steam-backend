// Reparto determinista de las reseñas de demo. Todo es puro: mismos datos, mismo resultado.

/** Hash FNV-1a de un texto a entero de 32 bits. */
export function hash(text) {
  let h = 0x811c9dc5;
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i);
    h = Math.imul(h, 0x01000193);
  }
  return h >>> 0;
}

/** PRNG con semilla (mulberry32): devuelve una función que da números en [0, 1). */
export function mulberry32(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/**
 * Elige qué reseñadores reseñan el juego y con qué voto.
 * Los primeros round(count × % / 100) votan positivo y el resto negativo.
 */
export function assignReviews(game, reviewerUsernames) {
  const rand = mulberry32(hash(`assign:${game.key}`));
  const pool = [...reviewerUsernames];
  for (let i = pool.length - 1; i > 0; i--) { // Fisher-Yates
    const j = Math.floor(rand() * (i + 1));
    [pool[i], pool[j]] = [pool[j], pool[i]];
  }
  const positives = Math.round((game.reviewCount * game.steamPositivePercent) / 100);
  return pool.slice(0, game.reviewCount).map((username, i) => ({ username, recommended: i < positives }));
}

/** Una entrada del banco es un texto o { text, aboutPrice: true } si habla de precio o de la compra. */
export const phraseText = (entry) => (typeof entry === 'string' ? entry : entry.text);

/** Frases usables en ese juego: a los gratuitos se les quitan las de precio. */
export function usablePhrases(phrasesByLang, game, lang, recommended) {
  const bank = phrasesByLang[lang]?.[recommended ? 'positive' : 'negative'] ?? [];
  return bank.filter((e) => !(game.price === 0 && typeof e !== 'string' && e.aboutPrice)).map(phraseText);
}

function compose(bank, rand) {
  const first = Math.floor(rand() * bank.length);
  const parts = [bank[first]];
  if (rand() < 0.6 && bank.length > 1) {
    let second = Math.floor(rand() * (bank.length - 1));
    if (second >= first) second++;
    parts.push(bank[second]);
  }
  return parts.join(' ');
}

/**
 * Texto de una reseña: 1-2 frases del banco del idioma y el voto, elegidas con el PRNG.
 * Si se pasa `used` (textos ya emitidos en ese juego), no repite ninguno y añade el nuevo.
 */
export function reviewText(phrasesByLang, game, lang, recommended, seed, used) {
  const bank = usablePhrases(phrasesByLang, game, lang, recommended);
  let text;
  for (let attempt = 0; attempt < 40; attempt++) {
    text = compose(bank, mulberry32(hash(`text:${game.key}:${lang}:${recommended}:${seed}:${attempt}`)));
    if (!used || !used.has(text)) break;
  }
  if (used?.has(text)) { // el PRNG no encontró hueco: se recorre el banco en orden fijo
    outer: for (const a of bank) {
      if (!used.has(a)) { text = a; break; }
      for (const b of bank) if (a !== b && !used.has(`${a} ${b}`)) { text = `${a} ${b}`; break outer; }
    }
  }
  used?.add(text);
  return text;
}

/** Textos de todas las reseñas de un juego, sin repetir ninguno. langOf(username) da el idioma de cada cuenta. */
export function gameReviewTexts(phrasesByLang, game, assigned, langOf) {
  const used = new Set();
  return assigned.map(({ username, recommended }) => ({
    username, recommended, text: reviewText(phrasesByLang, game, langOf(username), recommended, username, used),
  }));
}
