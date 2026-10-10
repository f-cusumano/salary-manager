package com.fcusumano.sam.anagrafica;

import java.time.LocalDate;

/**
 * Persona (dipendente), univoca per codice fiscale all'interno del workspace.
 *
 * @param dataNascita null se non nota
 */
public record Persona(PersonaId id, String codiceFiscale, String nome, String cognome, LocalDate dataNascita) {

    /** "Nome Cognome". */
    public String nomeCompleto() {
        return nome + " " + cognome;
    }
}
