package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.cedolini.CedolinoAcquisito;
import com.fcusumano.sam.cedolini.CedolinoEliminato;
import com.fcusumano.sam.cedolini.application.estrazione.LettoreCedolini;
import com.fcusumano.sam.cedolini.application.estrazione.InterpreteRispostaEstrazione;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Supporto ai test di integrazione del caricamento: lettoreCedolini programmabile (varianti delle fixture registrate per
 * nome file, conteggio delle estrazioni), registro degli eventi pubblicati (anche dai thread di elaborazione) e PDF
 * di prova. Solo dati inventati.
 */
final class SupportoCaricamenti {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private SupportoCaricamenti() {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Configurazione {

        @Bean
        @Primary
        LettoreProgrammabile lettoreProgrammabile(
                @Qualifier("fixtureLettoreCedolini") LettoreCedolini fixture, Clock clock) {
            return new LettoreProgrammabile(fixture, InterpreteRispostaEstrazione.standard(clock));
        }

        @Bean
        EventiRegistrati eventiRegistrati() {
            return new EventiRegistrati();
        }
    }

    /**
     * Lettore che, per i nomi file registrati, interpreta un JSON di variante (passando dalla stessa validazione
     * delle risposte del modello); per gli altri delega all'lettoreCedolini a fixture. Conta le estrazioni per nome file.
     */
    static final class LettoreProgrammabile implements LettoreCedolini {

        private final LettoreCedolini fixture;
        private final InterpreteRispostaEstrazione interprete;
        private final Map<String, String> varianti = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> estrazioni = new ConcurrentHashMap<>();
        private final Map<String, Duration> ritardi = new ConcurrentHashMap<>();

        LettoreProgrammabile(LettoreCedolini fixture, InterpreteRispostaEstrazione interprete) {
            this.fixture = fixture;
            this.interprete = interprete;
        }

        @Override
        public RisultatoEstrazione estrai(byte[] pdf) {
            return fixture.estrai(pdf);
        }

        @Override
        public RisultatoEstrazione estrai(byte[] pdf, String nomeFileOriginale) {
            estrazioni.computeIfAbsent(nomeFileOriginale, n -> new AtomicInteger()).incrementAndGet();
            var ritardo = ritardi.get(nomeFileOriginale);
            if (ritardo != null) {
                try {
                    Thread.sleep(ritardo);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            var variante = varianti.get(nomeFileOriginale);
            return variante != null ? interprete.interpreta(variante, "test") : fixture.estrai(pdf, nomeFileOriginale);
        }

        /** Registra per il nome file una variante della fixture indicata, modificata dal correttore. */
        void registraVariante(String nomeFile, String fixtureBase, Consumer<ObjectNode> correttore) {
            var radice = (ObjectNode) JSON.readTree(testoRisorsa("/fixtures/cedolini/" + fixtureBase + ".json"));
            correttore.accept(radice);
            varianti.put(nomeFile, JSON.writeValueAsString(radice));
        }

        /** Rallenta l'estrazione del file indicato (simula la durata di una lettura reale). */
        void rallenta(String nomeFile, Duration ritardo) {
            ritardi.put(nomeFile, ritardo);
        }

        int estrazioni(String nomeFile) {
            var contatore = estrazioni.get(nomeFile);
            return contatore == null ? 0 : contatore.get();
        }

        void azzera() {
            varianti.clear();
            estrazioni.clear();
            ritardi.clear();
        }
    }

    /** Eventi del modulo cedolini pubblicati in qualunque thread. */
    static final class EventiRegistrati {

        final List<CedolinoAcquisito> acquisiti = new CopyOnWriteArrayList<>();
        final List<CedolinoEliminato> eliminati = new CopyOnWriteArrayList<>();

        @EventListener
        void on(CedolinoAcquisito evento) {
            acquisiti.add(evento);
        }

        @EventListener
        void on(CedolinoEliminato evento) {
            eliminati.add(evento);
        }

        void azzera() {
            acquisiti.clear();
            eliminati.clear();
        }
    }

    /** PDF di fixture dal classpath ({@code fixtures/pdf/<nome>.pdf}). */
    static byte[] pdfFixture(String nome) {
        try (InputStream in = SupportoCaricamenti.class.getResourceAsStream("/fixtures/pdf/" + nome + ".pdf")) {
            if (in == null) {
                throw new IllegalArgumentException("PDF di fixture assente: " + nome);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** PDF di una pagina con il testo indicato: contenuto (e hash) diverso per ogni testo. */
    static byte[] pdfConTesto(String testo) {
        try (var documento = new PDDocument(); var uscita = new ByteArrayOutputStream()) {
            var pagina = new PDPage();
            documento.addPage(pagina);
            try (var contenuto = new PDPageContentStream(documento, pagina)) {
                contenuto.beginText();
                contenuto.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contenuto.newLineAtOffset(72, 720);
                contenuto.showText(testo);
                contenuto.endText();
            }
            documento.save(uscita);
            return uscita.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String testoRisorsa(String percorso) {
        try (InputStream in = SupportoCaricamenti.class.getResourceAsStream(percorso)) {
            if (in == null) {
                throw new IllegalArgumentException("Risorsa assente: " + percorso);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
