import type {
  AziendaSintesi,
  Caricamento,
  CategoriaVoce,
  CedolinoDettaglio,
  CedolinoManuale,
  Competenza,
  ErroreCampo,
  PersonaSintesi,
  RapportoDettaglio,
  VerificaCedolinoManuale,
  Voce,
} from '../api/types';
import {
  aggiungiRapporto,
  competenzaFineRapporto,
  competenzaOrdinaria,
  competenzaSpeciale,
  mese,
  ordinaMensilita,
  rigaDaCedolino,
  rinumeraFineRapporto,
  titoloCompetenza,
  type MockDb,
} from './dati';
import {
  datiNonValidi,
  fileInErrore,
  MESSAGGI_FILE_IN_ERRORE,
  RifiutoMock,
  rimuoviMancante,
  risolviFile,
  type FileInErrore,
} from './fileInErrore';

/**
 * Inserimento a mano del cedolino di un file in errore nel backend simulato: stessi errori bloccanti principali del
 * backend (400 con `errori` sui percorsi dello schema), duplicati e aggiuntivi (409), censimento di persona, azienda e
 * rapporto e verifica senza salvataggio.
 */

const CODICE_FISCALE_PERSONA =
  /^[A-Z]{6}[0-9LMNPQRSTUV]{2}[ABCDEHLMPRST][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]$/;
const UNDICI_CIFRE = /^\d{11}$/;
const TOLLERANZA_NETTO = 1;
const TOLLERANZA_SOMME = 0.05;
const MASSIMO_ASSOLUTO = 1e10;

/** Lunghezze massime dei testi (colonne del database). */
const LUNGHEZZE: readonly (readonly [string, number])[] = [
  ['dipendente.nome', 100],
  ['dipendente.cognome', 100],
  ['dipendente.codiceDipendente', 50],
  ['datoreLavoro.ragioneSociale', 200],
  ['datoreLavoro.indirizzo', 300],
  ['datoreLavoro.codiceAzienda', 50],
  ['datoreLavoro.posizioneInps', 50],
  ['datoreLavoro.posizioneInail', 50],
  ['datiContrattuali.qualifica', 100],
  ['datiContrattuali.livello', 50],
  ['datiContrattuali.ccnl', 300],
  ['datiContrattuali.codiceCnel', 20],
];

const LUNGHEZZE_VOCE: readonly (readonly ['codice' | 'descrizione' | 'unita', number])[] = [
  ['codice', 30],
  ['descrizione', 200],
  ['unita', 20],
];

// ------------------------------------------------------------------ normalizzazione

function testo(valore: string | null): string | null {
  const normalizzato = valore?.replace(/\s+/g, ' ').trim() ?? '';
  return normalizzato.length > 0 ? normalizzato : null;
}

function codice(valore: string | null): string | null {
  return testo(valore)?.replace(/\s/g, '').toUpperCase() ?? null;
}

/**
 * Testi senza spazi superflui (vuoti = null), codici fiscali e partita IVA maiuscoli senza spazi (senza prefisso IT),
 * flag di tredicesima e quattordicesima imposti dal tipo, importi dei buoni pasto solo se presenti.
 */
