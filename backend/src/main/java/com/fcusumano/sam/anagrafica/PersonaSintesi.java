package com.fcusumano.sam.anagrafica;

import java.time.LocalDate;

/**
 * Voce della tendina persone (barra superiore e scelta della persona nell'inserimento a mano).
 *
 * @param dataNascita null se non nota
 * @param etichetta   "Nome Cognome", con " (CODICEFISCALE)" aggiunto solo in caso di omonimia nel workspace
 */
public record PersonaSintesi(PersonaId id, String nome, String cognome, String codiceFiscale, LocalDate dataNascita,
                             String etichetta) {
}
