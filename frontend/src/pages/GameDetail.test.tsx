// Ficha del juego: puntuación estilo Steam, filtro de reseñas por idioma y formulario sin estrellas.
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { setToken } from '@/api/client';
import { mockFetch, page, type Routes } from '@/test/http';
import { t, useI18n } from '@/i18n';
import { demoUser, renderRoutes } from '@/test/render';
import GameDetail from './GameDetail';

const game = {
  id: 13, title: 'Neon Drift', slug: 'neon-drift', description: 'Carreras', descriptions: { es: 'Carreras', en: null, it: null },
  coverUrl: null, steamAppId: null, priceSyncedAt: null, price: 19.99, discountPercent: 0, finalPrice: 19.99,
  releaseDate: '2026-01-10', status: 'PUBLISHED', reviewCount: 60, positivePercent: 85, reviewScore: 'VERY_POSITIVE',
  salesCount: 120, genres: [], developer: { id: 2, displayName: 'Studio' },
  createdAt: '2026-01-01T10:00:00Z', updatedAt: '2026-01-01T10:00:00Z', version: 0,
};

const review = {
  id: 1, gameId: 13, recommended: true, body: 'Muy divertido', language: 'es',
  author: { id: 3, displayName: 'Ana' }, createdAt: '2026-02-01T10:00:00Z', updatedAt: '2026-02-01T10:00:00Z',
};

function LangSwitch() {
  const { setLang } = useI18n();
  return <button onClick={() => setLang('it')}>cambiar-a-it</button>;
}

const routes = { '/games/:slug': <GameDetail /> };
const routesWithSwitch = { '/games/:slug': <><LangSwitch /><GameDetail /></> };

function setup(extra: Routes = {}, loggedIn = false) {
  if (loggedIn) setToken('jwt-demo');
  return mockFetch({
    'GET /api/games/neon-drift': { body: game },
    'GET /api/games/neon-drift/reviews': { body: page([review]) },
    'GET /api/users/me': { body: demoUser },
    'GET /api/wishlist': { body: page([]) },
    'GET /api/cart': { body: { items: [], itemCount: 0, total: 0 } },
    ...extra,
  });
}

const reviewCalls = (fetchMock: ReturnType<typeof mockFetch>) =>
  fetchMock.mock.calls
    .map(([url]) => new URL(String(url), 'http://localhost'))
    .filter((url) => url.pathname === '/api/games/neon-drift/reviews');

