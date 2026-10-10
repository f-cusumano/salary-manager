package com.fcusumano.sam.calcoli.application;

import com.fcusumano.sam.anagrafica.CcnlRapportoModificato;
import com.fcusumano.sam.calcoli.CalcoliRapporto;
import com.fcusumano.sam.cedolini.CedolinoAcquisito;
import com.fcusumano.sam.cedolini.CedolinoEliminato;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Ricalcolo sincrono del riepilogo, nella stessa transazione che ha pubblicato l'evento.
 */
@Component
class RicalcoloRiepilogoListener {

    private final CalcoliRapporto calcoli;

    RicalcoloRiepilogoListener(CalcoliRapporto calcoli) {
        this.calcoli = calcoli;
    }

    @EventListener
    void on(CedolinoAcquisito evento) {
        calcoli.ricalcola(evento.rapportoId());
    }

    @EventListener
    void on(CedolinoEliminato evento) {
        calcoli.ricalcola(evento.rapportoId());
    }

    @EventListener
    void on(CcnlRapportoModificato evento) {
        calcoli.ricalcola(evento.rapportoId());
    }
}
