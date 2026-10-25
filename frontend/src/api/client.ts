// Client HTTP minimale sopra fetch: aggiunge il Bearer token e converte i ProblemDetail
// (RFC 9457) del backend in ApiError con il messaggio e gli errori per campo.

import { getLang, t } from '@/i18n';

const BASE_URL = (import.meta.env.VITE_API_URL ?? '').replace(/\/$/, '');
const TOKEN_KEY = 'arcade.token';
const REFRESH_KEY = 'arcade.refresh';

/** Swagger UI del backend: stessa origin con il proxy (Vite/Caddy) o VITE_API_URL in produzione. */
export const API_DOCS_URL = `${BASE_URL}/swagger-ui/index.html`;

export class ApiError extends Error {
  status: number;
  fieldErrors: Record<string, string>;
  body: unknown;
  /** Chiave dell'errore del backend (es. auth.emailNotVerified), per distinguere errori con lo stesso status. */
  readonly code?: string;

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}, body?: unknown, code?: string) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
    this.body = body;
    this.code = code;
  }
}

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    // storage bloccato: la sessione dura quanto la scheda
  }
}

export function getRefreshToken(): string | null {
  try {
    return localStorage.getItem(REFRESH_KEY);
  } catch {
    return null;
  }
}

/** setToken(null) non tocca il refresh token: va cancellato a parte con setRefreshToken(null). */
export function setRefreshToken(token: string | null) {
  try {
    if (token) localStorage.setItem(REFRESH_KEY, token);
    else localStorage.removeItem(REFRESH_KEY);
  } catch {
    // storage bloccato: la sessione dura quanto la scheda
  }
}

// Si avvisa AuthContext quando il backend risponde 401 con un token (scaduto o non valido).
let onUnauthorized: (() => void) | null = null;
export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler;
}

type Query = Record<string, string | number | boolean | string[] | undefined | null>;

function buildUrl(path: string, query?: Query) {
  const params = new URLSearchParams();
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value === undefined || value === null || value === '') continue;
      if (Array.isArray(value)) {
        if (value.length) params.set(key, value.join(','));
      } else {
        params.set(key, String(value));
      }
    }
  }
  const qs = params.toString();
  return `${BASE_URL}${path}${qs ? `?${qs}` : ''}`;
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  query?: Query;
  /** Status diversi da 2xx che vengono restituiti come risposta normale (es. 402 del pagamento). */
  acceptStatus?: number[];
  /** La richiesta sopravvive a una navigazione immediata (chiusura della sessione). */
  keepalive?: boolean;
}

// Rinnovo in corso, condiviso: più richieste con 401 contemporanee fanno un solo refresh.
let refreshing: Promise<boolean> | null = null;

/** true se sono stati salvati token nuovi; false se il backend ha rifiutato il refresh (sessione chiusa). */
function refreshSession(): Promise<boolean> {
  refreshing ??= doRefresh().finally(() => {
    refreshing = null;
  });
  return refreshing;
}

async function doRefresh(): Promise<boolean> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return false;
  let res: Response;
  try {
    // fetch diretto (non api()): un 401 qui non deve ritentare il refresh
    res = await fetch(buildUrl('/api/auth/refresh'), {
      method: 'POST',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json', 'Accept-Language': getLang() },
      body: JSON.stringify({ refreshToken }),
    });
  } catch {
    // senza connessione non si perde la sessione: la richiesta originale fallisce e si riproverà
    throw new ApiError(0, t('common.serverDown'));
  }
  const data = res.ok ? ((await res.json().catch(() => null)) as { accessToken?: string; refreshToken?: string } | null) : null;
  if (data?.accessToken && data.refreshToken) {
    setToken(data.accessToken);
    setRefreshToken(data.refreshToken);
    return true;
  }
  // Solo un rifiuto di autenticazione (400/401) chiude la sessione; un 5xx o un 429 è un errore
  // temporaneo del server e non deve buttare fuori l'utente.
  if (res.status !== 400 && res.status !== 401) throw new ApiError(res.status, t('common.somethingWrong'));
  setToken(null);
  setRefreshToken(null);
  onUnauthorized?.();
  return false;
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return request<T>(path, options, true);
}

async function request<T>(path: string, options: RequestOptions, canRefresh: boolean): Promise<T> {
  const { method = 'GET', body, query, acceptStatus = [], keepalive } = options;
  const token = getToken();
  // L'API risponde (errori, descrizioni, motivi di pagamento) nella lingua dell'interfaccia
  const headers: Record<string, string> = { Accept: 'application/json', 'Accept-Language': getLang() };
  // Un FormData (upload di file) va così com'è: il browser imposta il Content-Type con il suo boundary
  const isForm = typeof FormData !== 'undefined' && body instanceof FormData;
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;

  let res: Response;
  try {
    res = await fetch(buildUrl(path, query), {
      method,
      headers,
      body: body === undefined ? undefined : isForm ? (body as FormData) : JSON.stringify(body),
      keepalive,
    });
  } catch {
    throw new ApiError(0, t('common.serverDown'));
  }

  const text = await res.text();
  const data = text ? safeJson(text) : null;

  if (res.ok || acceptStatus.includes(res.status)) {
    return data as T;
  }

  if (res.status === 401 && token) {
    if (canRefresh && !path.startsWith('/api/auth/') && getRefreshToken()) {
      // Un'altra richiesta ha già rinnovato i token mentre questa era in volo: basta ripeterla
      // (vale solo se c'è un token nuovo; senza token la sessione è chiusa e non si ripete senza credenziali)
      const current = getToken();
      const renewed = (current !== null && current !== token) || (await refreshSession());
      if (renewed) return request<T>(path, options, false);
      // refreshSession ha già chiuso la sessione e avvisato una sola volta
    } else if (getToken() === token) {
      // (se il token è già cambiato o è stato cancellato, se ne è occupata un'altra richiesta)
      onUnauthorized?.();
    }
  }

  const problem = (data ?? {}) as { detail?: string; title?: string; errors?: Record<string, string>; code?: string };
  const message = problem.detail || problem.title || `Error ${res.status}`;
  throw new ApiError(res.status, message, problem.errors ?? {}, data, problem.code);
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}
