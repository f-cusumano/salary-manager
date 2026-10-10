package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.cedolini.MotivoErrore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * All'avvio, prima che il server accetti richieste:
 * <ol>
 *   <li>porta in ERRORE (motivo INTERROTTO) i caricamenti rimasti IN_CODA o IN_LETTURA: la loro elaborazione viveva in
 *       memoria ed e' stata interrotta dal riavvio. Il PDF del file resta conservato solo se un tentativo precedente
 *       lo aveva gia' conservato (es. una nuova lettura interrotta); altrimenti l'utente deve ricaricare il file;</li>
 *   <li>elimina PDF conservati e associazioni dei file non piu' in errore (rete di sicurezza: di norma spariscono
 *       nella transazione che risolve il file).</li>
 * </ol>
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
        int interrotti = caricamenti.interrompiNonTerminati(MotivoErrore.INTERROTTO,
                MessaggiCaricamento.INTERROTTO_DAL_RIAVVIO);
        if (interrotti > 0) {
            log.warn("{} caricamenti interrotti dal riavvio marcati in errore", interrotti);
        }
        int ripuliti = caricamenti.ripulisciFileNonInErrore();
        if (ripuliti > 0) {
            log.info("Eliminati {} PDF conservati o associazioni di file non piu' in errore", ripuliti);
        }
        return interrotti;
    }
}
