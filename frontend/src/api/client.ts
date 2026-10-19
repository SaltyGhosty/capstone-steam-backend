// Cliente HTTP mínimo sobre fetch: añade el Bearer token y convierte los ProblemDetail
// (RFC 9457) del backend en ApiError con el mensaje y los errores por campo.

import { getLang, t } from '@/i18n';

const BASE_URL = (import.meta.env.VITE_API_URL ?? '').replace(/\/$/, '');
const TOKEN_KEY = 'arcade.token';
const REFRESH_KEY = 'arcade.refresh';

/** Swagger UI del backend: mismo origen con el proxy (Vite/Caddy) o VITE_API_URL en producción. */
export const API_DOCS_URL = `${BASE_URL}/swagger-ui/index.html`;

export class ApiError extends Error {
  status: number;
  fieldErrors: Record<string, string>;
  body: unknown;

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}, body?: unknown) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
    this.body = body;
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
    // almacenamiento bloqueado: la sesión solo dura lo que la pestaña
  }
}

export function getRefreshToken(): string | null {
  try {
    return localStorage.getItem(REFRESH_KEY);
  } catch {
    return null;
  }
}

/** setToken(null) no toca el refresh token: hay que borrarlo aparte con setRefreshToken(null). */
export function setRefreshToken(token: string | null) {
  try {
    if (token) localStorage.setItem(REFRESH_KEY, token);
    else localStorage.removeItem(REFRESH_KEY);
  } catch {
    // almacenamiento bloqueado: la sesión solo dura lo que la pestaña
  }
}

// Se avisa a AuthContext cuando el backend responde 401 con token (caducado o inválido).
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
  /** Estados distintos de 2xx que se devuelven como respuesta normal (p. ej. 402 del pago). */
  acceptStatus?: number[];
  /** La petición sobrevive a una navegación inmediata (cierre de sesión). */
  keepalive?: boolean;
}

// Renovación en curso, compartida: varias peticiones con 401 a la vez hacen un solo refresh.
let refreshing: Promise<boolean> | null = null;

/** true si se guardaron tokens nuevos; false si el backend rechazó el refresh (sesión cerrada). */
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
    // fetch directo (no api()): un 401 aquí no debe volver a intentar refrescar
    res = await fetch(buildUrl('/api/auth/refresh'), {
      method: 'POST',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json', 'Accept-Language': getLang() },
      body: JSON.stringify({ refreshToken }),
    });
  } catch {
    // sin conexión no se pierde la sesión: la petición original falla y ya se reintentará
    throw new ApiError(0, t('common.serverDown'));
  }
  const data = res.ok ? ((await res.json().catch(() => null)) as { accessToken?: string; refreshToken?: string } | null) : null;
  if (data?.accessToken && data.refreshToken) {
    setToken(data.accessToken);
    setRefreshToken(data.refreshToken);
    return true;
  }
  // Solo un rechazo de autenticación (400/401) cierra la sesión; un 5xx o un 429 es un fallo
  // temporal del servidor y no debe expulsar al usuario.
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
  // La API responde (errores, descripciones, motivos de pago) en el idioma de la interfaz
  const headers: Record<string, string> = { Accept: 'application/json', 'Accept-Language': getLang() };
  // Un FormData (subida de archivos) va tal cual: el navegador pone el Content-Type con su boundary
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
      // Otra petición ya renovó los tokens mientras esta volaba: basta con repetirla
      const renewed = getToken() !== token || (await refreshSession());
      if (renewed) return request<T>(path, options, false);
      // refreshSession ya cerró la sesión y avisó una sola vez
    } else if (getToken() === token) {
      // (si el token ya cambió o se borró, otra petición se ocupó de la sesión)
      onUnauthorized?.();
    }
  }

  const problem = (data ?? {}) as { detail?: string; title?: string; errors?: Record<string, string> };
  const message = problem.detail || problem.title || `Error ${res.status}`;
  throw new ApiError(res.status, message, problem.errors ?? {}, data);
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}
