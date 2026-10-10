import type {
  AziendaSintesi,
  Caricamento,
  CedolinoDettaglio,
  Ccnl,
  Competenza,
  PersonaSintesi,
  RapportoDettaglio,
  RateoCedolino,
  Riepilogo,
  RigaMensilita,
  MotivoErrore,
  Segnalazione,
  TipoRateo,
  UnitaMisura,
  Voce,
} from '../api/types';
import { formatMese } from '../format/format';

/**
 * Dati INVENTATI per lo sviluppo senza backend e per i test. Nessun dato personale reale.
 * Gli importi sono coerenti tra loro solo quanto basta a rendere credibile l'interfaccia.
 */

export const CCNL_MOCK: Ccnl[] = [
  {
    codice: 'ALTRO_13',
    descrizione: 'Altro CCNL (13 mensilità)',
    mensilita: 13,
    periodicitaScattiMesi: null,
    maxScatti: null,
    meseQuattordicesima: null,
    noteVerifica: 'Voce generica per CCNL non presenti in tabella: nessuna regola sugli scatti.',
  },
  {
    codice: 'ALTRO_14',
    descrizione: 'Altro CCNL (14 mensilità)',
    mensilita: 14,
    periodicitaScattiMesi: null,
    maxScatti: null,
    meseQuattordicesima: 7,
    noteVerifica:
      'Voce generica per CCNL non presenti in tabella: quattordicesima assunta a luglio, nessuna regola sugli scatti.',
  },
  {
    codice: 'METALMECCANICO_INDUSTRIA',
    descrizione: 'Metalmeccanico industria (Federmeccanica-Assistal)',
    mensilita: 13,
    periodicitaScattiMesi: 24,
    maxScatti: 5,
    meseQuattordicesima: null,
    noteVerifica: null,
  },
  {
    codice: 'STUDI_PROFESSIONALI',
    descrizione: 'Studi professionali (Confprofessioni)',
    mensilita: 14,
    periodicitaScattiMesi: null,
    maxScatti: null,
    meseQuattordicesima: null,
    noteVerifica:
      'Verificare periodicità e numero massimo scatti di anzianità e mese di pagamento della quattordicesima (non impostato).',
  },
  {
    codice: 'TELECOMUNICAZIONI',
    descrizione: 'Telecomunicazioni (Asstel)',
    mensilita: 14,
    periodicitaScattiMesi: null,
    maxScatti: null,
    meseQuattordicesima: null,
    noteVerifica:
      'Verificare periodicità e numero massimo scatti di anzianità e mese di pagamento della quattordicesima (non impostato).',
  },
  {
    codice: 'COMMERCIO',
    descrizione: 'Terziario, distribuzione e servizi (Confcommercio)',
    mensilita: 14,
    periodicitaScattiMesi: 36,
    maxScatti: 10,
    meseQuattordicesima: 7,
    noteVerifica: null,
  },
];

export function trovaCcnl(codice: string): Ccnl | undefined {
  return CCNL_MOCK.find((c) => c.codice === codice);
}

// ------------------------------------------------------------------ anagrafiche inventate

const MARIO: PersonaSintesi = {
  id: 1,
  nome: 'Mario',
  cognome: 'Rossi',
  codiceFiscale: 'RSSMRA80A01H501U',
  etichetta: 'Mario Rossi',
};

const LAURA: PersonaSintesi = {
  id: 2,
  nome: 'Laura',
  cognome: 'Bianchi',
  codiceFiscale: 'BNCLRA85M41F205X',
  etichetta: 'Laura Bianchi',
};

const ALFA: AziendaSintesi = {
  id: 1,
  ragioneSociale: 'Alfa Software S.r.l.',
  partitaIva: '01234567897',
  codiceFiscale: '01234567897',
  posizioneInps: '1234567890',
  indirizzo: 'Via Roma 1, 00100 Roma',
};

const BETA: AziendaSintesi = {
  id: 2,
  ragioneSociale: 'Beta Consulting S.p.A.',
  partitaIva: '09876543217',
  codiceFiscale: '09876543217',
  posizioneInps: '0987654321',
  indirizzo: 'Corso Italia 10, 20100 Milano',
};

const GAMMA: AziendaSintesi = {
  id: 3,
  ragioneSociale: 'Gamma Servizi S.r.l.',
  partitaIva: '11223344554',
  codiceFiscale: null,
  posizioneInps: null,
  indirizzo: null,
};

// ------------------------------------------------------------------ utilità

/** Pseudo-casuale deterministico in [0, 1) per variare gli importi senza perdere la riproducibilità. */
function rumore(seme: number): number {
  const x = Math.sin(seme * 9301 + 49297) * 233280;
  return x - Math.floor(x);
}

function arrotonda(valore: number): number {
  return Math.round(valore * 100) / 100;
}

export function mese(anno: number, m: number): string {
  return `${anno}-${String(m).padStart(2, '0')}`;
}

/** Mesi da `da` ad `a` inclusi, come coppie [anno, mese]. */
function intervalloMesi(da: [number, number], a: [number, number]): [number, number][] {
  const risultato: [number, number][] = [];
  let [anno, m] = da;
  while (anno < a[0] || (anno === a[0] && m <= a[1])) {
    risultato.push([anno, m]);
    m += 1;
    if (m > 12) {
      m = 1;
      anno += 1;
    }
  }
  return risultato;
}

export function competenzaOrdinaria(anno: number, m: number): Competenza {
  return {
    chiave: `${anno}.${String(m).padStart(2, '0')}`,
    tipo: 'ORDINARIA',
    anno,
    mese: m,
    progressivoTfr: null,
  };
}

export function competenzaSpeciale(anno: number, tipo: 'TREDICESIMA' | 'QUATTORDICESIMA'): Competenza {
  const m = tipo === 'TREDICESIMA' ? 13 : 14;
  return { chiave: `${anno}.${m}`, tipo, anno, mese: m, progressivoTfr: null };
}

