// Profilo di un giocatore: header sempre visibile; libreria e amici solo se il backend li invia.
import { Link, useParams } from 'react-router';
import { friendsApi, userApi } from '@/api/endpoints';
import type { PublicProfile } from '@/api/types';
import UserAvatar from '@/components/UserAvatar';
import { Empty, ErrorState, Loading, PageHeader } from '@/components/ui';
import { useStore } from '@/context/StoreContext';
import { useI18n } from '@/i18n';
import { cardImage, formatDate, formatPlaytime } from '@/lib/format';
import { useAsync } from '@/lib/useAsync';

export default function Profile() {
  const { username = '' } = useParams();
  const { notify, notifyError } = useStore();
  const { t } = useI18n();
  const profile = useAsync(() => userApi.profile(username), [username]);
  const data = profile.data;

  if (profile.error) return <ErrorState error={profile.error} onRetry={profile.reload} />;
  if (!data) return <Loading />;

  const name = data.user.displayName || data.user.username;

  const act = async (action: () => Promise<unknown>, message: string) => {
    try {
      await action();
      notify(message);
      profile.reload();
    } catch (e) {
      notifyError(e);
    }
  };

  const friendButton = (p: PublicProfile) => {
    switch (p.relation) {
      case 'SELF':
        return <span className="muted">{t('profile.yourProfile')}</span>;
      case 'FRIENDS':
        return <span className="muted">{t('profile.isFriend')}</span>;
      case 'REQUEST_SENT':
        return <button className="secondary small" disabled>{t('friends.requestSent')}</button>;
      case 'REQUEST_RECEIVED':
        return (
          <button className="primary small" disabled={p.requestId === null}
            onClick={() => act(() => friendsApi.accept(p.requestId!), t('friends.acceptedToast', { name }))}>
            {t('friends.accept')}
          </button>
        );
      default:
        return (
          <button className="primary small"
            onClick={() => act(() => friendsApi.send(p.user.username), t('friends.sentToast', { name }))}>
            {t('friends.add')}
          </button>
        );
    }
  };

  return (
    <>
      <PageHeader eyebrow={t('profile.eyebrow')} title={name} />
      <section className="panel profile-head">
        <UserAvatar user={data.user} className="avatar big" />
        <div>
          <h3>{name}</h3>
          <p>@{data.user.username} · {t('profile.memberSince', { date: formatDate(data.memberSince) })}</p>
        </div>
        <div className="friend-actions">{friendButton(data)}</div>
      </section>

      {!data.visible && (
        <Empty icon="shield" title={t('profile.private', { name })} />
      )}

      {data.visible && (
        <>
          <h3 className="profile-subtitle">{t('profile.library')}</h3>
          {data.library?.length === 0 && <p className="muted">{t('profile.noGames')}</p>}
          <div className="library-grid">
            {data.library?.map((game) => (
              <article key={game.gameId} className="library-card">
                <Link to={`/games/${game.slug}`} className="cover tall">
                  {game.coverUrl && <img src={cardImage(game.coverUrl)} alt="" loading="lazy" />}
                </Link>
                <div className="library-info">
                  <h3><Link to={`/games/${game.slug}`}>{game.title}</Link></h3>
                  <p>{formatPlaytime(game.playtimeMinutes)}</p>
                </div>
              </article>
            ))}
          </div>

          <h3 className="profile-subtitle">{t('profile.friends')}</h3>
          {data.friends?.length === 0 && <p className="muted">{t('profile.noFriends')}</p>}
          <ul className="friend-list panel">
            {data.friends?.map((f) => (
              <li key={f.user.username} className="friend-row">
                <Link to={`/users/${f.user.username}`} className="friend-who">
                  <UserAvatar user={f.user} />
                  <span><b>{f.user.displayName || f.user.username}</b><small>@{f.user.username}</small></span>
                </Link>
              </li>
            ))}
          </ul>
        </>
      )}
    </>
  );
}
