import { describe, expect, it } from 'vitest';
import type { AttribuzioneCaricamento, CaricamentoInErrore, MotivoErrore } from '../api/types';
import { creaDbMock } from '../mocks/dati';
import {
  azioniPerCaricamento,
  chiaveMensilita,
  codiceFiscaleValido,
  compilatiSezione,
  completabileAMano,
  controlliCoerenza,
  DATA_INCOMPLETA,
  daAssociazione,
  daCedolinoRiferimento,
  daRapporto,
  erroriBloccanti,
  indiciRighe,
  partitaIvaValida,
  percorsoForm,
  rateoCopiatoVuoto,
  rateoVuoto,
  richiestaDa,
  rigaDiRiferimento,
  senzaErroriRisolti,
  sezioneDi,
  titoloPrevisto,
  unisciSeVuoti,
  valoriVuoti,
  voceCopiataVuota,
  voceVuota,
  type ValoriCedolinoManuale,
} from './useCedolinoManuale';

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

// ------------------------------------------------------------------ modulo di inserimento a mano (dati INVENTATI)

/** Oggi fissato: l'anno massimo del periodo e' l'anno successivo (2027). */
const OGGI = new Date(2026, 9, 10);

/** Modulo minimo valido: Giuseppe Verdi presso Delta Servizi, Agosto 2025. */
function valoriValidi(modifica: (v: ValoriCedolinoManuale) => void = () => undefined): ValoriCedolinoManuale {
  const v = valoriVuoti();
  v.periodo = { ...v.periodo, tipo: 'ORDINARIA', mese: '8', anno: 2025 };
  v.dipendente = { ...v.dipendente, codiceFiscale: 'VRDGPP90C15L219G', nome: 'Giuseppe', cognome: 'Verdi' };
  v.datoreLavoro.ragioneSociale = 'Delta Servizi S.r.l.';
  v.totali = { ...v.totali, totaleCompetenze: 2000, totaleTrattenute: 500, netto: 1500 };
  modifica(v);
  return v;
}

function voce(valori: Partial<ReturnType<typeof voceVuota>>) {
  return { ...voceVuota(), ...valori };
}

describe('richiesta dai valori del modulo', () => {
  it('un modulo vuoto diventa una richiesta di soli null, senza righe e senza TFR', () => {
    const richiesta = richiestaDa(valoriVuoti());
    expect(richiesta.dipendente).toEqual({
      codiceFiscale: null,
      nome: null,
      cognome: null,
      dataNascita: null,
      codiceDipendente: null,
    });
    expect(richiesta.periodo).toEqual({
      anno: null,
      mese: null,
      tipo: null,
      dataElaborazione: null,
      meseVariabili: null,
    });
    expect(Object.values(richiesta.totali).every((x) => x === null)).toBe(true);
    expect(richiesta.retribuzione.superminimoAssorbibile).toBeNull();
    expect(richiesta.voci).toEqual([]);
    expect(richiesta.ratei).toEqual([]);
    expect(richiesta.tfr).toBeNull();
    expect(richiesta.buoniPasto).toEqual({
      presenti: false,
      numero: null,
      valoreUnitario: null,
      importo: null,
    });
    expect(richiesta.contieneTredicesima).toBe(false);
  });

  it('converte testi, numeri (anche intermedi della digitazione), scelte e mese delle variabili', () => {
    const richiesta = richiestaDa(
      valoriValidi((v) => {
        v.dipendente.nome = '  Giuseppe ';
        v.dipendente.codiceDipendente = '   ';
        v.totali.arrotondamento = '0.';
        v.totali.lordo = '2100.5';
        v.periodo.meseVariabili = '2025-07';
        v.retribuzione.superminimoAssorbibile = 'SI';
      }),
    );
    expect(richiesta.dipendente.nome).toBe('Giuseppe');
    expect(richiesta.dipendente.codiceDipendente).toBeNull();
    expect(richiesta.periodo).toMatchObject({
      anno: 2025,
      mese: 8,
      tipo: 'ORDINARIA',
      meseVariabili: '2025-07',
    });
    expect(richiesta.totali).toMatchObject({ arrotondamento: 0, lordo: 2100.5, netto: 1500 });
    expect(richiesta.retribuzione.superminimoAssorbibile).toBe(true);
    expect(
      richiestaDa(valoriValidi((v) => (v.periodo.meseVariabili = '2025-'))).periodo.meseVariabili,
    ).toBeNull();
    expect(
      richiestaDa(valoriValidi((v) => (v.retribuzione.superminimoAssorbibile = 'NO'))).retribuzione
        .superminimoAssorbibile,
    ).toBe(false);
  });

  it('non invia le righe vuote di voci e ratei (la sola categoria non basta)', () => {
    const richiesta = richiestaDa(
      valoriValidi((v) => {
        v.voci = [
          voce({ categoria: 'TRATTENUTA_FISCALE' }),
          voce({ descrizione: 'Retribuzione ordinaria', importoCompetenza: 2000 }),
          voce({}),
        ];
        v.ratei = [rateoVuoto(), { ...rateoVuoto('FERIE', 'GIORNI'), maturato: 2.17 }];
      }),
    );
    expect(richiesta.voci).toEqual([
      {
        codice: null,
        descrizione: 'Retribuzione ordinaria',
        categoria: 'COMPETENZA',
        quantita: null,
        unita: null,
        base: null,
        importoCompetenza: 2000,
        importoTrattenuta: null,
      },
    ]);
    expect(richiesta.ratei).toEqual([
      {
        tipo: 'FERIE',
        unita: 'GIORNI',
        residuoAnnoPrecedente: null,
        maturato: 2.17,
        goduto: null,
        saldo: null,
      },
    ]);
  });

  it('il TFR è null solo se tutti i suoi campi sono vuoti', () => {
    expect(richiestaDa(valoriValidi()).tfr).toBeNull();
    const tfr = richiestaDa(valoriValidi((v) => (v.tfr.quotaMese = 150))).tfr;
    expect(tfr).toMatchObject({ quotaMese: 150, fondo31DicembreAnnoPrecedente: null });
  });

  it('senza buoni pasto non invia gli importi; il tipo imposta i flag di tredicesima e quattordicesima', () => {
    const richiesta = richiestaDa(
      valoriValidi((v) => {
        v.buoniPasto = { presenti: false, numero: 20, valoreUnitario: 8, importo: 160 };
        v.periodo.tipo = 'TREDICESIMA';
      }),
    );
    expect(richiesta.buoniPasto).toEqual({
      presenti: false,
      numero: null,
      valoreUnitario: null,
      importo: null,
    });
    expect(richiesta.contieneTredicesima).toBe(true);
    expect(richiesta.contieneQuattordicesima).toBe(false);
    const conBuoni = richiestaDa(
      valoriValidi((v) => (v.buoniPasto = { ...v.buoniPasto, presenti: true, numero: 20 })),
    );
    expect(conBuoni.buoniPasto).toEqual({ presenti: true, numero: 20, valoreUnitario: null, importo: null });
  });
});

