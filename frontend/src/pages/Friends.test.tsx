import { screen, waitFor, within } from '@testing-library/react';
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

describe('chat con amigos', () => {
  beforeEach(() => setToken('jwt-demo'));

  const msg = (id: number, mine: boolean, body: string) => ({ id, mine, body, createdAt: '2026-10-22T10:00:00Z' });
  const conv = (friend = true) => ({
    username: 'leo', displayName: 'Leo', avatarUrl: null, friend, unread: 2, lastMessage: 'hola', lastMessageAt: '2026-10-22T10:00:00Z',
  });
  const chatBase = (messages: unknown[], friend = true) => ({
    ...base,
    'GET /api/friends': { body: friend ? [{ user: leo, since: '2026-10-01T10:00:00Z' }] : [] },
    'GET /api/friends/requests': { body: { incoming: [], outgoing: [] } },
    'GET /api/friends/conversations': { body: [conv(friend)] },
    'GET /api/friends/activity': { body: { pendingRequests: 0, unreadMessages: 0, latestMessage: null } },
    'GET /api/friends/leo/messages': { body: messages },
    'POST /api/friends/leo/messages/read': { status: 204 },
  });
  const open = () => renderRoutes({ '/friends': <RequireAuth><Friends /></RequireAuth> }, '/friends?chat=leo');
  const calls = (fetchMock: ReturnType<typeof mockFetch>, method: string, suffix: string) =>
    fetchMock.mock.calls.filter(([url, init]) =>
      String(url).includes(suffix) && ((init as RequestInit | undefined)?.method ?? 'GET') === method);

  it('abre la conversación del enlace ?chat= y marca como leído', async () => {
    const fetchMock = mockFetch(chatBase([msg(2, false, 'segundo'), msg(1, true, 'primero')]));
    open();
    const log = await screen.findByRole('log');
    expect(await within(log).findByText('primero')).toBeInTheDocument();
    // del más antiguo al más nuevo
    expect(within(log).getAllByText(/primero|segundo/).map((e) => e.textContent)).toEqual(['primero', 'segundo']);
    await waitFor(() => expect(calls(fetchMock, 'POST', '/messages/read')).toHaveLength(1));
    await waitFor(() => expect(calls(fetchMock, 'GET', '/api/friends/activity').length).toBeGreaterThan(0));
  });

  it('envía con Enter y añade el mensaje', async () => {
    const fetchMock = mockFetch({
      ...chatBase([]),
      'POST /api/friends/leo/messages': () => ({ status: 201, body: msg(5, true, 'buenas') }),
    });
    open();
    const box = await screen.findByLabelText('Escribe un mensaje');
    await userEvent.type(box, 'buenas{Enter}');
    expect(await within(screen.getByRole('log')).findByText('buenas')).toBeInTheDocument();
    const post = calls(fetchMock, 'POST', '/api/friends/leo/messages').find(([url]) => String(url).endsWith('/messages'))!;
    expect(JSON.parse(String((post[1] as RequestInit).body))).toEqual({ body: 'buenas' });
    expect(box).toHaveValue('');
  });

  it('Mayús+Enter no envía y el contador cuenta', async () => {
    const fetchMock = mockFetch(chatBase([]));
    open();
    const box = await screen.findByLabelText('Escribe un mensaje');
    await userEvent.type(box, 'a{Shift>}{Enter}{/Shift}b');
    expect(box).toHaveValue('a\nb');
    expect(screen.getByText('3/1000')).toBeInTheDocument();
    expect(calls(fetchMock, 'POST', '/api/friends/leo/messages').filter(([url]) => String(url).endsWith('/messages'))).toHaveLength(0);
  });

  it('no deja enviar un texto vacío', async () => {
    mockFetch(chatBase([]));
    open();
    const box = await screen.findByLabelText('Escribe un mensaje');
    await userEvent.type(box, '   ');
    expect(screen.getByRole('button', { name: 'Enviar' })).toBeDisabled();
  });

  it('un ex-amigo se lee pero no se puede escribir', async () => {
    mockFetch(chatBase([msg(1, false, 'adiós')], false));
    open();
    expect(await screen.findByText('adiós')).toBeInTheDocument();
    expect(screen.getByText('Ya no sois amigos: solo puedes leer esta conversación')).toBeInTheDocument();
    expect(screen.getByLabelText('Escribe un mensaje')).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Enviar' })).toBeDisabled();
  });

  it('muestra <b>hola</b> como texto', async () => {
    mockFetch(chatBase([msg(1, false, '<b>hola</b>')]));
    open();
    const text = await screen.findByText('<b>hola</b>');
    expect(text.querySelector('b')).toBeNull();
  });

  it('cargar más antiguos usa before con el id más antiguo', async () => {
    const latest = Array.from({ length: 30 }, (_, i) => msg(100 - i, false, `m${100 - i}`));
    const fetchMock = mockFetch({
      ...chatBase([]),
      'GET /api/friends/leo/messages': { body: latest },
    });
    // la página con before devuelve otra distinta
    const original = fetchMock.getMockImplementation()!;
    fetchMock.mockImplementation(async (input, init) => {
      const url = new URL(String(input), 'http://localhost');
      if (url.pathname === '/api/friends/leo/messages' && url.searchParams.has('before')) {
        return new Response(JSON.stringify([msg(70, false, 'antiguo')]), { status: 200 });
      }
      return original(input, init);
    });
    open();
    await userEvent.click(await screen.findByRole('button', { name: 'Cargar anteriores' }));
    expect(await screen.findByText('antiguo')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('before=71'))).toBe(true);
    expect(screen.queryByRole('button', { name: 'Cargar anteriores' })).not.toBeInTheDocument();
  });

  it('abre una conversación desde la lista', async () => {
    mockFetch(chatBase([msg(1, false, 'hola leo')]));
    renderRoutes({ '/friends': <RequireAuth><Friends /></RequireAuth> }, '/friends');
    await userEvent.click(await screen.findByRole('button', { name: /Leo.*hola/ }));
    expect(await screen.findByText('hola leo')).toBeInTheDocument();
  });
});
