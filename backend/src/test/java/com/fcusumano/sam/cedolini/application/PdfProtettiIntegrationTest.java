package com.fcusumano.sam.cedolini.application;

import static com.fcusumano.sam.cedolini.application.SupportoCaricamenti.pdfConTesto;
import static com.fcusumano.sam.cedolini.application.SupportoCaricamenti.pdfFixture;
import static com.fcusumano.sam.cedolini.application.SupportoCaricamenti.pdfProtettoConTesto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fcusumano.sam.cedolini.ArchivioCedolini;
import com.fcusumano.sam.cedolini.CaricamentiInErrore;
import com.fcusumano.sam.cedolini.Caricamento;
import com.fcusumano.sam.cedolini.CaricamentoCedolini;
import com.fcusumano.sam.cedolini.CaricamentoInErrore;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.FileCaricato;
import com.fcusumano.sam.cedolini.MotivoErrore;
import com.fcusumano.sam.cedolini.StatoCaricamento;
import com.fcusumano.sam.cedolini.application.SupportoCaricamenti.LettoreProgrammabile;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException.Motivo;
import com.fcusumano.sam.shared.WorkspaceId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Cedolini protetti da password sul database H2: archiviazione della copia senza protezione, password ricordata cifrata
 * e riusata sui PDF successivi, nuova lettura di tutti i file in errore protetti e sblocco dei PDF gia' archiviati
 * ancora protetti. PDF di fixture (password {@code demo-beta-2025}) o generati al volo; dati inventati.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(SupportoCaricamenti.Configurazione.class)
class PdfProtettiIntegrationTest {

    private static final WorkspaceId WS = WorkspaceId.DEFAULT;
    private static final String PASSWORD = "demo-beta-2025";
    private static final Duration ATTESA_MASSIMA = Duration.ofSeconds(60);

    @Autowired
    CaricamentoCedolini caricamento;
    @Autowired
    CaricamentiInErrore inErrore;
    @Autowired
    ArchivioCedolini archivio;
    @Autowired
    CaricamentiRepository caricamentiRepository;
    @Autowired
    PasswordPdfRicordate ricordate;
    @Autowired
    SbloccoPdfArchiviati sblocco;
    @Autowired
    LettoreProgrammabile lettoreCedolini;
    @Autowired
    DSLContext dsl;

    @BeforeEach
    void databaseVuoto() {
        dsl.execute("DELETE FROM caricamento_attribuzione");
        dsl.execute("DELETE FROM caricamento_pdf");
        dsl.execute("DELETE FROM caricamento");
        dsl.execute("DELETE FROM cedolino");
        dsl.execute("DELETE FROM rapporto_lavoro");
        dsl.execute("DELETE FROM persona");
        dsl.execute("DELETE FROM azienda");
        dsl.execute("DELETE FROM password_pdf");
        lettoreCedolini.azzera();
    }

    @Test
    void ilCedolinoProtettoSiArchiviaSenzaProtezioneERestaUnDuplicato() throws IOException {
        var originale = pdfFixture("rossi-beta-2025-03");

        var acquisito = carica(PASSWORD, new FileCaricato("rossi-beta-2025-03.pdf", originale));

        assertThat(acquisito.stato()).isEqualTo(StatoCaricamento.ACQUISITO);
        var archiviato = archivio.pdf(acquisito.cedolinoId()).orElseThrow().contenuto();
        try (var copia = Loader.loadPDF(archiviato)) {
            assertThat(copia.isEncrypted()).isFalse();
            assertThat(new PDFTextStripper().getText(copia)).contains("Fixture: rossi-beta-2025-03");
        }
        assertThat(pdfProtetto(acquisito.cedolinoId())).isFalse();
        var ricaricato = carica(null, new FileCaricato("di-nuovo.pdf", originale));
        assertThat(ricaricato.stato()).as("l'hash resta quello del PDF caricato").isEqualTo(StatoCaricamento.DUPLICATO);
    }

