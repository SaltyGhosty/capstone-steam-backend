import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { getToken, setToken } from '@/api/client';
import { RequireAuth } from '@/components/ui';
import { mockFetch, page } from '@/test/http';
import { demoUser, renderRoutes } from '@/test/render';
import Settings from './Settings';

// Distinto del 'es' de demoUser para comprobar que el selector parte del idioma guardado, no del valor por defecto
const user = { ...demoUser, locale: 'en' };

describe('ajustes', () => {
  beforeEach(() => setToken('jwt-demo'));

  it('muestra el idioma de los correos del usuario y lo envía al guardar', async () => {
    const fetchMock = mockFetch({
      'GET /api/users/me': { body: user },
      'GET /api/cart': { body: { items: [], itemCount: 0, total: 0 } },
      'GET /api/wishlist': { body: page([]) },
      'PATCH /api/users/me': (init) => ({ body: { ...user, ...JSON.parse(String(init.body)) } }),
    });
    // Como en App.tsx: RequireAuth espera a cargar el usuario antes de montar el formulario
    renderRoutes({ '/settings': <RequireAuth><Settings /></RequireAuth> }, '/settings');

    const select = await screen.findByLabelText(/Idioma de los correos/);
    expect(select).toHaveValue('en');
    await userEvent.selectOptions(select, 'Italiano');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    const isPatch = ([, init]: unknown[]) => (init as RequestInit | undefined)?.method === 'PATCH';
    await waitFor(() => expect(fetchMock.mock.calls.some(isPatch)).toBe(true));
    const patch = fetchMock.mock.calls.find(isPatch)!;
    expect(JSON.parse(String(patch[1]!.body))).toEqual({ displayName: 'Demo Player', locale: 'it', profilePublic: false });
    await waitFor(() => expect(screen.getByRole('button', { name: 'Guardar cambios' })).toBeEnabled());
    expect(screen.getByLabelText(/Idioma de los correos/)).toHaveValue('it');
  });

  it('la casilla de perfil público envía profilePublic: true', async () => {
    const fetchMock = mockFetch({
      'GET /api/users/me': { body: user },
      'GET /api/cart': { body: { items: [], itemCount: 0, total: 0 } },
      'GET /api/wishlist': { body: page([]) },
      'PATCH /api/users/me': (init) => ({ body: { ...user, ...JSON.parse(String(init.body)) } }),
    });
    renderRoutes({ '/settings': <RequireAuth><Settings /></RequireAuth> }, '/settings');

    const box = await screen.findByRole('checkbox', { name: /Perfil público/ });
    expect(box).not.toBeChecked();
    await userEvent.click(box);
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    const isPatch = ([, init]: unknown[]) => (init as RequestInit | undefined)?.method === 'PATCH';
    await waitFor(() => expect(fetchMock.mock.calls.some(isPatch)).toBe(true));
    const patch = fetchMock.mock.calls.find(isPatch)!;
    expect(JSON.parse(String(patch[1]!.body))).toMatchObject({ profilePublic: true });
  });

  it('pide la contraseña y cierra la sesión con la fecha', async () => {
    const fetchMock = mockFetch({
      'GET /api/users/me': { body: user },
      'GET /api/cart': { body: { items: [], itemCount: 0, total: 0 } },
      'GET /api/wishlist': { body: page([]) },
      'POST /api/users/me/deletion': { body: { deletionScheduledAt: '2026-11-05T04:15:00Z' } },
      'POST /api/auth/logout': { status: 204 },
    });
    renderRoutes({ '/settings': <RequireAuth><Settings /></RequireAuth> }, '/settings');

    await userEvent.click(await screen.findByRole('button', { name: 'Eliminar mi cuenta' }));
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).queryByText(/tus juegos siguen a la venta/i)).not.toBeInTheDocument();
    await userEvent.type(within(dialog).getByLabelText('Tu contraseña para confirmar'), 'password123');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Eliminar mi cuenta' }));

    expect(await screen.findByText(/Tu cuenta se eliminará el .*2026/)).toBeInTheDocument();
    const call = fetchMock.mock.calls.find(([url]) => String(url) === '/api/users/me/deletion')!;
    expect(JSON.parse(String(call[1]!.body))).toEqual({ password: 'password123' });
    await userEvent.click(screen.getByRole('button', { name: 'Entendido' }));
    expect(getToken()).toBeNull();
  });

  it('muestra el error de último admin', async () => {
    mockFetch({
      'GET /api/users/me': { body: user },
      'GET /api/cart': { body: { items: [], itemCount: 0, total: 0 } },
      'GET /api/wishlist': { body: page([]) },
      'POST /api/users/me/deletion': { status: 409, body: { detail: 'Eres el último administrador', code: 'user.lastAdmin' } },
    });
    renderRoutes({ '/settings': <RequireAuth><Settings /></RequireAuth> }, '/settings');

    await userEvent.click(await screen.findByRole('button', { name: 'Eliminar mi cuenta' }));
    const dialog = screen.getByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Tu contraseña para confirmar'), 'password123');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Eliminar mi cuenta' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Eres el último administrador');
  });

  it('el diálogo atrapa el foco, Escape lo cierra y el foco vuelve al botón que lo abrió', async () => {
    mockFetch({
      'GET /api/users/me': { body: user },
      'GET /api/cart': { body: { items: [], itemCount: 0, total: 0 } },
      'GET /api/wishlist': { body: page([]) },
    });
    renderRoutes({ '/settings': <RequireAuth><Settings /></RequireAuth> }, '/settings');

    const opener = await screen.findByRole('button', { name: 'Eliminar mi cuenta' });
    await userEvent.click(opener);
    const dialog = screen.getByRole('dialog');
    const password = within(dialog).getByLabelText('Tu contraseña para confirmar');
    expect(password).toHaveFocus();
    // Tab da la vuelta dentro del diálogo, en los dos sentidos
    await userEvent.tab(); // Cancelar
    await userEvent.tab(); // Eliminar mi cuenta
    await userEvent.tab();
    expect(password).toHaveFocus();
    await userEvent.tab({ shift: true });
    expect(within(dialog).getByRole('button', { name: 'Eliminar mi cuenta' })).toHaveFocus();

    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(opener).toHaveFocus();
  });

  it('con la baja ya pedida, Escape no cierra: solo «Entendido»', async () => {
    mockFetch({
      'GET /api/users/me': { body: user },
      'GET /api/cart': { body: { items: [], itemCount: 0, total: 0 } },
      'GET /api/wishlist': { body: page([]) },
      'POST /api/users/me/deletion': { body: { deletionScheduledAt: '2026-11-05T04:15:00Z' } },
      'POST /api/auth/logout': { status: 204 },
    });
    renderRoutes({ '/settings': <RequireAuth><Settings /></RequireAuth> }, '/settings');

    await userEvent.click(await screen.findByRole('button', { name: 'Eliminar mi cuenta' }));
    const dialog = screen.getByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Tu contraseña para confirmar'), 'password123');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Eliminar mi cuenta' }));
    const understood = await within(dialog).findByRole('button', { name: 'Entendido' });
    expect(understood).toHaveFocus();

    await userEvent.keyboard('{Escape}');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(getToken()).toBe('jwt-demo');
  });

  it('un estudio ve el aviso sobre sus juegos', async () => {
    mockFetch({
      'GET /api/users/me': { body: { ...user, roles: ['ROLE_USER', 'ROLE_DEVELOPER'] } },
      'GET /api/cart': { body: { items: [], itemCount: 0, total: 0 } },
      'GET /api/wishlist': { body: page([]) },
    });
    renderRoutes({ '/settings': <RequireAuth><Settings /></RequireAuth> }, '/settings');

    await userEvent.click(await screen.findByRole('button', { name: 'Eliminar mi cuenta' }));
    expect(within(screen.getByRole('dialog')).getByText(/Tus juegos siguen a la venta/)).toBeInTheDocument();
  });
});
