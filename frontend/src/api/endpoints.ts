// Una función por endpoint del backend. La colección de Postman es el contrato de referencia.
import { api } from './client';
import type {
  AccountType, AuthResponse, Cart, CatalogQuery, GameDetail, GameRequest, GameStatus, GameSummary, Genre,
  LibraryEntry, Order, OrderSummary, Ownership, PageResponse, PaymentMethod, Review, ReviewRequest, Role, User,
  WishlistItem,
} from './types';

const page = (p = 0, size = 20) => ({ page: p, size });

export const authApi = {
  login: (login: string, password: string) =>
    api<AuthResponse>('/api/auth/login', { method: 'POST', body: { login, password } }),
  register: (body: { username: string; email: string; password: string; displayName?: string; accountType: AccountType }) =>
    api<AuthResponse>('/api/auth/register', { method: 'POST', body }),
};

export const userApi = {
  me: () => api<User>('/api/users/me'),
  update: (body: { displayName?: string; avatarUrl?: string }) =>
    api<User>('/api/users/me', { method: 'PATCH', body }),
};

export const catalogApi = {
  search: (q: CatalogQuery = {}) =>
    api<PageResponse<GameSummary>>('/api/games', { query: { ...q } }),
  get: (slug: string) => api<GameDetail>(`/api/games/${encodeURIComponent(slug)}`),
  reviews: (slug: string, p = 0, size = 10) =>
    api<PageResponse<Review>>(`/api/games/${encodeURIComponent(slug)}/reviews`, { query: page(p, size) }),
  genres: () => api<Genre[]>('/api/genres'),
};

export const cartApi = {
  get: () => api<Cart>('/api/cart'),
  add: (gameId: number) => api<Cart>(`/api/cart/items/${gameId}`, { method: 'PUT' }),
  remove: (gameId: number) => api<Cart>(`/api/cart/items/${gameId}`, { method: 'DELETE' }),
  clear: () => api<Cart>('/api/cart', { method: 'DELETE' }),
};

export const wishlistApi = {
  list: (p = 0, size = 50) => api<PageResponse<WishlistItem>>('/api/wishlist', { query: page(p, size) }),
  add: (gameId: number) => api<void>(`/api/wishlist/${gameId}`, { method: 'PUT' }),
  remove: (gameId: number) => api<void>(`/api/wishlist/${gameId}`, { method: 'DELETE' }),
};

export const libraryApi = {
  list: (p = 0, size = 50) => api<PageResponse<LibraryEntry>>('/api/library', { query: page(p, size) }),
  status: (gameId: number) => api<Ownership>(`/api/library/status/${gameId}`),
  play: (gameId: number, minutes: number) =>
    api<LibraryEntry>(`/api/library/${gameId}/play`, { method: 'POST', body: { minutes } }),
};

export const reviewApi = {
  create: (body: ReviewRequest) => api<Review>('/api/reviews', { method: 'POST', body }),
  update: (id: number, body: ReviewRequest) => api<Review>(`/api/reviews/${id}`, { method: 'PUT', body }),
  remove: (id: number) => api<void>(`/api/reviews/${id}`, { method: 'DELETE' }),
};

export const orderApi = {
  checkout: () => api<Order>('/api/orders', { method: 'POST' }),
  // 402 = pago rechazado: el backend devuelve el pedido FAILED en el cuerpo
  pay: (id: number, method: PaymentMethod, cardNumber?: string) =>
    api<Order>(`/api/orders/${id}/pay`, { method: 'POST', body: { method, cardNumber }, acceptStatus: [402] }),
  cancel: (id: number) => api<Order>(`/api/orders/${id}/cancel`, { method: 'POST' }),
  list: (p = 0, size = 20) => api<PageResponse<OrderSummary>>('/api/orders', { query: page(p, size) }),
  get: (id: number) => api<Order>(`/api/orders/${id}`),
};

export const developerApi = {
  list: (p = 0, size = 50) => api<PageResponse<GameDetail>>('/api/developer/games', { query: page(p, size) }),
  get: (id: number) => api<GameDetail>(`/api/developer/games/${id}`),
  create: (body: GameRequest) => api<GameDetail>('/api/developer/games', { method: 'POST', body }),
  update: (id: number, body: GameRequest) => api<GameDetail>(`/api/developer/games/${id}`, { method: 'PUT', body }),
  remove: (id: number) => api<void>(`/api/developer/games/${id}`, { method: 'DELETE' }),
  publish: (id: number) => api<GameDetail>(`/api/developer/games/${id}/publish`, { method: 'POST' }),
  unpublish: (id: number) => api<GameDetail>(`/api/developer/games/${id}/unpublish`, { method: 'POST' }),
  discountAll: (percent: number) =>
    api<{ updated: number }>('/api/developer/games/discount', { method: 'POST', body: { percent } }),
};

export const adminApi = {
  games: (status?: GameStatus, p = 0, size = 50) =>
    api<PageResponse<GameDetail>>('/api/admin/games', { query: { status, ...page(p, size) } }),
  moderate: (id: number, status: GameStatus) =>
    api<GameDetail>(`/api/admin/games/${id}/status`, { method: 'PATCH', body: { status } }),
  users: (p = 0, size = 50) => api<PageResponse<User>>('/api/admin/users', { query: page(p, size) }),
  setRoles: (id: number, roles: Role[]) => api<User>(`/api/admin/users/${id}/roles`, { method: 'PUT', body: { roles } }),
  setEnabled: (id: number, enabled: boolean) =>
    api<User>(`/api/admin/users/${id}/enabled`, { method: 'PATCH', body: { enabled } }),
  grantGame: (userId: number, gameId: number) =>
    api<LibraryEntry>(`/api/admin/users/${userId}/library/${gameId}`, { method: 'POST' }),
  createGenre: (name: string) => api<Genre>('/api/admin/genres', { method: 'POST', body: { name } }),
  updateGenre: (id: number, name: string) => api<Genre>(`/api/admin/genres/${id}`, { method: 'PUT', body: { name } }),
  deleteGenre: (id: number) => api<void>(`/api/admin/genres/${id}`, { method: 'DELETE' }),
};
