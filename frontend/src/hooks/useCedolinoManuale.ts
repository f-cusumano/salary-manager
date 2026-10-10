import type { CaricamentoInErrore, MotivoErrore } from '../api/types';

/**
 * Funzioni pure dei cedolini caricati con errore e del loro inserimento a mano (testate in useCedolinoManuale.test.ts,
 * come le funzioni accanto a useCaricamento).
 */

/** Azioni possibili su un file in errore (le etichette sono in testi.inErrore.azione). */
export type AzioneFileInErrore =
  | 'INSERISCI_A_MANO'
  | 'RIPROVA_CON_PASSWORD'
  | 'RIPROVA'
  | 'ASSOCIA'
  | 'CAMBIA_MESE'
  | 'RIMUOVI_ASSOCIAZIONE'
  | 'SCARICA_PDF'
  | 'RIMUOVI'
  | 'CARICA_DI_NUOVO';

/** Indicazione mostrata accanto alle azioni (testi in testi.inErrore.suggerimento). */
export type SuggerimentoFileInErrore =
  'MESE_PRESENTE' | 'PDF_NON_VALIDO' | 'DIVIDI_PDF' | 'PDF_NON_CONSERVATO';

export interface AzioniFileInErrore {
  /** Pulsante visibile della riga. */
  principale: AzioneFileInErrore;
  /** Voci del menu "Altre azioni", nell'ordine in cui mostrarle. */
  menu: AzioneFileInErrore[];
  suggerimento: SuggerimentoFileInErrore | null;
  /** Un nuovo tentativo dello stesso file e' in corso: azioni disabilitate (il backend le rifiuterebbe). */
  bloccate: boolean;
}

/** Motivi transitori: la prima cosa da fare e' rileggere il PDF conservato. */
const MOTIVI_DA_RIPROVARE: readonly MotivoErrore[] = [
  'SERVIZIO_NON_DISPONIBILE',
  'INTERROTTO',
  'ERRORE_IMPREVISTO',
];

/** Chiave di una mensilita' (come Competenza.chiave) da anno + mese 1..14: (2023, 5) => "2023.05", 13 => "2023.13". */
export function chiaveMensilita(anno: number, mese: number): string {
  return `${anno}.${String(mese).padStart(2, '0')}`;
}

/** Il file si puo' inserire a mano adesso: PDF conservato e nessun nuovo tentativo in corso. */
export function completabileAMano(
  elemento: Pick<CaricamentoInErrore, 'pdfDisponibile' | 'inElaborazione'>,
): boolean {
  return elemento.pdfDisponibile && !elemento.inElaborazione;
}

function suggerimentoSenzaPdf(motivo: MotivoErrore | null): SuggerimentoFileInErrore {
  switch (motivo) {
    case 'PDF_NON_VALIDO':
      return 'PDF_NON_VALIDO';
    case 'PDF_TROPPE_PAGINE':
    case 'PIU_CEDOLINI':
      return 'DIVIDI_PDF';
    default:
      // anche gli errori registrati prima dei motivi (motivo null): il PDF non e' stato conservato
      return 'PDF_NON_CONSERVATO';
  }
}

/**
 * Azione principale, menu e indicazione di un file in errore, in base a motivo, PDF conservato, associazione e
 * tentativo in corso: un mese diventato presente suggerisce di rimuovere il file; con il PDF si propone la rilettura
 * (password per i protetti, semplice per i guasti transitori) o l'inserimento a mano; senza PDF si puo' solo caricare
 * di nuovo il file.
 */
export function azioniPerCaricamento(
  elemento: Pick<CaricamentoInErrore, 'motivo' | 'pdfDisponibile' | 'inElaborazione' | 'attribuzione'>,
): AzioniFileInErrore {
  const bloccate = elemento.inElaborazione;
  if (elemento.attribuzione?.competenzaPresente) {
    return { principale: 'RIMUOVI', menu: ['RIMUOVI_ASSOCIAZIONE'], suggerimento: 'MESE_PRESENTE', bloccate };
  }
  const associazione: AzioneFileInErrore[] = elemento.attribuzione
    ? ['CAMBIA_MESE', 'RIMUOVI_ASSOCIAZIONE']
    : ['ASSOCIA'];
  if (!elemento.pdfDisponibile) {
    return {
      principale: 'CARICA_DI_NUOVO',
      menu: [...associazione, 'RIMUOVI'],
      suggerimento: suggerimentoSenzaPdf(elemento.motivo),
      bloccate,
    };
  }
  if (elemento.motivo === 'PDF_PROTETTO') {
    return {
      principale: 'RIPROVA_CON_PASSWORD',
      menu: ['INSERISCI_A_MANO', ...associazione, 'SCARICA_PDF', 'RIMUOVI'],
      suggerimento: null,
      bloccate,
    };
  }
  if (elemento.motivo !== null && MOTIVI_DA_RIPROVARE.includes(elemento.motivo)) {
    return {
      principale: 'RIPROVA',
      menu: ['INSERISCI_A_MANO', ...associazione, 'SCARICA_PDF', 'RIMUOVI'],
      suggerimento: null,
      bloccate,
    };
  }
  return {
    principale: 'INSERISCI_A_MANO',
    menu: ['RIPROVA', ...associazione, 'SCARICA_PDF', 'RIMUOVI'],
    suggerimento: null,
    bloccate,
  };
}