describe('percorsi degli errori nel modulo', () => {
  it('converte gli indici della richiesta nei percorsi del modulo', () => {
    expect(percorsoForm('totali.netto')).toBe('totali.netto');
    expect(percorsoForm('voci[3].descrizione')).toBe('voci.3.descrizione');
    expect(percorsoForm('ratei[0]')).toBe('ratei.0');
    expect(percorsoForm(null)).toBeNull();
  });

  it('riporta gli indici delle righe inviate a quelli del modulo, che contiene anche le righe vuote', () => {
    const valori = valoriValidi((v) => {
      v.voci = [voce({}), voce({ descrizione: 'A' }), voce({}), voce({ descrizione: 'B' })];
      v.ratei = [rateoVuoto(), { ...rateoVuoto('ROL', 'ORE'), goduto: 4 }];
    });
    const indici = indiciRighe(valori);
    expect(indici).toEqual({ voci: [1, 3], ratei: [1] });
    expect(percorsoForm('voci[1].importoCompetenza', indici)).toBe('voci.3.importoCompetenza');
    expect(percorsoForm('ratei[0]', indici)).toBe('ratei.1');
  });
});

describe('codici di controllo', () => {
  it('verifica il carattere di controllo del codice fiscale e la cifra della partita IVA', () => {
    expect(codiceFiscaleValido('VRDGPP90C15L219G')).toBe(true);
    expect(codiceFiscaleValido('VRDGPP90C15L219X')).toBe(false);
    expect(codiceFiscaleValido('VRDGPP90C15L2')).toBe(false);
    expect(partitaIvaValida('01234567897')).toBe(true);
    expect(partitaIvaValida('01234567890')).toBe(false);
    expect(partitaIvaValida('00000000000')).toBe(false);
  });
});

type Modifica = (v: ValoriCedolinoManuale) => void;