function normalizza(dati: CedolinoManuale): CedolinoManuale {
  const { dipendente, datoreLavoro, datiContrattuali, periodo, buoniPasto } = dati;
  const partitaIva = codice(datoreLavoro.partitaIva);
  return {
    ...dati,
    dipendente: {
      codiceFiscale: codice(dipendente.codiceFiscale),
      nome: testo(dipendente.nome),
      cognome: testo(dipendente.cognome),
      dataNascita: testo(dipendente.dataNascita),
      codiceDipendente: testo(dipendente.codiceDipendente),
    },
    datoreLavoro: {
      codiceFiscale: codice(datoreLavoro.codiceFiscale),
      partitaIva: partitaIva?.startsWith('IT') ? partitaIva.slice(2) : partitaIva,
      posizioneInps: testo(datoreLavoro.posizioneInps),
      posizioneInail: testo(datoreLavoro.posizioneInail),
      ragioneSociale: testo(datoreLavoro.ragioneSociale),
      indirizzo: testo(datoreLavoro.indirizzo),
      codiceAzienda: testo(datoreLavoro.codiceAzienda),
    },
    datiContrattuali: {
      dataAssunzione: testo(datiContrattuali.dataAssunzione),
      dataCessazione: testo(datiContrattuali.dataCessazione),
      qualifica: testo(datiContrattuali.qualifica),
      livello: testo(datiContrattuali.livello),
      ccnl: testo(datiContrattuali.ccnl),
      codiceCnel: testo(datiContrattuali.codiceCnel),
    },
    periodo: { ...periodo, dataElaborazione: testo(periodo.dataElaborazione) },
    voci: dati.voci.map((v) => ({
      ...v,
      codice: testo(v.codice),
      descrizione: testo(v.descrizione),
      unita: testo(v.unita),
    })),
    buoniPasto: buoniPasto.presenti
      ? buoniPasto
      : { presenti: false, numero: null, valoreUnitario: null, importo: null },
    contieneTredicesima: dati.contieneTredicesima || periodo.tipo === 'TREDICESIMA',
    contieneQuattordicesima: dati.contieneQuattordicesima || periodo.tipo === 'QUATTORDICESIMA',
  };
}

// ------------------------------------------------------------------ errori bloccanti

function valoreDi(oggetto: unknown, percorso: string): unknown {
  return percorso
    .split('.')
    .reduce<unknown>(
      (corrente, parte) =>
        typeof corrente === 'object' && corrente !== null
          ? (corrente as Record<string, unknown>)[parte]
          : undefined,
      oggetto,
    );
}

/** Tutti i numeri della richiesta con il loro percorso nello schema (es. `voci[2].base`). */
function numeri(valore: unknown, percorso: string, trovati: [string, number][] = []): [string, number][] {
  if (typeof valore === 'number') {
    trovati.push([percorso, valore]);
  } else if (Array.isArray(valore)) {
    valore.forEach((elemento: unknown, indice) => numeri(elemento, `${percorso}[${indice}]`, trovati));
  } else if (typeof valore === 'object' && valore !== null) {
    for (const [nome, figlio] of Object.entries(valore)) {
      numeri(figlio, percorso ? `${percorso}.${nome}` : nome, trovati);
    }
  }
  return trovati;
}

function fuoriLimiti(valore: number): boolean {
  const scalato = valore * 1e5;
  return (
    !Number.isFinite(valore) ||
    Math.abs(valore) >= MASSIMO_ASSOLUTO ||
    Math.abs(scalato - Math.round(scalato)) > 1e-6
  );
}

