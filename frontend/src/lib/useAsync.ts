import { useCallback, useEffect, useState, type DependencyList } from 'react';
import { useI18n } from '@/i18n';

/**
 * Carga datos al montar (y cuando cambian las deps o el idioma, porque la API responde en el idioma
 * de la interfaz). Ignora respuestas de peticiones ya obsoletas.
 */
export function useAsync<T>(loader: () => Promise<T>, deps: DependencyList) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  const [tick, setTick] = useState(0);
  const { lang } = useI18n();

  const load = useCallback(loader, deps);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    load()
      .then((result) => { if (!cancelled) setData(result); })
      .catch((e) => { if (!cancelled) setError(e); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [load, tick, lang]);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  return { data, setData, error, loading, reload };
}
