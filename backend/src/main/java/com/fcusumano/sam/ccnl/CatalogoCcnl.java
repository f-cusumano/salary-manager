package com.fcusumano.sam.ccnl;

import java.util.List;
import java.util.Optional;

/**
 * Consultazione della tabella CCNL.
 */
public interface CatalogoCcnl {

    /** Tutti i CCNL attivi, ordinati per descrizione. */
    List<Ccnl> elenco();

    /** CCNL per codice (esatto). */
    Optional<Ccnl> trova(String codice);

    /**
     * Riconosce il CCNL dal testo stampato sul cedolino (es. "CCNL Commercio Terziario"), confrontando il testo
     * normalizzato con le parole chiave della tabella. Restituisce un valore solo se il riconoscimento e' univoco:
     * in caso di dubbio (nessuna o piu' corrispondenze) restituisce {@link Optional#empty()} e il rapporto resta
     * "CCNL da indicare".
     */
    Optional<Ccnl> riconosci(String testoStampato);
}
