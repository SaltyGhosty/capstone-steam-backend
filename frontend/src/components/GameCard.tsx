import { Link } from 'react-router';
import type { GameSummary } from '@/api/types';
import { useStore } from '@/context/StoreContext';
import { useI18n } from '@/i18n';
import { cardImage, formatMoney, genreLabel } from '@/lib/format';
import Icon from './Icon';
import ReviewScoreBadge from './ReviewScoreBadge';

export default function GameCard({ game }: { game: GameSummary }) {
  const { wishlistIds, toggleWishlist, addToCart } = useStore();
  const { t } = useI18n();
  const wished = wishlistIds.has(game.id);

  return (
    <article className="game-card">
      <div className="cover">
        {game.coverUrl ? <img src={cardImage(game.coverUrl)} alt="" loading="lazy" /> : <div className="cover-fallback">{game.title}</div>}
        <button onClick={(e) => { e.stopPropagation(); toggleWishlist(game.id, game.title); }} className={wished ? 'wish active' : 'wish'}
          aria-label={t(wished ? 'card.removeWish' : 'card.addWish', { title: game.title })}>
          <Icon name="heart" size={18} />
        </button>
        {game.reviewScore !== 'NONE' && <div className="rating"><ReviewScoreBadge game={game} compact /></div>}
        {game.discountPercent > 0 && <div className="card-sale">−{game.discountPercent}%</div>}
      </div>
      <div className="game-info">
        <div>
          {/* Il ::after di questo link copre tutta la card: immagine e titolo portano al dettaglio */}
          <h3><Link className="card-link" to={`/games/${game.slug}`}>{game.title}</Link></h3>
          <p>{genreLabel(game.genres)}</p>
        </div>
        <button onClick={(e) => { e.stopPropagation(); addToCart(game.id, game.title); }} aria-label={t('card.addCart', { title: game.title })}>
          {game.discountPercent > 0 && <s>{formatMoney(game.price)}</s>}
          {formatMoney(game.finalPrice)}
        </button>
      </div>
    </article>
  );
}
