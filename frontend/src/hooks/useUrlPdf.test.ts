import { renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, esito } from '../api/errors';
import { contestoHook } from '../test/contestoHook';
import { useUrlPdf } from './useUrlPdf';

describe('URL del PDF conservato per l’anteprima', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function stubUrl() {
    let progressivo = 0;
    const creati: Blob[] = [];
    const revocati: string[] = [];
    // jsdom non implementa gli URL dei Blob
    vi.stubGlobal(
      'URL',
      class extends URL {
        static override createObjectURL = vi.fn((contenuto: Blob) => {
          creati.push(contenuto);
          return `blob:pdf-${++progressivo}`;
        });
        static override revokeObjectURL = vi.fn((url: string) => {
          revocati.push(url);
        });
      },
    );
    return { creati, revocati };
  }

  it('crea l’URL del PDF con il nome originale e lo revoca allo smontaggio', async () => {
    const { creati, revocati } = stubUrl();
    const { wrapper, api } = contestoHook();
    const [scansione] = esito(await api.GET('/api/v1/caricamenti/in-errore'));

    const { result, unmount } = renderHook(() => useUrlPdf(scansione?.id ?? 0), { wrapper });

    await waitFor(() => expect(result.current.url).toBe('blob:pdf-1'));
    expect(result.current.nome).toBe('busta-scansione.pdf');
    expect(creati[0]?.type).toBe('application/pdf');
    expect(revocati).toEqual([]);

    unmount();

    expect(revocati).toEqual(['blob:pdf-1']);
  });

  it('nel doppio montaggio di StrictMode resta un URL valido', async () => {
    const { revocati } = stubUrl();
    const { wrapper, api } = contestoHook();
    const [scansione] = esito(await api.GET('/api/v1/caricamenti/in-errore'));

    const { result } = renderHook(() => useUrlPdf(scansione?.id ?? 0), { wrapper, reactStrictMode: true });

    await waitFor(() => expect(result.current.url).not.toBeNull());
    expect(revocati).not.toContain(result.current.url);
  });

  it('riporta l’errore se il PDF non è disponibile', async () => {
    const { creati } = stubUrl();
    const { wrapper } = contestoHook();

    const { result } = renderHook(() => useUrlPdf(999), { wrapper });

    await waitFor(() => expect(result.current.errore).toBeInstanceOf(ApiError));
    expect((result.current.errore as ApiError).status).toBe(404);
    expect(result.current.url).toBeNull();
    expect(creati).toEqual([]);
  });
});
