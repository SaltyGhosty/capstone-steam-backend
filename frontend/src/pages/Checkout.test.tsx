// Flujo de compra: carrito -> pedido PENDING -> pago (aprobado o rechazado con 402).
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { setToken } from '@/api/client';
import { RequireAuth } from '@/components/ui';
import { mockFetch, page } from '@/test/http';
import { demoUser, renderRoutes } from '@/test/render';
import Cart from './Cart';
import { OrderDetail } from './Orders';

const item = {
  gameId: 13, title: 'Neon Drift', slug: 'neon-drift', coverUrl: null,
  price: 19.99, discountPercent: 0, finalPrice: 19.99, available: true, addedAt: '2026-09-30T10:00:00Z',
};
const cart = { items: [item], itemCount: 1, total: 19.99 };
const pendingOrder = {
  id: 11, status: 'PENDING', total: 19.99, createdAt: '2026-09-30T10:00:00Z', paidAt: null,
  items: [{ gameId: 13, title: 'Neon Drift', slug: 'neon-drift', coverUrl: null, unitPrice: 19.99 }],
  lastPayment: null,
};

const routes = {
  '/cart': <RequireAuth><Cart /></RequireAuth>,
  '/orders/:id': <RequireAuth><OrderDetail /></RequireAuth>,
};

function session(extra: Parameters<typeof mockFetch>[0]) {
  setToken('jwt-demo');
  return mockFetch({
    'GET /api/users/me': { body: demoUser },
    'GET /api/wishlist': { body: page([]) },
    ...extra,
  });
}

describe('checkout', () => {
  it('crea el pedido desde el carrito y paga con tarjeta', async () => {
    let paid = false;
    const fetchMock = session({
      'GET /api/cart': () => ({ body: paid ? { items: [], itemCount: 0, total: 0 } : cart }),
      'POST /api/orders': { status: 201, body: pendingOrder },
      'GET /api/orders/11': { body: pendingOrder },
      'POST /api/orders/11/pay': () => {
        paid = true;
        return { body: { ...pendingOrder, status: 'PAID', paidAt: '2026-09-30T10:01:00Z',
          lastPayment: { method: 'CREDIT_CARD', status: 'SUCCEEDED', reference: 'SIM-1', failureReason: null, createdAt: '2026-09-30T10:01:00Z' } } };
      },
    });
    const { path } = renderRoutes(routes, '/cart');

    expect(await screen.findByText('Neon Drift')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Continuar al pago' }));
    await waitFor(() => expect(path()).toBe('/orders/11'));

    await userEvent.type(await screen.findByLabelText(/Número de tarjeta/), '4242 4242 4242 4242');
    await userEvent.click(screen.getByRole('button', { name: /^Pagar/ }));

    expect(await screen.findByText('¡Compra completada!')).toBeInTheDocument();
    const payCall = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/api/orders/11/pay'))!;
    // los espacios se quitan antes de enviar el número
    expect(JSON.parse(String(payCall[1]!.body))).toEqual({ method: 'CREDIT_CARD', cardNumber: '4242424242424242' });
  });

  it('muestra el motivo cuando el pago se rechaza (402)', async () => {
    session({
      'GET /api/cart': { body: cart },
      'GET /api/orders/11': { body: pendingOrder },
      'POST /api/orders/11/pay': {
        status: 402,
        body: { ...pendingOrder, status: 'FAILED',
          lastPayment: { method: 'CREDIT_CARD', status: 'FAILED', reference: 'SIM-2', failureReason: 'Tarjeta rechazada por el emisor', createdAt: '2026-09-30T10:01:00Z' } },
      },
    });
    renderRoutes(routes, '/orders/11');

    await userEvent.type(await screen.findByLabelText(/Número de tarjeta/), '4242424242420000');
    await userEvent.click(screen.getByRole('button', { name: /^Pagar/ }));

    expect(await screen.findByText(/Tarjeta rechazada por el emisor\./)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver al carrito' })).toBeInTheDocument();
  });

  it('valida el número de tarjeta antes de llamar a la API', async () => {
    const fetchMock = session({ 'GET /api/cart': { body: cart }, 'GET /api/orders/11': { body: pendingOrder } });
    renderRoutes(routes, '/orders/11');

    await userEvent.type(await screen.findByLabelText(/Número de tarjeta/), '1234');
    await userEvent.click(screen.getByRole('button', { name: /^Pagar/ }));

    expect(screen.getByText('Introduce entre 12 y 19 dígitos')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/pay'))).toBe(false);
  });

  it('sin sesión, el carrito redirige al login', async () => {
    mockFetch({});
    const { path } = renderRoutes({ ...routes, '/login': <p>Login</p> }, '/cart');
    await waitFor(() => expect(path()).toBe('/login'));
  });
});
