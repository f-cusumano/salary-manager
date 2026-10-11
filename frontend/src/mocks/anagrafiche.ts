import type { AziendaSelezionabile, AziendaSintesi, PersonaSintesi } from '../api/types';
import type { MockDb } from './dati';

/**
 * Elenchi delle anagrafiche del backend simulato con le etichette anti-omonimia del backend (Etichette): persona con
 * il codice fiscale, azienda con partita IVA, poi codice fiscale, poi posizione INPS, solo se un'altra ha lo stesso
 * nome. Le aziende sono quelle dei rapporti (nel backend ogni azienda ne ha almeno uno). Qui anche le regole del
 * censimento che servono al riconoscimento di un'azienda gia' in archivio (RagioneSociale, IdentificativiAzienda e
 * AbbinamentoAzienda del backend).
 */

function maiuscoloSenzaAccenti(testo: string): string {
  return testo.normalize('NFD').replace(/\p{M}/gu, '').toUpperCase();
}

/** Nome e cognome confrontabili tra omonimi: maiuscolo, senza accenti, punteggiatura e spazi ripetuti. */
function chiaveNome(persona: Pick<PersonaSintesi, 'nome' | 'cognome'>): string {
  return maiuscoloSenzaAccenti(`${persona.nome} ${persona.cognome}`)
    .replace(/[^A-Z0-9]+/g, ' ')
    .trim();
}

// ------------------------------------------------------------------ ragione sociale (RagioneSociale del backend)

const LUNGHEZZA_RAGIONE_SOCIALE = 200;

/** Forme societarie e forma canonica: le sequenze piu' lunghe prima di quelle che contengono. */
const FORME_SOCIETARIE: readonly (readonly [string, readonly string[]])[] = (
  [
    ['SCARL', 'SOCIETA COOPERATIVA A RESPONSABILITA LIMITATA'],
    ['SCARL', 'SOC COOP A RESPONSABILITA LIMITATA'],
    ['SCARL', 'SOC COOP A RL'],
    ['SCARL', 'SOC COOP ARL'],
    ['SCARL', 'SC A RL'],
    ['SCARL', 'SCRL'],
    ['SC', 'SOCIETA COOPERATIVA'],
    ['SC', 'SOC COOP'],
    ['SRL', 'SOCIETA A RESPONSABILITA LIMITATA SEMPLIFICATA'],
    ['SRL', 'SOCIETA A RESPONSABILITA LIMITATA'],
    ['SRL', 'SRLSU'],
    ['SRL', 'SRLS'],
    ['SRL', 'SRLU'],
    ['SAPA', 'SOCIETA IN ACCOMANDITA PER AZIONI'],
    ['SPA', 'SOCIETA PER AZIONI'],
    ['SPA', 'SPASU'],
    ['SAS', 'SOCIETA IN ACCOMANDITA SEMPLICE'],
    ['SNC', 'SOCIETA IN NOME COLLETTIVO'],
    ['SS', 'SOCIETA SEMPLICE'],
  ] as const
).map(([canonica, parole]) => [canonica, parole.split(' ')] as const);

/** Indicazioni che non identificano l'azienda (socio unico, liquidazione). */
const DA_RIMUOVERE: readonly (readonly string[])[] = [
  'A SOCIO UNICO',
  'CON SOCIO UNICO',
  'SOCIO UNICO',
  'UNIPERSONALE',
  'IN LIQUIDAZIONE',
].map((sequenza) => sequenza.split(' '));

/** Sigle societarie che possono comparire puntate e spaziate. */
const SIGLE = new Set([
  'SRL',
  'SRLS',
  'SRLSU',
  'SRLU',
  'SPA',
  'SPASU',
  'SAPA',
  'SAS',
  'SNC',
  'SS',
  'SC',
  'SCARL',
  'SCRL',
  'ARL',
  'SU',
]);

/** Forme canoniche dopo le quali un "SU" isolato significa "socio unico". */
const FORME_CON_SOCIO_UNICO = ['SRL', 'SPA'];

function indiceDi(parole: readonly string[], sequenza: readonly string[]): number {
  for (let i = 0; i + sequenza.length <= parole.length; i++) {
    if (sequenza.every((parola, j) => parole[i + j] === parola)) {
      return i;
    }
  }
  return -1;
}

