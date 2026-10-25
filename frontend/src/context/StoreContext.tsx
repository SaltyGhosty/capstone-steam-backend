// Stato globale della sessione: utente e token, carrello, id della wishlist e avvisi (toast).
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { ApiError, getRefreshToken, getToken, setRefreshToken, setToken, setUnauthorizedHandler } from '@/api/client';
import { authApi, cartApi, friendsApi, userApi, wishlistApi } from '@/api/endpoints';
import type { Cart, FriendActivity, RegisterRequest, Role, User } from '@/api/types';
import { t } from '@/i18n';

type ToastKind = 'success' | 'error';
interface ToastAction { label: string; to: string }
interface Toast { id: number; message: string; kind: ToastKind; action?: ToastAction }

/** Ogni quanto si consulta l'attività degli amici (anche al ritorno sulla scheda). */
const ACTIVITY_POLL_MS = 30_000;
const TOAST_MS = 2800;
const TOAST_WITH_ACTION_MS = 6000;

/** Quanto già visto nella consultazione precedente, per sapere cosa è nuovo. */
interface ActivitySeen { requestIds: Set<number>; unread: Map<string, number> }

const seenOf = (a: FriendActivity): ActivitySeen => ({
  requestIds: new Set(a.incomingRequests.map((r) => r.id)),
  unread: new Map(a.unread.map((u) => [u.username, u.count])),
});

/** L'avviso di ciò che è cambiato dalla consultazione precedente: uno solo, specifico o riassuntivo. */
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
  /** true mentre si controlla il token salvato al caricamento della pagina */
  booting: boolean;
  hasRole: (role: Role) => boolean;
  login: (login: string, password: string) => Promise<void>;
  /** verificationRequired: l'account esiste ma non c'è sessione finché non si verifica l'email. */
  register: (data: RegisterRequest) => Promise<{ verificationRequired: boolean; email: string }>;
  /** Annulla la cancellazione in sospeso dell'account e avvia la sessione. */
  recoverAccount: (login: string, password: string) => Promise<void>;
  /** Riscatta il link dell'email e avvia la sessione. */
  verifyEmail: (token: string, password: string) => Promise<User>;
  logout: () => void;
  setUser: (user: User) => void;

  cart: Cart | null;
  refreshCart: () => Promise<void>;
  addToCart: (gameId: number, title: string) => Promise<boolean>;
  removeFromCart: (gameId: number) => Promise<void>;

  wishlistIds: Set<number>;
  toggleWishlist: (gameId: number, title: string) => Promise<void>;

  /** Richieste ricevute + messaggi non letti: il numero di «Amici». */
  friendBadge: number;
  refreshFriendActivity: () => Promise<void>;

  toast: Toast | null;
  /** Con `action` l'avviso porta un link e dura di più. */
  notify: (message: string, kind?: ToastKind, action?: ToastAction) => void;
  notifyError: (error: unknown) => void;
  /** Porta al login e poi torna alla pagina corrente */
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
  // null = ancora nessuna consultazione in questa sessione: la prima non avvisa
  const activitySeen = useRef<ActivitySeen | null>(null);
  // numero dell'ultima consultazione lanciata (le risposte precedenti si scartano) e quella ancora in volo (0 = nessuna)
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

  // All'avvio: se c'è un token salvato, lo si valida chiedendo /users/me
  useEffect(() => {
    if (!getToken()) return;
    userApi
      .me()
      .then(async (me) => {
        setUser(me);
        await loadUserData();
      })
      .catch((e: unknown) => {
        // Solo un 401 invalida la sessione; un errore di rete o un 5xx non deve chiuderla
        if (e instanceof ApiError && e.status === 401) clearSession();
      })
      .finally(() => setBooting(false));
  }, [clearSession, loadUserData]);

  // Sessione scaduta (il refresh di 7 giorni non vale più) o non valida: si chiude e si manda al login
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
      // Con la verifica email attiva non c'è sessione: bisogna cliccare il link dell'email
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
    // Ricarica completa sulla home: React Router naviga come transizione e, se si svuotasse solo
    // lo stato, RequireAuth vedrebbe prima l'utente vuoto e manderebbe al login.
    const refreshToken = getRefreshToken();
    // Si revoca sul server senza aspettare la risposta; se fallisce, il refresh scadrà da solo
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

  // Solo i giocatori hanno amici (studi e admin non consultano)
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
    // se la sessione si è chiusa o è arrivata una consultazione più recente nel frattempo, questa risposta non vale più
    if (!getToken() || reqId !== activityReqId.current) return;
    const seen = activitySeen.current;
    activitySeen.current = seenOf(activity);
    setFriendBadge(activity.total);
    const news = seen ? friendToast(activity, seen) : null;
    if (news) notify(news.message, 'success', { label: t('toast.friendView'), to: news.to });
  }, [isPlayer, notify]);

  // Consulta all'ingresso, ogni 30 s e al ritorno sulla scheda. Un'altra sessione riparte da zero: la sua prima consultazione non avvisa.
  useEffect(() => {
    activitySeen.current = null;
    activityReqId.current++;
    activityInFlight.current = 0;
    setFriendBadge(0);
    if (!isPlayer) return;
    const poll = () => refreshFriendActivity().catch(() => undefined);
    poll();
    // l'intervallo non consulta con la scheda nascosta né mentre un'altra consultazione è ancora in volo
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
    // refreshFriendActivity dipende da isPlayer; userId riavvia la consultazione con un'altra sessione
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
