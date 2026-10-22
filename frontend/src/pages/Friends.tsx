// Amigos: buscador de jugadores, solicitudes recibidas y enviadas, y la lista de amigos.
import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router';
import { friendsApi, userApi } from '@/api/endpoints';
import type { SearchHit, UserCard } from '@/api/types';
import FriendChat from '@/components/FriendChat';
import Icon from '@/components/Icon';
import UserAvatar from '@/components/UserAvatar';
import { ErrorState, Loading, PageHeader } from '@/components/ui';
import { useStore } from '@/context/StoreContext';
import { useI18n } from '@/i18n';
import { formatDate } from '@/lib/format';
import { useAsync } from '@/lib/useAsync';

const SEARCH_DELAY_MS = 300;
const MIN_QUERY = 2;

const nameOf = (u: UserCard) => u.displayName || u.username;

function Person({ user, children }: { user: UserCard; children: ReactNode }) {
  return (
    <li className="friend-row">
      <Link to={`/users/${user.username}`} className="friend-who">
        <UserAvatar user={user} />
        <span><b>{nameOf(user)}</b><small>@{user.username}</small></span>
      </Link>
      <div className="friend-actions">{children}</div>
    </li>
  );
}

export default function Friends() {
  const { notify, notifyError } = useStore();
  const { t } = useI18n();
  const [query, setQuery] = useState('');
  const [hits, setHits] = useState<SearchHit[] | null>(null);
  const [confirming, setConfirming] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const friends = useAsync(() => friendsApi.list(), []);
  const requests = useAsync(() => friendsApi.requests(), []);
  const conversations = useAsync(() => friendsApi.conversations(), []);
  const [params, setParams] = useSearchParams();
  const chatWith = params.get('chat');
  const reloadConversations = conversations.reload;

  const term = query.trim();
  // Último término escrito: una respuesta de otro término más viejo se descarta
  const latestTerm = useRef(term);
  latestTerm.current = term;

  const runSearch = useCallback(async () => {
    if (term.length < MIN_QUERY) {
      setHits(null);
      return;
    }
    try {
      const found = await userApi.search(term);
      if (latestTerm.current === term) setHits(found);
    } catch (e) {
      if (latestTerm.current === term) notifyError(e);
    }
  }, [term, notifyError]);

  // Espera 300 ms tras la última tecla antes de buscar
  useEffect(() => {
    const timer = window.setTimeout(runSearch, SEARCH_DELAY_MS);
    return () => window.clearTimeout(timer);
  }, [runSearch]);

  const refresh = () => {
    friends.reload();
    requests.reload();
    void runSearch();
  };

  /** Ejecuta una acción, avisa y refresca las listas. */
  const act = async (action: () => Promise<unknown>, message: string) => {
    setBusy(true);
    try {
      await action();
      notify(message);
      setConfirming(null);
      refresh();
    } catch (e) {
      notifyError(e);
    } finally {
      setBusy(false);
    }
  };

  const hitButton = (hit: SearchHit) => {
    const name = nameOf(hit.user);
    switch (hit.relation) {
      case 'NONE':
        return (
          <button className="primary small" disabled={busy}
            onClick={() => act(() => friendsApi.send(hit.user.username), t('friends.sentToast', { name }))}>
            <Icon name="plus" size={14} /> {t('friends.add')}
          </button>
        );
      case 'REQUEST_SENT':
        return <button className="secondary small" disabled>{t('friends.requestSent')}</button>;
      case 'REQUEST_RECEIVED':
        return (
          <button className="primary small" disabled={busy || hit.requestId === null}
            onClick={() => act(() => friendsApi.accept(hit.requestId!), t('friends.acceptedToast', { name }))}>
            {t('friends.accept')}
          </button>
        );
      default:
        return <Link className="secondary small" to={`/users/${hit.user.username}`}>{t('friends.viewProfile')}</Link>;
    }
  };

  const openChat = (username: string | null) => setParams(username ? { chat: username } : {}, { replace: true });
  const chatConv = conversations.data?.find((c) => c.username === chatWith);
  const chatFriend = friends.data?.find((f) => f.user.username === chatWith)?.user;
  // Un ex-amigo solo sale en conversaciones (friend: false); sin dato se deja escribir y el servidor decide
  const writable = chatConv ? chatConv.friend : true;
  const chatName = chatConv ? chatConv.displayName || chatConv.username : chatFriend ? nameOf(chatFriend) : chatWith ?? '';

  return (
    <>
      <PageHeader eyebrow={t('friends.eyebrow')} title={t('friends.title')} />

      <section className="panel friend-section">
        <h3>{t('chat.title')}</h3>
        <div className={chatWith ? 'chat-layout open' : 'chat-layout'}>
          <div className="chat-list-col">
            {conversations.data?.length === 0 && <p className="muted">{t('chat.noConversations')}</p>}
            <ul className="chat-list">
              {conversations.data?.map((c) => (
                <li key={c.username}>
                  <button className={c.username === chatWith ? 'active' : ''} onClick={() => openChat(c.username)}>
                    <UserAvatar user={c} />
                    <span className="chat-preview">
                      <b>{c.displayName || c.username}</b>
                      <small>{c.lastMessage}</small>
                    </span>
                    {c.unread > 0 && <span className="chat-unread" aria-label={String(c.unread)}>{c.unread}</span>}
                  </button>
                </li>
              ))}
            </ul>
          </div>
          {chatWith && conversations.data && (
            <FriendChat username={chatWith} name={chatName} writable={writable}
              onBack={() => openChat(null)} onChanged={reloadConversations} />
          )}
        </div>
      </section>

      <section className="panel friend-section">
        <label className="field">
          <span>{t('friends.searchLabel')}</span>
          <input type="search" value={query} onChange={(e) => setQuery(e.target.value)}
            placeholder={t('friends.searchPlaceholder')} maxLength={50} />
        </label>
        {term.length < MIN_QUERY && <p className="muted">{t('friends.searchHint')}</p>}
        {hits?.length === 0 && term.length >= MIN_QUERY && <p className="muted">{t('friends.noResults')}</p>}
        {hits && hits.length > 0 && term.length >= MIN_QUERY && (
          <ul className="friend-list">
            {hits.map((hit) => <Person key={hit.user.username} user={hit.user}>{hitButton(hit)}</Person>)}
          </ul>
        )}
      </section>

      {requests.error ? <ErrorState error={requests.error} onRetry={requests.reload} /> : null}
      {requests.data && (
        <>
          <section className="panel friend-section">
            <h3>{t('friends.incoming')}</h3>
            {requests.data.incoming.length === 0
              ? <p className="muted">{t('friends.noIncoming')}</p>
              : (
                <ul className="friend-list">
                  {requests.data.incoming.map((r) => (
                    <Person key={r.id} user={r.user}>
                      <button className="primary small" disabled={busy}
                        onClick={() => act(() => friendsApi.accept(r.id), t('friends.acceptedToast', { name: nameOf(r.user) }))}>
                        {t('friends.accept')}
                      </button>
                      <button className="secondary small" disabled={busy}
                        onClick={() => act(() => friendsApi.reject(r.id), t('friends.rejectedToast'))}>
                        {t('friends.reject')}
                      </button>
                    </Person>
                  ))}
                </ul>
              )}
          </section>
          <section className="panel friend-section">
            <h3>{t('friends.outgoing')}</h3>
            {requests.data.outgoing.length === 0
              ? <p className="muted">{t('friends.noOutgoing')}</p>
              : (
                <ul className="friend-list">
                  {requests.data.outgoing.map((r) => (
                    <Person key={r.id} user={r.user}>
                      <button className="secondary small" disabled={busy}
                        onClick={() => act(() => friendsApi.cancel(r.id), t('friends.cancelledToast'))}>
                        {t('friends.cancel')}
                      </button>
                    </Person>
                  ))}
                </ul>
              )}
          </section>
        </>
      )}

      <section className="panel friend-section">
        <h3>{t('friends.yours')}</h3>
        {friends.loading && !friends.data && <Loading />}
        {friends.error ? <ErrorState error={friends.error} onRetry={friends.reload} /> : null}
        {friends.data?.length === 0 && <p className="muted">{t('friends.none')}</p>}
        {friends.data && friends.data.length > 0 && (
          <ul className="friend-list">
            {friends.data.map((f) => (
              <Person key={f.user.username} user={f.user}>
                {f.since && <small className="muted">{t('friends.since', { date: formatDate(f.since) })}</small>}
                <button className="secondary small" onClick={() => openChat(f.user.username)}>
                  {t('chat.open')}
                </button>
                {confirming === f.user.username ? (
                  <span className="confirm-inline" role="group" aria-label={t('friends.confirmRemove', { name: nameOf(f.user) })}>
                    <span>{t('friends.confirmRemove', { name: nameOf(f.user) })}</span>
                    <button className="danger small" disabled={busy}
                      onClick={() => act(() => friendsApi.remove(f.user.username), t('friends.removedToast', { name: nameOf(f.user) }))}>
                      {t('friends.confirmYes')}
                    </button>
                    <button className="secondary small" onClick={() => setConfirming(null)}>{t('common.cancel')}</button>
                  </span>
                ) : (
                  <button className="secondary small" onClick={() => setConfirming(f.user.username)}>
                    {t('friends.remove')}
                  </button>
                )}
              </Person>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