function rimuoviIndicazioni(parole: readonly string[]): string[] {
  const risultato = [...parole];
  for (const sequenza of DA_RIMUOVERE) {
    for (let i = indiceDi(risultato, sequenza); i >= 0; i = indiceDi(risultato, sequenza)) {
      risultato.splice(i, sequenza.length);
    }
  }
  return risultato;
}

/** Sigle puntate e spaziate ("S R L") ricompattate: la coda piu' lunga di lettere singole che forma una sigla nota. */
function ricompattaSigle(parole: readonly string[]): string[] {
  const risultato: string[] = [];
  let lettere: string[] = [];
  const chiudiSigla = () => {
    let inizio = lettere.length;
    for (let i = 0; i < lettere.length - 1; i++) {
      if (SIGLE.has(lettere.slice(i).join(''))) {
        inizio = i;
        break;
      }
    }
    risultato.push(...lettere.slice(0, inizio));
    if (inizio < lettere.length) {
      risultato.push(lettere.slice(inizio).join(''));
    }
    lettere = [];
  };
  for (const parola of parole) {
    if (/^[A-Z]$/.test(parola)) {
      lettere.push(parola);
    } else {
      chiudiSigla();
      risultato.push(parola);
    }
  }
  chiudiSigla();
  return risultato;
}

function uniformaForme(parole: readonly string[]): string[] {
  const risultato = [...parole];
  for (const [canonica, sequenza] of FORME_SOCIETARIE) {
    for (let i = indiceDi(risultato, sequenza); i >= 0; i = indiceDi(risultato, sequenza)) {
      risultato.splice(i, sequenza.length, canonica);
    }
  }
  return risultato;
}

function rimuoviSocioUnico(parole: readonly string[]): string[] {
  const risultato: string[] = [];
  for (const parola of parole) {
    const precedente = risultato.at(-1);
    if (!(parola === 'SU' && precedente !== undefined && FORME_CON_SOCIO_UNICO.includes(precedente))) {
      risultato.push(parola);
    }
  }
  return risultato;
}

/**
 * Ragione sociale confrontabile, come RagioneSociale.normalizza del backend: maiuscolo senza accenti e punteggiatura,
 * sigle puntate ricompattate, forme societarie uniformate e "socio unico" o "in liquidazione" tolti, cosi' "Alfa
 * Software S.r.l.", "ALFA SOFTWARE SRL" e "Alfa Software Società a responsabilità limitata" coincidono. '' se vuota.
 */
export function ragioneSocialeNormalizzata(ragioneSociale: string | null): string {
  if (ragioneSociale === null || ragioneSociale.trim() === '') {
    return '';
  }
  const testo = maiuscoloSenzaAccenti(ragioneSociale)
    .replaceAll('&', ' E ')
    .replaceAll('.', '')
    .replace(/[^A-Z0-9]+/g, ' ')
    .trim();
  if (testo === '') {
    return '';
  }
  const parole = rimuoviSocioUnico(
    uniformaForme(rimuoviIndicazioni(ricompattaSigle(rimuoviIndicazioni(testo.split(' '))))),
  );
  const normalizzata = parole.join(' ');
  return normalizzata.length > LUNGHEZZA_RAGIONE_SOCIALE
    ? normalizzata.slice(0, LUNGHEZZA_RAGIONE_SOCIALE).trim()
    : normalizzata;
}

// ------------------------------------------------------------------ identificativi (IdentificativiAzienda del backend)

/**
 * Chiave della posizione INPS come la salva il backend: il primo gruppo di esattamente 10 cifre (la matricola, anche
 * stampata con la posizione INAIL), altrimenti le prime 10 cifre di un gruppo piu' lungo o di tutte le cifre, tutte le
 * cifre se meno di 10, il testo alfanumerico maiuscolo senza cifre; null se vuota.
 */
