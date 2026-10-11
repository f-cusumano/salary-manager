import { describe, expect, it } from 'vitest';
import {
  etichettaCampoCedolino,
  formatAnzianita,
  formatData,
  formatDimensione,
  formatEtichettaRapporto,
  formatImporto,
  formatMese,
  formatMeseDiData,
  formatMensilita,
  formatNumero,
  formatPercentuale,
  formatPeriodo,
  formatQuantita,
  formatQuantitaBreve,
} from './format';

const NBSP = ' ';

describe('formatImporto', () => {
  it('usa il formato italiano con separatore delle migliaia anche a 4 cifre', () => {
    expect(formatImporto(1234.56)).toBe(`1.234,56${NBSP}€`);
    expect(formatImporto(1234567.8)).toBe(`1.234.567,80${NBSP}€`);
    expect(formatImporto(12)).toBe(`12,00${NBSP}€`);
  });

  it('gestisce importi negativi e valori assenti', () => {
    expect(formatImporto(-0.4)).toBe(`-0,40${NBSP}€`);
    expect(formatImporto(null)).toBe('—');
    expect(formatImporto(undefined)).toBe('—');
    expect(formatImporto(Number.NaN)).toBe('—');
  });
});

describe('formatNumero e formatPercentuale', () => {
  it('formatta decimali e percentuali', () => {
    expect(formatNumero(1234.5)).toBe('1.234,5');
    expect(formatNumero(2.123456, 5)).toBe('2,12346');
    expect(formatPercentuale(0.23)).toBe(`23%`);
    expect(formatPercentuale(null)).toBe('—');
  });
});

describe('formatQuantita', () => {
  it("declina l'unità al singolare e al plurale", () => {
    expect(formatQuantita(12.5, 'GIORNI')).toBe('12,5 giorni');
    expect(formatQuantita(1, 'GIORNI')).toBe('1 giorno');
    expect(formatQuantita(1, 'ORE')).toBe('1 ora');
    expect(formatQuantita(32, 'ORE')).toBe('32 ore');
    expect(formatQuantita(null, 'ORE')).toBe('—');
  });

  it('ha una forma compatta per i badge', () => {
    expect(formatQuantitaBreve(12.5, 'GIORNI')).toBe('12,5 g');
    expect(formatQuantitaBreve(8, 'ORE')).toBe('8 h');
  });
});

describe('mesi e date', () => {
  it('scrive il mese per esteso con iniziale maiuscola', () => {
    expect(formatMese('2024-01')).toBe('Gennaio 2024');
    expect(formatMese('2026-12')).toBe('Dicembre 2026');
    expect(formatMese(null)).toBe('—');
    expect(formatMese('non-valido')).toBe('non-valido');
  });

  it('formatta date complete e mese di una data', () => {
    expect(formatData('2024-01-05')).toBe('5 Gennaio 2024');
    expect(formatMeseDiData('2027-03-01')).toBe('Marzo 2027');
  });

  it('intitola una mensilità indicata come anno e mese 1..14, come il backend', () => {
    expect(formatMensilita(2023, 5)).toBe('Maggio 2023');
    expect(formatMensilita(2023, 12)).toBe('Dicembre 2023');
    expect(formatMensilita(2023, 13)).toBe('Tredicesima 2023');
    expect(formatMensilita(2023, 14)).toBe('Quattordicesima 2023');
  });
});

describe('periodo del rapporto', () => {
  it('distingue rapporto concluso e in corso', () => {
    expect(formatPeriodo({ da: '2024-01', a: '2026-02' })).toBe('da Gennaio 2024 a Febbraio 2026');
    expect(formatPeriodo({ da: '2024-01', a: null })).toBe('da Gennaio 2024');
    expect(formatPeriodo({ da: null, a: '2026-02' })).toBe('fino a Febbraio 2026');
    expect(formatPeriodo({ da: null, a: null })).toBeNull();
  });

  it("compone l'etichetta della riga azienda", () => {
    expect(formatEtichettaRapporto('Alfa Software S.r.l.', { da: '2024-01', a: '2026-02' })).toBe(
      'Alfa Software S.r.l. (da Gennaio 2024 a Febbraio 2026)',
    );
    expect(formatEtichettaRapporto('Alfa Software S.r.l.', { da: '2024-01', a: null })).toBe(
      'Alfa Software S.r.l. (da Gennaio 2024)',
    );
    expect(formatEtichettaRapporto('Alfa Software S.r.l.', { da: null, a: null })).toBe(
      'Alfa Software S.r.l.',
    );
  });
});

describe('formatAnzianita', () => {
  it('segue gli esempi della specifica', () => {
    expect(formatAnzianita(2, 2)).toBe('2 anni e 2 mesi');
    expect(formatAnzianita(2, 0)).toBe('2 anni');
    expect(formatAnzianita(1, 1)).toBe('1 anno e 1 mese');
    expect(formatAnzianita(0, 5)).toBe('5 mesi');
    expect(formatAnzianita(0, 0)).toBe('meno di un mese');
  });
});

describe('formatDimensione', () => {
  it("sceglie l'unità adatta", () => {
    expect(formatDimensione(512)).toBe('512 B');
    expect(formatDimensione(850 * 1024)).toBe('850 KB');
    expect(formatDimensione(1.25 * 1024 * 1024)).toBe('1,3 MB');
  });
});

describe('etichettaCampoCedolino', () => {
  it('traduce i percorsi noti dello schema', () => {
    expect(etichettaCampoCedolino('datiContrattuali.codiceCnel')).toBe('codice CNEL del contratto');
    expect(etichettaCampoCedolino('datoreLavoro.partitaIva')).toBe('partita IVA dell’azienda');
  });

  it("per i percorsi sconosciuti usa l'ultimo segmento in parole", () => {
    expect(etichettaCampoCedolino('dipendente.indirizzoResidenza')).toBe('indirizzo residenza');
    expect(etichettaCampoCedolino('voci[3].codiceVoce')).toBe('codice voce');
    expect(etichettaCampoCedolino('ccnl')).toBe('ccnl');
  });
});