    @Test
    void laPasswordCheApreUnPdfVieneRicordataCifrataEProvataSuiCaricamentiSuccessivi() {
        var acquisito = carica(PASSWORD, new FileCaricato("rossi-beta-2025-03.pdf", pdfFixture("rossi-beta-2025-03")));
        assertThat(acquisito.stato()).isEqualTo(StatoCaricamento.ACQUISITO);

        assertThat(ricordate.elenco(WS)).containsExactly(PASSWORD);
        var salvate = dsl.fetch("SELECT password_cifrata FROM password_pdf").getValues(0, byte[].class);
        assertThat(salvate).singleElement().satisfies(
                cifrata -> assertThat(new String(cifrata, StandardCharsets.ISO_8859_1)).doesNotContain(PASSWORD));

        // stesso PDF di un mese diverso, caricato senza password: si apre con quella ricordata
        var febbraio = pdfProtettoConTesto("Fixture: rossi-beta-2025-02", PASSWORD);
        assertThat(carica(null, new FileCaricato("febbraio.pdf", febbraio)).stato())
                .isEqualTo(StatoCaricamento.ACQUISITO);
        assertThat(ricordate.ricorda(WS, PASSWORD)).as("una password gia' ricordata non si salva due volte").isFalse();
        assertThat(ricordate.elenco(WS)).hasSize(1);
    }

    @Test
    void unaPasswordSbagliataNonVieneRicordata() {
        var errore = carica("sbagliata", new FileCaricato("rossi-beta-2025-03.pdf", pdfFixture("rossi-beta-2025-03")));

        assertThat(errore.motivo()).isEqualTo(MotivoErrore.PDF_PROTETTO);
        assertThat(ricordate.elenco(WS)).isEmpty();
    }

    @Test
    void dopoLaPasswordGiustaSiRileggonoTuttiIFileProtettiInErrore() {
        var marzo = carica(null, new FileCaricato("rossi-beta-2025-03.pdf", pdfFixture("rossi-beta-2025-03")));
        var febbraio = carica(null, new FileCaricato("febbraio.pdf",
                pdfProtettoConTesto("Fixture: rossi-beta-2025-02", PASSWORD)));
        var altraPassword = carica(null, new FileCaricato("altra-password.pdf",
                pdfProtettoConTesto("Fixture: rossi-beta-2025-01", "tutt'altra")));
        assertThat(List.of(marzo, febbraio, altraPassword)).extracting(Caricamento::motivo)
                .containsOnly(MotivoErrore.PDF_PROTETTO);

        var riletto = attendi(inErrore.rileggi(marzo.id(), PASSWORD).getFirst().lotto()).getFirst();
        assertThat(riletto.stato()).isEqualTo(StatoCaricamento.ACQUISITO);

        var registrati = inErrore.rileggiProtetti();

        assertThat(registrati).extracting(Caricamento::nomeFileOriginale)
                .containsExactlyInAnyOrder("febbraio.pdf", "altra-password.pdf");
        assertThat(registrati).extracting(Caricamento::lotto).as("un solo lotto").containsOnly(
                registrati.getFirst().lotto());
        var esiti = attendi(registrati.getFirst().lotto());
        assertThat(esiti).filteredOn(c -> c.nomeFileOriginale().equals("febbraio.pdf")).singleElement()
                .extracting(Caricamento::stato).isEqualTo(StatoCaricamento.ACQUISITO);
        assertThat(esiti).filteredOn(c -> c.nomeFileOriginale().equals("altra-password.pdf")).singleElement()
                .extracting(Caricamento::motivo).isEqualTo(MotivoErrore.PDF_PROTETTO);
        assertThat(inErrore.elenco()).extracting(CaricamentoInErrore::nomeFileOriginale)
                .containsExactly("altra-password.pdf");
        assertThat(inErrore.rileggiProtetti()).as("il file rimasto si rilegge di nuovo").hasSize(1);
    }

    @Test
    void senzaFileProtettiInErroreNonSiRileggeNulla() {
        lettoreCedolini.registraErrore("scansione.pdf", new EstrazioneCedolinoException(Motivo.PDF_SENZA_TESTO,
                "Il PDF non contiene testo selezionabile"));
        carica(null, new FileCaricato("scansione.pdf", pdfConTesto("Scansione")));

        assertThat(inErrore.rileggiProtetti()).isEmpty();
    }

