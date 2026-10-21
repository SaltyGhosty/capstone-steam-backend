// Panel de admin, pestaña Usuarios: eliminar una cuenta y cancelar la baja.
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { setToken } from '@/api/client';
import { mockFetch, page } from '@/test/http';
import { demoUser, renderRoutes } from '@/test/render';
import { useStore } from '@/context/StoreContext';
import Admin from './Admin';

// El aviso lo pinta el Layout; aquí se muestra el mismo toast para poder leerlo
function ToastProbe() {
  const { toast } = useStore();
  return toast ? <output data-testid="toast">{toast.message}</output> : null;
}

const me = { ...demoUser, id: 1, username: 'root', displayName: 'Root', email: 'root@test.com', roles: ['ROLE_ADMIN'] };
const ana = { ...demoUser, id: 5, username: 'ana', displayName: 'Ana', email: 'ana@test.com' };

const routes = { '/admin': <><Admin /><ToastProbe /></> };

function setup(extra = {}) {
  setToken('jwt-demo');
  return mockFetch({
    'GET /api/users/me': { body: me },
    'GET /api/admin/users': { body: page([me, ana]) },
    'GET /api/games': { body: page([]) },
    'GET /api/wishlist': { body: page([]) },
    'GET /api/cart': { body: { items: [], itemCount: 0, total: 0 } },
    ...extra,
  });
}

afterEach(() => vi.restoreAllMocks());

describe('admin: usuarios', () => {
  it('elimina y cancela la baja de un usuario', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const scheduled = { ...ana, deletionScheduledAt: '2026-11-05T04:15:00Z', deletionRequestedBy: 'ADMIN' };
    const fetchMock = setup({
      'DELETE /api/admin/users/5': { body: scheduled },
      'POST /api/admin/users/5/deletion/cancel': { body: ana },
    });
    renderRoutes(routes, '/admin?tab=users');

    const row = (await screen.findByText('@ana · ana@test.com')).closest('tr')!;
    await userEvent.click(within(row).getByRole('button', { name: 'Eliminar' }));
    expect(confirm).toHaveBeenCalled();
    expect(await within(row).findByText(/Se elimina el/)).toBeInTheDocument();
    expect(within(row).getByText(/lo pidió un admin/)).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/admin/users/5', expect.objectContaining({ method: 'DELETE' }));

    await userEvent.click(within(row).getByRole('button', { name: 'Cancelar eliminación' }));
    await waitFor(() => expect(within(row).queryByText(/Se elimina el/)).not.toBeInTheDocument());
    expect(within(row).getByRole('button', { name: 'Eliminar' })).toBeInTheDocument();
  });

  it('no ofrece eliminar la propia cuenta', async () => {
    setup();
    renderRoutes(routes, '/admin?tab=users');

    const mine = (await screen.findByText('@root · root@test.com')).closest('tr')!;
    expect(within(mine).queryByRole('button', { name: 'Eliminar' })).not.toBeInTheDocument();
    const other = screen.getByText('@ana · ana@test.com').closest('tr')!;
    expect(within(other).getByRole('button', { name: 'Eliminar' })).toBeInTheDocument();
  });

  it('si el backend rechaza la baja (último admin), muestra su mensaje y la fila no cambia', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    setup({
      'DELETE /api/admin/users/5': {
        status: 409, body: { title: 'Conflict', detail: 'No puedes eliminar al último administrador', code: 'user.lastAdmin' },
      },
    });
    renderRoutes(routes, '/admin?tab=users');

    const row = (await screen.findByText('@ana · ana@test.com')).closest('tr')!;
    await userEvent.click(within(row).getByRole('button', { name: 'Eliminar' }));
    expect(await screen.findByTestId('toast')).toHaveTextContent('No puedes eliminar al último administrador');
    expect(within(row).queryByText(/Se elimina el/)).not.toBeInTheDocument();
    expect(within(row).getByRole('button', { name: 'Eliminar' })).toBeInTheDocument();
  });
});