export function competenzaFineRapporto(anno: number, progressivo: number): Competenza {
  return {
    chiave: `${anno}.TFR${String(progressivo).padStart(2, '0')}`,
    tipo: 'FINE_RAPPORTO',
    anno,
    mese: null,
    progressivoTfr: progressivo,
  };
}

export function titoloCompetenza(competenza: Competenza, periodo: string | null): string {
  switch (competenza.tipo) {
    case 'ORDINARIA':
      return formatMese(mese(competenza.anno, competenza.mese ?? 1));
    case 'TREDICESIMA':
      return `Tredicesima ${competenza.anno}`;
    case 'QUATTORDICESIMA':
      return `Quattordicesima ${competenza.anno}`;
    case 'FINE_RAPPORTO':
      return `Fine rapporto ${competenza.progressivoTfr ?? 1} – ${formatMese(periodo)}`;
  }
}

function voce(descrizione: string, valori: Partial<Voce>): Voce {
  return {
    codice: null,
    descrizione,
    quantita: null,
    unita: null,
    base: null,
    importoCompetenza: null,
    importoTrattenuta: null,
    ...valori,
  };
}

// ------------------------------------------------------------------ generazione cedolini

interface ProfiloRapporto {
  rapportoId: number;
  personaId: number;
  aziendaId: number;
  qualifica: string;
  livello: string;
  ccnlTesto: string | null;
  dataAssunzione: string;
  dataCessazione: string | null;
  /** Lordo mensile contrattuale in funzione del mese (gli aumenti cambiano gli elementi). */
  elementi: (
    anno: number,
    m: number,
  ) => { minimo: number; contingenza: number; superminimo: number; scatti: number };
  numeroScatti: (anno: number, m: number) => number;
  buoniPasto: boolean;
  ratei: { tipo: TipoRateo; unita: UnitaMisura; maturatoMese: number }[];
  /** Avvisi della lettura riportati su ogni cedolino del rapporto. */
  avvisiEstrazione: string[];
}

interface Contatori {
  prossimoIdCedolino: number;
  prossimoIdCaricamento: number;
  /** Persone, aziende e rapporti creati dall'inserimento a mano (lontani dagli id dei dati dimostrativi). */
  prossimoIdAnagrafica: number;
}

