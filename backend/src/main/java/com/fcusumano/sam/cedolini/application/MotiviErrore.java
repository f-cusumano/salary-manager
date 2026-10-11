package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.cedolini.MotivoErrore;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException;
import com.fcusumano.sam.cedolini.application.estrazione.PdfNonLeggibileException;

/**
 * Motivo pubblico ({@link MotivoErrore}) di un errore di apertura o lettura del PDF: la causa specifica dei PDF non
 * leggibili prevale sul motivo generico dell'estrazione.
 */
final class MotiviErrore {

    private MotiviErrore() {
    }

    static MotivoErrore di(EstrazioneCedolinoException e) {
        if (e instanceof PdfNonLeggibileException pdf) {
            return switch (pdf.causa()) {
                case PROTETTO_DA_PASSWORD -> MotivoErrore.PDF_PROTETTO;
                case TROPPO_GRANDE -> MotivoErrore.PDF_TROPPO_GRANDE;
                case TROPPE_PAGINE -> MotivoErrore.PDF_TROPPE_PAGINE;
                case NON_PDF, DANNEGGIATO -> MotivoErrore.PDF_NON_VALIDO;
            };
        }
        return switch (e.motivo()) {
            case PDF_SENZA_TESTO -> MotivoErrore.PDF_SENZA_TESTO;
            case LAYOUT_NON_RICONOSCIUTO -> MotivoErrore.LAYOUT_NON_RICONOSCIUTO;
            case PIU_CEDOLINI -> MotivoErrore.PIU_CEDOLINI;
            case SERVIZIO_NON_DISPONIBILE, CONFIGURAZIONE -> MotivoErrore.SERVIZIO_NON_DISPONIBILE;
            case DOCUMENTO_NON_VALIDO, OUTPUT_NON_VALIDO, RIFIUTO, LIMITE_TOKEN -> MotivoErrore.LETTURA_NON_RIUSCITA;
        };
    }
}
