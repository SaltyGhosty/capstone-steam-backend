import { Link } from 'react-router';
import type { GameSummary } from '@/api/types';
import { useStore } from '@/context/StoreContext';
import { cardImage, formatMoney, genreLabel } from '@/lib/format';
import Icon from './Icon';

export default function GameCard({ game }: { game: GameSummary }) {
  const { wishlistIds, toggleWishlist, addToCart } = useStore();
  const wished = wishlistIds.has(game.id);

  return (
    <article className="game-card">
      <div className="cover">
        <Link to={`/games/${game.slug}`} aria-label={game.title}>
          {game.coverUrl ? <img src={cardImage(game.coverUrl)} alt="" loading="lazy" /> : <div className="cover-fallback">{game.title}</div>}
        </Link>
        <button onClick={() => toggleWishlist(game.id, game.title)} className={wished ? 'wish active' : 'wish'}
          aria-label={wished ? `Quitar ${game.title} de la wishlist` : `Añadir ${game.title} a la wishlist`}>
          <Icon name="heart" size={18} />
        </button>
        {game.ratingCount > 0 && <div className="rating"><Icon name="star" size={13} /> {game.averageRating.toFixed(1)}</div>}
        {game.discountPercent > 0 && <div className="card-sale">−{game.discountPercent}%</div>}
      </div>
      <div className="game-info">
        <div>
          <h3><Link to={`/games/${game.slug}`}>{game.title}</Link></h3>
          <p>{genreLabel(game.genres)}</p>
        </div>
        <button onClick={() => addToCart(game.id, game.title)} aria-label={`Añadir ${game.title} al carrito`}>
          {game.discountPercent > 0 && <s>{formatMoney(game.price)}</s>}
          {formatMoney(game.finalPrice)}
        </button>
      </div>
    </article>
  );
}
