import { describe, expect, it } from 'vitest';
import type { AttribuzioneCaricamento, CaricamentoInErrore, MotivoErrore } from '../api/types';
import { azioniPerCaricamento, chiaveMensilita, completabileAMano } from './useCedolinoManuale';

function fileInErrore(valori: Partial<CaricamentoInErrore>): CaricamentoInErrore {
  return {
    id: 1,
    lotto: 'lotto-1',
    nomeFileOriginale: 'busta.pdf',
    dimensione: 100,
    motivo: 'PDF_SENZA_TESTO',
    messaggio: 'Lettura non riuscita',
    pdfDisponibile: true,
    inElaborazione: false,
    attribuzione: null,
    creatoIl: '2026-10-01T09:00:00Z',
    ...valori,
  };
}

function attribuzione(valori: Partial<AttribuzioneCaricamento> = {}): AttribuzioneCaricamento {
  return {
    rapportoId: 1,
    personaId: 1,
    persona: 'Mario Rossi',
    azienda: 'Alfa Software S.r.l.',
    competenza: { chiave: '2023.05', tipo: 'ORDINARIA', anno: 2023, mese: 5, progressivoTfr: null },
    titolo: 'Maggio 2023',
    competenzaPresente: false,
    ...valori,
  };
}

const MOTIVI_CON_PDF: MotivoErrore[] = [
  'PDF_PROTETTO',
  'PDF_SENZA_TESTO',
  'LAYOUT_NON_RICONOSCIUTO',
  'LETTURA_NON_RIUSCITA',
  'DATI_INCOMPLETI',
  'PDF_TROPPO_GRANDE',
  'SERVIZIO_NON_DISPONIBILE',
  'INTERROTTO',
  'ERRORE_IMPREVISTO',
];

describe('azioni su un file in errore', () => {
  it.each(MOTIVI_CON_PDF)('con il PDF conservato e motivo %s propone l’azione giusta', (motivo) => {
    const azioni = azioniPerCaricamento(fileInErrore({ motivo }));
    const attesa =
      motivo === 'PDF_PROTETTO'
        ? 'RIPROVA_CON_PASSWORD'
        : ['SERVIZIO_NON_DISPONIBILE', 'INTERROTTO', 'ERRORE_IMPREVISTO'].includes(motivo)
          ? 'RIPROVA'
          : 'INSERISCI_A_MANO';
    expect(azioni.principale).toBe(attesa);
    expect(azioni.suggerimento).toBeNull();
    expect(azioni.bloccate).toBe(false);
    // l'inserimento a mano resta sempre disponibile, nel menu se non e' l'azione principale
    expect([azioni.principale, ...azioni.menu]).toContain('INSERISCI_A_MANO');
    expect(azioni.menu).toEqual(expect.arrayContaining(['ASSOCIA', 'SCARICA_PDF', 'RIMUOVI']));
    expect(azioni.menu).not.toContain(azioni.principale);
  });

  it('per il PDF protetto non offre una rilettura senza password nel menu', () => {
    expect(azioniPerCaricamento(fileInErrore({ motivo: 'PDF_PROTETTO' })).menu).toEqual([
      'INSERISCI_A_MANO',
      'ASSOCIA',
      'SCARICA_PDF',
      'RIMUOVI',
    ]);
    expect(azioniPerCaricamento(fileInErrore({ motivo: 'LAYOUT_NON_RICONOSCIUTO' })).menu).toEqual([
      'RIPROVA',
      'ASSOCIA',
      'SCARICA_PDF',
      'RIMUOVI',
    ]);
  });

  it.each<[MotivoErrore | null, string]>([
    ['PDF_NON_VALIDO', 'PDF_NON_VALIDO'],
    ['PDF_TROPPE_PAGINE', 'DIVIDI_PDF'],
    ['PIU_CEDOLINI', 'DIVIDI_PDF'],
    ['LETTURA_NON_RIUSCITA', 'PDF_NON_CONSERVATO'],
    [null, 'PDF_NON_CONSERVATO'],
  ])('senza PDF (motivo %s) si può solo caricare di nuovo il file', (motivo, suggerimento) => {
    expect(azioniPerCaricamento(fileInErrore({ motivo, pdfDisponibile: false }))).toEqual({
      principale: 'CARICA_DI_NUOVO',
      menu: ['ASSOCIA', 'RIMUOVI'],
      suggerimento,
      bloccate: false,
    });
  });

  it('un file già associato propone di cambiare mese o togliere l’associazione', () => {
    expect(azioniPerCaricamento(fileInErrore({ attribuzione: attribuzione() })).menu).toEqual([
      'RIPROVA',
      'CAMBIA_MESE',
      'RIMUOVI_ASSOCIAZIONE',
      'SCARICA_PDF',
      'RIMUOVI',
    ]);
    expect(
      azioniPerCaricamento(fileInErrore({ pdfDisponibile: false, attribuzione: attribuzione() })).menu,
    ).toEqual(['CAMBIA_MESE', 'RIMUOVI_ASSOCIAZIONE', 'RIMUOVI']);
  });

  it('se nel frattempo il mese associato è stato acquisito suggerisce di rimuovere il file', () => {
    const presente = attribuzione({ competenzaPresente: true });
    for (const pdfDisponibile of [true, false]) {
      expect(azioniPerCaricamento(fileInErrore({ pdfDisponibile, attribuzione: presente }))).toEqual({
        principale: 'RIMUOVI',
        menu: ['RIMUOVI_ASSOCIAZIONE'],
        suggerimento: 'MESE_PRESENTE',
        bloccate: false,
      });
    }
  });

  it('durante un nuovo tentativo dello stesso file blocca tutte le azioni', () => {
    for (const valori of [
      { motivo: 'PDF_PROTETTO' as const },
      { pdfDisponibile: false },
      { attribuzione: attribuzione({ competenzaPresente: true }) },
    ]) {
      expect(azioniPerCaricamento(fileInErrore({ ...valori, inElaborazione: true })).bloccate).toBe(true);
    }
  });
});

describe('file completabile a mano', () => {
  it('richiede il PDF conservato e nessun tentativo in corso', () => {
    expect(completabileAMano({ pdfDisponibile: true, inElaborazione: false })).toBe(true);
    expect(completabileAMano({ pdfDisponibile: true, inElaborazione: true })).toBe(false);
    expect(completabileAMano({ pdfDisponibile: false, inElaborazione: false })).toBe(false);
  });
});

describe('chiave di una mensilità', () => {
  it('coincide con Competenza.chiave per mesi, tredicesima e quattordicesima', () => {
    expect(chiaveMensilita(2023, 5)).toBe('2023.05');
    expect(chiaveMensilita(2023, 12)).toBe('2023.12');
    expect(chiaveMensilita(2023, 13)).toBe('2023.13');
    expect(chiaveMensilita(2023, 14)).toBe('2023.14');
  });
});
