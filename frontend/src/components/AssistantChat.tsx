import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { Link } from 'react-router';
import { ApiError } from '@/api/client';
import { assistantApi } from '@/api/endpoints';
import type { ChatMessage, GameSummary } from '@/api/types';
import { useStore } from '@/context/StoreContext';
import { useI18n } from '@/i18n';
import { cardImage, formatMoney } from '@/lib/format';
import Icon from './Icon';
import ReviewScoreBadge from './ReviewScoreBadge';

/** Cuántos mensajes viajan en cada petición (el backend acepta 10 como máximo). */
const MAX_HISTORY = 10;
const MAX_LENGTH = 500;

type Entry = ChatMessage & { games?: GameSummary[] };
type Notice = 'assistant.unavailableUi' | 'assistant.rateLimitedUi';

function MiniCard({ game, onOpen }: { game: GameSummary; onOpen: () => void }) {
  return (
    <Link className="assistant-card" to={`/games/${game.slug}`} onClick={onOpen}>
      {game.coverUrl ? <img src={cardImage(game.coverUrl)} alt="" loading="lazy" /> : <span className="assistant-cover-fallback" aria-hidden="true" />}
      <span className="assistant-card-info">
        <b>{game.title}</b>
        <span>{formatMoney(game.finalPrice)}</span>
        <ReviewScoreBadge game={game} compact />
      </span>
    </Link>
  );
}

/** Chat flotante que recomienda juegos del catálogo. La conversación vive en el estado: se pierde al recargar. */
export default function AssistantChat() {
  const { user } = useStore();
  const { t } = useI18n();
  const [open, setOpen] = useState(false);
  const [entries, setEntries] = useState<Entry[]>([]);
  const [text, setText] = useState('');
  const [thinking, setThinking] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (open) inputRef.current?.focus();
  }, [open]);

  useEffect(() => {
    const list = listRef.current;
    if (list) list.scrollTop = list.scrollHeight;
  }, [entries, thinking, notice, open]);

  if (!user) return null;

  const close = () => setOpen(false);

  const onKeyDown = (e: KeyboardEvent) => {
    if (e.key === 'Escape') close();
  };

  const send = async (e: FormEvent) => {
    e.preventDefault();
    const content = text.trim();
    if (!content || thinking) return;
    const history: Entry[] = [...entries, { role: 'user', content }];
    setEntries(history);
    setText('');
    setNotice(null);
    setThinking(true);
    try {
      const messages = history.slice(-MAX_HISTORY).map(({ role, content: c }) => ({ role, content: c.slice(0, MAX_LENGTH) }));
      const res = await assistantApi.chat(messages);
      setEntries((prev) => [...prev, { role: 'assistant', content: res.reply, games: res.games }]);
    } catch (err) {
      setNotice(err instanceof ApiError && err.status === 429 ? 'assistant.rateLimitedUi' : 'assistant.unavailableUi');
    } finally {
      setThinking(false);
      inputRef.current?.focus();
    }
  };

  return (
    <>
      {!open && (
        <button type="button" className="assistant-fab" onClick={() => setOpen(true)} aria-label={t('assistant.open')} title={t('assistant.open')}>
          <Icon name="chat" size={24} />
        </button>
      )}
      {open && (
        <section className="assistant-panel" role="dialog" aria-label={t('assistant.title')} onKeyDown={onKeyDown}>
          <header>
            <h2><Icon name="chat" size={18} /> {t('assistant.title')}</h2>
            <button type="button" className="icon-button" onClick={close} aria-label={t('assistant.close')}>
              <Icon name="close" size={16} />
            </button>
          </header>
          <div className="assistant-messages" ref={listRef} aria-live="polite">
            <p className="assistant-msg bot">{t('assistant.greeting')}</p>
            {entries.map((entry, i) => (
              <div key={i} className={`assistant-msg ${entry.role === 'user' ? 'me' : 'bot'}`}>
                <p>{entry.content}</p>
                {entry.games && entry.games.length > 0 && (
                  <div className="assistant-cards">
                    {entry.games.map((g) => <MiniCard key={g.id} game={g} onOpen={close} />)}
                  </div>
                )}
              </div>
            ))}
            {thinking && <p className="assistant-msg bot thinking">{t('assistant.thinking')}</p>}
            {notice && <p className="assistant-msg notice" role="alert">{t(notice)}</p>}
          </div>
          <form className="assistant-form" onSubmit={send}>
            <input ref={inputRef} value={text} onChange={(e) => setText(e.target.value)} maxLength={MAX_LENGTH}
              placeholder={t('assistant.placeholder')} aria-label={t('assistant.inputLabel')} autoComplete="off" />
            <button className="primary small" type="submit" disabled={thinking || !text.trim()}>{t('assistant.send')}</button>
          </form>
        </section>
      )}
    </>
  );
}