describe('errori bloccanti (come il backend)', () => {
  it('un modulo completo non ha errori', () => {
    expect(erroriBloccanti(valoriValidi(), [], OGGI)).toEqual([]);
  });

  it.each<[string, Modifica, string, string]>([
    ['tipo mancante', (v) => (v.periodo.tipo = null), 'periodo.tipo', 'Indica il tipo di cedolino'],
    ['anno mancante', (v) => (v.periodo.anno = ''), 'periodo.anno', "Indica l'anno (dal 1900 al 2027)"],
    [
      'anno troppo vecchio',
      (v) => (v.periodo.anno = 1899),
      'periodo.anno',
      "Indica l'anno (dal 1900 al 2027)",
    ],
    ['anno futuro', (v) => (v.periodo.anno = 2028), 'periodo.anno', "Indica l'anno (dal 1900 al 2027)"],
    [
      'mese mancante',
      (v) => (v.periodo.mese = null),
      'periodo.mese',
      'Indica il mese (da 1 a 12): tredicesima e quattordicesima si indicano con il tipo',
    ],
    [
      'mese delle variabili incompleto',
      (v) => (v.periodo.meseVariabili = '-07'),
      'periodo.meseVariabili',
      'Indica mese e anno delle variabili',
    ],
    [
      'data fuori dagli anni ammessi',
      (v) => (v.periodo.dataElaborazione = '1850-01-31'),
      'periodo.dataElaborazione',
      'Data non valida (anno dal 1900 al 2999)',
    ],
    [
      'codice fiscale mancante',
      (v) => (v.dipendente.codiceFiscale = ' '),
      'dipendente.codiceFiscale',
      'Indica il codice fiscale del dipendente',
    ],
    [
      'codice fiscale con il carattere di controllo sbagliato',
      (v) => (v.dipendente.codiceFiscale = 'VRDGPP90C15L219X'),
      'dipendente.codiceFiscale',
      'Codice fiscale non valido: controlla i 16 caratteri',
    ],
    ['nome mancante', (v) => (v.dipendente.nome = ''), 'dipendente.nome', 'Indica il nome'],
    [
      'cognome di soli spazi',
      (v) => (v.dipendente.cognome = '   '),
      'dipendente.cognome',
      'Indica il cognome',
    ],
    [
      'nome troppo lungo',
      (v) => (v.dipendente.nome = 'a'.repeat(101)),
      'dipendente.nome',
      'Testo troppo lungo (massimo 100 caratteri)',
    ],
    [
      'carattere di controllo nel testo',
      (v) => (v.dipendente.nome = 'Giu\u0007seppe'),
      'dipendente.nome',
      'Il testo contiene caratteri non ammessi',
    ],
    [
      'data di nascita nel futuro',
      (v) => (v.dipendente.dataNascita = '2026-10-11'),
      'dipendente.dataNascita',
      'La data di nascita è nel futuro',
    ],
    [
      'ragione sociale senza lettere né cifre',
      (v) => (v.datoreLavoro.ragioneSociale = ' - '),
      'datoreLavoro.ragioneSociale',
      'Indica la ragione sociale',
    ],
    [
      'partita IVA corta',
      (v) => (v.datoreLavoro.partitaIva = '0123456'),
      'datoreLavoro.partitaIva',
      'Partita IVA non valida: deve avere 11 cifre',
    ],
    [
      'codice fiscale dell’azienda malformato',
      (v) => (v.datoreLavoro.codiceFiscale = 'ABC123'),
      'datoreLavoro.codiceFiscale',
      "Codice fiscale dell'azienda non valido",
    ],
    [
      'cessazione prima dell’assunzione',
      (v) => {
        v.datiContrattuali.dataAssunzione = '2022-03-01';
        v.datiContrattuali.dataCessazione = '2021-12-31';
      },
      'datiContrattuali.dataCessazione',
      "La data di cessazione precede l'assunzione",
    ],
    [
      'totale competenze mancante',
      (v) => (v.totali.totaleCompetenze = ''),
      'totali.totaleCompetenze',
      'Indica il totale competenze',
    ],
    [
      'totale trattenute mancante',
      (v) => (v.totali.totaleTrattenute = ''),
      'totali.totaleTrattenute',
      'Indica il totale trattenute',
    ],
    ['netto mancante', (v) => (v.totali.netto = ''), 'totali.netto', 'Indica il netto'],
    [
      'totale competenze negativo',
      (v) => (v.totali.totaleCompetenze = -1),
      'totali.totaleCompetenze',
      "Indica l'importo senza segno",
    ],
    [
      'importo di 9 cifre intere (i dati calcolati non entrerebbero nelle colonne)',
      (v) => (v.totali.lordo = 1e8),
      'totali.lordo',
      'Valore fuori dai limiti (massimo 8 cifre intere e 5 decimali)',
    ],
    [
      'importo con più di 5 decimali',
      (v) => (v.retribuzione.pagaOraria = 15.123456),
      'retribuzione.pagaOraria',
      'Valore fuori dai limiti (massimo 8 cifre intere e 5 decimali)',
    ],
    [
      'importo minuscolo scritto in forma esponenziale',
      (v) => (v.tfr.quotaMese = 1e-7),
      'tfr.quotaMese',
      'Valore fuori dai limiti (massimo 8 cifre intere e 5 decimali)',
    ],
    [
      'data con l’anno di 5 cifre (il campo del browser lo ammette)',
      (v) => (v.dipendente.dataNascita = '19800-01-01'),
      'dipendente.dataNascita',
      'Data non valida (anno dal 1900 al 2999)',
    ],
    [
      'data inesistente',
      (v) => (v.datiContrattuali.dataAssunzione = '2023-02-30'),
      'datiContrattuali.dataAssunzione',
      'Data non valida (anno dal 1900 al 2999)',
    ],
    [
      'data incompleta (il browser non la interpreta)',
      (v) => (v.periodo.dataElaborazione = DATA_INCOMPLETA),
      'periodo.dataElaborazione',
      'Data incompleta o inesistente: indica giorno, mese e anno',
    ],
    [
      'data di nascita incompleta (mai confrontata con oggi)',
      (v) => (v.dipendente.dataNascita = DATA_INCOMPLETA),
      'dipendente.dataNascita',
      'Data incompleta o inesistente: indica giorno, mese e anno',
    ],
    [
      'numero non interpretabile',
      (v) => (v.presenze.oreLavorate = '1,5'),
      'presenze.oreLavorate',
      'Numero non valido',
    ],
    [
      'importo di una voce negativo',
      (v) => (v.voci = [voce({ descrizione: 'Rimborso', importoTrattenuta: -10 })]),
      'voci[0].importoTrattenuta',
      "Indica l'importo senza segno",
    ],
    [
      'voce senza descrizione',
      (v) => (v.voci = [voce({ importoCompetenza: 100 })]),
      'voci[0].descrizione',
      'Indica la descrizione della voce',
    ],
    [
      'voce senza categoria',
      (v) => (v.voci = [voce({ descrizione: 'Straordinario', categoria: null })]),
      'voci[0].categoria',
      'Indica la categoria della voce',
    ],
    [
      'codice della voce troppo lungo',
      (v) => (v.voci = [voce({ descrizione: 'Premio', codice: 'X'.repeat(31) })]),
      'voci[0].codice',
      'Testo troppo lungo (massimo 30 caratteri)',
    ],
    [
      'unità della voce troppo lunga',
      (v) => (v.voci = [voce({ descrizione: 'Premio', unita: 'u'.repeat(21) })]),
      'voci[0].unita',
      'Testo troppo lungo (massimo 20 caratteri)',
    ],
    [
      'rateo ripetuto',
      (v) =>
        (v.ratei = [
          { ...rateoVuoto('FERIE', 'GIORNI'), maturato: 2 },
          { ...rateoVuoto('FERIE', 'GIORNI'), goduto: 1 },
        ]),
      'ratei[1]',
      'Rateo ripetuto: uno solo per tipo e unità',
    ],
    [
      'rateo senza valori',
      (v) => (v.ratei = [rateoVuoto('ROL', 'ORE')]),
      'ratei[0]',
      'Indica almeno un valore del rateo',
    ],
    [
      'rateo senza tipo',
      (v) => (v.ratei = [{ ...rateoVuoto(null, 'ORE'), saldo: 3 }]),
      'ratei[0].tipo',
      'Indica il tipo del rateo',
    ],
    [
      'numero di scatti oltre 99',
      (v) => (v.scatti.numeroScatti = 100),
      'scatti.numeroScatti',
      'Numero di scatti non valido',
    ],
    [
      'qualifica troppo lunga',
      (v) => (v.datiContrattuali.qualifica = 'q'.repeat(101)),
      'datiContrattuali.qualifica',
      'Testo troppo lungo (massimo 100 caratteri)',
    ],
    [
      'livello troppo lungo',
      (v) => (v.datiContrattuali.livello = 'l'.repeat(51)),
      'datiContrattuali.livello',
      'Testo troppo lungo (massimo 50 caratteri)',
    ],
    [
      'CCNL troppo lungo',
      (v) => (v.datiContrattuali.ccnl = 'c'.repeat(301)),
      'datiContrattuali.ccnl',
      'Testo troppo lungo (massimo 300 caratteri)',
    ],
    [
      'codice CNEL troppo lungo',
      (v) => (v.datiContrattuali.codiceCnel = 'C'.repeat(21)),
      'datiContrattuali.codiceCnel',
      'Testo troppo lungo (massimo 20 caratteri)',
    ],
    [
      'matricola troppo lunga',
      (v) => (v.dipendente.codiceDipendente = '1'.repeat(51)),
      'dipendente.codiceDipendente',
      'Testo troppo lungo (massimo 50 caratteri)',
    ],
    [
      'indirizzo troppo lungo',
      (v) => (v.datoreLavoro.indirizzo = 'i'.repeat(301)),
      'datoreLavoro.indirizzo',
      'Testo troppo lungo (massimo 300 caratteri)',
    ],
    [
      'codice azienda troppo lungo',
      (v) => (v.datoreLavoro.codiceAzienda = 'A'.repeat(51)),
      'datoreLavoro.codiceAzienda',
      'Testo troppo lungo (massimo 50 caratteri)',
    ],
    [
      'posizione INPS troppo lunga',
      (v) => (v.datoreLavoro.posizioneInps = '9'.repeat(51)),
      'datoreLavoro.posizioneInps',
      'Testo troppo lungo (massimo 50 caratteri)',
    ],
    [
      'posizione INAIL troppo lunga',
      (v) => (v.datoreLavoro.posizioneInail = '9'.repeat(51)),
      'datoreLavoro.posizioneInail',
      'Testo troppo lungo (massimo 50 caratteri)',
    ],
  ])('%s', (_descrizione, modifica, campo, messaggio) => {
    expect(erroriBloccanti(valoriValidi(modifica), [], OGGI)).toEqual([{ campo, messaggio }]);
  });

  it('accetta il codice fiscale provvisorio, quello di una persona già presente e quello scritto male', () => {
    const conCf = (cf: string) => valoriValidi((v) => (v.dipendente.codiceFiscale = cf));
    expect(erroriBloccanti(conCf('12345678901'), [], OGGI)).toEqual([]);
    expect(erroriBloccanti(conCf(' vrdgpp90c15l219g '), [], OGGI)).toEqual([]);
    expect(erroriBloccanti(conCf('VRDGPP90C15L219X'), ['vrdgpp90c15l219x'], OGGI)).toEqual([]);
    expect(
      erroriBloccanti(
        valoriValidi((v) => (v.datoreLavoro.partitaIva = 'IT 01234567897')),
        [],
        OGGI,
      ),
    ).toEqual([]);
  });

  it('accetta importi fino a 8 cifre intere e 5 decimali, anche negativi', () => {
    const valori = valoriValidi((v) => {
      v.totali.lordo = 99999999.99999;
      v.totali.arrotondamento = -99999999.5;
      // oltre il milione i decimali binari non sono esatti: 1234567,89 ha comunque 2 decimali
      v.progressivi.imponibileIrpef = 1234567.89;
      v.retribuzione.pagaOraria = 12.13873;
      v.datiContrattuali.dataCessazione = '2024-02-29';
    });
    expect(erroriBloccanti(valori, [], OGGI)).toEqual([]);
  });

  it('una cessazione incompleta non viene confrontata con l’assunzione', () => {
    const valori = valoriValidi((v) => {
      v.datiContrattuali.dataAssunzione = '2022-03-01';
      v.datiContrattuali.dataCessazione = DATA_INCOMPLETA;
    });
    expect(erroriBloccanti(valori, [], OGGI)).toEqual([
      {
        campo: 'datiContrattuali.dataCessazione',
        messaggio: 'Data incompleta o inesistente: indica giorno, mese e anno',
      },
    ]);
  });

  it('ammette netto e arrotondamento negativi e ignora le righe vuote', () => {
    const valori = valoriValidi((v) => {
      v.totali.netto = -20;
      v.totali.arrotondamento = -0.5;
      v.voci = [voce({ categoria: 'INFORMATIVA' })];
      v.ratei = [rateoVuoto()];
    });
    expect(erroriBloccanti(valori, [], OGGI)).toEqual([]);
  });

  it('segnala un solo errore per campo, nell’ordine dei campi del modulo', () => {
    const errori = erroriBloccanti(valoriVuoti(), [], OGGI);
    expect(errori.map((e) => e.campo)).toEqual([
      'periodo.tipo',
      'periodo.mese',
      'periodo.anno',
      'dipendente.codiceFiscale',
      'dipendente.cognome',
      'dipendente.nome',
      'datoreLavoro.ragioneSociale',
      'totali.totaleCompetenze',
      'totali.totaleTrattenute',
      'totali.netto',
    ]);
  });
});

