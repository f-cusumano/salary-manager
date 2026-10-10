package com.fcusumano.sam.web.api;

import com.fcusumano.sam.web.api.dto.ProblemaDto;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Risposte di errore (Problem Details) documentate sulle operazioni REST, come in {@code docs/openapi.yaml}.
 * Sono meta-annotazioni lette da springdoc: evitano di ripetere la stessa {@link ApiResponse} su ogni metodo.
 */
final class Risposte {

    static final String PROBLEMA = "application/problem+json";

    private Risposte() {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @ApiResponse(responseCode = "400", description = "Richiesta non valida",
            content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ProblemaDto.class)))
    @interface RichiestaNonValida {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @ApiResponse(responseCode = "404", description = "Risorsa inesistente",
            content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ProblemaDto.class)))
    @interface NonTrovato {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @ApiResponse(responseCode = "409", description = "Operazione non consentita o modifica concorrente",
            content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ProblemaDto.class)))
    @interface Conflitto {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @ApiResponse(responseCode = "413", description = "File o caricamento troppo grande",
            content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ProblemaDto.class)))
    @interface TroppoGrande {
    }
}
