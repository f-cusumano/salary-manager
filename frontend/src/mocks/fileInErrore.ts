import type {
  AttribuzioneCaricamento,
  Caricamento,
  CaricamentoInErrore,
  Competenza,
  ErroreCampo,
  MotivoErrore,
  RapportoDettaglio,
  RigaMensilita,
  Segnalazione,
} from '../api/types';
import { STATI_TERMINALI } from '../api/types';
import {
  chiaveFile,
  competenzaOrdinaria,
  competenzaSpeciale,
  mese,
  ordinaMensilita,
  titoloCompetenza,
  type MockDb,
} from './dati';

/**
 * Cedolini caricati con errore nel backend simulato: stesso predicato "file in errore" del backend (ultimo tentativo
 * concluso del file in ERRORE), PDF conservati, associazioni alle mensilita' e loro effetto su mensilita' e
 * segnalazioni dei rapporti. Il file e' identificato da nome + dimensione (chiaveFile) al posto dell'hash.
 */

/** Rifiuto di una richiesta: diventa un Problem Details con lo stato indicato. */
export class RifiutoMock extends Error {
  readonly status: number;
  readonly errori: ErroreCampo[] | null;

  constructor(status: number, dettaglio: string, errori: ErroreCampo[] | null = null) {
    super(dettaglio);
    this.name = 'RifiutoMock';
    this.status = status;
    this.errori = errori;
  }
}

/** Errori di validazione (400 con `errori`): un solo errore usa il suo messaggio come dettaglio. */
export function datiNonValidi(errori: ErroreCampo[]): RifiutoMock {
  const [unico] = errori;
  return new RifiutoMock(
    400,
    errori.length === 1 && unico ? unico.messaggio : `${errori.length} campi da correggere`,
    errori,
  );
}

/** Messaggi dei rifiuti come nel backend (MessaggiCaricamento). */
export const MESSAGGI_FILE_IN_ERRORE = {
  nonInErrore: 'Il file non è più in errore: è già stato inserito, rimosso o ricaricato',
  inElaborazione: 'Il file è in elaborazione: attendi la fine',
  pdfNonConservato: 'PDF del file non conservato: caricalo di nuovo',
  competenzaGiaPresente: (titolo: string) => `Per ${titolo} è già presente un cedolino`,
  competenzaGiaAssociata: (titolo: string) => `${titolo} è già associato a un altro file in errore`,
};

/** MotivoErrore.descrizione() del backend, usata nelle segnalazioni. */
export const DESCRIZIONE_MOTIVO: Record<MotivoErrore, string> = {
  PDF_PROTETTO: 'Protetto da password',
  PDF_SENZA_TESTO: 'Scansione senza testo',
  LAYOUT_NON_RICONOSCIUTO: 'Layout non riconosciuto',
  LETTURA_NON_RIUSCITA: 'Lettura non riuscita',
  DATI_INCOMPLETI: 'Dati essenziali mancanti',
  PDF_TROPPO_GRANDE: 'PDF troppo grande per la lettura',
  SERVIZIO_NON_DISPONIBILE: 'Lettore non disponibile',
  INTERROTTO: 'Elaborazione interrotta',
  ERRORE_IMPREVISTO: 'Errore imprevisto',
  PDF_NON_VALIDO: 'File danneggiato o non PDF',
  PDF_TROPPE_PAGINE: 'Troppe pagine',
  PIU_CEDOLINI: 'Più cedolini nello stesso PDF',
};

/** Motivi per cui il PDF non viene conservato (mai servito di nuovo, non completabile a mano). */
const MOTIVI_SENZA_PDF: readonly MotivoErrore[] = ['PDF_NON_VALIDO', 'PDF_TROPPE_PAGINE', 'PIU_CEDOLINI'];

const TIPI_MANCANTI: readonly Segnalazione['tipo'][] = [
  'MENSILITA_MANCANTE',
  'TREDICESIMA_MANCANTE',
  'QUATTORDICESIMA_MANCANTE',
];

/** Allinea pdfDisponibile dei tentativi del file: true solo per quelli in ERRORE con il PDF conservato. */
function sincronizzaPdf(db: MockDb, chiave: string): void {
  const presente = db.pdfFile.has(chiave);
  for (const c of db.caricamenti) {
    if (chiaveFile(c) === chiave) {
      c.pdfDisponibile = c.stato === 'ERRORE' && presente;
    }
  }
}

