import { useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router';
import { ApiError } from '@/api/client';
import { orderApi } from '@/api/endpoints';
import type { Order, PaymentMethod } from '@/api/types';
import Icon from '@/components/Icon';
import { Empty, ErrorState, FieldError, Loading, PageHeader, Pagination } from '@/components/ui';
import { useStore } from '@/context/StoreContext';
import { cardImage, formatDateTime, formatMoney, ORDER_STATUS } from '@/lib/format';
import { useAsync } from '@/lib/useAsync';

export function OrderList() {
  const [page, setPage] = useState(0);
  const orders = useAsync(() => orderApi.list(page, 15), [page]);

  return (
    <>
      <PageHeader eyebrow="HISTORIAL" title="Mis pedidos" />
      {orders.loading && !orders.data && <Loading />}
      {orders.error ? <ErrorState error={orders.error} onRetry={orders.reload} /> : null}
      {orders.data?.content.length === 0 && (
        <Empty icon="receipt" title="Aún no has hecho ningún pedido">
          <Link className="primary small" to="/browse">Explorar juegos</Link>
        </Empty>
      )}
      {orders.data && orders.data.content.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>Pedido</th><th>Fecha</th><th>Juegos</th><th>Total</th><th>Estado</th><th /></tr></thead>
            <tbody>
              {orders.data.content.map((o) => (
                <tr key={o.id}>
                  <td>#{o.id}</td>
                  <td>{formatDateTime(o.createdAt)}</td>
                  <td>{o.itemCount}</td>
                  <td>{formatMoney(o.total)}</td>
                  <td><span className={`badge ${o.status.toLowerCase()}`}>{ORDER_STATUS[o.status]}</span></td>
                  <td><Link className="view-all" to={`/orders/${o.id}`}>{o.status === 'PENDING' ? 'Pagar' : 'Ver'} <Icon name="chevron" size={14} /></Link></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {orders.data && <Pagination page={orders.data.page} totalPages={orders.data.totalPages} onChange={setPage} />}
    </>
  );
}

const METHODS: { value: PaymentMethod; label: string }[] = [
  { value: 'CREDIT_CARD', label: 'Tarjeta' },
  { value: 'PAYPAL', label: 'PayPal' },
  { value: 'WALLET', label: 'Monedero' },
];

export function OrderDetail() {
  const { id } = useParams();
  const { refreshCart, notify, notifyError } = useStore();
  const order = useAsync(() => orderApi.get(Number(id)), [id]);
  const [method, setMethod] = useState<PaymentMethod>('CREDIT_CARD');
  const [card, setCard] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  if (order.loading && !order.data) return <Loading />;
  if (order.error || !order.data) return <ErrorState error={order.error} onRetry={order.reload} />;
  const o: Order = order.data;
  const reason = o.lastPayment?.failureReason?.trim() ?? '';
  const failureText = reason && !/[.!?]$/.test(reason) ? `${reason}.` : reason;

  // Paso 2 del checkout: 200 → PAID, 402 → FAILED (ambos devuelven el pedido)
  const pay = async (e: FormEvent) => {
    e.preventDefault();
    const digits = card.replace(/\s+/g, '');
    if (method === 'CREDIT_CARD' && !/^\d{12,19}$/.test(digits)) {
      setErrors({ cardNumber: 'Introduce entre 12 y 19 dígitos' });
      return;
    }
    setBusy(true);
    setErrors({});
    try {
      const result = await orderApi.pay(o.id, method, method === 'CREDIT_CARD' ? digits : undefined);
      order.setData(result);
      await refreshCart();
      if (result.status === 'PAID') notify('¡Pago completado! Tus juegos ya están en la biblioteca.');
      else notify('El pago ha sido rechazado', 'error');
    } catch (err) {
      if (err instanceof ApiError) setErrors(err.fieldErrors);
      notifyError(err);
      order.reload();
    } finally {
      setBusy(false);
    }
  };

  const cancel = async () => {
    try {
      order.setData(await orderApi.cancel(o.id));
      notify('Pedido cancelado');
    } catch (e) {
      notifyError(e);
    }
  };

  return (
    <>
      <PageHeader eyebrow={`PEDIDO #${o.id} · ${formatDateTime(o.createdAt)}`} title={o.status === 'PENDING' ? 'Pagar pedido' : 'Detalle del pedido'}>
        <span className={`badge large ${o.status.toLowerCase()}`}>{ORDER_STATUS[o.status]}</span>
      </PageHeader>

      {o.status === 'PAID' && (
        <div className="banner success">
          <Icon name="library" /> <div><b>¡Compra completada!</b> Los juegos ya están en tu biblioteca. Te hemos enviado el recibo por correo.</div>
          <Link className="primary small" to="/library">Ir a la biblioteca</Link>
        </div>
      )}
      {o.status === 'FAILED' && (
        <div className="banner error">
          <Icon name="close" /> <div><b>Pago rechazado.</b> {failureText} Los juegos siguen en tu carrito para volver a intentarlo.</div>
          <Link className="secondary small" to="/cart">Volver al carrito</Link>
        </div>
      )}

      <div className="checkout-grid">
        <div className="line-list">
          {o.items.map((item) => (
            <article key={item.gameId} className="line">
              <Link to={`/games/${item.slug}`} className="line-cover">{item.coverUrl && <img src={cardImage(item.coverUrl)} alt="" />}</Link>
              <div className="line-info"><h3><Link to={`/games/${item.slug}`}>{item.title}</Link></h3></div>
              <span className="price"><b>{formatMoney(item.unitPrice)}</b></span>
            </article>
          ))}
        </div>

        <aside className="panel summary">
          <div className="summary-row total"><span>Total</span><b>{formatMoney(o.total)}</b></div>
          {o.status === 'PENDING' ? (
            <form onSubmit={pay} className="pay-form">
              <div className="segmented">
                {METHODS.map((m) => (
                  <button type="button" key={m.value} className={method === m.value ? 'active' : ''} onClick={() => setMethod(m.value)}>{m.label}</button>
                ))}
              </div>
              {method === 'CREDIT_CARD' && (
                <label className="field">
                  <span>Número de tarjeta</span>
                  <input inputMode="numeric" autoComplete="off" value={card} onChange={(e) => setCard(e.target.value)} placeholder="4242 4242 4242 4242" />
                  <FieldError errors={errors} name="cardNumber" />
                  <small className="hint">Simulado: cualquier número vale; si termina en 0000, se rechaza. No se guarda.</small>
                </label>
              )}
              <button className="primary full" disabled={busy}>{busy ? 'Procesando…' : `Pagar ${formatMoney(o.total)}`}</button>
              <button type="button" className="secondary full" onClick={cancel} disabled={busy}>Cancelar pedido</button>
            </form>
          ) : (
            o.lastPayment && (
              <dl className="facts">
                <dt>Método</dt><dd>{METHODS.find((m) => m.value === o.lastPayment!.method)?.label}</dd>
                <dt>Referencia</dt><dd>{o.lastPayment.reference ?? '—'}</dd>
                <dt>Pagado</dt><dd>{formatDateTime(o.paidAt)}</dd>
              </dl>
            )
          )}
        </aside>
      </div>
    </>
  );
}