function erroriBloccanti(dati: CedolinoManuale, codiciFiscaliCensiti: Set<string>): ErroreCampo[] {
  const errori: ErroreCampo[] = [];
  const errore = (campo: string, messaggio: string) => errori.push({ campo, messaggio });
  const { periodo, dipendente, datoreLavoro, datiContrattuali, totali } = dati;
  const annoMassimo = new Date().getFullYear() + 1;

  if (periodo.tipo === null) {
    errore('periodo.tipo', 'Indica il tipo di cedolino');
  }
  if (
    periodo.anno === null ||
    !Number.isInteger(periodo.anno) ||
    periodo.anno < 1900 ||
    periodo.anno > annoMassimo
  ) {
    errore('periodo.anno', `Indica l'anno (dal 1900 al ${annoMassimo})`);
  }
  if (periodo.mese === null || !Number.isInteger(periodo.mese) || periodo.mese < 1 || periodo.mese > 12) {
    errore(
      'periodo.mese',
      'Indica il mese (da 1 a 12): tredicesima e quattordicesima si indicano con il tipo',
    );
  }
  const cf = dipendente.codiceFiscale;
  if (cf === null) {
    errore('dipendente.codiceFiscale', 'Indica il codice fiscale del dipendente');
  } else if (!CODICE_FISCALE_PERSONA.test(cf) && !UNDICI_CIFRE.test(cf) && !codiciFiscaliCensiti.has(cf)) {
    errore('dipendente.codiceFiscale', 'Codice fiscale non valido: controlla i 16 caratteri');
  }
  if (dipendente.cognome === null) {
    errore('dipendente.cognome', 'Indica il cognome');
  }
  if (dipendente.nome === null) {
    errore('dipendente.nome', 'Indica il nome');
  }
  if (datoreLavoro.ragioneSociale === null || !/[\p{L}\p{N}]/u.test(datoreLavoro.ragioneSociale)) {
    errore('datoreLavoro.ragioneSociale', 'Indica la ragione sociale');
  }
  if (datoreLavoro.partitaIva !== null && !UNDICI_CIFRE.test(datoreLavoro.partitaIva)) {
    errore('datoreLavoro.partitaIva', 'Partita IVA non valida: deve avere 11 cifre');
  }
  if (datoreLavoro.codiceFiscale !== null && !/^(\d{11}|[A-Z0-9]{16})$/.test(datoreLavoro.codiceFiscale)) {
    errore('datoreLavoro.codiceFiscale', "Codice fiscale dell'azienda non valido");
  }
  const totaliObbligatori = [
    ['totaleCompetenze', 'Indica il totale competenze'],
    ['totaleTrattenute', 'Indica il totale trattenute'],
    ['netto', 'Indica il netto'],
  ] as const;
  for (const [campo, messaggio] of totaliObbligatori) {
    if (totali[campo] === null) {
      errore(`totali.${campo}`, messaggio);
    }
  }
  for (const campo of ['totaleCompetenze', 'totaleTrattenute'] as const) {
    if ((totali[campo] ?? 0) < 0) {
      errore(`totali.${campo}`, "Indica l'importo senza segno");
    }
  }
  if (
    datiContrattuali.dataAssunzione !== null &&
    datiContrattuali.dataCessazione !== null &&
    datiContrattuali.dataCessazione < datiContrattuali.dataAssunzione
  ) {
    errore('datiContrattuali.dataCessazione', "La data di cessazione precede l'assunzione");
  }
  if (dipendente.dataNascita !== null && dipendente.dataNascita > new Date().toISOString().slice(0, 10)) {
    errore('dipendente.dataNascita', 'La data di nascita è nel futuro');
  }
  dati.voci.forEach((voce, i) => {
    if (voce.descrizione === null) {
      errore(`voci[${i}].descrizione`, 'Indica la descrizione della voce');
    }
    if (voce.categoria === null) {
      errore(`voci[${i}].categoria`, 'Indica la categoria della voce');
    }
    for (const campo of ['importoCompetenza', 'importoTrattenuta'] as const) {
      if ((voce[campo] ?? 0) < 0) {
        errore(`voci[${i}].${campo}`, "Indica l'importo senza segno");
      }
    }
    for (const [campo, massimo] of LUNGHEZZE_VOCE) {
      if ((voce[campo]?.length ?? 0) > massimo) {
        errore(`voci[${i}].${campo}`, `Testo troppo lungo (massimo ${massimo} caratteri)`);
      }
    }
  });
  const ratei = new Set<string>();
  dati.ratei.forEach((rateo, i) => {
    const chiave = `${rateo.tipo}:${rateo.unita}`;
    if (ratei.has(chiave)) {
      errore(`ratei[${i}]`, 'Rateo ripetuto: uno solo per tipo e unità');
    }
    ratei.add(chiave);
    if ([rateo.residuoAnnoPrecedente, rateo.maturato, rateo.goduto, rateo.saldo].every((v) => v === null)) {
      errore(`ratei[${i}]`, 'Indica almeno un valore del rateo');
    }
  });
  const scatti = dati.scatti.numeroScatti;
  if (scatti !== null && (!Number.isInteger(scatti) || scatti < 0 || scatti > 99)) {
    errore('scatti.numeroScatti', 'Numero di scatti non valido');
  }
  for (const [campo, valore] of numeri(dati, '')) {
    if (fuoriLimiti(valore)) {
      errore(campo, 'Valore fuori dai limiti (massimo 10 cifre intere e 5 decimali)');
    }
  }
  for (const [campo, massimo] of LUNGHEZZE) {
    const valore = valoreDi(dati, campo);
    if (typeof valore === 'string' && valore.length > massimo) {
      errore(campo, `Testo troppo lungo (massimo ${massimo} caratteri)`);
    }
  }
  return errori;
}

