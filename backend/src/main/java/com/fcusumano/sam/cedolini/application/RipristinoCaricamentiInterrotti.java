package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.cedolini.CaricamentiInErroreModificati;
import com.fcusumano.sam.cedolini.MotivoErrore;
import com.fcusumano.sam.cedolini.application.CaricamentiRepository.RapportoAssociato;
import com.fcusumano.sam.shared.WorkspaceId;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * All'avvio, prima che il server accetti richieste:
 * <ol>
 *   <li>i caricamenti rimasti IN_CODA o IN_LETTURA il cui file e' gia' archiviato come cedolino diventano DUPLICATO,
 *       come li avrebbe scartati la loro elaborazione (se il cedolino venisse eliminato il file non tornerebbe in
 *       errore);</li>
 *   <li>gli altri rimasti IN_CODA o IN_LETTURA vanno in ERRORE (motivo INTERROTTO): la loro elaborazione viveva in
 *       memoria ed e' stata interrotta dal riavvio. Il PDF del file resta conservato solo se un tentativo precedente
 *       lo aveva gia' conservato (es. una nuova lettura interrotta), e il messaggio propone di rileggerlo o di
 *       inserirlo a mano; altrimenti l'utente deve ricaricare il file;</li>
 *   <li>elimina PDF conservati e associazioni dei file non piu' in errore (rete di sicurezza: di norma spariscono
 *       nella transazione che risolve il file);</li>
 *   <li>ricalcola le segnalazioni dei rapporti a cui erano associati i file coinvolti (riportano il motivo
 *       dell'errore e le azioni possibili), con l'evento {@link CaricamentiInErroreModificati} pubblicato nel
 *       workspace di ciascun rapporto.</li>
 * </ol>
 */
@Component
class RipristinoCaricamentiInterrotti implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(RipristinoCaricamentiInterrotti.class);

    private final CaricamentiRepository caricamenti;
    private final ScrittureSerializzate scritture;
    private final ApplicationEventPublisher eventi;

    RipristinoCaricamentiInterrotti(CaricamentiRepository caricamenti, ScrittureSerializzate scritture,
                                    ApplicationEventPublisher eventi) {
        this.caricamenti = caricamenti;
        this.scritture = scritture;
        this.eventi = eventi;
    }

    @Override
    public void afterSingletonsInstantiated() {
        ripristina();
    }

    /** @return numero di caricamenti marcati come interrotti */
    int ripristina() {
        var daRicalcolare = new LinkedHashSet<>(caricamenti.rapportiAssociatiANonTerminati());
        int scartati = caricamenti.scartaNonTerminatiGiaArchiviati(MessaggiCaricamento.FILE_GIA_CARICATO);
        if (scartati > 0) {
            log.info("{} caricamenti interrotti dal riavvio di file gia' archiviati marcati come duplicati", scartati);
        }
        int interrotti = caricamenti.interrompiNonTerminati(MotivoErrore.INTERROTTO,
                MessaggiCaricamento.INTERROTTO_DAL_RIAVVIO_RILEGGIBILE, MessaggiCaricamento.INTERROTTO_DAL_RIAVVIO);
        if (interrotti > 0) {
            log.warn("{} caricamenti interrotti dal riavvio marcati in errore", interrotti);
        }
        daRicalcolare.addAll(caricamenti.rapportiAssociatiAFileNonInErrore());
        int ripuliti = caricamenti.ripulisciFileNonInErrore();
        if (ripuliti > 0) {
            log.info("Eliminati {} PDF conservati o associazioni di file non piu' in errore", ripuliti);
        }
        ricalcolaSegnalazioni(daRicalcolare);
        return interrotti;
    }

    /** Un evento per workspace, nella sua transazione serializzata; un errore non blocca l'avvio. */
    private void ricalcolaSegnalazioni(Set<RapportoAssociato> rapporti) {
        Map<WorkspaceId, Set<RapportoLavoroId>> perWorkspace = rapporti.stream().collect(Collectors.groupingBy(
                RapportoAssociato::workspace, () -> new TreeMap<>((a, b) -> Long.compare(a.valore(), b.valore())),
                Collectors.mapping(RapportoAssociato::rapportoId, Collectors.toCollection(LinkedHashSet::new))));
        perWorkspace.forEach((workspace, associati) -> {
            try {
                scritture.inTransazione(workspace, stato -> {
                    eventi.publishEvent(new CaricamentiInErroreModificati(associati));
                    return null;
                });
            } catch (RuntimeException e) {
                log.warn("Segnalazioni dei file in errore del workspace {} non ricalcolate all'avvio: {}",
                        workspace.valore(), e.getClass().getName());
            }
        });
    }
}
