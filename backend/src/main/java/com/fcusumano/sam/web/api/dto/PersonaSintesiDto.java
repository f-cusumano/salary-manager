package com.fcusumano.sam.web.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * Voce della tendina persone (barra superiore e scelta della persona nell'inserimento a mano).
 *
 * @param dataNascita data di nascita, se nota (precompila l'inserimento a mano)
 * @param etichetta   "Nome Cognome"; in caso di omonimia "Nome Cognome (CODICEFISCALE)"
 */
@Schema(name = "PersonaSintesi")
public record PersonaSintesiDto(long id, String nome, String cognome, String codiceFiscale, LocalDate dataNascita,
                                String etichetta) {
}
