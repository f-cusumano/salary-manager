package com.fcusumano.sam.cedolini.application;

import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.CF_GIOVANNA;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.PIVA_ALFA;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.PIVA_GAMMA;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conDatore;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conDipendente;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conPeriodo;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conRatei;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conRetribuzione;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conScelte;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conTotali;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.essenziale;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.n;
import static com.fcusumano.sam.cedolini.application.SupportoCaricamenti.pdfConTesto;
import static com.fcusumano.sam.cedolini.application.SupportoCaricamenti.pdfFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.anagrafica.RapportoLavoro;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.cedolini.ArchivioCedolini;
import com.fcusumano.sam.cedolini.AttribuzioneCaricamento;
import com.fcusumano.sam.cedolini.CaricamentiInErrore;
import com.fcusumano.sam.cedolini.CaricamentiInErroreModificati;
import com.fcusumano.sam.cedolini.Caricamento;
import com.fcusumano.sam.cedolini.CaricamentoCedolini;
import com.fcusumano.sam.cedolini.CaricamentoId;
import com.fcusumano.sam.cedolini.CaricamentoInErrore;
import com.fcusumano.sam.cedolini.CedolinoAcquisito;
import com.fcusumano.sam.cedolini.CedolinoManuale;
import com.fcusumano.sam.cedolini.ElementiRetributivi;
import com.fcusumano.sam.cedolini.FileCaricato;
import com.fcusumano.sam.cedolini.MotivoErrore;
import com.fcusumano.sam.cedolini.Rateo;
import com.fcusumano.sam.cedolini.StatoCaricamento;
import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.cedolini.VerificaCedolinoManuale;
import com.fcusumano.sam.cedolini.VerificaCedolinoManuale.Esito;
import com.fcusumano.sam.cedolini.application.CaricamentiRepository.FileInCoda;
import com.fcusumano.sam.cedolini.application.SupportoCaricamenti.EventiRegistrati;
import com.fcusumano.sam.cedolini.application.SupportoCaricamenti.LettoreProgrammabile;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException.Motivo;
import com.fcusumano.sam.cedolini.application.estrazione.InterpreteRispostaEstrazione;
import com.fcusumano.sam.cedolini.application.estrazione.LettoreCedolini.RisultatoEstrazione;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.DatiNonValidiException;
import com.fcusumano.sam.shared.DatiNonValidiException.ErroreCampo;
import com.fcusumano.sam.shared.OperazioneNonConsentitaException;
import com.fcusumano.sam.shared.RisorsaNonTrovataException;
import com.fcusumano.sam.shared.TipoMensilita;
import com.fcusumano.sam.shared.UnitaMisura;
import com.fcusumano.sam.shared.WorkspaceId;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
 * dopo il riavvio; azioni sui file in errore (elenco, PDF, associazione a una mensilita', rimozione, nuova lettura,
 * verifica e inserimento a mano). Dati inventati.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(SupportoCaricamenti.Configurazione.class)
class CaricamentiInErroreIntegrationTest {

    private static final WorkspaceId WS = WorkspaceId.DEFAULT;
    /** Persona non presente nelle fixture. */
    private static final CedolinoManuale.Dipendente GIOVANNA =
            new CedolinoManuale.Dipendente(CF_GIOVANNA, "Giovanna", "Bianchi", null, null);
    /** Azienda non presente nelle fixture. */
    private static final CedolinoManuale.DatoreLavoro GAMMA =
            new CedolinoManuale.DatoreLavoro(null, PIVA_GAMMA, null, null, "Gamma Servizi S.r.l.", null, null);
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
    @Autowired
    CaricamentiInErrore inErrore;
    @Autowired
    ArchivioCedolini archivio;
    @Autowired
    ChiusuraCaricamento chiusura;
    @Autowired
    AcquisizioneCedolino acquisizione;
    @Autowired
    ConsultazioneAnagrafica anagrafica;

