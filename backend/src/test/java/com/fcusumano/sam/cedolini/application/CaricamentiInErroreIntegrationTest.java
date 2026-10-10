package com.fcusumano.sam.cedolini.application;

import static com.fcusumano.sam.cedolini.application.SupportoCaricamenti.pdfConTesto;
import static com.fcusumano.sam.cedolini.application.SupportoCaricamenti.pdfFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.cedolini.CaricamentiInErroreModificati;
import com.fcusumano.sam.cedolini.Caricamento;
import com.fcusumano.sam.cedolini.CaricamentoCedolini;
import com.fcusumano.sam.cedolini.FileCaricato;
import com.fcusumano.sam.cedolini.MotivoErrore;
import com.fcusumano.sam.cedolini.StatoCaricamento;
import com.fcusumano.sam.cedolini.application.CaricamentiRepository.FileInCoda;
import com.fcusumano.sam.cedolini.application.SupportoCaricamenti.EventiRegistrati;
import com.fcusumano.sam.cedolini.application.SupportoCaricamenti.LettoreProgrammabile;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException.Motivo;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.WorkspaceId;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * File caricati con errore sul database H2 (schema Liquibase): motivo dell'errore, PDF originale conservato uno per
 * file solo se completabile, eliminazione di PDF e associazione quando il file viene archiviato o scartato, ripristino
 * dopo il riavvio. Dati inventati.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(SupportoCaricamenti.Configurazione.class)
class CaricamentiInErroreIntegrationTest {

    private static final WorkspaceId WS = WorkspaceId.DEFAULT;
    private static final Duration ATTESA_MASSIMA = Duration.ofSeconds(60);

