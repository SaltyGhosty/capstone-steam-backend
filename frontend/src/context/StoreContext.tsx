// Estado global de la sesión: usuario y token, carrito, ids de la wishlist y avisos (toast).
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { ApiError, getRefreshToken, getToken, setRefreshToken, setToken, setUnauthorizedHandler } from '@/api/client';
import { authApi, cartApi, userApi, wishlistApi } from '@/api/endpoints';
import type { Cart, RegisterRequest, Role, User } from '@/api/types';
import { t } from '@/i18n';

type ToastKind = 'success' | 'error';
interface Toast { id: number; message: string; kind: ToastKind }

interface StoreValue {
  user: User | null;
  /** true mientras se comprueba el token guardado al cargar la página */
  booting: boolean;
  hasRole: (role: Role) => boolean;
  login: (login: string, password: string) => Promise<void>;
  /** verificationRequired: la cuenta existe pero no hay sesión hasta verificar el email. */
  register: (data: RegisterRequest) => Promise<{ verificationRequired: boolean; email: string }>;
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

  toast: Toast | null;
  notify: (message: string, kind?: ToastKind) => void;
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

  const notify = useCallback((message: string, kind: ToastKind = 'success') => {
    window.clearTimeout(toastTimer.current);
    setToast({ id: Date.now(), message, kind });
    toastTimer.current = window.setTimeout(() => setToast(null), 2800);
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

  const value = useMemo<StoreValue>(
    () => ({
      user, booting, hasRole, login, register, verifyEmail, logout, setUser,
      cart, refreshCart, addToCart, removeFromCart,
      wishlistIds, toggleWishlist,
      toast, notify, notifyError, requireLogin,
    }),
    [user, booting, hasRole, login, register, verifyEmail, logout, cart, refreshCart, addToCart, removeFromCart,
      wishlistIds, toggleWishlist, toast, notify, notifyError, requireLogin],
  );

  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>;
}

export function useStore() {
  const ctx = useContext(StoreContext);
  if (!ctx) throw new Error('useStore debe usarse dentro de <StoreProvider>');
  return ctx;
}
