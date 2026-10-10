import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { cedolinoManuale } from '../test/cedolinoManuale';
import { useUrlPdf } from '../hooks/useUrlPdf';
import { contestoHook } from '../test/contestoHook';
import { chiavi } from './chiavi';
import { erroriDiCampo, esito } from './errors';
import { invalidaDopoAcquisizione, invalidaDopoModificaFileInErrore } from './invalidazioni';
import {
  useAssociaCaricamento,
  useCaricamentiInErrore,
  useEliminaCedolino,
  useInserisciCedolinoManuale,
  useRimuoviAssociazione,
  useRimuoviCaricamento,
  useVerificaCedolinoManuale,
} from './queries';
import type { CaricamentoInErrore } from './types';

type Contesto = ReturnType<typeof contestoHook>;

async function fileInErrore({ api }: Contesto, nome: string): Promise<CaricamentoInErrore> {
  const file = esito(await api.GET('/api/v1/caricamenti/in-errore')).find(
    (c) => c.nomeFileOriginale === nome,
  );
  if (!file) {
    throw new Error(`${nome} non è tra i file in errore`);
  }
  return file;
}

function invalidata({ queryClient }: Contesto, chiave: readonly unknown[]): boolean | undefined {
  return queryClient.getQueryState(chiave)?.isInvalidated;
}

/** Riempie la cache come se le viste fossero gia' state caricate. */
function popolaCache({ queryClient }: Contesto) {
  const chiaviPopolate = [
    chiavi.persone,
    chiavi.rapporti(1),
    chiavi.rapporto(1),
    chiavi.cedolino(101),
    chiavi.caricamentiInErrore,
    chiavi.ccnl,
    chiavi.lotto('lotto-in-corso'),
  ];
  for (const chiave of chiaviPopolate) {
    queryClient.setQueryData(chiave, []);
  }
}

describe('invalidazioni', () => {
  it('dopo un’acquisizione rilegge anagrafiche, rapporti, cedolini e file in errore', async () => {
    const contesto = contestoHook();
    popolaCache(contesto);

    await invalidaDopoAcquisizione(contesto.queryClient);

    expect(invalidata(contesto, chiavi.persone)).toBe(true);
    expect(invalidata(contesto, chiavi.rapporti(1))).toBe(true);
    expect(invalidata(contesto, chiavi.rapporto(1))).toBe(true);
    expect(invalidata(contesto, chiavi.cedolino(101))).toBe(true);
    expect(invalidata(contesto, chiavi.caricamentiInErrore)).toBe(true);
    expect(invalidata(contesto, chiavi.ccnl)).toBe(false);
    expect(invalidata(contesto, chiavi.lotto('lotto-in-corso'))).toBe(false);
  });

  it('dopo un’associazione o una rimozione rilegge rapporti e file in errore', async () => {
    const contesto = contestoHook();
    popolaCache(contesto);

    await invalidaDopoModificaFileInErrore(contesto.queryClient);

    expect(invalidata(contesto, chiavi.rapporto(1))).toBe(true);
    expect(invalidata(contesto, chiavi.rapporti(1))).toBe(true);
    expect(invalidata(contesto, chiavi.caricamentiInErrore)).toBe(true);
    expect(invalidata(contesto, chiavi.persone)).toBe(false);
    expect(invalidata(contesto, chiavi.cedolino(101))).toBe(false);
  });
});

