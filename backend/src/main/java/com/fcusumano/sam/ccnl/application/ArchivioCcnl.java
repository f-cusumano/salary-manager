package com.fcusumano.sam.ccnl.application;

import com.fcusumano.sam.ccnl.Ccnl;
import com.fcusumano.sam.ccnl.domain.RegolaRiconoscimento;
import java.util.List;
import java.util.Optional;

/**
 * Porta di persistenza della tabella CCNL (dato di riferimento globale).
 */
public interface ArchivioCcnl {

    /** CCNL attivi ordinati per descrizione. */
    List<Ccnl> attivi();

    /** CCNL per codice esatto, anche se non piu' attivo (puo' essere ancora associato a un rapporto). */
    Optional<Ccnl> perCodice(String codice);

    /** Regole di riconoscimento dei CCNL attivi. */
    List<RegolaRiconoscimento> regoleRiconoscimento();
}