describe('errori risolti modificando un altro campo', () => {
  it('toglie gli errori di riga e tra campi che i valori non hanno più, lasciando gli altri', () => {
    const errori = {
      'ratei.1': 'Rateo ripetuto: uno solo per tipo e unità',
      'voci.0.descrizione': 'Indica la descrizione della voce',
      'datiContrattuali.dataCessazione': "La data di cessazione precede l'assunzione",
      'dipendente.codiceFiscale': 'Codice fiscale già usato da un’altra persona',
    };
    const corretti = valoriValidi((v) => {
      v.ratei = [
        { ...rateoVuoto('FERIE', 'GIORNI'), maturato: 2 },
        { ...rateoVuoto('FERIE', 'ORE'), goduto: 1 },
      ];
      v.voci = [voce({})];
      v.datiContrattuali.dataAssunzione = '2021-01-01';
      v.datiContrattuali.dataCessazione = '2021-12-31';
    });

    expect(senzaErroriRisolti(errori, corretti, [])).toEqual({
      'dipendente.codiceFiscale': 'Codice fiscale già usato da un’altra persona',
    });
  });

  it('lascia gli errori ancora presenti e restituisce lo stesso oggetto se non cambia nulla', () => {
    const errori = { 'ratei.0': 'Indica almeno un valore del rateo' };
    const ancoraVuoto = valoriValidi((v) => (v.ratei = [rateoVuoto('ROL', 'ORE')]));
    expect(senzaErroriRisolti(errori, ancoraVuoto, [])).toBe(errori);
    const senzaDerivati = { 'totali.netto': 'Indica il netto' };
    expect(senzaErroriRisolti(senzaDerivati, valoriValidi(), [])).toBe(senzaDerivati);
  });
});

