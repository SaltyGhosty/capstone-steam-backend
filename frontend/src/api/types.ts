// Tipi che rispecchiano i DTO del backend (com.capstone.steam.*.dto).
// I BigDecimal arrivano come number nel JSON; le date come stringhe ISO.

export type Role = 'ROLE_USER' | 'ROLE_DEVELOPER' | 'ROLE_ADMIN';
export type GameStatus = 'DRAFT' | 'PUBLISHED' | 'DELISTED';
export type OrderStatus = 'PENDING' | 'PAID' | 'FAILED' | 'CANCELLED';
export type PaymentMethod = 'CREDIT_CARD' | 'PAYPAL' | 'WALLET';
export type PaymentStatus = 'INITIATED' | 'SUCCEEDED' | 'FAILED';
export type AccountType = 'PLAYER' | 'DEVELOPER';
/** Etichetta di valutazione di un gioco, con le regole di Steam (NONE = meno di 10 recensioni). */
export type ReviewScore =
  | 'OVERWHELMINGLY_POSITIVE' | 'VERY_POSITIVE' | 'POSITIVE' | 'MOSTLY_POSITIVE' | 'MIXED'
  | 'MOSTLY_NEGATIVE' | 'NEGATIVE' | 'VERY_NEGATIVE' | 'OVERWHELMINGLY_NEGATIVE' | 'NONE';
/** Lingua delle email dell'utente (l'interfaccia si sceglie a parte, con le bandiere). */
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
  /** false finché il titolare non clicca il link dell'email (senza verifica non può accedere). */
  emailVerified: boolean;
  /** Data in cui l'account verrà anonimizzato se è in pausa; null se non c'è cancellazione in sospeso. */
  deletionScheduledAt: string | null;
  deletionRequestedBy: 'SELF' | 'ADMIN' | null;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  displayName?: string;
  accountType: AccountType;
  locale?: Locale;
}

/** Campi omessi = non vengono modificati */
export interface UpdateProfileRequest {
  displayName?: string;
  avatarUrl?: string;
  locale?: Locale;
  profilePublic?: boolean;
}

/** Registrazione con verifica email: account creato ma senza sessione finché non si clicca il link dell'email. */
export interface RegisterResponse {
  id: number;
  email: string;
  verificationRequired: true;
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
  /** null se lo studio ha eliminato il suo account */
  developerName: string | null;
  developerDeleted: boolean;
}

export interface GameDetail {
  id: number;
  title: string;
  slug: string;
  /** Nella lingua dell'interfaccia (quella spagnola se manca la traduzione) */
  description: string | null;
  /** Le tre versioni così come sono, per il form dello sviluppatore */
  descriptions: { es: string | null; en: string | null; it: string | null };
  coverUrl: string | null;
  /** AppID dello store di Steam; se presente, prezzo e sconto vengono da Steam */
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
  developer: { id: number; displayName: string | null; deleted: boolean };
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
  developerName: string | null;
  developerDeleted: boolean;
}

export interface Review {
  id: number;
  gameId: number;
  recommended: boolean;
  body: string | null;
  /** Lingua in cui è scritta */
  language: Locale;
  author: { id: number; displayName: string };
  createdAt: string;
  updatedAt: string;
}

export interface ReviewRequest {
  gameId?: number;
  recommended: boolean;
  body?: string;
  /** Lingua dell'interfaccia al momento della scrittura */
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

/** Risultato della sincronizzazione dei prezzi con Steam. */
export interface SteamSyncResult {
  checked: number;
  updated: number;
  newSales: number;
  notified: number;
}

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
  /** Messaggi di questo amico che non ho ancora letto. */
  unreadMessages: number;
}

/** `user` è l'altra persona, non chi consulta. */
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

/** Richiesta ricevuta in sospeso, nella consultazione dell'attività. */
export interface ActivityRequest {
  id: number;
  username: string;
  displayName: string | null;
  createdAt: string;
}

/** Messaggi non letti di un mittente. */
export interface ActivityUnread {
  username: string;
  displayName: string | null;
  count: number;
  lastMessageAt: string;
}

/** `total` = richieste ricevute + messaggi non letti: è il numero di «Amici». */
export interface FriendActivity {
  incomingRequests: ActivityRequest[];
  unread: ActivityUnread[];
  total: number;
}

export interface Conversation {
  username: string;
  displayName: string | null;
  avatarUrl: string | null;
  friend: boolean;
  unread: number;
  lastMessage: string | null;
  lastMessageAt: string | null;
}

/** `mine`: l'ho scritto io. */
export interface ChatMessageItem {
  id: number;
  mine: boolean;
  body: string;
  createdAt: string;
}

export interface ProfileGame {
  gameId: number;
  title: string;
  slug: string;
  coverUrl: string | null;
  playtimeMinutes: number;
}

/** `library` e `friends` sono null se `visible` è false. */
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

/** Un messaggio della chat dell'assistente; role in minuscolo, come nel backend. */
export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

export interface ChatResponse {
  reply: string;
  games: GameSummary[];
}
