package com.fcusumano.sam.web.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * Flusso completo via HTTP sul server reale (porta casuale): contesto completo, lettore a fixture, H2 in memoria.
 * Carica i PDF di fixture (dati inventati, vedi {@code fixtures/README.md}), segue i lotti in polling e legge
 * persone, rapporti, dettaglio, cedolino e PDF; poi note, CCNL ed eliminazione con ricalcolo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApiEndToEndTest {

    private static final String CARTELLA_PDF = "fixtures/pdf/";
    private static final String PASSWORD_PROTETTO = "rssmra80a01h501u";
    private static final String PROTETTO = "rossi-beta-2024-09.pdf";
    private static final Duration ATTESA_MASSIMA = Duration.ofSeconds(60);

    @LocalServerPort
    int porta;

    private RestClient http;

    private record Risposta(HttpStatusCode stato, HttpHeaders intestazioni, byte[] corpo) {

        String json() {
            return new String(corpo, StandardCharsets.UTF_8);
        }

        <T> T leggi(String percorso) {
            return JsonPath.read(json(), percorso);
        }
    }

    @BeforeEach
    void client() {
        http = RestClient.builder().baseUrl("http://localhost:" + porta).build();
    }

    @Test
    void caricamentoConsultazioneEModificheViaHttp() throws Exception {
        assertThat(get("/api/v1/ccnl").stato().value()).isEqualTo(200);

        // PDF protetto: senza password non si apre (nessuna persona censita), con la password del lotto si'
        var senzaPassword = attendi(carica(null, PROTETTO));
        assertThat(senzaPassword.<String>leggi("$.caricamenti[0].stato")).isEqualTo("ERRORE");
        assertThat(senzaPassword.<String>leggi("$.caricamenti[0].messaggio")).contains("password");
        var conPassword = attendi(carica(PASSWORD_PROTETTO, PROTETTO));
        assertThat(conPassword.<String>leggi("$.caricamenti[0].stato")).isEqualTo("ACQUISITO");
        assertThat(conPassword.json()).doesNotContain(PASSWORD_PROTETTO);

        var principali = new ArrayList<String>();
        Stream.of("2023-01", "2023-02", "2023-03", "2023-04", "2023-06", "2023-07", "2023-08", "2023-09", "2023-10",
                        "2023-11", "2023-12", "2023-13", "2024-01", "2024-02", "2024-03", "2024-04", "2024-05",
                        "2024-06", "2024-tfr01")
                .forEach(mese -> principali.add("rossi-alfa-" + mese + ".pdf"));
        Stream.of("2024-06", "2024-07", "2024-08", "2024-10", "2024-11", "2024-12", "2025-01", "2025-02")
                .forEach(mese -> principali.add("rossi-beta-" + mese + ".pdf"));
        var lotto = attendi(carica(null, principali.toArray(String[]::new)));
        assertThat(lotto.<List<String>>leggi("$.caricamenti[*].stato")).hasSize(principali.size()).containsOnly("ACQUISITO");

        // Scarti: cedolino aggiuntivo nello stesso mese e copia di un file gia' caricato
        var scarti = attendi(carica(null, Map.of(
                "rossi-alfa-2024-03-aggiuntivo.pdf", "rossi-alfa-2024-03-aggiuntivo.pdf",
                "copia di gennaio.pdf", "rossi-alfa-2023-01.pdf")));
        assertThat(scarti.<List<String>>leggi("$.caricamenti[*].stato"))
                .containsExactly("DUPLICATO", "AGGIUNTIVO_NON_GESTITO");
        assertThat(scarti.<List<Object>>leggi("$.caricamenti[*].cedolinoEsistenteId")).doesNotContainNull();

        // Persone e rapporti
        var persone = get("/api/v1/persone");
        assertThat(persone.<List<String>>leggi("$[*].etichetta")).containsExactly("Mario Rossi");
        int personaId = persone.leggi("$[0].id");
        var rapporti = get("/api/v1/persone/" + personaId + "/rapporti");
        assertThat(rapporti.<List<String>>leggi("$[*].etichetta"))
                .containsExactly("Beta Consulting S.p.A.", "Alfa Software S.r.l.");
        assertThat(rapporti.<List<Boolean>>leggi("$[*].attuale")).containsExactly(true, false);
        // date contrattuali stampate: prevalgono sul primo e sull'ultimo cedolino caricato
        assertThat(rapporti.<String>leggi("$[1].periodo.da")).isEqualTo("2021-09");
        assertThat(rapporti.<String>leggi("$[1].periodo.a")).isEqualTo("2024-06");
        assertThat(rapporti.<String>leggi("$[1].periodo.fonteInizio")).isEqualTo("DATA_CONTRATTUALE");
        assertThat(rapporti.<String>leggi("$[0].periodo.da")).isEqualTo("2024-06");
        assertThat(rapporti.<Object>leggi("$[0].periodo.a")).isNull();
        int betaId = rapporti.leggi("$[0].id");
        int alfaId = rapporti.leggi("$[1].id");

        // Dettaglio del rapporto concluso: lista unica in ordine cronologico inverso, mensilita' mancante, niente ratei
        var alfa = get("/api/v1/rapporti/" + alfaId);
        List<String> titoli = alfa.leggi("$.mensilita[*].titolo");
        assertThat(titoli.getFirst()).isEqualTo("Fine rapporto 1 – Luglio 2024");
        assertThat(titoli.getLast()).isEqualTo("Gennaio 2023");
        assertThat(titoli).containsSubsequence("Giugno 2024", "Gennaio 2024", "Tredicesima 2023", "Dicembre 2023",
                "Giugno 2023", "Maggio 2023", "Aprile 2023");
        assertThat(titoli.indexOf("Tredicesima 2023")).isEqualTo(titoli.indexOf("Dicembre 2023") - 1);
        assertThat(alfa.<List<String>>leggi("$.mensilita[?(@.titolo == 'Maggio 2023')].stato"))
                .containsExactly("MANCANTE");
        assertThat(alfa.<List<Object>>leggi("$.mensilita[?(@.stato == 'MANCANTE')].cedolinoId")).containsOnlyNulls();
        assertThat(alfa.<Boolean>leggi("$.attuale")).isFalse();
        assertThat(alfa.<String>leggi("$.ccnl.codice")).isEqualTo("METALMECCANICO_INDUSTRIA");
        assertThat(alfa.<List<Object>>leggi("$.riepilogo.ratei.saldi")).isEmpty();
        assertThat(alfa.<Boolean>leggi("$.riepilogo.tfr.liquidato")).isTrue();
        assertThat(alfa.<List<String>>leggi("$.segnalazioni[*].tipo")).contains("MENSILITA_MANCANTE");

        // Dettaglio del rapporto attuale: ratei esposti, CCNL da indicare
        var beta = get("/api/v1/rapporti/" + betaId);
        assertThat(beta.<Boolean>leggi("$.attuale")).isTrue();
        assertThat(beta.<Boolean>leggi("$.ccnlDaIndicare")).isTrue();
        assertThat(beta.<List<Object>>leggi("$.riepilogo.ratei.saldi")).isNotEmpty();
        assertThat(beta.<List<String>>leggi("$.mensilita[*].titolo"))
                .startsWith("Febbraio 2025", "Gennaio 2025", "Dicembre 2024");

        // Dettaglio del cedolino e PDF originale (anche quello protetto resta cifrato com'era)
        int settembreId = beta.<List<Integer>>leggi("$.mensilita[?(@.titolo == 'Settembre 2024')].cedolinoId")
                .getFirst();
        var cedolino = get("/api/v1/cedolini/" + settembreId);
        assertThat(cedolino.<String>leggi("$.titolo")).isEqualTo("Settembre 2024");
        assertThat(cedolino.<Integer>leggi("$.rapportoId")).isEqualTo(betaId);
        var pdf = get("/api/v1/cedolini/" + settembreId + "/pdf");
        assertThat(pdf.intestazioni().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        var nomeFile = cedolino.<String>leggi("$.nomeFile");
        assertThat(nomeFile).matches("2024\\.09_\\d+_\\d+\\.pdf");
        assertThat(pdf.intestazioni().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"" + nomeFile + "\"; filename*=UTF-8''" + nomeFile);
        assertThat(pdf.corpo()).isEqualTo(fixture(PROTETTO));

        // Note e CCNL (il cambio ricalcola il riepilogo)
        var note = put("/api/v1/rapporti/" + betaId + "/note", "{\"note\":\"Verificare il premio di produzione\"}");
        assertThat(note.<String>leggi("$.note")).isEqualTo("Verificare il premio di produzione");
        var ricalcolato = put("/api/v1/rapporti/" + betaId + "/ccnl", "{\"ccnlCodice\":\"COMMERCIO\"}");
        assertThat(ricalcolato.stato().value()).isEqualTo(200);
        assertThat(ricalcolato.<Boolean>leggi("$.ccnlDaIndicare")).isFalse();
        assertThat(ricalcolato.<Boolean>leggi("$.ccnlSceltoDaUtente")).isTrue();
        assertThat(ricalcolato.<Integer>leggi("$.riepilogo.retribuzione.mensilita")).isEqualTo(14);
        assertThat(ricalcolato.<String>leggi("$.note")).isEqualTo("Verificare il premio di produzione");
        assertThat(put("/api/v1/rapporti/" + betaId + "/ccnl", "{\"ccnlCodice\":\"INESISTENTE\"}").stato().value())
                .isEqualTo(400);

        // Eliminazione: il dettaglio viene ricalcolato da zero e la mensilita' risulta mancante
        int febbraioId = alfa.<List<Integer>>leggi("$.mensilita[?(@.titolo == 'Febbraio 2023')].cedolinoId").getFirst();
        assertThat(http.delete().uri("/api/v1/cedolini/" + febbraioId).retrieve().toBodilessEntity()
                .getStatusCode().value()).isEqualTo(204);
        assertThat(get("/api/v1/cedolini/" + febbraioId).stato().value()).isEqualTo(404);
        var dopo = get("/api/v1/rapporti/" + alfaId);
        assertThat(dopo.<List<String>>leggi("$.mensilita[?(@.titolo == 'Febbraio 2023')].stato"))
                .containsExactly("MANCANTE");

        // Elenco caricamenti
        var recenti = get("/api/v1/caricamenti?limite=3");
        assertThat(recenti.<List<Object>>leggi("$")).hasSize(3);
    }

    @Test
    void erroriViaHttp() throws Exception {
        var nonPdf = new MultipartBodyBuilder();
        nonPdf.part("files", "solo testo".getBytes(StandardCharsets.UTF_8)).filename("note.txt")
                .contentType(MediaType.TEXT_PLAIN);
        var risposta = scambia(http.post().uri("/api/v1/caricamenti").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(nonPdf.build()));
        assertThat(risposta.stato().value()).isEqualTo(400);
        assertThat(risposta.intestazioni().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(risposta.<String>leggi("$.detail")).contains("note.txt");

        // Molti file in un'unica richiesta: oltre il limite predefinito di Tomcat (50 parti) arrivano alla validazione
        var molti = new MultipartBodyBuilder();
        for (int i = 1; i <= 120; i++) {
            molti.part("files", ("file " + i).getBytes(StandardCharsets.UTF_8)).filename("nota-" + i + ".txt")
                    .contentType(MediaType.TEXT_PLAIN);
        }
        var troppiNonPdf = scambia(http.post().uri("/api/v1/caricamenti").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(molti.build()));
        assertThat(troppiNonPdf.stato().value()).isEqualTo(400);
        assertThat(troppiNonPdf.<String>leggi("$.detail")).startsWith("Sono ammessi solo file PDF");

        // File oltre il limite per file: rifiutato dal container con 413 in Problem Details
        var enorme = new byte[25 * 1024 * 1024 + 1];
        System.arraycopy("%PDF-1.4".getBytes(StandardCharsets.US_ASCII), 0, enorme, 0, 8);
        var grande = new MultipartBodyBuilder();
        grande.part("files", enorme).filename("enorme.pdf").contentType(MediaType.APPLICATION_PDF);
        var troppoGrande = scambia(http.post().uri("/api/v1/caricamenti").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(grande.build()));
        assertThat(troppoGrande.stato().value()).isEqualTo(413);
        assertThat(troppoGrande.<String>leggi("$.detail")).contains("25 MB");

        assertThat(get("/api/v1/rapporti/987654").stato().value()).isEqualTo(404);
        assertThat(get("/api/v1/caricamenti/lotti/00000000-0000-4000-8000-000000000000").stato().value())
                .isEqualTo(404);
        assertThat(get("/api/v1/caricamenti?limite=0").stato().value()).isEqualTo(400);
    }

    // ------------------------------------------------------------------------------------------ supporto

    private Risposta carica(String password, String... fixture) throws IOException {
        var nomi = new LinkedHashMap<String, String>();
        for (var nome : fixture) {
            nomi.put(nome, nome);
        }
        return carica(password, nomi);
    }

    /** Carica le fixture indicate (nome con cui caricare -> fixture) in un unico lotto, in ordine di nome. */
    private Risposta carica(String password, Map<String, String> fixture) throws IOException {
        var corpo = new MultipartBodyBuilder();
        for (var voce : new TreeMap<>(fixture).entrySet()) {
            corpo.part("files", fixture(voce.getValue())).filename(voce.getKey()).contentType(MediaType.APPLICATION_PDF);
        }
        if (password != null) {
            corpo.part("password", password);
        }
        var risposta = scambia(http.post().uri("/api/v1/caricamenti").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(corpo.build()));
        assertThat(risposta.stato().value()).isEqualTo(202);
        assertThat(risposta.intestazioni().getLocation()).isNotNull();
        return risposta;
    }

    /** Segue il lotto in polling finche' tutti i caricamenti sono in uno stato terminale. */
    private Risposta attendi(Risposta avvio) throws InterruptedException {
        var percorso = avvio.intestazioni().getLocation().getPath();
        var scadenza = Instant.now().plus(ATTESA_MASSIMA);
        while (true) {
            var stato = get(percorso);
            if (stato.<Boolean>leggi("$.completato")) {
                return stato;
            }
            assertThat(Instant.now()).as("lotto completato entro %s", ATTESA_MASSIMA).isBefore(scadenza);
            Thread.sleep(100);
        }
    }

    private static byte[] fixture(String nome) throws IOException {
        return new ClassPathResource(CARTELLA_PDF + nome).getContentAsByteArray();
    }

    private Risposta get(String percorso) {
        return scambia(http.get().uri(percorso));
    }

    private Risposta put(String percorso, String json) {
        return scambia(http.put().uri(percorso).contentType(MediaType.APPLICATION_JSON).body(json));
    }

    private static Risposta scambia(RestClient.RequestHeadersSpec<?> richiesta) {
        return richiesta.exchange((req, res) ->
                new Risposta(res.getStatusCode(), res.getHeaders(), res.getBody().readAllBytes()));
    }
}