/** Il file e' archiviato o scartato: PDF conservato e associazione non servono piu'. */
export function risolviFile(db: MockDb, chiave: string): void {
  db.pdfFile.delete(chiave);
  db.associazioni.delete(chiave);
  sincronizzaPdf(db, chiave);
}

/**
 * Conservazione del PDF alla conclusione di un tentativo, come ChiusuraCaricamento del backend: in ERRORE il PDF
 * resta (se il motivo lo consente) per inserimento a mano e rilettura; ogni altro esito risolve il file.
 */
export function chiudiTentativo(db: MockDb, caricamento: Caricamento, contenuto: Blob): void {
  const chiave = chiaveFile(caricamento);
  if (caricamento.stato !== 'ERRORE') {
    risolviFile(db, chiave);
    return;
  }
  if (caricamento.motivo !== null && !MOTIVI_SENZA_PDF.includes(caricamento.motivo)) {
    if (!db.pdfFile.has(chiave)) {
      db.pdfFile.set(chiave, contenuto);
    }
  } else {
    db.pdfFile.delete(chiave);
  }
  sincronizzaPdf(db, chiave);
}

function tentativi(db: MockDb, chiave: string): Caricamento[] {
  return db.caricamenti.filter((c) => chiaveFile(c) === chiave);
}

/** Ultimo tentativo concluso del file (id piu' alto tra quelli in uno stato terminale). */
function ultimoConcluso(db: MockDb, chiave: string): Caricamento | undefined {
  return tentativi(db, chiave)
    .filter((c) => STATI_TERMINALI.includes(c.stato))
    .reduce<Caricamento | undefined>(
      (ultimo, c) => (ultimo === undefined || c.id > ultimo.id ? c : ultimo),
      undefined,
    );
}

function inElaborazione(db: MockDb, chiave: string): boolean {
  return tentativi(db, chiave).some((c) => !STATI_TERMINALI.includes(c.stato));
}

/** Mensilita' 1..12 ordinaria, 13 tredicesima, 14 quattordicesima. */
function competenzaDiMese(anno: number, m: number): Competenza {
  if (m === 13) {
    return competenzaSpeciale(anno, 'TREDICESIMA');
  }
  if (m === 14) {
    return competenzaSpeciale(anno, 'QUATTORDICESIMA');
  }
  return competenzaOrdinaria(anno, m);
}

function attribuzioneDelFile(db: MockDb, chiave: string): AttribuzioneCaricamento | null {
  const associazione = db.associazioni.get(chiave);
  const rapporto = associazione ? db.rapporti.get(associazione.rapportoId) : undefined;
  if (!associazione || !rapporto) {
    return null;
  }
  const competenza = competenzaDiMese(associazione.anno, associazione.mese);
  return {
    rapportoId: rapporto.id,
    personaId: rapporto.persona.id,
    persona: rapporto.persona.etichetta,
    azienda: rapporto.etichetta,
    competenza,
    titolo: titoloCompetenza(competenza, null),
    competenzaPresente: rapporto.mensilita.some(
      (r) => r.stato === 'PRESENTE' && r.chiave === competenza.chiave,
    ),
  };
}

function comeElemento(db: MockDb, caricamento: Caricamento): CaricamentoInErrore {
  const chiave = chiaveFile(caricamento);
  return {
    id: caricamento.id,
    lotto: caricamento.lotto,
    nomeFileOriginale: caricamento.nomeFileOriginale,
    dimensione: caricamento.dimensione,
    motivo: caricamento.motivo,
    messaggio: caricamento.messaggio ?? '',
    pdfDisponibile: db.pdfFile.has(chiave),
    inElaborazione: inElaborazione(db, chiave),
    attribuzione: attribuzioneDelFile(db, chiave),
    creatoIl: caricamento.creatoIl,
  };
}

/** File in errore, uno per file (l'ultimo tentativo concluso, in ERRORE), dal piu' recente. */
export function elencoInErrore(db: MockDb): CaricamentoInErrore[] {
  const chiavi = new Set(db.caricamenti.map(chiaveFile));
  return [...chiavi]
    .flatMap((chiave) => {
      const ultimo = ultimoConcluso(db, chiave);
      return ultimo?.stato === 'ERRORE' ? [comeElemento(db, ultimo)] : [];
    })
    .sort((a, b) => b.creatoIl.localeCompare(a.creatoIl) || b.id - a.id);
}

