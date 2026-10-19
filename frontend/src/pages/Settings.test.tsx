import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { setToken } from '@/api/client';
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
});
