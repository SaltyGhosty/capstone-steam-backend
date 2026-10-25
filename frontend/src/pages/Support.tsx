// Formulario de asistencia al cliente (/support). Público: sin sesión pide el email; con sesión el backend usa el de la cuenta.
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { ApiError } from '@/api/client';
import { supportApi } from '@/api/endpoints';
import { FieldError, Loading, PageHeader } from '@/components/ui';
import { useStore } from '@/context/StoreContext';
import { useI18n } from '@/i18n';

const SUBJECT_MAX = 150;
const MESSAGE_MAX = 3000;

export default function Support() {
  const { user, booting, notifyError } = useStore();
  const { t } = useI18n();
  const [email, setEmail] = useState('');
  const [subject, setSubject] = useState('');
  const [message, setMessage] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  // el email al que se envió la copia, para enseñarlo al terminar
  const [sentTo, setSentTo] = useState<string | null>(null);

  if (booting) return <Loading />;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setErrors({});
    try {
      await supportApi.send({ email: user ? undefined : email.trim(), subject: subject.trim(), message: message.trim() });
      setSentTo(user?.email ?? email.trim());
    } catch (err) {
      if (err instanceof ApiError) setErrors(err.fieldErrors);
      notifyError(err);
    } finally {
      setBusy(false);
    }
  };

  if (sentTo !== null) {
    return (
      <>
        <PageHeader eyebrow={t('support.eyebrow')} title={t('support.title')} />
        <section className="panel form" role="status">
          <h3>{t('support.sentTitle')}</h3>
          <p>{t('support.sentText', { email: sentTo })}</p>
          <div className="form-actions">
            <Link className="primary small" to="/">{t('common.backHome')}</Link>
            <button type="button" className="secondary small" onClick={() => { setSentTo(null); setSubject(''); setMessage(''); }}>
              {t('support.another')}
            </button>
          </div>
        </section>
      </>
    );
  }

  return (
    <>
      <PageHeader eyebrow={t('support.eyebrow')} title={t('support.title')} />
      <form className="panel form support-form" onSubmit={submit}>
        <p className="hint">{t('support.intro')}</p>
        {!user && (
          <label className="field">
            <span>{t('support.email')}</span>
            <input type="email" autoComplete="email" required maxLength={254} value={email} onChange={(e) => setEmail(e.target.value)} />
            <FieldError errors={errors} name="email" />
          </label>
        )}
        <label className="field">
          <span>{t('support.subject')}</span>
          <input required minLength={3} maxLength={SUBJECT_MAX} value={subject} onChange={(e) => setSubject(e.target.value)} />
          <FieldError errors={errors} name="subject" />
        </label>
        <div className="field">
          <label htmlFor="support-message">{t('support.message')}</label>
          <textarea id="support-message" required minLength={10} maxLength={MESSAGE_MAX} rows={8} value={message}
            onChange={(e) => setMessage(e.target.value)} />
          <small className="char-count" aria-live="polite">{t('support.counter', { count: message.length, max: MESSAGE_MAX })}</small>
          <FieldError errors={errors} name="message" />
        </div>
        <div className="form-actions">
          <button className="primary small" disabled={busy}>{busy ? t('support.sending') : t('support.send')}</button>
        </div>
      </form>
    </>
  );
}
