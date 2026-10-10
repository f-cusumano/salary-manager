package com.fcusumano.sam.anagrafica;

import java.time.LocalDate;

/**
 * Dati anagrafici della persona letti dal cedolino (input di {@link CensimentoAnagrafica#trovaOCreaPersona}).
 *
 * @param codiceFiscale obbligatorio, 16 caratteri (viene normalizzato in maiuscolo senza spazi)
 * @param dataNascita   opzionale
 */
public record DatiPersona(String codiceFiscale, String nome, String cognome, LocalDate dataNascita) {
}
