import { describe, expect, it, vi } from 'vitest';
import { mockFetch } from '@/test/http';
import { api, ApiError, getRefreshToken, getToken, setRefreshToken, setToken, setUnauthorizedHandler } from './client';

describe('cliente de la API', () => {
  it('envía el token y serializa los filtros del catálogo en la query', async () => {
    setToken('abc');
    const fetchMock = mockFetch({ 'GET /api/games': { body: { ok: true } } });

    await api('/api/games', { query: { q: 'neon', genres: ['rpg', 'indie'], onSale: true, maxPrice: undefined, page: 0 } });

    const [url, init] = fetchMock.mock.calls[0]!;
    expect(String(url)).toBe('/api/games?q=neon&genres=rpg%2Cindie&onSale=true&page=0');
    expect((init!.headers as Record<string, string>).Authorization).toBe('Bearer abc');
  });

  it('manda un FormData tal cual, sin Content-Type (el navegador pone el boundary)', async () => {
    setToken('abc');
    const fetchMock = mockFetch({ 'POST /api/developer/games/1/cover': { body: { id: 1 } } });
    const form = new FormData();
    form.append('file', new File(['x'], 'portada.png', { type: 'image/png' }));

    await api('/api/developer/games/1/cover', { method: 'POST', body: form });

    const [, init] = fetchMock.mock.calls[0]!;
    expect(init!.body).toBe(form);
    const headers = init!.headers as Record<string, string>;
    expect(headers['Content-Type']).toBeUndefined();
    expect(headers.Authorization).toBe('Bearer abc');
  });

  it('convierte un ProblemDetail con errores de validación en ApiError', async () => {
    mockFetch({
      'POST /api/auth/register': {
        status: 400,
        body: { title: 'Bad Request', detail: 'Datos no válidos', errors: { email: 'debe ser un email válido' } },
      },
    });

    const error = await api<never>('/api/auth/register', { method: 'POST', body: {} }).catch((e: unknown) => e as ApiError);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(400);
    expect(error.message).toBe('Datos no válidos');
    expect(error.fieldErrors).toEqual({ email: 'debe ser un email válido' });
  });

  it('acepta los estados indicados en acceptStatus (402 del pago rechazado)', async () => {
    mockFetch({ 'POST /api/orders/1/pay': { status: 402, body: { id: 1, status: 'FAILED' } } });

    const order = await api<{ status: string }>('/api/orders/1/pay', { method: 'POST', body: {}, acceptStatus: [402] });

    expect(order.status).toBe('FAILED');
  });

  it('avisa del 401 solo cuando había sesión', async () => {
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    mockFetch({ 'GET /api/cart': { status: 401, body: { detail: 'Se requiere un token válido' } } });

    await expect(api('/api/cart')).rejects.toThrow('Se requiere un token válido');
    expect(onUnauthorized).not.toHaveBeenCalled();

    setToken('caducado');
    await expect(api('/api/cart')).rejects.toBeInstanceOf(ApiError);
    expect(onUnauthorized).toHaveBeenCalledOnce();
    setUnauthorizedHandler(null);
  });

  describe('renovación de la sesión', () => {
    const renewed = { accessToken: 'nuevo', tokenType: 'Bearer', expiresIn: 900, refreshToken: 'refresh-2' };

    it('ante un 401 con refresh guardado renueva los tokens y repite la petición', async () => {
      setToken('caducado');
      setRefreshToken('refresh-1');
      const sent: string[] = [];
      const fetchMock = mockFetch({
        'GET /api/cart': (init) => {
          const auth = (init.headers as Record<string, string>).Authorization;
          sent.push(auth);
          return auth === 'Bearer nuevo' ? { body: { items: [] } } : { status: 401, body: { detail: 'caducado' } };
        },
        'POST /api/auth/refresh': (init) => {
          expect(JSON.parse(String(init.body))).toEqual({ refreshToken: 'refresh-1' });
          return { body: renewed };
        },
      });

      const cart = await api<{ items: unknown[] }>('/api/cart');

      expect(cart).toEqual({ items: [] });
      expect(sent).toEqual(['Bearer caducado', 'Bearer nuevo']);
      expect(getToken()).toBe('nuevo');
      expect(getRefreshToken()).toBe('refresh-2');
      expect(fetchMock.mock.calls.filter(([url]) => String(url) === '/api/auth/refresh')).toHaveLength(1);
    });

    it('dos peticiones simultáneas con 401 hacen un solo refresh y las dos se repiten', async () => {
      setToken('caducado');
      setRefreshToken('refresh-1');
      const fetchMock = mockFetch({
        'GET /api/cart': (init) =>
          (init.headers as Record<string, string>).Authorization === 'Bearer nuevo'
            ? { body: { ok: 'cart' } }
            : { status: 401, body: {} },
        'GET /api/wishlist': (init) =>
          (init.headers as Record<string, string>).Authorization === 'Bearer nuevo'
            ? { body: { ok: 'wishlist' } }
            : { status: 401, body: {} },
        'POST /api/auth/refresh': { body: renewed },
      });

      const [cart, wishlist] = await Promise.all([api('/api/cart'), api('/api/wishlist')]);

      expect(cart).toEqual({ ok: 'cart' });
      expect(wishlist).toEqual({ ok: 'wishlist' });
      expect(fetchMock.mock.calls.filter(([url]) => String(url) === '/api/auth/refresh')).toHaveLength(1);
    });

    it('si el refresh falla borra los dos tokens y avisa una sola vez', async () => {
      const onUnauthorized = vi.fn();
      setUnauthorizedHandler(onUnauthorized);
      setToken('caducado');
      setRefreshToken('refresh-malo');
      const fetchMock = mockFetch({
        'GET /api/cart': { status: 401, body: { detail: 'caducado' } },
        'GET /api/wishlist': { status: 401, body: { detail: 'caducado' } },
        'POST /api/auth/refresh': { status: 401, body: { detail: 'La sesión ha caducado' } },
      });

      const results = await Promise.allSettled([api('/api/cart'), api('/api/wishlist')]);

      expect(results.map((r) => r.status)).toEqual(['rejected', 'rejected']);
      expect(getToken()).toBeNull();
      expect(getRefreshToken()).toBeNull();
      expect(onUnauthorized).toHaveBeenCalledOnce();
      // sin bucle: un solo refresh, y las peticiones originales no se repiten
      expect(fetchMock.mock.calls.filter(([url]) => String(url) === '/api/auth/refresh')).toHaveLength(1);
      expect(fetchMock.mock.calls).toHaveLength(3);
      setUnauthorizedHandler(null);
    });

    it.each([502, 429])('un refresh que da %i no borra la sesión ni avisa', async (status) => {
      const onUnauthorized = vi.fn();
      setUnauthorizedHandler(onUnauthorized);
      setToken('caducado');
      setRefreshToken('refresh-1');
      mockFetch({
        'GET /api/cart': { status: 401, body: { detail: 'caducado' } },
        'POST /api/auth/refresh': { status, body: { detail: 'fallo temporal' } },
      });

      const error = await api<never>('/api/cart').catch((e: unknown) => e as ApiError);

      expect(error).toBeInstanceOf(ApiError);
      expect(error.status).toBe(status);
      expect(getToken()).toBe('caducado');
      expect(getRefreshToken()).toBe('refresh-1');
      expect(onUnauthorized).not.toHaveBeenCalled();
      setUnauthorizedHandler(null);
    });

    it('un 400 del refresh sí cierra la sesión', async () => {
      const onUnauthorized = vi.fn();
      setUnauthorizedHandler(onUnauthorized);
      setToken('caducado');
      setRefreshToken('refresh-1');
      mockFetch({
        'GET /api/cart': { status: 401, body: {} },
        'POST /api/auth/refresh': { status: 400, body: { detail: 'no válido' } },
      });

      await expect(api('/api/cart')).rejects.toBeInstanceOf(ApiError);

      expect(getToken()).toBeNull();
      expect(getRefreshToken()).toBeNull();
      expect(onUnauthorized).toHaveBeenCalledOnce();
      setUnauthorizedHandler(null);
    });

    it('un 401 tardío con la sesión ya borrada no repite la petición sin token', async () => {
      setUnauthorizedHandler(vi.fn());
      setToken('caducado');
      setRefreshToken('refresh-1');
      // el token de acceso desapareció mientras la petición volaba (otro refresh cerró la sesión)
      const fetchMock = mockFetch({
        'GET /api/cart': () => {
          setToken(null);
          return { status: 401, body: {} };
        },
        'POST /api/auth/refresh': { status: 401, body: { detail: 'La sesión ha caducado' } },
      });

      await expect(api('/api/cart')).rejects.toBeInstanceOf(ApiError);

      expect(fetchMock.mock.calls.filter(([url]) => String(url) === '/api/cart')).toHaveLength(1);
      setUnauthorizedHandler(null);
    });

    it('un 401 de /api/auth/login no intenta refrescar', async () => {
      setToken('viejo');
      setRefreshToken('refresh-1');
      const fetchMock = mockFetch({
        'POST /api/auth/login': { status: 401, body: { detail: 'Usuario o contraseña incorrectos' } },
      });

      await expect(api('/api/auth/login', { method: 'POST', body: {} })).rejects.toThrow('Usuario o contraseña incorrectos');

      expect(fetchMock).toHaveBeenCalledOnce();
      expect(getRefreshToken()).toBe('refresh-1');
    });
  });

  it('da un mensaje claro si el backend no responde', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    const error = await api<never>('/api/games').catch((e: unknown) => e as ApiError);

    expect(error.status).toBe(0);
    expect(error.message).toMatch(/backend/);
  });

  it('ApiError.code toma el code del ProblemDetail para distinguir errores del mismo status', async () => {
    mockFetch({ 'POST /api/auth/login': { status: 403, body: { detail: 'Sin verificar', code: 'auth.emailNotVerified' } } });

    const error = await api<never>('/api/auth/login', { method: 'POST', body: {} }).catch((e: unknown) => e as ApiError);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(403);
    expect(error.code).toBe('auth.emailNotVerified');
  });

  it('sin code en la respuesta, ApiError.code queda undefined', async () => {
    mockFetch({ 'GET /api/cart': { status: 404, body: { detail: 'No existe' } } });

    const error = await api<never>('/api/cart').catch((e: unknown) => e as ApiError);

    expect(error.code).toBeUndefined();
  });
});
