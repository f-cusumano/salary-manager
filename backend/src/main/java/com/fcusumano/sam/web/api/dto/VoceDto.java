package com.fcusumano.sam.web.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * Voce del cedolino.
 *
 * @param unita unita' della quantita' come stampata (ore, giorni, km, %...)
 * @param base  base o tariffa unitaria
 */
@Schema(name = "Voce")
public record VoceDto(
        String codice,
        String descrizione,
        BigDecimal quantita,
        String unita,
        BigDecimal base,
        BigDecimal importoCompetenza,
        BigDecimal importoTrattenuta) {
}
