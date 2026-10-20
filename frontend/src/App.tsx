import { Link, Route, Routes } from 'react-router';
import Layout from '@/components/Layout';
import { Empty, RequireAuth } from '@/components/ui';
import { useI18n } from '@/i18n';
import Admin from '@/pages/Admin';
import { Login, Register } from '@/pages/Auth';
import Browse from '@/pages/Browse';
import Cart from '@/pages/Cart';
import { Library, Wishlist } from '@/pages/Collection';
import { DeveloperGameForm, DeveloperGames } from '@/pages/Developer';
import Demos from '@/pages/Demos';
import Discover from '@/pages/Discover';
import { CheckEmail, VerifyEmail } from '@/pages/EmailVerification';
import Friends from '@/pages/Friends';
import GameDetail from '@/pages/GameDetail';
import { OrderDetail, OrderList } from '@/pages/Orders';
import Profile from '@/pages/Profile';
import Settings from '@/pages/Settings';

function NotFound() {
  const { t } = useI18n();
  return (
    <Empty icon="compass" title={t('common.notFound')}>
      <Link className="primary small" to="/">{t('common.backHome')}</Link>
    </Empty>
  );
}

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Discover />} />
        <Route path="browse" element={<Browse />} />
        <Route path="demos" element={<Demos />} />
        <Route path="games/:slug" element={<GameDetail />} />
        <Route path="login" element={<Login />} />
        <Route path="register" element={<Register />} />
        <Route path="check-email" element={<CheckEmail />} />
        <Route path="verify-email" element={<VerifyEmail />} />

        <Route path="library" element={<RequireAuth><Library /></RequireAuth>} />
        <Route path="wishlist" element={<RequireAuth><Wishlist /></RequireAuth>} />
        <Route path="cart" element={<RequireAuth><Cart /></RequireAuth>} />
        <Route path="orders" element={<RequireAuth><OrderList /></RequireAuth>} />
        <Route path="orders/:id" element={<RequireAuth><OrderDetail /></RequireAuth>} />
        <Route path="friends" element={<RequireAuth><Friends /></RequireAuth>} />
        <Route path="users/:username" element={<RequireAuth><Profile /></RequireAuth>} />
        <Route path="settings" element={<RequireAuth><Settings /></RequireAuth>} />

        <Route path="developer" element={<RequireAuth role="ROLE_DEVELOPER"><DeveloperGames /></RequireAuth>} />
        <Route path="developer/games/:id" element={<RequireAuth role="ROLE_DEVELOPER"><DeveloperGameForm /></RequireAuth>} />
        <Route path="admin" element={<RequireAuth role="ROLE_ADMIN"><Admin /></RequireAuth>} />

        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}
