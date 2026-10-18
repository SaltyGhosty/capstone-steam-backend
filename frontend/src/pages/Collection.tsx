// Biblioteca y wishlist: las dos colecciones del usuario.
import { useState } from 'react';
import { Link } from 'react-router';
import { libraryApi, wishlistApi } from '@/api/endpoints';
import Icon from '@/components/Icon';
import { Empty, ErrorState, Loading, PageHeader, Pagination, Price } from '@/components/ui';
import { useStore } from '@/context/StoreContext';
import { useI18n } from '@/i18n';
import { cardImage, formatDate, formatPlaytime } from '@/lib/format';
import { useAsync } from '@/lib/useAsync';

// No hay cliente de juego real: cada partida suma una sesión simulada de 30 a 90 minutos
const randomSession = () => 30 + Math.floor(Math.random() * 61);

export function Library() {
  const { notify, notifyError } = useStore();
  const { t } = useI18n();
  const [page, setPage] = useState(0);
  const [playing, setPlaying] = useState<number | null>(null);
  const library = useAsync(() => libraryApi.list(page, 24), [page]);
  const data = library.data;

  const play = async (gameId: number, title: string) => {
    setPlaying(gameId);
    try {
      const minutes = randomSession();
      const updated = await libraryApi.play(gameId, minutes);
      library.setData((prev) => prev && {
        ...prev,
        content: prev.content.map((e) => (e.gameId === gameId ? updated : e)),
      });
      notify(t('library.played', { minutes, title }));
    } catch (e) {
      notifyError(e);
    } finally {
      setPlaying(null);
    }
  };

  return (
    <>
      <PageHeader eyebrow={t('library.eyebrow')} title={t('nav.library')}>
        {data && <span className="result-count">{t('common.games', { count: data.totalElements })}</span>}
      </PageHeader>
      {library.loading && !data && <Loading />}
      {library.error ? <ErrorState error={library.error} onRetry={library.reload} /> : null}
      {data?.content.length === 0 && (
        <Empty icon="library" title={t('library.empty')}>
          <p>{t('library.emptyHint')}</p>
          <Link className="primary small" to="/browse">{t('common.browseGames')}</Link>
        </Empty>
      )}
      <div className="library-grid">
        {data?.content.map((entry) => (
          <article key={entry.gameId} className="library-card">
            <Link to={`/games/${entry.slug}`} className="cover tall">
              {entry.coverUrl && <img src={entry.coverUrl} alt="" loading="lazy" />}
            </Link>
            <div className="library-info">
              <h3><Link to={`/games/${entry.slug}`}>{entry.title}</Link></h3>
              <p>{t(entry.orderId ? 'library.bought' : 'library.gift', { date: formatDate(entry.acquiredAt) })}</p>
              <p>{formatPlaytime(entry.playtimeMinutes)}</p>
              <button className="primary small full-width" onClick={() => play(entry.gameId, entry.title)} disabled={playing === entry.gameId}>
                <Icon name="play" size={14} /> {playing === entry.gameId ? t('library.playing') : t('library.play')}
              </button>
            </div>
          </article>
        ))}
      </div>
      {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />}
    </>
  );
}

export function Wishlist() {
  const { toggleWishlist, addToCart, cart } = useStore();
  const { t } = useI18n();
  const [page, setPage] = useState(0);
  const wishlist = useAsync(() => wishlistApi.list(page, 24), [page]);
  const data = wishlist.data;
  const inCart = new Set(cart?.items.map((i) => i.gameId));

  const remove = async (gameId: number, title: string) => {
    await toggleWishlist(gameId, title);
    wishlist.reload();
  };

  return (
    <>
      <PageHeader eyebrow={t('library.eyebrow')} title={t('nav.wishlist')} />
      {wishlist.loading && !data && <Loading />}
      {wishlist.error ? <ErrorState error={wishlist.error} onRetry={wishlist.reload} /> : null}
      {data?.content.length === 0 && (
        <Empty icon="heart" title={t('wishlist.empty')}>
          <p>{t('wishlist.emptyHint')}</p>
          <Link className="primary small" to="/browse">{t('common.browseGames')}</Link>
        </Empty>
      )}
      <div className="line-list">
        {data?.content.map((item) => (
          <article key={item.gameId} className={item.available ? 'line' : 'line unavailable'}>
            <Link to={`/games/${item.slug}`} className="line-cover">{item.coverUrl && <img src={cardImage(item.coverUrl)} alt="" />}</Link>
            <div className="line-info">
              <h3><Link to={`/games/${item.slug}`}>{item.title}</Link></h3>
              <p>{t('wishlist.added', { date: formatDate(item.addedAt) })}</p>
              {item.onSale && item.available && <span className="badge paid">{t('wishlist.onSale')}</span>}
              {!item.available && <p className="warn">{t('common.unavailable')}</p>}
            </div>
            <Price price={item.price} finalPrice={item.finalPrice} discountPercent={item.discountPercent} />
            <div className="line-actions">
              {item.available && (inCart.has(item.gameId)
                ? <Link className="secondary small" to="/cart">{t('wishlist.inCart')}</Link>
                : <button className="primary small" onClick={() => addToCart(item.gameId, item.title)}><Icon name="cart" size={15} /> {t('wishlist.toCart')}</button>)}
              <button className="icon-button" onClick={() => remove(item.gameId, item.title)} aria-label={t('card.removeWish', { title: item.title })}>
                <Icon name="close" size={16} />
              </button>
            </div>
          </article>
        ))}
      </div>
      {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />}
    </>
  );
}
