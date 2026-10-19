import { screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { getRefreshToken, getToken, setRefreshToken, setToken } from '@/api/client';
import { mockFetch } from '@/test/http';
import { renderRoutes } from '@/test/render';

describe('arranque de la sesión', () => {
  afterEach(() => {
    setToken(null);
    setRefreshToken(null);
  });

  it('un fallo de red al validar la sesión no la borra', async () => {
    setToken('vivo');
    setRefreshToken('refresh-1');
    const fetchMock = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));
    vi.stubGlobal('fetch', fetchMock);

    renderRoutes({ '/': <p>inicio</p> }, '/');

    await screen.findByText('inicio');
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(getToken()).toBe('vivo');
    expect(getRefreshToken()).toBe('refresh-1');
  });

  it('un 401 al validar la sesión sí la borra', async () => {
    setToken('muerto');
    mockFetch({ 'GET /api/users/me': { status: 401, body: { detail: 'no' } } });

    renderRoutes({ '/': <p>inicio</p> }, '/');

    await waitFor(() => expect(getToken()).toBeNull());
  });
});
