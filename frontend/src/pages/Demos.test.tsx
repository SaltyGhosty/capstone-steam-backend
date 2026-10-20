// Página /demos: una tarjeta por demo cuyo juego está publicado.
import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { mockFetch } from '@/test/http';
import { renderRoutes } from '@/test/render';
import Demos from './Demos';

const base = {
  description: '', descriptions: { es: '', en: null, it: null }, coverUrl: null, steamAppId: null, priceSyncedAt: null,
  price: 10, discountPercent: 0, finalPrice: 10, releaseDate: '2020-01-01', status: 'PUBLISHED', reviewCount: 0,
  positivePercent: 0, reviewScore: 'NONE', salesCount: 0, genres: [], developer: { id: 2, displayName: 'Studio' },
  createdAt: '2026-01-01T10:00:00Z', updatedAt: '2026-01-01T10:00:00Z', version: 0,
};
const game = (id: number, slug: string, title: string) => ({ ...base, id, slug, title });
const notFound = { status: 404, body: { detail: 'no existe', status: 404 } };

describe('página de demos', () => {
  it('sin ninguna demo disponible muestra un estado vacío', async () => {
    mockFetch({
      'GET /api/games/celeste': notFound,
      'GET /api/games/vampire-survivors': notFound,
      'GET /api/games/rocket-league': { status: 500, body: { detail: 'caído' } },
      'GET /api/games/balatro': notFound,
    });
    renderRoutes({ '/demos': <Demos /> }, '/demos');
    expect(await screen.findByText('Ahora mismo no hay demos disponibles')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Jugar demo/ })).not.toBeInTheDocument();
  });

  it('lista las demos de los juegos publicados', async () => {
    mockFetch({
      'GET /api/games/celeste': { body: game(1, 'celeste', 'Celeste') },
      'GET /api/games/vampire-survivors': { body: game(2, 'vampire-survivors', 'Vampire Survivors') },
      'GET /api/games/rocket-league': { body: game(3, 'rocket-league', 'Rocket League') },
      'GET /api/games/balatro': { body: game(4, 'balatro', 'Balatro') },
    });
    renderRoutes({ '/demos': <Demos /> }, '/demos');
    expect(await screen.findByRole('heading', { name: 'Celeste' })).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /Jugar demo/ })).toHaveLength(4);
  });

  it('oculta la demo de un juego que no está en el catálogo', async () => {
    mockFetch({
      'GET /api/games/celeste': { body: game(1, 'celeste', 'Celeste') },
      'GET /api/games/vampire-survivors': { body: game(2, 'vampire-survivors', 'Vampire Survivors') },
      'GET /api/games/rocket-league': { body: game(3, 'rocket-league', 'Rocket League') },
      'GET /api/games/balatro': notFound,
    });
    renderRoutes({ '/demos': <Demos /> }, '/demos');
    expect(await screen.findByRole('heading', { name: 'Celeste' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Balatro' })).not.toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /Jugar demo/ })).toHaveLength(3);
    expect(screen.queryByText(/algo ha ido mal/i)).not.toBeInTheDocument();
  });
});
