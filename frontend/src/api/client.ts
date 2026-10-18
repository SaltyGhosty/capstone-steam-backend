// Cliente HTTP mínimo sobre fetch: añade el Bearer token y convierte los ProblemDetail
// (RFC 9457) del backend en ApiError con el mensaje y los errores por campo.

import { getLang, t } from '@/i18n';

const BASE_URL = (import.meta.env.VITE_API_URL ?? '').replace(/\/$/, '');
const TOKEN_KEY = 'arcade.token';

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
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, query, acceptStatus = [] } = options;
  const token = getToken();
  // La API responde (errores, descripciones, motivos de pago) en el idioma de la interfaz
  const headers: Record<string, string> = { Accept: 'application/json', 'Accept-Language': getLang() };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;

  let res: Response;
  try {
    res = await fetch(buildUrl(path, query), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, t('common.serverDown'));
  }

  const text = await res.text();
  const data = text ? safeJson(text) : null;

  if (res.ok || acceptStatus.includes(res.status)) {
    return data as T;
  }

  if (res.status === 401 && token) onUnauthorized?.();

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
