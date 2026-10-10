package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.StatoCaricamento;

/**
 * Esito della fase di persistenza di un caricamento.
 */
sealed interface EsitoAcquisizione {

    /** Cedolino salvato. */
    record Acquisito(CedolinoId cedolinoId) implements EsitoAcquisizione {
    }

    /**
     * Cedolino scartato (DUPLICATO o AGGIUNTIVO_NON_GESTITO): la transazione e' annullata, quindi nessuna persona,
     * azienda o rapporto viene creato per un cedolino che non si archivia.
     *
     * @param esistente cedolino gia' presente con cui il nuovo confligge, null se non individuato
     */
    record Scartato(StatoCaricamento stato, String messaggio, CedolinoId esistente) implements EsitoAcquisizione {
    }
}