    @Test
    void unPdfApertoDallaPasswordMaNonLettoSiConservaSenzaProtezione() throws IOException {
        var protetto = pdfFixture("rossi-beta-2025-03");
        var errore = carica(null, new FileCaricato("rossi-beta-2025-03.pdf", protetto));
        var hash = CaricamentoCedoliniService.sha256(protetto);
        assertThat(caricamentiRepository.pdf(WS, hash)).hasValue(protetto);
        lettoreCedolini.registraErrore("rossi-beta-2025-03.pdf", new EstrazioneCedolinoException(
                Motivo.LAYOUT_NON_RICONOSCIUTO, "Layout non riconosciuto"));

        var riletto = attendi(inErrore.rileggi(errore.id(), PASSWORD).getFirst().lotto()).getFirst();

        assertThat(riletto.motivo()).isEqualTo(MotivoErrore.LAYOUT_NON_RICONOSCIUTO);
        try (var conservato = Loader.loadPDF(caricamentiRepository.pdf(WS, hash).orElseThrow())) {
            assertThat(conservato.isEncrypted()).isFalse();
        }
        assertThat(ricordate.elenco(WS)).containsExactly(PASSWORD);
    }

    @Test
    void iPdfArchiviatiAncoraProtettiSiSbloccanoQuandoSiRicordaLaPassword() throws Exception {
        var protetto = pdfProtettoConTesto("Fixture: rossi-beta-2025-02", PASSWORD);
        var acquisito = carica(PASSWORD, new FileCaricato("febbraio.pdf", protetto));
        // come un cedolino archiviato prima di questa versione: PDF originale protetto, stato da verificare
        dsl.execute("UPDATE cedolino_pdf SET contenuto = ?, protetto = NULL WHERE cedolino_id = ?", protetto,
                acquisito.cedolinoId().valore());
        dsl.execute("DELETE FROM password_pdf");

        sblocco.avvia(WS).get(30, TimeUnit.SECONDS);

        assertThat(pdfProtetto(acquisito.cedolinoId())).as("nessuna password nota").isTrue();
        assertThat(apribileSenzaPassword(acquisito.cedolinoId())).isFalse();

        assertThat(ricordate.ricorda(WS, PASSWORD)).isTrue();
        sblocco.avvia(WS).get(30, TimeUnit.SECONDS);

        assertThat(pdfProtetto(acquisito.cedolinoId())).isFalse();
        assertThat(apribileSenzaPassword(acquisito.cedolinoId())).isTrue();
        assertThat(dsl.fetchSingle("SELECT hash_contenuto FROM cedolino WHERE id = ?", acquisito.cedolinoId().valore())
                .get(0, String.class)).isEqualTo(CaricamentoCedoliniService.sha256(protetto));
    }

    @Test
    void allAvvioIPdfDaVerificareSenzaProtezioneSonoMarcatiComeTali() {
        var acquisito = carica(null, new FileCaricato("rossi-beta-2025-02.pdf", pdfFixture("rossi-beta-2025-02")));
        dsl.execute("UPDATE cedolino_pdf SET protetto = NULL");

        sblocco.allAvvio();

        await().atMost(ATTESA_MASSIMA).until(() -> Boolean.FALSE.equals(pdfProtetto(acquisito.cedolinoId())));
    }

    private Boolean pdfProtetto(CedolinoId id) {
        return dsl.fetchSingle("SELECT protetto FROM cedolino_pdf WHERE cedolino_id = ?", id.valore())
                .get(0, Boolean.class);
    }

    private boolean apribileSenzaPassword(CedolinoId id) throws IOException {
        try (var pdf = Loader.loadPDF(archivio.pdf(id).orElseThrow().contenuto())) {
            return !pdf.isEncrypted();
        } catch (InvalidPasswordException e) {
            return false;
        }
    }

    /** Carica un file in un lotto e ne attende l'esito. */
    private Caricamento carica(String password, FileCaricato file) {
        return attendi(caricamento.avvia(List.of(file), password).getFirst().lotto()).getFirst();
    }

    private List<Caricamento> attendi(UUID lotto) {
        await().atMost(ATTESA_MASSIMA).pollInterval(Duration.ofMillis(50))
                .until(() -> caricamento.statoLotto(lotto).stream().allMatch(c -> c.stato().terminale()));
        return caricamento.statoLotto(lotto);
    }
}
