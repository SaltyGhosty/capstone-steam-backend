import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate, useSearchParams } from 'react-router';
import { useStore } from '@/context/StoreContext';
import { useI18n } from '@/i18n';
import { initials } from '@/lib/format';
import Icon, { type IconName } from './Icon';
import LanguageSwitcher from './LanguageSwitcher';

function SideLink({ to, icon, label, count, end }: { to: string; icon: IconName; label: string; count?: number; end?: boolean }) {
  return (
    <NavLink to={to} end={end} className={({ isActive }) => (isActive ? 'active' : undefined)} title={label}>
      <Icon name={icon} /> {label}
      {count !== undefined && <span className="nav-count">{count}</span>}
    </NavLink>
  );
}

function Brand({ className }: { className: string }) {
  const { t } = useI18n();
  return (
    <Link className={className} to="/" aria-label={t('nav.home')}>
      <span className="brand-mark"><span /></span><b>ARCADE</b>
    </Link>
  );
}

function roleLabel(roles: string[], t: ReturnType<typeof useI18n>['t']) {
  if (roles.includes('ROLE_ADMIN')) return t('role.admin');
  if (roles.includes('ROLE_DEVELOPER')) return t('role.developer');
  return t('role.player');
}

export default function Layout() {
  const { user, hasRole, cart, wishlistIds, logout, toast } = useStore();
  const { t } = useI18n();
  const navigate = useNavigate();
  const location = useLocation();
  const [params] = useSearchParams();
  const [query, setQuery] = useState('');
  const [menuOpen, setMenuOpen] = useState(false);
  const searchRef = useRef<HTMLInputElement>(null);

  // El buscador refleja ?q= cuando se está en el catálogo
  useEffect(() => {
    setQuery(location.pathname === '/browse' ? params.get('q') ?? '' : '');
  }, [location.pathname, params]);

  useEffect(() => setMenuOpen(false), [location.pathname]);

  // ⌘K / Ctrl+K enfoca el buscador
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        searchRef.current?.focus();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  const onSearch = (e: FormEvent) => {
    e.preventDefault();
    const next = new URLSearchParams(location.pathname === '/browse' ? params : undefined);
    if (query.trim()) next.set('q', query.trim());
    else next.delete('q');
    next.delete('page');
    navigate(`/browse?${next.toString()}`);
  };

  const cartCount = cart?.itemCount ?? 0;
  const name = user?.displayName || user?.username || '';

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <Brand className="brand" />
        <nav className="side-nav" aria-label={t('nav.main')}>
          <p>{t('nav.menu')}</p>
          <SideLink to="/" end icon="home" label={t('nav.discover')} />
          <SideLink to="/browse" icon="compass" label={t('nav.browse')} />
          <SideLink to="/library" icon="library" label={t('nav.library')} />
          <p>{t('nav.collection')}</p>
          <SideLink to="/wishlist" icon="heart" label={t('nav.wishlist')} count={user ? wishlistIds.size : undefined} />
          <SideLink to="/orders" icon="receipt" label={t('nav.orders')} />
          {hasRole('ROLE_DEVELOPER') && (<><p>{t('nav.studio')}</p><SideLink to="/developer" icon="code" label={t('nav.myGames')} /></>)}
          {hasRole('ROLE_ADMIN') && (<><p>{t('nav.admin')}</p><SideLink to="/admin" icon="shield" label={t('nav.moderation')} /></>)}
        </nav>
        <div className="side-bottom">
          <div className="promo-icon"><Icon name="gift" size={24} /></div>
          <p><strong>{t('nav.dealsTitle')}</strong><br /><span>{t('nav.dealsText')}</span></p>
          <Link to="/browse?onSale=true">{t('nav.dealsLink')}</Link>
        </div>
        <NavLink className={({ isActive }) => (isActive ? 'settings active' : 'settings')} to="/settings" title={t('nav.settings')}>
          <Icon name="settings" /> {t('nav.settings')}
        </NavLink>
      </aside>

      <main>
        <header className="topbar">
          <Brand className="mobile-brand" />
          <form className="search" role="search" onSubmit={onSearch}>
            <Icon name="search" />
            <input ref={searchRef} value={query} onChange={(e) => setQuery(e.target.value)}
              placeholder={t('nav.searchPlaceholder')} aria-label={t('nav.searchLabel')} />
            <kbd>Ctrl K</kbd>
          </form>
          <div className="account">
            <LanguageSwitcher />
            <Link className="cart-button" to="/cart" aria-label={t('nav.cartLabel', { count: cartCount })}>
              <Icon name="cart" /><span>{t('nav.cart')}</span>{cartCount > 0 && <b>{cartCount}</b>}
            </Link>
            {user ? (
              <div className="profile-wrap">
                <button className="profile" onClick={() => setMenuOpen((o) => !o)} aria-expanded={menuOpen}>
                  {user.avatarUrl
                    ? <img className="avatar" src={user.avatarUrl} alt="" />
                    : <span className="avatar">{initials(name)}</span>}
                  <span><b>{name}</b><small>{roleLabel(user.roles, t)}</small></span>
                  <Icon name="chevron" size={15} />
                </button>
                {menuOpen && (
                  <div className="menu" role="menu">
                    <Link to="/settings" role="menuitem"><Icon name="settings" size={16} /> {t('nav.settings')}</Link>
                    <Link to="/orders" role="menuitem"><Icon name="receipt" size={16} /> {t('nav.myOrders')}</Link>
                    <button onClick={logout} role="menuitem"><Icon name="logout" size={16} /> {t('nav.logout')}</button>
                  </div>
                )}
              </div>
            ) : (
              <>
                <Link className="ghost-link" to="/login">{t('nav.login')}</Link>
                <Link className="primary small" to="/register">{t('nav.register')}</Link>
              </>
            )}
          </div>
        </header>

        <nav className="mobile-nav" aria-label={t('nav.mobile')}>
          <NavLink to="/" end><Icon name="home" /> {t('nav.start')}</NavLink>
          <NavLink to="/browse"><Icon name="compass" /> {t('nav.browse')}</NavLink>
          <NavLink to="/library"><Icon name="library" /> {t('nav.library')}</NavLink>
          <NavLink to="/wishlist"><Icon name="heart" /> {t('nav.wishlist')}</NavLink>
          <NavLink to="/orders"><Icon name="receipt" /> {t('nav.orders')}</NavLink>
          <NavLink to="/settings"><Icon name="settings" /> {t('nav.settings')}</NavLink>
        </nav>

        <div className="content">
          <Outlet />
        </div>
      </main>

      {toast && (
        <div key={toast.id} className={`toast ${toast.kind}`} role="status">
          <span>{toast.kind === 'error' ? '!' : '✓'}</span>{toast.message}
        </div>
      )}
    </div>
  );
}