export function normalizzaPosizioneInps(posizioneInps: string | null): string | null {
  if (posizioneInps === null || posizioneInps.trim() === '') {
    return null;
  }
  const gruppi = posizioneInps.match(/[0-9]+/g) ?? [];
  const matricola = gruppi.find((g) => g.length === 10) ?? gruppi.find((g) => g.length > 10)?.slice(0, 10);
  if (matricola !== undefined) {
    return matricola;
  }
  const cifre = gruppi.join('');
  if (cifre !== '') {
    return cifre.slice(0, 10);
  }
  const alfanumerico = maiuscoloSenzaAccenti(posizioneInps).replace(/[^A-Z0-9]+/g, '');
  return alfanumerico === '' ? null : alfanumerico.slice(0, 30);
}

type IdentificativiAzienda = Pick<AziendaSintesi, 'partitaIva' | 'codiceFiscale' | 'posizioneInps'>;

/** Codice fiscale e partita IVA, confrontabili in modo incrociato. */
function fiscali(azienda: IdentificativiAzienda): Set<string> {
  return new Set([azienda.codiceFiscale, azienda.partitaIva].filter((id): id is string => id !== null));
}

const UNDICI_CIFRE = /^\d{11}$/;

/** Valori di tipo partita IVA: la partita IVA e il codice fiscale numerico. */
function numeriPartitaIva(azienda: IdentificativiAzienda): string[] {
  return [
    azienda.partitaIva,
    UNDICI_CIFRE.test(azienda.codiceFiscale ?? '') ? azienda.codiceFiscale : null,
  ].filter((id): id is string => id !== null);
}

/**
 * Identificativi fiscali che dimostrano due aziende diverse (AbbinamentoAzienda.conflittoFiscale): nessuno in comune
 * e codici fiscali alfanumerici da entrambe le parti, o numeri di partita IVA da entrambe le parti.
 */
function conflittoFiscale(a: IdentificativiAzienda, b: IdentificativiAzienda): boolean {
  const fiscaliB = fiscali(b);
  if ([...fiscali(a)].some((id) => fiscaliB.has(id))) {
    return false;
  }
  const alfanumerico = (cf: string | null) => cf !== null && !UNDICI_CIFRE.test(cf);
  return (
    (alfanumerico(a.codiceFiscale) && alfanumerico(b.codiceFiscale)) ||
    (numeriPartitaIva(a).length > 0 && numeriPartitaIva(b).length > 0)
  );
}

/** Dato che fa riconoscere un'azienda gia' in archivio (AziendaRiconosciuta.Dato del backend). */
export type DatoRiconosciuto = 'partitaIva' | 'codiceFiscale' | 'posizioneInps' | 'ragioneSociale';

/**
 * Dato per cui il censimento del backend (AbbinamentoAzienda) abbinerebbe un'azienda letta a una in archivio, null se
 * la creerebbe: identificativi fiscali in comune (vince l'azienda con piu' identificativi in comune, poi la prima
 * registrata; 'partitaIva' se la partita IVA letta e' tra i suoi identificativi), poi la stessa matricola INPS senza
 * identificativi fiscali in conflitto, poi la stessa ragione sociale normalizzata senza identificativi fiscali o
 * matricola INPS in conflitto (la prima registrata).
 *
 * @param letta dati gia' normalizzati: codice fiscale e partita IVA maiuscoli e senza prefisso IT, posizione INPS come
 *              la scrive l'utente
 */
export function aziendaRiconosciuta(
  aziende: readonly AziendaSintesi[],
  letta: IdentificativiAzienda & Pick<AziendaSintesi, 'ragioneSociale'>,
): DatoRiconosciuto | null {
  const profilo = { ...letta, posizioneInps: normalizzaPosizioneInps(letta.posizioneInps) };
  const perId = [...aziende].sort((a, b) => a.id - b.id);
  const fiscaliLetti = fiscali(profilo);
  const inComune = (azienda: AziendaSintesi) =>
    [...fiscali(azienda)].filter((id) => fiscaliLetti.has(id)).length;
  const perFiscale = perId
    .filter((a) => inComune(a) > 0)
    .reduce<AziendaSintesi | null>(
      (migliore, a) => (migliore && inComune(migliore) >= inComune(a) ? migliore : a),
      null,
    );
  if (perFiscale) {
    return profilo.partitaIva !== null && fiscali(perFiscale).has(profilo.partitaIva)
      ? 'partitaIva'
      : 'codiceFiscale';
  }
  const { posizioneInps } = profilo;
  if (
    posizioneInps !== null &&
    perId.some((a) => a.posizioneInps === posizioneInps && !conflittoFiscale(profilo, a))
  ) {
    return 'posizioneInps';
  }
  const nome = ragioneSocialeNormalizzata(profilo.ragioneSociale);
  const omonima = perId.some(
    (a) =>
      ragioneSocialeNormalizzata(a.ragioneSociale) === nome &&
      !conflittoFiscale(profilo, a) &&
      !(posizioneInps !== null && a.posizioneInps !== null && a.posizioneInps !== posizioneInps),
  );
  return omonima ? 'ragioneSociale' : null;
}