describe('voci e ratei copiati da un altro cedolino', () => {
  const db = creaDbMock('errori');
  const cedolino = [...db.cedolini.values()].find((c) => c.titolo === 'Luglio 2025')!;
  const copiati = () => {
    const dati = daCedolinoRiferimento(cedolino, 2025);
    return valoriValidi((v) => {
      v.voci = [...(dati.voci ?? [])];
      v.ratei = [...(dati.ratei ?? [])];
    });
  };

  it('finché non si completano non vengono inviati, controllati né contati', () => {
    const valori = copiati();
    expect(valori.voci.length).toBeGreaterThan(0);
    expect(valori.ratei.length).toBeGreaterThan(0);
    expect(valori.voci.every(voceCopiataVuota)).toBe(true);
    expect(valori.ratei.every(rateoCopiatoVuoto)).toBe(true);

    expect(richiestaDa(valori).voci).toEqual([]);
    expect(richiestaDa(valori).ratei).toEqual([]);
    expect(erroriBloccanti(valori, [], OGGI)).toEqual([]);
    expect(compilatiSezione(valori, 'voci')).toBe(0);
    expect(compilatiSezione(valori, 'ratei')).toBe(0);
  });

  it('una riga completata o modificata diventa dell’utente e viene inviata', () => {
    const valori = copiati();
    const [prima, seconda] = valori.voci;
    const [rateo] = valori.ratei;
    valori.voci[0] = { ...prima!, importoCompetenza: 1500 };
    valori.voci[1] = { ...seconda!, descrizione: 'Voce rinominata' };
    valori.ratei[0] = { ...rateo!, maturato: 2.16667 };

    const richiesta = richiestaDa(valori);
    expect(richiesta.voci.map((v) => v.descrizione)).toEqual([prima!.descrizione, 'Voce rinominata']);
    expect(richiesta.voci[0]).toMatchObject({ importoCompetenza: 1500 });
    expect(richiesta.ratei).toEqual([expect.objectContaining({ tipo: rateo!.tipo, maturato: 2.16667 })]);
    expect(voceCopiataVuota(valori.voci[1])).toBe(false);
    expect(compilatiSezione(valori, 'voci')).toBe(2);
  });

  it('un rateo copiato con il solo residuo dell’anno precedente non è un errore', () => {
    const valori = copiati();
    expect(valori.ratei.some((r) => r.residuoAnnoPrecedente !== '')).toBe(true);
    expect(erroriBloccanti(valori, [], OGGI)).toEqual([]);
  });

  it('una nuova copia sostituisce le righe copiate non completate', () => {
    const copiate = [voce({ descrizione: 'Retribuzione ordinaria' })];
    expect(unisciSeVuoti(copiati(), { voci: copiate })).toMatchObject({
      valori: { voci: copiate },
      copiati: ['voci'],
    });
  });
});

