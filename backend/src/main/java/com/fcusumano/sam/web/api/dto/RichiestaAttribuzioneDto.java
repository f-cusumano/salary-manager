package com.fcusumano.sam.web.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Mensilita' a cui associare un file in errore. Valori mancanti o fuori intervallo sono rifiutati con 400 ed
 * {@code errori} sui campi {@code rapportoId}, {@code anno} e {@code mese}.
 *
 * @param anno dal 1900 all'anno prossimo
 * @param mese 1..12, 13 tredicesima, 14 quattordicesima
 */
@Schema(name = "RichiestaAttribuzione")
public record RichiestaAttribuzioneDto(
        Long rapportoId,
        @Schema(minimum = "1900", maximum = "2999") Integer anno,
        @Schema(description = "1..12, 13 tredicesima, 14 quattordicesima", minimum = "1", maximum = "14")
        Integer mese) {
}
