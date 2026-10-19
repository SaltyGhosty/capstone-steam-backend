import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { setToken } from '@/api/client';
import { mockFetch, page } from '@/test/http';
import { demoUser, renderRoutes } from '@/test/render';
import AssistantChat from './AssistantChat';

const base = {
  'GET /api/users/me': { body: demoUser },
  'GET /api/cart': { body: { items: [], itemCount: 0, total: 0 } },
  'GET /api/wishlist': { body: page([]) },
};

const hades = {
  id: 1, title: 'Hades', slug: 'hades', coverUrl: null, price: 24.99, discountPercent: 0, finalPrice: 24.99,
  releaseDate: null, reviewCount: 120, positivePercent: 96, reviewScore: 'OVERWHELMINGLY_POSITIVE',
  genres: [], developerName: 'Supergiant',
};

const bodiesOf = (fetchMock: ReturnType<typeof mockFetch>) =>
  fetchMock.mock.calls
    .filter(([url, init]) => String(url).endsWith('/api/recommendations/chat') && (init as RequestInit | undefined)?.method === 'POST')
    .map(([, init]) => JSON.parse(String((init as RequestInit).body)));

const open = async () => {
  await userEvent.click(await screen.findByRole('button', { name: 'Abrir el asistente' }));
  return screen.findByRole('textbox', { name: 'Escribe tu mensaje' });
};

describe('chat del asistente', () => {
  beforeEach(() => setToken('jwt-demo'));

  it('sin sesión no hay botón', async () => {
    setToken(null);
    mockFetch({ 'GET /api/cart': { status: 401, body: {} } });
    renderRoutes({ '/': <AssistantChat /> }, '/');

    await waitFor(() => expect(screen.queryByRole('button', { name: 'Abrir el asistente' })).not.toBeInTheDocument());
  });

  it('envía el mensaje y muestra la respuesta con una tarjeta que abre la ficha', async () => {
    const fetchMock = mockFetch({
      ...base,
      'POST /api/recommendations/chat': { body: { reply: 'Prueba este', games: [hades] } },
    });
    renderRoutes({ '/': <AssistantChat /> }, '/');

    const input = await open();
    expect(screen.getByRole('dialog', { name: 'Asistente de recomendaciones' })).toBeInTheDocument();
    expect(input).toHaveFocus();
    expect(input).toHaveAttribute('maxlength', '500');
    expect(screen.getByText(/Dime qué te apetece/)).toBeInTheDocument();

    await userEvent.type(input, 'algo oscuro');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar' }));

    expect(await screen.findByText('Prueba este')).toBeInTheDocument();
    expect(bodiesOf(fetchMock)).toEqual([{ messages: [{ role: 'user', content: 'algo oscuro' }] }]);
    const card = screen.getByRole('link', { name: /Hades/ });
    expect(card).toHaveAttribute('href', '/games/hades');
    expect(card).toHaveTextContent('24,99');
    expect(screen.getByText('algo oscuro')).toBeInTheDocument();
  });

  it('el segundo envío manda también los mensajes anteriores, como mucho los 10 últimos', async () => {
    const fetchMock = mockFetch({
      ...base,
      'POST /api/recommendations/chat': { body: { reply: 'ok', games: [] } },
    });
    renderRoutes({ '/': <AssistantChat /> }, '/');
    const input = await open();

    await userEvent.type(input, 'uno');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar' }));
    await waitFor(() => expect(bodiesOf(fetchMock)).toHaveLength(1));
    await screen.findByText('ok');
    await userEvent.type(input, 'dos');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar' }));

    await waitFor(() => expect(bodiesOf(fetchMock)).toHaveLength(2));
    expect(bodiesOf(fetchMock)[1].messages).toEqual([
      { role: 'user', content: 'uno' },
      { role: 'assistant', content: 'ok' },
      { role: 'user', content: 'dos' },
    ]);

    // con una conversación larga solo viajan los 10 últimos
    for (const text of ['tres', 'cuatro', 'cinco', 'seis']) {
      await userEvent.type(input, text);
      await userEvent.click(screen.getByRole('button', { name: 'Enviar' }));
      await waitFor(() => expect(screen.getAllByText('ok').length).toBeGreaterThan(0));
    }
    await waitFor(() => expect(bodiesOf(fetchMock)).toHaveLength(6));
    const last = bodiesOf(fetchMock)[5].messages;
    expect(last).toHaveLength(10);
    expect(last.at(-1)).toEqual({ role: 'user', content: 'seis' });
  });

  it('un 503 muestra el aviso de no disponible y un 429 el del límite', async () => {
    let status = 503;
    mockFetch({
      ...base,
      'POST /api/recommendations/chat': () => ({ status, body: { detail: 'texto del servidor' } }),
    });
    renderRoutes({ '/': <AssistantChat /> }, '/');
    const input = await open();

    await userEvent.type(input, 'hola');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar' }));
    expect(await screen.findByText('El asistente no está disponible ahora')).toBeInTheDocument();

    status = 429;
    await userEvent.type(input, 'otra');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar' }));
    expect(await screen.findByText(/demasiados mensajes/)).toBeInTheDocument();
  });

  it('Esc y el botón de cerrar lo cierran', async () => {
    mockFetch(base);
    renderRoutes({ '/': <AssistantChat /> }, '/');

    await open();
    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    await open();
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar el asistente' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
