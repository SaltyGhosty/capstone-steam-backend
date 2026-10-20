import { useEffect, useRef, useState } from 'react';
import type { MessageKey } from '@/i18n/messages';
import { useI18n } from '@/i18n';
import type { DemoGameProps } from '../types';
import {
  MAX_PLAY, createRound, discardCards, evaluateHand, playCards, type Card, type HandName,
} from './logic';

const HAND_KEYS: Record<HandName, MessageKey> = {
  highCard: 'demos.balatro.hand.highCard',
  pair: 'demos.balatro.hand.pair',
  twoPair: 'demos.balatro.hand.twoPair',
  threeOfAKind: 'demos.balatro.hand.threeOfAKind',
  straight: 'demos.balatro.hand.straight',
  flush: 'demos.balatro.hand.flush',
  fullHouse: 'demos.balatro.hand.fullHouse',
  fourOfAKind: 'demos.balatro.hand.fourOfAKind',
  straightFlush: 'demos.balatro.hand.straightFlush',
};

const RANK_LABEL: Record<number, string> = { 11: 'J', 12: 'Q', 13: 'K', 14: 'A' };
const rankLabel = (card: Card) => RANK_LABEL[card.rank] ?? String(card.rank);
const isRed = (card: Card) => card.suit === '♥' || card.suit === '♦';

/** Un tiempo para ver la última jugada antes de enseñar la pantalla final */
const FINISH_DELAY_MS = 1100;

/** Se juega con DOM (botones), así vale con ratón, teclado y toque. */
export default function BalatroDemo({ paused, onFinish }: DemoGameProps) {
  const { t } = useI18n();
  const [round, setRound] = useState(() => createRound(Math.floor(Math.random() * 2 ** 31)));
  const [selected, setSelected] = useState<number[]>([]);
  const finishRef = useRef(onFinish);
  finishRef.current = onFinish;

  useEffect(() => {
    if (!round.finished) return;
    const timer = setTimeout(() => {
      finishRef.current(round.won
        ? { won: true, messageKey: 'demos.balatro.won', values: { score: round.score } }
        : { won: false, messageKey: 'demos.balatro.lost', values: { score: round.score, target: round.target } });
    }, FINISH_DELAY_MS);
    return () => clearTimeout(timer);
  }, [round.finished, round.won, round.score, round.target]);

  const toggle = (index: number) => setSelected((prev) => (
    prev.includes(index) ? prev.filter((i) => i !== index) : prev.length < MAX_PLAY ? [...prev, index] : prev
  ));
  const act = (action: typeof playCards) => {
    setRound((r) => action(r, selected));
    setSelected([]);
  };

  const locked = paused || round.finished;
  const preview = selected.length > 0 ? evaluateHand(selected.map((i) => round.hand[i])) : null;
  const progress = Math.min(100, (round.score / round.target) * 100);

  return (
    <div className="balatro">
      <div className="balatro-stats">
        <div><small>{t('demos.balatro.target')}</small><b>{round.target}</b></div>
        <div><small>{t('demos.balatro.score')}</small><b>{round.score}</b></div>
        <div><small>{t('demos.balatro.hands')}</small><b>{round.handsLeft}</b></div>
        <div><small>{t('demos.balatro.discards')}</small><b>{round.discardsLeft}</b></div>
      </div>
      <div className="balatro-bar" role="progressbar" aria-valuemin={0} aria-valuemax={round.target} aria-valuenow={Math.min(round.score, round.target)}>
        <span style={{ width: `${progress}%` }} />
      </div>

      <p className="balatro-info" aria-live="polite">
        {preview
          ? t('demos.balatro.preview', { hand: t(HAND_KEYS[preview.hand]), chips: preview.chips, mult: preview.mult })
          : round.lastPlay
            ? t('demos.balatro.last', { hand: t(HAND_KEYS[round.lastPlay.hand]), points: round.lastPlay.points })
            : t('demos.balatro.pick')}
      </p>

      <div className="balatro-hand">
        {round.hand.map((card, i) => (
          <button
            key={`${card.rank}${card.suit}`}
            type="button"
            className={`balatro-card${selected.includes(i) ? ' selected' : ''}${isRed(card) ? ' red' : ''}`}
            aria-pressed={selected.includes(i)}
            aria-label={`${rankLabel(card)} ${card.suit}`}
            disabled={locked}
            onClick={() => toggle(i)}
          >
            <span>{rankLabel(card)}</span>
            <span aria-hidden="true">{card.suit}</span>
          </button>
        ))}
      </div>

      <div className="balatro-actions">
        <button type="button" className="primary" disabled={locked || selected.length === 0} onClick={() => act(playCards)}>
          {t('demos.balatro.play')}
        </button>
        <button type="button" className="secondary" disabled={locked || selected.length === 0 || round.discardsLeft === 0} onClick={() => act(discardCards)}>
          {t('demos.balatro.discard')}
        </button>
      </div>
    </div>
  );
}
