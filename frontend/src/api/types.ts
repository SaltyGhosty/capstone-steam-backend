// Tipos que reflejan los DTOs del backend (com.capstone.steam.*.dto).
// Los BigDecimal llegan como number en el JSON; las fechas, como string ISO.

export type Role = 'ROLE_USER' | 'ROLE_DEVELOPER' | 'ROLE_ADMIN';
export type GameStatus = 'DRAFT' | 'PUBLISHED' | 'DELISTED';
export type OrderStatus = 'PENDING' | 'PAID' | 'FAILED' | 'CANCELLED';
export type PaymentMethod = 'CREDIT_CARD' | 'PAYPAL' | 'WALLET';
export type PaymentStatus = 'INITIATED' | 'SUCCEEDED' | 'FAILED';
export type AccountType = 'PLAYER' | 'DEVELOPER';
/** Idioma de los correos del usuario (la interfaz web sigue solo en español). */
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
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
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
  averageRating: number;
  ratingCount: number;
  genres: Genre[];
  developerName: string;
}

export interface GameDetail {
  id: number;
  title: string;
  slug: string;
  description: string | null;
  coverUrl: string | null;
  price: number;
  discountPercent: number;
  finalPrice: number;
  releaseDate: string | null;
  status: GameStatus;
  averageRating: number;
  ratingCount: number;
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
  price: number;
  discountPercent?: number | null;
  releaseDate?: string | null;
  coverUrl?: string | null;
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
  rating: number;
  recommended: boolean;
  body: string | null;
  author: { id: number; displayName: string };
  createdAt: string;
  updatedAt: string;
}

export interface ReviewRequest {
  gameId?: number;
  rating: number;
  recommended: boolean;
  body?: string;
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
  minRating?: number;
  sort?: GameSort;
  page?: number;
  size?: number;
}
