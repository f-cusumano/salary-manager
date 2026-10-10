package com.fcusumano.sam.web.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Voce della tendina persone.
 *
 * @param etichetta "Nome Cognome"; in caso di omonimia "Nome Cognome (CODICEFISCALE)"
 */
@Schema(name = "PersonaSintesi")
public record PersonaSintesiDto(long id, String nome, String cognome, String codiceFiscale, String etichetta) {
}
