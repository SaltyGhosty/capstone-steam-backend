import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { cartApi, orderApi } from '@/api/endpoints';
import Icon from '@/components/Icon';
import { Empty, Loading, PageHeader, Price } from '@/components/ui';
import { useStore } from '@/context/StoreContext';
import { cardImage, formatMoney } from '@/lib/format';

export default function Cart() {
  const { cart, removeFromCart, refreshCart, notifyError } = useStore();
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);

  if (!cart) return <Loading />;

  const available = cart.items.filter((i) => i.available);
  const hasUnavailable = available.length < cart.items.length;

  // Paso 1 del checkout: el backend congela el carrito en un pedido PENDING
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
    if (!window.confirm('¿Vaciar el carrito?')) return;
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
        <PageHeader eyebrow="TU COMPRA" title="Carrito" />
        <Empty icon="cart" title="Tu carrito está vacío">
          <p>Añade juegos desde el catálogo y vuelve aquí para pagar.</p>
          <Link className="primary small" to="/browse">Explorar juegos</Link>
        </Empty>
      </>
    );
  }

  return (
    <>
      <PageHeader eyebrow="TU COMPRA" title="Carrito">
        <button className="view-all" onClick={clear}><Icon name="trash" size={15} /> Vaciar carrito</button>
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
                {!item.available && <p className="warn">Ya no está a la venta: no se incluirá en el pedido.</p>}
              </div>
              <Price price={item.price} finalPrice={item.finalPrice} discountPercent={item.discountPercent} />
              <button className="icon-button" onClick={() => removeFromCart(item.gameId)} aria-label={`Quitar ${item.title}`}>
                <Icon name="close" size={16} />
              </button>
            </article>
          ))}
        </div>
        <aside className="panel summary">
          <h3>Resumen</h3>
          <div className="summary-row"><span>{available.length} {available.length === 1 ? 'juego' : 'juegos'}</span><b>{formatMoney(cart.total)}</b></div>
          {hasUnavailable && <p className="warn">Hay juegos no disponibles en tu carrito. Quítalos para continuar.</p>}
          <div className="summary-row total"><span>Total</span><b>{formatMoney(cart.total)}</b></div>
          <button className="primary full" onClick={checkout} disabled={busy || available.length === 0 || hasUnavailable}>
            {busy ? 'Creando pedido…' : 'Continuar al pago'}
          </button>
          <p className="hint small">Pago simulado: no se hace ningún cargo real.</p>
        </aside>
      </div>
    </>
  );
}
