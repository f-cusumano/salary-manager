package com.fcusumano.sam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.anagrafica.PersonaSintesi;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.anagrafica.RapportoSintesi;
import com.fcusumano.sam.calcoli.CalcoliRapporto;
import com.fcusumano.sam.cedolini.ArchivioCedolini;
import com.fcusumano.sam.cedolini.CaricamentiInErrore;
import com.fcusumano.sam.cedolini.Caricamento;
import com.fcusumano.sam.cedolini.CaricamentoCedolini;
import com.fcusumano.sam.cedolini.CaricamentoInErrore;
import com.fcusumano.sam.cedolini.CategoriaVoce;
import com.fcusumano.sam.cedolini.CedolinoAcquisito;
import com.fcusumano.sam.cedolini.CedolinoEliminato;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.CedolinoManuale;
import com.fcusumano.sam.cedolini.CedolinoSintesi;
import com.fcusumano.sam.cedolini.FileCaricato;
import com.fcusumano.sam.cedolini.MotivoErrore;
import com.fcusumano.sam.cedolini.StatoCaricamento;
import com.fcusumano.sam.cedolini.Voce;
import com.fcusumano.sam.segnalazioni.Segnalazione;
import com.fcusumano.sam.segnalazioni.SegnalazioniRapporto;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.TipoMensilita;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Flusso completo tra i moduli con il lettore a fixture (dati inventati): caricamento di un lotto con molte
 * elaborazioni contemporanee, censimento serializzato per workspace, ricalcolo sincrono di riepilogo e segnalazioni
 * nella stessa transazione dell'acquisizione e dell'eliminazione; mensilita' mancante completata da un file in errore
 * associato e inserito a mano.
 */
@SpringBootTest(properties = "salary-manager.letture-contemporanee=8")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FlussoCedoliniIntegrationTest.Guasti.class)
class FlussoCedoliniIntegrationTest {

    private static final Duration ATTESA_MASSIMA = Duration.ofSeconds(60);
    private static final String CF_MARIO = "RSSMRA80A01H501U";
    private static final String CF_OMONIMO = "RSSMRA85C12F205M";
    /** Password del PDF di fixture protetto (rossi-beta-2024-09): il codice fiscale fittizio in minuscolo. */
    private static final String PASSWORD_LOTTO = "rssmra80a01h501u";
    /** Partita IVA (inventata) di Alfa Software nelle fixture. */
    private static final String PIVA_ALFA = "01234567897";

    @Autowired
    CaricamentoCedolini caricamento;
    @Autowired
    CaricamentiInErrore caricamentiInErrore;
    @Autowired
    ArchivioCedolini archivio;
    @Autowired
    ConsultazioneAnagrafica anagrafica;
    @Autowired
    CalcoliRapporto calcoli;
    @Autowired
    SegnalazioniRapporto segnalazioni;
    @Autowired
    Guasti guasti;
    @Autowired
    DSLContext dsl;
    @Autowired
    MockMvc mvc;

    @BeforeEach
    void databaseVuoto() {
        guasti.acquisizione.set(false);
        guasti.eliminazione.set(false);
        dsl.execute("DELETE FROM caricamento_attribuzione");
        dsl.execute("DELETE FROM caricamento_pdf");
        dsl.execute("DELETE FROM caricamento");
        dsl.execute("DELETE FROM cedolino");
        dsl.execute("DELETE FROM rapporto_lavoro");
        dsl.execute("DELETE FROM persona");
        dsl.execute("DELETE FROM azienda");
    }

