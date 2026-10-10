package com.fcusumano.sam.segnalazioni;

import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.shared.Competenza;

/**
 * Evidenza a supporto di una segnalazione: un cedolino e/o un valore coinvolto.
 *
 * @param descrizione testo leggibile (es. "Fondo TFR al 31/12/2023")
 * @param cedolinoId  cedolino coinvolto, se presente
 * @param competenza  competenza coinvolta, se presente
 * @param valore      valore coinvolto gia' formattato per la lettura (es. "12.345,67 €"), se presente
 */
public record Evidenza(String descrizione, CedolinoId cedolinoId, Competenza competenza, String valore) {
}
