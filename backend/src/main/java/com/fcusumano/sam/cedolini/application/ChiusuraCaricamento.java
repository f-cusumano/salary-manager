package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.cedolini.CaricamentiInErroreModificati;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.MotivoErrore;
import com.fcusumano.sam.cedolini.StatoCaricamento;
import com.fcusumano.sam.cedolini.application.ElaborazioneCaricamenti.Lavoro;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Registra gli esiti conclusivi diversi da ACQUISITO (che si registra nella transazione di acquisizione) insieme alla
 * sorte del PDF originale e dell'associazione del file, in una transazione serializzata per workspace:
 * <ul>
 *   <li>ERRORE: motivo e messaggio. Se il file e' ancora in errore (ricontrollato dopo la registrazione: un tentativo
 *       successivo dello stesso file puo' essersi concluso prima) il PDF si conserva (uno per file) se il motivo lo
 *       prevede, altrimenti si elimina, e l'associazione resta: le segnalazioni del rapporto associato, che riportano
 *       motivo e azioni possibili, vengono ricalcolate. Se il file non e' piu' in errore PDF e associazione si
 *       eliminano;</li>
 *   <li>DUPLICATO / AGGIUNTIVO_NON_GESTITO: il file non e' piu' in errore, quindi PDF e associazione si eliminano.</li>
 * </ul>
 * Se la transazione fallisce lo stato si registra comunque con un aggiornamento semplice: un caricamento non resta mai
 * IN_LETTURA. Nei log solo id e tipo di errore.
 */
@Component
class ChiusuraCaricamento {

    private static final Logger log = LoggerFactory.getLogger(ChiusuraCaricamento.class);

    private final ScrittureSerializzate scritture;
    private final CaricamentiRepository caricamenti;
    private final ApplicationEventPublisher eventi;

    ChiusuraCaricamento(ScrittureSerializzate scritture, CaricamentiRepository caricamenti,
                        ApplicationEventPublisher eventi) {
        this.scritture = scritture;
        this.caricamenti = caricamenti;
        this.eventi = eventi;
    }

    /** Porta il caricamento in ERRORE con motivo e messaggio (mai dati personali). */
    void errore(Lavoro lavoro, MotivoErrore motivo, String messaggio) {
        var ws = lavoro.workspace();
        try {
            scritture.inTransazione(ws, stato -> {
                caricamenti.registraErrore(ws, lavoro.caricamentoId(), motivo, messaggio);
                var hash = lavoro.hash();
                if (caricamenti.fileInErrore(ws, hash)) {
                    if (motivo.conservaPdf()) {
                        caricamenti.salvaPdfSeAssente(ws, hash, lavoro.contenuto());
                    } else {
                        caricamenti.eliminaPdf(ws, hash);
                    }
                    caricamenti.attribuzione(ws, hash).ifPresent(attribuzione -> eventi.publishEvent(
                            new CaricamentiInErroreModificati(Set.of(attribuzione.rapportoId()))));
                } else {
                    // file archiviato o risolto da un tentativo successivo gia' concluso: nulla da conservare
                    caricamenti.eliminaPdf(ws, hash);
                    caricamenti.eliminaAttribuzione(ws, hash).ifPresent(
                            rapporto -> eventi.publishEvent(new CaricamentiInErroreModificati(Set.of(rapporto))));
                }
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("Chiusura in errore del caricamento {} non riuscita: {}", lavoro.caricamentoId().valore(),
                    e.getClass().getName());
            ripiego(lavoro, () -> caricamenti.registraErrore(ws, lavoro.caricamentoId(), motivo, messaggio));
        }
    }

    /**
     * Registra un caricamento scartato (DUPLICATO o AGGIUNTIVO_NON_GESTITO).
     *
     * @param esistente cedolino gia' presente con cui confligge, null se non individuato
     */
    void scartato(Lavoro lavoro, StatoCaricamento stato, String messaggio, CedolinoId esistente) {
        var ws = lavoro.workspace();
        try {
            scritture.inTransazione(ws, transazione -> {
                caricamenti.aggiorna(ws, lavoro.caricamentoId(), stato, messaggio, null, esistente);
                caricamenti.eliminaPdf(ws, lavoro.hash());
                caricamenti.eliminaAttribuzione(ws, lavoro.hash()).ifPresent(
                        rapporto -> eventi.publishEvent(new CaricamentiInErroreModificati(Set.of(rapporto))));
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("Chiusura del caricamento scartato {} non riuscita: {}", lavoro.caricamentoId().valore(),
                    e.getClass().getName());
            ripiego(lavoro, () -> caricamenti.aggiorna(ws, lavoro.caricamentoId(), stato, messaggio, null, esistente));
        }
    }

    /**
     * Ripiego senza transazione: solo lo stato del caricamento. Eventuali PDF o associazioni rimasti di un file non piu'
     * in errore sono eliminati al riavvio ({@link RipristinoCaricamentiInterrotti}).
     */
    private static void ripiego(Lavoro lavoro, Runnable aggiornamento) {
        try {
            aggiornamento.run();
        } catch (RuntimeException e) {
            log.error("Stato del caricamento {} non aggiornabile: {}", lavoro.caricamentoId().valore(),
                    e.getClass().getName());
        }
    }
}
