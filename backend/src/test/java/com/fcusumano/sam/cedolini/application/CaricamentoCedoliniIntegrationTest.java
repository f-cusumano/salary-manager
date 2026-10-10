package com.fcusumano.sam.cedolini.application;

import static com.fcusumano.sam.cedolini.application.SupportoCaricamenti.pdfConTesto;
import static com.fcusumano.sam.cedolini.application.SupportoCaricamenti.pdfFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.anagrafica.PersonaSintesi;
import com.fcusumano.sam.anagrafica.RapportoLavoro;
import com.fcusumano.sam.anagrafica.RapportoSintesi;
import com.fcusumano.sam.cedolini.ArchivioCedolini;
import com.fcusumano.sam.cedolini.Caricamento;
import com.fcusumano.sam.cedolini.CaricamentoCedolini;
import com.fcusumano.sam.cedolini.CategoriaVoce;
import com.fcusumano.sam.cedolini.CedolinoAcquisito;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.cedolini.CedolinoSintesi;
import com.fcusumano.sam.cedolini.FileCaricato;
import com.fcusumano.sam.cedolini.StatoCaricamento;
import com.fcusumano.sam.cedolini.application.CaricamentiRepository.FileInCoda;
import com.fcusumano.sam.cedolini.application.SupportoCaricamenti.LettoreProgrammabile;
import com.fcusumano.sam.cedolini.application.SupportoCaricamenti.EventiRegistrati;
import com.fcusumano.sam.cedolini.application.estrazione.PdfNonLeggibileException;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.RisorsaNonTrovataException;
import com.fcusumano.sam.shared.WorkspaceId;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Caricamento end-to-end con l'lettoreCedolini a fixture sul database H2 (schema Liquibase): elaborazione asincrona,
 * censimento, competenze, duplicati e aggiuntivi, fine rapporto con progressivi, PDF protetti, eliminazione,
 * concorrenza e ripristino dopo il riavvio. Dati inventati.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(SupportoCaricamenti.Configurazione.class)
class CaricamentoCedoliniIntegrationTest {

    private static final String CF_MARIO = "RSSMRA80A01H501U";
    private static final String PASSWORD_BETA_09 = "rssmra80a01h501u";
    private static final Duration ATTESA_MASSIMA = Duration.ofSeconds(60);

    @Autowired
    CaricamentoCedolini caricamento;
    @Autowired
    ArchivioCedolini archivio;
    @Autowired
    ConsultazioneAnagrafica anagrafica;
    @Autowired
    LettoreProgrammabile lettoreCedolini;
    @Autowired
    EventiRegistrati eventi;
    @Autowired
    CaricamentiRepository caricamentiRepository;
    @Autowired
    RipristinoCaricamentiInterrotti ripristino;
    @Autowired
    ElaborazioneCaricamenti elaborazione;
    @Autowired
    DSLContext dsl;

    @BeforeEach
    void databaseVuoto() {
        dsl.execute("DELETE FROM caricamento");
        dsl.execute("DELETE FROM cedolino");
        dsl.execute("DELETE FROM rapporto_lavoro");
        dsl.execute("DELETE FROM persona");
        dsl.execute("DELETE FROM azienda");
        lettoreCedolini.azzera();
        eventi.azzera();
    }

    // ------------------------------------------------------------------------------------------ flusso completo