describe('query e modifiche dei cedolini caricati con errore', () => {
  it('elenca i file in errore', async () => {
    const { wrapper } = contestoHook();

    const { result } = renderHook(() => useCaricamentiInErrore(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.map((c) => c.nomeFileOriginale)).toEqual([
      'busta-scansione.pdf',
      'cedolino-protetto.pdf',
      'ristampa.pdf',
    ]);
  });

  it('associa e rimuove l’associazione rileggendo i rapporti', async () => {
    const contesto = contestoHook();
    const scansione = await fileInErrore(contesto, 'busta-scansione.pdf');
    const { result } = renderHook(
      () => ({ associa: useAssociaCaricamento(), rimuovi: useRimuoviAssociazione() }),
      {
        wrapper: contesto.wrapper,
      },
    );

    popolaCache(contesto);
    const associato = await result.current.associa.mutateAsync({
      caricamentoId: scansione.id,
      attribuzione: { rapportoId: 1, anno: 2026, mese: 13 },
    });
    expect(associato.attribuzione?.titolo).toBe('Tredicesima 2026');
    expect(invalidata(contesto, chiavi.rapporto(1))).toBe(true);
    expect(invalidata(contesto, chiavi.caricamentiInErrore)).toBe(true);

    popolaCache(contesto);
    const rimosso = await result.current.rimuovi.mutateAsync(scansione.id);
    expect(rimosso.attribuzione).toBeNull();
    expect(invalidata(contesto, chiavi.rapporti(1))).toBe(true);
  });

  it('la verifica riporta gli errori sui campi', async () => {
    const contesto = contestoHook();
    const ristampa = await fileInErrore(contesto, 'ristampa.pdf');
    const { result } = renderHook(() => useVerificaCedolinoManuale(), { wrapper: contesto.wrapper });

    const errore: unknown = await result.current
      .mutateAsync({
        caricamentoId: ristampa.id,
        dati: cedolinoManuale((d) => {
          d.totali.netto = null;
        }),
      })
      .catch((e: unknown) => e);

    expect(erroriDiCampo(errore)).toEqual([{ campo: 'totali.netto', messaggio: 'Indica il netto' }]);
  });

  it('l’inserimento a mano rilegge quanto cambia un’acquisizione e il lotto del file', async () => {
    const contesto = contestoHook();
    const ristampa = await fileInErrore(contesto, 'ristampa.pdf');
    const { result } = renderHook(() => useInserisciCedolinoManuale(), { wrapper: contesto.wrapper });
    popolaCache(contesto);
    contesto.queryClient.setQueryData(chiavi.lotto(ristampa.lotto), { lotto: ristampa.lotto });
    contesto.queryClient.setQueryData(chiavi.pdfCaricamento(ristampa.id), { nome: 'ristampa.pdf' });

    const caricamento = await result.current.mutateAsync({
      caricamentoId: ristampa.id,
      dati: cedolinoManuale(),
    });

    expect(caricamento).toMatchObject({ stato: 'ACQUISITO', personaId: 1, rapportoId: 1 });
    expect(invalidata(contesto, chiavi.persone)).toBe(true);
    expect(invalidata(contesto, chiavi.caricamentiInErrore)).toBe(true);
    expect(invalidata(contesto, chiavi.lotto(ristampa.lotto))).toBe(true);
    expect(contesto.queryClient.getQueryState(chiavi.pdfCaricamento(ristampa.id))).toBeUndefined();
  });

  it('con l’anteprima aperta il PDF del file inserito resta fino alla chiusura, senza richiederlo di nuovo', async () => {
    const contesto = contestoHook();
    const ristampa = await fileInErrore(contesto, 'ristampa.pdf');
    const { result, unmount } = renderHook(
      () => ({ anteprima: useUrlPdf(ristampa.id), inserimento: useInserisciCedolinoManuale() }),
      { wrapper: contesto.wrapper },
    );
    await waitFor(() => expect(result.current.anteprima.url).not.toBeNull());

    await act(() =>
      result.current.inserimento.mutateAsync({ caricamentoId: ristampa.id, dati: cedolinoManuale() }),
    );

    // il backend non conserva piu' il PDF: richiederlo di nuovo darebbe 404 sotto l'anteprima ancora aperta
    expect(result.current.anteprima.url).not.toBeNull();
    expect(result.current.anteprima.errore).toBeNull();
    expect(contesto.queryClient.getQueryState(chiavi.pdfCaricamento(ristampa.id))?.status).toBe('success');
    unmount();
    await waitFor(() =>
      expect(contesto.queryClient.getQueryState(chiavi.pdfCaricamento(ristampa.id))).toBeUndefined(),
    );
  });

  it('la rimozione di un file elimina anche il suo PDF dalla cache', async () => {
    const contesto = contestoHook();
    const protetto = await fileInErrore(contesto, 'cedolino-protetto.pdf');
    const { result } = renderHook(() => useRimuoviCaricamento(), { wrapper: contesto.wrapper });
    popolaCache(contesto);
    contesto.queryClient.setQueryData(chiavi.pdfCaricamento(protetto.id), { nome: 'cedolino-protetto.pdf' });

    expect(await result.current.mutateAsync(protetto.id)).toBe(protetto.id);

    expect(invalidata(contesto, chiavi.caricamentiInErrore)).toBe(true);
    expect(contesto.queryClient.getQueryState(chiavi.pdfCaricamento(protetto.id))).toBeUndefined();
    expect(contesto.backend.db.caricamenti.some((c) => c.id === protetto.id)).toBe(false);
  });

  it('l’eliminazione di un cedolino rilegge anche i file in errore', async () => {
    const contesto = contestoHook();
    const luglio = contesto.backend.db.rapporti.get(1)?.mensilita.find((r) => r.chiave === '2025.07');
    if (!luglio?.cedolinoId) {
      throw new Error('Luglio 2025 non è presente nei dati dimostrativi');
    }
    const { result } = renderHook(() => useEliminaCedolino(), { wrapper: contesto.wrapper });
    popolaCache(contesto);

    await result.current.mutateAsync(luglio.cedolinoId);

    // un file associato al mese eliminato torna da completare; se sparisce il rapporto perde l'associazione
    expect(invalidata(contesto, chiavi.caricamentiInErrore)).toBe(true);
    expect(invalidata(contesto, chiavi.rapporto(1))).toBe(true);
  });
});
