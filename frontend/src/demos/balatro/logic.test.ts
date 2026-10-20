import { describe, expect, it } from 'vitest';
import {
  createRound, discardCards, evaluateHand, playCards, scoreHand, type Card, type HandName, type RoundState, type Suit,
} from './logic';

const S: Suit = '♠', H: Suit = '♥', D: Suit = '♦', C: Suit = '♣';
const c = (rank: number, suit: Suit = S): Card => ({ rank: rank as Card['rank'], suit });

/** Ronda con una mano elegida a dedo y un mazo fijo detrás. */
function roundWith(hand: Card[], overrides: Partial<RoundState> = {}): RoundState {
  const base = createRound(1);
  return { ...base, hand, deck: base.deck.slice(0, 20), ...overrides };
}

describe('evaluateHand', () => {
  const cases: [HandName, Card[], number, number][] = [
    ['highCard', [c(2), c(5, H), c(9, D), c(11, C), c(13)], 5, 1],
    ['pair', [c(4), c(4, H), c(9, D), c(11, C), c(13)], 10, 2],
    ['twoPair', [c(4), c(4, H), c(9, D), c(9, C), c(13)], 20, 2],
    ['threeOfAKind', [c(7), c(7, H), c(7, D), c(11, C), c(13)], 30, 3],
    ['straight', [c(5), c(6, H), c(7, D), c(8, C), c(9)], 30, 4],
    ['flush', [c(2, H), c(5, H), c(9, H), c(11, H), c(13, H)], 35, 4],
    ['fullHouse', [c(3), c(3, H), c(3, D), c(12, C), c(12)], 40, 4],
    ['fourOfAKind', [c(10), c(10, H), c(10, D), c(10, C), c(2)], 60, 7],
    ['straightFlush', [c(5, D), c(6, D), c(7, D), c(8, D), c(9, D)], 100, 8],
  ];

  it.each(cases)('reconoce %s', (hand, cards, chips, mult) => {
    expect(evaluateHand(cards)).toMatchObject({ hand, chips, mult });
  });

  it('la escalera con As bajo (A-2-3-4-5) y alto (10-J-Q-K-A) vale', () => {
    expect(evaluateHand([c(14), c(2, H), c(3, D), c(4, C), c(5)]).hand).toBe('straight');
    expect(evaluateHand([c(10), c(11, H), c(12, D), c(13, C), c(14)]).hand).toBe('straight');
    // pero no da la vuelta: Q-K-A-2-3 no es escalera
    expect(evaluateHand([c(12), c(13, H), c(14, D), c(2, C), c(3)]).hand).toBe('highCard');
  });

  it('con menos de 5 cartas no hay escalera ni color, pero sí parejas', () => {
    expect(evaluateHand([c(5, H), c(9, H), c(11, H), c(13, H)]).hand).toBe('highCard');
    expect(evaluateHand([c(5, H), c(5)]).hand).toBe('pair');
    expect(evaluateHand([c(5, H), c(5), c(5, D), c(5, C)]).hand).toBe('fourOfAKind');
  });
});

describe('scoreHand', () => {
  it('una pareja de reyes', () => {
    expect(scoreHand([c(13), c(13, H), c(2, D), c(5, C), c(9)])).toBe((10 + 10 + 10) * 2);
  });

  it('carta alta con un as', () => {
    expect(scoreHand([c(14), c(3, H), c(5, D)])).toBe((5 + 11) * 1);
  });

  it('solo puntúan las cartas que forman la jugada', () => {
    // pareja de 4 más kicker alto: (10 + 4 + 4) × 2, el As no suma
    expect(scoreHand([c(4), c(4, H), c(14, D)])).toBe((10 + 4 + 4) * 2);
  });

  it('el póquer y la escalera de color usan su tabla', () => {
    expect(scoreHand([c(10), c(10, H), c(10, D), c(10, C), c(2)])).toBe((60 + 40) * 7);
    expect(scoreHand([c(5, D), c(6, D), c(7, D), c(8, D), c(9, D)])).toBe((100 + 5 + 6 + 7 + 8 + 9) * 8);
  });
});

describe('ronda', () => {
  it('empieza con 8 cartas, 5 manos, 2 descartes y objetivo 300', () => {
    const r = createRound(42);
    expect(r.hand).toHaveLength(8);
    expect(r.handsLeft).toBe(5);
    expect(r.discardsLeft).toBe(2);
    expect(r.target).toBe(300);
    expect(r.score).toBe(0);
    expect(new Set([...r.hand, ...r.deck].map((x) => `${x.rank}${x.suit}`)).size).toBe(52);
  });

  it('jugar repone la mano hasta 8 y gasta una mano', () => {
    const r = roundWith([c(2), c(3, H), c(4, D), c(6, C), c(8), c(9, H), c(11, D), c(12, C)]);
    const next = playCards(r, [0, 1]);
    expect(next.hand).toHaveLength(8);
    expect(next.handsLeft).toBe(4);
    expect(next.discardsLeft).toBe(2);
    expect(next.score).toBeGreaterThan(0);
    expect(next.lastPlay?.hand).toBe('highCard');
  });

  it('descartar no gasta mano y gasta un descarte', () => {
    const r = roundWith([c(2), c(3, H), c(4, D), c(6, C), c(8), c(9, H), c(11, D), c(12, C)]);
    const next = discardCards(r, [0, 1, 2]);
    expect(next.hand).toHaveLength(8);
    expect(next.handsLeft).toBe(5);
    expect(next.discardsLeft).toBe(1);
    expect(next.score).toBe(0);
  });

  it('no se pueden jugar más de 5 cartas ni ninguna', () => {
    const r = roundWith([c(2), c(3, H), c(4, D), c(6, C), c(8), c(9, H), c(11, D), c(12, C)]);
    expect(playCards(r, [0, 1, 2, 3, 4, 5])).toBe(r);
    expect(playCards(r, [])).toBe(r);
    expect(playCards(r, [0, 0])).toBe(r);
    expect(playCards(r, [9])).toBe(r);
    expect(discardCards(r, [0, 1, 2, 3, 4, 5])).toBe(r);
  });

  it('sin descartes no se puede descartar', () => {
    const r = roundWith([c(2), c(3, H), c(4, D), c(6, C), c(8), c(9, H), c(11, D), c(12, C)], { discardsLeft: 0 });
    expect(discardCards(r, [0])).toBe(r);
  });

  it('superar 300 gana; quedarse sin manos por debajo pierde', () => {
    const strong = roundWith([c(14), c(14, H), c(14, D), c(14, C), c(2), c(3), c(4), c(6, H)], { score: 200 });
    const won = playCards(strong, [0, 1, 2, 3]);
    expect(won.score).toBeGreaterThanOrEqual(300);
    expect(won.finished).toBe(true);
    expect(won.won).toBe(true);

    const weak = roundWith([c(2), c(3, H), c(4, D), c(6, C), c(8), c(9, H), c(11, D), c(12, C)], { handsLeft: 1 });
    const lost = playCards(weak, [0]);
    expect(lost.finished).toBe(true);
    expect(lost.won).toBe(false);
    // acabada la ronda, ya no se juega más
    expect(playCards(lost, [0])).toBe(lost);
  });

  it('con el mismo número de semilla el reparto es el mismo', () => {
    expect(createRound(9).hand).toEqual(createRound(9).hand);
    expect(createRound(9).hand).not.toEqual(createRound(10).hand);
  });
});