function generaCedolino(
  profilo: ProfiloRapporto,
  competenza: Competenza,
  periodo: [number, number],
  contatori: Contatori,
): CedolinoDettaglio {
  const id = contatori.prossimoIdCedolino++;
  const [anno, m] = periodo;
  const seme = anno * 100 + m + (competenza.mese ?? 20) + profilo.rapportoId * 7;
  const e = profilo.elementi(anno, m);
  const totaleFisso = arrotonda(e.minimo + e.contingenza + e.superminimo + e.scatti);
  const ordinaria = competenza.tipo === 'ORDINARIA';
  const fineRapporto = competenza.tipo === 'FINE_RAPPORTO';

  const competenze: Voce[] = [];
  if (ordinaria) {
    competenze.push(
      voce('Retribuzione ordinaria', {
        codice: '0001',
        quantita: 26,
        unita: 'gg',
        importoCompetenza: totaleFisso,
      }),
    );
    if (rumore(seme) > 0.6) {
      const ore = Math.round(rumore(seme + 1) * 10) + 2;
      const paga = arrotonda((totaleFisso / 168) * 1.25);
      competenze.push(
        voce('Straordinario 25%', {
          codice: '0250',
          quantita: ore,
          unita: 'ore',
          base: paga,
          importoCompetenza: arrotonda(ore * paga),
        }),
      );
    }
  } else if (competenza.tipo === 'TREDICESIMA') {
    competenze.push(
      voce('13ma Mensilità', {
        codice: '0130',
        quantita: 12,
        unita: 'ratei',
        importoCompetenza: totaleFisso,
      }),
    );
  } else if (competenza.tipo === 'QUATTORDICESIMA') {
    competenze.push(
      voce('14ma Mensilità', {
        codice: '0140',
        quantita: 12,
        unita: 'ratei',
        importoCompetenza: totaleFisso,
      }),
    );
  } else {
    competenze.push(
      voce('Ferie non godute', {
        codice: '0410',
        quantita: 6.5,
        unita: 'gg',
        base: arrotonda(totaleFisso / 26),
        importoCompetenza: arrotonda((totaleFisso / 26) * 6.5),
      }),
      voce('Rateo 13ma Mensilità', {
        codice: '0131',
        quantita: 2,
        unita: 'ratei',
        importoCompetenza: arrotonda((totaleFisso / 12) * 2),
      }),
      voce('Indennità sostitutiva del preavviso', {
        codice: '0420',
        importoCompetenza: arrotonda(totaleFisso * 0.5),
      }),
    );
  }

  const lordo = arrotonda(competenze.reduce((s, v) => s + (v.importoCompetenza ?? 0), 0));
  const inps = arrotonda(lordo * 0.0919);
  const imponibileIrpef = lordo - inps;
  const irpef = arrotonda(imponibileIrpef * (fineRapporto ? 0.23 : 0.24));
  const detrazioni = ordinaria ? arrotonda(110 + rumore(seme + 3) * 20) : 0;
  const addizionali = ordinaria ? arrotonda(imponibileIrpef * 0.0223) : 0;

  const trattenutePrevidenziali = [
    voce('Contributo IVS a carico dipendente', {
      codice: 'C001',
      base: lordo,
      quantita: 9.19,
      unita: '%',
      importoTrattenuta: inps,
    }),
  ];
  const trattenuteFiscali = [
    voce('IRPEF lorda', { codice: 'F001', base: arrotonda(imponibileIrpef), importoTrattenuta: irpef }),
    ...(detrazioni > 0
      ? [voce('Detrazioni lavoro dipendente', { codice: 'F010', importoCompetenza: detrazioni })]
      : []),
    ...(addizionali > 0
      ? [
          voce('Addizionale regionale', { codice: 'F020', importoTrattenuta: arrotonda(addizionali * 0.7) }),
          voce('Addizionale comunale', { codice: 'F021', importoTrattenuta: arrotonda(addizionali * 0.3) }),
        ]
      : []),
  ];
  const altreTrattenute =
    ordinaria && profilo.rapportoId === 1
      ? [voce('Quota associativa sindacale', { codice: 'T100', importoTrattenuta: 12 })]
      : [];

  const vociNonRetributive: Voce[] = [];
  const numeroBuoni = ordinaria && profilo.buoniPasto ? 18 + Math.round(rumore(seme + 5) * 4) : 0;
  if (numeroBuoni > 0) {
    vociNonRetributive.push(
      voce('Buoni pasto elettronici', {
        codice: 'N001',
        quantita: numeroBuoni,
        unita: 'n.',
        base: 8,
        importoCompetenza: arrotonda(numeroBuoni * 8),
      }),
    );
  }
  if (ordinaria && rumore(seme + 7) > 0.7) {
    const km = Math.round(rumore(seme + 8) * 300) + 40;
    vociNonRetributive.push(
      voce('Rimborso chilometrico', {
        codice: 'N010',
        quantita: km,
        unita: 'km',
        base: 0.42,
        importoCompetenza: arrotonda(km * 0.42),
      }),
    );
  }

  const totaleTrattenute = arrotonda(
    [...trattenutePrevidenziali, ...trattenuteFiscali, ...altreTrattenute].reduce(
      (s, v) => s + (v.importoTrattenuta ?? 0) - (v.importoCompetenza ?? 0),
      0,
    ),
  );
  const nonRetributivo = arrotonda(vociNonRetributive.reduce((s, v) => s + (v.importoCompetenza ?? 0), 0));
  const nettoGrezzo = lordo - totaleTrattenute + nonRetributivo;
  const netto = Math.round(nettoGrezzo);
  const arrotondamento = arrotonda(netto - nettoGrezzo);

  const meseIndice = (anno - 2020) * 12 + m;
  const ratei: RateoCedolino[] = profilo.ratei.map((r, i) => {
    const maturato = arrotonda(r.maturatoMese * m);
    const residuo = r.unita === 'GIORNI' ? 6 + i : 12 + i * 4;
    const goduto = arrotonda(
      Math.min(maturato + residuo - 1, maturato * (0.4 + rumore(meseIndice + i) * 0.4)),
    );
    return {
      tipo: r.tipo,
      unita: r.unita,
      residuoAnnoPrecedente: residuo,
      maturato,
      goduto,
      saldo: fineRapporto ? 0 : arrotonda(residuo + maturato - goduto),
    };
  });

  const quotaMese = arrotonda(lordo / 13.5);
  const fondoPrecedente = arrotonda(1800 + (anno - 2018) * 2350);
  const tfr = {
    fondo31DicembreAnnoPrecedente: fondoPrecedente,
    rivalutazione: arrotonda(fondoPrecedente * 0.0021 * m),
    impostaSostitutiva: arrotonda(fondoPrecedente * 0.0021 * m * 0.17),
    quotaAnno: arrotonda(quotaMese * m),
    quotaMese,
    quotaFondi: profilo.rapportoId === 1 ? arrotonda(quotaMese * 0.35) : 0,
    quotaBusta: 0,
    anticipi: 0,
    retribuzioneUtile: lordo,
    liquidatoLordo: fineRapporto ? 9870 : null,
    ritenuta: fineRapporto ? 2170.4 : null,
  };

  return {
    id,
    rapportoId: profilo.rapportoId,
    titolo: titoloCompetenza(competenza, mese(anno, m)),
    competenza,
    periodoRetribuzione: mese(anno, m),
    meseVariabili: ordinaria ? mese(m === 1 ? anno - 1 : anno, m === 1 ? 12 : m - 1) : null,
    dataElaborazione: `${mese(anno, m)}-27`,
    nomeFile: `${competenza.chiave}_${profilo.aziendaId}_${profilo.personaId}.pdf`,
    acquisitoIl: '2026-10-01T09:30:00Z',
    confidenza: arrotonda(0.86 + rumore(seme + 11) * 0.13),
    inseritoAMano: false,
    campiNonTrovati: profilo.ccnlTesto ? [] : ['datiContrattuali.ccnl', 'datiContrattuali.codiceCnel'],
    avvisiEstrazione: profilo.avvisiEstrazione,
    inquadramento: {
      qualifica: profilo.qualifica,
      livello: profilo.livello,
      codiceDipendente: String(1000 + profilo.personaId),
      ccnlTesto: profilo.ccnlTesto,
      dataAssunzione: profilo.dataAssunzione,
      dataCessazione: profilo.dataCessazione,
    },
    totali: {
      lordo,
      totaleCompetenze: arrotonda(lordo + nonRetributivo + detrazioni),
      totaleTrattenute: arrotonda(totaleTrattenute + detrazioni),
      arrotondamento,
      netto,
    },
    elementiRetributivi: {
      minimo: e.minimo,
      contingenza: e.contingenza,
      superminimo: e.superminimo,
      superminimoAssorbibile: true,
      importoScatti: e.scatti,
      altriElementiFissi: null,
      retribuzioneBase: null,
      totale: totaleFisso,
      pagaOraria: arrotonda(totaleFisso / 168),
      pagaGiornaliera: arrotonda(totaleFisso / 26),
      divisoreOrario: 168,
      divisoreGiornaliero: 26,
    },
    presenze: {
      giorniLavorati: ordinaria ? 21 : null,
      oreLavorate: ordinaria ? 168 : null,
      giorniRetribuiti: ordinaria ? 26 : null,
      voci:
        ordinaria && rumore(seme + 13) > 0.5
          ? [
              voce('Ferie godute', { quantita: 2, unita: 'gg' }),
              voce('Permessi retribuiti', { quantita: 4, unita: 'ore' }),
            ]
          : [],
    },
    competenze,
    trattenutePrevidenziali,
    trattenuteFiscali,
    altreTrattenute,
    vociNonRetributive,
    vociInformative: ordinaria
      ? [voce('Imponibile fiscale del mese', { importoCompetenza: arrotonda(imponibileIrpef) })]
      : [],
    tfr,
    ratei,
    scatti: {
      numeroScatti: profilo.numeroScatti(anno, m),
      prossimoScatto: profilo.rapportoId === 1 ? '2028-03-01' : null,
    },
    buoniPasto: {
      presenti: numeroBuoni > 0,
      numero: numeroBuoni > 0 ? numeroBuoni : null,
      valoreUnitario: numeroBuoni > 0 ? 8 : null,
      importo: numeroBuoni > 0 ? arrotonda(numeroBuoni * 8) : null,
    },
    progressivi: {
      imponibilePrevidenziale: arrotonda(lordo * m),
      imponibileIrpef: arrotonda(imponibileIrpef * m),
      irpefTrattenuta: arrotonda(irpef * m * 0.8),
    },
    contieneTredicesima: competenza.tipo === 'TREDICESIMA',
    contieneQuattordicesima: competenza.tipo === 'QUATTORDICESIMA',
  };
}

