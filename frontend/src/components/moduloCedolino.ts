import type { FormErrors, UseFormReturnType } from '@mantine/form';
import { createContext, useContext, useSyncExternalStore } from 'react';
import { unisciSeVuoti, type ValoriCedolinoManuale, type ValoriParziali } from '../hooks/useCedolinoManuale';
import { testi } from '../i18n';

/** Modulo dell'inserimento a mano (@mantine/form, modalita' non controllata). */
export type ModuloCedolino = UseFormReturnType<
  ValoriCedolinoManuale,
  ValoriCedolinoManuale,
  (valori: ValoriCedolinoManuale) => FormErrors
>;

/**
 * Valori correnti del modulo osservabili dai componenti che ne mostrano un riassunto (titolo previsto, somme delle
 * voci, controlli, conteggi delle sezioni): nella modalita' non controllata i campi non fanno ridisegnare il modulo,
 * cosi' a ogni tasto si ridisegnano solo questi componenti, e solo se il dato che leggono cambia.
 */
export interface NegozioValori {
  leggi: () => ValoriCedolinoManuale;
  imposta: (valori: ValoriCedolinoManuale) => void;
  sottoscrivi: (ascoltatore: () => void) => () => void;
}

export function creaNegozioValori(iniziali: ValoriCedolinoManuale): NegozioValori {
  let valori = iniziali;
  const ascoltatori = new Set<() => void>();
  return {
    leggi: () => valori,
    imposta: (nuovi) => {
      valori = nuovi;
      ascoltatori.forEach((ascoltatore) => ascoltatore());
    },
    sottoscrivi: (ascoltatore) => {
      ascoltatori.add(ascoltatore);
      return () => {
        ascoltatori.delete(ascoltatore);
      };
    },
  };
}

/** Scelta "Crea nuovo" nelle tendine di persona e azienda (gli id dell'elenco sono numerici). */
export const ANAGRAFICA_NUOVA = 'nuova';

/** Lettura dei dati del rapporto della coppia scelta, per precompilare i campi che seguono. */
export type LetturaRapporto = { stato: 'inCorso' } | { stato: 'errore'; errore: unknown } | null;

export interface ContestoModulo {
  form: ModuloCedolino;
  negozio: NegozioValori;
  /** Campi compilati in automatico (percorso del modulo => nota "Copiato da …: verifica"). */
  copiati: Readonly<Record<string, string>>;
  /** L'utente ha modificato un campo compilato in automatico: il segno non serve piu'. */
  rimuoviCopiato: (percorso: string) => void;
  /**
   * Scelta della persona: id dell'elenco, ANAGRAFICA_NUOVA ("Crea nuovo") o null (di nuovo da scegliere). I dati
   * anagrafici e i campi che seguono vengono precompilati di conseguenza.
   */
  scegliPersona: (scelta: string | null) => void;
  /** Scelta dell'azienda, come scegliPersona. */
  scegliAzienda: (scelta: string | null) => void;
  letturaRapporto: LetturaRapporto;
  riprovaLetturaRapporto: () => void;
  /** Cambio del tipo di cedolino (il TFR si apre da solo per i cedolini di fine rapporto). */
  onTipoCambiato: (tipo: string | null) => void;
}

export const ModuloCedolinoContext = createContext<ContestoModulo | null>(null);

export function useModulo(): ContestoModulo {
  const contesto = useContext(ModuloCedolinoContext);
  if (!contesto) {
    throw new Error('useModulo richiede ModuloCedolinoContext (fornito da ModuloCedolinoManuale)');
  }
  return contesto;
}

type Semplice = string | number | boolean | null;

/**
 * Dato derivato dai valori correnti del modulo, aggiornato a ogni modifica. Il selettore deve restituire un valore
 * semplice (stringa, numero, booleano o null): il componente si ridisegna solo quando cambia.
 */
export function useValoreModulo<T extends Semplice>(selettore: (valori: ValoriCedolinoManuale) => T): T {
  const { negozio } = useModulo();
  return useSyncExternalStore(negozio.sottoscrivi, () => selettore(negozio.leggi()));
}

const campiCedolino: Readonly<Record<string, string>> = testi.campoCedolino;

/** "voci.3.descrizione" => "voci.descrizione": percorso senza indici, chiave delle etichette dei campi. */
export function percorsoGenerico(percorso: string): string {
  return percorso.replace(/\.\d+(?=\.|$)/g, '');
}

/** Etichetta di un campo del modulo: il nome leggibile del campo del cedolino, con l'iniziale maiuscola. */
export function etichettaCampo(percorso: string): string {
  const nome = campiCedolino[percorsoGenerico(percorso)] ?? percorso;
  return nome.charAt(0).toUpperCase() + nome.slice(1);
}

/** Posizione (da 1) della riga di una lista in un percorso del modulo: "voci.2.base" => 3. */
export function rigaDi(percorso: string): number | null {
  const match = /^(?:voci|ratei)\.(\d+)/.exec(percorso);
  return match ? Number(match[1]) + 1 : null;
}

/** Nome di un campo nell'elenco degli errori: "Descrizione della voce 3", "Rateo 2" per gli errori sull'intera riga. */
export function etichettaErrore(percorso: string): string {
  const riga = /^(voci|ratei)\.(\d+)$/.exec(percorso);
  if (riga) {
    return `${riga[1] === 'voci' ? testi.manuale.voce : testi.manuale.rateo} ${Number(riga[2]) + 1}`;
  }
  const numero = rigaDi(percorso);
  return numero !== null ? `${etichettaCampo(percorso)} ${numero}` : etichettaCampo(percorso);
}

/** Primo campo del modulo per un percorso (il campo stesso o il primo di una riga o di un gruppo). */
export function campoDelModulo(radice: HTMLElement | null, percorso: string): HTMLElement | null {
  if (!radice) {
    return null;
  }
  // i percorsi contengono solo lettere, cifre e punti: nessun carattere da proteggere nel selettore
  const esatto = radice.querySelector<HTMLElement>(`[data-path="${percorso}"]`);
  return esatto ?? radice.querySelector<HTMLElement>(`[data-path^="${percorso}."]`);
}

/** Dati da copiare nei campi vuoti, con la provenienza mostrata accanto ai campi compilati. */
export interface FonteCopia {
  dati: ValoriParziali;
  nota: string;
}

/** "Copiato da Luglio 2025: verifica". */
export function notaCopia(origine: string): string {
  return `${testi.manuale.copiatoDa} ${origine}: ${testi.manuale.daVerificare}`;
}

/**
 * Compila i campi vuoti con le fonti, nell'ordine (la prima che compila un campo vince), e indica la provenienza di
 * ogni campo compilato.
 */
export function copiaSeVuoti(
  valori: ValoriCedolinoManuale,
  fonti: readonly FonteCopia[],
): { valori: ValoriCedolinoManuale; copiati: Record<string, string> } {
  const copiati: Record<string, string> = {};
  let risultato = valori;
  for (const { dati, nota } of fonti) {
    const unione = unisciSeVuoti(risultato, dati);
    for (const percorso of unione.copiati) {
      copiati[percorso] = nota;
    }
    risultato = unione.valori;
  }
  return { valori: risultato, copiati };
}
