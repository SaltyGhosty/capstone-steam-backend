import type { UserCard } from '@/api/types';
import { initials } from '@/lib/format';

/** Avatar di un altro giocatore: la sua immagine o, se non ce l'ha, le iniziali del nome. */
export default function UserAvatar({ user, className = 'avatar' }: { user: UserCard; className?: string }) {
  return user.avatarUrl
    ? <img className={className} src={user.avatarUrl} alt="" />
    : <span className={className}>{initials(user.displayName || user.username)}</span>;
}