export function rigaDaCedolino(
  cedolino: CedolinoDettaglio,
  indicatori: { scattoInBusta: boolean; aumentoFuoriScatti: boolean },
): RigaMensilita {
  return {
    chiave: cedolino.competenza.chiave,
    stato: 'PRESENTE',
    titolo: cedolino.titolo,
    competenza: cedolino.competenza,
    periodoRetribuzione: cedolino.periodoRetribuzione,
    cedolinoId: cedolino.id,
    netto: cedolino.totali.netto,
    lordo: cedolino.totali.lordo,
    ratei:
      cedolino.competenza.tipo === 'ORDINARIA'
        ? cedolino.ratei.flatMap((r) =>
            r.saldo === null ? [] : [{ tipo: r.tipo, unita: r.unita, saldo: r.saldo }],
          )
        : [],
    scattoInBusta: indicatori.scattoInBusta,
    aumentoFuoriScatti: indicatori.aumentoFuoriScatti,
    nomeFile: cedolino.nomeFile,
    caricamentoId: null,
    motivoErrore: null,
  };
}

function rigaMancante(competenza: Competenza, periodo: string): RigaMensilita {
  return {
    chiave: competenza.chiave,
    stato: 'MANCANTE',
    titolo: titoloCompetenza(competenza, periodo),
    competenza,
    periodoRetribuzione: periodo,
    cedolinoId: null,
    netto: null,
    lordo: null,
    ratei: [],
    scattoInBusta: false,
    aumentoFuoriScatti: false,
    nomeFile: null,
    caricamentoId: null,
    motivoErrore: null,
  };
}

/** Ordine cronologico: la tredicesima segue dicembre, la quattordicesima luglio, il fine rapporto il suo periodo. */
export function ordineCompetenza(riga: Pick<RigaMensilita, 'competenza' | 'periodoRetribuzione'>): number {
  const { competenza } = riga;
  switch (competenza.tipo) {
    case 'ORDINARIA':
      return competenza.anno * 1000 + (competenza.mese ?? 0) * 10;
    case 'TREDICESIMA':
      return competenza.anno * 1000 + 125;
    case 'QUATTORDICESIMA':
      return competenza.anno * 1000 + 75;
    case 'FINE_RAPPORTO': {
      const [a, m] = (riga.periodoRetribuzione ?? `${competenza.anno}-12`).split('-').map(Number);
      return (a ?? competenza.anno) * 1000 + (m ?? 12) * 10 + 8 + (competenza.progressivoTfr ?? 0) / 100;
    }
  }
}

// ------------------------------------------------------------------ database in memoria

/** Mensilita' (rapporto + anno + mese 1..14, 13 e 14 tredicesima e quattordicesima) associata a un file in errore. */
export interface AssociazioneMock {
  rapportoId: number;
  anno: number;
  mese: number;
}

export interface MockDb {
  persone: PersonaSintesi[];
  rapporti: Map<number, RapportoDettaglio>;
  cedolini: Map<number, CedolinoDettaglio>;
  contatori: Contatori;
  /** Tutti i tentativi di caricamento, dal piu' recente. */
  caricamenti: Caricamento[];
  /** PDF conservati dei file in errore completabili a mano, per file (vedi chiaveFile). */
  pdfFile: Map<string, Blob>;
  /** Mensilita' a cui l'utente ha associato un file in errore, per file (vedi chiaveFile). */
  associazioni: Map<string, AssociazioneMock>;
}

/**
 * Surrogato dell'hash del contenuto con cui il backend identifica un file: nel mock stesso nome e stessa dimensione
 * sono lo stesso file.
 */
export function chiaveFile(caricamento: Pick<Caricamento, 'nomeFileOriginale' | 'dimensione'>): string {
  return `${caricamento.nomeFileOriginale}:${caricamento.dimensione}`;
}

function segnalazione(
  id: number,
  valori: Omit<Segnalazione, 'id' | 'cedolinoId' | 'competenza' | 'evidenze'> & Partial<Segnalazione>,
): Segnalazione {
  return { id, cedolinoId: null, competenza: null, evidenze: [], ...valori };
}

