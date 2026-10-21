import { screen, within } from '@testing-library/react';
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
    const side = await screen.findByRole('navigation', { name: 'Navegación principal' });
    expect(within(side).getByRole('link', { name: /Amigos/ })).toBeInTheDocument();
    await userEvent.click(await screen.findByRole('button', { name: /Demo Player/ }));
    expect(screen.getByRole('menuitem', { name: /Mi perfil/ })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: /Amigos/ })).toBeInTheDocument();
  });

  it('el menú móvil tiene Demos y, para un jugador, Amigos', async () => {
    routesFor(['ROLE_USER']);
    renderRoutes({ '/': <Layout /> }, '/');
    await screen.findByRole('button', { name: /Demo Player/ });
    const mobile = screen.getByRole('navigation', { name: 'Navegación móvil' });
    expect(within(mobile).getByRole('link', { name: /Demos/ })).toHaveAttribute('href', '/demos');
    expect(within(mobile).getByRole('link', { name: /Amigos/ })).toHaveAttribute('href', '/friends');
    expect(within(mobile).queryByRole('link', { name: /Mis juegos|Moderación/ })).not.toBeInTheDocument();
  });

  it.each([['ROLE_ADMIN', /Moderación/], ['ROLE_DEVELOPER', /Mis juegos/]])('%s tiene su panel en el menú móvil', async (role, panel) => {
    routesFor(['ROLE_USER', role]);
    renderRoutes({ '/': <Layout /> }, '/');
    await screen.findByRole('button', { name: /Demo Player/ });
    const mobile = screen.getByRole('navigation', { name: 'Navegación móvil' });
    expect(within(mobile).getByRole('link', { name: panel })).toBeInTheDocument();
    expect(within(mobile).queryByRole('link', { name: /Amigos/ })).not.toBeInTheDocument();
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
