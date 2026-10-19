import type { UserCard } from '@/api/types';
import { initials } from '@/lib/format';

/** Avatar de otro jugador: su imagen o, si no tiene, las iniciales de su nombre. */
export default function UserAvatar({ user, className = 'avatar' }: { user: UserCard; className?: string }) {
  return user.avatarUrl
    ? <img className={className} src={user.avatarUrl} alt="" />
    : <span className={className}>{initials(user.displayName || user.username)}</span>;
}
