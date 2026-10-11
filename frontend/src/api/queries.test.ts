import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { cedolinoManuale } from '../test/cedolinoManuale';
import { useUrlPdf } from '../hooks/useUrlPdf';
import { contestoHook } from '../test/contestoHook';
import { chiavi } from './chiavi';
import { erroriDiCampo, esito } from './errors';
import {
  invalidaDopoAcquisizione,
  invalidaDopoModificaFileInErrore,
  invalidaSeFileInErroreCambiato,
} from './invalidazioni';
import { ApiError } from './errors';
import {
  useAssociaCaricamento,
  useAziende,
  useCaricamentiInErrore,
  useEliminaCedolino,
  useInserisciCedolinoManuale,
  useLetture,
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
    chiavi.aziende,
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
    expect(invalidata(contesto, chiavi.aziende)).toBe(true);
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
    expect(invalidata(contesto, chiavi.aziende)).toBe(false);
    expect(invalidata(contesto, chiavi.cedolino(101))).toBe(false);
  });
});

describe('anagrafiche e letture per la precompilazione', () => {
  it('l’elenco delle aziende si legge solo se richiesto', async () => {
    const contesto = contestoHook();
    const { result, rerender } = renderHook(({ abilitato }) => useAziende(abilitato), {
      wrapper: contesto.wrapper,
      initialProps: { abilitato: false },
    });
    expect(result.current.fetchStatus).toBe('idle');

    rerender({ abilitato: true });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.map((a) => a.etichetta)).toEqual([
      'Alfa Software S.r.l.',
      'Beta Consulting S.p.A.',
      'Gamma Servizi S.r.l.',
    ]);
  });

  it('le letture su richiesta usano la cache delle viste (stesse chiavi)', async () => {
    const contesto = contestoHook();
    const { result } = renderHook(() => useLetture(), { wrapper: contesto.wrapper });

    const rapporti = await result.current.rapporti(1);
    const rapporto = await result.current.rapporto(rapporti[0]?.id ?? 0);
    const cedolinoId = rapporto.mensilita.find((r) => r.cedolinoId !== null)?.cedolinoId ?? 0;
    const cedolino = await result.current.cedolino(cedolinoId);

    expect(contesto.queryClient.getQueryData(chiavi.rapporti(1))).toBe(rapporti);
    expect(contesto.queryClient.getQueryData(chiavi.rapporto(rapporto.id))).toBe(rapporto);
    expect(contesto.queryClient.getQueryData(chiavi.cedolino(cedolinoId))).toBe(cedolino);
    // gia' in cache e non scaduto: nessuna nuova richiesta
    expect(await result.current.rapporti(1)).toBe(rapporti);
  });
});

describe('rifiuti che rivelano un file in errore cambiato', () => {
  it('404 e 409 rileggono i file in errore e cio’ che un’acquisizione cambia; gli altri errori no', async () => {
    for (const [errore, atteso] of [
      [new ApiError(404, null), true],
      [new ApiError(409, null), true],
      [new ApiError(400, null), false],
      [new TypeError('rete'), false],
    ] as const) {
      const contesto = contestoHook();
      popolaCache(contesto);

      await invalidaSeFileInErroreCambiato(contesto.queryClient, errore);

      expect(invalidata(contesto, chiavi.caricamentiInErrore)).toBe(atteso);
      expect(invalidata(contesto, chiavi.rapporto(1))).toBe(atteso);
      expect(invalidata(contesto, chiavi.ccnl)).toBe(false);
    }
  });

  it('le azioni su un file non più in errore rileggono l’elenco', async () => {
    const contesto = contestoHook();
    const ristampa = await fileInErrore(contesto, 'ristampa.pdf');
    const { result } = renderHook(
      () => ({
        verifica: useVerificaCedolinoManuale(),
        inserisci: useInserisciCedolinoManuale(),
        associa: useAssociaCaricamento(),
        rimuoviAssociazione: useRimuoviAssociazione(),
        rimuovi: useRimuoviCaricamento(),
      }),
      { wrapper: contesto.wrapper },
    );
    // il file viene inserito altrove (un'altra scheda): ogni azione successiva e' rifiutata con 409 o 404
    await result.current.inserisci.mutateAsync({ caricamentoId: ristampa.id, dati: cedolinoManuale() });
    const azioni: (() => Promise<unknown>)[] = [
      () => result.current.verifica.mutateAsync({ caricamentoId: ristampa.id, dati: cedolinoManuale() }),
      () => result.current.inserisci.mutateAsync({ caricamentoId: ristampa.id, dati: cedolinoManuale() }),
      () =>
        result.current.associa.mutateAsync({
          caricamentoId: ristampa.id,
          attribuzione: { rapportoId: 1, anno: 2026, mese: 13 },
        }),
      () => result.current.rimuoviAssociazione.mutateAsync(ristampa.id),
      () => result.current.rimuovi.mutateAsync(ristampa.id),
    ];

    for (const azione of azioni) {
      popolaCache(contesto);
      const errore: unknown = await azione().catch((e: unknown) => e);
      expect(errore).toBeInstanceOf(ApiError);
      expect([404, 409]).toContain((errore as ApiError).status);
      await waitFor(() => expect(invalidata(contesto, chiavi.caricamentiInErrore)).toBe(true));
    }
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
    expect(invalidata(contesto, chiavi.aziende)).toBe(true);
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
    // sparito il rapporto, puo' sparire anche l'azienda dall'elenco
    expect(invalidata(contesto, chiavi.aziende)).toBe(true);
  });
});
