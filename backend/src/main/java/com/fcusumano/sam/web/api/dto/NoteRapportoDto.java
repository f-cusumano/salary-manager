package com.fcusumano.sam.web.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Note libere del rapporto (null o vuote = nessuna nota).
 */
@Schema(name = "NoteRapporto")
public record NoteRapportoDto(
        @Size(max = NoteRapportoDto.LUNGHEZZA_MASSIMA,
                message = "Le note non possono superare " + NoteRapportoDto.LUNGHEZZA_MASSIMA + " caratteri")
        String note) {

    public static final int LUNGHEZZA_MASSIMA = 4000;
}
