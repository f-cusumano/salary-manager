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
import { codiceFiscaleValido, partitaIvaValida } from '../hooks/useCedolinoManuale';
import { aziendaRiconosciuta, aziendeInArchivio, normalizzaPosizioneInps } from './anagrafiche';
import {
  aggiungiRapporto,
  chiaveFile,
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
 * backend (400 con `errori` sui percorsi dello schema), duplicati e aggiuntivi (409), persona e azienda scelte
 * dall'elenco (dati anagrafici in archivio) o nuove (rifiutate se gia' in archivio), rapporto e verifica senza
 * salvataggio.
 */

const UNDICI_CIFRE = /^\d{11}$/;
const TOLLERANZA_NETTO = 1;
const TOLLERANZA_SOMME = 0.05;
const MASSIMO_ASSOLUTO = 1e8;
const PRIMO_ANNO = 1900;
const ULTIMO_ANNO_DATE = 2999;
const DATA_ISO = /^(\d{4})-(\d{2})-(\d{2})$/;
const MESE_ISO = /^\d{4}-(0[1-9]|1[0-2])$/;
const MESSAGGIO_DATA = `Data non valida (anno dal ${PRIMO_ANNO} al ${ULTIMO_ANNO_DATE})`;

/** Caratteri di controllo ISO, come Character.isISOControl del backend. */
function contieneCaratteriDiControllo(testo: string): boolean {
  for (const carattere of testo) {
    const codice = carattere.codePointAt(0) ?? 0;
    if (codice <= 0x1f || (codice >= 0x7f && codice <= 0x9f)) {
      return true;
    }
  }
  return false;
}

/** Testi con la loro lunghezza massima (colonne del database). */
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

/** Date del cedolino (ISO, anno nell'intervallo ammesso dal database). */
const DATE = [
  'dipendente.dataNascita',
  'datiContrattuali.dataAssunzione',
  'datiContrattuali.dataCessazione',
  'periodo.dataElaborazione',
  'scatti.dataProssimoScatto',
] as const;

/**
 * Campi nell'ordine in cui il backend li controlla (ordine dello schema): gli errori escono in quest'ordine. Le righe
 * di voci e ratei seguono il loro indice e poi l'ordine dei loro campi.
 */
const ORDINE_CAMPI = [
  'dipendente.codiceFiscale',
  'dipendente.nome',
  'dipendente.cognome',
  'dipendente.dataNascita',
  'dipendente.codiceDipendente',
  'datoreLavoro.codiceFiscale',
  'datoreLavoro.partitaIva',
  'datoreLavoro.posizioneInps',
  'datoreLavoro.posizioneInail',
  'datoreLavoro.ragioneSociale',
  'datoreLavoro.indirizzo',
  'datoreLavoro.codiceAzienda',
  'datiContrattuali.dataAssunzione',
  'datiContrattuali.dataCessazione',
  'datiContrattuali.qualifica',
  'datiContrattuali.livello',
  'datiContrattuali.ccnl',
  'datiContrattuali.codiceCnel',
  'periodo.anno',
  'periodo.mese',
  'periodo.tipo',
  'periodo.dataElaborazione',
  'periodo.meseVariabili',
  'totali.lordo',
  'totali.totaleCompetenze',
  'totali.totaleTrattenute',
  'totali.arrotondamento',
  'totali.netto',
  'retribuzione.minimo',
  'retribuzione.contingenza',
  'retribuzione.superminimo',
  'retribuzione.importoScatti',
  'retribuzione.altriElementiFissi',
  'retribuzione.retribuzioneBase',
  'retribuzione.totale',
  'retribuzione.pagaOraria',
  'retribuzione.pagaGiornaliera',
  'retribuzione.divisoreOrario',
  'retribuzione.divisoreGiornaliero',
  'voci',
  'ratei',
  'tfr.fondo31DicembreAnnoPrecedente',
  'tfr.rivalutazione',
  'tfr.impostaSostitutiva',
  'tfr.quotaAnno',
  'tfr.quotaMese',
  'tfr.quotaFondi',
  'tfr.quotaBusta',
  'tfr.anticipi',
  'tfr.retribuzioneUtile',
  'tfr.liquidatoLordo',
  'tfr.ritenuta',
  'presenze.giorniLavorati',
  'presenze.oreLavorate',
  'presenze.giorniRetribuiti',
  'scatti.numeroScatti',
  'scatti.dataProssimoScatto',
  'buoniPasto.numero',
  'buoniPasto.valoreUnitario',
  'buoniPasto.importo',
  'progressivi.imponibilePrevidenziale',
  'progressivi.imponibileIrpef',
  'progressivi.irpefTrattenuta',
];
const CAMPI_VOCE = [
  'codice',
  'descrizione',
  'categoria',
  'quantita',
  'unita',
  'base',
  'importoCompetenza',
  'importoTrattenuta',
];
/** Campi di un rateo; '' e' il rateo intero (ripetuto, senza valori). */
const CAMPI_RATEO = ['tipo', 'unita', '', 'residuoAnnoPrecedente', 'maturato', 'goduto', 'saldo'];

