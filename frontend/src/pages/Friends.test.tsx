import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setToken } from '@/api/client';
import { RequireAuth } from '@/components/ui';
import { mockFetch, page } from '@/test/http';
import { demoUser, renderRoutes } from '@/test/render';
import Friends from './Friends';

const leo = { username: 'leo', displayName: 'Leo', avatarUrl: null };
const ana = { username: 'ana', displayName: 'Ana', avatarUrl: null };

const base = {
  'GET /api/users/me': { body: demoUser },
  'GET /api/cart': { body: { items: [], itemCount: 0, total: 0 } },
  'GET /api/wishlist': { body: page([]) },
};

describe('amigos', () => {
  beforeEach(() => setToken('jwt-demo'));

  it('busca con 2 caracteres y envía la solicitud con Añadir', async () => {
    let sent = false;
    const fetchMock = mockFetch({
      ...base,
      'GET /api/friends': { body: [] },
      'GET /api/friends/requests': { body: { incoming: [], outgoing: [] } },
      'GET /api/users/search': () => ({ body: [{ user: leo, relation: sent ? 'REQUEST_SENT' : 'NONE', requestId: sent ? 5 : null }] }),
      'POST /api/friends/requests': () => (sent = true, { status: 201, body: { id: 5, user: leo, status: 'PENDING', createdAt: '2026-10-19T10:00:00Z' } }),
    });
    renderRoutes({ '/friends': <RequireAuth><Friends /></RequireAuth> }, '/friends');

    await userEvent.type(await screen.findByRole('searchbox'), 'le');
    expect(await screen.findByText('Leo')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/api/users/search?q=le'))).toBe(true);

    await userEvent.click(screen.getByRole('button', { name: 'Añadir' }));
    await waitFor(() => {
      const post = fetchMock.mock.calls.find(([url, init]) =>
        String(url).endsWith('/api/friends/requests') && (init as RequestInit | undefined)?.method === 'POST');
      expect(post).toBeTruthy();
      expect(JSON.parse(String((post![1] as RequestInit).body))).toEqual({ username: 'leo' });
    });
    expect(await screen.findByRole('button', { name: 'Solicitud enviada' })).toBeDisabled();
  });

  it('acepta una solicitud recibida', async () => {
    const fetchMock = mockFetch({
      ...base,
      'GET /api/friends': { body: [] },
      'GET /api/friends/requests': {
        body: { incoming: [{ id: 9, user: ana, status: 'PENDING', createdAt: '2026-10-19T10:00:00Z' }], outgoing: [] },
      },
      'POST /api/friends/requests/9/accept': { body: { id: 9, user: ana, status: 'ACCEPTED', createdAt: '2026-10-19T10:00:00Z' } },
    });
    renderRoutes({ '/friends': <RequireAuth><Friends /></RequireAuth> }, '/friends');

    await userEvent.click(await screen.findByRole('button', { name: 'Aceptar' }));
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/requests/9/accept'))).toBe(true));
  });

  it('elimina a un amigo tras confirmar en la propia página', async () => {
    const fetchMock = mockFetch({
      ...base,
      'GET /api/friends': { body: [{ user: leo, since: '2026-10-01T10:00:00Z' }] },
      'GET /api/friends/requests': { body: { incoming: [], outgoing: [] } },
      'DELETE /api/friends/leo': { status: 204 },
    });
    renderRoutes({ '/friends': <RequireAuth><Friends /></RequireAuth> }, '/friends');

    await userEvent.click(await screen.findByRole('button', { name: 'Eliminar' }));
    expect(fetchMock.mock.calls.some(([, init]) => (init as RequestInit | undefined)?.method === 'DELETE')).toBe(false);
    await userEvent.click(screen.getByRole('button', { name: 'Sí, eliminar' }));
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/api/friends/leo'))).toBe(true));
  });

  it('descarta una respuesta de búsqueda que llega tarde', async () => {
    const lena = { username: 'lena', displayName: 'Lena', avatarUrl: null };
    const pending: Record<string, (hits: unknown[]) => void> = {};
    const json = (body: unknown) => new Response(JSON.stringify(body), { status: 200 });
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = new URL(String(input), 'http://localhost');
      if (url.pathname === '/api/users/search') {
        const q = url.searchParams.get('q') ?? '';
        return new Promise<Response>((resolve) => { pending[q] = (hits) => resolve(json(hits)); });
      }
      const bodies: Record<string, unknown> = {
        '/api/users/me': demoUser,
        '/api/cart': { items: [], itemCount: 0, total: 0 },
        '/api/wishlist': page([]),
        '/api/friends': [],
        '/api/friends/requests': { incoming: [], outgoing: [] },
      };
      return json(bodies[url.pathname]);
    });
    vi.stubGlobal('fetch', fetchMock);
    renderRoutes({ '/friends': <RequireAuth><Friends /></RequireAuth> }, '/friends');

    const box = await screen.findByRole('searchbox');
    await userEvent.type(box, 'le');
    await waitFor(() => expect(pending.le).toBeDefined());
    await userEvent.type(box, 'o');
    await waitFor(() => expect(pending.leo).toBeDefined());

    pending.leo([{ user: leo, relation: 'NONE', requestId: null }]);
    expect(await screen.findByText('Leo')).toBeInTheDocument();
    pending.le([{ user: lena, relation: 'NONE', requestId: null }]); // llega tarde
    await new Promise((r) => setTimeout(r, 50));
    expect(screen.queryByText('Lena')).not.toBeInTheDocument();
    expect(screen.getByText('Leo')).toBeInTheDocument();
  });

  it('no rellena resultados si el término bajó de 2 caracteres', async () => {
    const pending: Record<string, (hits: unknown[]) => void> = {};
    const json = (body: unknown) => new Response(JSON.stringify(body), { status: 200 });
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = new URL(String(input), 'http://localhost');
      if (url.pathname === '/api/users/search') {
        const q = url.searchParams.get('q') ?? '';
        return new Promise<Response>((resolve) => { pending[q] = (hits) => resolve(json(hits)); });
      }
      const bodies: Record<string, unknown> = {
        '/api/users/me': demoUser,
        '/api/cart': { items: [], itemCount: 0, total: 0 },
        '/api/wishlist': page([]),
        '/api/friends': [],
        '/api/friends/requests': { incoming: [], outgoing: [] },
      };
      return json(bodies[url.pathname]);
    }));
    renderRoutes({ '/friends': <RequireAuth><Friends /></RequireAuth> }, '/friends');

    const box = await screen.findByRole('searchbox');
    await userEvent.type(box, 'le');
    await waitFor(() => expect(pending.le).toBeDefined());
    await userEvent.clear(box);
    await new Promise((r) => setTimeout(r, 400));
    pending.le([{ user: leo, relation: 'NONE', requestId: null }]);
    await new Promise((r) => setTimeout(r, 50));
    expect(screen.queryByText('Leo')).not.toBeInTheDocument();
  });
});