describe('ficha del juego', () => {
  it('muestra la etiqueta y el porcentaje de reseñas positivas', async () => {
    setup();
    renderRoutes(routes, '/games/neon-drift');

    expect(await screen.findByText('Muy positivas')).toBeInTheDocument();
    expect(screen.getByText(/85 %/)).toBeInTheDocument();
    expect(screen.getByText(/60 reseñas/)).toBeInTheDocument();
  });

  it('pide las reseñas en el idioma de la interfaz y con «Todos los idiomas» las pide todas', async () => {
    const fetchMock = setup();
    renderRoutes(routes, '/games/neon-drift');

    expect(await screen.findByText('Muy divertido')).toBeInTheDocument();
    expect(reviewCalls(fetchMock)[0].searchParams.get('language')).toBe('es');

    await userEvent.click(screen.getByRole('button', { name: 'Todos los idiomas' }));
    await waitFor(() => expect(reviewCalls(fetchMock).length).toBeGreaterThan(1));
    expect(reviewCalls(fetchMock).at(-1)!.searchParams.has('language')).toBe(false);
  });

  it('avisa cuando no hay reseñas en tu idioma pero sí en otros', async () => {
    setup({ 'GET /api/games/neon-drift/reviews': { body: page([]) } });
    renderRoutes(routes, '/games/neon-drift');

    expect(await screen.findByText(/en tu idioma/i)).toBeInTheDocument();
    // el control de idioma y el botón del aviso
    expect(screen.getAllByRole('button', { name: 'Todos los idiomas' })).toHaveLength(2);
  });

  it('envía el voto y el idioma, sin puntuación', async () => {
    const fetchMock = setup({
      'GET /api/library/status/13': { body: { gameId: 13, owned: true, inCart: false, inWishlist: false, reviewed: false } },
      'POST /api/reviews': { status: 201, body: { ...review, id: 9, author: { id: 7, displayName: 'Demo Player' } } },
    }, true);
    renderRoutes(routes, '/games/neon-drift');

    const form = (await screen.findByRole('heading', { name: 'Escribe tu reseña' })).closest('form')!;
    // sin elegir veredicto no se envía
    await userEvent.click(within(form).getByRole('button', { name: 'Publicar reseña' }));
    expect(await within(form).findByText('Elige si recomiendas el juego o no')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/api/reviews'))).toBe(false);

    await userEvent.click(within(form).getByRole('button', { name: 'Lo recomiendo' }));
    await userEvent.type(within(form).getByLabelText('Texto de la reseña'), 'Genial');
    await userEvent.click(within(form).getByRole('button', { name: 'Publicar reseña' }));

    await waitFor(() => expect(fetchMock.mock.calls.some(([url, init]) =>
      String(url).endsWith('/api/reviews') && init?.method === 'POST')).toBe(true));
    const call = fetchMock.mock.calls.find(([url, init]) => String(url).endsWith('/api/reviews') && init?.method === 'POST')!;
    const sent = JSON.parse(String(call[1]!.body));
    expect(sent).toEqual({ gameId: 13, recommended: true, body: 'Genial', language: 'es' });
    expect(sent).not.toHaveProperty('rating');
  });

  it('al editar solo el texto vuelve a pedir las reseñas y muestra el texto nuevo', async () => {
    let current = { ...review, author: { id: 7, displayName: 'Demo Player' } };
    const fetchMock = setup({
      'GET /api/games/neon-drift/reviews': () => ({ body: page([current]) }),
      'GET /api/library/status/13': { body: { gameId: 13, owned: true, inCart: false, inWishlist: false, reviewed: true } },
      'PUT /api/reviews/1': () => {
        current = { ...current, body: 'Texto nuevo' };
        return { body: current };
      },
    }, true);
    renderRoutes(routes, '/games/neon-drift');

    expect(await screen.findByText('Muy divertido')).toBeInTheDocument();
    const before = reviewCalls(fetchMock).length;
    await userEvent.click(await screen.findByRole('button', { name: 'Editar' }));
    const form = screen.getByRole('heading', { name: 'Editar tu reseña' }).closest('form')!;
    const text = within(form).getByLabelText('Texto de la reseña');
    await userEvent.clear(text);
    await userEvent.type(text, 'Texto nuevo');
    await userEvent.click(within(form).getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText('Texto nuevo')).toBeInTheDocument();
    expect(reviewCalls(fetchMock).length).toBeGreaterThan(before);
  });

  it('editar una reseña conserva su idioma aunque la interfaz esté en otro', async () => {
    const fetchMock = setup({
      'GET /api/games/neon-drift/reviews': { body: page([{ ...review, author: { id: 7, displayName: 'Demo Player' } }]) },
      'GET /api/library/status/13': { body: { gameId: 13, owned: true, inCart: false, inWishlist: false, reviewed: true } },
      'PUT /api/reviews/1': { body: review },
    }, true);
    renderRoutes(routesWithSwitch, '/games/neon-drift');

    expect(await screen.findByText('Muy divertido')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'cambiar-a-it' }));
    await userEvent.click(await screen.findByRole('button', { name: t('common.edit') }));
    const form = screen.getByRole('heading', { name: t('review.editYours') }).closest('form')!;
    await userEvent.click(within(form).getByRole('button', { name: t('common.saveChanges') }));

    await waitFor(() => expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(true));
    const call = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT')!;
    expect(JSON.parse(String(call[1]!.body)).language).toBe('es');
  });
});
