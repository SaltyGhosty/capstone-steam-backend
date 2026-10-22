// Estado global de la sesión: usuario y token, carrito, ids de la wishlist y avisos (toast).
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { ApiError, getRefreshToken, getToken, setRefreshToken, setToken, setUnauthorizedHandler } from '@/api/client';
import { authApi, cartApi, friendsApi, userApi, wishlistApi } from '@/api/endpoints';
import type { Cart, FriendActivity, RegisterRequest, Role, User } from '@/api/types';
import { t } from '@/i18n';

type ToastKind = 'success' | 'error';
interface ToastAction { label: string; to: string }
interface Toast { id: number; message: string; kind: ToastKind; action?: ToastAction }

/** Cada cuánto se consulta la actividad de amigos (también al volver a la pestaña). */
const ACTIVITY_POLL_MS = 30_000;
const TOAST_MS = 2800;
const TOAST_WITH_ACTION_MS = 6000;

/** Lo que ya se había visto en la consulta anterior, para saber qué es nuevo. */
interface ActivitySeen { requestIds: Set<number>; unread: Map<string, number> }

const seenOf = (a: FriendActivity): ActivitySeen => ({
  requestIds: new Set(a.incomingRequests.map((r) => r.id)),
  unread: new Map(a.unread.map((u) => [u.username, u.count])),
});

/** El aviso de lo que ha cambiado desde la consulta anterior: uno solo, concreto o resumen. */
function friendToast(activity: FriendActivity, seen: ActivitySeen): { message: string; to: string } | null {
  const newRequests = activity.incomingRequests.filter((r) => !seen.requestIds.has(r.id));
  const newSenders = activity.unread.filter((u) => u.count > (seen.unread.get(u.username) ?? 0));
  if (newRequests.length === 1 && newSenders.length === 0) {
    const r = newRequests[0];
    return { message: t('toast.friendRequest', { name: r.displayName || r.username }), to: '/friends' };
  }
  if (newSenders.length === 1 && newRequests.length === 0) {
    const u = newSenders[0];
    return { message: t('toast.friendMessage', { name: u.displayName || u.username }), to: `/friends?chat=${encodeURIComponent(u.username)}` };
  }
  const messages = newSenders.reduce((n, u) => n + u.count - (seen.unread.get(u.username) ?? 0), 0);
  const requests = newRequests.length;
  if (requests === 0 && messages === 0) return null;
  const requestsText = t('toast.friendRequests', { count: requests });
  const messagesText = t('toast.friendMessages', { count: messages });
  const message = requests && messages
    ? t('toast.friendSummary', { requests: requestsText, messages: messagesText })
    : t('toast.friendOnly', { items: requests ? requestsText : messagesText });
  return { message, to: '/friends' };
}

interface StoreValue {
  user: User | null;
  /** true mientras se comprueba el token guardado al cargar la página */
  booting: boolean;
  hasRole: (role: Role) => boolean;
  login: (login: string, password: string) => Promise<void>;
  /** verificationRequired: la cuenta existe pero no hay sesión hasta verificar el email. */
  register: (data: RegisterRequest) => Promise<{ verificationRequired: boolean; email: string }>;
  /** Cancela la baja pendiente de la cuenta e inicia la sesión. */
  recoverAccount: (login: string, password: string) => Promise<void>;
  /** Canjea el enlace del correo e inicia la sesión. */
  verifyEmail: (token: string, password: string) => Promise<User>;
  logout: () => void;
  setUser: (user: User) => void;

  cart: Cart | null;
  refreshCart: () => Promise<void>;
  addToCart: (gameId: number, title: string) => Promise<boolean>;
  removeFromCart: (gameId: number) => Promise<void>;

  wishlistIds: Set<number>;
  toggleWishlist: (gameId: number, title: string) => Promise<void>;

  /** Solicitudes recibidas + mensajes sin leer: el número de «Amigos». */
  friendBadge: number;
  refreshFriendActivity: () => Promise<void>;

  toast: Toast | null;
  /** Con `action` el aviso trae un enlace y dura más. */
  notify: (message: string, kind?: ToastKind, action?: ToastAction) => void;
  notifyError: (error: unknown) => void;
  /** Lleva al login y vuelve a la página actual después */
  requireLogin: () => boolean;
}

const StoreContext = createContext<StoreValue | null>(null);