/** Cedolino ordinario più recente (righe in ordine cronologico): riferimento della RAL, come nel backend. */
function ultimaOrdinaria(righe: RigaMensilita[]): number | null {
  return (
    righe.findLast((r) => r.stato === 'PRESENTE' && r.competenza.tipo === 'ORDINARIA')?.cedolinoId ?? null
  );
}

function rapportoMario1(db: MockDb): RapportoDettaglio {
  const profilo: ProfiloRapporto = {
    rapportoId: 1,
    personaId: MARIO.id,
    aziendaId: ALFA.id,
    qualifica: 'Impiegato',
    livello: '2',
    ccnlTesto: 'CCNL Terziario Confcommercio',
    dataAssunzione: '2022-03-01',
    dataCessazione: null,
    elementi: (anno, m) => {
      const rinnovo = anno > 2024 || (anno === 2024 && m >= 4);
      const scatto = anno > 2025 || (anno === 2025 && m >= 3);
      const promozione = anno >= 2026;
      return {
        minimo: promozione ? 1990.5 : rinnovo ? 1850.25 : 1780.1,
        contingenza: 532.15,
        superminimo: promozione ? 310 : rinnovo ? 180 : 250.15,
        scatti: scatto ? 30.33 : 0,
      };
    },
    numeroScatti: (anno, m) => (anno > 2025 || (anno === 2025 && m >= 3) ? 1 : 0),
    buoniPasto: true,
    avvisiEstrazione: [],
    ratei: [
      { tipo: 'FERIE', unita: 'GIORNI', maturatoMese: 2.17 },
      { tipo: 'PERMESSI', unita: 'ORE', maturatoMese: 6 },
      { tipo: 'EX_FESTIVITA', unita: 'ORE', maturatoMese: 2.67 },
    ],
  };

  const righe: RigaMensilita[] = [];
  for (const [anno, m] of intervalloMesi([2024, 1], [2026, 9])) {
    if (anno === 2025 && m === 8) {
      righe.push(rigaMancante(competenzaOrdinaria(anno, m), mese(anno, m)));
      continue;
    }
    const cedolino = generaCedolino(profilo, competenzaOrdinaria(anno, m), [anno, m], db.contatori);
    db.cedolini.set(cedolino.id, cedolino);
    righe.push(
      rigaDaCedolino(cedolino, {
        scattoInBusta: anno === 2025 && m === 3,
        aumentoFuoriScatti: (anno === 2024 && m === 4) || (anno === 2026 && m === 1),
      }),
    );
  }
  for (const anno of [2024, 2025, 2026]) {
    const quattordicesima = generaCedolino(
      profilo,
      competenzaSpeciale(anno, 'QUATTORDICESIMA'),
      [anno, 7],
      db.contatori,
    );
    db.cedolini.set(quattordicesima.id, quattordicesima);
    righe.push(rigaDaCedolino(quattordicesima, { scattoInBusta: false, aumentoFuoriScatti: false }));
  }
  for (const anno of [2024, 2025]) {
    const tredicesima = generaCedolino(
      profilo,
      competenzaSpeciale(anno, 'TREDICESIMA'),
      [anno, 12],
      db.contatori,
    );
    db.cedolini.set(tredicesima.id, tredicesima);
    righe.push(rigaDaCedolino(tredicesima, { scattoInBusta: false, aumentoFuoriScatti: false }));
  }

  const riepilogo: Riepilogo = {
    calcolatoIl: '2026-10-01T09:31:00Z',
    anzianita: {
      dal: '2022-03',
      al: '2026-09',
      inCorso: true,
      anni: 4,
      mesi: 7,
      fonteInizio: 'DATA_CONTRATTUALE',
      fonteFine: 'CEDOLINI',
    },
    scatti: {
      attuali: 1,
      massimo: 10,
      prossimoScatto: '2028-03-01',
      fonteProssimo: 'CEDOLINI',
      fonteAttuali: 'CEDOLINI',
      attualiStimati: false,
    },
    retribuzione: {
      ral: 40081.72,
      lordoMensile: 2862.98,
      mensilita: 14,
      minimo: 1990.5,
      contingenza: 532.15,
      superminimo: 310,
      importoScatti: 30.33,
      altriElementiFissi: null,
      retribuzioneBase: null,
      stimata: false,
      nota: null,
      cedolinoRiferimentoId: ultimaOrdinaria(righe),
    },
    buoniPasto: true,
    ratei: {
      cedolinoRiferimentoId: righe.find((r) => r.chiave === '2026.09')?.cedolinoId ?? null,
      saldi: [
        {
          tipo: 'FERIE',
          unita: 'GIORNI',
          saldo: 12.5,
          valoreStimato: 1376.4,
          nota: 'Giorni x paga giornaliera stampata sul cedolino.',
        },
        {
          tipo: 'PERMESSI',
          unita: 'ORE',
          saldo: 32,
          valoreStimato: 545.33,
          nota: 'Ore x paga oraria stampata sul cedolino.',
        },
        {
          tipo: 'EX_FESTIVITA',
          unita: 'ORE',
          saldo: 16,
          valoreStimato: 272.67,
          nota: 'Ore x paga oraria stampata sul cedolino.',
        },
      ],
    },
    tfr: {
      totaleAccumulato: 12345.67,
      quotaMensile: 212.07,
      quotaInAzienda: 8120.4,
      quotaFondi: 4225.27,
      quotaBusta: 0,
      anticipi: 0,
      stimaNetto: 9506.17,
      aliquotaStimata: 0.23,
      stimato: false,
      riferimento: '2026-09',
      nota: null,
      quotaMensileStimata: false,
      liquidato: false,
      importoLiquidato: null,
      nettoLiquidato: null,
    },
  };

  const mancante = righe.find((r) => r.stato === 'MANCANTE');
  return {
    id: 1,
    persona: MARIO,
    azienda: ALFA,
    etichetta: ALFA.ragioneSociale,
    periodo: { da: '2022-03', a: null, fonteInizio: 'DATA_CONTRATTUALE', fonteFine: 'CEDOLINI' },
    attuale: true,
    dataAssunzione: '2022-03-01',
    dataCessazione: null,
    qualifica: 'Impiegato',
    livello: '1',
    ccnl: trovaCcnl('COMMERCIO') ?? null,
    ccnlDaIndicare: false,
    ccnlSceltoDaUtente: false,
    note: 'Accordo integrativo aziendale firmato a Gennaio 2026.',
    segnalazioni: mancante
      ? [
          segnalazione(1, {
            tipo: 'MENSILITA_MANCANTE',
            gravita: 'AVVISO',
            titolo: `Manca il cedolino di ${mancante.titolo}`,
            descrizione: `Tra i cedolini caricati manca la mensilità ordinaria di ${mancante.titolo}: caricala per completare i calcoli.`,
            competenza: mancante.competenza,
            evidenze: [
              {
                descrizione: 'Cedolino precedente',
                cedolinoId: null,
                competenza: null,
                valore: 'Luglio 2025',
              },
              {
                descrizione: 'Cedolino successivo',
                cedolinoId: null,
                competenza: null,
                valore: 'Settembre 2025',
              },
            ],
          }),
        ]
      : [],
    riepilogo,
    mensilita: righe,
  };
}