    /**
     * Tutte le fixture in un solo lotto, elaborate in parallelo: una persona per codice fiscale, una azienda per
     * identificativi, riepilogo e segnalazioni gia' calcolati per ogni rapporto al termine del lotto.
     */
    @RepeatedTest(3)
    void lottoInParalleloCensisceUnaVoltaERicalcolaOgniRapporto() {
        var esiti = carica(tutteLeFixture());

        assertThat(esiti).filteredOn(c -> c.stato() != StatoCaricamento.ACQUISITO)
                .singleElement().satisfies(c -> {
                    assertThat(c.nomeFileOriginale()).isEqualTo("rossi-alfa-2024-03-aggiuntivo.pdf");
                    assertThat(c.stato()).isEqualTo(StatoCaricamento.AGGIUNTIVO_NON_GESTITO);
                });
        var persone = anagrafica.elencoPersone();
        assertThat(persone).extracting(PersonaSintesi::codiceFiscale).containsExactlyInAnyOrder(CF_MARIO, CF_OMONIMO);
        var rapporti = persone.stream().flatMap(p -> anagrafica.rapportiDiPersona(p.id()).stream()).toList();
        assertThat(rapporti).hasSize(3);
        assertThat(rapporti.stream().map(RapportoSintesi::aziendaId).collect(Collectors.toSet())).hasSize(2);
        assertThat(dsl.fetchCount(dsl.selectFrom("azienda"))).isEqualTo(2);

        for (var rapporto : rapporti) {
            var cedolini = archivio.normalizzatiDiRapporto(rapporto.id());
            assertThat(calcoli.riepilogo(rapporto.id())).as("riepilogo del rapporto %s", rapporto.id())
                    .hasValueSatisfying(r -> assertThat(r.mensilita()).hasSize(cedolini.size()));
        }
        var alfa = rapportoDi(CF_MARIO, "Alfa");
        assertThat(segnalazioni.mensilitaMancanti(alfa)).containsExactly(new Competenza.Mensilita(2023, 5));
        assertThat(segnalazioni.segnalazioni(rapportoDi(CF_MARIO, "Beta"))).extracting(Segnalazione::tipo)
                .contains(TipoSegnalazione.CCNL_NON_DETERMINATO);
    }

    @Test
    void lEliminazioneRicalcolaRiepilogoESegnalazioniDaZero() {
        carica(List.of("rossi-alfa-2023-03", "rossi-alfa-2023-04", "rossi-alfa-2023-06"));
        var alfa = rapportoDi(CF_MARIO, "Alfa");
        assertThat(calcoli.riepilogo(alfa).orElseThrow().mensilita()).hasSize(3);
        assertThat(segnalazioni.mensilitaMancanti(alfa)).containsExactly(new Competenza.Mensilita(2023, 5));

        archivio.elimina(cedolino(alfa, 2023, 4));

        assertThat(calcoli.riepilogo(alfa).orElseThrow().mensilita()).hasSize(2);
        assertThat(segnalazioni.mensilitaMancanti(alfa))
                .containsExactly(new Competenza.Mensilita(2023, 4), new Competenza.Mensilita(2023, 5));
    }

    @Test
    void unErroreNelRicalcoloAnnullaLAcquisizione() {
        guasti.acquisizione.set(true);

        var esiti = carica(List.of("rossi-alfa-2023-01"));

        assertThat(esiti).singleElement().satisfies(c -> {
            assertThat(c.stato()).isEqualTo(StatoCaricamento.ERRORE);
            assertThat(c.cedolinoId()).isNull();
        });
        assertThat(anagrafica.elencoPersone()).as("transazione annullata: nessuna anagrafica").isEmpty();
        assertThat(dsl.fetchCount(dsl.selectFrom("cedolino"))).isZero();
        assertThat(dsl.fetchCount(dsl.selectFrom("riepilogo_rapporto"))).isZero();
    }

    @Test
    void unErroreNelRicalcoloAnnullaLEliminazione() {
        carica(List.of("rossi-alfa-2023-03", "rossi-alfa-2023-04"));
        var alfa = rapportoDi(CF_MARIO, "Alfa");
        var aprile = cedolino(alfa, 2023, 4);
        guasti.eliminazione.set(true);

        assertThatThrownBy(() -> archivio.elimina(aprile)).isInstanceOf(IllegalStateException.class);

        assertThat(archivio.dettaglio(aprile)).as("transazione annullata: il cedolino resta").isPresent();
        assertThat(calcoli.riepilogo(alfa).orElseThrow().mensilita()).hasSize(2);
    }

