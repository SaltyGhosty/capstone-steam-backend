import type { GameSummary, ReviewScore } from '@/api/types';
import { useI18n } from '@/i18n';

type ScoreGame = Pick<GameSummary, 'reviewCount' | 'positivePercent' | 'reviewScore'>;

const POSITIVE: ReviewScore[] = ['OVERWHELMINGLY_POSITIVE', 'VERY_POSITIVE', 'POSITIVE', 'MOSTLY_POSITIVE'];

/** Colore dell'etichetta, come su Steam: blu le positive, ambra le miste e rosso le negative. */
const scoreClass = (score: ReviewScore) =>
  POSITIVE.includes(score) ? 'score-positive' : score === 'MIXED' ? 'score-mixed' : 'score-negative';

/** Etichetta di valutazione del gioco («Molto positive») con la percentuale di recensioni positive. */
export default function ReviewScoreBadge({ game, compact }: { game: ScoreGame; compact?: boolean }) {
  const { t } = useI18n();

  // Con meno di 10 recensioni non c'è punteggio: solo il numero di recensioni (e niente sulla card)
  if (game.reviewScore === 'NONE') {
    if (compact) return null;
    return (
      <span className="review-score">
        {game.reviewCount > 0 ? t('game.reviews', { count: game.reviewCount }) : t('game.noReviews')}
      </span>
    );
  }

  return (
    <span className="review-score">
      <b className={scoreClass(game.reviewScore)}>{t(`reviewScore.${game.reviewScore}`)}</b>
      {!compact && <span>{t('game.positiveOf', { percent: game.positivePercent, count: game.reviewCount })}</span>}
    </span>
  );
}
