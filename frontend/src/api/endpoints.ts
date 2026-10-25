// Una función por endpoint del backend. La colección de Postman es el contrato de referencia.
import { api } from './client';
import type {
  AuthResponse, Cart, CatalogQuery, ChatMessage, ChatResponse, GameDetail, GameRequest, GameStatus, GameSummary, Genre,
  LibraryEntry, Locale, Order, OrderSummary, Ownership, PageResponse, PaymentMethod, RegisterRequest, RegisterResponse, Review, ReviewRequest, Role,
  ChatMessageItem, Conversation, FriendActivity, FriendRequest, FriendRequests, FriendResponse, PublicProfile, SearchHit, SteamSyncResult, UpdateProfileRequest, User,
  WishlistItem,
} from './types';

const page = (p = 0, size = 20) => ({ page: p, size });

export const authApi = {
  login: (login: string, password: string) =>
    api<AuthResponse>('/api/auth/login', { method: 'POST', body: { login, password } }),
  /** Con la verificación de email activa no devuelve sesión sino un RegisterResponse. */
  register: (body: RegisterRequest) =>
    api<AuthResponse | RegisterResponse>('/api/auth/register', { method: 'POST', body }),
  /** De qué cuenta es el enlace (si sigue vigente). No gasta el enlace. */
  previewVerification: (token: string) =>
    api<{ username: string }>('/api/auth/verify-email/preview', { method: 'POST', body: { token } }),
  /** Canjea el enlace del correo con la contraseña de ese registro: verifica el email y devuelve la sesión. */
  verifyEmail: (token: string, password: string) =>
    api<AuthResponse>('/api/auth/verify-email', { method: 'POST', body: { token, password } }),
  /** Siempre 204, exista o no la cuenta. */
  resendVerification: (email: string) =>
    api<void>('/api/auth/resend-verification', { method: 'POST', body: { email } }),
  /** Cancela la baja pendiente (pedida por el dueño) y abre la sesión como el login. */
  recoverAccount: (login: string, password: string) =>
    api<AuthResponse>('/api/auth/recover-account', { method: 'POST', body: { login, password } }),
  /** Revoca el refresh token en el servidor (204). */
  logout: (refreshToken: string) =>
    api<void>('/api/auth/logout', { method: 'POST', body: { refreshToken }, keepalive: true }),
};

export const userApi = {
  me: () => api<User>('/api/users/me'),
  update: (body: UpdateProfileRequest) =>
    api<User>('/api/users/me', { method: 'PATCH', body }),
  /** Pone la cuenta en pausa 14 días; el servidor corta las sesiones. */
  requestDeletion: (password: string) =>
    api<{ deletionScheduledAt: string }>('/api/users/me/deletion', { method: 'POST', body: { password } }),
  search: (q: string) => api<SearchHit[]>('/api/users/search', { query: { q } }),
  profile: (username: string) => api<PublicProfile>(`/api/users/${encodeURIComponent(username)}/profile`),
};

export const friendsApi = {
  list: () => api<FriendResponse[]>('/api/friends'),
  requests: () => api<FriendRequests>('/api/friends/requests'),
  send: (username: string) =>
    api<FriendRequest>('/api/friends/requests', { method: 'POST', body: { username } }),
  accept: (id: number) => api<FriendRequest>(`/api/friends/requests/${id}/accept`, { method: 'POST' }),
  reject: (id: number) => api<void>(`/api/friends/requests/${id}/reject`, { method: 'POST' }),
  cancel: (id: number) => api<void>(`/api/friends/requests/${id}`, { method: 'DELETE' }),
  remove: (username: string) => api<void>(`/api/friends/${encodeURIComponent(username)}`, { method: 'DELETE' }),
  activity: () => api<FriendActivity>('/api/friends/activity'),
  conversations: () => api<Conversation[]>('/api/friends/conversations'),
  /** Del más nuevo al más antiguo; `before` es el id del mensaje más antiguo que ya se tiene. */
  messages: (username: string, before?: number, size?: number) =>
    api<ChatMessageItem[]>(`/api/friends/${encodeURIComponent(username)}/messages`, { query: { before, size } }),
  sendMessage: (username: string, body: string) =>
    api<ChatMessageItem>(`/api/friends/${encodeURIComponent(username)}/messages`, { method: 'POST', body: { body } }),
  markRead: (username: string) =>
    api<void>(`/api/friends/${encodeURIComponent(username)}/messages/read`, { method: 'POST' }),
};

export const assistantApi = {
  /** Los últimos mensajes (máx. 10); el último tiene que ser del usuario. El idioma sale de Accept-Language. */
  chat: (messages: ChatMessage[]) =>
    api<ChatResponse>('/api/recommendations/chat', { method: 'POST', body: { messages } }),
};

export const supportApi = {
  /** 202 sin cuerpo. Sin sesión hace falta el email; con sesión el backend usa el de la cuenta. */
  send: (body: { email?: string; subject: string; message: string }) =>
    api<void>('/api/support', { method: 'POST', body }),
};

export const catalogApi = {
  search: (q: CatalogQuery = {}) =>
    api<PageResponse<GameSummary>>('/api/games', { query: { ...q } }),
  get: (slug: string) => api<GameDetail>(`/api/games/${encodeURIComponent(slug)}`),
  /** Sin language salen las reseñas de todos los idiomas. */
  reviews: (slug: string, p = 0, size = 10, language?: Locale) =>
    api<PageResponse<Review>>(`/api/games/${encodeURIComponent(slug)}/reviews`, { query: { ...page(p, size), language } }),
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

/** Sube el archivo como multipart (la parte se llama `file`). */
const coverForm = (file: File) => {
  const form = new FormData();
  form.append('file', file);
  return form;
};

export const uploadsApi = {
  /** Si el servidor tiene Cloudinary configurado, se puede subir la portada como archivo */
  coverStatus: () => api<{ enabled: boolean }>('/api/uploads/covers/status'),
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
  importFromSteam: (steamAppId: number) =>
    api<GameDetail>('/api/developer/games/import', { method: 'POST', body: { steamAppId } }),
  uploadCover: (id: number, file: File) =>
    api<GameDetail>(`/api/developer/games/${id}/cover`, { method: 'POST', body: coverForm(file) }),
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
  verifyEmail: (id: number) => api<User>(`/api/admin/users/${id}/verify-email`, { method: 'POST' }),
  deleteUser: (id: number) => api<User>(`/api/admin/users/${id}`, { method: 'DELETE' }),
  cancelDeletion: (id: number) => api<User>(`/api/admin/users/${id}/deletion/cancel`, { method: 'POST' }),
  grantGame: (userId: number, gameId: number) =>
    api<LibraryEntry>(`/api/admin/users/${userId}/library/${gameId}`, { method: 'POST' }),
  createGenre: (name: string) => api<Genre>('/api/admin/genres', { method: 'POST', body: { name } }),
  updateGenre: (id: number, name: string) => api<Genre>(`/api/admin/genres/${id}`, { method: 'PUT', body: { name } }),
  deleteGenre: (id: number) => api<void>(`/api/admin/genres/${id}`, { method: 'DELETE' }),
  syncSteam: () => api<SteamSyncResult>('/api/admin/steam/sync', { method: 'POST' }),
  uploadCover: (id: number, file: File) =>
    api<GameDetail>(`/api/admin/games/${id}/cover`, { method: 'POST', body: coverForm(file) }),
};
