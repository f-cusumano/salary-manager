package com.fcusumano.sam.segnalazioni.application;

import com.fcusumano.sam.anagrafica.CcnlRapportoModificato;
import com.fcusumano.sam.cedolini.CedolinoAcquisito;
import com.fcusumano.sam.cedolini.CedolinoEliminato;
import com.fcusumano.sam.segnalazioni.SegnalazioniRapporto;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Ricalcolo sincrono delle segnalazioni, nella stessa transazione che ha pubblicato l'evento.
 */
@Component
class RicalcoloSegnalazioniListener {

    private final SegnalazioniRapporto segnalazioni;

    RicalcoloSegnalazioniListener(SegnalazioniRapporto segnalazioni) {
        this.segnalazioni = segnalazioni;
    }

    @EventListener
    void on(CedolinoAcquisito evento) {
        segnalazioni.ricalcola(evento.rapportoId());
    }

    @EventListener
    void on(CedolinoEliminato evento) {
        segnalazioni.ricalcola(evento.rapportoId());
    }

    @EventListener
    void on(CcnlRapportoModificato evento) {
        segnalazioni.ricalcola(evento.rapportoId());
    }
}
