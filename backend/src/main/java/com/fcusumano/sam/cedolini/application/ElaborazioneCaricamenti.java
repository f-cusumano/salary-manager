package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.anagrafica.PersonaSintesi;
import com.fcusumano.sam.cedolini.CaricamentoId;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.StatoCaricamento;
import com.fcusumano.sam.cedolini.application.AcquisizioneCedolino.DocumentoCaricato;
import com.fcusumano.sam.cedolini.application.EsitoAcquisizione.Acquisito;
import com.fcusumano.sam.cedolini.application.EsitoAcquisizione.Scartato;
import com.fcusumano.sam.cedolini.application.estrazione.LettoreCedolini;
import com.fcusumano.sam.cedolini.application.estrazione.LettoreCedolini.RisultatoEstrazione;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException;
import com.fcusumano.sam.cedolini.application.estrazione.LettorePdf;
import com.fcusumano.sam.cedolini.application.estrazione.PdfNonLeggibileException;
import com.fcusumano.sam.cedolini.application.estrazione.PdfNonLeggibileException.Causa;
import com.fcusumano.sam.cedolini.config.LettoreProperties;
import com.fcusumano.sam.shared.ContestoWorkspace;
import com.fcusumano.sam.shared.WorkspaceId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * Elaborazione asincrona dei file caricati, uno per thread virtuale:
 * <ol>
 *   <li>file identico (hash SHA-256 del PDF originale) gia' archiviato: DUPLICATO, senza leggerlo di nuovo;</li>
 *   <li>IN_LETTURA: apertura del PDF (decifratura se protetto: password del lotto, password configurate, codici
 *       fiscali delle persone censite) e lettura con il {@link LettoreCedolini} configurato, con al massimo
 *       {@code salary-manager.letture-contemporanee} letture contemporanee;</li>
 *   <li>persistenza serializzata per workspace ({@link AcquisizioneCedolino}).</li>
 * </ol>
 * Ogni cambio di stato e' committato subito, cosi' il polling della UI vede l'avanzamento. File identici nello stesso
 * lotto sono elaborati uno dopo l'altro: il secondo risulta DUPLICATO senza una seconda estrazione.
 * Un PDF protetto che nessuna password candidata apre torna IN_CODA e viene riprovato una volta a fine lotto: gli altri
 * file possono aver censito nel frattempo la persona il cui codice fiscale e' la password.
 * Gli errori diventano stato ERRORE con un messaggio comprensibile; i log riportano solo id e tipo di errore.
 */
