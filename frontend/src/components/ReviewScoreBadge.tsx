import type { GameSummary, ReviewScore } from '@/api/types';
import { useI18n } from '@/i18n';

type ScoreGame = Pick<GameSummary, 'reviewCount' | 'positivePercent' | 'reviewScore'>;

const POSITIVE: ReviewScore[] = ['OVERWHELMINGLY_POSITIVE', 'VERY_POSITIVE', 'POSITIVE', 'MOSTLY_POSITIVE'];

/** Color de la etiqueta, como en Steam: azul las positivas, ámbar las variadas y rojo las negativas. */
const scoreClass = (score: ReviewScore) =>
  POSITIVE.includes(score) ? 'score-positive' : score === 'MIXED' ? 'score-mixed' : 'score-negative';

/** Etiqueta de valoración del juego («Muy positivas») con el porcentaje de reseñas positivas. */
export default function ReviewScoreBadge({ game, compact }: { game: ScoreGame; compact?: boolean }) {
  const { t } = useI18n();

  // Con menos de 10 reseñas no hay puntuación: solo el número de reseñas (y nada en la tarjeta)
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