    @BeforeEach
    void databaseVuoto() {
        dsl.execute("DELETE FROM caricamento_attribuzione");
        dsl.execute("DELETE FROM caricamento_pdf");
        dsl.execute("DELETE FROM caricamento");
        dsl.execute("DELETE FROM password_pdf");
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
        assertThat(interrotto.messaggio()).as("il PDF e' conservato: si rilegge o si inserisce a mano")
                .isEqualTo(MessaggiCaricamento.INTERROTTO_DAL_RIAVVIO_RILEGGIBILE);
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

    @Test
    void alRiavvioUnTentativoNonConclusoDiUnFileGiaArchiviatoDiventaDuplicato() {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        var contenuto = pdfFixture("rossi-alfa-2023-04");
        var tentativo = caricamentiRepository.registra(WS, UUID.randomUUID(),
                List.of(new FileInCoda("copia.pdf", contenuto.length, hash(contenuto)))).getFirst();

        assertThat(ripristino.ripristina()).isZero();

        assertThat(rileggi(tentativo)).satisfies(c -> {
            assertThat(c.stato()).isEqualTo(StatoCaricamento.DUPLICATO);
            assertThat(c.motivo()).isNull();
            assertThat(c.messaggio()).isEqualTo(MessaggiCaricamento.FILE_GIA_CARICATO);
            assertThat(c.cedolinoEsistenteId()).isEqualTo(aprile.cedolinoId());
        });
        archivio.elimina(aprile.cedolinoId());
        assertThat(inErrore.elenco()).as("eliminato il cedolino, il file non torna in errore").isEmpty();
    }

    @Test
    void alRiavvioLaSegnalazioneDelFileAssociatoRiportaLInterruzione() {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        var contenuto = pdfConTesto("Maggio interrotto dal riavvio");
        var caricato = caricaInErrore("maggio.pdf", contenuto);
        inErrore.associa(caricato.id(), aprile.rapportoId(), 2023, 5);
        assertThat(descrizioneCedolinoInErrore(aprile.rapportoId(), 2023, 5)).contains("(Scansione senza testo)");
        var tentativo = caricamentiRepository.registra(WS, UUID.randomUUID(),
                List.of(new FileInCoda("maggio.pdf", contenuto.length, hash(contenuto)))).getFirst();
        caricamentiRepository.aggiorna(WS, tentativo.id(), StatoCaricamento.IN_LETTURA, null, null, null);
        eventi.azzera();

        ripristino.ripristina();

        assertThat(descrizioneCedolinoInErrore(aprile.rapportoId(), 2023, 5)).isEqualTo("Il cedolino di Maggio 2023 "
                + "è stato caricato ma non è stato letto (Elaborazione interrotta): inseriscilo a mano dall'elenco "
                + "«Cedolini caricati con errore» o riprova la lettura.");
        assertThat(eventi.modificatiInErrore)
                .containsExactly(new CaricamentiInErroreModificati(Set.of(aprile.rapportoId())));
    }

    // ------------------------------------------------------------------------------------------ chiusura in errore

    @Test
    void unNuovoErroreDelFileAssociatoAggiornaMotivoEAzioniDellaSegnalazione() {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        var contenuto = pdfConTesto("Maggio con errori diversi");
        var caricato = caricaInErrore("maggio.pdf", contenuto);
        inErrore.associa(caricato.id(), aprile.rapportoId(), 2023, 5);
        assertThat(descrizioneCedolinoInErrore(aprile.rapportoId(), 2023, 5)).contains("(Scansione senza testo)");

        lettoreCedolini.registraErrore("maggio.pdf", new EstrazioneCedolinoException(
                Motivo.SERVIZIO_NON_DISPONIBILE, "Servizio di lettura non disponibile: riprova più tardi"));
        carica(new FileCaricato("maggio.pdf", contenuto));

        assertThat(descrizioneCedolinoInErrore(aprile.rapportoId(), 2023, 5)).isEqualTo("Il cedolino di Maggio 2023 "
                + "è stato caricato ma non è stato letto (Lettore non disponibile): inseriscilo a mano dall'elenco "
                + "«Cedolini caricati con errore» o riprova la lettura.");

        lettoreCedolini.registraErrore("maggio.pdf", new EstrazioneCedolinoException(Motivo.PIU_CEDOLINI,
                "Il PDF contiene 2 cedolini distinti: carica un file per ogni cedolino"));
        carica(new FileCaricato("maggio.pdf", contenuto));

        assertThat(pdfConservati()).isZero();
        assertThat(attribuzioni()).isEqualTo(1);
        assertThat(descrizioneCedolinoInErrore(aprile.rapportoId(), 2023, 5)).isEqualTo("Il cedolino di Maggio 2023 "
                + "è stato caricato ma non è stato letto (Più cedolini nello stesso PDF): dividi il PDF in un file "
                + "per cedolino e caricali.");
    }

    @Test
    void unErroreConclusoDopoUnTentativoSuccessivoGiaScartatoNonConservaIlPdf() {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        var contenuto = pdfConTesto("Tentativi sovrapposti");
        var tentativi = caricamentiRepository.registra(WS, UUID.randomUUID(), List.of(
                new FileInCoda("sovrapposti.pdf", contenuto.length, hash(contenuto)),
                new FileInCoda("sovrapposti.pdf", contenuto.length, hash(contenuto))));
        // il secondo tentativo si conclude per primo, scartato; l'associazione e' un residuo
        caricamentiRepository.aggiorna(WS, tentativi.get(1).id(), StatoCaricamento.AGGIUNTIVO_NON_GESTITO,
                "Cedolino aggiuntivo di prova", null, aprile.cedolinoId());
        associa(contenuto, aprile.rapportoId(), 2023, 5);
        eventi.azzera();

        chiusura.errore(new ElaborazioneCaricamenti.Lavoro(WS, tentativi.get(0).id(), "sovrapposti.pdf", contenuto,
                hash(contenuto), null), MotivoErrore.PDF_SENZA_TESTO, "Il PDF non contiene testo selezionabile");

        var primo = rileggi(tentativi.get(0));
        assertThat(primo.stato()).isEqualTo(StatoCaricamento.ERRORE);
        assertThat(primo.pdfDisponibile()).isFalse();
        assertThat(pdfConservati()).isZero();
        assertThat(attribuzioni()).isZero();
        assertThat(inErrore.elenco()).isEmpty();
        assertThat(eventi.modificatiInErrore)
                .containsExactly(new CaricamentiInErroreModificati(Set.of(aprile.rapportoId())));
        // anche con un PDF rimasto (chiusura non riuscita) il file non piu' in errore non lo restituisce
        caricamentiRepository.salvaPdfSeAssente(WS, hash(contenuto), contenuto);
        assertThat(inErrore.pdf(primo.id())).isEmpty();
    }

    // ------------------------------------------------------------------------------------------ errori senza motivo

    @Test
    void gliErroriRegistratiPrimaDeiMotiviRestanoGestibiliSenzaPdf() {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        // file con due tentativi in errore registrati prima dei motivi (motivo NULL, nessun PDF conservato) e file
        // con un errore vecchio poi risolto da un duplicato
        var vecchio = "a".repeat(64);
        var risolto = "b".repeat(64);
        var tentativi = caricamentiRepository.registra(WS, UUID.randomUUID(), List.of(
                new FileInCoda("vecchio.pdf", 10, vecchio), new FileInCoda("vecchio.pdf", 10, vecchio),
                new FileInCoda("risolto.pdf", 10, risolto), new FileInCoda("risolto.pdf", 10, risolto)));
        for (int i = 0; i < 3; i++) {
            dsl.execute("UPDATE caricamento SET stato = 'ERRORE', motivo = NULL, messaggio = 'Errore di prova' "
                    + "WHERE id = ?", tentativi.get(i).id().valore());
        }
        caricamentiRepository.aggiorna(WS, tentativi.get(3).id(), StatoCaricamento.DUPLICATO,
                MessaggiCaricamento.FILE_GIA_CARICATO, null, aprile.cedolinoId());
        var ultimo = tentativi.get(1);

        assertThat(inErrore.elenco()).singleElement().satisfies(riga -> {
            assertThat(riga.id()).isEqualTo(ultimo.id());
            assertThat(riga.motivo()).isNull();
            assertThat(riga.messaggio()).isEqualTo("Errore di prova");
            assertThat(riga.pdfDisponibile()).isFalse();
            assertThat(riga.inElaborazione()).isFalse();
        });
        assertThat(inErrore.pdf(ultimo.id())).isEmpty();
        List<Runnable> senzaPdf = List.of(
                () -> inErrore.inserisci(ultimo.id(), sceltoDa(aprile)),
                () -> inErrore.verifica(ultimo.id(), sceltoDa(aprile)),
                () -> inErrore.rileggi(ultimo.id(), null));
        assertThat(senzaPdf).allSatisfy(azione -> assertThatThrownBy(azione::run)
                .isInstanceOf(OperazioneNonConsentitaException.class)
                .hasMessage(MessaggiCaricamento.PDF_NON_CONSERVATO));

        inErrore.associa(ultimo.id(), aprile.rapportoId(), 2023, 5);
        assertThat(descrizioneCedolinoInErrore(aprile.rapportoId(), 2023, 5)).as("nessuna azione senza PDF proposta")
                .isEqualTo("Il cedolino di Maggio 2023 è stato caricato ma non è stato letto: il PDF non è stato "
                        + "conservato, caricalo di nuovo.");

        inErrore.rimuovi(ultimo.id());

        assertThat(dsl.fetchCount(dsl.selectFrom("caricamento").where("hash_contenuto = ?", vecchio))).isZero();
        assertThat(dsl.fetchCount(dsl.selectFrom("caricamento").where("hash_contenuto = ?", risolto))).isEqualTo(2);
        assertThat(attribuzioni()).isZero();
        assertThat(segnalazioniDi(aprile.rapportoId(), 2023, 5)).doesNotContain("CEDOLINO_IN_ERRORE");
        assertThat(inErrore.elenco()).isEmpty();
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

    // ------------------------------------------------------------------------------------------ elenco e PDF

    @Test
    void ilFileInErroreEElencatoConIlPdfOriginale() {
        var contenuto = pdfConTesto("Scansione elencata");
        var caricato = caricaInErrore("scansione.pdf", contenuto);

        assertThat(inErrore.elenco()).singleElement().satisfies(riga -> {
            assertThat(riga.id()).isEqualTo(caricato.id());
            assertThat(riga.lotto()).isEqualTo(caricato.lotto());
            assertThat(riga.nomeFileOriginale()).isEqualTo("scansione.pdf");
            assertThat(riga.dimensione()).isEqualTo(contenuto.length);
            assertThat(riga.motivo()).isEqualTo(MotivoErrore.PDF_SENZA_TESTO);
            assertThat(riga.messaggio()).isEqualTo("Il PDF non contiene testo selezionabile");
            assertThat(riga.pdfDisponibile()).isTrue();
            assertThat(riga.inElaborazione()).isFalse();
            assertThat(riga.attribuzione()).isNull();
            assertThat(riga.creatoIl()).isNotNull();
        });
        assertThat(inErrore.pdf(caricato.id())).hasValueSatisfying(pdf -> {
            assertThat(pdf.nomeFile()).isEqualTo("scansione.pdf");
            assertThat(pdf.contenuto()).isEqualTo(contenuto);
        });
    }

    @Test
    void loStessoFileInErroreCaricatoDueVolteEElencatoUnaVolta() {
        var contenuto = pdfConTesto("Scansione caricata due volte");
        caricaInErrore("doppio.pdf", contenuto);
        var secondo = caricaInErrore("doppio.pdf", contenuto);
        var nonPdf = carica(new FileCaricato("appunti.pdf", "non sono un pdf".getBytes()));

        assertThat(inErrore.elenco()).extracting(CaricamentoInErrore::id).containsExactly(nonPdf.id(), secondo.id());
        assertThat(inErrore.elenco()).extracting(CaricamentoInErrore::pdfDisponibile).containsExactly(false, true);
        assertThat(pdfConservati()).isEqualTo(1);
        assertThat(inErrore.pdf(nonPdf.id())).isEmpty();
    }

    // ------------------------------------------------------------------------------------------ inserimento a mano

    @Test
    void ilCedolinoInseritoAManoVieneArchiviatoConIlPdfOriginale() {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        var contenuto = pdfConTesto("Maggio senza testo");
        var caricato = caricaInErrore("maggio.pdf", contenuto);
        inErrore.associa(caricato.id(), aprile.rapportoId(), 2023, 5);
        assertThat(segnalazioniDi(aprile.rapportoId(), 2023, 5)).containsExactly("CEDOLINO_IN_ERRORE");
        eventi.azzera();

        var esito = inErrore.inserisci(caricato.id(), sceltoDa(aprile));

        assertThat(esito.id()).isEqualTo(caricato.id());
        assertThat(esito.stato()).isEqualTo(StatoCaricamento.ACQUISITO);
        assertThat(esito.motivo()).isNull();
        assertThat(esito.pdfDisponibile()).isFalse();
        assertThat(esito.personaId()).isEqualTo(aprile.personaId());
        assertThat(esito.rapportoId()).isEqualTo(aprile.rapportoId());
        var cedolino = dsl.fetchSingle("SELECT modello_estrazione, confidenza, hash_contenuto FROM cedolino WHERE id = ?",
                esito.cedolinoId().valore());
        assertThat(cedolino.get(0, String.class)).isEqualTo(ModelliEstrazione.MODELLO_INSERIMENTO_MANUALE);
        assertThat(cedolino.get(1)).isNull();
        assertThat(cedolino.get(2, String.class)).isEqualTo(hash(contenuto));
        assertThat(dsl.fetchSingle("SELECT contenuto FROM cedolino_pdf WHERE cedolino_id = ?",
                esito.cedolinoId().valore()).get(0, byte[].class)).isEqualTo(contenuto);
        assertThat(archivio.dettaglio(esito.cedolinoId())).hasValueSatisfying(dettaglio -> {
            assertThat(dettaglio.inseritoAMano()).isTrue();
            assertThat(dettaglio.confidenza()).isNull();
            assertThat(dettaglio.campiNonTrovati()).isEmpty();
            assertThat(dettaglio.avvisiEstrazione()).contains(ValidazioneCedolinoManuale.AVVISO_SOMMA_TRATTENUTE);
            assertThat(dettaglio.dati().competenza()).isEqualTo(new Competenza.Mensilita(2023, 5));
        });
        assertThat(archivio.dettaglio(aprile.cedolinoId())).hasValueSatisfying(
                dettaglio -> assertThat(dettaglio.inseritoAMano()).isFalse());
        assertThat(pdfConservati()).isZero();
        assertThat(attribuzioni()).isZero();
        assertThat(inErrore.elenco()).isEmpty();
        assertThat(eventi.acquisiti).containsExactly(new CedolinoAcquisito(esito.cedolinoId(), aprile.rapportoId(),
                new Competenza.Mensilita(2023, 5)));
        assertThat(archivio.sintesiDiRapporto(aprile.rapportoId())).hasSize(2);
        assertThat(dsl.fetchCount(dsl.selectFrom("riepilogo_rapporto").where("rapporto_id = ?",
                aprile.rapportoId().valore()))).isEqualTo(1);
        assertThat(segnalazioniDi(aprile.rapportoId(), 2023, 5)).as("segnalazioni ricalcolate nell'acquisizione")
                .isEmpty();

        var ricaricato = carica(new FileCaricato("maggio.pdf", contenuto));
        assertThat(ricaricato.stato()).isEqualTo(StatoCaricamento.DUPLICATO);
        assertThat(ricaricato.messaggio()).isEqualTo(MessaggiCaricamento.FILE_GIA_CARICATO);
        assertThat(ricaricato.cedolinoEsistenteId()).isEqualTo(esito.cedolinoId());
    }

    @Test
    void unaCompetenzaGiaPresenteNonSiInserisceEIlFileRestaInErrore() {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        var contenuto = pdfConTesto("Aprile diverso");
        var caricato = caricaInErrore("aprile.pdf", contenuto);
        var aprileDiverso = conPeriodo(sceltoDa(aprile), new CedolinoManuale.Periodo(2023, 4,
                TipoMensilita.ORDINARIA, null, null));
        var prima = conteggi();

        var verifica = inErrore.verifica(caricato.id(), aprileDiverso);

        assertThat(verifica.esito()).isEqualTo(Esito.AGGIUNTIVO_NON_GESTITO);
        assertThat(verifica.messaggio()).isNotBlank();
        assertThat(verifica.competenza()).isNull();
        assertThat(verifica.cedolinoEsistenteId()).isEqualTo(aprile.cedolinoId());
        assertThat(verifica.rapportoId()).isEqualTo(aprile.rapportoId());
        assertThatThrownBy(() -> inErrore.inserisci(caricato.id(), aprileDiverso))
                .isInstanceOf(OperazioneNonConsentitaException.class)
                .hasMessage(verifica.messaggio());
        assertThat(conteggi()).isEqualTo(prima);
        assertThat(rileggi(caricato).stato()).isEqualTo(StatoCaricamento.ERRORE);
        assertThat(inErrore.elenco()).extracting(CaricamentoInErrore::id).containsExactly(caricato.id());
        assertThat(caricamentiRepository.pdf(WS, hash(contenuto))).isPresent();
    }

    @Test
    void iDatiNonValidiNonScrivonoNulla() {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        var caricato = caricaInErrore("non-valido.pdf", pdfConTesto("Dati non validi"));
        var scelti = sceltoDa(aprile);
        var senzaRagioneSociale = conDatore(conScelte(scelti, scelti.personaId(), null),
                new CedolinoManuale.DatoreLavoro(null, PIVA_GAMMA, null, null, "  ", null, null));
        var nuovaPersonaSenzaNome = conDipendente(conScelte(scelti, null, scelti.aziendaId()),
                new CedolinoManuale.Dipendente(CF_GIOVANNA, null, "Bianchi", null, null));
        var prima = conteggi();

        assertThatThrownBy(() -> inErrore.inserisci(caricato.id(), senzaRagioneSociale))
                .isInstanceOfSatisfying(DatiNonValidiException.class, e -> assertThat(e.errori())
                        .containsExactly(new ErroreCampo("datoreLavoro.ragioneSociale", "Indica la ragione sociale")));
        assertThatThrownBy(() -> inErrore.verifica(caricato.id(), nuovaPersonaSenzaNome))
                .isInstanceOfSatisfying(DatiNonValidiException.class, e -> assertThat(e.errori())
                        .extracting(ErroreCampo::campo).containsExactly("dipendente.nome"));
        assertThatThrownBy(() -> inErrore.inserisci(caricato.id(), nuovaPersonaSenzaNome))
                .isInstanceOf(DatiNonValidiException.class);
        assertThat(conteggi()).isEqualTo(prima);
        assertThat(rileggi(caricato).stato()).isEqualTo(StatoCaricamento.ERRORE);
    }

    @Test
    void laVerificaNonLasciaTracceEIndicaCosaVerrebbeCreato() {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        var alfa = aprile.rapportoId();
        var caricato = caricaInErrore("verifica.pdf", pdfConTesto("Cedolino da verificare"));
        inErrore.associa(caricato.id(), alfa, 2023, 6);
        var prima = conteggi();
        var rigaPrima = rileggi(caricato);
        var elencoPrima = inErrore.elenco();
        var scelti = sceltoDa(aprile);

        var stessoRapporto = inErrore.verifica(caricato.id(), scelti);
        var nuovaAzienda = inErrore.verifica(caricato.id(), conDatore(conScelte(scelti, scelti.personaId(), null),
                GAMMA));
        var nuovaPersona = inErrore.verifica(caricato.id(), conDatore(conDipendente(essenziale(), GIOVANNA), GAMMA));

        assertThat(stessoRapporto.esito()).isEqualTo(Esito.ACQUISIBILE);
        assertThat(stessoRapporto.messaggio()).isNull();
        assertThat(stessoRapporto.competenza()).isEqualTo(new Competenza.Mensilita(2023, 5));
        assertThat(stessoRapporto.nuovaPersona()).isFalse();
        assertThat(stessoRapporto.nuovaAzienda()).isFalse();
        assertThat(stessoRapporto.nuovoRapporto()).isFalse();
        assertThat(stessoRapporto.rapportoId()).isEqualTo(alfa);
        assertThat(stessoRapporto.cedolinoEsistenteId()).isNull();
        assertThat(stessoRapporto.avvisi()).contains(ValidazioneCedolinoManuale.AVVISO_SOMMA_TRATTENUTE,
                        "Il periodo indicato è diverso dal mese associato al file (Giugno 2023)")
                .doesNotContain(CaricamentiInErroreService.AVVISO_NUOVA_PERSONA,
                        CaricamentiInErroreService.AVVISO_NUOVA_AZIENDA,
                        CaricamentiInErroreService.AVVISO_NUOVO_RAPPORTO,
                        "Persona o azienda diverse da quelle del mese associato al file (Giugno 2023)");

        assertThat(nuovaAzienda.esito()).isEqualTo(Esito.ACQUISIBILE);
        assertThat(nuovaAzienda.nuovaPersona()).isFalse();
        assertThat(nuovaAzienda.nuovaAzienda()).isTrue();
        assertThat(nuovaAzienda.nuovoRapporto()).isTrue();
        assertThat(nuovaAzienda.rapportoId()).isNull();
        assertThat(nuovaAzienda.avvisi()).as("con un'azienda nuova il rapporto nuovo e' ovvio")
                .contains(CaricamentiInErroreService.AVVISO_NUOVA_AZIENDA,
                        "Persona o azienda diverse da quelle del mese associato al file (Giugno 2023)")
                .doesNotContain(CaricamentiInErroreService.AVVISO_NUOVA_PERSONA,
                        CaricamentiInErroreService.AVVISO_NUOVO_RAPPORTO);

        assertThat(nuovaPersona.esito()).isEqualTo(Esito.ACQUISIBILE);
        assertThat(nuovaPersona.nuovaPersona()).isTrue();
        assertThat(nuovaPersona.nuovaAzienda()).isTrue();
        assertThat(nuovaPersona.nuovoRapporto()).isTrue();
        assertThat(nuovaPersona.rapportoId()).isNull();
        assertThat(nuovaPersona.avvisi()).containsSubsequence(
                        "Persona o azienda diverse da quelle del mese associato al file (Giugno 2023)",
                        CaricamentiInErroreService.AVVISO_NUOVA_PERSONA,
                        CaricamentiInErroreService.AVVISO_NUOVA_AZIENDA)
                .doesNotContain(CaricamentiInErroreService.AVVISO_NUOVO_RAPPORTO);

        assertThat(conteggi()).isEqualTo(prima);
        assertThat(rileggi(caricato)).isEqualTo(rigaPrima);
        assertThat(inErrore.elenco()).isEqualTo(elencoPrima);
        assertThat(archivio.sintesiDiRapporto(alfa)).hasSize(1);
    }

    @Test
    void laVerificaSegnalaUnRapportoNuovoTraAnagraficheInArchivioEDiversoDalMeseAssociato() {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        var beta = rapporto(caricaFixture("rossi2-beta-2025-01").rapportoId());
        var caricato = caricaInErrore("altro-rapporto.pdf", pdfConTesto("Cedolino di un altro rapporto"));
        inErrore.associa(caricato.id(), aprile.rapportoId(), 2023, 5);
        // Mario Rossi (il primo) non ha cedolini con Beta Consulting: rapporto nuovo tra anagrafiche in archivio
        var marioInBeta = conScelte(essenziale(), aprile.personaId().valore(), beta.aziendaId().valore());

        var verifica = inErrore.verifica(caricato.id(), marioInBeta);

        assertThat(verifica.esito()).isEqualTo(Esito.ACQUISIBILE);
        assertThat(verifica.nuovaPersona()).isFalse();
        assertThat(verifica.nuovaAzienda()).isFalse();
        assertThat(verifica.nuovoRapporto()).isTrue();
        assertThat(verifica.rapportoId()).isNull();
        assertThat(verifica.avvisi()).containsSubsequence(
                        "Persona o azienda diverse da quelle del mese associato al file (Maggio 2023)",
                        CaricamentiInErroreService.AVVISO_NUOVO_RAPPORTO)
                .doesNotContain(CaricamentiInErroreService.AVVISO_NUOVA_PERSONA,
                        CaricamentiInErroreService.AVVISO_NUOVA_AZIENDA)
                .noneMatch(a -> a.startsWith("Il periodo indicato"));
    }

    @Test
    void conPersonaEAziendaScelteIlCedolinoVaAlLoroRapportoConIDatiInArchivio() {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        var caricato = caricaInErrore("refusi.pdf", pdfConTesto("Maggio con refusi"));
        var scelti = sceltoDa(aprile);
        // dati anagrafici scritti con refusi: contano le anagrafiche scelte
        var conRefusi = conDatore(conDipendente(scelti, new CedolinoManuale.Dipendente("RSSMRA80A01H501X", "Maria",
                        "Rosi", null, "000123")),
                new CedolinoManuale.DatoreLavoro("98765432109", "98765432109", "999", null, "Alfa Sofware",
                        "Via Verdi 2, 00100 Roma (RM)", "ALFA01"));
        var prima = conteggi();

        var verifica = inErrore.verifica(caricato.id(), conRefusi);
        var esito = inErrore.inserisci(caricato.id(), conRefusi);

        assertThat(verifica.rapportoId()).isEqualTo(aprile.rapportoId());
        assertThat(esito.stato()).isEqualTo(StatoCaricamento.ACQUISITO);
        assertThat(esito.personaId()).isEqualTo(aprile.personaId());
        assertThat(esito.rapportoId()).isEqualTo(aprile.rapportoId());
        for (var tabella : List.of("persona", "azienda", "rapporto_lavoro")) {
            assertThat(conteggi().get(tabella)).as(tabella).isEqualTo(prima.get(tabella));
        }
        var persona = dsl.fetchSingle("SELECT codice_fiscale, nome, cognome FROM persona");
        assertThat(persona.intoList()).containsExactly(CedoliniManualiDiProva.CF_MARIO, "Mario", "Rossi");
        var azienda = dsl.fetchSingle("SELECT codice_fiscale, partita_iva, posizione_inps, ragione_sociale, indirizzo "
                + "FROM azienda");
        assertThat(azienda.intoList()).as("chiavi immutate; indirizzo aggiornato dal cedolino piu' recente")
                .containsExactly(PIVA_ALFA, PIVA_ALFA, "1122334455", "Alfa Software S.r.l.",
                        "Via Verdi 2, 00100 Roma (RM)");
        var json = dsl.fetchSingle("SELECT estratto_json FROM cedolino WHERE id = ?", esito.cedolinoId().valore())
                .get(0, String.class);
        assertThat(json).contains(CedoliniManualiDiProva.CF_MARIO, "\"Rossi\"", "Alfa Software S.r.l.", PIVA_ALFA)
                .doesNotContain("RSSMRA80A01H501X", "Rosi", "Maria", "Alfa Sofware", "98765432109");
    }

    @Test
    void unaDataDiNascitaNonPlausibileInArchivioNonBloccaLaPersonaScelta() {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        var caricato = caricaInErrore("nascita.pdf", pdfConTesto("Persona con la data di nascita letta male"));
        var scelti = sceltoDa(aprile);
        // date lette male da un PDF e salvate dal censimento senza controlli: nel futuro, poi con un anno troppo vecchio
        for (var inArchivio : List.of(LocalDate.of(2090, 1, 1), LocalDate.of(1850, 3, 1))) {
            dsl.execute("UPDATE persona SET data_nascita = ?", inArchivio);
            // la data inviata non conta: quella in sola lettura del modulo, nessuna o una corretta
            for (var inviata : Arrays.asList(inArchivio, null, LocalDate.of(1980, 1, 1))) {
                var dati = conDipendente(scelti, new CedolinoManuale.Dipendente(null, null, null, inviata, null));
                assertThat(inErrore.verifica(caricato.id(), dati).esito()).as("%s / %s", inArchivio, inviata)
                        .isEqualTo(Esito.ACQUISIBILE);
            }
        }

        var esito = inErrore.inserisci(caricato.id(), conDipendente(scelti,
                new CedolinoManuale.Dipendente(null, null, null, LocalDate.of(1980, 1, 1), null)));

        assertThat(esito.stato()).isEqualTo(StatoCaricamento.ACQUISITO);
        assertThat(esito.personaId()).isEqualTo(aprile.personaId());
        assertThat(dsl.fetchSingle("SELECT data_nascita FROM persona").get(0, LocalDate.class))
                .as("la persona resta com'e'").isEqualTo(LocalDate.of(1850, 3, 1));
        var json = dsl.fetchSingle("SELECT estratto_json FROM cedolino WHERE id = ?", esito.cedolinoId().valore())
                .get(0, String.class);
        assertThat(json).contains("1850-03-01").doesNotContain("1980-01-01");
    }

    @Test
    void laSceltaDellAziendaVinceSulRiconoscimento() {
        // due aziende omonime senza identificativi (righe scritte prima delle regole attuali): il censimento
        // abbinerebbe la ragione sociale alla prima registrata
        var prima = inserisciAzienda("Delta Servizi S.r.l.", "DELTA SERVIZI SRL");
        var seconda = inserisciAzienda("DELTA SERVIZI SRL", "DELTA SERVIZI SRL");
        var caricato = caricaInErrore("delta.pdf", pdfConTesto("Cedolino della seconda Delta"));
        var giovannaInDelta = conDatore(conDipendente(essenziale(), GIOVANNA),
                new CedolinoManuale.DatoreLavoro(null, null, null, null, "Delta Servizi S.r.l.", null, null));

        assertThatThrownBy(() -> inErrore.verifica(caricato.id(), giovannaInDelta))
                .isInstanceOfSatisfying(DatiNonValidiException.class, e -> assertThat(e.errori()).containsExactly(
                        new ErroreCampo("datoreLavoro.ragioneSociale", MessaggiCaricamento.RAGIONE_SOCIALE_IN_ARCHIVIO)));

        var esito = inErrore.inserisci(caricato.id(), conScelte(giovannaInDelta, null, seconda));

        assertThat(esito.stato()).isEqualTo(StatoCaricamento.ACQUISITO);
        assertThat(rapporto(esito.rapportoId()).aziendaId().valore()).isEqualTo(seconda).isNotEqualTo(prima);
        assertThat(dsl.fetchCount(dsl.selectFrom("azienda"))).isEqualTo(2);
    }

    @Test
    void unaPersonaGiaInArchivioVaSceltaDallElenco() {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        var caricato = caricaInErrore("persona-esistente.pdf", pdfConTesto("Persona gia' in archivio"));
        var scelti = sceltoDa(aprile);
        // "Crea nuovo" con il codice fiscale di Mario Rossi, scritto anche in minuscolo e con spazi
        var nuovaMaGiaInArchivio = conDipendente(conScelte(scelti, null, scelti.aziendaId()),
                new CedolinoManuale.Dipendente(" rssmra80a01 h501u", "Mario", "Rossi", null, null));
        var atteso = new ErroreCampo("dipendente.codiceFiscale",
                "Questo codice fiscale è già in archivio: seleziona la persona dall'elenco");

        assertNonValidoSenzaScritture(caricato, nuovaMaGiaInArchivio, atteso);
    }

    @Test
    void unAziendaGiaInArchivioVaSceltaDallElenco() {
        caricaFixture("rossi-alfa-2023-04");
        var beta = caricaFixture("rossi-beta-2025-01");
        var caricato = caricaInErrore("azienda-esistente.pdf", pdfConTesto("Azienda gia' in archivio"));
        var mario = conScelte(essenziale(), beta.personaId().valore(), null);
        Map<CedolinoManuale.DatoreLavoro, ErroreCampo> casi = new LinkedHashMap<>();
        casi.put(new CedolinoManuale.DatoreLavoro(null, PIVA_ALFA, null, null, "Alfa Nuova S.r.l.", null, null),
                new ErroreCampo("datoreLavoro.partitaIva", MessaggiCaricamento.PARTITA_IVA_IN_ARCHIVIO));
        casi.put(new CedolinoManuale.DatoreLavoro(PIVA_ALFA, null, null, null, "Alfa Nuova S.r.l.", null, null),
                new ErroreCampo("datoreLavoro.codiceFiscale", MessaggiCaricamento.CODICE_FISCALE_AZIENDA_IN_ARCHIVIO));
        // la matricola INPS di Beta Consulting, stampata attaccata alla posizione INAIL
        casi.put(new CedolinoManuale.DatoreLavoro(null, null, "7012345678 01", null, "Beta Nuova S.p.A.", null,
                        null),
                new ErroreCampo("datoreLavoro.posizioneInps", MessaggiCaricamento.POSIZIONE_INPS_IN_ARCHIVIO));
        casi.put(new CedolinoManuale.DatoreLavoro(null, null, null, null, "ALFA SOFTWARE SRL", null, null),
                new ErroreCampo("datoreLavoro.ragioneSociale", MessaggiCaricamento.RAGIONE_SOCIALE_IN_ARCHIVIO));

        casi.forEach((datore, atteso) -> assertNonValidoSenzaScritture(caricato, conDatore(mario, datore), atteso));
        // una partita IVA diversa distingue un'omonima: e' davvero un'azienda nuova
        var omonima = conDatore(mario, new CedolinoManuale.DatoreLavoro(null, PIVA_GAMMA, null, null,
                "ALFA SOFTWARE SRL", null, null));
        assertThat(inErrore.verifica(caricato.id(), omonima).nuovaAzienda()).isTrue();
    }

    @Test
    void unaSceltaNonPiuInArchivioEUnErroreSulCampoDellaScelta() {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        var caricato = caricaInErrore("scelta-assente.pdf", pdfConTesto("Scelta non piu' in archivio"));
        var scelti = sceltoDa(aprile);
        var altro = nuovoWorkspace();
        dsl.execute("INSERT INTO persona (workspace_id, codice_fiscale, nome, cognome) "
                + "VALUES (?, ?, 'Giovanna', 'Bianchi')", altro.valore(), CF_GIOVANNA);
        var personaAltrove = dsl.fetchSingle("SELECT MAX(id) FROM persona WHERE workspace_id = ?", altro.valore())
                .get(0, Long.class);
        var aziendaAltrove = inserisciAzienda(altro, "Gamma Servizi S.r.l.", "GAMMA SERVIZI SRL");
        var persona = new ErroreCampo("personaId", MessaggiCaricamento.PERSONA_NON_IN_ARCHIVIO);
        var azienda = new ErroreCampo("aziendaId", MessaggiCaricamento.AZIENDA_NON_IN_ARCHIVIO);

        assertNonValidoSenzaScritture(caricato, conScelte(scelti, Long.MAX_VALUE, scelti.aziendaId()), persona);
        assertNonValidoSenzaScritture(caricato, conScelte(scelti, 0L, scelti.aziendaId()), persona);
        assertNonValidoSenzaScritture(caricato, conScelte(scelti, personaAltrove, scelti.aziendaId()), persona);
        assertNonValidoSenzaScritture(caricato, conScelte(scelti, scelti.personaId(), -3L), azienda);
        assertNonValidoSenzaScritture(caricato, conScelte(scelti, scelti.personaId(), aziendaAltrove), azienda);
        // le scelte non valide vengono segnalate da sole, prima degli altri errori
        assertThatThrownBy(() -> inErrore.verifica(caricato.id(), conTotali(conScelte(scelti, Long.MAX_VALUE,
                Long.MAX_VALUE), new CedolinoManuale.Totali(null, null, null, null, null))))
                .isInstanceOfSatisfying(DatiNonValidiException.class, e -> assertThat(e.errori())
                        .containsExactly(persona, azienda));
    }

    @Test
    void ilControlloNellaTransazioneBloccaLeAnagraficheCambiateNelFrattempo() {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        var caricato = caricaInErrore("concorrente.pdf", pdfConTesto("Cedolino con un caricamento concorrente"));
        // dati preparati quando Mario Rossi e Alfa Software non erano ancora in archivio ("Crea nuovo")
        var preparato = interpretato(essenziale());
        var prima = conteggi();

        assertThatThrownBy(() -> acquisizione.acquisisciManuale(WS, caricato.id(), preparato, false,
                AnagraficheScelte.Scelte.NESSUNA))
                .isInstanceOfSatisfying(DatiNonValidiException.class, e -> assertThat(e.errori()).containsExactly(
                        new ErroreCampo("dipendente.codiceFiscale", MessaggiCaricamento.CODICE_FISCALE_PERSONA_IN_ARCHIVIO),
                        new ErroreCampo("datoreLavoro.partitaIva", MessaggiCaricamento.PARTITA_IVA_IN_ARCHIVIO)));
        // azienda scelta eliminata nel frattempo
        var scelte = new AnagraficheScelte.Scelte(aprile.personaId(), new AziendaId(Long.MAX_VALUE));
        assertThatThrownBy(() -> acquisizione.acquisisciManuale(WS, caricato.id(), interpretato(sceltoDa(aprile)),
                true, scelte))
                .isInstanceOfSatisfying(DatiNonValidiException.class, e -> assertThat(e.errori()).containsExactly(
                        new ErroreCampo("aziendaId", MessaggiCaricamento.AZIENDA_NON_IN_ARCHIVIO)));
        assertThat(conteggi()).isEqualTo(prima);
        assertThat(rileggi(caricato).stato()).isEqualTo(StatoCaricamento.ERRORE);
    }

    @Test
    void dueInserimentiInParalleloAcquisisconoUnSoloCedolino() throws Exception {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        var caricato = caricaInErrore("parallelo.pdf", pdfConTesto("Inserimento in parallelo"));
        var partenza = new CountDownLatch(1);
        Callable<Object> inserimento = () -> {
            partenza.await();
            try {
                return inErrore.inserisci(caricato.id(), sceltoDa(aprile));
            } catch (OperazioneNonConsentitaException e) {
                return e;
            }
        };
        var esiti = new ArrayList<Object>();

        try (var esecutore = Executors.newFixedThreadPool(2)) {
            var primo = esecutore.submit(inserimento);
            var secondo = esecutore.submit(inserimento);
            partenza.countDown();
            esiti.add(primo.get(60, TimeUnit.SECONDS));
            esiti.add(secondo.get(60, TimeUnit.SECONDS));
        }

        assertThat(esiti).filteredOn(Caricamento.class::isInstance).singleElement()
                .satisfies(c -> assertThat(((Caricamento) c).stato()).isEqualTo(StatoCaricamento.ACQUISITO));
        assertThat(esiti).filteredOn(OperazioneNonConsentitaException.class::isInstance).singleElement()
                .satisfies(e -> assertThat(((Exception) e).getMessage()).isEqualTo(MessaggiCaricamento.NON_IN_ERRORE));
        assertThat(dsl.fetchCount(dsl.selectFrom("cedolino"))).isEqualTo(2);
    }

    @Test
    void lInserimentoAManoChiudeITentativiSuccessiviInErroreDelloStessoFile() {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        var contenuto = pdfConTesto("Maggio caricato due volte");
        var primo = caricaInErrore("maggio.pdf", contenuto);
        var secondo = caricaInErrore("maggio.pdf", contenuto);

        var esito = inErrore.inserisci(primo.id(), sceltoDa(aprile));

        assertThat(esito.stato()).isEqualTo(StatoCaricamento.ACQUISITO);
        assertThat(rileggi(secondo)).satisfies(c -> {
            assertThat(c.stato()).isEqualTo(StatoCaricamento.DUPLICATO);
            assertThat(c.motivo()).isNull();
            assertThat(c.messaggio()).isEqualTo(MessaggiCaricamento.FILE_GIA_CARICATO);
            assertThat(c.cedolinoEsistenteId()).isEqualTo(esito.cedolinoId());
            assertThat(c.pdfDisponibile()).isFalse();
        });
        assertThat(inErrore.elenco()).isEmpty();

        archivio.elimina(esito.cedolinoId());

        assertThat(inErrore.elenco()).as("eliminato il cedolino, il file non torna in errore").isEmpty();
        assertThat(pdfConservati()).isZero();
    }

    @Test
    void valoriTroppoGrandiSonoErroriDeiDatiEIlFileRestaInErrore() {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        var caricato = caricaInErrore("enorme.pdf", pdfConTesto("Importi enormi"));
        var nettoEnorme = conTotali(sceltoDa(aprile), new CedolinoManuale.Totali(null, n("2100.00"), n("450.00"),
                null, n("999999999")));
        // ogni valore nei limiti, ma il valore stimato dei ratei (saldo x paga oraria) non sta nelle colonne
        var prodottoEnorme = conRatei(conRetribuzione(sceltoDa(aprile), new ElementiRetributivi(null, null, null, null,
                        null, null, null, null, n("99999999"), null, null, null)),
                List.of(new Rateo(TipoRateo.FERIE, UnitaMisura.ORE, null, null, null, n("99999999"))));
        var prima = conteggi();

        assertThatThrownBy(() -> inErrore.inserisci(caricato.id(), nettoEnorme))
                .isInstanceOfSatisfying(DatiNonValidiException.class, e -> assertThat(e.errori()).containsExactly(
                        new ErroreCampo("totali.netto", ValidazioneCedolinoManuale.MESSAGGIO_FUORI_LIMITI)));
        assertThatThrownBy(() -> inErrore.verifica(caricato.id(), prodottoEnorme))
                .isInstanceOfSatisfying(DatiNonValidiException.class, e -> assertThat(e.errori()).containsExactly(
                        new ErroreCampo(null, MessaggiCaricamento.VALORI_TROPPO_GRANDI)));
        assertThatThrownBy(() -> inErrore.inserisci(caricato.id(), prodottoEnorme))
                .isInstanceOfSatisfying(DatiNonValidiException.class, e -> assertThat(e.errori()).containsExactly(
                        new ErroreCampo(null, MessaggiCaricamento.VALORI_TROPPO_GRANDI)));
        assertThat(conteggi()).isEqualTo(prima);
        assertThat(rileggi(caricato).stato()).isEqualTo(StatoCaricamento.ERRORE);
        assertThat(inErrore.elenco()).extracting(CaricamentoInErrore::id).containsExactly(caricato.id());
    }

    @Test
    void ilFileProtettoSenzaPasswordSiInserisceAManoConIByteCifrati() {
        var cifrato = pdfFixture("rossi-beta-2025-03");
        var protetto = carica(new FileCaricato("rossi-beta-2025-03.pdf", cifrato));
        assertThat(protetto.motivo()).isEqualTo(MotivoErrore.PDF_PROTETTO);
        assertThat(protetto.pdfDisponibile()).isTrue();
        var marzo = conDatore(conPeriodo(conTotali(essenziale(), new CedolinoManuale.Totali(n("2760.00"),
                                n("2760.00"), n("717.12"), null, n("2042.88"))),
                        new CedolinoManuale.Periodo(2025, 3, TipoMensilita.ORDINARIA, null, null)),
                new CedolinoManuale.DatoreLavoro(null, null, null, null, "Beta Consulting S.p.A.", null, null));

        var esito = inErrore.inserisci(protetto.id(), marzo);

        assertThat(esito.stato()).isEqualTo(StatoCaricamento.ACQUISITO);
        assertThat(archivio.pdf(esito.cedolinoId())).hasValueSatisfying(
                pdf -> assertThat(pdf.contenuto()).isEqualTo(cifrato));
        assertThat(inErrore.elenco()).isEmpty();
    }

    // ------------------------------------------------------------------------------------------ rimozione

    @Test
    void laRimozioneEliminaGliErroriDelFilePdfEAssociazione() {
        var alfa = caricaFixture("rossi-alfa-2023-04").rapportoId();
        var contenuto = pdfConTesto("File da rimuovere");
        caricaInErrore("da-rimuovere.pdf", contenuto);
        var ultimo = caricaInErrore("da-rimuovere.pdf", contenuto);
        var altro = caricaInErrore("da-tenere.pdf", pdfConTesto("File da tenere"));
        inErrore.associa(ultimo.id(), alfa, 2023, 5);
        eventi.azzera();

        inErrore.rimuovi(ultimo.id());

        assertThat(dsl.fetchCount(dsl.selectFrom("caricamento").where("hash_contenuto = ?", hash(contenuto))))
                .isZero();
        assertThat(caricamentiRepository.pdf(WS, hash(contenuto))).isEmpty();
        assertThat(attribuzioni()).isZero();
        assertThat(eventi.modificatiInErrore).containsExactly(new CaricamentiInErroreModificati(Set.of(alfa)));
        assertThat(inErrore.elenco()).extracting(CaricamentoInErrore::id).containsExactly(altro.id());
    }

    @Test
    void laRimozioneRifiutaIFileNonInErrore() {
        var acquisito = caricaFixture("rossi-alfa-2023-04");
        var caricato = caricaInErrore("senza-associazione.pdf", pdfConTesto("Senza associazione"));
        eventi.azzera();

        inErrore.rimuovi(caricato.id());

        assertThat(eventi.modificatiInErrore).as("nessuna associazione: nessun evento").isEmpty();
        assertThatThrownBy(() -> inErrore.rimuovi(caricato.id()))
                .isInstanceOf(RisorsaNonTrovataException.class);
        assertThatThrownBy(() -> inErrore.rimuovi(acquisito.id()))
                .isInstanceOf(OperazioneNonConsentitaException.class)
                .hasMessage(MessaggiCaricamento.NON_IN_ERRORE);
        assertThatThrownBy(() -> inErrore.rimuovi(new CaricamentoId(Long.MAX_VALUE)))
                .isInstanceOf(RisorsaNonTrovataException.class);
        assertThat(rileggi(acquisito).stato()).isEqualTo(StatoCaricamento.ACQUISITO);
    }

    // ------------------------------------------------------------------------------------------ nuova lettura

    @Test
    void laNuovaLetturaConLaPasswordArchiviaIlFileProtetto() {
        var protetto = carica(new FileCaricato("rossi-beta-2025-03.pdf", pdfFixture("rossi-beta-2025-03")));
        assertThat(protetto.motivo()).isEqualTo(MotivoErrore.PDF_PROTETTO);

        var registrati = inErrore.rileggi(protetto.id(), "demo-beta-2025");

        assertThat(registrati).singleElement().satisfies(c -> {
            assertThat(c.id()).isNotEqualTo(protetto.id());
            assertThat(c.lotto()).isNotEqualTo(protetto.lotto());
            assertThat(c.nomeFileOriginale()).isEqualTo("rossi-beta-2025-03.pdf");
        });
        var esito = attendi(registrati.getFirst().lotto());
        assertThat(esito.stato()).isEqualTo(StatoCaricamento.ACQUISITO);
        assertThat(inErrore.elenco()).isEmpty();
        assertThat(pdfConservati()).isZero();
        assertThat(rileggi(protetto).stato()).as("il tentativo precedente resta in ERRORE")
                .isEqualTo(StatoCaricamento.ERRORE);
        assertThatThrownBy(() -> inErrore.rileggi(protetto.id(), null))
                .isInstanceOf(OperazioneNonConsentitaException.class)
                .hasMessage(MessaggiCaricamento.NON_IN_ERRORE);
    }

    @Test
    void duranteLaNuovaLetturaLeAzioniSulFileSonoRifiutate() {
        var aprile = caricaFixture("rossi-alfa-2023-04");
        var alfa = aprile.rapportoId();
        var caricato = caricaInErrore("lento.pdf", pdfConTesto("Lettura lenta"));
        lettoreCedolini.rallenta("lento.pdf", Duration.ofSeconds(3));

        var registrato = inErrore.rileggi(caricato.id(), null).getFirst();

        assertThat(inErrore.elenco()).singleElement().satisfies(riga -> {
            assertThat(riga.id()).isEqualTo(caricato.id());
            assertThat(riga.inElaborazione()).isTrue();
        });
        List<Runnable> azioni = List.of(
                () -> inErrore.rimuovi(caricato.id()),
                () -> inErrore.rileggi(caricato.id(), null),
                () -> inErrore.associa(caricato.id(), alfa, 2023, 5),
                () -> inErrore.rimuoviAssociazione(caricato.id()),
                () -> inErrore.verifica(caricato.id(), sceltoDa(aprile)),
                () -> inErrore.inserisci(caricato.id(), sceltoDa(aprile)));
        assertThat(azioni).allSatisfy(azione -> assertThatThrownBy(azione::run)
                .isInstanceOf(OperazioneNonConsentitaException.class)
                .hasMessage(MessaggiCaricamento.IN_ELABORAZIONE));

        var esito = attendi(registrato.lotto());
        assertThat(esito.stato()).isEqualTo(StatoCaricamento.ERRORE);
        assertThat(inErrore.elenco()).singleElement().satisfies(riga -> {
            assertThat(riga.id()).isEqualTo(registrato.id());
            assertThat(riga.inElaborazione()).isFalse();
            assertThat(riga.pdfDisponibile()).isTrue();
        });
    }

    @Test
    void unaNuovaLetturaInterrottaSiCompletaAMano() {
        var contenuto = pdfConTesto("Nuova lettura interrotta");
        caricaInErrore("interrotto.pdf", contenuto);
        var tentativo = caricamentiRepository.registra(WS, UUID.randomUUID(),
                List.of(new FileInCoda("interrotto.pdf", contenuto.length, hash(contenuto)))).getFirst();
        caricamentiRepository.aggiorna(WS, tentativo.id(), StatoCaricamento.IN_LETTURA, null, null, null);
        ripristino.ripristina();

        var esito = inErrore.inserisci(tentativo.id(), essenziale());

        assertThat(esito.stato()).isEqualTo(StatoCaricamento.ACQUISITO);
        assertThat(dsl.fetchSingle("SELECT contenuto FROM cedolino_pdf WHERE cedolino_id = ?",
                esito.cedolinoId().valore()).get(0, byte[].class)).isEqualTo(contenuto);
    }

    @Test
    void unaPrimaLetturaInterrottaNonHaIlPdfPerCompletarla() {
        var contenuto = pdfConTesto("Prima lettura interrotta");
        var tentativo = caricamentiRepository.registra(WS, UUID.randomUUID(),
                List.of(new FileInCoda("prima-lettura.pdf", contenuto.length, hash(contenuto)))).getFirst();
        caricamentiRepository.aggiorna(WS, tentativo.id(), StatoCaricamento.IN_LETTURA, null, null, null);
        ripristino.ripristina();

        assertThat(inErrore.elenco()).singleElement().satisfies(riga -> {
            assertThat(riga.motivo()).isEqualTo(MotivoErrore.INTERROTTO);
            assertThat(riga.messaggio()).as("senza PDF il file va ricaricato")
                    .isEqualTo(MessaggiCaricamento.INTERROTTO_DAL_RIAVVIO);
            assertThat(riga.pdfDisponibile()).isFalse();
        });
        assertThatThrownBy(() -> inErrore.inserisci(tentativo.id(), essenziale()))
                .isInstanceOf(OperazioneNonConsentitaException.class)
                .hasMessage(MessaggiCaricamento.PDF_NON_CONSERVATO);
        assertThatThrownBy(() -> inErrore.rileggi(tentativo.id(), null))
                .isInstanceOf(OperazioneNonConsentitaException.class)
                .hasMessage(MessaggiCaricamento.PDF_NON_CONSERVATO);
        assertThat(inErrore.pdf(tentativo.id())).isEmpty();
    }

    // ------------------------------------------------------------------------------------------ associazione

    @Test
    void lAssociazioneAUnaMensilitaPubblicaLeModifiche() {
        var alfa = caricaFixture("rossi-alfa-2023-04").rapportoId();
        var beta = caricaFixture("rossi-beta-2025-01").rapportoId();
        var caricato = caricaInErrore("associato.pdf", pdfConTesto("File da associare"));
        eventi.azzera();

        var associato = inErrore.associa(caricato.id(), alfa, 2023, 5);

        assertThat(associato.id()).isEqualTo(caricato.id());
        assertThat(associato.attribuzione())
                .isEqualTo(new AttribuzioneCaricamento(alfa, new Competenza.Mensilita(2023, 5), false));
        assertThat(inErrore.associatiARapporto(alfa)).containsExactly(associato);
        assertThat(inErrore.associatiARapporto(beta)).isEmpty();

        var spostato = inErrore.associa(caricato.id(), alfa, 2023, 6);
        var altroRapporto = inErrore.associa(caricato.id(), beta, 2025, 2);
        var senzaAssociazione = inErrore.rimuoviAssociazione(caricato.id());
        inErrore.rimuoviAssociazione(caricato.id());

        assertThat(spostato.attribuzione().competenza()).isEqualTo(new Competenza.Mensilita(2023, 6));
        assertThat(altroRapporto.attribuzione().rapportoId()).isEqualTo(beta);
        assertThat(senzaAssociazione.attribuzione()).isNull();
        assertThat(attribuzioni()).isZero();
        assertThat(eventi.modificatiInErrore).containsExactly(
                new CaricamentiInErroreModificati(Set.of(alfa)),
                new CaricamentiInErroreModificati(Set.of(alfa)),
                new CaricamentiInErroreModificati(Set.of(alfa, beta)),
                new CaricamentiInErroreModificati(Set.of(beta)));
    }

    @Test
    void unaMensilitaPresenteOGiaAssociataNonSiAssocia() {
        var alfa = caricaFixture("rossi-alfa-2023-04").rapportoId();
        var primo = caricaInErrore("primo.pdf", pdfConTesto("Primo file"));
        var secondo = caricaInErrore("secondo.pdf", pdfConTesto("Secondo file"));
        inErrore.associa(primo.id(), alfa, 2023, 5);
        eventi.azzera();

        assertThatThrownBy(() -> inErrore.associa(secondo.id(), alfa, 2023, 4))
                .isInstanceOf(OperazioneNonConsentitaException.class)
                .hasMessage(MessaggiCaricamento.competenzaGiaPresente(new Competenza.Mensilita(2023, 4)));
        assertThatThrownBy(() -> inErrore.associa(secondo.id(), alfa, 2023, 5))
                .isInstanceOf(OperazioneNonConsentitaException.class)
                .hasMessage(MessaggiCaricamento.competenzaGiaAssociata(new Competenza.Mensilita(2023, 5)));
        assertThat(inErrore.associa(primo.id(), alfa, 2023, 5).attribuzione().competenza())
                .as("riassociare lo stesso file alla stessa mensilita' e' ammesso")
                .isEqualTo(new Competenza.Mensilita(2023, 5));
        assertThatThrownBy(() -> inErrore.associa(secondo.id(), new RapportoLavoroId(Long.MAX_VALUE), 2023, 5))
                .isInstanceOf(RisorsaNonTrovataException.class);
        assertThatThrownBy(() -> inErrore.associa(secondo.id(), alfa, 1899, 15))
                .isInstanceOfSatisfying(DatiNonValidiException.class, e -> assertThat(e.errori())
                        .extracting(ErroreCampo::campo).containsExactly("anno", "mese"));
        assertThatThrownBy(() -> inErrore.associa(secondo.id(), null, null, 13))
                .isInstanceOfSatisfying(DatiNonValidiException.class, e -> assertThat(e.errori())
                        .extracting(ErroreCampo::campo).containsExactly("rapportoId", "anno"));
        assertThat(attribuzioni()).isEqualTo(1);
        assertThat(eventi.modificatiInErrore).containsExactly(new CaricamentiInErroreModificati(Set.of(alfa)));
    }

    @Test
    void lEliminazioneDelRapportoEliminaLeAssociazioni() {
        var gennaio = caricaFixture("rossi-beta-2025-01");
        var caricato = caricaInErrore("orfano.pdf", pdfConTesto("File di un rapporto eliminato"));
        inErrore.associa(caricato.id(), gennaio.rapportoId(), 2025, 2);

        archivio.elimina(gennaio.cedolinoId());

        assertThat(attribuzioni()).isZero();
        assertThat(inErrore.elenco()).singleElement().satisfies(riga -> {
            assertThat(riga.id()).isEqualTo(caricato.id());
            assertThat(riga.attribuzione()).isNull();
        });
    }

    // ------------------------------------------------------------------------------------------ workspace

    @Test
    void unCaricamentoDiUnAltroWorkspaceNonEVisibile() {
        var altro = nuovoWorkspace();
        var contenuto = pdfConTesto("File di un altro workspace");
        var registrato = caricamentiRepository.registra(altro, UUID.randomUUID(),
                List.of(new FileInCoda("altro.pdf", contenuto.length, hash(contenuto)))).getFirst();
        caricamentiRepository.registraErrore(altro, registrato.id(), MotivoErrore.PDF_SENZA_TESTO,
                "Il PDF non contiene testo selezionabile");
        caricamentiRepository.salvaPdfSeAssente(altro, hash(contenuto), contenuto);
        var id = registrato.id();

        assertThat(inErrore.elenco()).isEmpty();
        assertThat(inErrore.pdf(id)).isEmpty();
        List<Runnable> azioni = List.of(
                () -> inErrore.rimuovi(id),
                () -> inErrore.rileggi(id, null),
                () -> inErrore.associa(id, new RapportoLavoroId(1), 2023, 5),
                () -> inErrore.rimuoviAssociazione(id),
                () -> inErrore.verifica(id, essenziale()),
                () -> inErrore.inserisci(id, essenziale()));
        assertThat(azioni).allSatisfy(azione -> assertThatThrownBy(azione::run)
                .isInstanceOf(RisorsaNonTrovataException.class));
        assertThat(caricamentiRepository.pdf(altro, hash(contenuto))).isPresent();
    }

    // ------------------------------------------------------------------------------------------ supporto

    /** Carica un PDF che il lettore non riesce a leggere (senza testo): file in errore con il PDF conservato. */
    private Caricamento caricaInErrore(String nomeFile, byte[] contenuto) {
        lettoreCedolini.registraErrore(nomeFile, new EstrazioneCedolinoException(Motivo.PDF_SENZA_TESTO,
                "Il PDF non contiene testo selezionabile"));
        var esito = carica(new FileCaricato(nomeFile, contenuto));
        assertThat(esito.motivo()).isEqualTo(MotivoErrore.PDF_SENZA_TESTO);
        assertThat(esito.pdfDisponibile()).isTrue();
        return esito;
    }

    /**
     * Il cedolino essenziale di maggio 2023 con persona e azienda scelte dall'elenco: quelle del rapporto del
     * cedolino acquisito (Mario Rossi in Alfa Software per le fixture "rossi-alfa").
     */
    private CedolinoManuale sceltoDa(Caricamento acquisito) {
        var rapporto = rapporto(acquisito.rapportoId());
        return conScelte(essenziale(), rapporto.personaId().valore(), rapporto.aziendaId().valore());
    }

    private RapportoLavoro rapporto(RapportoLavoroId id) {
        return anagrafica.trovaRapporto(id).orElseThrow();
    }

    /** Azienda inserita direttamente nel database, senza identificativi (come scritta prima delle regole attuali). */
    private long inserisciAzienda(String ragioneSociale, String normalizzata) {
        return inserisciAzienda(WS, ragioneSociale, normalizzata);
    }

    private long inserisciAzienda(WorkspaceId workspace, String ragioneSociale, String normalizzata) {
        dsl.execute("INSERT INTO azienda (workspace_id, ragione_sociale, ragione_sociale_normalizzata) "
                + "VALUES (?, ?, ?)", workspace.valore(), ragioneSociale, normalizzata);
        return dsl.fetchSingle("SELECT MAX(id) FROM azienda WHERE workspace_id = ?", workspace.valore())
                .get(0, Long.class);
    }

    /** Il cedolino inserito a mano come lo prepara il servizio: validato, normalizzato e interpretato. */
    private RisultatoEstrazione interpretato(CedolinoManuale dati) {
        var normalizzato = ValidazioneCedolinoManuale.normalizza(dati);
        return InterpreteRispostaEstrazione.standard(Clock.systemUTC()).interpreta(
                JsonCedolinoManuale.scrivi(normalizzato), ModelliEstrazione.MODELLO_INSERIMENTO_MANUALE);
    }

    /** Verifica e inserimento rifiutati con il solo errore atteso, senza scritture: il file resta in errore. */
    private void assertNonValidoSenzaScritture(Caricamento caricato, CedolinoManuale dati, ErroreCampo atteso) {
        var prima = conteggi();
        assertThatThrownBy(() -> inErrore.verifica(caricato.id(), dati))
                .isInstanceOfSatisfying(DatiNonValidiException.class,
                        e -> assertThat(e.errori()).containsExactly(atteso));
        assertThatThrownBy(() -> inErrore.inserisci(caricato.id(), dati))
                .isInstanceOfSatisfying(DatiNonValidiException.class,
                        e -> assertThat(e.errori()).containsExactly(atteso));
        assertThat(conteggi()).isEqualTo(prima);
        assertThat(rileggi(caricato).stato()).isEqualTo(StatoCaricamento.ERRORE);
    }

    private Caricamento caricaFixture(String nome) {
        var esito = carica(new FileCaricato(nome + ".pdf", pdfFixture(nome)));
        assertThat(esito.stato()).isEqualTo(StatoCaricamento.ACQUISITO);
        return esito;
    }

    /** Carica un file in un lotto e ne attende l'esito. */
    private Caricamento carica(FileCaricato file) {
        return attendi(caricamento.avvia(List.of(file)).getFirst().lotto());
    }

    /** Attende l'esito del lotto di un solo file. */
    private Caricamento attendi(UUID lotto) {
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

    /** Righe delle tabelle toccate da acquisizione e azioni sui file in errore. */
    private Map<String, Integer> conteggi() {
        var conteggi = new LinkedHashMap<String, Integer>();
        for (var tabella : List.of("persona", "azienda", "rapporto_lavoro", "cedolino", "cedolino_pdf",
                "riepilogo_rapporto", "segnalazione", "caricamento", "caricamento_pdf", "caricamento_attribuzione")) {
            conteggi.put(tabella, dsl.fetchCount(dsl.selectFrom(tabella)));
        }
        return conteggi;
    }

    /** Descrizione della segnalazione CEDOLINO_IN_ERRORE di una mensilita' del rapporto. */
    private String descrizioneCedolinoInErrore(RapportoLavoroId rapporto, int anno, int mese) {
        return dsl.fetchSingle("SELECT descrizione FROM segnalazione WHERE rapporto_id = ? AND competenza_anno = ? "
                + "AND competenza_mese = ? AND tipo = 'CEDOLINO_IN_ERRORE'", rapporto.valore(), anno, mese)
                .get(0, String.class);
    }

    /** Tipi delle segnalazioni salvate per una mensilita' del rapporto. */
    private List<String> segnalazioniDi(RapportoLavoroId rapporto, int anno, int mese) {
        return dsl.fetch("SELECT tipo FROM segnalazione WHERE rapporto_id = ? AND competenza_anno = ? "
                + "AND competenza_mese = ? ORDER BY tipo", rapporto.valore(), anno, mese).getValues(0, String.class);
    }

    private WorkspaceId nuovoWorkspace() {
        var codice = "prova-" + UUID.randomUUID();
        dsl.execute("INSERT INTO workspace (codice, descrizione) VALUES (?, 'Secondo workspace di prova')", codice);
        return new WorkspaceId(dsl.fetchSingle("SELECT id FROM workspace WHERE codice = ?", codice).get(0, Long.class));
    }

    private static String hash(byte[] contenuto) {
        return CaricamentoCedoliniService.sha256(contenuto);
    }
}
