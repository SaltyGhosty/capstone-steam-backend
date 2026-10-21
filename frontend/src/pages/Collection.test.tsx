// Biblioteca: la tarjeta muestra el estudio, o «Estudio eliminado» si ya no existe.
import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { setToken } from '@/api/client';
import { mockFetch, page } from '@/test/http';
import { demoUser, renderRoutes } from '@/test/render';
import { Library } from './Collection';

const entry = {
  gameId: 1, title: 'Neon Drift', slug: 'neon-drift', coverUrl: null, acquiredAt: '2026-02-01T10:00:00Z',
  playtimeMinutes: 0, orderId: 4, developerName: 'Supergiant', developerDeleted: false,
};

function setup(entries: unknown[]) {
  setToken('jwt-demo');
  mockFetch({
    'GET /api/users/me': { body: demoUser },
    'GET /api/library': { body: page(entries) },
    'GET /api/wishlist': { body: page([]) },
    'GET /api/cart': { body: { items: [], itemCount: 0, total: 0 } },
  });
}

describe('biblioteca', () => {
  it('muestra el estudio de cada juego', async () => {
    setup([entry]);
    renderRoutes({ '/library': <Library /> }, '/library');
    expect(await screen.findByText('Supergiant')).toBeInTheDocument();
  });

  it('muestra «Estudio eliminado» cuando el estudio ya no existe', async () => {
    setup([{ ...entry, developerName: null, developerDeleted: true }]);
    renderRoutes({ '/library': <Library /> }, '/library');
    expect(await screen.findByText('Estudio eliminado')).toBeInTheDocument();
  });
});
