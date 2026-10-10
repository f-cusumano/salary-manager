import { describe, expect, it } from 'vitest';
import type { Caricamento, LottoCaricamento } from '../api/types';
import { caricamentiInEvidenza, personaDelLotto } from './useCaricamento';

function caricamento(valori: Partial<Caricamento>): Caricamento {
  return {
    id: 1,
    lotto: 'lotto-1',
    nomeFileOriginale: 'cedolino.pdf',
    dimensione: 100,
    stato: 'ERRORE',
    messaggio: null,
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
