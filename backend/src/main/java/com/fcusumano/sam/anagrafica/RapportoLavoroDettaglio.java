package com.fcusumano.sam.anagrafica;

/**
 * Dettaglio anagrafico completo di un rapporto.
 */
public record RapportoLavoroDettaglio(
        RapportoLavoro rapporto,
        Persona persona,
        Azienda azienda,
        String etichettaAzienda,
        PeriodoRapporto periodo) {
}
