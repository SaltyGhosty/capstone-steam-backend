import { Link, Route, Routes } from 'react-router';
import Layout from '@/components/Layout';
import { Empty, RequireAuth } from '@/components/ui';
import Admin from '@/pages/Admin';
import { Login, Register } from '@/pages/Auth';
import Browse from '@/pages/Browse';
import Cart from '@/pages/Cart';
import { Library, Wishlist } from '@/pages/Collection';
import { DeveloperGameForm, DeveloperGames } from '@/pages/Developer';
import Discover from '@/pages/Discover';
import GameDetail from '@/pages/GameDetail';
import { OrderDetail, OrderList } from '@/pages/Orders';
import Settings from '@/pages/Settings';

function NotFound() {
  return (
    <Empty icon="compass" title="Esta página no existe">
      <Link className="primary small" to="/">Volver al inicio</Link>
    </Empty>
  );
}

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Discover />} />
        <Route path="browse" element={<Browse />} />
        <Route path="games/:slug" element={<GameDetail />} />
        <Route path="login" element={<Login />} />
        <Route path="register" element={<Register />} />

        <Route path="library" element={<RequireAuth><Library /></RequireAuth>} />
        <Route path="wishlist" element={<RequireAuth><Wishlist /></RequireAuth>} />
        <Route path="cart" element={<RequireAuth><Cart /></RequireAuth>} />
        <Route path="orders" element={<RequireAuth><OrderList /></RequireAuth>} />
        <Route path="orders/:id" element={<RequireAuth><OrderDetail /></RequireAuth>} />
        <Route path="settings" element={<RequireAuth><Settings /></RequireAuth>} />

        <Route path="developer" element={<RequireAuth role="ROLE_DEVELOPER"><DeveloperGames /></RequireAuth>} />
        <Route path="developer/games/:id" element={<RequireAuth role="ROLE_DEVELOPER"><DeveloperGameForm /></RequireAuth>} />
        <Route path="admin" element={<RequireAuth role="ROLE_ADMIN"><Admin /></RequireAuth>} />

        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}
