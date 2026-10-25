import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { setToken } from '@/api/client';
import { useStore } from '@/context/StoreContext';
import { mockFetch, page } from '@/test/http';
import { demoUser, renderRoutes } from '@/test/render';
import Support from './Support';

/** Pinta el aviso (toast) actual: el Layout no está en estos tests. */
function ToastProbe() {
  const { toast } = useStore();
  return <output data-testid="toast">{toast?.message}</output>;
}

const sessionRoutes = {
  'GET /api/users/me': { body: demoUser },
  'GET /api/cart': { body: { items: [], itemCount: 0, total: 0 } },
  'GET /api/wishlist': { body: page([]) },
};

async function fillAndSend(subject: string, message: string) {
  await userEvent.type(screen.getByLabelText('Asunto'), subject);
  await userEvent.type(screen.getByLabelText('Mensaje'), message);
  await userEvent.click(screen.getByRole('button', { name: 'Enviar mensaje' }));
}

describe('asistencia al cliente', () => {
  beforeEach(() => localStorage.clear());

  it('sin sesión pide el email, envía el formulario y confirma la copia', async () => {
    const fetchMock = mockFetch({ 'POST /api/support': { status: 202 } });
    renderRoutes({ '/support': <Support /> }, '/support');

    await userEvent.type(screen.getByLabelText('Tu email'), 'ana@test.com');
    await fillAndSend('No carga mi biblioteca', 'Desde ayer no me aparecen mis juegos.');

    expect(await screen.findByText(/Mensaje enviado: te hemos enviado una copia a ana@test.com/)).toBeInTheDocument();
    expect(JSON.parse(String(fetchMock.mock.calls[0]![1]!.body))).toEqual({
      email: 'ana@test.com', subject: 'No carga mi biblioteca', message: 'Desde ayer no me aparecen mis juegos.',
    });
  });

  it('con sesión no muestra el campo de email ni lo envía', async () => {
    setToken('jwt-demo');
    const fetchMock = mockFetch({ ...sessionRoutes, 'POST /api/support': { status: 202 } });
    renderRoutes({ '/support': <Support /> }, '/support');

    await screen.findByLabelText('Asunto');
    expect(screen.queryByLabelText('Tu email')).not.toBeInTheDocument();
    await fillAndSend('Una duda', 'Quería preguntar por mi pedido.');

    expect(await screen.findByText(/te hemos enviado una copia a demo@test.com/)).toBeInTheDocument();
    const call = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/api/support'))!;
    expect(JSON.parse(String(call[1]!.body))).toEqual({ subject: 'Una duda', message: 'Quería preguntar por mi pedido.' });
  });

  it('cuenta los caracteres del mensaje', async () => {
    mockFetch({});
    renderRoutes({ '/support': <Support /> }, '/support');

    await userEvent.type(screen.getByLabelText('Mensaje'), 'hola');

    expect(screen.getByText('4 / 3000')).toBeInTheDocument();
  });

  it('un 429 enseña el texto del backend y deja el formulario', async () => {
    mockFetch({
      'POST /api/support': { status: 429, body: { status: 429, detail: 'Has enviado demasiadas solicitudes de asistencia' } },
    });
    renderRoutes({ '/support': <><Support /><ToastProbe /></> }, '/support');

    await userEvent.type(screen.getByLabelText('Tu email'), 'ana@test.com');
    await fillAndSend('Otra vez', 'Este es mi sexto mensaje de la hora.');

    await waitFor(() => expect(screen.getByTestId('toast')).toHaveTextContent('Has enviado demasiadas solicitudes de asistencia'));
    expect(screen.getByRole('button', { name: 'Enviar mensaje' })).toBeEnabled();
  });
});