// ------------------------------------------------------------------ avvisi

function differisce(a: number, b: number, tolleranza: number): boolean {
  return Math.abs(a - b) > tolleranza + 1e-9;
}

/** Controlli di coerenza non bloccanti salvati con il cedolino (come il validatore e il lettore locale). */
function avvisiCoerenza(dati: CedolinoManuale): string[] {
  const avvisi: string[] = [];
  const { totaleCompetenze, totaleTrattenute, arrotondamento, netto } = dati.totali;
  if (totaleCompetenze !== null && totaleTrattenute !== null && netto !== null) {
    const differenza = totaleCompetenze - totaleTrattenute;
    const a = arrotondamento ?? 0;
    if (
      differisce(differenza + a, netto, TOLLERANZA_NETTO) &&
      differisce(differenza - a, netto, TOLLERANZA_NETTO)
    ) {
      avvisi.push('Totale competenze meno totale trattenute non corrisponde al netto');
    }
  }
  const conImporto = dati.voci.filter(
    (v) =>
      v.categoria !== 'INFORMATIVA' &&
      v.categoria !== 'PRESENZA' &&
      (v.importoCompetenza !== null || v.importoTrattenuta !== null),
  );
  if (conImporto.length > 0) {
    const somma = (campo: 'importoCompetenza' | 'importoTrattenuta') =>
      conImporto.reduce((s, v) => s + (v[campo] ?? 0), 0);
    if (
      totaleCompetenze !== null &&
      differisce(somma('importoCompetenza'), totaleCompetenze, TOLLERANZA_SOMME)
    ) {
      avvisi.push('La somma delle voci di competenza non corrisponde al totale competenze stampato');
    }
    if (
      totaleTrattenute !== null &&
      differisce(somma('importoTrattenuta'), totaleTrattenute, TOLLERANZA_SOMME)
    ) {
      avvisi.push('La somma delle voci di trattenuta non corrisponde al totale trattenute stampato');
    }
  }
  if (dati.dipendente.codiceFiscale !== null && UNDICI_CIFRE.test(dati.dipendente.codiceFiscale)) {
    avvisi.push('Codice fiscale provvisorio (11 cifre)');
  }
  return avvisi;
}

// ------------------------------------------------------------------ esito dell'acquisizione

function stessaAzienda(azienda: AziendaSintesi, dati: CedolinoManuale['datoreLavoro']): boolean {
  const nome = (valore: string | null) => valore?.toLowerCase().replace(/\s+/g, ' ').trim() ?? null;
  return (
    (azienda.partitaIva !== null && azienda.partitaIva === dati.partitaIva) ||
    (azienda.codiceFiscale !== null && azienda.codiceFiscale === dati.codiceFiscale) ||
    nome(azienda.ragioneSociale) === nome(dati.ragioneSociale)
  );
}

interface Esito {
  dati: CedolinoManuale;
  file: FileInErrore;
  persona: PersonaSintesi | undefined;
  rapporto: RapportoDettaglio | undefined;
  competenza: Competenza;
  titolo: string;
  periodo: string;
  scarto: { esito: 'DUPLICATO' | 'AGGIUNTIVO_NON_GESTITO'; messaggio: string; cedolinoId: number } | null;
}

const SEZIONI = [
  'dipendente',
  'datoreLavoro',
  'datiContrattuali',
  'periodo',
  'totali',
  'retribuzione',
  'presenze',
  'scatti',
  'buoniPasto',
  'progressivi',
] as const;