describe('controlli di coerenza (non bloccanti)', () => {
  it('nessun avviso per un cedolino coerente', () => {
    expect(controlliCoerenza(valoriValidi())).toEqual([]);
  });

  it('confronta competenze meno trattenute con il netto, con o senza arrotondamento', () => {
    expect(controlliCoerenza(valoriValidi((v) => (v.totali.netto = 1450)))).toEqual([
      {
        campo: 'totali.netto',
        messaggio: 'Totale competenze meno totale trattenute non corrisponde al netto',
      },
    ]);
    const arrotondato = valoriValidi((v) => {
      v.totali.netto = 1497;
      v.totali.arrotondamento = -3;
    });
    expect(controlliCoerenza(arrotondato)).toEqual([]);
  });

  it('confronta le somme delle voci con i totali, escluse informative e presenze', () => {
    const valori = valoriValidi((v) => {
      v.voci = [
        voce({ descrizione: 'Retribuzione', importoCompetenza: 1999.97 }),
        voce({ descrizione: 'Contributi', categoria: 'TRATTENUTA_PREVIDENZIALE', importoTrattenuta: 400 }),
        voce({ descrizione: 'Imponibile', categoria: 'INFORMATIVA', importoCompetenza: 5000 }),
      ];
    });
    expect(controlliCoerenza(valori)).toEqual([
      {
        campo: 'voci',
        messaggio: 'La somma delle voci di trattenuta non corrisponde al totale trattenute stampato',
      },
    ]);
    // senza voci con importo il confronto non si fa
    expect(controlliCoerenza(valoriValidi((v) => (v.voci = [voce({ descrizione: 'Nota' })])))).toEqual([]);
  });

  it('segnala il saldo di un rateo diverso da residuo + maturato − goduto', () => {
    const valori = valoriValidi((v) => {
      v.ratei = [
        {
          ...rateoVuoto('FERIE', 'GIORNI'),
          residuoAnnoPrecedente: 6,
          maturato: 2.17,
          goduto: 1,
          saldo: 7.17,
        },
        { ...rateoVuoto('PERMESSI', 'ORE'), residuoAnnoPrecedente: 16, maturato: 6, goduto: 2, saldo: 21 },
      ];
    });
    expect(controlliCoerenza(valori)).toEqual([
      {
        campo: 'ratei.1',
        messaggio: 'Saldo del rateo Permessi (ore) diverso da residuo + maturato − goduto',
      },
    ]);
  });

  it('avvisa per codice fiscale provvisorio, codici di controllo e fine rapporto senza cessazione', () => {
    const valori = valoriValidi((v) => {
      v.dipendente.codiceFiscale = '12345678901';
      v.datoreLavoro.partitaIva = '01234567890';
      v.datoreLavoro.codiceFiscale = '01234567897';
      v.periodo.tipo = 'FINE_RAPPORTO';
    });
    expect(controlliCoerenza(valori).map((a) => a.messaggio)).toEqual([
      'Cedolino di fine rapporto senza data di cessazione stampata',
      'Codice fiscale provvisorio (11 cifre)',
      "Partita IVA dell'azienda formalmente non valida",
    ]);
    const censito = valoriValidi((v) => (v.dipendente.codiceFiscale = 'VRDGPP90C15L219X'));
    expect(controlliCoerenza(censito, ['VRDGPP90C15L219X'])).toEqual([
      {
        campo: 'dipendente.codiceFiscale',
        messaggio: 'Codice fiscale del dipendente formalmente non valido',
      },
    ]);
  });
});