@Component
class ElaborazioneCaricamenti implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ElaborazioneCaricamenti.class);
    private static final long ATTESA_CHIUSURA_SECONDI = 30;

    private final CaricamentiRepository caricamenti;
    private final CedoliniRepository cedolini;
    private final LettorePdf lettorePdf;
    private final LettoreCedolini lettore;
    private final AcquisizioneCedolino acquisizione;
    private final ConsultazioneAnagrafica consultazione;
    private final Semaphore estrazioni;
    private final ExecutorService esecutore =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("caricamento-", 1).factory());
    private final Map<String, CompletableFuture<Void>> inCorsoPerHash = new ConcurrentHashMap<>();

    ElaborazioneCaricamenti(CaricamentiRepository caricamenti, CedoliniRepository cedolini, LettorePdf lettorePdf,
                            LettoreCedolini lettore, AcquisizioneCedolino acquisizione,
                            ConsultazioneAnagrafica consultazione, LettoreProperties lettoreProperties) {
        this.caricamenti = caricamenti;
        this.cedolini = cedolini;
        this.lettorePdf = lettorePdf;
        this.lettore = lettore;
        this.acquisizione = acquisizione;
        this.consultazione = consultazione;
        this.estrazioni = new Semaphore(Math.max(1, lettoreProperties.lettureContemporanee()), true);
    }

    /**
     * File da elaborare.
     *
     * @param password password di apertura indicata per il lotto (solo in memoria), null se assente
     */
    record Lavoro(WorkspaceId workspace, CaricamentoId caricamentoId, String nomeFileOriginale, byte[] contenuto,
                  String hash, String password) {

        /** Solo identificativi: mai password, nome file o contenuto. */
        @Override
        public String toString() {
            return "Lavoro[workspace=" + workspace.valore() + ", caricamento=" + caricamentoId.valore() + "]";
        }
    }

    /** Avvia l'elaborazione asincrona dei file di un lotto (gia' registrati IN_CODA). */
    void accoda(List<Lavoro> lotto) {
        var avanzamento = new AvanzamentoLotto(lotto.size(), lotto.size() > 1);
        lotto.forEach(lavoro -> avvia(lavoro, avanzamento));
    }

    /**
     * Il lavoro gira nel workspace del lotto ({@link ContestoWorkspace}): censimento, ricalcoli sincroni e codici
     * fiscali provati come password riguardano solo quel workspace, qualunque sia il workspace della richiesta.
     */
    private void avvia(Lavoro lavoro, AvanzamentoLotto lotto) {
        try {
            esecutore.execute(() -> ContestoWorkspace.esegui(lavoro.workspace(), () -> elabora(lavoro, lotto)));
        } catch (RejectedExecutionException e) {
            termina(lavoro, StatoCaricamento.ERRORE, MessaggiCaricamento.INTERROTTO, null);
        }
    }

    private void elabora(Lavoro lavoro, AvanzamentoLotto lotto) {
        try {
            var turno = attendiTurno(lavoro);
            try {
                elaboraInEsclusiva(lavoro, lotto);
            } finally {
                inCorsoPerHash.remove(chiaveHash(lavoro), turno);
                turno.complete(null);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            termina(lavoro, StatoCaricamento.ERRORE, MessaggiCaricamento.INTERROTTO, null);
        } catch (RuntimeException e) {
            erroreImprevisto(lavoro, e);
        } catch (Error e) {
            // es. OutOfMemoryError di un PDF costruito ad arte: il caricamento non deve restare IN_LETTURA per sempre
            erroreImprevisto(lavoro, e);
            if (e instanceof VirtualMachineError && !(e instanceof OutOfMemoryError || e instanceof StackOverflowError)) {
                throw e;
            }
        } finally {
            riprovaRimandati(lotto.concludi());
        }
    }

    /** Ultimo tentativo per i PDF protetti rimandati, a lotto concluso. */
    private void riprovaRimandati(List<Lavoro> rimandati) {
        if (rimandati.isEmpty()) {
            return;
        }
        var ultimoTentativo = new AvanzamentoLotto(rimandati.size(), false);
        rimandati.forEach(lavoro -> avvia(lavoro, ultimoTentativo));
    }

    /** Attende che finisca l'elaborazione di un file identico dello stesso workspace eventualmente in corso. */
    private CompletableFuture<Void> attendiTurno(Lavoro lavoro) throws InterruptedException {
        var mio = new CompletableFuture<Void>();
        while (true) {
            var altro = inCorsoPerHash.putIfAbsent(chiaveHash(lavoro), mio);
            if (altro == null) {
                return mio;
            }
            try {
                altro.get();
            } catch (ExecutionException e) {
                // il turno precedente si chiude sempre normalmente: si riprova comunque
            }
        }
    }

    private void elaboraInEsclusiva(Lavoro lavoro, AvanzamentoLotto lotto) throws InterruptedException {
        var ws = lavoro.workspace();
        var giaCaricato = cedolini.perHash(ws, lavoro.hash());
        if (giaCaricato.isPresent()) {
            log.info("Caricamento {} scartato: file gia' caricato", lavoro.caricamentoId().valore());
            termina(lavoro, StatoCaricamento.DUPLICATO, MessaggiCaricamento.FILE_GIA_CARICATO, giaCaricato.get());
            return;
        }
        RisultatoEstrazione estrazione;
        estrazioni.acquire();
        try {
            caricamenti.aggiorna(ws, lavoro.caricamentoId(), StatoCaricamento.IN_LETTURA, null, null, null);
            var documento = lettorePdf.apri(lavoro.contenuto(), lavoro.password(), this::codiciFiscaliCensiti);
            estrazione = lettore.estrai(documento.contenutoPerEstrazione(), lavoro.nomeFileOriginale());
        } catch (EstrazioneCedolinoException e) {
            if (e instanceof PdfNonLeggibileException pdf && pdf.causa() == Causa.PROTETTO_DA_PASSWORD
                    && lotto.rimandabile()) {
                log.info("Caricamento {}: PDF protetto non ancora apribile, riprovato a fine lotto",
                        lavoro.caricamentoId().valore());
                caricamenti.aggiorna(ws, lavoro.caricamentoId(), StatoCaricamento.IN_CODA, null, null, null);
                lotto.rimanda(lavoro);
                return;
            }
            log.info("Lettura non riuscita (caricamento {}, motivo {})", lavoro.caricamentoId().valore(), e.motivo());
            termina(lavoro, StatoCaricamento.ERRORE, e.getMessage(), null);
            return;
        } finally {
            estrazioni.release();
        }
        acquisisci(lavoro, estrazione);
    }

    private void acquisisci(Lavoro lavoro, RisultatoEstrazione estrazione) {
        EsitoAcquisizione esito;
        try {
            esito = acquisizione.acquisisci(
                    new DocumentoCaricato(lavoro.workspace(), lavoro.caricamentoId(), lavoro.contenuto(), lavoro.hash()),
                    estrazione);
        } catch (CedolinoNonAcquisibileException | IllegalArgumentException e) {
            // messaggi pensati per l'utente (dati mancanti o non validi), senza dati personali
            log.info("Cedolino non acquisibile (caricamento {}): {}", lavoro.caricamentoId().valore(),
                    e.getClass().getSimpleName());
            termina(lavoro, StatoCaricamento.ERRORE, MessaggiCaricamento.nonAcquisibile(e.getMessage()), null);
            return;
        }
        switch (esito) {
            case Acquisito a -> log.debug("Caricamento {} acquisito", lavoro.caricamentoId().valore());
            case Scartato s -> {
                log.info("Caricamento {} scartato: {}", lavoro.caricamentoId().valore(), s.stato());
                termina(lavoro, s.stato(), s.messaggio(), s.esistente());
            }
        }
    }

    private List<String> codiciFiscaliCensiti() {
        return consultazione.elencoPersone().stream().map(PersonaSintesi::codiceFiscale).toList();
    }

    private void erroreImprevisto(Lavoro lavoro, Throwable e) {
        // il messaggio delle eccezioni tecniche puo' contenere valori estratti: si registra solo il tipo
        log.warn("Errore imprevisto nell'elaborazione del caricamento {}: {}", lavoro.caricamentoId().valore(),
                e.getClass().getName());
        log.debug("Dettaglio dell'errore del caricamento {}", lavoro.caricamentoId().valore(), e);
        termina(lavoro, StatoCaricamento.ERRORE, MessaggiCaricamento.ERRORE_IMPREVISTO, null);
    }

    private void termina(Lavoro lavoro, StatoCaricamento stato, String messaggio, CedolinoId esistente) {
        try {
            caricamenti.aggiorna(lavoro.workspace(), lavoro.caricamentoId(), stato, messaggio, null, esistente);
        } catch (RuntimeException e) {
            log.error("Stato del caricamento {} non aggiornabile: {}", lavoro.caricamentoId().valore(),
                    e.getClass().getName());
        }
    }

    private static String chiaveHash(Lavoro lavoro) {
        return lavoro.workspace().valore() + ":" + lavoro.hash();
    }

    /**
     * Avanzamento di un lotto: chi conclude l'ultimo file raccoglie i PDF protetti rimandati. Un file viene aggiunto ai
     * rimandati prima di concludersi, quindi nessun rimandato va perso.
     */
    private static final class AvanzamentoLotto {

        private final AtomicInteger daConcludere;
        private final boolean rimandabile;
        private final Queue<Lavoro> rimandati = new ConcurrentLinkedQueue<>();

        AvanzamentoLotto(int file, boolean rimandabile) {
            this.daConcludere = new AtomicInteger(file);
            this.rimandabile = rimandabile;
        }

        /** True se un PDF protetto puo' essere riprovato a fine lotto (primo tentativo, lotto con altri file). */
        boolean rimandabile() {
            return rimandabile;
        }

        void rimanda(Lavoro lavoro) {
            rimandati.add(lavoro);
        }

        /** Conclude un file: se era l'ultimo del lotto restituisce i rimandati da riprovare, altrimenti nessuno. */
        List<Lavoro> concludi() {
            if (daConcludere.decrementAndGet() > 0) {
                return List.of();
            }
            var daRiprovare = new ArrayList<Lavoro>();
            for (var lavoro = rimandati.poll(); lavoro != null; lavoro = rimandati.poll()) {
                daRiprovare.add(lavoro);
            }
            return daRiprovare;
        }
    }

    @Override
    public void destroy() throws InterruptedException {
        esecutore.shutdownNow();
        if (!esecutore.awaitTermination(ATTESA_CHIUSURA_SECONDI, TimeUnit.SECONDS)) {
            log.warn("Elaborazioni dei caricamenti non terminate alla chiusura: al riavvio risulteranno interrotte");
        }
    }
}
