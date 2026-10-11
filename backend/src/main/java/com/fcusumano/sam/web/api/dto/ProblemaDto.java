package com.fcusumano.sam.web.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Forma delle risposte di errore (Problem Details, RFC 9457), usata solo per la documentazione OpenAPI: a runtime
 * gli errori sono {@link org.springframework.http.ProblemDetail} prodotti da {@code web.error.GestoreErrori}.
 *
 * @param errori errori sui singoli campi, solo nelle risposte 400 dell'inserimento a mano, della sua verifica e
 *               dell'associazione a una mensilita'; assente negli altri errori
 */
@Schema(name = "Problema", description = "Problem Details (RFC 9457)")
public record ProblemaDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "uri") String type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int status,
        String detail,
        String instance,
        List<ErroreCampoDto> errori) {
}
