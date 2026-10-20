package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.anagrafica.PersonaSintesi;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.application.CedoliniRepository.PdfDaSbloccare;
import com.fcusumano.sam.cedolini.application.estrazione.LettorePdf;
import com.fcusumano.sam.cedolini.application.estrazione.LettorePdf.Sblocco;
import com.fcusumano.sam.shared.ContestoWorkspace;
import com.fcusumano.sam.shared.WorkspaceId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Sblocco in sottofondo dei PDF archiviati ancora protetti o mai verificati (cedolini archiviati prima che i PDF si
 * archiviassero sbloccati, cedolini inseriti a mano dal PDF conservato): ciascuno si apre con le password note (quelle
 * configurate, quelle ricordate e i codici fiscali delle persone del workspace) e, se si apre, la sua copia senza
 * protezione prende il posto dell'originale. Quelli che nessuna password apre restano protetti e si riprovano al
 * prossimo avvio o quando si ricorda una nuova password.
 * <p>
 * Parte all'avvio dell'applicazione (tutti i workspace), quando si ricorda una nuova password e dopo un inserimento a
 * mano (un workspace). Un solo sblocco alla volta, su un thread virtuale: le richieste non lo aspettano. L'hash del
 * cedolino resta quello del PDF caricato, quindi ricaricare lo stesso file protetto continua a dare "gia' caricato".
 * Nei log solo id e conteggi.
 */
@Component
class SbloccoPdfArchiviati implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SbloccoPdfArchiviati.class);
    private static final long ATTESA_CHIUSURA_SECONDI = 10;

    private final CedoliniRepository cedolini;
    private final LettorePdf lettorePdf;
    private final PasswordPdfRicordate ricordate;
    private final ConsultazioneAnagrafica consultazione;
    private final ExecutorService esecutore =
            Executors.newSingleThreadExecutor(Thread.ofVirtual().name("sblocco-pdf-", 1).factory());

    SbloccoPdfArchiviati(CedoliniRepository cedolini, LettorePdf lettorePdf, PasswordPdfRicordate ricordate,
                         ConsultazioneAnagrafica consultazione) {
        this.cedolini = cedolini;
        this.lettorePdf = lettorePdf;
        this.ricordate = ricordate;
        this.consultazione = consultazione;
    }

    /** All'avvio: sblocco dei PDF di tutti i workspace. */
    @EventListener(ApplicationReadyEvent.class)
    void allAvvio() {
        avvia(() -> {
            var perWorkspace = new LinkedHashMap<WorkspaceId, List<CedolinoId>>();
            for (PdfDaSbloccare pdf : cedolini.pdfDaSbloccare()) {
                perWorkspace.computeIfAbsent(pdf.workspace(), w -> new ArrayList<>()).add(pdf.cedolinoId());
            }
            perWorkspace.forEach(this::sblocca);
        });
    }

    /** Sblocco dei PDF di un workspace (nuova password ricordata, inserimento a mano). */
    Future<?> avvia(WorkspaceId workspace) {
        return avvia(() -> sblocca(workspace, cedolini.pdfDaSbloccare(workspace)));
    }

    private Future<?> avvia(Runnable sblocco) {
        try {
            return esecutore.submit(() -> {
                try {
                    sblocco.run();
                } catch (RuntimeException e) {
                    log.warn("Sblocco dei PDF archiviati interrotto: {}", e.getClass().getName());
                }
            });
        } catch (RejectedExecutionException e) {
            // applicazione in chiusura: si riprova al prossimo avvio
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Prova a sbloccare i PDF indicati del workspace.
     *
     * @return numero di PDF sbloccati
     */
    int sblocca(WorkspaceId workspace, List<CedolinoId> daSbloccare) {
        if (daSbloccare.isEmpty()) {
            return 0;
        }
        return ContestoWorkspace.calcola(workspace, () -> {
            var password = new Memo<>(() -> {
                var note = new ArrayList<>(ricordate.elenco(workspace));
                consultazione.elencoPersone().stream().map(PersonaSintesi::codiceFiscale).forEach(note::add);
                return List.copyOf(note);
            });
            int sbloccati = 0;
            int protetti = 0;
            for (var id : daSbloccare) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                var pdf = cedolini.pdf(workspace, id);
                if (pdf.isEmpty()) {
                    continue;
                }
                switch (lettorePdf.sblocca(pdf.get().contenuto(), password)) {
                    case Sblocco.NonProtetto n -> cedolini.aggiornaPdf(workspace, id, null, false);
                    case Sblocco.Sbloccato s -> {
                        cedolini.aggiornaPdf(workspace, id, s.contenuto(), false);
                        sbloccati++;
                    }
                    case Sblocco.Protetto p -> {
                        cedolini.aggiornaPdf(workspace, id, null, true);
                        protetti++;
                    }
                    // non si apre per altri motivi (danneggiato): resta da verificare, si riprova al prossimo avvio
                    case Sblocco.NonLeggibile n -> {
                    }
                }
            }
            if (sbloccati > 0 || protetti > 0) {
                log.info("PDF archiviati del workspace {}: {} sbloccati, {} ancora protetti", workspace.valore(),
                        sbloccati, protetti);
            }
            return sbloccati;
        });
    }

    /** Valore calcolato al primo uso (le password si decifrano solo se un PDF le richiede). */
    private static final class Memo<T> implements Supplier<T> {

        private final Supplier<T> calcolo;
        private T valore;

        Memo(Supplier<T> calcolo) {
            this.calcolo = calcolo;
        }

        @Override
        public T get() {
            if (valore == null) {
                valore = calcolo.get();
            }
            return valore;
        }
    }

    @Override
    public void destroy() throws InterruptedException {
        esecutore.shutdownNow();
        if (!esecutore.awaitTermination(ATTESA_CHIUSURA_SECONDI, TimeUnit.SECONDS)) {
            log.warn("Sblocco dei PDF archiviati non terminato alla chiusura: riprende al prossimo avvio");
        }
    }
}
