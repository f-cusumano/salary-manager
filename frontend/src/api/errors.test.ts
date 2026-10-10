import { describe, expect, it } from 'vitest';
import { ApiError, esito, messaggioErrore } from './errors';

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
