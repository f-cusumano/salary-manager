import { createContext, useContext } from 'react';

/** Da dove si apre l'associazione di un file in errore a una mensilita'. */
export interface OpzioniAssociazione {
  /** Dall'elenco dei file in errore: il file e' fissato, si scelgono persona, azienda e mese. */
  caricamentoId?: number;
  /** Da una mensilita' mancante: rapporto, chiave e titolo sono fissati, si sceglie il file. */
  rapportoId?: number;
  chiave?: string;
  titolo?: string;
}

/**
 * Azioni sui cedolini caricati con errore, offerte da App: le modali vivono nella radice e si aprono da qualunque
 * punto (elenco dei file in errore, righe delle mensilita', modale di caricamento) senza passare props.
 */
export interface AzioniCaricamenti {
  apriInserimento: (caricamentoId: number) => void;
  apriAssociazione: (opzioni: OpzioniAssociazione) => void;
  apriRilettura: (caricamentoId: number) => void;
  /** Rimuove il file in errore dopo la conferma dell'utente (onRimosso: per esempio chiudere la finestra aperta). */
  rimuovi: (caricamentoId: number, opzioni?: { onRimosso?: () => void }) => void;
  rimuoviAssociazione: (caricamentoId: number) => void;
  /** Apre la modale di caricamento (file senza PDF conservato: va caricato di nuovo). */
  caricaDiNuovo: () => void;
}

export const AzioniCaricamentiContext = createContext<AzioniCaricamenti | null>(null);

export function useAzioniCaricamenti(): AzioniCaricamenti {
  const azioni = useContext(AzioniCaricamentiContext);
  if (!azioni) {
    throw new Error('useAzioniCaricamenti richiede AzioniCaricamentiContext (fornito da App)');
  }
  return azioni;
}
