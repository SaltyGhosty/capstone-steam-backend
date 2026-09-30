// Doble de fetch para los tests: cada ruta ("GET /api/games") devuelve un estado y un cuerpo JSON.
import { vi } from 'vitest';

type Route = { status?: number; body?: unknown };
export type Routes = Record<string, Route | ((init: RequestInit) => Route)>;

export function mockFetch(routes: Routes) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = new URL(String(input), 'http://localhost');
    const key = `${init.method ?? 'GET'} ${url.pathname}`;
    const route = routes[key];
    if (!route) return new Response(JSON.stringify({ detail: `Sin mock para ${key}` }), { status: 500 });
    const { status = 200, body } = typeof route === 'function' ? route(init) : route;
    return new Response(body === undefined ? '' : JSON.stringify(body), { status });
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

export const page = <T,>(content: T[]) => ({
  content, page: 0, size: 20, totalElements: content.length, totalPages: 1, last: true,
});
