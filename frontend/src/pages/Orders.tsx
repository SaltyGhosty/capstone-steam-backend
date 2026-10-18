import { useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router';
import { ApiError } from '@/api/client';
import { orderApi } from '@/api/endpoints';
import type { Order, PaymentMethod } from '@/api/types';
import Icon from '@/components/Icon';
import { Empty, ErrorState, FieldError, Loading, PageHeader, Pagination } from '@/components/ui';
import { useStore } from '@/context/StoreContext';
import { useI18n } from '@/i18n';
import { cardImage, formatDateTime, formatMoney, orderStatus } from '@/lib/format';
import { useAsync } from '@/lib/useAsync';

export function OrderList() {
  const [page, setPage] = useState(0);
  const orders = useAsync(() => orderApi.list(page, 15), [page]);
  const { t } = useI18n();

  return (
    <>
      <PageHeader eyebrow={t('orders.eyebrow')} title={t('nav.myOrders')} />
      {orders.loading && !orders.data && <Loading />}
      {orders.error ? <ErrorState error={orders.error} onRetry={orders.reload} /> : null}
      {orders.data?.content.length === 0 && (
        <Empty icon="receipt" title={t('orders.empty')}>
          <Link className="primary small" to="/browse">{t('common.browseGames')}</Link>
        </Empty>
      )}
      {orders.data && orders.data.content.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>{t('orders.order')}</th><th>{t('orders.date')}</th><th>{t('orders.games')}</th><th>{t('common.total')}</th><th>{t('orders.status')}</th><th /></tr></thead>
            <tbody>
              {orders.data.content.map((o) => (
                <tr key={o.id}>
                  <td>#{o.id}</td>
                  <td>{formatDateTime(o.createdAt)}</td>
                  <td>{o.itemCount}</td>
                  <td>{formatMoney(o.total)}</td>
                  <td><span className={`badge ${o.status.toLowerCase()}`}>{orderStatus(o.status)}</span></td>
                  <td><Link className="view-all" to={`/orders/${o.id}`}>{o.status === 'PENDING' ? t('orders.pay') : t('orders.view')} <Icon name="chevron" size={14} /></Link></td>
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

const METHODS: PaymentMethod[] = ['CREDIT_CARD', 'PAYPAL', 'WALLET'];

export function OrderDetail() {
  const { id } = useParams();
  const { refreshCart, notify, notifyError } = useStore();
  const { t } = useI18n();
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
      setErrors({ cardNumber: t('orders.cardDigits') });
      return;
    }
    setBusy(true);
    setErrors({});
    try {
      const result = await orderApi.pay(o.id, method, method === 'CREDIT_CARD' ? digits : undefined);
      order.setData(result);
      await refreshCart();
      if (result.status === 'PAID') notify(t('orders.paid'));
      else notify(t('orders.rejected'), 'error');
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
      notify(t('orders.cancelled'));
    } catch (e) {
      notifyError(e);
    }
  };

  return (
    <>
      <PageHeader eyebrow={t('orders.orderEyebrow', { id: o.id, date: formatDateTime(o.createdAt) })} title={o.status === 'PENDING' ? t('orders.payTitle') : t('orders.detailTitle')}>
        <span className={`badge large ${o.status.toLowerCase()}`}>{orderStatus(o.status)}</span>
      </PageHeader>

      {o.status === 'PAID' && (
        <div className="banner success">
          <Icon name="library" /> <div><b>{t('orders.doneTitle')}</b> {t('orders.doneText')}</div>
          <Link className="primary small" to="/library">{t('orders.goLibrary')}</Link>
        </div>
      )}
      {o.status === 'FAILED' && (
        <div className="banner error">
          <Icon name="close" /> <div><b>{t('orders.failTitle')}</b> {failureText} {t('orders.failText')}</div>
          <Link className="secondary small" to="/cart">{t('orders.backCart')}</Link>
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
          <div className="summary-row total"><span>{t('common.total')}</span><b>{formatMoney(o.total)}</b></div>
          {o.status === 'PENDING' ? (
            <form onSubmit={pay} className="pay-form">
              <div className="segmented">
                {METHODS.map((m) => (
                  <button type="button" key={m} className={method === m ? 'active' : ''} onClick={() => setMethod(m)}>{t(`orders.method.${m}`)}</button>
                ))}
              </div>
              {method === 'CREDIT_CARD' && (
                <label className="field">
                  <span>{t('orders.cardNumber')}</span>
                  <input inputMode="numeric" autoComplete="off" value={card} onChange={(e) => setCard(e.target.value)} placeholder="4242 4242 4242 4242" />
                  <FieldError errors={errors} name="cardNumber" />
                  <small className="hint">{t('orders.cardHint')}</small>
                </label>
              )}
              <button className="primary full" disabled={busy}>{busy ? t('orders.processing') : t('orders.payAmount', { amount: formatMoney(o.total) })}</button>
              <button type="button" className="secondary full" onClick={cancel} disabled={busy}>{t('orders.cancel')}</button>
            </form>
          ) : (
            o.lastPayment && (
              <dl className="facts">
                <dt>{t('orders.method')}</dt><dd>{t(`orders.method.${o.lastPayment.method}`)}</dd>
                <dt>{t('orders.reference')}</dt><dd>{o.lastPayment.reference ?? '—'}</dd>
                <dt>{t('orders.paidAt')}</dt><dd>{formatDateTime(o.paidAt)}</dd>
              </dl>
            )
          )}
        </aside>
      </div>
    </>
  );
}