export interface FileInErrore {
  /** Tentativo indicato nella richiesta (in ERRORE). */
  caricamento: Caricamento;
  chiave: string;
  elemento: CaricamentoInErrore;
}

/**
 * Controlli comuni alle azioni su un file in errore: 404 se il caricamento non esiste, 409 se il tentativo non e' in
 * ERRORE, se il file non e' piu' in errore o se un nuovo tentativo e' in corso.
 */
export function fileInErrore(db: MockDb, caricamentoId: number): FileInErrore {
  const caricamento = db.caricamenti.find((c) => c.id === caricamentoId);
  if (!caricamento) {
    throw new RifiutoMock(404, `Caricamento inesistente (id ${caricamentoId}).`);
  }
  const chiave = chiaveFile(caricamento);
  const ultimo = ultimoConcluso(db, chiave);
  if (caricamento.stato !== 'ERRORE' || ultimo?.stato !== 'ERRORE') {
    throw new RifiutoMock(409, MESSAGGI_FILE_IN_ERRORE.nonInErrore);
  }
  if (inElaborazione(db, chiave)) {
    throw new RifiutoMock(409, MESSAGGI_FILE_IN_ERRORE.inElaborazione);
  }
  return { caricamento, chiave, elemento: comeElemento(db, ultimo) };
}

/** Elemento aggiornato del file dopo un'azione (associazione o sua rimozione). */
function elementoDelFile(db: MockDb, chiave: string): CaricamentoInErrore | undefined {
  return elencoInErrore(db).find((e) => chiaveFile(e) === chiave);
}

/** Associa il file a una mensilita' del rapporto, con i controlli del backend (400, 404, 409). */
export function associa(db: MockDb, caricamentoId: number, corpo: unknown): CaricamentoInErrore {
  const { rapportoId, anno, mese: m } = (corpo ?? {}) as Record<string, unknown>;
  const errori: ErroreCampo[] = [];
  const annoMassimo = new Date().getFullYear() + 1;
  if (typeof anno !== 'number' || !Number.isInteger(anno) || anno < 1900 || anno > annoMassimo) {
    errori.push({ campo: 'anno', messaggio: `Indica l'anno (dal 1900 al ${annoMassimo})` });
  }
  if (typeof m !== 'number' || !Number.isInteger(m) || m < 1 || m > 14) {
    errori.push({ campo: 'mese', messaggio: 'Indica il mese (da 1 a 14)' });
  }
  if (errori.length > 0) {
    throw datiNonValidi(errori);
  }
  const rapporto = typeof rapportoId === 'number' ? db.rapporti.get(rapportoId) : undefined;
  if (!rapporto) {
    throw new RifiutoMock(404, `Rapporto di lavoro inesistente (id ${String(rapportoId)}).`);
  }
  const { chiave } = fileInErrore(db, caricamentoId);
  const competenza = competenzaDiMese(anno as number, m as number);
  const titolo = titoloCompetenza(competenza, null);
  if (rapporto.mensilita.some((r) => r.stato === 'PRESENTE' && r.chiave === competenza.chiave)) {
    throw new RifiutoMock(409, MESSAGGI_FILE_IN_ERRORE.competenzaGiaPresente(titolo));
  }
  const occupata = [...db.associazioni].some(
    ([altro, a]) => altro !== chiave && a.rapportoId === rapporto.id && a.anno === anno && a.mese === m,
  );
  if (occupata) {
    throw new RifiutoMock(409, MESSAGGI_FILE_IN_ERRORE.competenzaGiaAssociata(titolo));
  }
  db.associazioni.set(chiave, { rapportoId: rapporto.id, anno: anno as number, mese: m as number });
  return elementoAggiornato(db, chiave);
}

export function rimuoviAssociazione(db: MockDb, caricamentoId: number): CaricamentoInErrore {
  const { chiave } = fileInErrore(db, caricamentoId);
  db.associazioni.delete(chiave);
  return elementoAggiornato(db, chiave);
}

function elementoAggiornato(db: MockDb, chiave: string): CaricamentoInErrore {
  const elemento = elementoDelFile(db, chiave);
  if (!elemento) {
    throw new Error("file in errore non piu' elencato");
  }
  return elemento;
}

