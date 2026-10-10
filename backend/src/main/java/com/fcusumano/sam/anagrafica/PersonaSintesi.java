package com.fcusumano.sam.anagrafica;

/**
 * Voce della tendina persone.
 *
 * @param etichetta "Nome Cognome", con " (CODICEFISCALE)" aggiunto solo in caso di omonimia nel workspace
 */
public record PersonaSintesi(PersonaId id, String nome, String cognome, String codiceFiscale, String etichetta) {
}