    /**
     * Mensilita' mancante coperta da un file non letto: associato alla mensilita', il file ne prende il posto (riga
     * IN_ERRORE e avviso CEDOLINO_IN_ERRORE invece di MENSILITA_MANCANTE); inserito a mano, la mensilita' e' un
     * cedolino come gli altri e non resta alcuna segnalazione sulla mensilita'.
     */
    @Test
    void unFileInErroreAssociatoEInseritoAManoCompletaLaMensilitaMancante() throws Exception {
        carica(List.of("rossi-alfa-2023-03", "rossi-alfa-2023-04", "rossi-alfa-2023-06"));
        var alfa = rapportoDi(CF_MARIO, "Alfa");
        var maggio = new Competenza.Mensilita(2023, 5);
        assertThat(segnalazioniDiMaggio(alfa)).containsExactly(TipoSegnalazione.MENSILITA_MANCANTE);
        assertThat(rigaDiMaggio(alfa)).containsEntry("stato", "MANCANTE");

        var fallito = caricaFile("maggio-illeggibile.pdf", pdfSenzaCedolino()).getFirst();
        assertThat(fallito.stato()).isEqualTo(StatoCaricamento.ERRORE);
        assertThat(fallito.motivo()).isEqualTo(MotivoErrore.LETTURA_NON_RIUSCITA);
        assertThat(fallito.pdfDisponibile()).isTrue();

        var associato = caricamentiInErrore.associa(fallito.id(), alfa, 2023, 5);

        assertThat(associato.attribuzione().competenza()).isEqualTo(maggio);
        assertThat(segnalazioni.mensilitaMancanti(alfa)).isEmpty();
        assertThat(segnalazioniDiMaggio(alfa)).containsExactly(TipoSegnalazione.CEDOLINO_IN_ERRORE);
        assertThat(segnalazioni.segnalazioni(alfa)).extracting(Segnalazione::tipo)
                .doesNotContain(TipoSegnalazione.MENSILITA_MANCANTE);
        var inErrore = rigaDiMaggio(alfa);
        assertThat(inErrore).containsEntry("stato", "IN_ERRORE").containsEntry("motivoErrore", "LETTURA_NON_RIUSCITA");
        assertThat(numero(inErrore.get("caricamentoId"))).isEqualTo(fallito.id().valore());
        assertThat(inErrore.get("cedolinoId")).isNull();

        var acquisito = caricamentiInErrore.inserisci(fallito.id(), cedolinoDiMaggio());

        assertThat(acquisito.stato()).isEqualTo(StatoCaricamento.ACQUISITO);
        assertThat(acquisito.rapportoId()).isEqualTo(alfa);
        assertThat(caricamentiInErrore.elenco()).extracting(CaricamentoInErrore::id).doesNotContain(fallito.id());
        assertThat(segnalazioni.mensilitaMancanti(alfa)).isEmpty();
        assertThat(segnalazioniDiMaggio(alfa)).isEmpty();
        assertThat(segnalazioni.segnalazioni(alfa)).extracting(Segnalazione::tipo)
                .doesNotContain(TipoSegnalazione.MENSILITA_MANCANTE, TipoSegnalazione.CEDOLINO_IN_ERRORE);
        assertThat(calcoli.riepilogo(alfa).orElseThrow().mensilita()).hasSize(4);
        var presente = rigaDiMaggio(alfa);
        assertThat(presente).containsEntry("stato", "PRESENTE");
        assertThat(numero(presente.get("cedolinoId"))).isEqualTo(acquisito.cedolinoId().valore());
        assertThat(presente.get("caricamentoId")).isNull();
        assertThat(presente.get("motivoErrore")).isNull();
    }

    // ------------------------------------------------------------------------------------------ supporto

    /**
     * Listener che falliscono a comando, eseguiti come quelli di calcoli e segnalazioni: dimostrano che i ricalcoli
     * avvengono nella transazione che pubblica l'evento.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class Guasti {

        final AtomicBoolean acquisizione = new AtomicBoolean();
        final AtomicBoolean eliminazione = new AtomicBoolean();

        @EventListener
        void on(CedolinoAcquisito evento) {
            if (acquisizione.get()) {
                throw new IllegalStateException("Guasto simulato nel ricalcolo dopo l'acquisizione");
            }
        }

        @EventListener
        void on(CedolinoEliminato evento) {
            if (eliminazione.get()) {
                throw new IllegalStateException("Guasto simulato nel ricalcolo dopo l'eliminazione");
            }
        }
    }

    private static List<String> tutteLeFixture() {
        var nomi = new ArrayList<String>();
        IntStream.rangeClosed(1, 12).filter(m -> m != 5).forEach(m -> nomi.add("rossi-alfa-2023-%02d".formatted(m)));
        nomi.add("rossi-alfa-2023-13");
        IntStream.rangeClosed(1, 6).forEach(m -> nomi.add("rossi-alfa-2024-%02d".formatted(m)));
        nomi.add("rossi-alfa-2024-03-aggiuntivo");
        nomi.add("rossi-alfa-2024-tfr01");
        IntStream.rangeClosed(6, 12).forEach(m -> nomi.add("rossi-beta-2024-%02d".formatted(m)));
        nomi.addAll(List.of("rossi-beta-2025-01", "rossi-beta-2025-02", "rossi2-beta-2025-01", "rossi2-beta-2025-02"));
        return nomi;
    }

    private List<Caricamento> carica(List<String> fixture) {
        return caricaLotto(fixture.stream().map(n -> new FileCaricato(n + ".pdf", pdf(n))).toList());
    }

    private List<Caricamento> caricaFile(String nome, byte[] contenuto) {
        return caricaLotto(List.of(new FileCaricato(nome, contenuto)));
    }

    private List<Caricamento> caricaLotto(List<FileCaricato> files) {
        var lotto = caricamento.avvia(files, PASSWORD_LOTTO).getFirst().lotto();
        await().atMost(ATTESA_MASSIMA).pollInterval(Duration.ofMillis(50))
                .until(() -> caricamento.statoLotto(lotto).stream().allMatch(c -> c.stato().terminale()));
        return caricamento.statoLotto(lotto);
    }

    /** Tipi delle segnalazioni del rapporto sulla mensilita' di maggio 2023. */
    private List<TipoSegnalazione> segnalazioniDiMaggio(RapportoLavoroId rapporto) {
        return segnalazioni.segnalazioni(rapporto).stream()
                .filter(s -> new Competenza.Mensilita(2023, 5).equals(s.competenza()))
                .map(Segnalazione::tipo)
                .toList();
    }