function rapportoMario2(db: MockDb): RapportoDettaglio {
  const profilo: ProfiloRapporto = {
    rapportoId: 2,
    personaId: MARIO.id,
    aziendaId: BETA.id,
    qualifica: 'Impiegato tecnico',
    livello: 'C3',
    ccnlTesto: 'CONTRATTO DI LAVORO Metalmeccanici imp. (p.ind) CNEL C011',
    dataAssunzione: '2018-09-03',
    dataCessazione: '2022-02-28',
    elementi: () => ({ minimo: 1910.4, contingenza: 0, superminimo: 380, scatti: 26.5 }),
    numeroScatti: () => 1,
    buoniPasto: false,
    avvisiEstrazione: [],
    ratei: [
      { tipo: 'FERIE', unita: 'ORE', maturatoMese: 13.33 },
      { tipo: 'PERMESSI', unita: 'ORE', maturatoMese: 8.67 },
    ],
  };
  const righe: RigaMensilita[] = [];
  for (const [anno, m] of intervalloMesi([2021, 1], [2022, 2])) {
    const cedolino = generaCedolino(profilo, competenzaOrdinaria(anno, m), [anno, m], db.contatori);
    db.cedolini.set(cedolino.id, cedolino);
    righe.push(rigaDaCedolino(cedolino, { scattoInBusta: false, aumentoFuoriScatti: false }));
  }
  const tredicesima = generaCedolino(
    profilo,
    competenzaSpeciale(2021, 'TREDICESIMA'),
    [2021, 12],
    db.contatori,
  );
  db.cedolini.set(tredicesima.id, tredicesima);
  righe.push(rigaDaCedolino(tredicesima, { scattoInBusta: false, aumentoFuoriScatti: false }));
  // due cedolini dopo la cessazione: fine rapporto e successivo conguaglio, numerati per periodo stampato
  for (const [progressivo, periodo] of [
    [1, [2022, 3]],
    [2, [2022, 5]],
  ] as const) {
    const fine = generaCedolino(
      profilo,
      competenzaFineRapporto(2022, progressivo),
      [...periodo],
      db.contatori,
    );
    db.cedolini.set(fine.id, fine);
    righe.push(rigaDaCedolino(fine, { scattoInBusta: false, aumentoFuoriScatti: false }));
  }

  return {
    id: 2,
    persona: MARIO,
    azienda: BETA,
    etichetta: BETA.ragioneSociale,
    periodo: {
      da: '2018-09',
      a: '2022-02',
      fonteInizio: 'DATA_CONTRATTUALE',
      fonteFine: 'DATA_CONTRATTUALE',
    },
    attuale: false,
    dataAssunzione: '2018-09-03',
    dataCessazione: '2022-02-28',
    qualifica: 'Impiegato tecnico',
    livello: 'C3',
    ccnl: trovaCcnl('METALMECCANICO_INDUSTRIA') ?? null,
    ccnlDaIndicare: false,
    ccnlSceltoDaUtente: false,
    note: null,
    segnalazioni: [],
    riepilogo: {
      calcolatoIl: '2026-10-01T09:31:00Z',
      anzianita: {
        dal: '2018-09',
        al: '2022-02',
        inCorso: false,
        anni: 3,
        mesi: 6,
        fonteInizio: 'DATA_CONTRATTUALE',
        fonteFine: 'DATA_CONTRATTUALE',
      },
      scatti: {
        attuali: 1,
        massimo: 5,
        prossimoScatto: null,
        fonteProssimo: 'NON_DISPONIBILE',
        fonteAttuali: 'CEDOLINI',
        attualiStimati: false,
      },
      retribuzione: {
        ral: 30119.7,
        lordoMensile: 2316.9,
        mensilita: 13,
        minimo: 1910.4,
        contingenza: null,
        superminimo: 380,
        importoScatti: 26.5,
        altriElementiFissi: null,
        retribuzioneBase: null,
        stimata: false,
        nota: null,
        cedolinoRiferimentoId: ultimaOrdinaria(righe),
      },
      buoniPasto: false,
      ratei: { cedolinoRiferimentoId: null, saldi: [] },
      tfr: {
        totaleAccumulato: 0,
        quotaMensile: 171.62,
        quotaInAzienda: 0,
        quotaFondi: 0,
        quotaBusta: 0,
        anticipi: 0,
        stimaNetto: null,
        aliquotaStimata: 0.22,
        stimato: false,
        riferimento: '2022-03',
        nota: 'TFR liquidato nel cedolino di fine rapporto di Marzo 2022: nessun TFR residuo in azienda.',
        quotaMensileStimata: false,
        liquidato: true,
        importoLiquidato: 9870,
        nettoLiquidato: 7699.6,
      },
    },
    mensilita: righe,
  };
}

