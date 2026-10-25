import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { cartApi, orderApi } from '@/api/endpoints';
import Icon from '@/components/Icon';
import { Empty, Loading, PageHeader, Price } from '@/components/ui';
import { useStore } from '@/context/StoreContext';
import { useI18n } from '@/i18n';
import { cardImage, formatMoney } from '@/lib/format';

export default function Cart() {
  const { cart, removeFromCart, refreshCart, notifyError } = useStore();
  const { t } = useI18n();
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);

  if (!cart) return <Loading />;

  const available = cart.items.filter((i) => i.available);
  const hasUnavailable = available.length < cart.items.length;

  // Passo 1 del checkout: il backend congela il carrello in un ordine PENDING
  const checkout = async () => {
    setBusy(true);
    try {
      const order = await orderApi.checkout();
      navigate(`/orders/${order.id}`);
    } catch (e) {
      notifyError(e);
      await refreshCart();
    } finally {
      setBusy(false);
    }
  };

  const clear = async () => {
    if (!window.confirm(t('cart.confirmClear'))) return;
    try {
      await cartApi.clear();
      await refreshCart();
    } catch (e) {
      notifyError(e);
    }
  };

  if (cart.items.length === 0) {
    return (
      <>
        <PageHeader eyebrow={t('cart.eyebrow')} title={t('cart.title')} />
        <Empty icon="cart" title={t('cart.empty')}>
          <p>{t('cart.emptyHint')}</p>
          <Link className="primary small" to="/browse">{t('common.browseGames')}</Link>
        </Empty>
      </>
    );
  }

  return (
    <>
      <PageHeader eyebrow={t('cart.eyebrow')} title={t('cart.title')}>
        <button className="view-all" onClick={clear}><Icon name="trash" size={15} /> {t('cart.clear')}</button>
      </PageHeader>
      <div className="checkout-grid">
        <div className="line-list">
          {cart.items.map((item) => (
            <article key={item.gameId} className={item.available ? 'line' : 'line unavailable'}>
              <Link to={`/games/${item.slug}`} className="line-cover">
                {item.coverUrl && <img src={cardImage(item.coverUrl)} alt="" />}
              </Link>
              <div className="line-info">
                <h3><Link to={`/games/${item.slug}`}>{item.title}</Link></h3>
                {!item.available && <p className="warn">{t('cart.unavailable')}</p>}
              </div>
              <Price price={item.price} finalPrice={item.finalPrice} discountPercent={item.discountPercent} />
              <button className="icon-button" onClick={() => removeFromCart(item.gameId)} aria-label={t('cart.remove', { title: item.title })}>
                <Icon name="close" size={16} />
              </button>
            </article>
          ))}
        </div>
        <aside className="panel summary">
          <h3>{t('cart.summary')}</h3>
          <div className="summary-row"><span>{t('common.games', { count: available.length })}</span><b>{formatMoney(cart.total)}</b></div>
          {hasUnavailable && <p className="warn">{t('cart.hasUnavailable')}</p>}
          <div className="summary-row total"><span>{t('common.total')}</span><b>{formatMoney(cart.total)}</b></div>
          <button className="primary full" onClick={checkout} disabled={busy || available.length === 0 || hasUnavailable}>
            {busy ? t('cart.creatingOrder') : t('cart.continue')}
          </button>
          <p className="hint small">{t('cart.simulated')}</p>
        </aside>
      </div>
    </>
  );
}
