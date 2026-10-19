import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { setToken } from '@/api/client';
import Layout from '@/components/Layout';
import { mockFetch, page } from '@/test/http';
import { demoUser, renderRoutes } from '@/test/render';

function routesFor(roles: string[]) {
  mockFetch({
    'GET /api/users/me': { body: { ...demoUser, roles } },
    'GET /api/cart': { body: { items: [], itemCount: 0, total: 0 } },
    'GET /api/wishlist': { body: page([]) },
  });
}

describe('menú del layout según el rol', () => {
  beforeEach(() => setToken('jwt-demo'));

  it('un jugador ve Amigos y Mi perfil', async () => {
    routesFor(['ROLE_USER']);
    renderRoutes({ '/': <Layout /> }, '/');
    expect(await screen.findByRole('link', { name: /Amigos/ })).toBeInTheDocument();
    await userEvent.click(await screen.findByRole('button', { name: /Demo Player/ }));
    expect(screen.getByRole('menuitem', { name: /Mi perfil/ })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: /Amigos/ })).toBeInTheDocument();
  });

  it.each([['ROLE_ADMIN'], ['ROLE_DEVELOPER']])('%s no ve Amigos ni Mi perfil', async (role) => {
    routesFor(['ROLE_USER', role]);
    renderRoutes({ '/': <Layout /> }, '/');
    await userEvent.click(await screen.findByRole('button', { name: /Demo Player/ }));
    expect(screen.queryByRole('link', { name: /Amigos/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: /Amigos/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: /Mi perfil/ })).not.toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: /Ajustes/ })).toBeInTheDocument();
  });
});