function conteggi<T>(elementi: readonly T[], chiave: (elemento: T) => string): Map<string, number> {
  const risultato = new Map<string, number>();
  for (const elemento of elementi) {
    risultato.set(chiave(elemento), (risultato.get(chiave(elemento)) ?? 0) + 1);
  }
  return risultato;
}

/** Confronto di testi in minuscolo come String.compareTo del backend (unita' UTF-16). */
function confronta(a: string, b: string): number {
  const [x, y] = [a.toLocaleLowerCase('it'), b.toLocaleLowerCase('it')];
  return x < y ? -1 : x > y ? 1 : 0;
}

/** GET /api/v1/persone: per cognome e nome (poi id), "Nome Cognome" con il codice fiscale per gli omonimi. */
export function elencoPersone(db: MockDb): PersonaSintesi[] {
  const omonimi = conteggi(db.persone, chiaveNome);
  const ordinate = [...db.persone].sort(
    (a, b) => confronta(a.cognome, b.cognome) || confronta(a.nome, b.nome) || a.id - b.id,
  );
  return ordinate.map((p) => {
    const base = `${p.nome} ${p.cognome}`;
    return { ...p, etichetta: (omonimi.get(chiaveNome(p)) ?? 0) > 1 ? `${base} (${p.codiceFiscale})` : base };
  });
}

/** Aziende in archivio (quelle dei rapporti), una volta sola ciascuna. */
export function aziendeInArchivio(db: MockDb): AziendaSintesi[] {
  const perId = new Map<number, AziendaSintesi>();
  for (const rapporto of db.rapporti.values()) {
    perId.set(rapporto.azienda.id, rapporto.azienda);
  }
  return [...perId.values()];
}

function distintivo(azienda: AziendaSintesi): string {
  if (azienda.partitaIva !== null) {
    return ` (P.IVA ${azienda.partitaIva})`;
  }
  if (azienda.codiceFiscale !== null) {
    return ` (CF ${azienda.codiceFiscale})`;
  }
  return azienda.posizioneInps !== null ? ` (Pos. INPS ${azienda.posizioneInps})` : '';
}

/** Ordine del backend: ragione sociale in minuscolo (confronto dei caratteri, come String.compareTo), poi id. */
function perRagioneSociale(a: AziendaSintesi, b: AziendaSintesi): number {
  return confronta(a.ragioneSociale, b.ragioneSociale) || a.id - b.id;
}

/** GET /api/v1/aziende: per ragione sociale (poi id), etichette calcolate su tutte le aziende. */
export function elencoAziende(db: MockDb): AziendaSelezionabile[] {
  const aziende = aziendeInArchivio(db);
  const omonime = conteggi(aziende, (a) => ragioneSocialeNormalizzata(a.ragioneSociale));
  return aziende.sort(perRagioneSociale).map((a) => ({
    id: a.id,
    ragioneSociale: a.ragioneSociale,
    partitaIva: a.partitaIva,
    codiceFiscale: a.codiceFiscale,
    posizioneInps: a.posizioneInps,
    indirizzo: a.indirizzo,
    codiceAzienda: null,
    etichetta:
      (omonime.get(ragioneSocialeNormalizzata(a.ragioneSociale)) ?? 0) > 1
        ? `${a.ragioneSociale}${distintivo(a)}`
        : a.ragioneSociale,
  }));
}