    @Test
    void flussoCompletoCensisceArchiviaENormalizza() {
        var originale = pdfFixture("rossi-alfa-2023-01");
        var esiti = caricaFixture("rossi-alfa-2023-02", "rossi-alfa-2023-01", "rossi-alfa-2023-03");

        assertThat(esiti).extracting(Caricamento::stato).containsOnly(StatoCaricamento.ACQUISITO);
        assertThat(esiti).allSatisfy(c -> {
            assertThat(c.cedolinoId()).isNotNull();
            assertThat(c.messaggio()).isNull();
            assertThat(c.cedolinoEsistenteId()).isNull();
        });

        var persone = anagrafica.elencoPersone();
        assertThat(persone).singleElement().satisfies(p -> {
            assertThat(p.codiceFiscale()).isEqualTo(CF_MARIO);
            assertThat(p.etichetta()).isEqualTo("Mario Rossi");
        });
        var rapporto = unicoRapporto(persone.getFirst());
        assertThat(rapporto.primoCedolino()).isEqualTo(YearMonth.of(2023, 1));
        assertThat(rapporto.ultimoCedolino()).isEqualTo(YearMonth.of(2023, 3));
        assertThat(rapporto.dataAssunzione()).isEqualTo(LocalDate.of(2021, 9, 1));
        assertThat(rapporto.ccnlCodice()).isEqualTo("METALMECCANICO_INDUSTRIA");

        var sintesi = archivio.sintesiDiRapporto(rapporto.id());
        var suffisso = "_" + rapporto.aziendaId().valore() + "_" + rapporto.personaId().valore() + ".pdf";
        assertThat(sintesi).extracting(CedolinoSintesi::nomeFile)
                .containsExactly("2023.03" + suffisso, "2023.02" + suffisso, "2023.01" + suffisso);
        assertThat(sintesi.getLast().ratei()).isNotEmpty();

        var normalizzati = archivio.normalizzatiDiRapporto(rapporto.id());
        assertThat(normalizzati).extracting(CedolinoNormalizzato::competenza).containsExactly(
                new Competenza.Mensilita(2023, 1), new Competenza.Mensilita(2023, 2), new Competenza.Mensilita(2023, 3));
        var gennaio = normalizzati.getFirst();
        assertThat(gennaio.personaId()).isEqualTo(rapporto.personaId());
        assertThat(gennaio.aziendaId()).isEqualTo(rapporto.aziendaId());
        assertThat(gennaio.netto()).isEqualByComparingTo("1652.00");
        assertThat(gennaio.voci()).isNotEmpty();
        assertThat(gennaio.vociDi(CategoriaVoce.COMPETENZA)).isNotEmpty();
        assertThat(gennaio.elementiRetributivi().totale()).isNotNull();
        assertThat(gennaio.ccnlTesto()).isEqualTo("CCNL Metalmeccanica Industria");

        var idGennaio = gennaio.id();
        assertThat(archivio.dettaglio(idGennaio)).get().satisfies(d -> {
            assertThat(d.nomeFile()).isEqualTo("2023.01" + suffisso);
            assertThat(d.confidenza()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
            assertThat(d.acquisitoIl()).isNotNull();
            assertThat(d.dati()).isEqualTo(gennaio);
        });
        assertThat(archivio.pdf(idGennaio)).get().satisfies(pdf -> {
            assertThat(pdf.nomeFile()).isEqualTo("2023.01" + suffisso);
            assertThat(pdf.contenuto()).isEqualTo(originale);
        });

        assertThat(eventi.acquisiti).hasSize(3).extracting(CedolinoAcquisito::rapportoId).containsOnly(rapporto.id());
        assertThat(esiti).allSatisfy(c -> {
            assertThat(c.personaId()).isEqualTo(rapporto.personaId());
            assertThat(c.rapportoId()).isEqualTo(rapporto.id());
        });
        assertThat(caricamento.recenti(10)).hasSize(3);
        assertThat(caricamento.stato(List.of(esiti.getFirst().id()))).singleElement()
                .extracting(Caricamento::stato).isEqualTo(StatoCaricamento.ACQUISITO);
    }

    // ------------------------------------------------------------------------------------------ duplicati e aggiuntivi

    @Test
    void gliAvvisiDellEstrazioneSonoSalvatiERestituitiNelDettaglio() {
        lettoreCedolini.registraVariante("con-avvisi.pdf", "rossi-beta-2025-01", json -> {
            ((tools.jackson.databind.node.ObjectNode) json.get("totali")).put("netto", "1000.00");
            json.withArray("avvisi").add("Periodo stampato su due righe");
        });

        var acquisito = carica(null, new FileCaricato("con-avvisi.pdf", pdfConTesto("Avvisi"))).getFirst();

        assertThat(acquisito.stato()).isEqualTo(StatoCaricamento.ACQUISITO);
        assertThat(archivio.dettaglio(acquisito.cedolinoId())).get()
                .satisfies(d -> assertThat(d.avvisiEstrazione()).containsExactly(
                        "Totale competenze meno totale trattenute non corrisponde al netto",
                        "Nota estrazione: Periodo stampato su due righe"));
        var senzaAvvisi = caricaFixture("rossi-beta-2025-02").getFirst();
        assertThat(archivio.dettaglio(senzaAvvisi.cedolinoId())).get()
                .satisfies(d -> assertThat(d.avvisiEstrazione()).isEmpty());
    }

    @Test
    void stessoFileGiaCaricatoEDuplicatoSenzaNuovaEstrazione() {
        var primo = caricaFixture("rossi-alfa-2023-01").getFirst();
        var secondo = caricaFixture("rossi-alfa-2023-01").getFirst();

        assertThat(secondo.stato()).isEqualTo(StatoCaricamento.DUPLICATO);
        assertThat(secondo.cedolinoEsistenteId()).isEqualTo(primo.cedolinoId());
        assertThat(secondo.messaggio()).isEqualTo(MessaggiCaricamento.FILE_GIA_CARICATO);
        assertThat(secondo.personaId()).isNotNull().isEqualTo(primo.personaId());
        assertThat(secondo.rapportoId()).isNotNull().isEqualTo(primo.rapportoId());
        assertThat(lettoreCedolini.estrazioni("rossi-alfa-2023-01.pdf")).isEqualTo(1);
    }

    @Test
    void stessoFileDueVolteNelLottoUnoAcquisitoEUnoDuplicato() {
        var contenuto = pdfFixture("rossi-alfa-2023-02");
        var esiti = carica(null, new FileCaricato("rossi-alfa-2023-02.pdf", contenuto),
                new FileCaricato("rossi-alfa-2023-02.pdf", contenuto));

        assertThat(esiti).extracting(Caricamento::stato)
                .containsExactlyInAnyOrder(StatoCaricamento.ACQUISITO, StatoCaricamento.DUPLICATO);
        assertThat(lettoreCedolini.estrazioni("rossi-alfa-2023-02.pdf")).isEqualTo(1);
        assertThat(archivio.sintesiDiRapporto(unicoRapporto().id())).hasSize(1);
    }

    @Test
    void stessaCompetenzaConGliStessiImportiEDuplicato() {
        var primo = caricaFixture("rossi-alfa-2023-01").getFirst();
        // PDF diverso (altro hash) ma stessi dati estratti
        var esito = carica(null, new FileCaricato("rossi-alfa-2023-01.pdf", pdfConTesto("Ristampa"))).getFirst();

        assertThat(esito.stato()).isEqualTo(StatoCaricamento.DUPLICATO);
        assertThat(esito.cedolinoEsistenteId()).isEqualTo(primo.cedolinoId());
        assertThat(esito.messaggio()).contains("Gennaio 2023").contains("stessi importi");
        assertThat(archivio.sintesiDiRapporto(unicoRapporto().id())).hasSize(1);
    }

    @Test
    void cedolinoAggiuntivoDichiaratoENonGestitoConRiferimento() {
        var marzo = caricaFixture("rossi-alfa-2024-03").getFirst();
        var aggiuntivo = caricaFixture("rossi-alfa-2024-03-aggiuntivo").getFirst();

        assertThat(aggiuntivo.stato()).isEqualTo(StatoCaricamento.AGGIUNTIVO_NON_GESTITO);
        assertThat(aggiuntivo.cedolinoEsistenteId()).isEqualTo(marzo.cedolinoId());
        assertThat(aggiuntivo.messaggio()).contains("Marzo 2024").contains("non gestito");
        assertThat(archivio.sintesiDiRapporto(unicoRapporto().id())).hasSize(1);
    }

    @Test
    void stessaCompetenzaConImportiDiversiEAggiuntivoNonGestito() {
        var marzo = caricaFixture("rossi-alfa-2024-03").getFirst();
        lettoreCedolini.registraVariante("marzo-arretrati.pdf", "rossi-alfa-2024-03", json -> {
            var totali = (tools.jackson.databind.node.ObjectNode) json.get("totali");
            totali.put("totaleCompetenze", "2500.00");
            totali.put("lordo", "2500.00");
            totali.put("netto", "1800.00");
        });

        var esito = carica(null, new FileCaricato("marzo-arretrati.pdf", pdfConTesto("Arretrati marzo"))).getFirst();

        assertThat(esito.stato()).isEqualTo(StatoCaricamento.AGGIUNTIVO_NON_GESTITO);
        assertThat(esito.cedolinoEsistenteId()).isEqualTo(marzo.cedolinoId());
        assertThat(esito.messaggio()).contains("Marzo 2024").contains("importi diversi");
    }

    @Test
    void unCedolinoScartatoNonLasciaAnagraficaOrfana() {
        var esito = caricaFixture("rossi-alfa-2024-03-aggiuntivo").getFirst();

        assertThat(esito.stato()).isEqualTo(StatoCaricamento.AGGIUNTIVO_NON_GESTITO);
        assertThat(esito.cedolinoEsistenteId()).isNull();
        assertThat(anagrafica.elencoPersone()).isEmpty();
    }

    // ------------------------------------------------------------------------------------------ competenze

    @Test
    void tredicesimaSeparataEMese13ENonDicembre() {
        var esiti = caricaFixture("rossi-alfa-2023-12", "rossi-alfa-2023-13");

        assertThat(esiti).extracting(Caricamento::stato).containsOnly(StatoCaricamento.ACQUISITO);
        var rapporto = unicoRapporto();
        var sintesi = archivio.sintesiDiRapporto(rapporto.id());
        assertThat(sintesi).extracting(CedolinoSintesi::competenza)
                .containsExactly(Competenza.Mensilita.tredicesima(2023), new Competenza.Mensilita(2023, 12));
        assertThat(sintesi.getFirst().nomeFile()).startsWith("2023.13_");
        assertThat(sintesi.getFirst().periodoRetribuzione()).isEqualTo(YearMonth.of(2023, 12));
        // estremi del rapporto solo dalle mensilita' ordinarie
        assertThat(rapporto.primoCedolino()).isEqualTo(YearMonth.of(2023, 12));
        assertThat(rapporto.ultimoCedolino()).isEqualTo(YearMonth.of(2023, 12));
    }

    @Test
    void cambioAziendaNelloStessoMeseDueCedoliniDiRapportiDiversi() {
        var esiti = caricaFixture("rossi-alfa-2024-06", "rossi-beta-2024-06");

        assertThat(esiti).extracting(Caricamento::stato).containsOnly(StatoCaricamento.ACQUISITO);
        var persona = anagrafica.elencoPersone().getFirst();
        var rapporti = anagrafica.rapportiDiPersona(persona.id());
        assertThat(rapporti).hasSize(2);
        for (var sintesi : rapporti) {
            assertThat(archivio.sintesiDiRapporto(sintesi.id())).singleElement()
                    .extracting(CedolinoSintesi::competenza).isEqualTo(new Competenza.Mensilita(2024, 6));
        }
        var alfa = rapporti.stream().map(r -> anagrafica.trovaRapporto(r.id()).orElseThrow())
                .filter(r -> r.dataCessazione() != null).findFirst().orElseThrow();
        assertThat(alfa.dataCessazione()).isEqualTo(LocalDate.of(2024, 6, 15));
    }

    @Test
    void fineRapportoConProgressiviInOrdineCronologicoERinumerazione() {
        registraFineRapporto("tfr-settembre.pdf", 9, "FINE_RAPPORTO", true);
        registraFineRapporto("ordinario-agosto.pdf", 8, "ORDINARIA", true);

        var settembre = carica(null, new FileCaricato("tfr-settembre.pdf", pdfConTesto("TFR settembre"))).getFirst();
        assertThat(settembre.stato()).isEqualTo(StatoCaricamento.ACQUISITO);
        var rapporto = unicoRapporto();
        var suffisso = "_" + rapporto.aziendaId().valore() + "_" + rapporto.personaId().valore() + ".pdf";
        assertThat(nomiFile(rapporto)).containsExactly("2024.TFR01" + suffisso);

        // arriva un cedolino di fine rapporto precedente: settembre slitta a TFR02
        var luglio = caricaFixture("rossi-alfa-2024-tfr01").getFirst();
        assertThat(luglio.stato()).isEqualTo(StatoCaricamento.ACQUISITO);
        assertThat(competenze(rapporto)).containsExactly(
                new Competenza.FineRapporto(1, YearMonth.of(2024, 7)),
                new Competenza.FineRapporto(2, YearMonth.of(2024, 9)));
        assertThat(nomiFile(rapporto)).containsExactly("2024.TFR02" + suffisso, "2024.TFR01" + suffisso);
        assertThat(archivio.pdf(settembre.cedolinoId())).get()
                .extracting(p -> p.nomeFile()).isEqualTo("2024.TFR02" + suffisso);

        // una mensilita' "ordinaria" successiva alla cessazione e' un cedolino di fine rapporto
        var agosto = carica(null, new FileCaricato("ordinario-agosto.pdf", pdfConTesto("Agosto"))).getFirst();
        assertThat(agosto.stato()).isEqualTo(StatoCaricamento.ACQUISITO);
        assertThat(competenze(rapporto)).containsExactly(
                new Competenza.FineRapporto(1, YearMonth.of(2024, 7)),
                new Competenza.FineRapporto(2, YearMonth.of(2024, 8)),
                new Competenza.FineRapporto(3, YearMonth.of(2024, 9)));

        // eliminazione: i progressivi rimasti si ricompattano e i file vengono rinominati
        archivio.elimina(luglio.cedolinoId());
        assertThat(competenze(rapporto)).containsExactly(
                new Competenza.FineRapporto(1, YearMonth.of(2024, 8)),
                new Competenza.FineRapporto(2, YearMonth.of(2024, 9)));
        assertThat(nomiFile(rapporto)).containsExactly("2024.TFR02" + suffisso, "2024.TFR01" + suffisso);
        var aggiornato = anagrafica.trovaRapporto(rapporto.id()).orElseThrow();
        assertThat(aggiornato.primoCedolino()).isEqualTo(YearMonth.of(2024, 8));
        assertThat(aggiornato.ultimoCedolino()).isEqualTo(YearMonth.of(2024, 9));
        assertThat(eventi.eliminati).singleElement().satisfies(e -> {
            assertThat(e.cedolinoId()).isEqualTo(luglio.cedolinoId());
            assertThat(e.competenza()).isEqualTo(new Competenza.FineRapporto(1, YearMonth.of(2024, 7)));
        });
    }

    @Test
    void ordinariaSuccessivaAllaCessazioneVieneRiclassificataQuandoLaCessazioneDiventaNota() {
        registraFineRapporto("agosto-senza-cessazione.pdf", 8, "ORDINARIA", false);

        var agosto = carica(null, new FileCaricato("agosto-senza-cessazione.pdf", pdfConTesto("Agosto"))).getFirst();
        var rapporto = unicoRapporto();
        assertThat(competenze(rapporto)).containsExactly(new Competenza.Mensilita(2024, 8));

        // arriva il cedolino di giugno con la cessazione al 15/06/2024
        assertThat(caricaFixture("rossi-alfa-2024-06")).extracting(Caricamento::stato)
                .containsOnly(StatoCaricamento.ACQUISITO);

        assertThat(competenze(rapporto)).containsExactly(
                new Competenza.Mensilita(2024, 6), new Competenza.FineRapporto(1, YearMonth.of(2024, 8)));
        assertThat(archivio.pdf(agosto.cedolinoId())).get()
                .extracting(p -> p.nomeFile()).asString().startsWith("2024.TFR01_");
        var aggiornato = anagrafica.trovaRapporto(rapporto.id()).orElseThrow();
        assertThat(aggiornato.ultimoCedolino()).isEqualTo(YearMonth.of(2024, 6));
    }

    @Test
    void eliminandoIlCedolinoConLaCessazioneLaMensilitaRiclassificataTornaOrdinaria() {
        registraFineRapporto("agosto-senza-cessazione.pdf", 8, "ORDINARIA", false);
        carica(null, new FileCaricato("agosto-senza-cessazione.pdf", pdfConTesto("Agosto")));
        var esiti = caricaFixture("rossi-alfa-2024-05", "rossi-alfa-2024-06");
        var rapporto = unicoRapporto();
        assertThat(competenze(rapporto)).containsExactly(new Competenza.Mensilita(2024, 5),
                new Competenza.Mensilita(2024, 6), new Competenza.FineRapporto(1, YearMonth.of(2024, 8)));

        archivio.elimina(esiti.get(1).cedolinoId());

        var suffisso = "_" + rapporto.aziendaId().valore() + "_" + rapporto.personaId().valore() + ".pdf";
        assertThat(competenze(rapporto)).containsExactly(new Competenza.Mensilita(2024, 5),
                new Competenza.Mensilita(2024, 8));
        assertThat(nomiFile(rapporto)).containsExactly("2024.08" + suffisso, "2024.05" + suffisso);
        var aggiornato = anagrafica.trovaRapporto(rapporto.id()).orElseThrow();
        assertThat(aggiornato.dataCessazione()).isNull();
        assertThat(aggiornato.ultimoCedolino()).isEqualTo(YearMonth.of(2024, 8));

        // stesso stato di un caricamento da zero dei soli cedolini rimasti
        var dopoEliminazione = statoNormalizzato(rapporto);
        databaseVuoto();
        registraFineRapporto("agosto-senza-cessazione.pdf", 8, "ORDINARIA", false);
        caricaFixture("rossi-alfa-2024-05");
        carica(null, new FileCaricato("agosto-senza-cessazione.pdf", pdfConTesto("Agosto")));
        assertThat(statoNormalizzato(unicoRapporto())).isEqualTo(dopoEliminazione);
    }

    @Test
    void unaTredicesimaSuccessivaAllaCessazioneEFineRapportoETornaTredicesimaSeLaCessazioneSparisce() {
        lettoreCedolini.registraVariante("tredicesima-dicembre.pdf", "rossi-alfa-2024-tfr01", json -> {
            var periodo = (tools.jackson.databind.node.ObjectNode) json.get("periodo");
            periodo.put("mese", 12);
            periodo.put("tipo", "TREDICESIMA");
            periodo.put("dataElaborazione", "2024-12-10");
            ((tools.jackson.databind.node.ObjectNode) json.get("datiContrattuali")).put("dataCessazione", "");
            json.put("contieneTredicesima", true);
        });
        var tredicesima = carica(null, new FileCaricato("tredicesima-dicembre.pdf", pdfConTesto("Tredicesima")))
                .getFirst();
        var rapporto = unicoRapporto();
        var suffisso = "_" + rapporto.aziendaId().valore() + "_" + rapporto.personaId().valore() + ".pdf";
        assertThat(competenze(rapporto)).containsExactly(Competenza.Mensilita.tredicesima(2024));

        // arriva il cedolino di giugno con la cessazione al 15/06/2024: la tredicesima pagata dopo e' un TFRnn
        var giugno = caricaFixture("rossi-alfa-2024-06").getFirst();
        assertThat(competenze(rapporto)).containsExactly(new Competenza.Mensilita(2024, 6),
                new Competenza.FineRapporto(1, YearMonth.of(2024, 12)));
        assertThat(archivio.pdf(tredicesima.cedolinoId())).get()
                .extracting(p -> p.nomeFile()).isEqualTo("2024.TFR01" + suffisso);
        assertThat(archivio.normalizzatiDiRapporto(rapporto.id()).getLast().contieneTredicesima()).isTrue();

        archivio.elimina(giugno.cedolinoId());

        assertThat(competenze(rapporto)).containsExactly(Competenza.Mensilita.tredicesima(2024));
        assertThat(archivio.pdf(tredicesima.cedolinoId())).get()
                .extracting(p -> p.nomeFile()).isEqualTo("2024.13" + suffisso);
    }

    // ------------------------------------------------------------------------------------------ PDF protetti

    @Test
    void pdfProtettoSenzaPasswordNotaVaInErrore() {
        var esito = caricaFixture("rossi-beta-2024-09").getFirst();

        assertThat(esito.stato()).isEqualTo(StatoCaricamento.ERRORE);
        assertThat(esito.messaggio()).isEqualTo(PdfNonLeggibileException.MESSAGGIO_PASSWORD);
        assertThat(lettoreCedolini.estrazioni("rossi-beta-2024-09.pdf")).isZero();
        assertThat(anagrafica.elencoPersone()).isEmpty();
    }

    @Test
    void pdfProtettoConPasswordErrataVaInErrore() {
        var esito = carica("password-sbagliata",
                new FileCaricato("rossi-beta-2024-09.pdf", pdfFixture("rossi-beta-2024-09"))).getFirst();

        assertThat(esito.stato()).isEqualTo(StatoCaricamento.ERRORE);
        assertThat(esito.messaggio()).isEqualTo(PdfNonLeggibileException.MESSAGGIO_PASSWORD);
    }

    @Test
    void pdfProtettoConLaPasswordDelLottoSiAcquisisceEConservaLOriginale() {
        var originale = pdfFixture("rossi-beta-2024-09");
        var esito = carica(PASSWORD_BETA_09, new FileCaricato("rossi-beta-2024-09.pdf", originale)).getFirst();

        assertThat(esito.stato()).isEqualTo(StatoCaricamento.ACQUISITO);
        assertThat(archivio.pdf(esito.cedolinoId())).get()
                .satisfies(pdf -> assertThat(pdf.contenuto()).isEqualTo(originale));

        // l'hash e' sull'originale cifrato: il secondo caricamento e' un duplicato riconosciuto prima di decifrare
        var ripetuto = caricaFixture("rossi-beta-2024-09").getFirst();
        assertThat(ripetuto.stato()).isEqualTo(StatoCaricamento.DUPLICATO);
        assertThat(ripetuto.cedolinoEsistenteId()).isEqualTo(esito.cedolinoId());
    }

    @Test
    void pdfProtettoSiApreColCodiceFiscaleDiUnaPersonaGiaCensita() {
        caricaFixture("rossi-beta-2024-08");

        var esito = caricaFixture("rossi-beta-2024-09").getFirst();

        assertThat(esito.stato()).isEqualTo(StatoCaricamento.ACQUISITO);
        assertThat(archivio.sintesiDiRapporto(unicoRapporto().id())).hasSize(2);
    }

    // ------------------------------------------------------------------------------------------ workspace del lotto

    @Test
    void ilLavoroAsincronoScriveTuttoNelWorkspaceDelLottoAncheSeLaRichiestaNeHaUnAltro() {
        var secondo = nuovoWorkspace();

        var esito = elaboraNelWorkspace(secondo, "rossi-beta-2025-01");

        assertThat(esito.stato()).isEqualTo(StatoCaricamento.ACQUISITO);
        for (var tabella : List.of("persona", "azienda", "rapporto_lavoro", "cedolino", "riepilogo_rapporto",
                "segnalazione")) {
            assertThat(dsl.fetch("SELECT DISTINCT workspace_id FROM " + tabella).getValues(0, Long.class))
                    .as("workspace delle righe di %s", tabella).containsOnly(secondo.valore());
        }
        assertThat(dsl.fetchCount(dsl.selectFrom("riepilogo_rapporto"))).isEqualTo(1);
        assertThat(anagrafica.elencoPersone()).as("nulla nel workspace della richiesta").isEmpty();
    }

    @Test
    void comePasswordSiProvanoSoloICodiciFiscaliDelWorkspaceDelLotto() {
        caricaFixture("rossi-beta-2024-08");
        var secondo = nuovoWorkspace();

        var esito = elaboraNelWorkspace(secondo, "rossi-beta-2024-09");

        assertThat(esito.stato()).isEqualTo(StatoCaricamento.ERRORE);
        assertThat(esito.messaggio()).isEqualTo(PdfNonLeggibileException.MESSAGGIO_PASSWORD);
    }

    @Test
    void pdfProtettoColCodiceFiscaleDiUnaPersonaCensitaNelloStessoLottoSiRiprovaAFineLotto() {
        // il PDF protetto e' aperto subito, prima che il cedolino in chiaro censisca la persona
        lettoreCedolini.rallenta("rossi-beta-2024-08.pdf", Duration.ofMillis(500));

        var esiti = caricaFixture("rossi-beta-2024-09", "rossi-beta-2024-08");

        assertThat(esiti).extracting(Caricamento::stato)
                .containsExactly(StatoCaricamento.ACQUISITO, StatoCaricamento.ACQUISITO);
        assertThat(archivio.sintesiDiRapporto(unicoRapporto().id())).hasSize(2);
        assertThat(lettoreCedolini.estrazioni("rossi-beta-2024-09.pdf")).isEqualTo(1);
    }

    @Test
    void pdfProtettoConPasswordSconosciutaRestaInErroreDopoIlSecondoTentativo() {
        var esiti = caricaFixture("rossi-beta-2025-03", "rossi-beta-2025-02");

        assertThat(esiti.getFirst().stato()).isEqualTo(StatoCaricamento.ERRORE);
        assertThat(esiti.getFirst().messaggio()).isEqualTo(PdfNonLeggibileException.MESSAGGIO_PASSWORD);
        assertThat(esiti.get(1).stato()).isEqualTo(StatoCaricamento.ACQUISITO);
        assertThat(lettoreCedolini.estrazioni("rossi-beta-2025-03.pdf")).isZero();

        var conPassword = carica("demo-beta-2025",
                new FileCaricato("rossi-beta-2025-03.pdf", pdfFixture("rossi-beta-2025-03"))).getFirst();
        assertThat(conPassword.stato()).isEqualTo(StatoCaricamento.ACQUISITO);
    }

    // ------------------------------------------------------------------------------------------ errori

    @Test
    void documentiNonValidiVannoInErroreConMessaggioComprensibile() {
        var esiti = carica(null,
                new FileCaricato("test-due-cedolini.pdf", pdfFixture("test-due-cedolini")),
                new FileCaricato("test-non-cedolino.pdf", pdfFixture("test-non-cedolino")),
                new FileCaricato("appunti.pdf", "non sono un pdf".getBytes()));

        assertThat(esiti).extracting(Caricamento::stato).containsOnly(StatoCaricamento.ERRORE);
        assertThat(esiti.get(0).messaggio()).contains("2 cedolini distinti");
        assertThat(esiti.get(1).messaggio()).contains("non sembra un cedolino");
        assertThat(esiti.get(2).messaggio()).isEqualTo("Il file non è un PDF");
        assertThat(anagrafica.elencoPersone()).isEmpty();
    }

    @Test
    void periodoNonLeggibileVaInErrore() {
        lettoreCedolini.registraVariante("senza-periodo.pdf", "rossi-alfa-2023-01", json -> {
            var periodo = (tools.jackson.databind.node.ObjectNode) json.get("periodo");
            periodo.put("mese", 0);
        });

        var esito = carica(null, new FileCaricato("senza-periodo.pdf", pdfConTesto("Senza periodo"))).getFirst();

        assertThat(esito.stato()).isEqualTo(StatoCaricamento.ERRORE);
        assertThat(esito.messaggio()).isEqualTo(
                MessaggiCaricamento.nonAcquisibile(MessaggiCaricamento.PERIODO_NON_LEGGIBILE));
        assertThat(anagrafica.elencoPersone()).isEmpty();
    }

    // ------------------------------------------------------------------------------------------ eliminazione

    @Test
    void eliminazioneAggiornaRapportoERicalcolaFinoAllaPuliziaDegliOrfani() {
        var esiti = caricaFixture("rossi-alfa-2024-05", "rossi-alfa-2024-06");
        var rapporto = unicoRapporto();
        assertThat(rapporto.dataCessazione()).isEqualTo(LocalDate.of(2024, 6, 15));
        var giugno = esiti.get(1).cedolinoId();

        archivio.elimina(giugno);

        var aggiornato = anagrafica.trovaRapporto(rapporto.id()).orElseThrow();
        assertThat(aggiornato.ultimoCedolino()).isEqualTo(YearMonth.of(2024, 5));
        assertThat(aggiornato.dataCessazione()).as("la cessazione era stampata solo sul cedolino eliminato").isNull();
        assertThat(archivio.pdf(giugno)).isEmpty();
        assertThat(archivio.dettaglio(giugno)).isEmpty();
        assertThat(caricamento.stato(List.of(esiti.get(1).id()))).singleElement()
                .satisfies(c -> {
                    assertThat(c.cedolinoId()).isNull();
                    assertThat(c.personaId()).as("cedolino eliminato: nessuna attribuzione").isNull();
                    assertThat(c.rapportoId()).isNull();
                });
        assertThat(eventi.eliminati).singleElement().satisfies(e -> {
            assertThat(e.rapportoId()).isEqualTo(rapporto.id());
            assertThat(e.competenza()).isEqualTo(new Competenza.Mensilita(2024, 6));
        });

        archivio.elimina(esiti.get(0).cedolinoId());

        assertThat(anagrafica.trovaRapporto(rapporto.id())).isEmpty();
        assertThat(anagrafica.trovaAzienda(rapporto.aziendaId())).isEmpty();
        assertThat(anagrafica.elencoPersone()).isEmpty();
        assertThat(eventi.eliminati).hasSize(2);
        assertThat(archivio.normalizzatiDiRapporto(rapporto.id())).isEmpty();
    }

    @Test
    void eliminareUnCedolinoInesistenteEUnErrore() {
        assertThatThrownBy(() -> archivio.elimina(new CedolinoId(999_999)))
                .isInstanceOf(RisorsaNonTrovataException.class);
    }

    // ------------------------------------------------------------------------------------------ concorrenza e riavvio

    @Test
    void piuFileDellaStessaPersonaNuovaNelLottoCreanoUnaSolaPersona() {
        var esiti = caricaFixture("rossi-alfa-2023-01", "rossi-alfa-2023-02", "rossi-alfa-2023-03",
                "rossi-alfa-2023-04", "rossi-alfa-2023-06", "rossi-alfa-2023-07", "rossi-beta-2024-06",
                "rossi-beta-2024-07", "rossi-beta-2024-08", "rossi2-beta-2025-01");

        assertThat(esiti).extracting(Caricamento::stato).containsOnly(StatoCaricamento.ACQUISITO);
        var persone = anagrafica.elencoPersone();
        assertThat(persone).hasSize(2).extracting(PersonaSintesi::codiceFiscale)
                .containsExactlyInAnyOrder(CF_MARIO, "RSSMRA85C12F205M");
        var mario = persone.stream().filter(p -> p.codiceFiscale().equals(CF_MARIO)).findFirst().orElseThrow();
        var rapporti = anagrafica.rapportiDiPersona(mario.id());
        assertThat(rapporti).hasSize(2);
        var omonimo = persone.stream().filter(p -> !p.codiceFiscale().equals(CF_MARIO)).findFirst().orElseThrow();
        var aziendaOmonimo = anagrafica.rapportiDiPersona(omonimo.id()).getFirst().aziendaId();
        assertThat(rapporti).extracting(RapportoSintesi::aziendaId).contains(aziendaOmonimo);
        assertThat(rapporti.stream().mapToInt(r -> archivio.sintesiDiRapporto(r.id()).size()).sum()).isEqualTo(9);
    }

    @Test
    void alRiavvioICaricamentiNonTerminatiVannoInErrore() {
        var lotto = UUID.randomUUID();
        var registrati = caricamentiRepository.registra(WorkspaceId.DEFAULT, lotto,
                List.of(new FileInCoda("in-coda.pdf", 10, "a".repeat(64)), new FileInCoda("in-lettura.pdf", 10, "b".repeat(64))));
        caricamentiRepository.aggiorna(WorkspaceId.DEFAULT, registrati.get(1).id(), StatoCaricamento.IN_LETTURA, null,
                null, null);

        assertThat(ripristino.ripristina()).isEqualTo(2);

        assertThat(caricamento.statoLotto(lotto)).allSatisfy(c -> {
            assertThat(c.stato()).isEqualTo(StatoCaricamento.ERRORE);
            assertThat(c.messaggio()).isEqualTo(MessaggiCaricamento.INTERROTTO_DAL_RIAVVIO);
        });
    }

    @Test
    void lottoVuotoNonRegistraNulla() {
        assertThat(caricamento.avvia(List.of())).isEmpty();
        assertThat(caricamento.recenti(10)).isEmpty();
    }

    // ------------------------------------------------------------------------------------------ supporto

    private List<Caricamento> caricaFixture(String... nomi) {
        var files = new ArrayList<FileCaricato>();
        for (var nome : nomi) {
            files.add(new FileCaricato(nome + ".pdf", pdfFixture(nome)));
        }
        return carica(null, files.toArray(FileCaricato[]::new));
    }

    /** Avvia il lotto e attende che tutti i file siano in uno stato terminale. */
    private List<Caricamento> carica(String password, FileCaricato... files) {
        var registrati = caricamento.avvia(List.of(files), password);
        assertThat(registrati).hasSize(files.length)
                .allSatisfy(c -> assertThat(c.stato()).isEqualTo(StatoCaricamento.IN_CODA));
        var lotto = registrati.getFirst().lotto();
        await().atMost(ATTESA_MASSIMA).pollInterval(Duration.ofMillis(50))
                .until(() -> caricamento.statoLotto(lotto).stream().allMatch(c -> c.stato().terminale()));
        return caricamento.statoLotto(lotto);
    }

    /**
     * Variante del cedolino di fine rapporto di luglio 2024 con un altro periodo e tipo.
     *
     * @param conCessazione false per togliere la data di cessazione stampata
     */
    private void registraFineRapporto(String nomeFile, int mese, String tipo, boolean conCessazione) {
        lettoreCedolini.registraVariante(nomeFile, "rossi-alfa-2024-tfr01", json -> {
            var periodo = (tools.jackson.databind.node.ObjectNode) json.get("periodo");
            periodo.put("mese", mese);
            periodo.put("tipo", tipo);
            periodo.put("dataElaborazione", "2024-%02d-10".formatted(mese));
            if (!conCessazione) {
                ((tools.jackson.databind.node.ObjectNode) json.get("datiContrattuali")).put("dataCessazione", "");
            }
        });
    }

    private RapportoLavoro unicoRapporto() {
        var persone = anagrafica.elencoPersone();
        assertThat(persone).hasSize(1);
        return unicoRapporto(persone.getFirst());
    }

    private RapportoLavoro unicoRapporto(PersonaSintesi persona) {
        var rapporti = anagrafica.rapportiDiPersona(persona.id());
        assertThat(rapporti).hasSize(1);
        return anagrafica.trovaRapporto(rapporti.getFirst().id()).orElseThrow();
    }

    private List<Competenza> competenze(RapportoLavoro rapporto) {
        return archivio.normalizzatiDiRapporto(rapporto.id()).stream().map(CedolinoNormalizzato::competenza).toList();
    }

    private WorkspaceId nuovoWorkspace() {
        var codice = "prova-" + UUID.randomUUID();
        dsl.execute("INSERT INTO workspace (codice, descrizione) VALUES (?, 'Secondo workspace di prova')", codice);
        return new WorkspaceId(dsl.fetchSingle("SELECT id FROM workspace WHERE codice = ?", codice).get(0, Long.class));
    }

    /**
     * Elabora una fixture come lotto di un solo file del workspace indicato, mentre il workspace della richiesta
     * (provider dei test) resta quello di default.
     */
    private Caricamento elaboraNelWorkspace(WorkspaceId workspace, String fixture) {
        var nome = fixture + ".pdf";
        var contenuto = pdfFixture(fixture);
        var hash = CaricamentoCedoliniService.sha256(contenuto);
        var registrato = caricamentiRepository.registra(workspace, UUID.randomUUID(),
                List.of(new FileInCoda(nome, contenuto.length, hash))).getFirst();
        elaborazione.accoda(List.of(new ElaborazioneCaricamenti.Lavoro(workspace, registrato.id(), nome, contenuto,
                hash, null)));
        await().atMost(ATTESA_MASSIMA).pollInterval(Duration.ofMillis(50))
                .until(() -> caricamentiRepository.perId(workspace, List.of(registrato.id())).getFirst().stato()
                        .terminale());
        return caricamentiRepository.perId(workspace, List.of(registrato.id())).getFirst();
    }

    /** Competenze e nomi file (senza gli id di azienda e persona) in ordine cronologico. */
    private List<String> statoNormalizzato(RapportoLavoro rapporto) {
        var suffisso = "_" + rapporto.aziendaId().valore() + "_" + rapporto.personaId().valore() + ".pdf";
        return archivio.sintesiDiRapporto(rapporto.id()).stream()
                .map(c -> c.competenza() + " " + c.nomeFile().replace(suffisso, ""))
                .toList();
    }

    private List<String> nomiFile(RapportoLavoro rapporto) {
        return archivio.sintesiDiRapporto(rapporto.id()).stream().map(CedolinoSintesi::nomeFile).toList();
    }
}
