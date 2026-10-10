package com.fcusumano.sam.cedolini;

import java.math.BigDecimal;

/**
 * Voce del corpo del cedolino. Gli importi sono positivi; la colonna (competenza o trattenuta) indica il segno.
 *
 * @param codice   codice voce del software paghe, se stampato
 * @param unita    unita' della quantita' come stampata e normalizzata in minuscolo (es. "ore", "giorni", "km", "%"); null se assente
 * @param base     base/tariffa unitaria
 */
public record Voce(
        String codice,
        String descrizione,
        CategoriaVoce categoria,
        BigDecimal quantita,
        String unita,
        BigDecimal base,
        BigDecimal importoCompetenza,
        BigDecimal importoTrattenuta) {
}