/** Struttura minima del corpo (sezioni e liste presenti), come la deserializzazione del backend. */
function formaValida(corpo: unknown): corpo is CedolinoManuale {
  if (typeof corpo !== 'object' || corpo === null) {
    return false;
  }
  const campi = corpo as Record<string, unknown>;
  return (
    SEZIONI.every((sezione) => typeof campi[sezione] === 'object' && campi[sezione] !== null) &&
    Array.isArray(campi.voci) &&
    Array.isArray(campi.ratei)
  );
}

/** Controlli comuni, errori bloccanti e acquisizione simulata, senza modificare nulla. */
function valuta(db: MockDb, caricamentoId: number, corpo: unknown): Esito {
  const file = fileInErrore(db, caricamentoId);
  if (!db.pdfFile.has(file.chiave)) {
    throw new RifiutoMock(409, MESSAGGI_FILE_IN_ERRORE.pdfNonConservato);
  }
  if (!formaValida(corpo)) {
    throw new RifiutoMock(400, 'Corpo della richiesta mancante o non valido (JSON atteso).');
  }
  const dati = normalizza(corpo);
  const errori = erroriBloccanti(dati, new Set(db.persone.map((p) => p.codiceFiscale)));
  if (errori.length > 0) {
    throw datiNonValidi(errori);
  }
  const anno = dati.periodo.anno ?? 0;
  const numeroMese = dati.periodo.mese ?? 1;
  const persona = db.persone.find((p) => p.codiceFiscale === dati.dipendente.codiceFiscale);
  const rapporto = persona
    ? [...db.rapporti.values()].find(
        (r) => r.persona.id === persona.id && stessaAzienda(r.azienda, dati.datoreLavoro),
      )
    : undefined;
  const periodo = mese(anno, numeroMese);
  let competenza: Competenza;
  switch (dati.periodo.tipo) {
    case 'TREDICESIMA':
    case 'QUATTORDICESIMA':
      competenza = competenzaSpeciale(anno, dati.periodo.tipo);
      break;
    case 'FINE_RAPPORTO': {
      const precedenti = rapporto?.mensilita.filter((r) => r.competenza.tipo === 'FINE_RAPPORTO').length ?? 0;
      competenza = competenzaFineRapporto(anno, precedenti + 1);
      break;
    }
    default:
      competenza = competenzaOrdinaria(anno, numeroMese);
  }
  const titolo = titoloCompetenza(competenza, periodo);
  const presente = rapporto?.mensilita.find((r) => r.stato === 'PRESENTE' && r.chiave === competenza.chiave);
  const esistente = presente?.cedolinoId != null ? db.cedolini.get(presente.cedolinoId) : undefined;
  let scarto: Esito['scarto'] = null;
  if (esistente) {
    const uguali = (['totaleCompetenze', 'totaleTrattenute', 'netto'] as const).every(
      (campo) => !differisce(esistente.totali[campo] ?? 0, dati.totali[campo] ?? 0, 0.005),
    );
    scarto = uguali
      ? {
          esito: 'DUPLICATO',
          messaggio: `Cedolino di ${titolo} già presente con gli stessi importi: scartato`,
          cedolinoId: esistente.id,
        }
      : {
          esito: 'AGGIUNTIVO_NON_GESTITO',
          messaggio:
            `Per ${titolo} è già presente un cedolino con importi diversi: cedolino aggiuntivo (es. premi o ` +
            'arretrati pagati a parte) non gestito',
          cedolinoId: esistente.id,
        };
  }
  return { dati, file, persona, rapporto, competenza, titolo, periodo, scarto };
}

