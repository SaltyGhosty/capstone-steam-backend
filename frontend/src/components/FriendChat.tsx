// Conversazione uno a uno con un amico: cronologia, polling ogni 4 s e campo di invio.
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { friendsApi } from '@/api/endpoints';
import type { ChatMessageItem } from '@/api/types';
import { useStore } from '@/context/StoreContext';
import { useI18n } from '@/i18n';

const POLL_MS = 4000;
const PAGE = 30;
const MAX_LENGTH = 1000;

const time = (iso: string) =>
  new Date(iso).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });

/** Unisce i messaggi nuovi a quelli già presenti, senza id ripetuti e dal più vecchio al più nuovo. */
const merge = (old: ChatMessageItem[], incoming: ChatMessageItem[]) => {
  const seen = new Set(old.map((m) => m.id));
  const fresh = incoming.filter((m) => !seen.has(m.id));
  return fresh.length === 0 ? old : [...old, ...fresh].sort((a, b) => a.id - b.id);
};

interface Props {
  username: string;
  name: string;
  /** false con un ex amico: si può solo leggere. */
  writable: boolean;
  onBack: () => void;
  /** Segnala che qualcosa è cambiato (inviato o letto) per aggiornare la lista delle conversazioni. */
  onChanged: () => void;
}

export default function FriendChat({ username, name, writable, onBack, onChanged }: Props) {
  const { notifyError, refreshFriendActivity } = useStore();
  const { t } = useI18n();
  const [messages, setMessages] = useState<ChatMessageItem[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const listRef = useRef<HTMLDivElement>(null);
  // Si scende in fondo solo quando arrivano messaggi in basso, non quando si caricano quelli precedenti
  const stickBottom = useRef(true);
  // Id già mostrati: servono a sapere se la consultazione porta nuovi messaggi altrui
  const known = useRef(new Set<number>());

  const markRead = useCallback(async () => {
    try {
      await friendsApi.markRead(username);
      await refreshFriendActivity();
      onChanged();
    } catch { /* un errore nel segnare come letto non interrompe la conversazione */ }
  }, [username, refreshFriendActivity, onChanged]);
  const markReadRef = useRef(markRead);
  markReadRef.current = markRead;

  // All'apertura: ultimi 30, scendere in fondo e segnare come letto
  useEffect(() => {
    let cancelled = false;
    setMessages([]);
    setHasMore(false);
    setDraft('');
    known.current = new Set();
    stickBottom.current = true;
    friendsApi.messages(username, undefined, PAGE).then((latest) => {
      if (cancelled) return;
      latest.forEach((m) => known.current.add(m.id));
      setMessages([...latest].reverse());
      setHasMore(latest.length === PAGE);
      void markReadRef.current();
    }).catch((e) => { if (!cancelled) notifyError(e); });
    return () => { cancelled = true; };
  }, [username, notifyError]);

  // Consultazione dei nuovi ogni 4 s, solo con la scheda visibile
  useEffect(() => {
    let cancelled = false;
    const timer = window.setInterval(async () => {
      if (document.visibilityState !== 'visible') return;
      try {
        const latest = await friendsApi.messages(username, undefined, PAGE);
        if (cancelled) return;
        const fresh = latest.filter((m) => !known.current.has(m.id));
        if (fresh.length === 0) return;
        fresh.forEach((m) => known.current.add(m.id));
        stickBottom.current = true;
        setMessages((old) => merge(old, fresh));
        if (fresh.some((m) => !m.mine)) void markReadRef.current();
      } catch { /* la consultazione successiva riprova */ }
    }, POLL_MS);
    return () => { cancelled = true; window.clearInterval(timer); };
  }, [username]);

  useLayoutEffect(() => {
    const el = listRef.current;
    if (el && stickBottom.current) el.scrollTop = el.scrollHeight;
  }, [messages]);

  const loadOlder = async () => {
    const oldest = messages[0]?.id;
    if (oldest === undefined) return;
    try {
      const older = await friendsApi.messages(username, oldest, PAGE);
      older.forEach((m) => known.current.add(m.id));
      stickBottom.current = false;
      setMessages((old) => merge(old, older));
      setHasMore(older.length === PAGE);
    } catch (e) {
      notifyError(e);
    }
  };

  const text = draft.trim();
  const send = async () => {
    if (!text || sending || !writable) return;
    setSending(true);
    try {
      const sent = await friendsApi.sendMessage(username, text);
      known.current.add(sent.id);
      stickBottom.current = true;
      setMessages((old) => merge(old, [sent]));
      setDraft('');
      onChanged();
    } catch (e) {
      notifyError(e);
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="chat-pane">
      <div className="chat-head">
        <button className="secondary small chat-back" onClick={onBack}>{t('chat.back')}</button>
        <b>{name}</b>
      </div>
      <div className="chat-messages" ref={listRef} role="log" aria-label={t('chat.messagesLabel', { name })}>
        {hasMore && (
          <button className="secondary small chat-older" onClick={loadOlder}>{t('chat.loadOlder')}</button>
        )}
        {messages.length === 0 && <p className="muted">{t('chat.empty')}</p>}
        {messages.map((m) => (
          <div key={m.id} className={m.mine ? 'chat-msg mine' : 'chat-msg'}>
            <p>{m.body}</p>
            <small>{time(m.createdAt)}</small>
          </div>
        ))}
      </div>
      {!writable && <p className="muted chat-readonly">{t('chat.readOnly')}</p>}
      <div className="chat-compose">
        <textarea value={draft} rows={2} maxLength={MAX_LENGTH} disabled={!writable}
          aria-label={t('chat.inputLabel')} placeholder={t('chat.placeholder')}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              void send();
            }
          }} />
        <div className="chat-send">
          <small className="muted">{draft.length}/{MAX_LENGTH}</small>
          <button className="primary small" disabled={!writable || sending || !text} onClick={() => void send()}>
            {t('chat.send')}
          </button>
        </div>
      </div>
    </div>
  );
}