    @Autowired
    CaricamentoCedolini caricamento;
    @Autowired
    LettoreProgrammabile lettoreCedolini;
    @Autowired
    EventiRegistrati eventi;
    @Autowired
    CaricamentiRepository caricamentiRepository;
    @Autowired
    RipristinoCaricamentiInterrotti ripristino;
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
        lettoreCedolini.azzera();
        eventi.azzera();
    }

    // ------------------------------------------------------------------------------------------ PDF conservato

    @Test
    void unaLetturaNonRiuscitaConservaIlPdfOriginaleDelFile() {
        var contenuto = pdfConTesto("Scansione di prova");
        lettoreCedolini.registraErrore("scansione.pdf", new EstrazioneCedolinoException(Motivo.PDF_SENZA_TESTO,
                "Il PDF non contiene testo selezionabile"));

        var esito = carica(new FileCaricato("scansione.pdf", contenuto));

        assertThat(esito.stato()).isEqualTo(StatoCaricamento.ERRORE);
        assertThat(esito.motivo()).isEqualTo(MotivoErrore.PDF_SENZA_TESTO);
        assertThat(esito.messaggio()).isEqualTo("Il PDF non contiene testo selezionabile");
        assertThat(esito.pdfDisponibile()).isTrue();
        assertThat(caricamentiRepository.pdf(WS, hash(contenuto))).hasValueSatisfying(
                pdf -> assertThat(pdf).isEqualTo(contenuto));
        assertThat(dsl.fetchSingle("SELECT dimensione FROM caricamento_pdf").get(0, Long.class))
                .isEqualTo(contenuto.length);
    }

    @Test
    void loStessoFileInErroreCaricatoDueVolteHaUnSoloPdfConservato() {
        var contenuto = pdfConTesto("Layout sconosciuto di prova");
        lettoreCedolini.registraErrore("sconosciuto.pdf", new EstrazioneCedolinoException(
                Motivo.LAYOUT_NON_RICONOSCIUTO, "Il lettore locale non riconosce il layout di questo cedolino"));

        var primo = carica(new FileCaricato("sconosciuto.pdf", contenuto));
        var secondo = carica(new FileCaricato("sconosciuto.pdf", contenuto));

        assertThat(List.of(primo, secondo)).allSatisfy(c -> {
            assertThat(c.motivo()).isEqualTo(MotivoErrore.LAYOUT_NON_RICONOSCIUTO);
            assertThat(c.pdfDisponibile()).isTrue();
        });
        assertThat(primo.id()).isNotEqualTo(secondo.id());
        assertThat(pdfConservati()).isEqualTo(1);
    }

    @Test
    void unFileNonPdfNonVieneConservato() {
        var esito = carica(new FileCaricato("appunti.pdf", "non sono un pdf".getBytes()));

        assertThat(esito.motivo()).isEqualTo(MotivoErrore.PDF_NON_VALIDO);
        assertThat(esito.pdfDisponibile()).isFalse();
        assertThat(pdfConservati()).isZero();
    }

    @Test
    void unNuovoTentativoConUnMotivoNonCompletabileEliminaIlPdfConservato() {
        var contenuto = pdfConTesto("Due cedolini di prova");
        lettoreCedolini.registraErrore("doppio.pdf", new EstrazioneCedolinoException(
                Motivo.SERVIZIO_NON_DISPONIBILE, "Servizio di lettura non disponibile: riprova più tardi"));
        var primo = carica(new FileCaricato("doppio.pdf", contenuto));
        assertThat(primo.pdfDisponibile()).isTrue();

        lettoreCedolini.registraErrore("doppio.pdf", new EstrazioneCedolinoException(Motivo.PIU_CEDOLINI,
                "Il PDF contiene 2 cedolini distinti: carica un file per ogni cedolino"));
        var secondo = carica(new FileCaricato("doppio.pdf", contenuto));

        assertThat(secondo.motivo()).isEqualTo(MotivoErrore.PIU_CEDOLINI);
        assertThat(secondo.pdfDisponibile()).isFalse();
        assertThat(rileggi(primo).pdfDisponibile()).as("il PDF e' del file, non del tentativo").isFalse();
        assertThat(pdfConservati()).isZero();
    }

    // ------------------------------------------------------------------------------------------ file risolto

    @Test
    void ilFileArchiviatoPerdePdfEAssociazioneERicalcolaIlRapportoAssociato() {
        var alfa = caricaFixture("rossi-alfa-2023-01").rapportoId();
        var contenuto = pdfConTesto("Cedolino di prova da rileggere");
        lettoreCedolini.registraErrore("rilettura.pdf", new EstrazioneCedolinoException(
                Motivo.SERVIZIO_NON_DISPONIBILE, "Servizio di lettura non disponibile: riprova più tardi"));
        var inErrore = carica(new FileCaricato("rilettura.pdf", contenuto));
        associa(contenuto, alfa, 2023, 2);
        // la nuova lettura riesce: il cedolino e' di un altro rapporto (stessa persona, altra azienda)
        lettoreCedolini.registraVariante("rilettura.pdf", "rossi-beta-2025-01", json -> {
        });
        eventi.azzera();

        var acquisito = carica(new FileCaricato("rilettura.pdf", contenuto));

        assertThat(acquisito.stato()).isEqualTo(StatoCaricamento.ACQUISITO);
        assertThat(acquisito.motivo()).isNull();
        assertThat(acquisito.pdfDisponibile()).isFalse();
        assertThat(acquisito.rapportoId()).isNotNull().isNotEqualTo(alfa);
        assertThat(rileggi(inErrore).pdfDisponibile()).isFalse();
        assertThat(pdfConservati()).isZero();
        assertThat(attribuzioni()).isZero();
        assertThat(eventi.acquisiti).hasSize(1);
        assertThat(eventi.modificatiInErrore).containsExactly(new CaricamentiInErroreModificati(Set.of(alfa)));
        assertThat(dsl.fetchSingle("SELECT contenuto FROM cedolino_pdf WHERE cedolino_id = ?",
                acquisito.cedolinoId().valore()).get(0, byte[].class)).as("si archivia l'originale").isEqualTo(contenuto);
    }

    @Test
    void ilFileArchiviatoNelRapportoAssociatoNonPubblicaAltriEventi() {
        var alfa = caricaFixture("rossi-alfa-2023-01").rapportoId();
        var contenuto = pdfConTesto("Febbraio di prova");
        lettoreCedolini.registraErrore("febbraio.pdf", new EstrazioneCedolinoException(
                Motivo.LAYOUT_NON_RICONOSCIUTO, "Il lettore locale non riconosce il layout di questo cedolino"));
        carica(new FileCaricato("febbraio.pdf", contenuto));
        associa(contenuto, alfa, 2023, 2);
        lettoreCedolini.registraVariante("febbraio.pdf", "rossi-alfa-2023-02", json -> {
        });
        eventi.azzera();

        var acquisito = carica(new FileCaricato("febbraio.pdf", contenuto));

        assertThat(acquisito.rapportoId()).isEqualTo(alfa);
        assertThat(attribuzioni()).isZero();
        assertThat(eventi.acquisiti).hasSize(1);
        assertThat(eventi.modificatiInErrore).as("il ricalcolo del rapporto e' gia' dovuto all'acquisizione")
                .isEmpty();
    }

    @Test
    void unoScartoPerCompetenzaGiaPresenteEliminaPdfEAssociazione() {
        var gennaio = caricaFixture("rossi-alfa-2023-01");
        var contenuto = pdfConTesto("Copia di gennaio");
        lettoreCedolini.registraErrore("copia.pdf", new EstrazioneCedolinoException(
                Motivo.SERVIZIO_NON_DISPONIBILE, "Servizio di lettura non disponibile: riprova più tardi"));
        carica(new FileCaricato("copia.pdf", contenuto));
        associa(contenuto, gennaio.rapportoId(), 2023, 3);
        lettoreCedolini.registraVariante("copia.pdf", "rossi-alfa-2023-01", json -> {
        });
        eventi.azzera();

        var scartato = carica(new FileCaricato("copia.pdf", contenuto));

        assertThat(scartato.stato()).isEqualTo(StatoCaricamento.DUPLICATO);
        assertThat(scartato.cedolinoEsistenteId()).isEqualTo(gennaio.cedolinoId());
        assertThat(pdfConservati()).isZero();
        assertThat(attribuzioni()).isZero();
        assertThat(eventi.modificatiInErrore)
                .containsExactly(new CaricamentiInErroreModificati(Set.of(gennaio.rapportoId())));
    }

    // ------------------------------------------------------------------------------------------ riavvio

    @Test
    void unaNuovaLetturaInterrottaDalRiavvioRestaCompletabile() {
        var contenuto = pdfConTesto("Cedolino in nuova lettura");
        lettoreCedolini.registraErrore("nuova-lettura.pdf", new EstrazioneCedolinoException(
                Motivo.SERVIZIO_NON_DISPONIBILE, "Servizio di lettura non disponibile: riprova più tardi"));
        carica(new FileCaricato("nuova-lettura.pdf", contenuto));
        // nuovo tentativo dello stesso file ancora in lettura quando l'applicazione si ferma
        var tentativo = caricamentiRepository.registra(WS, UUID.randomUUID(),
                List.of(new FileInCoda("nuova-lettura.pdf", contenuto.length, hash(contenuto)))).getFirst();
        caricamentiRepository.aggiorna(WS, tentativo.id(), StatoCaricamento.IN_LETTURA, null, null, null);

        assertThat(ripristino.ripristina()).isEqualTo(1);

        var interrotto = rileggi(tentativo);
        assertThat(interrotto.stato()).isEqualTo(StatoCaricamento.ERRORE);
        assertThat(interrotto.motivo()).isEqualTo(MotivoErrore.INTERROTTO);
        assertThat(interrotto.messaggio()).isEqualTo(MessaggiCaricamento.INTERROTTO_DAL_RIAVVIO);
        assertThat(interrotto.pdfDisponibile()).isTrue();
        assertThat(caricamentiRepository.pdf(WS, hash(contenuto))).hasValueSatisfying(
                pdf -> assertThat(pdf).isEqualTo(contenuto));
    }

    @Test
    void alRiavvioSiEliminanoPdfEAssociazioniDeiFileNonPiuInErrore() {
        var gennaio = caricaFixture("rossi-alfa-2023-01");
        var rapporto = gennaio.rapportoId();
        // file ancora in errore: PDF e associazione restano
        var inErrore = pdfConTesto("Ancora in errore");
        lettoreCedolini.registraErrore("in-errore.pdf", new EstrazioneCedolinoException(
                Motivo.LAYOUT_NON_RICONOSCIUTO, "Il lettore locale non riconosce il layout di questo cedolino"));
        carica(new FileCaricato("in-errore.pdf", inErrore));
        associa(inErrore, rapporto, 2023, 5);
        // residui di file non piu' in errore (es. chiusura non riuscita): file archiviato, file con un tentativo
        // successivo scartato, file senza caricamenti
        var archiviato = hash(pdfFixture("rossi-alfa-2023-01"));
        caricamentiRepository.salvaPdfSeAssente(WS, archiviato, new byte[] {1});
        associa(archiviato, rapporto, 2023, 6);
        var scartato = "e".repeat(64);
        var tentativi = caricamentiRepository.registra(WS, UUID.randomUUID(), List.of(
                new FileInCoda("scartato.pdf", 1, scartato), new FileInCoda("scartato.pdf", 1, scartato)));
        caricamentiRepository.registraErrore(WS, tentativi.get(0).id(), MotivoErrore.SERVIZIO_NON_DISPONIBILE,
                "Servizio di lettura non disponibile");
        caricamentiRepository.aggiorna(WS, tentativi.get(1).id(), StatoCaricamento.DUPLICATO,
                MessaggiCaricamento.duplicato(new Competenza.Mensilita(2023, 1)), null, gennaio.cedolinoId());
        caricamentiRepository.salvaPdfSeAssente(WS, scartato, new byte[] {2});
        caricamentiRepository.salvaPdfSeAssente(WS, "f".repeat(64), new byte[] {3});

        ripristino.ripristina();

        assertThat(dsl.fetch("SELECT hash_contenuto FROM caricamento_pdf").getValues(0, String.class))
                .containsExactly(hash(inErrore));
        assertThat(dsl.fetch("SELECT hash_contenuto FROM caricamento_attribuzione").getValues(0, String.class))
                .containsExactly(hash(inErrore));
    }

    // ------------------------------------------------------------------------------------------ motivi

    @Test
    void ogniMotivoSiRegistraESiRilegge() {
        var motivi = MotivoErrore.values();
        var registrati = caricamentiRepository.registra(WS, UUID.randomUUID(), Arrays.stream(motivi)
                .map(m -> new FileInCoda(m.name().toLowerCase() + ".pdf", 1, hash(m.name().getBytes())))
                .toList());
        for (int i = 0; i < motivi.length; i++) {
            caricamentiRepository.registraErrore(WS, registrati.get(i).id(), motivi[i], "Errore di prova");
        }

        assertThat(caricamento.statoLotto(registrati.getFirst().lotto())).extracting(Caricamento::motivo)
                .containsExactly(motivi);

        // un esito diverso da ERRORE azzera il motivo
        caricamentiRepository.aggiorna(WS, registrati.getFirst().id(), StatoCaricamento.DUPLICATO,
                MessaggiCaricamento.FILE_GIA_CARICATO, null, null);
        assertThat(rileggi(registrati.getFirst()).motivo()).isNull();
    }

    // ------------------------------------------------------------------------------------------ supporto

    private Caricamento caricaFixture(String nome) {
        var esito = carica(new FileCaricato(nome + ".pdf", pdfFixture(nome)));
        assertThat(esito.stato()).isEqualTo(StatoCaricamento.ACQUISITO);
        return esito;
    }

    /** Carica un file in un lotto e ne attende l'esito. */
    private Caricamento carica(FileCaricato file) {
        var lotto = caricamento.avvia(List.of(file)).getFirst().lotto();
        await().atMost(ATTESA_MASSIMA).pollInterval(Duration.ofMillis(50))
                .until(() -> caricamento.statoLotto(lotto).getFirst().stato().terminale());
        return caricamento.statoLotto(lotto).getFirst();
    }

    private Caricamento rileggi(Caricamento c) {
        return caricamento.stato(List.of(c.id())).getFirst();
    }

    private void associa(byte[] contenuto, RapportoLavoroId rapporto, int anno, int mese) {
        associa(hash(contenuto), rapporto, anno, mese);
    }

    private void associa(String hash, RapportoLavoroId rapporto, int anno, int mese) {
        dsl.execute("INSERT INTO caricamento_attribuzione (workspace_id, hash_contenuto, rapporto_id, competenza_anno, "
                + "competenza_mese) VALUES (?, ?, ?, ?, ?)", WS.valore(), hash, rapporto.valore(), anno, mese);
    }

    private int pdfConservati() {
        return dsl.fetchCount(dsl.selectFrom("caricamento_pdf"));
    }

    private int attribuzioni() {
        return dsl.fetchCount(dsl.selectFrom("caricamento_attribuzione"));
    }

    private static String hash(byte[] contenuto) {
        return CaricamentoCedoliniService.sha256(contenuto);
    }
}