/** Prova dell'inserimento a mano: competenza, conflitti, nuove anagrafiche e avvisi, senza salvare nulla. */
export function verificaManuale(db: MockDb, caricamentoId: number, corpo: unknown): VerificaCedolinoManuale {
  const { dati, file, persona, rapporto, competenza, titolo, scarto } = valuta(db, caricamentoId, corpo);
  const avvisi = avvisiCoerenza(dati);
  if (persona && (persona.nome !== dati.dipendente.nome || persona.cognome !== dati.dipendente.cognome)) {
    avvisi.push(
      'Nome e cognome in archivio per questo codice fiscale sono diversi: restano quelli in archivio',
    );
  }
  const associata = file.elemento.attribuzione;
  if (associata && associata.competenza.chiave !== competenza.chiave) {
    avvisi.push(`Il periodo indicato è diverso dal mese associato al file (${associata.titolo})`);
  }
  if (!persona) {
    avvisi.push('Verrà creata una nuova persona: controlla il codice fiscale');
  }
  if (!rapporto) {
    avvisi.push(
      'Verrà creato un nuovo rapporto di lavoro: se l’azienda esiste già controlla ragione sociale, partita IVA e ' +
        'codice fiscale',
    );
  }
  return {
    esito: scarto?.esito ?? 'ACQUISIBILE',
    messaggio: scarto?.messaggio ?? null,
    competenza: scarto ? null : competenza,
    titolo: scarto ? null : titolo,
    nuovaPersona: !persona,
    nuovoRapporto: !rapporto,
    rapportoId: scarto ? null : (rapporto?.id ?? null),
    cedolinoEsistenteId: scarto?.cedolinoId ?? null,
    avvisi,
  };
}

function vociDi(dati: CedolinoManuale, categoria: CategoriaVoce): Voce[] {
  return dati.voci
    .filter((v) => v.categoria === categoria)
    .map((v) => ({
      codice: v.codice,
      descrizione: v.descrizione ?? '',
      quantita: v.quantita,
      unita: v.unita,
      base: v.base,
      importoCompetenza: v.importoCompetenza,
      importoTrattenuta: v.importoTrattenuta,
    }));
}

function nuovoRapporto(db: MockDb, dati: CedolinoManuale, persona: PersonaSintesi, periodo: string) {
  const { datoreLavoro, datiContrattuali } = dati;
  const azienda: AziendaSintesi = [...db.rapporti.values()]
    .map((r) => r.azienda)
    .find((a) => stessaAzienda(a, datoreLavoro)) ?? {
    id: db.contatori.prossimoIdAnagrafica++,
    ragioneSociale: datoreLavoro.ragioneSociale ?? '',
    partitaIva: datoreLavoro.partitaIva,
    codiceFiscale: datoreLavoro.codiceFiscale,
    posizioneInps: datoreLavoro.posizioneInps,
    indirizzo: datoreLavoro.indirizzo,
  };
  const rapporto: RapportoDettaglio = {
    id: db.contatori.prossimoIdAnagrafica++,
    persona,
    azienda,
    etichetta: azienda.ragioneSociale,
    periodo: {
      da: datiContrattuali.dataAssunzione?.slice(0, 7) ?? periodo,
      a: datiContrattuali.dataCessazione?.slice(0, 7) ?? null,
      fonteInizio: datiContrattuali.dataAssunzione !== null ? 'DATA_CONTRATTUALE' : 'CEDOLINI',
      fonteFine: datiContrattuali.dataCessazione !== null ? 'DATA_CONTRATTUALE' : 'CEDOLINI',
    },
    attuale: datiContrattuali.dataCessazione === null,
    dataAssunzione: datiContrattuali.dataAssunzione,
    dataCessazione: datiContrattuali.dataCessazione,
    qualifica: datiContrattuali.qualifica,
    livello: datiContrattuali.livello,
    ccnl: null,
    ccnlDaIndicare: true,
    ccnlSceltoDaUtente: false,
    note: null,
    segnalazioni: [],
    riepilogo: null,
    mensilita: [],
  };
  aggiungiRapporto(db, rapporto);
  return rapporto;
}

/**
 * Inserimento a mano: il cedolino entra nel rapporto (persona, azienda e rapporto creati se nuovi), la mensilita'
 * mancante o in errore lascia il posto alla presente e il tentativo indicato diventa ACQUISITO. Un duplicato o un
 * aggiuntivo viene rifiutato con 409 e il file resta in errore.
 */
