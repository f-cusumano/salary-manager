import { describe, expect, it } from 'vitest';
import { ApiError, erroriDiCampo, esito, messaggioErrore } from './errors';

describe('esito', () => {
  it('restituisce i dati di una risposta riuscita', () => {
    expect(esito({ data: [1, 2], response: new Response(null, { status: 200 }) })).toEqual([1, 2]);
  });

  it('trasforma un Problem Details in ApiError leggibile', () => {
    const chiamata = () =>
      esito({
        error: {
          type: 'about:blank',
          title: 'Richiesta non valida',
          status: 400,
          detail: 'CCNL sconosciuto.',
        },
        response: new Response(null, { status: 400 }),
      });
    expect(chiamata).toThrow(ApiError);
    try {
      chiamata();
    } catch (errore) {
      expect((errore as ApiError).status).toBe(400);
      expect(messaggioErrore(errore)).toBe('CCNL sconosciuto.');
    }
  });
});

describe('messaggioErrore', () => {
  it('spiega quando il server non risponde', () => {
    expect(messaggioErrore(new TypeError('Failed to fetch'))).toMatch(/server non risponde/);
  });

  it('non espone dettagli tecnici di errori sconosciuti', () => {
    expect(messaggioErrore(new Error('stack interno'))).toBe('Si è verificato un errore inatteso.');
    expect(messaggioErrore(new ApiError(500, null))).toBe('Si è verificato un errore inatteso.');
  });
});

describe('erroriDiCampo', () => {
  function rifiuto(corpo: unknown): unknown {
    try {
      esito({ error: corpo, response: new Response(null, { status: 400 }) });
    } catch (errore) {
      return errore;
    }
    throw new Error('attesa una risposta di errore');
  }

  it('restituisce gli errori sui campi con i percorsi dello schema', () => {
    const errore = rifiuto({
      title: 'Richiesta non valida',
      status: 400,
      detail: '2 campi da correggere',
      errori: [
        { campo: 'totali.netto', messaggio: 'Indica il netto' },
        { campo: null, messaggio: 'Dati incoerenti' },
      ],
    });
    expect(erroriDiCampo(errore)).toEqual([
      { campo: 'totali.netto', messaggio: 'Indica il netto' },
      { campo: null, messaggio: 'Dati incoerenti' },
    ]);
    expect(messaggioErrore(errore)).toBe('2 campi da correggere');
  });

  it('ignora gli elementi malformati', () => {
    const errore = rifiuto({
      title: 'Richiesta non valida',
      errori: [null, 'testo', { campo: 3, messaggio: 'Campo generico' }, { campo: 'voci[0].descrizione' }],
    });
    expect(erroriDiCampo(errore)).toEqual([{ campo: null, messaggio: 'Campo generico' }]);
  });

  it('è vuoto senza errori sui campi o per errori non della API', () => {
    expect(erroriDiCampo(rifiuto({ title: 'Operazione non consentita', detail: 'Già presente' }))).toEqual(
      [],
    );
    expect(erroriDiCampo(rifiuto({ title: 'Richiesta non valida', errori: 'non una lista' }))).toEqual([]);
    expect(erroriDiCampo(new TypeError('Failed to fetch'))).toEqual([]);
  });
});