describe('precompilamento', () => {
  const db = creaDbMock('errori');
  const rapporto = db.rapporti.get(1)!;
  const cedolinoDi = (titolo: string) => [...db.cedolini.values()].find((c) => c.titolo === titolo)!;

  it('dal mese associato: tipo, mese e anno con il flag della mensilità aggiuntiva', () => {
    const competenza = (mese: number) => ({
      competenza: {
        chiave: `2025.${String(mese)}`,
        tipo: 'ORDINARIA' as const,
        anno: 2025,
        mese,
        progressivoTfr: null,
      },
    });
    expect(daAssociazione(competenza(8))).toEqual({ periodo: { tipo: 'ORDINARIA', mese: '8', anno: 2025 } });
    expect(daAssociazione(competenza(13))).toEqual({
      periodo: { tipo: 'TREDICESIMA', mese: '12', anno: 2025 },
      contieneTredicesima: true,
    });
    expect(daAssociazione(competenza(14), 6)).toEqual({
      periodo: { tipo: 'QUATTORDICESIMA', mese: '6', anno: 2025 },
      contieneQuattordicesima: true,
    });
    expect(daAssociazione(competenza(14)).periodo?.mese).toBe('7');
  });

  it('dal rapporto: anagrafiche del dipendente e dell’azienda e data di assunzione', () => {
    expect(daRapporto(rapporto)).toEqual({
      dipendente: { codiceFiscale: 'RSSMRA80A01H501U', nome: 'Mario', cognome: 'Rossi' },
      datoreLavoro: {
        ragioneSociale: 'Alfa Software S.r.l.',
        partitaIva: rapporto.azienda.partitaIva ?? '',
        codiceFiscale: rapporto.azienda.codiceFiscale ?? '',
        posizioneInps: rapporto.azienda.posizioneInps ?? '',
        indirizzo: rapporto.azienda.indirizzo ?? '',
      },
      datiContrattuali: { dataAssunzione: '2022-03-01' },
    });
  });

  it('il cedolino di riferimento è il più vicino tra i precedenti, altrimenti il primo successivo', () => {
    expect(rigaDiRiferimento(rapporto.mensilita, { anno: 2025, mese: 8 })?.titolo).toBe('Luglio 2025');
    expect(rigaDiRiferimento(rapporto.mensilita, { anno: 2023, mese: 6 })?.titolo).toBe('Gennaio 2024');
    expect(rigaDiRiferimento(rapporto.mensilita, null)?.titolo).toBe('Settembre 2026');
    expect(rigaDiRiferimento([], { anno: 2025, mese: 8 })).toBeNull();
  });

  it('dal cedolino di riferimento copia i dati che si ripetono, senza quantità, importi e cessazione', () => {
    const cedolino = cedolinoDi('Luglio 2025');
    const dati = daCedolinoRiferimento(cedolino, 2025);
    expect(dati.dipendente).toEqual({ codiceDipendente: cedolino.inquadramento.codiceDipendente ?? '' });
    expect(dati.datiContrattuali).toEqual({
      qualifica: cedolino.inquadramento.qualifica ?? '',
      livello: cedolino.inquadramento.livello ?? '',
      ccnl: cedolino.inquadramento.ccnlTesto ?? '',
    });
    expect(dati.datiContrattuali).not.toHaveProperty('dataCessazione');
    expect(dati.voci?.length).toBeGreaterThan(0);
    for (const v of dati.voci ?? []) {
      expect(v).toMatchObject({ quantita: '', importoCompetenza: '', importoTrattenuta: '' });
      expect(v.descrizione).not.toBe('');
    }
    expect(dati.voci?.map((v) => v.categoria)).toContain('TRATTENUTA_FISCALE');
    expect(dati.ratei?.map((r) => [r.tipo, r.unita, r.residuoAnnoPrecedente, r.maturato])).toEqual(
      cedolino.ratei.map((r) => [r.tipo, r.unita, r.residuoAnnoPrecedente ?? '', '']),
    );
    expect(dati.tfr).toEqual({ fondo31DicembreAnnoPrecedente: cedolino.tfr?.fondo31DicembreAnnoPrecedente });
    expect(dati.retribuzione?.superminimoAssorbibile).toBe('SI');
    expect(dati.buoniPasto).toEqual({ presenti: true, valoreUnitario: cedolino.buoniPasto.valoreUnitario });
  });

  it('da un cedolino di un altro anno non copia residui dei ratei né fondo TFR', () => {
    const dati = daCedolinoRiferimento(cedolinoDi('Luglio 2025'), 2026);
    expect(dati.ratei?.every((r) => r.residuoAnnoPrecedente === '')).toBe(true);
    expect(dati).not.toHaveProperty('tfr');
  });

  it('compila solo i campi vuoti e indica quelli compilati', () => {
    const valori = valoriVuoti();
    valori.dipendente.nome = 'Mariano';
    valori.retribuzione.superminimoAssorbibile = 'NO';
    const { valori: uniti, copiati } = unisciSeVuoti(valori, {
      dipendente: { nome: 'Mario', cognome: 'Rossi' },
      retribuzione: { superminimoAssorbibile: 'SI', minimo: 1780.1 },
      buoniPasto: { presenti: true },
    });
    expect(uniti.dipendente).toMatchObject({ nome: 'Mariano', cognome: 'Rossi' });
    expect(uniti.retribuzione).toMatchObject({ superminimoAssorbibile: 'NO', minimo: 1780.1 });
    expect(uniti.buoniPasto.presenti).toBe(true);
    expect(copiati).toEqual(['dipendente.cognome', 'retribuzione.minimo', 'buoniPasto.presenti']);
    // l'originale non cambia
    expect(valori.dipendente.cognome).toBe('');
  });

  it('copia una lista solo se nel modulo non ci sono righe compilate', () => {
    const copiate = [voce({ descrizione: 'Retribuzione ordinaria' })];
    const conRigaVuota = valoriValidi((v) => (v.voci = [voce({})]));
    expect(unisciSeVuoti(conRigaVuota, { voci: copiate })).toMatchObject({
      valori: { voci: copiate },
      copiati: ['voci'],
    });
    const conRigaPiena = valoriValidi((v) => (v.voci = [voce({ descrizione: 'Premio' })]));
    expect(unisciSeVuoti(conRigaPiena, { voci: copiate }).copiati).toEqual([]);
  });
});

