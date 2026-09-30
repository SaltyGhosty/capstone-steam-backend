import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate, useSearchParams } from 'react-router';
import { useStore } from '@/context/StoreContext';
import { initials } from '@/lib/format';
import Icon, { type IconName } from './Icon';

function SideLink({ to, icon, label, count, end }: { to: string; icon: IconName; label: string; count?: number; end?: boolean }) {
  return (
    <NavLink to={to} end={end} className={({ isActive }) => (isActive ? 'active' : undefined)} title={label}>
      <Icon name={icon} /> {label}
      {count !== undefined && <span className="nav-count">{count}</span>}
    </NavLink>
  );
}

function Brand({ className }: { className: string }) {
  return (
    <Link className={className} to="/" aria-label="ARCADE, inicio">
      <span className="brand-mark"><span /></span><b>ARCADE</b>
    </Link>
  );
}

function roleLabel(roles: string[]) {
  if (roles.includes('ROLE_ADMIN')) return 'Administrador';
  if (roles.includes('ROLE_DEVELOPER')) return 'Developer';
  return 'Jugador';
}

export default function Layout() {
  const { user, hasRole, cart, wishlistIds, logout, toast } = useStore();
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
        <nav className="side-nav" aria-label="Navegación principal">
          <p>MENÚ</p>
          <SideLink to="/" end icon="home" label="Descubrir" />
          <SideLink to="/browse" icon="compass" label="Explorar" />
          <SideLink to="/library" icon="library" label="Biblioteca" />
          <p>COLECCIÓN</p>
          <SideLink to="/wishlist" icon="heart" label="Wishlist" count={user ? wishlistIds.size : undefined} />
          <SideLink to="/orders" icon="receipt" label="Pedidos" />
          {hasRole('ROLE_DEVELOPER') && (<><p>ESTUDIO</p><SideLink to="/developer" icon="code" label="Mis juegos" /></>)}
          {hasRole('ROLE_ADMIN') && (<><p>ADMIN</p><SideLink to="/admin" icon="shield" label="Moderación" /></>)}
        </nav>
        <div className="side-bottom">
          <div className="promo-icon"><Icon name="gift" size={24} /></div>
          <p><strong>Ofertas de la semana</strong><br /><span>Juegos rebajados por sus estudios</span></p>
          <Link to="/browse?onSale=true">Ver ofertas</Link>
        </div>
        <NavLink className={({ isActive }) => (isActive ? 'settings active' : 'settings')} to="/settings" title="Ajustes">
          <Icon name="settings" /> Ajustes
        </NavLink>
      </aside>

      <main>
        <header className="topbar">
          <Brand className="mobile-brand" />
          <form className="search" role="search" onSubmit={onSearch}>
            <Icon name="search" />
            <input ref={searchRef} value={query} onChange={(e) => setQuery(e.target.value)}
              placeholder="Busca juegos por título…" aria-label="Buscar juegos" />
            <kbd>Ctrl K</kbd>
          </form>
          <div className="account">
            <Link className="cart-button" to="/cart" aria-label={`Carrito con ${cartCount} juegos`}>
              <Icon name="cart" /><span>Carrito</span>{cartCount > 0 && <b>{cartCount}</b>}
            </Link>
            {user ? (
              <div className="profile-wrap">
                <button className="profile" onClick={() => setMenuOpen((o) => !o)} aria-expanded={menuOpen}>
                  {user.avatarUrl
                    ? <img className="avatar" src={user.avatarUrl} alt="" />
                    : <span className="avatar">{initials(name)}</span>}
                  <span><b>{name}</b><small>{roleLabel(user.roles)}</small></span>
                  <Icon name="chevron" size={15} />
                </button>
                {menuOpen && (
                  <div className="menu" role="menu">
                    <Link to="/settings" role="menuitem"><Icon name="settings" size={16} /> Ajustes</Link>
                    <Link to="/orders" role="menuitem"><Icon name="receipt" size={16} /> Mis pedidos</Link>
                    <button onClick={logout} role="menuitem"><Icon name="logout" size={16} /> Cerrar sesión</button>
                  </div>
                )}
              </div>
            ) : (
              <>
                <Link className="ghost-link" to="/login">Iniciar sesión</Link>
                <Link className="primary small" to="/register">Crear cuenta</Link>
              </>
            )}
          </div>
        </header>

        <nav className="mobile-nav" aria-label="Navegación móvil">
          <NavLink to="/" end><Icon name="home" /> Inicio</NavLink>
          <NavLink to="/browse"><Icon name="compass" /> Explorar</NavLink>
          <NavLink to="/library"><Icon name="library" /> Biblioteca</NavLink>
          <NavLink to="/wishlist"><Icon name="heart" /> Wishlist</NavLink>
          <NavLink to="/settings"><Icon name="settings" /> Ajustes</NavLink>
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