function rapportoLaura(db: MockDb): RapportoDettaglio {
  const profilo: ProfiloRapporto = {
    rapportoId: 3,
    personaId: LAURA.id,
    aziendaId: GAMMA.id,
    qualifica: 'Impiegata',
    livello: '3',
    ccnlTesto: null,
    dataAssunzione: '2025-11-03',
    dataCessazione: null,
    elementi: () => ({ minimo: 1650, contingenza: 0, superminimo: 300, scatti: 0 }),
    numeroScatti: () => 0,
    buoniPasto: false,
    avvisiEstrazione: ["Rimossi dati bancari presenti nell'estrazione: non vengono salvati"],
    ratei: [
      { tipo: 'FERIE', unita: 'ORE', maturatoMese: 14.67 },
      { tipo: 'ROL', unita: 'ORE', maturatoMese: 6 },
    ],
  };
  const righe: RigaMensilita[] = [];
  for (const [anno, m] of intervalloMesi([2025, 11], [2026, 9])) {
    const cedolino = generaCedolino(profilo, competenzaOrdinaria(anno, m), [anno, m], db.contatori);
    db.cedolini.set(cedolino.id, cedolino);
    righe.push(rigaDaCedolino(cedolino, { scattoInBusta: false, aumentoFuoriScatti: false }));
  }
  const tredicesima = generaCedolino(
    profilo,
    competenzaSpeciale(2025, 'TREDICESIMA'),
    [2025, 12],
    db.contatori,
  );
  db.cedolini.set(tredicesima.id, tredicesima);
  righe.push(rigaDaCedolino(tredicesima, { scattoInBusta: false, aumentoFuoriScatti: false }));

  return {
    id: 3,
    persona: LAURA,
    azienda: GAMMA,
    etichetta: GAMMA.ragioneSociale,
    periodo: { da: '2025-11', a: null, fonteInizio: 'DATA_CONTRATTUALE', fonteFine: 'CEDOLINI' },
    attuale: true,
    dataAssunzione: '2025-11-03',
    dataCessazione: null,
    qualifica: 'Impiegata',
    livello: '3',
    ccnl: null,
    ccnlDaIndicare: true,
    ccnlSceltoDaUtente: false,
    note: null,
    segnalazioni: [
      segnalazione(2, {
        tipo: 'CCNL_NON_DETERMINATO',
        gravita: 'INFORMATIVA',
        titolo: 'CCNL non determinato',
        descrizione:
          'Il CCNL non è stampato sui cedolini: indicalo nel riepilogo del rapporto. Finché non è indicato la RAL è ' +
          'solo stimata e non è possibile controllare quattordicesima, scatti di anzianità e livello secondo le ' +
          'regole del contratto.',
      }),
    ],
    riepilogo: {
      calcolatoIl: '2026-10-01T09:31:00Z',
      anzianita: {
        dal: '2025-11',
        al: '2026-09',
        inCorso: true,
        anni: 0,
        mesi: 11,
        fonteInizio: 'DATA_CONTRATTUALE',
        fonteFine: 'CEDOLINI',
      },
      scatti: {
        attuali: 0,
        massimo: null,
        prossimoScatto: null,
        fonteProssimo: 'NON_DISPONIBILE',
        fonteAttuali: 'CCNL',
        attualiStimati: true,
      },
      retribuzione: {
        ral: 25350,
        lordoMensile: 1950,
        mensilita: 13,
        minimo: 1650,
        contingenza: null,
        superminimo: 300,
        importoScatti: null,
        altriElementiFissi: null,
        retribuzioneBase: null,
        stimata: true,
        nota: 'CCNL non indicato: RAL calcolata su 13 mensilità.',
        cedolinoRiferimentoId: ultimaOrdinaria(righe),
      },
      buoniPasto: false,
      ratei: {
        cedolinoRiferimentoId: righe.find((r) => r.chiave === '2026.09')?.cedolinoId ?? null,
        saldi: [
          {
            tipo: 'FERIE',
            unita: 'ORE',
            saldo: 61.5,
            valoreStimato: 713.88,
            nota: 'Ore x lordo mensile / divisore orario 173 (da configurazione).',
          },
          { tipo: 'ROL', unita: 'ORE', saldo: 22, valoreStimato: null, nota: null },
        ],
      },
      tfr: {
        totaleAccumulato: 1590.12,
        quotaMensile: 144.44,
        quotaInAzienda: 1590.12,
        quotaFondi: 0,
        quotaBusta: 0,
        anticipi: 0,
        stimaNetto: 1224.39,
        aliquotaStimata: 0.23,
        stimato: true,
        riferimento: '2026-09',
        nota: 'Fondo al 31/12 non stampato: totale ricostruito sommando le quote mensili.',
        quotaMensileStimata: true,
        liquidato: false,
        importoLiquidato: null,
        nettoLiquidato: null,
      },
    },
    mensilita: righe,
  };
}

/**
 * Rinumera i cedolini di fine rapporto di un rapporto in ordine di periodo stampato (TFR01, TFR02, ...), come fa il
 * backend dopo un'eliminazione: cambiano competenza, titolo e nome del file.
 */
