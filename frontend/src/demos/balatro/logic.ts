// Lógica pura del minijuego de cartas (sin DOM): jugadas de póquer, puntuación y rondas.

export type Suit = '♠' | '♥' | '♦' | '♣';
export type Rank = 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14;
export interface Card { rank: Rank; suit: Suit }

export type HandName =
  | 'highCard' | 'pair' | 'twoPair' | 'threeOfAKind' | 'straight' | 'flush' | 'fullHouse' | 'fourOfAKind' | 'straightFlush';

export const SUITS: Suit[] = ['♠', '♥', '♦', '♣'];
export const HAND_SIZE = 8;
export const MAX_PLAY = 5;
export const START_HANDS = 5;
export const START_DISCARDS = 2;
export const TARGET_SCORE = 300;

/** Fichas y multiplicador de cada jugada (los valores de Balatro) */
export const HAND_TABLE: Record<HandName, { chips: number; mult: number }> = {
  highCard: { chips: 5, mult: 1 },
  pair: { chips: 10, mult: 2 },
  twoPair: { chips: 20, mult: 2 },
  threeOfAKind: { chips: 30, mult: 3 },
  straight: { chips: 30, mult: 4 },
  flush: { chips: 35, mult: 4 },
  fullHouse: { chips: 40, mult: 4 },
  fourOfAKind: { chips: 60, mult: 7 },
  straightFlush: { chips: 100, mult: 8 },
};

export interface Evaluation { hand: HandName; chips: number; mult: number; scoring: Card[] }

/** 2-10 su valor, J/Q/K 10, As 11 */
export const cardChips = (card: Card) => (card.rank === 14 ? 11 : Math.min(card.rank, 10));

function isStraight(cards: Card[]): boolean {
  if (cards.length !== 5) return false;
  const ranks = [...new Set(cards.map((c) => c.rank))].sort((a, b) => a - b);
  if (ranks.length !== 5) return false;
  if (ranks[4] - ranks[0] === 4) return true;
  // A-2-3-4-5: el As también vale 1
  return ranks.join() === '2,3,4,5,14';
}

export function evaluateHand(cards: Card[]): Evaluation {
  const groups = new Map<number, Card[]>();
  for (const card of cards) groups.set(card.rank, [...(groups.get(card.rank) ?? []), card]);
  const sorted = [...groups.values()].sort((a, b) => b.length - a.length || b[0].rank - a[0].rank);
  const counts = sorted.map((g) => g.length);
  const flush = cards.length === 5 && cards.every((c) => c.suit === cards[0].suit);
  const straight = isStraight(cards);

  let hand: HandName;
  let scoring: Card[];
  if (straight && flush) { hand = 'straightFlush'; scoring = cards; }
  else if (counts[0] === 4) { hand = 'fourOfAKind'; scoring = sorted[0]; }
  else if (counts[0] === 3 && counts[1] === 2) { hand = 'fullHouse'; scoring = cards; }
  else if (flush) { hand = 'flush'; scoring = cards; }
  else if (straight) { hand = 'straight'; scoring = cards; }
  else if (counts[0] === 3) { hand = 'threeOfAKind'; scoring = sorted[0]; }
  else if (counts[0] === 2 && counts[1] === 2) { hand = 'twoPair'; scoring = [...sorted[0], ...sorted[1]]; }
  else if (counts[0] === 2) { hand = 'pair'; scoring = sorted[0]; }
  else {
    hand = 'highCard';
    scoring = cards.length ? [cards.reduce((best, c) => (c.rank > best.rank ? c : best))] : [];
  }
  return { hand, ...HAND_TABLE[hand], scoring };
}

/** (fichas de la jugada + fichas de las cartas que puntúan) × multiplicador */
export function scoreHand(cards: Card[]): number {
  if (cards.length === 0) return 0;
  const { chips, mult, scoring } = evaluateHand(cards);
  return (chips + scoring.reduce((sum, c) => sum + cardChips(c), 0)) * mult;
}

export interface RoundState {
  deck: Card[];
  hand: Card[];
  handsLeft: number;
  discardsLeft: number;
  target: number;
  score: number;
  /** Última mano jugada, para enseñar qué ha pasado */
  lastPlay: { hand: HandName; points: number } | null;
  finished: boolean;
  won: boolean;
}

/** Generador con semilla (mulberry32) */
function seededRandom(seed: number): () => number {
  let a = seed | 0;
  return () => {
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), a | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const byRank = (a: Card, b: Card) => b.rank - a.rank || SUITS.indexOf(a.suit) - SUITS.indexOf(b.suit);

export function createRound(seed: number): RoundState {
  const rand = seededRandom(seed);
  const deck: Card[] = SUITS.flatMap((suit) => Array.from({ length: 13 }, (_, i) => ({ rank: (i + 2) as Rank, suit })));
  for (let i = deck.length - 1; i > 0; i--) {
    const j = Math.floor(rand() * (i + 1));
    [deck[i], deck[j]] = [deck[j], deck[i]];
  }
  return {
    deck: deck.slice(HAND_SIZE),
    hand: deck.slice(0, HAND_SIZE).sort(byRank),
    handsLeft: START_HANDS, discardsLeft: START_DISCARDS, target: TARGET_SCORE, score: 0,
    lastPlay: null, finished: false, won: false,
  };
}

/** Índices válidos: de 1 a 5, sin repetir y dentro de la mano */
function validSelection(s: RoundState, indexes: number[]): boolean {
  return indexes.length >= 1 && indexes.length <= MAX_PLAY
    && new Set(indexes).size === indexes.length
    && indexes.every((i) => Number.isInteger(i) && i >= 0 && i < s.hand.length);
}

/** Quita las cartas elegidas y roba hasta volver a 8 (si queda mazo) */
function refill(s: RoundState, indexes: number[]): Pick<RoundState, 'hand' | 'deck'> {
  const kept = s.hand.filter((_, i) => !indexes.includes(i));
  const need = Math.min(HAND_SIZE - kept.length, s.deck.length);
  return { hand: [...kept, ...s.deck.slice(0, need)].sort(byRank), deck: s.deck.slice(need) };
}

export function playCards(s: RoundState, indexes: number[]): RoundState {
  if (s.finished || s.handsLeft <= 0 || !validSelection(s, indexes)) return s;
  const played = indexes.map((i) => s.hand[i]);
  const points = scoreHand(played);
  const score = s.score + points;
  const handsLeft = s.handsLeft - 1;
  const won = score >= s.target;
  return {
    ...s, ...refill(s, indexes), score, handsLeft,
    lastPlay: { hand: evaluateHand(played).hand, points },
    finished: won || handsLeft === 0, won,
  };
}

export function discardCards(s: RoundState, indexes: number[]): RoundState {
  if (s.finished || s.discardsLeft <= 0 || !validSelection(s, indexes)) return s;
  return { ...s, ...refill(s, indexes), discardsLeft: s.discardsLeft - 1 };
}
