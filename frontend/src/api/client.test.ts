import { describe, expect, it, vi } from 'vitest';
import { mockFetch } from '@/test/http';
import { api, ApiError, setToken, setUnauthorizedHandler } from './client';

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

  it('da un mensaje claro si el backend no responde', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    const error = await api<never>('/api/games').catch((e: unknown) => e as ApiError);

    expect(error.status).toBe(0);
    expect(error.message).toMatch(/backend/);
  });
});