/** Rimuove il file: tutti i suoi tentativi in ERRORE, il PDF conservato e l'associazione. */
export function rimuovi(db: MockDb, caricamentoId: number): void {
  const { chiave } = fileInErrore(db, caricamentoId);
  db.caricamenti = db.caricamenti.filter((c) => !(chiaveFile(c) === chiave && c.stato === 'ERRORE'));
  risolviFile(db, chiave);
}

/** Il rapporto e' stato eliminato: le sue associazioni spariscono con lui (ON DELETE CASCADE). */
export function eliminaAssociazioniDelRapporto(db: MockDb, rapportoId: number): void {
  for (const [chiave, associazione] of db.associazioni) {
    if (associazione.rapportoId === rapportoId) {
      db.associazioni.delete(chiave);
    }
  }
}

function rigaInErrore(elemento: CaricamentoInErrore, competenza: Competenza, titolo: string): RigaMensilita {
  return {
    chiave: competenza.chiave,
    stato: 'IN_ERRORE',
    titolo,
    competenza,
    periodoRetribuzione: competenza.tipo === 'ORDINARIA' ? mese(competenza.anno, competenza.mese ?? 1) : null,
    cedolinoId: null,
    netto: null,
    lordo: null,
    ratei: [],
    scattoInBusta: false,
    aumentoFuoriScatti: false,
    nomeFile: null,
    caricamentoId: elemento.id,
    motivoErrore: elemento.motivo,
  };
}

function segnalazioneInErrore(
  elemento: CaricamentoInErrore,
  competenza: Competenza,
  titolo: string,
): Segnalazione {
  return {
    id: 1000 + elemento.id,
    tipo: 'CEDOLINO_IN_ERRORE',
    gravita: 'AVVISO',
    titolo: `Cedolino caricato con errore: ${titolo}`,
    descrizione:
      `Il cedolino di ${titolo} è stato caricato ma non è stato letto` +
      (elemento.motivo !== null ? ` (${DESCRIZIONE_MOTIVO[elemento.motivo]})` : '') +
      ': inseriscilo a mano dall’elenco «Cedolini caricati con errore» o riprova la lettura.',
    cedolinoId: null,
    competenza,
    evidenze:
      elemento.motivo !== null
        ? [
            {
              descrizione: 'Motivo',
              cedolinoId: null,
              competenza: null,
              valore: DESCRIZIONE_MOTIVO[elemento.motivo],
            },
          ]
        : [],
  };
}

/**
 * Dettaglio del rapporto come lo compone il backend: ogni mensilita' senza cedolino con un file in errore associato
 * diventa una riga IN_ERRORE (al posto della MANCANTE, anche fuori dal periodo atteso) e la sua segnalazione di
 * mensilita' mancante lascia il posto a CEDOLINO_IN_ERRORE. Rimossa l'associazione, tutto torna com'era.
 */
export function rapportoConFileInErrore(db: MockDb, rapporto: RapportoDettaglio): RapportoDettaglio {
  const associati = elencoInErrore(db).flatMap((elemento) => {
    const a = elemento.attribuzione;
    return a?.rapportoId === rapporto.id && !a.competenzaPresente ? [{ elemento, a }] : [];
  });
  if (associati.length === 0) {
    return rapporto;
  }
  const chiavi = new Set(associati.map(({ a }) => a.competenza.chiave));
  return {
    ...rapporto,
    mensilita: ordinaMensilita([
      ...rapporto.mensilita.filter((r) => !(r.stato === 'MANCANTE' && chiavi.has(r.chiave))),
      ...associati.map(({ elemento, a }) => rigaInErrore(elemento, a.competenza, a.titolo)),
    ]),
    segnalazioni: [
      ...associati.map(({ elemento, a }) => segnalazioneInErrore(elemento, a.competenza, a.titolo)),
      ...rapporto.segnalazioni.filter(
        (s) => !(TIPI_MANCANTI.includes(s.tipo) && s.competenza !== null && chiavi.has(s.competenza.chiave)),
      ),
    ],
  };
}

/** Toglie dal rapporto la riga e la segnalazione di mensilita' mancante della competenza (cedolino acquisito). */
export function rimuoviMancante(rapporto: RapportoDettaglio, chiave: string): void {
  rapporto.mensilita = rapporto.mensilita.filter((r) => !(r.stato !== 'PRESENTE' && r.chiave === chiave));
  rapporto.segnalazioni = rapporto.segnalazioni.filter(
    (s) => !(TIPI_MANCANTI.includes(s.tipo) && s.competenza?.chiave === chiave),
  );
}