export function rinumeraFineRapporto(db: MockDb, rapporto: RapportoDettaglio): void {
  const fineRapporto = rapporto.mensilita
    .filter((r) => r.competenza.tipo === 'FINE_RAPPORTO' && r.cedolinoId !== null)
    .sort((a, b) => (a.periodoRetribuzione ?? '').localeCompare(b.periodoRetribuzione ?? ''));
  fineRapporto.forEach((riga, indice) => {
    const cedolino = riga.cedolinoId !== null ? db.cedolini.get(riga.cedolinoId) : undefined;
    if (!cedolino || riga.competenza.progressivoTfr === indice + 1) {
      return;
    }
    const competenza = competenzaFineRapporto(riga.competenza.anno, indice + 1);
    const titolo = titoloCompetenza(competenza, riga.periodoRetribuzione);
    const nomeFile = `${competenza.chiave}_${rapporto.azienda.id}_${rapporto.persona.id}.pdf`;
    Object.assign(cedolino, { competenza, titolo, nomeFile });
    Object.assign(riga, { chiave: competenza.chiave, competenza, titolo, nomeFile });
  });
}

/** Ordina le mensilità in ordine cronologico inverso. */
export function ordinaMensilita(righe: RigaMensilita[]): RigaMensilita[] {
  return [...righe].sort((a, b) => ordineCompetenza(b) - ordineCompetenza(a));
}

export function aggiungiRapporto(db: MockDb, rapporto: RapportoDettaglio) {
  rapporto.mensilita = ordinaMensilita(rapporto.mensilita);
  db.rapporti.set(rapporto.id, rapporto);
  if (!db.persone.some((p) => p.id === rapporto.persona.id)) {
    db.persone.push(rapporto.persona);
    db.persone.sort((a, b) => a.cognome.localeCompare(b.cognome, 'it') || a.nome.localeCompare(b.nome, 'it'));
  }
}

/** Aggiunge i dati di Mario Rossi (due aziende): usato anche dal caricamento simulato a database vuoto. */
export function popolaMario(db: MockDb): void {
  if (!db.rapporti.has(1)) {
    aggiungiRapporto(db, rapportoMario1(db));
  }
  if (!db.rapporti.has(2)) {
    aggiungiRapporto(db, rapportoMario2(db));
  }
}

/**
 * Tre file caricati ma non letti, con il PDF conservato: una scansione non associata, un PDF protetto da password e
 * una ristampa associata dall'utente alla mensilita' mancante di Agosto 2025 di Alfa Software.
 */
function aggiungiFileInErrore(db: MockDb): void {
  const contenuto = new Blob([PDF_MINIMO], { type: 'application/pdf' });
  const lotto = '5a3d2c1b-0000-4000-8000-00000000e770';
  const falliti: { nome: string; motivo: keyof typeof MESSAGGI_ERRORE; creatoIl: string }[] = [
    { nome: 'ristampa.pdf', motivo: 'LAYOUT_NON_RICONOSCIUTO', creatoIl: '2026-10-01T09:20:00Z' },
    { nome: 'cedolino-protetto.pdf', motivo: 'PDF_PROTETTO', creatoIl: '2026-10-01T09:20:05Z' },
    { nome: 'busta-scansione.pdf', motivo: 'PDF_SENZA_TESTO', creatoIl: '2026-10-01T09:20:10Z' },
  ];
  for (const { nome, motivo, creatoIl } of falliti) {
    const caricamento: Caricamento = {
      id: db.contatori.prossimoIdCaricamento++,
      lotto,
      nomeFileOriginale: nome,
      dimensione: contenuto.size,
      stato: 'ERRORE',
      messaggio: MESSAGGI_ERRORE[motivo],
      motivo,
      pdfDisponibile: true,
      cedolinoId: null,
      cedolinoEsistenteId: null,
      personaId: null,
      rapportoId: null,
      creatoIl,
      aggiornatoIl: creatoIl,
    };
    db.caricamenti.unshift(caricamento);
    db.pdfFile.set(chiaveFile(caricamento), contenuto);
    if (nome === 'ristampa.pdf') {
      db.associazioni.set(chiaveFile(caricamento), { rapportoId: 1, anno: 2025, mese: 8 });
    }
  }
}

/** Dati iniziali: dimostrativi, vuoti (primo avvio) o dimostrativi con tre cedolini caricati con errore. */
export type DatiIniziali = 'demo' | 'vuoto' | 'errori';

export function creaDbMock(datiIniziali: DatiIniziali): MockDb {
  const db: MockDb = {
    persone: [],
    rapporti: new Map(),
    cedolini: new Map(),
    contatori: { prossimoIdCedolino: 101, prossimoIdCaricamento: 1, prossimoIdAnagrafica: 101 },
    caricamenti: [],
    pdfFile: new Map(),
    associazioni: new Map(),
  };
  if (datiIniziali !== 'vuoto') {
    popolaMario(db);
    aggiungiRapporto(db, rapportoLaura(db));
  }
  if (datiIniziali === 'errori') {
    aggiungiFileInErrore(db);
  }
  return db;
}

/** Testo usato nei messaggi del caricamento simulato. */
export const MESSAGGIO_PDF_PROTETTO = 'PDF protetto da password: indica la password e ricarica';

/** PDF valido di una pagina vuota, servito come contenuto dei PDF simulati. */
export const PDF_MINIMO =
  '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n' +
  '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 595 842]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n';

/** Messaggi del backend per i motivi d'errore simulati (testi dei lettori, mai dati personali). */
export const MESSAGGI_ERRORE = {
  PDF_PROTETTO: MESSAGGIO_PDF_PROTETTO,
  PDF_SENZA_TESTO:
    "Il PDF non contiene testo selezionabile (probabilmente è una scansione o un'immagine) e il lettore locale non " +
    'può interpretarlo.',
  LAYOUT_NON_RICONOSCIUTO: 'Il lettore locale non riconosce il layout di questo cedolino.',
  PDF_NON_VALIDO: 'Il PDF è danneggiato o non leggibile',
  PIU_CEDOLINI: 'Il documento contiene più cedolini distinti: caricali come file separati.',
} as const satisfies Partial<Record<MotivoErrore, string>>;
