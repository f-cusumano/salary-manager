package com.fcusumano.sam.cedolini.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * All'avvio, prima che il server accetti richieste, porta in ERRORE i caricamenti rimasti IN_CODA o IN_LETTURA: la loro
 * elaborazione viveva in memoria ed e' stata interrotta dal riavvio (l'utente deve ricaricare i file).
 */
@Component
class RipristinoCaricamentiInterrotti implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(RipristinoCaricamentiInterrotti.class);

    private final CaricamentiRepository caricamenti;

    RipristinoCaricamentiInterrotti(CaricamentiRepository caricamenti) {
        this.caricamenti = caricamenti;
    }

    @Override
    public void afterSingletonsInstantiated() {
        ripristina();
    }

    /** @return numero di caricamenti marcati come interrotti */
    int ripristina() {
        int interrotti = caricamenti.interrompiNonTerminati(MessaggiCaricamento.INTERROTTO_DAL_RIAVVIO);
        if (interrotti > 0) {
            log.warn("{} caricamenti interrotti dal riavvio marcati in errore", interrotti);
        }
        return interrotti;
    }
}