export function inserisciManuale(db: MockDb, caricamentoId: number, corpo: unknown): Caricamento {
  const valutazione = valuta(db, caricamentoId, corpo);
  const { dati, file, competenza, titolo, periodo, scarto } = valutazione;
  if (scarto) {
    throw new RifiutoMock(409, scarto.messaggio);
  }
  const persona: PersonaSintesi = valutazione.persona ?? {
    id: db.contatori.prossimoIdAnagrafica++,
    nome: dati.dipendente.nome ?? '',
    cognome: dati.dipendente.cognome ?? '',
    codiceFiscale: dati.dipendente.codiceFiscale ?? '',
    etichetta: `${dati.dipendente.nome ?? ''} ${dati.dipendente.cognome ?? ''}`,
  };
  const rapporto = valutazione.rapporto ?? nuovoRapporto(db, dati, persona, periodo);
  const adesso = new Date().toISOString();
  const cedolino: CedolinoDettaglio = {
    id: db.contatori.prossimoIdCedolino++,
    rapportoId: rapporto.id,
    titolo,
    competenza,
    periodoRetribuzione: periodo,
    meseVariabili: dati.periodo.meseVariabili,
    dataElaborazione: dati.periodo.dataElaborazione,
    nomeFile: `${competenza.chiave}_${rapporto.azienda.id}_${persona.id}.pdf`,
    acquisitoIl: adesso,
    confidenza: null,
    inseritoAMano: true,
    campiNonTrovati: [],
    avvisiEstrazione: avvisiCoerenza(dati),
    inquadramento: {
      qualifica: dati.datiContrattuali.qualifica,
      livello: dati.datiContrattuali.livello,
      codiceDipendente: dati.dipendente.codiceDipendente,
      ccnlTesto: dati.datiContrattuali.ccnl,
      dataAssunzione: dati.datiContrattuali.dataAssunzione,
      dataCessazione: dati.datiContrattuali.dataCessazione,
    },
    totali: dati.totali,
    elementiRetributivi: dati.retribuzione,
    presenze: { ...dati.presenze, voci: vociDi(dati, 'PRESENZA') },
    competenze: vociDi(dati, 'COMPETENZA'),
    trattenutePrevidenziali: vociDi(dati, 'TRATTENUTA_PREVIDENZIALE'),
    trattenuteFiscali: vociDi(dati, 'TRATTENUTA_FISCALE'),
    altreTrattenute: vociDi(dati, 'ALTRA_TRATTENUTA'),
    vociNonRetributive: vociDi(dati, 'NON_RETRIBUTIVA'),
    vociInformative: vociDi(dati, 'INFORMATIVA'),
    tfr: dati.tfr,
    ratei: dati.ratei,
    scatti: { numeroScatti: dati.scatti.numeroScatti, prossimoScatto: dati.scatti.dataProssimoScatto },
    buoniPasto: dati.buoniPasto,
    progressivi: dati.progressivi,
    contieneTredicesima: dati.contieneTredicesima,
    contieneQuattordicesima: dati.contieneQuattordicesima,
  };
  db.cedolini.set(cedolino.id, cedolino);
  rimuoviMancante(rapporto, competenza.chiave);
  rapporto.mensilita = ordinaMensilita([
    ...rapporto.mensilita,
    rigaDaCedolino(cedolino, { scattoInBusta: false, aumentoFuoriScatti: false }),
  ]);
  rinumeraFineRapporto(db, rapporto);

  Object.assign(file.caricamento, {
    stato: 'ACQUISITO',
    messaggio: null,
    motivo: null,
    cedolinoId: cedolino.id,
    cedolinoEsistenteId: null,
    personaId: persona.id,
    rapportoId: rapporto.id,
    aggiornatoIl: adesso,
  } satisfies Partial<Caricamento>);
  risolviFile(db, file.chiave);
  return { ...file.caricamento };
}
