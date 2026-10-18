import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { catalogApi } from '@/api/endpoints';
import type { GameDetail, GameSummary } from '@/api/types';
import GameCard from '@/components/GameCard';
import Icon from '@/components/Icon';
import { ErrorState, Loading } from '@/components/ui';
import { useStore } from '@/context/StoreContext';
import { useI18n } from '@/i18n';
import { formatMoney, genreName, wideImage } from '@/lib/format';
import { useAsync } from '@/lib/useAsync';

interface HomeData {
  featured: GameDetail[];
  bestSelling: GameSummary[];
  newest: GameSummary[];
  onSale: GameSummary[];
  onSaleTotal: number;
}

async function loadHome(): Promise<HomeData> {
  const [topRated, bestSelling, newest, onSale] = await Promise.all([
    catalogApi.search({ sort: 'top_rated', size: 3 }),
    catalogApi.search({ sort: 'best_selling', size: 8 }),
    catalogApi.search({ sort: 'newest', size: 4 }),
    catalogApi.search({ onSale: true, sort: 'price_asc', size: 20 }),
  ]);
  // El hero necesita la descripción, que solo trae la ficha completa
  const featured = await Promise.all(topRated.content.map((g) => catalogApi.get(g.slug)));
  return {
    featured,
    bestSelling: bestSelling.content,
    newest: newest.content,
    onSale: onSale.content,
    onSaleTotal: onSale.totalElements,
  };
}

function Hero({ games }: { games: GameDetail[] }) {
  const { addToCart, toggleWishlist, wishlistIds } = useStore();
  const { t } = useI18n();
  const navigate = useNavigate();
  const [slide, setSlide] = useState(0);
  const active = games[slide];

  // Avance automático cada 8 s; se reinicia al cambiar de slide a mano
  useEffect(() => {
    if (games.length < 2) return;
    const id = window.setTimeout(() => setSlide((s) => (s + 1) % games.length), 8000);
    return () => window.clearTimeout(id);
  }, [slide, games.length]);

  if (!active) return null;
  const wished = wishlistIds.has(active.id);
  const buyNow = async () => {
    if (await addToCart(active.id, active.title)) navigate('/cart');
  };

  return (
    <section id="featured" className="hero" style={{ backgroundImage: `linear-gradient(90deg, rgba(8,12,18,.98) 0%, rgba(8,12,18,.74) 45%, rgba(8,12,18,.1) 100%), url("${wideImage(active.coverUrl)}")` }}>
      <div className="hero-copy">
        <p className="eyebrow">{slide === 0 ? t('home.topRated') : t('home.featured')} · {active.developer.displayName.toUpperCase()}</p>
        <h1><Link to={`/games/${active.slug}`}>{active.title.toUpperCase()}</Link></h1>
        <p className="hero-description">{active.description || t('home.noDescription')}</p>
        <div className="hero-tags">{active.genres.map((g) => <span key={g.id}>{genreName(g)}</span>)}</div>
        <div className="hero-actions">
          <button className="primary" onClick={buyNow}>
            <Icon name="cart" size={18} /> {t('home.buy', { price: formatMoney(active.finalPrice) })}
          </button>
          <button className="secondary" onClick={() => toggleWishlist(active.id, active.title)}>
            <Icon name="heart" size={18} /> {wished ? t('common.inWishlist') : t('common.addToWishlist')}
          </button>
        </div>
      </div>
      {games.length > 1 && (
        <div className="slide-controls">
          <button onClick={() => setSlide((slide + games.length - 1) % games.length)} aria-label={t('home.prevFeatured')}>‹</button>
          <span>{String(slide + 1).padStart(2, '0')} <i /> {String(games.length).padStart(2, '0')}</span>
          <button onClick={() => setSlide((slide + 1) % games.length)} aria-label={t('home.nextFeatured')}>›</button>
        </div>
      )}
    </section>
  );
}

export default function Discover() {
  const { data, error, loading, reload } = useAsync(loadHome, []);
  const { t } = useI18n();

  if (loading && !data) return <Loading />;
  if (error || !data) return <ErrorState error={error} onRetry={reload} />;

  const maxDiscount = Math.max(0, ...data.onSale.map((g) => g.discountPercent));
  const saleImage = data.onSale[0]?.coverUrl ?? data.bestSelling[1]?.coverUrl;
  // Distinta de la del hero (featured[0]) y de la tarjeta de ofertas
  const topImage = [...data.featured.slice(1), ...data.newest].map((g) => g.coverUrl).find((url) => url && url !== saleImage);

  return (
    <>
      <Hero games={data.featured} />

      <section id="browse" className="section">
        <div className="section-heading">
          <div><p className="eyebrow">{t('home.communityEyebrow')}</p><h2>{t('home.bestSelling')}</h2></div>
          <Link className="view-all" to="/browse?sort=best_selling">{t('home.seeCatalog')} <Icon name="chevron" size={16} /></Link>
        </div>
        <div className="game-grid">
          {data.bestSelling.map((game) => <GameCard key={game.id} game={game} />)}
        </div>
      </section>

      <section className="section offers">
        <div className="section-heading">
          <div><p className="eyebrow">{t('home.limited')}</p><h2>{t('home.specialOffers')}</h2></div>
        </div>
        <div className="offer-grid">
          <article className="offer-card purple">
            <div>
              <span className="sale">{maxDiscount > 0 ? t('home.upTo', { pct: maxDiscount }) : t('home.soon')}</span>
              <p>{data.onSaleTotal > 0 ? t('home.discounted', { count: data.onSaleTotal }) : t('home.noOffers')}</p>
              <h3>{t('home.offerLine1')}<br />{t('home.offerLine2')}</h3>
              <Link to="/browse?onSale=true">{t('home.seeOffers')} <Icon name="chevron" size={16} /></Link>
            </div>
            {saleImage && <img src={wideImage(saleImage, 900, 500)} alt="" />}
          </article>
          <article className="offer-card blue">
            <div>
              <span className="sale">TOP</span>
              <p>{t('home.bestRated')}</p>
              <h3>{t('home.topLine1')}<br />{t('home.topLine2')}</h3>
              <Link to="/browse?sort=top_rated">{t('home.seeFavorites')} <Icon name="chevron" size={16} /></Link>
            </div>
            {topImage && <img src={wideImage(topImage, 900, 500)} alt="" />}
          </article>
        </div>
      </section>

      <section className="section">
        <div className="section-heading">
          <div><p className="eyebrow">{t('home.newEyebrow')}</p><h2>{t('home.newest')}</h2></div>
          <Link className="view-all" to="/browse?sort=newest">{t('home.seeNewest')} <Icon name="chevron" size={16} /></Link>
        </div>
        <div className="game-grid">
          {data.newest.map((game) => <GameCard key={game.id} game={game} />)}
        </div>
      </section>
    </>
  );
}
