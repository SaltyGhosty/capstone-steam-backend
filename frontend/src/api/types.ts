// Tipos que reflejan los DTOs del backend (com.capstone.steam.*.dto).
// Los BigDecimal llegan como number en el JSON; las fechas, como string ISO.

export type Role = 'ROLE_USER' | 'ROLE_DEVELOPER' | 'ROLE_ADMIN';
export type GameStatus = 'DRAFT' | 'PUBLISHED' | 'DELISTED';
export type OrderStatus = 'PENDING' | 'PAID' | 'FAILED' | 'CANCELLED';
export type PaymentMethod = 'CREDIT_CARD' | 'PAYPAL' | 'WALLET';
export type PaymentStatus = 'INITIATED' | 'SUCCEEDED' | 'FAILED';
export type AccountType = 'PLAYER' | 'DEVELOPER';
/** Etiqueta de valoración de un juego, con las reglas de Steam (NONE = menos de 10 reseñas). */
export type ReviewScore =
  | 'OVERWHELMINGLY_POSITIVE' | 'VERY_POSITIVE' | 'POSITIVE' | 'MOSTLY_POSITIVE' | 'MIXED'
  | 'MOSTLY_NEGATIVE' | 'NEGATIVE' | 'VERY_NEGATIVE' | 'OVERWHELMINGLY_NEGATIVE' | 'NONE';
/** Idioma de los correos del usuario (la interfaz se elige aparte, con las banderas). */
export type Locale = 'es' | 'en' | 'it';

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface User {
  id: number;
  username: string;
  email: string;
  displayName: string | null;
  avatarUrl: string | null;
  enabled: boolean;
  roles: Role[];
  createdAt: string;
  locale: Locale;
  profilePublic: boolean;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  displayName?: string;
  accountType: AccountType;
  locale?: Locale;
}

/** Campos omitidos = no se modifican */
export interface UpdateProfileRequest {
  displayName?: string;
  avatarUrl?: string;
  locale?: Locale;
  profilePublic?: boolean;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  refreshToken: string;
  user: User;
}

export interface Genre {
  id: number;
  name: string;
  slug: string;
}

export interface GameSummary {
  id: number;
  title: string;
  slug: string;
  coverUrl: string | null;
  price: number;
  discountPercent: number;
  finalPrice: number;
  releaseDate: string | null;
  reviewCount: number;
  positivePercent: number;
  reviewScore: ReviewScore;
  genres: Genre[];
  developerName: string;
}

export interface GameDetail {
  id: number;
  title: string;
  slug: string;
  /** En el idioma de la interfaz (la española si falta la traducción) */
  description: string | null;
  /** Las tres versiones tal cual, para el formulario del developer */
  descriptions: { es: string | null; en: string | null; it: string | null };
  coverUrl: string | null;
  /** AppID de la tienda de Steam; si lo tiene, precio y descuento vienen de Steam */
  steamAppId: number | null;
  priceSyncedAt: string | null;
  price: number;
  discountPercent: number;
  finalPrice: number;
  releaseDate: string | null;
  status: GameStatus;
  reviewCount: number;
  positivePercent: number;
  reviewScore: ReviewScore;
  salesCount: number;
  genres: Genre[];
  developer: { id: number; displayName: string };
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface GameRequest {
  title: string;
  description?: string | null;
  descriptionEn?: string | null;
  descriptionIt?: string | null;
  price: number;
  discountPercent?: number | null;
  releaseDate?: string | null;
  coverUrl?: string | null;
  steamAppId?: number | null;
  genreIds?: number[];
  version?: number | null;
}

export interface Ownership {
  gameId: number;
  owned: boolean;
  inCart: boolean;
  inWishlist: boolean;
  reviewed: boolean;
}

export interface CartItem {
  gameId: number;
  title: string;
  slug: string;
  coverUrl: string | null;
  price: number;
  discountPercent: number;
  finalPrice: number;
  available: boolean;
  addedAt: string;
}

export interface Cart {
  items: CartItem[];
  itemCount: number;
  total: number;
}

export interface WishlistItem {
  gameId: number;
  title: string;
  slug: string;
  coverUrl: string | null;
  price: number;
  discountPercent: number;
  finalPrice: number;
  onSale: boolean;
  available: boolean;
  addedAt: string;
}

export interface LibraryEntry {
  gameId: number;
  title: string;
  slug: string;
  coverUrl: string | null;
  acquiredAt: string;
  playtimeMinutes: number;
  orderId: number | null;
}

export interface Review {
  id: number;
  gameId: number;
  recommended: boolean;
  body: string | null;
  /** Idioma en que está escrita */
  language: Locale;
  author: { id: number; displayName: string };
  createdAt: string;
  updatedAt: string;
}

export interface ReviewRequest {
  gameId?: number;
  recommended: boolean;
  body?: string;
  /** Idioma de la interfaz al escribirla */
  language: Locale;
}

export interface Order {
  id: number;
  status: OrderStatus;
  total: number;
  createdAt: string;
  paidAt: string | null;
  items: { gameId: number; title: string; slug: string; coverUrl: string | null; unitPrice: number }[];
  lastPayment: {
    method: PaymentMethod;
    status: PaymentStatus;
    reference: string | null;
    failureReason: string | null;
    createdAt: string;
  } | null;
}

export interface OrderSummary {
  id: number;
  status: OrderStatus;
  total: number;
  itemCount: number;
  createdAt: string;
  paidAt: string | null;
}

export type GameSort = 'newest' | 'price_asc' | 'price_desc' | 'top_rated' | 'best_selling' | 'title';

export interface CatalogQuery {
  q?: string;
  genres?: string[];
  minPrice?: number;
  maxPrice?: number;
  onSale?: boolean;
  minPositivePercent?: number;
  sort?: GameSort;
  page?: number;
  size?: number;
}

/** Resultado de sincronizar los precios con Steam. */
export interface SteamSyncResult {
  checked: number;
  updated: number;
  newSales: number;
  notified: number;
}

// ---- Amigos y perfil (Bloque E) ----
export type Relation = 'SELF' | 'FRIENDS' | 'REQUEST_SENT' | 'REQUEST_RECEIVED' | 'NONE';
export type FriendshipStatus = 'PENDING' | 'ACCEPTED';

export interface UserCard {
  username: string;
  displayName: string | null;
  avatarUrl: string | null;
}

export interface SearchHit {
  user: UserCard;
  relation: Relation;
  requestId: number | null;
}

export interface FriendResponse {
  user: UserCard;
  since: string | null;
}

/** `user` es la otra persona, no quien consulta. */
export interface FriendRequest {
  id: number;
  user: UserCard;
  status: FriendshipStatus;
  createdAt: string;
}

export interface FriendRequests {
  incoming: FriendRequest[];
  outgoing: FriendRequest[];
}

export interface ProfileGame {
  gameId: number;
  title: string;
  slug: string;
  coverUrl: string | null;
  playtimeMinutes: number;
}

/** `library` y `friends` son null si `visible` es false. */
export interface PublicProfile {
  user: UserCard;
  memberSince: string;
  profilePublic: boolean;
  relation: Relation;
  requestId: number | null;
  visible: boolean;
  library: ProfileGame[] | null;
  friends: FriendResponse[] | null;
}
