package com.fcusumano.sam.web.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * CCNL scelto dall'utente per il rapporto: il codice deve esistere nella tabella CCNL.
 */
@Schema(name = "SceltaCcnl")
public record SceltaCcnlDto(
        @NotBlank(message = "Indica il codice del CCNL")
        @Size(max = SceltaCcnlDto.LUNGHEZZA_MASSIMA, message = "Codice CCNL troppo lungo")
        String ccnlCodice) {

    /** Lunghezza massima di un codice CCNL accettata in input. */
    public static final int LUNGHEZZA_MASSIMA = 50;
}
