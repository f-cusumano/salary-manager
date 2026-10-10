package com.fcusumano.sam.web.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Evidenza a supporto di una segnalazione.
 *
 * @param valore valore gia' formattato per la lettura (es. "12.345,67 €")
 */
@Schema(name = "Evidenza")
public record EvidenzaDto(String descrizione, Long cedolinoId, CompetenzaDto competenza, String valore) {
}
