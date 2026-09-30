// Renderiza páginas reales con el router y el StoreProvider, empezando en una URL concreta.
import { render, screen } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { StoreProvider } from '@/context/StoreContext';

/** Muestra la ruta actual para poder comprobar las redirecciones. */
function CurrentPath() {
  const location = useLocation();
  return <output data-testid="path">{location.pathname}</output>;
}

export function renderRoutes(routes: Record<string, ReactElement>, initialPath: string) {
  const result = render(
    <MemoryRouter initialEntries={[initialPath]}>
      <StoreProvider>
        <Routes>
          {Object.entries(routes).map(([path, element]) => <Route key={path} path={path} element={element} />)}
        </Routes>
        <CurrentPath />
      </StoreProvider>
    </MemoryRouter>,
  );
  return { ...result, path: () => screen.getByTestId('path').textContent };
}

export const demoUser = {
  id: 7,
  username: 'demo_player',
  email: 'demo@test.com',
  displayName: 'Demo Player',
  avatarUrl: null,
  enabled: true,
  roles: ['ROLE_USER'],
  createdAt: '2026-09-30T10:00:00Z',
};
