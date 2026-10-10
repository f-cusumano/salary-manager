package com.fcusumano.sam.anagrafica;

/**
 * Evento: il CCNL di un rapporto e' cambiato (scelta dell'utente). I moduli calcoli e segnalazioni ricalcolano da zero
 * i dati derivati del rapporto, in modo sincrono nella stessa transazione.
 *
 * @param ccnlPrecedente null se prima era "da indicare"
 */
public record CcnlRapportoModificato(RapportoLavoroId rapportoId, String ccnlPrecedente, String ccnlNuovo) {
}
