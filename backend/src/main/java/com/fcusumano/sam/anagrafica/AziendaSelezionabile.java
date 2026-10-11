package com.fcusumano.sam.anagrafica;

/**
 * Voce dell'elenco aziende per la scelta del datore di lavoro (inserimento a mano).
 *
 * @param etichetta ragione sociale; solo in caso di omonimia tra tutte le aziende del workspace si aggiunge
 *                  " (P.IVA ...)", in mancanza " (CF ...)", poi " (Pos. INPS ...)"
 */
public record AziendaSelezionabile(Azienda azienda, String etichetta) {
}
