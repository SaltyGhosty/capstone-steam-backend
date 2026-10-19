import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { setToken } from '@/api/client';
import { RequireAuth } from '@/components/ui';
import { mockFetch, page } from '@/test/http';
import { demoUser, renderRoutes } from '@/test/render';
import Profile from './Profile';

const leo = { username: 'leo', displayName: 'Leo', avatarUrl: null };
const base = {
  'GET /api/users/me': { body: demoUser },
  'GET /api/cart': { body: { items: [], itemCount: 0, total: 0 } },
  'GET /api/wishlist': { body: page([]) },
};
const profile = { user: leo, memberSince: '2026-09-01T10:00:00Z', profilePublic: false, relation: 'NONE', requestId: null };

describe('perfil de otro jugador', () => {
  beforeEach(() => setToken('jwt-demo'));

  it('privado: muestra el aviso y no la biblioteca', async () => {
    mockFetch({
      ...base,
      'GET /api/users/leo/profile': { body: { ...profile, visible: false, library: null, friends: null } },
    });
    renderRoutes({ '/users/:username': <RequireAuth><Profile /></RequireAuth> }, '/users/leo');

    expect(await screen.findByText(/Leo tiene el perfil privado/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Añadir' })).toBeInTheDocument();
  });

  it('visible: muestra los juegos con las horas jugadas', async () => {
    mockFetch({
      ...base,
      'GET /api/users/leo/profile': {
        body: {
          ...profile, relation: 'FRIENDS', visible: true,
          library: [{ gameId: 1, title: 'Celeste', slug: 'celeste', coverUrl: null, playtimeMinutes: 90 }],
          friends: [{ user: { username: 'ana', displayName: 'Ana', avatarUrl: null }, since: null }],
        },
      },
    });
    renderRoutes({ '/users/:username': <RequireAuth><Profile /></RequireAuth> }, '/users/leo');

    expect(await screen.findByText('Celeste')).toBeInTheDocument();
    expect(screen.getByText(/1,5 h/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Ana/ })).toHaveAttribute('href', '/users/ana');
  });
});