    /** Riga della mensilita' di maggio 2023 nel dettaglio del rapporto esposto dall'API. */
    private Map<String, Object> rigaDiMaggio(RapportoLavoroId rapporto) throws Exception {
        var json = mvc.perform(get("/api/v1/rapporti/" + rapporto.valore()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Map<String, Object>> righe = JsonPath.read(json, "$.mensilita[?(@.chiave == '2023.05')]");
        assertThat(righe).as("una sola riga per maggio 2023").hasSize(1);
        return righe.getFirst();
    }

    private static long numero(Object valore) {
        assertThat(valore).isInstanceOf(Number.class);
        return ((Number) valore).longValue();
    }

    /** Maggio 2023 di Mario Rossi in Alfa Software inserito a mano: il minimo indispensabile, totali coerenti. */
    private static CedolinoManuale cedolinoDiMaggio() {
        return new CedolinoManuale(
                new CedolinoManuale.Dipendente(CF_MARIO, "Mario", "Rossi", null, null),
                new CedolinoManuale.DatoreLavoro(null, PIVA_ALFA, null, null, "Alfa Software S.r.l.", null, null),
                null,
                new CedolinoManuale.Periodo(2023, 5, TipoMensilita.ORDINARIA, null, null),
                new CedolinoManuale.Totali(null, new BigDecimal("2100.00"), new BigDecimal("450.00"), null,
                        new BigDecimal("1650.00")),
                null,
                List.of(new Voce(null, "Retribuzione ordinaria", CategoriaVoce.COMPETENZA, null, null, null,
                        new BigDecimal("2100.00"), null)),
                List.of(),
                null, null, null, null, null, false, false);
    }

    /** PDF valido di una pagina che il lettore a fixture non riconosce: la lettura non riesce. */
    private static byte[] pdfSenzaCedolino() {
        try (var documento = new PDDocument(); var uscita = new ByteArrayOutputStream()) {
            var pagina = new PDPage();
            documento.addPage(pagina);
            try (var contenuto = new PDPageContentStream(documento, pagina)) {
                contenuto.beginText();
                contenuto.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contenuto.newLineAtOffset(72, 720);
                contenuto.showText("Documento di prova senza cedolino");
                contenuto.endText();
            }
            documento.save(uscita);
            return uscita.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private RapportoLavoroId rapportoDi(String codiceFiscale, String azienda) {
        var persona = anagrafica.elencoPersone().stream().filter(p -> p.codiceFiscale().equals(codiceFiscale))
                .findFirst().orElseThrow();
        return anagrafica.rapportiDiPersona(persona.id()).stream()
                .filter(r -> r.ragioneSociale().contains(azienda))
                .map(RapportoSintesi::id)
                .findFirst().orElseThrow();
    }

    private CedolinoId cedolino(RapportoLavoroId rapporto, int anno, int mese) {
        var competenza = Competenza.Mensilita.ordinaria(YearMonth.of(anno, mese));
        return archivio.sintesiDiRapporto(rapporto).stream()
                .filter(c -> c.competenza().equals(competenza))
                .map(CedolinoSintesi::id)
                .findFirst().orElseThrow();
    }

    private static byte[] pdf(String nome) {
        try (InputStream in = FlussoCedoliniIntegrationTest.class.getResourceAsStream("/fixtures/pdf/" + nome + ".pdf")) {
            if (in == null) {
                throw new IllegalArgumentException("PDF di fixture assente: " + nome);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
