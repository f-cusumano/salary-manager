import type { AziendaSelezionabile, AziendaSintesi, PersonaSintesi } from '../api/types';
import type { MockDb } from './dati';

/**
 * Elenchi delle anagrafiche del backend simulato con le etichette anti-omonimia del backend (Etichette): persona con
 * il codice fiscale, azienda con partita IVA, poi codice fiscale, poi posizione INPS, solo se un'altra ha lo stesso
 * nome. Le aziende sono quelle dei rapporti (nel backend ogni azienda ne ha almeno uno).
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

/**
 * Ragione sociale confrontabile (versione semplificata di RagioneSociale.normalizza del backend): maiuscolo, senza
 * accenti, punteggiatura e spazi, cosi' "Alfa Software S.r.l." e "ALFA SOFTWARE SRL" coincidono.
 */
export function ragioneSocialeNormalizzata(ragioneSociale: string | null): string {
  return maiuscoloSenzaAccenti(ragioneSociale ?? '').replace(/[^A-Z0-9]+/g, '');
}

function conteggi<T>(elementi: readonly T[], chiave: (elemento: T) => string): Map<string, number> {
  const risultato = new Map<string, number>();
  for (const elemento of elementi) {
    risultato.set(chiave(elemento), (risultato.get(chiave(elemento)) ?? 0) + 1);
  }
  return risultato;
}

/** GET /api/v1/persone: "Nome Cognome", con il codice fiscale per gli omonimi. */
export function elencoPersone(db: MockDb): PersonaSintesi[] {
  const omonimi = conteggi(db.persone, chiaveNome);
  return db.persone.map((p) => {
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
  const [x, y] = [a.ragioneSociale.toLocaleLowerCase('it'), b.ragioneSociale.toLocaleLowerCase('it')];
  return x < y ? -1 : x > y ? 1 : a.id - b.id;
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
