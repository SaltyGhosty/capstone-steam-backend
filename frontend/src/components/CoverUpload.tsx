// Botón «Subir portada»: valida el archivo en el cliente (el servidor vuelve a validarlo) y lo manda.
import { useState, type ChangeEvent } from 'react';
import { uploadsApi } from '@/api/endpoints';
import type { GameDetail } from '@/api/types';
import { useStore } from '@/context/StoreContext';
import { useI18n } from '@/i18n';
import { useAsync } from '@/lib/useAsync';

const MAX_BYTES = 5 * 1024 * 1024;
const TYPES = ['image/jpeg', 'image/png', 'image/webp'];

/** true si el servidor tiene Cloudinary configurado; mientras carga o si falla, false (no se ofrece el botón). */
export function useCoverUploadEnabled(): boolean {
  const status = useAsync(() => uploadsApi.coverStatus(), []);
  return status.data?.enabled === true;
}

export default function CoverUpload({ upload, onUploaded }: {
  upload: (file: File) => Promise<GameDetail>;
  onUploaded: (game: GameDetail) => void;
}) {
  const { notify, notifyError } = useStore();
  const { t } = useI18n();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const choose = async (e: ChangeEvent<HTMLInputElement>) => {
    const input = e.target;
    const file = input.files?.[0];
    input.value = ''; // permite volver a elegir el mismo archivo
    if (!file) return;
    if (file.size > MAX_BYTES) return setError(t('cover.tooLarge'));
    if (file.type && !TYPES.includes(file.type)) return setError(t('cover.invalidType'));
    setError('');
    setBusy(true);
    try {
      const game = await upload(file);
      notify(t('cover.uploaded'));
      onUploaded(game);
    } catch (err) {
      notifyError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="cover-upload">
      <label className={busy ? 'secondary small file-button busy' : 'secondary small file-button'}>
        {busy ? t('cover.uploading') : t('cover.upload')}
        <input type="file" accept={TYPES.join(',')} disabled={busy} onChange={choose} />
      </label>
      {error && <small className="field-error" role="alert">{error}</small>}
    </div>
  );
}