// ------------------------------------------------------------------ normalizzazione

function testo(valore: string | null): string | null {
  const normalizzato = valore?.replace(/\s+/g, ' ').trim() ?? '';
  return normalizzato.length > 0 ? normalizzato : null;
}

function codice(valore: string | null): string | null {
  return testo(valore)?.replace(/\s/g, '').toUpperCase() ?? null;
}

/** Codice dell'azienda senza il prefisso IT davanti a 11 cifre, come il backend. */
function senzaPrefissoIt(valore: string | null): string | null {
  return valore !== null && /^IT\d{11}$/.test(valore) ? valore.slice(2) : valore;
}

/**
 * Testi senza spazi superflui (vuoti = null), codici fiscali e partita IVA maiuscoli senza spazi (quelli dell'azienda
 * senza prefisso IT davanti a 11 cifre), flag di tredicesima e quattordicesima imposti dal tipo, importi dei buoni
 * pasto solo se presenti.
 */
function normalizza(dati: CedolinoManuale): CedolinoManuale {
  const { dipendente, datoreLavoro, datiContrattuali, periodo, buoniPasto } = dati;
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
      codiceFiscale: senzaPrefissoIt(codice(datoreLavoro.codiceFiscale)),
      partitaIva: senzaPrefissoIt(codice(datoreLavoro.partitaIva)),
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

// ------------------------------------------------------------------ anagrafiche scelte

/** Testi del backend (MessaggiCaricamento) per le anagrafiche scelte o gia' in archivio. */
const MESSAGGI_ANAGRAFICHE = {
  personaNonInArchivio: "La persona scelta non è più in archivio: scegline un'altra o creane una nuova",
  aziendaNonInArchivio: "L'azienda scelta non è più in archivio: scegline un'altra o creane una nuova",
  codiceFiscaleInArchivio: "Questo codice fiscale è già in archivio: seleziona la persona dall'elenco",
  'datoreLavoro.partitaIva': "Questa partita IVA è già in archivio: seleziona l'azienda dall'elenco",
  'datoreLavoro.codiceFiscale': "Questo codice fiscale è già in archivio: seleziona l'azienda dall'elenco",
  'datoreLavoro.posizioneInps': "Questa posizione INPS è già in archivio: seleziona l'azienda dall'elenco",
  'datoreLavoro.ragioneSociale':
    "Un'azienda con questa ragione sociale è già in archivio: selezionala dall'elenco",
} as const;

/** Persona e azienda scelte dall'elenco (undefined = nuove). */
interface Scelte {
  persona: PersonaSintesi | undefined;
  azienda: AziendaSintesi | undefined;
}

/** Le anagrafiche indicate per id, se ancora in archivio: altrimenti 400 con i soli errori della scelta. */
function risolviScelte(db: MockDb, dati: CedolinoManuale): Scelte {
  const { personaId, aziendaId } = dati;
  const errori: ErroreCampo[] = [];
  const persona = personaId != null ? db.persone.find((p) => p.id === personaId) : undefined;
  if (personaId != null && !persona) {
    errori.push({ campo: 'personaId', messaggio: MESSAGGI_ANAGRAFICHE.personaNonInArchivio });
  }
  const azienda = aziendaId != null ? aziendeInArchivio(db).find((a) => a.id === aziendaId) : undefined;
  if (aziendaId != null && !azienda) {
    errori.push({ campo: 'aziendaId', messaggio: MESSAGGI_ANAGRAFICHE.aziendaNonInArchivio });
  }
  if (errori.length > 0) {
    throw datiNonValidi(errori);
  }
  return { persona, azienda };
}

/** I dati anagrafici delle anagrafiche scelte sono quelli in archivio, qualunque cosa sia stata inviata. */
function conDatiInArchivio(dati: CedolinoManuale, { persona, azienda }: Scelte): CedolinoManuale {
  return {
    ...dati,
    dipendente: persona
      ? {
          ...dati.dipendente,
          codiceFiscale: persona.codiceFiscale,
          nome: persona.nome,
          cognome: persona.cognome,
          dataNascita: persona.dataNascita ?? dati.dipendente.dataNascita,
        }
      : dati.dipendente,
    datoreLavoro: azienda
      ? {
          ...dati.datoreLavoro,
          ragioneSociale: azienda.ragioneSociale,
          partitaIva: azienda.partitaIva,
          codiceFiscale: azienda.codiceFiscale,
          posizioneInps: azienda.posizioneInps,
        }
      : dati.datoreLavoro,
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

/** Cifre decimali del numero come scritto (rappresentazione decimale piu' breve, anche esponenziale). */
function decimaliDi(valore: number): number {
  const [mantissa = '', esponente = '0'] = Math.abs(valore).toString().split('e');
  return Math.max(0, (mantissa.split('.')[1] ?? '').length - Number(esponente));
}

function fuoriLimiti(valore: number): boolean {
  return !Number.isFinite(valore) || Math.abs(valore) >= MASSIMO_ASSOLUTO || decimaliDi(valore) > 5;
}

/** Posizione di un campo nell'ordine dei controlli del backend. */
function posizione(campo: string): [number, number, number] {
  const riga = /^(voci|ratei)\[(\d+)\]\.?(.*)$/.exec(campo);
  if (riga) {
    const [, lista = '', indice = '0', sottocampo = ''] = riga;
    const campi = lista === 'voci' ? CAMPI_VOCE : CAMPI_RATEO;
    return [ORDINE_CAMPI.indexOf(lista), Number(indice), campi.indexOf(sottocampo)];
  }
  return [ORDINE_CAMPI.indexOf(campo), 0, 0];
}

function confrontaPosizioni(a: string, b: string): number {
  const [pa, pb] = [posizione(a), posizione(b)];
  return pa[0] - pb[0] || pa[1] - pb[1] || pa[2] - pb[2];
}

function annoFuoriIntervallo(valore: string): boolean {
  const anno = Number(valore.slice(0, 4));
  return anno < PRIMO_ANNO || anno > ULTIMO_ANNO_DATE;
}

/**
 * Errori bloccanti come li calcola il backend: al piu' un errore per campo (il primo rilevato, quindi le regole di
 * uno stesso campo seguono l'ordine del backend) e campi nell'ordine dello schema. I dati anagrafici di persona e
 * azienda scelte non si controllano (sono quelli in archivio); quelli di una persona o di un'azienda nuova non possono
 * essere gia' in archivio.
 */
function erroriBloccanti(db: MockDb, dati: CedolinoManuale, scelte: Scelte): ErroreCampo[] {
  const trovati = new Map<string, string>();
  const errore = (campo: string, messaggio: string) => {
    if (!trovati.has(campo)) {
      trovati.set(campo, messaggio);
    }
  };
  const testoValido = (campo: string, valore: unknown, massimo: number) => {
    if (typeof valore !== 'string') {
      return;
    }
    if (contieneCaratteriDiControllo(valore)) {
      errore(campo, 'Il testo contiene caratteri non ammessi');
    } else if (valore.length > massimo) {
      errore(campo, `Testo troppo lungo (massimo ${massimo} caratteri)`);
    }
  };
  const { periodo, dipendente, datoreLavoro, datiContrattuali, totali } = dati;
  const annoMassimo = new Date().getFullYear() + 1;

  if (periodo.tipo === null) {
    errore('periodo.tipo', 'Indica il tipo di cedolino');
  }
  if (
    periodo.anno === null ||
    !Number.isInteger(periodo.anno) ||
    periodo.anno < PRIMO_ANNO ||
    periodo.anno > annoMassimo
  ) {
    errore('periodo.anno', `Indica l'anno (dal ${PRIMO_ANNO} al ${annoMassimo})`);
  }
  if (periodo.mese === null || !Number.isInteger(periodo.mese) || periodo.mese < 1 || periodo.mese > 12) {
    errore(
      'periodo.mese',
      'Indica il mese (da 1 a 12): tredicesima e quattordicesima si indicano con il tipo',
    );
  }
  if (periodo.meseVariabili !== null && annoFuoriIntervallo(periodo.meseVariabili)) {
    errore('periodo.meseVariabili', MESSAGGIO_DATA);
  }
  const cf = dipendente.codiceFiscale;
  if (!scelte.persona) {
    if (cf === null) {
      errore('dipendente.codiceFiscale', 'Indica il codice fiscale del dipendente');
    } else if (db.persone.some((p) => p.codiceFiscale === cf)) {
      errore('dipendente.codiceFiscale', MESSAGGI_ANAGRAFICHE.codiceFiscaleInArchivio);
    } else if (!codiceFiscaleValido(cf) && !UNDICI_CIFRE.test(cf)) {
      errore('dipendente.codiceFiscale', 'Codice fiscale non valido: controlla i 16 caratteri');
    }
    if (dipendente.cognome === null) {
      errore('dipendente.cognome', 'Indica il cognome');
    }
    if (dipendente.nome === null) {
      errore('dipendente.nome', 'Indica il nome');
    }
  }
  if (!scelte.azienda) {
    if (datoreLavoro.ragioneSociale === null || !/[\p{L}\p{N}]/u.test(datoreLavoro.ragioneSociale)) {
      errore('datoreLavoro.ragioneSociale', 'Indica la ragione sociale');
    }
    if (datoreLavoro.partitaIva !== null && !UNDICI_CIFRE.test(datoreLavoro.partitaIva)) {
      errore('datoreLavoro.partitaIva', 'Partita IVA non valida: deve avere 11 cifre');
    }
    if (datoreLavoro.codiceFiscale !== null && !/^(\d{11}|[A-Z0-9]{16})$/.test(datoreLavoro.codiceFiscale)) {
      errore('datoreLavoro.codiceFiscale', "Codice fiscale dell'azienda non valido");
    }
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
  for (const campo of DATE) {
    const valore = valoreDi(dati, campo);
    if (typeof valore === 'string' && annoFuoriIntervallo(valore)) {
      errore(campo, MESSAGGIO_DATA);
    }
  }
  dati.voci.forEach((voce, i) => {
    testoValido(`voci[${i}].codice`, voce.codice, 30);
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
      testoValido(`voci[${i}].${campo}`, voce[campo], massimo);
    }
  });
  const ratei = new Set<string>();
  dati.ratei.forEach((rateo, i) => {
    // il corpo JSON puo' portare null anche dove il tipo non li prevede
    const tipo = rateo.tipo as string | null;
    const unita = rateo.unita as string | null;
    if (tipo === null) {
      errore(`ratei[${i}].tipo`, 'Indica il tipo del rateo');
    }
    if (unita === null) {
      errore(`ratei[${i}].unita`, "Indica l'unità del rateo");
    }
    const chiave = `${tipo}:${unita}`;
    if (tipo !== null && unita !== null && ratei.has(chiave)) {
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
      errore(campo, 'Valore fuori dai limiti (massimo 8 cifre intere e 5 decimali)');
    }
  }
  for (const [campo, massimo] of LUNGHEZZE) {
    testoValido(campo, valoreDi(dati, campo), massimo);
  }
  // un'azienda nuova che il censimento riconoscerebbe va scelta dall'elenco (solo con i dati del datore corretti)
  if (!scelte.azienda && ![...trovati.keys()].some((campo) => campo.startsWith('datoreLavoro.'))) {
    const dato = aziendaRiconosciuta(aziendeInArchivio(db), {
      ...datoreLavoro,
      ragioneSociale: datoreLavoro.ragioneSociale ?? '',
    });
    if (dato !== null) {
      errore(`datoreLavoro.${dato}`, MESSAGGI_ANAGRAFICHE[`datoreLavoro.${dato}`]);
    }
  }
  return [...trovati]
    .sort(([a], [b]) => confrontaPosizioni(a, b))
    .map(([campo, messaggio]) => ({ campo, messaggio }));
}

// ------------------------------------------------------------------ avvisi

function differisce(a: number, b: number, tolleranza: number): boolean {
  return Math.abs(a - b) > tolleranza + 1e-9;
}

/** Primo anno plausibile di un cedolino (ValidatoreCedolinoEstratto del backend). */
const PRIMO_ANNO_PLAUSIBILE = 1990;
const TOLLERANZA_RATEI = 0.05;

/**
 * Controlli di coerenza non bloccanti salvati con il cedolino, con i testi e nell'ordine del backend: quelli del
 * validatore di ogni lettura (anno, identificativi, totali, date, ratei), poi le somme delle voci e il codice fiscale
 * provvisorio dell'inserimento a mano. Non simula la rimozione dei dati bancari dai testi.
 */
function avvisiCoerenza(dati: CedolinoManuale): string[] {
  const avvisi: string[] = [];
  const { periodo, dipendente, datoreLavoro, datiContrattuali, totali } = dati;
  if (periodo.anno !== null && periodo.anno < PRIMO_ANNO_PLAUSIBILE) {
    avvisi.push('Anno del periodo di retribuzione non plausibile');
  }
  if (dipendente.codiceFiscale !== null && !codiceFiscaleValido(dipendente.codiceFiscale)) {
    avvisi.push('Codice fiscale del dipendente formalmente non valido');
  }
  if (datoreLavoro.partitaIva !== null && !partitaIvaValida(datoreLavoro.partitaIva)) {
    avvisi.push("Partita IVA dell'azienda formalmente non valida");
  }
  const cfAzienda = datoreLavoro.codiceFiscale;
  if (cfAzienda !== null && !partitaIvaValida(cfAzienda) && !codiceFiscaleValido(cfAzienda)) {
    avvisi.push("Codice fiscale dell'azienda formalmente non valido");
  }
  const { totaleCompetenze, totaleTrattenute, arrotondamento, netto } = totali;
  if (totaleCompetenze !== null && totaleTrattenute !== null && netto !== null) {
    const differenza = totaleCompetenze - totaleTrattenute;
    if (
      differisce(differenza + (arrotondamento ?? 0), netto, TOLLERANZA_NETTO) &&
      differisce(differenza, netto, TOLLERANZA_NETTO)
    ) {
      avvisi.push('Totale competenze meno totale trattenute non corrisponde al netto');
    }
  }
  const { dataAssunzione, dataCessazione } = datiContrattuali;
  if (
    dipendente.dataNascita !== null &&
    dataAssunzione !== null &&
    dipendente.dataNascita >= dataAssunzione
  ) {
    avvisi.push("Data di nascita non precedente all'assunzione");
  }
  if (periodo.tipo === 'FINE_RAPPORTO') {
    if (dataCessazione === null) {
      avvisi.push('Cedolino di fine rapporto senza data di cessazione stampata');
    } else if (
      periodo.anno !== null &&
      periodo.mese !== null &&
      mese(periodo.anno, periodo.mese) <= dataCessazione.slice(0, 7)
    ) {
      avvisi.push('Cedolino di fine rapporto con periodo non successivo al mese di cessazione');
    }
  }
  for (const rateo of dati.ratei) {
    const { residuoAnnoPrecedente, maturato, goduto, saldo } = rateo;
    if (
      residuoAnnoPrecedente !== null &&
      maturato !== null &&
      goduto !== null &&
      saldo !== null &&
      differisce(residuoAnnoPrecedente + maturato - goduto, saldo, TOLLERANZA_RATEI)
    ) {
      avvisi.push(`Saldo del rateo ${rateo.tipo} (${rateo.unita}) diverso da residuo + maturato - goduto`);
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
  if (dipendente.codiceFiscale !== null && UNDICI_CIFRE.test(dipendente.codiceFiscale)) {
    avvisi.push('Codice fiscale provvisorio (11 cifre)');
  }
  return avvisi;
}

// ------------------------------------------------------------------ esito dell'acquisizione

interface Esito {
  dati: CedolinoManuale;
  file: FileInErrore;
  /** Persona e azienda scelte (undefined = da creare). */
  persona: PersonaSintesi | undefined;
  azienda: AziendaSintesi | undefined;
  /** Rapporto della coppia scelta, se esiste. */
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

/** Data ISO esistente nel calendario (AAAA-MM-GG), come la legge la deserializzazione del backend. */
function dataEsistente(valore: string): boolean {
  const parti = DATA_ISO.exec(valore);
  if (!parti) {
    return false;
  }
  const [anno, mese, giorno] = parti.slice(1).map(Number) as [number, number, number];
  const data = new Date(Date.UTC(anno, mese - 1, giorno));
  return data.getUTCFullYear() === anno && data.getUTCMonth() === mese - 1 && data.getUTCDate() === giorno;
}

/** Id facoltativo (persona o azienda scelta): assente, null o intero, come un Long del backend. */
function idValido(valore: unknown): boolean {
  return valore === null || valore === undefined || Number.isInteger(valore);
}

/**
 * Struttura minima del corpo (sezioni e liste presenti, id interi, date e mese delle variabili leggibili), come la
 * deserializzazione del backend.
 */
function formaValida(corpo: unknown): corpo is CedolinoManuale {
  if (typeof corpo !== 'object' || corpo === null) {
    return false;
  }
  const campi = corpo as Record<string, unknown>;
  const meseVariabili = valoreDi(corpo, 'periodo.meseVariabili');
  return (
    idValido(campi.personaId) &&
    idValido(campi.aziendaId) &&
    SEZIONI.every((sezione) => typeof campi[sezione] === 'object' && campi[sezione] !== null) &&
    Array.isArray(campi.voci) &&
    Array.isArray(campi.ratei) &&
    DATE.every((campo) => {
      const valore = valoreDi(corpo, campo);
      return valore === null || valore === undefined || (typeof valore === 'string' && dataEsistente(valore));
    }) &&
    (meseVariabili === null ||
      meseVariabili === undefined ||
      (typeof meseVariabili === 'string' && MESE_ISO.test(meseVariabili)))
  );
}

/** Controlli comuni, errori bloccanti e acquisizione simulata, senza modificare nulla. */
function valuta(db: MockDb, caricamentoId: number, corpo: unknown): Esito {
  // come il backend: prima il corpo (400), poi le anagrafiche scelte e i campi (400), infine il file (404, 409)
  if (!formaValida(corpo)) {
    throw new RifiutoMock(400, 'Corpo della richiesta mancante o non valido (JSON atteso).');
  }
  const scelte = risolviScelte(db, corpo);
  const dati = normalizza(conDatiInArchivio(corpo, scelte));
  const errori = erroriBloccanti(db, dati, scelte);
  if (errori.length > 0) {
    throw datiNonValidi(errori);
  }
  const file = fileInErrore(db, caricamentoId);
  if (!db.pdfFile.has(file.chiave)) {
    throw new RifiutoMock(409, MESSAGGI_FILE_IN_ERRORE.pdfNonConservato);
  }
  const anno = dati.periodo.anno ?? 0;
  const numeroMese = dati.periodo.mese ?? 1;
  const { persona, azienda } = scelte;
  // persona e azienda nuove non hanno rapporti; scelte entrambe, il cedolino va al loro rapporto (o a uno nuovo)
  const rapporto =
    persona && azienda
      ? [...db.rapporti.values()].find((r) => r.persona.id === persona.id && r.azienda.id === azienda.id)
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
  return { dati, file, persona, azienda, rapporto, competenza, titolo, periodo, scarto };
}

/** Prova dell'inserimento a mano: competenza, conflitti, nuove anagrafiche e avvisi, senza salvare nulla. */
export function verificaManuale(db: MockDb, caricamentoId: number, corpo: unknown): VerificaCedolinoManuale {
  const { dati, file, persona, azienda, rapporto, competenza, titolo, scarto } = valuta(
    db,
    caricamentoId,
    corpo,
  );
  // avvisi di contesto (non salvati) dopo quelli di coerenza, nell'ordine del backend
  const avvisi = avvisiCoerenza(dati);
  const associata = file.elemento.attribuzione;
  if (associata && associata.competenza.chiave !== competenza.chiave) {
    avvisi.push(`Il periodo indicato è diverso dal mese associato al file (${associata.titolo})`);
  }
  if (associata && associata.rapportoId !== rapporto?.id) {
    avvisi.push(`Persona o azienda diverse da quelle del mese associato al file (${associata.titolo})`);
  }
  if (!persona) {
    avvisi.push('Verrà creata una nuova persona: controlla il codice fiscale');
  }
  if (!azienda) {
    avvisi.push('Verrà creata una nuova azienda: controlla ragione sociale, partita IVA e codice fiscale');
  }
  if (persona && azienda && !rapporto) {
    avvisi.push(
      "Verrà creato un nuovo rapporto di lavoro: la persona scelta non ha ancora cedolini con l'azienda scelta",
    );
  }
  return {
    esito: scarto?.esito ?? 'ACQUISIBILE',
    messaggio: scarto?.messaggio ?? null,
    competenza: scarto ? null : competenza,
    titolo: scarto ? null : titolo,
    nuovaPersona: !persona,
    nuovaAzienda: !azienda,
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

function nuovoRapporto(
  db: MockDb,
  dati: CedolinoManuale,
  persona: PersonaSintesi,
  azienda: AziendaSintesi,
  periodo: string,
) {
  const { datiContrattuali } = dati;
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
 * Inserimento a mano: il cedolino entra nel rapporto della persona e dell'azienda scelte (persona, azienda e rapporto
 * creati se nuovi), la mensilita' mancante o in errore lascia il posto alla presente e il tentativo indicato diventa
 * ACQUISITO. Un duplicato o un aggiuntivo viene rifiutato con 409 e il file resta in errore.
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
    dataNascita: dati.dipendente.dataNascita,
    etichetta: `${dati.dipendente.nome ?? ''} ${dati.dipendente.cognome ?? ''}`,
  };
  const { datoreLavoro } = dati;
  const azienda: AziendaSintesi = valutazione.azienda ?? {
    id: db.contatori.prossimoIdAnagrafica++,
    ragioneSociale: datoreLavoro.ragioneSociale ?? '',
    partitaIva: datoreLavoro.partitaIva,
    codiceFiscale: datoreLavoro.codiceFiscale,
    posizioneInps: normalizzaPosizioneInps(datoreLavoro.posizioneInps),
    indirizzo: datoreLavoro.indirizzo,
  };
  const rapporto = valutazione.rapporto ?? nuovoRapporto(db, dati, persona, azienda, periodo);
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
  // i tentativi successivi dello stesso file finiti in errore diventano duplicati del cedolino appena archiviato:
  // eliminato il cedolino, il file non torna in errore (come AcquisizioneCedolino del backend)
  for (const tentativo of db.caricamenti) {
    if (
      tentativo.id > file.caricamento.id &&
      tentativo.stato === 'ERRORE' &&
      chiaveFile(tentativo) === file.chiave
    ) {
      Object.assign(tentativo, {
        stato: 'DUPLICATO',
        messaggio: MESSAGGI_FILE_IN_ERRORE.fileGiaCaricato,
        motivo: null,
        cedolinoEsistenteId: cedolino.id,
        aggiornatoIl: adesso,
      } satisfies Partial<Caricamento>);
    }
  }
  risolviFile(db, file.chiave);
  return { ...file.caricamento };
}