export function StoreProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const location = useLocation();
  const [user, setUser] = useState<User | null>(null);
  const [booting, setBooting] = useState(() => Boolean(getToken()));
  const [cart, setCart] = useState<Cart | null>(null);
  const [wishlistIds, setWishlistIds] = useState<Set<number>>(new Set());
  const [toast, setToast] = useState<Toast | null>(null);
  const toastTimer = useRef<number | undefined>(undefined);

  const [friendBadge, setFriendBadge] = useState(0);
  // null = aún no hay consulta de esta sesión: la primera no avisa
  const activitySeen = useRef<ActivitySeen | null>(null);
  // número de la última consulta lanzada (las respuestas anteriores se descartan) y la que sigue en vuelo (0 = ninguna)
  const activityReqId = useRef(0);
  const activityInFlight = useRef(0);

  const notify = useCallback((message: string, kind: ToastKind = 'success', action?: ToastAction) => {
    window.clearTimeout(toastTimer.current);
    setToast({ id: Date.now(), message, kind, action });
    toastTimer.current = window.setTimeout(() => setToast(null), action ? TOAST_WITH_ACTION_MS : TOAST_MS);
  }, []);

  const notifyError = useCallback(
    (error: unknown) => notify(error instanceof ApiError ? error.message : t('common.somethingWrong'), 'error'),
    [notify],
  );

  const clearSession = useCallback(() => {
    setToken(null);
    setRefreshToken(null);
    setUser(null);
    setCart(null);
    setWishlistIds(new Set());
    setFriendBadge(0);
    activitySeen.current = null;
  }, []);

  const loadUserData = useCallback(async () => {
    const [cartRes, wishRes] = await Promise.all([cartApi.get(), wishlistApi.list(0, 100)]);
    setCart(cartRes);
    setWishlistIds(new Set(wishRes.content.map((w) => w.gameId)));
  }, []);

  // Al arrancar: si hay token guardado, se valida pidiendo /users/me
  useEffect(() => {
    if (!getToken()) return;
    userApi
      .me()
      .then(async (me) => {
        setUser(me);
        await loadUserData();
      })
      .catch((e: unknown) => {
        // Solo un 401 invalida la sesión; un fallo de red o un 5xx no debe cerrarla
        if (e instanceof ApiError && e.status === 401) clearSession();
      })
      .finally(() => setBooting(false));
  }, [clearSession, loadUserData]);

  // Sesión caducada (el refresh de 7 días ya no vale) o inválida: se cierra y se manda al login
  useEffect(() => {
    setUnauthorizedHandler(() => {
      clearSession();
      notify(t('toast.sessionExpired'), 'error');
      navigate('/login');
    });
    return () => setUnauthorizedHandler(null);
  }, [clearSession, navigate, notify]);

  const startSession = useCallback(
    async (token: string, refreshToken: string, me: User) => {
      setToken(token);
      setRefreshToken(refreshToken);
      setUser(me);
      await loadUserData();
    },
    [loadUserData],
  );

  const login = useCallback(
    async (loginValue: string, password: string) => {
      const res = await authApi.login(loginValue, password);
      await startSession(res.accessToken, res.refreshToken, res.user);
    },
    [startSession],
  );

  const recoverAccount = useCallback(
    async (loginValue: string, password: string) => {
      const res = await authApi.recoverAccount(loginValue, password);
      await startSession(res.accessToken, res.refreshToken, res.user);
    },
    [startSession],
  );

  const register = useCallback<StoreValue['register']>(
    async (data) => {
      const res = await authApi.register(data);
      // Con la verificación de email activa no hay sesión: hay que pulsar el enlace del correo
      if ('verificationRequired' in res) return { verificationRequired: true, email: res.email };
      await startSession(res.accessToken, res.refreshToken, res.user);
      return { verificationRequired: false, email: res.user.email };
    },
    [startSession],
  );

  const verifyEmail = useCallback<StoreValue['verifyEmail']>(
    async (token, password) => {
      const res = await authApi.verifyEmail(token, password);
      await startSession(res.accessToken, res.refreshToken, res.user);
      return res.user;
    },
    [startSession],
  );

  const logout = useCallback(() => {
    // Recarga completa en la portada: React Router navega como transición y, si solo se limpiara
    // el estado, RequireAuth vería antes el usuario vacío y mandaría al login.
    const refreshToken = getRefreshToken();
    // Se revoca en el servidor sin esperar la respuesta; si falla, el refresh caducará solo
    if (refreshToken) authApi.logout(refreshToken).catch(() => undefined);
    setToken(null);
    setRefreshToken(null);
    window.location.assign('/');
  }, []);

  const requireLogin = useCallback(() => {
    if (getToken()) return true;
    navigate('/login', { state: { from: location.pathname + location.search } });
    return false;
  }, [location, navigate]);

  const refreshCart = useCallback(async () => {
    if (!getToken()) return;
    setCart(await cartApi.get());
  }, []);

  const addToCart = useCallback(
    async (gameId: number, title: string) => {
      if (!requireLogin()) return false;
      try {
        setCart(await cartApi.add(gameId));
        notify(t('toast.addedToCart', { title }));
        return true;
      } catch (e) {
        notifyError(e);
        return false;
      }
    },
    [notify, notifyError, requireLogin],
  );

  const removeFromCart = useCallback(
    async (gameId: number) => {
      try {
        setCart(await cartApi.remove(gameId));
      } catch (e) {
        notifyError(e);
      }
    },
    [notifyError],
  );

  const toggleWishlist = useCallback(
    async (gameId: number, title: string) => {
      if (!requireLogin()) return;
      const inList = wishlistIds.has(gameId);
      try {
        if (inList) await wishlistApi.remove(gameId);
        else await wishlistApi.add(gameId);
        setWishlistIds((prev) => {
          const next = new Set(prev);
          if (inList) next.delete(gameId);
          else next.add(gameId);
          return next;
        });
        notify(t(inList ? 'toast.removedFromWishlist' : 'toast.addedToWishlist', { title }));
      } catch (e) {
        notifyError(e);
      }
    },
    [notify, notifyError, requireLogin, wishlistIds],
  );

  const hasRole = useCallback((role: Role) => Boolean(user?.roles.includes(role)), [user]);

  // Solo los jugadores tienen amigos (estudios y admins no consultan)
  const isPlayer = Boolean(user) && !hasRole('ROLE_DEVELOPER') && !hasRole('ROLE_ADMIN');
  const userId = user?.id;

  const refreshFriendActivity = useCallback(async () => {
    if (!getToken() || !isPlayer) return;
    const reqId = ++activityReqId.current;
    activityInFlight.current = reqId;
    let activity;
    try {
      activity = await friendsApi.activity();
    } finally {
      if (activityInFlight.current === reqId) activityInFlight.current = 0;
    }
    // si la sesión se cerró o llegó otra consulta más reciente mientras tanto, esta respuesta ya no vale
    if (!getToken() || reqId !== activityReqId.current) return;
    const seen = activitySeen.current;
    activitySeen.current = seenOf(activity);
    setFriendBadge(activity.total);
    const news = seen ? friendToast(activity, seen) : null;
    if (news) notify(news.message, 'success', { label: t('toast.friendView'), to: news.to });
  }, [isPlayer, notify]);

  // Consulta al entrar, cada 30 s y al volver a la pestaña. Otra sesión empieza de cero: su primera consulta no avisa.
  useEffect(() => {
    activitySeen.current = null;
    activityReqId.current++;
    activityInFlight.current = 0;
    setFriendBadge(0);
    if (!isPlayer) return;
    const poll = () => refreshFriendActivity().catch(() => undefined);
    poll();
    // el intervalo no consulta con la pestaña oculta ni mientras otra consulta sigue en vuelo
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible' && activityInFlight.current === 0) poll();
    }, ACTIVITY_POLL_MS);
    const onVisible = () => {
      if (document.visibilityState === 'visible') poll();
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisible);
    };
    // refreshFriendActivity depende de isPlayer; userId reinicia la consulta con otra sesión
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isPlayer, userId]);

  const value = useMemo<StoreValue>(
    () => ({
      user, booting, hasRole, login, recoverAccount, register, verifyEmail, logout, setUser,
      cart, refreshCart, addToCart, removeFromCart,
      wishlistIds, toggleWishlist,
      friendBadge, refreshFriendActivity,
      toast, notify, notifyError, requireLogin,
    }),
    [user, booting, hasRole, login, recoverAccount, register, verifyEmail, logout, cart, refreshCart, addToCart, removeFromCart,
      wishlistIds, toggleWishlist, friendBadge, refreshFriendActivity, toast, notify, notifyError, requireLogin],
  );

  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>;
}

export function useStore() {
  const ctx = useContext(StoreContext);
  if (!ctx) throw new Error('useStore debe usarse dentro de <StoreProvider>');
  return ctx;
}