describe('riepiloghi del modulo', () => {
  it('titolo con cui verrà registrato il cedolino', () => {
    const periodo = (tipo: string | null, mese: string | null, anno: number | string) => ({
      ...valoriVuoti().periodo,
      tipo,
      mese,
      anno,
    });
    expect(titoloPrevisto(periodo('ORDINARIA', '5', 2023))).toBe('Maggio 2023');
    expect(titoloPrevisto(periodo('TREDICESIMA', '12', 2023))).toBe('Tredicesima 2023');
    expect(titoloPrevisto(periodo('FINE_RAPPORTO', '5', 2023))).toBe('Fine rapporto – Maggio 2023');
    expect(titoloPrevisto(periodo('ORDINARIA', null, 2023))).toBeNull();
    expect(titoloPrevisto(periodo(null, '5', 2023))).toBeNull();
  });

  it('campi compilati per sezione e sezione di un percorso', () => {
    const valori = valoriValidi((v) => {
      v.contieneQuattordicesima = true;
      v.voci = [voce({}), voce({ descrizione: 'Premio' })];
      v.buoniPasto = { ...v.buoniPasto, presenti: true, numero: 20 };
    });
    expect(compilatiSezione(valori, 'periodo')).toBe(4);
    expect(compilatiSezione(valori, 'voci')).toBe(1);
    expect(compilatiSezione(valori, 'scatti')).toBe(2);
    expect(compilatiSezione(valori, 'retribuzione')).toBe(0);
    expect(sezioneDi('datoreLavoro.partitaIva')).toBe('datore');
    expect(sezioneDi('voci[2].descrizione')).toBe('voci');
    expect(sezioneDi('buoniPasto.numero')).toBe('scatti');
    expect(sezioneDi('sconosciuto')).toBeNull();
  });
});
