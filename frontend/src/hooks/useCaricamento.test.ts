import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { esito } from '../api/errors';
import { rileggiCaricamento } from '../api/queries';
import type { Caricamento, LottoCaricamento } from '../api/types';
import { contestoHook } from '../test/contestoHook';
import { caricamentiInEvidenza, personaDelLotto, useCaricamento } from './useCaricamento';

function caricamento(valori: Partial<Caricamento>): Caricamento {
  return {
    id: 1,
    lotto: 'lotto-1',
    nomeFileOriginale: 'cedolino.pdf',
    dimensione: 100,
    stato: 'ERRORE',
    messaggio: null,
    motivo: null,
    pdfDisponibile: false,
    cedolinoId: null,
    cedolinoEsistenteId: null,
    personaId: null,
    rapportoId: null,
    creatoIl: '2026-10-01T09:00:00Z',
    aggiornatoIl: '2026-10-01T09:00:00Z',
    ...valori,
  };
}

function lotto(...caricamenti: Caricamento[]): LottoCaricamento {
  return { lotto: 'lotto-1', completato: true, caricamenti };
}

describe('persona da selezionare dopo un caricamento', () => {
  it('preferisce la persona del primo file acquisito', () => {
    expect(
      personaDelLotto(
        lotto(
          caricamento({ stato: 'DUPLICATO', personaId: 7, rapportoId: 70 }),
          caricamento({ stato: 'ACQUISITO', personaId: 3, rapportoId: 30 }),
          caricamento({ stato: 'ACQUISITO', personaId: 4, rapportoId: 40 }),
        ),
      ),
    ).toBe(3);
  });

  it('senza acquisiti usa la persona di un duplicato o di un aggiuntivo', () => {
    expect(
      personaDelLotto(
        lotto(
          caricamento({ stato: 'ERRORE' }),
          caricamento({ stato: 'AGGIUNTIVO_NON_GESTITO', personaId: 5, rapportoId: 50 }),
        ),
      ),
    ).toBe(5);
  });

  it('non seleziona nessuno se nessun file è attribuito', () => {
    expect(personaDelLotto(lotto(caricamento({ stato: 'ERRORE' })))).toBeNull();
  });
});

describe('ordine dei file nel lotto', () => {
  it('durante l’elaborazione mantiene l’ordine di invio', () => {
    const inCorso = {
      ...lotto(caricamento({ id: 1, stato: 'ACQUISITO' }), caricamento({ id: 2, stato: 'IN_CODA' })),
      completato: false,
    };
    expect(caricamentiInEvidenza(inCorso).map((c) => c.id)).toEqual([1, 2]);
  });

  it('a lotto concluso mette prima errori, aggiuntivi e duplicati', () => {
    const concluso = {
      ...lotto(
        caricamento({ id: 1, stato: 'ACQUISITO' }),
        caricamento({ id: 2, stato: 'DUPLICATO' }),
        caricamento({ id: 3, stato: 'ACQUISITO' }),
        caricamento({ id: 4, stato: 'ERRORE' }),
        caricamento({ id: 5, stato: 'AGGIUNTIVO_NON_GESTITO' }),
      ),
      completato: true,
    };
    expect(caricamentiInEvidenza(concluso).map((c) => c.id)).toEqual([4, 5, 2, 1, 3]);
  });
});

describe('lotto seguito dopo una rilettura', () => {
  it('mostra subito il lotto ricevuto, lo segue in polling e notifica la fine una sola volta', async () => {
    const { wrapper, api } = contestoHook();
    const protetto = esito(await api.GET('/api/v1/caricamenti/in-errore')).find(
      (c) => c.nomeFileOriginale === 'cedolino-protetto.pdf',
    );
    const lotto = await rileggiCaricamento(api, protetto?.id ?? 0, 'parola-segreta');
    const completato = vi.fn();
    const { result } = renderHook(() => useCaricamento(completato, 10), { wrapper });

    act(() => result.current.segui(lotto));

    expect(result.current.lotto).toEqual(lotto);
    expect(result.current.inElaborazione).toBe(true);
    await waitFor(() => expect(result.current.lotto?.completato).toBe(true));
    expect(result.current.lotto?.caricamenti[0]?.stato).toBe('ACQUISITO');
    await waitFor(() => expect(completato).toHaveBeenCalledTimes(1));
    expect(completato).toHaveBeenCalledWith(
      expect.objectContaining({ lotto: lotto.lotto, completato: true }),
    );
  });
});
