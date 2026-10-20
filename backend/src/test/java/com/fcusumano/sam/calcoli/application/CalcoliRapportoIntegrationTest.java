package com.fcusumano.sam.calcoli.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.anagrafica.GestioneRapporti;
import com.fcusumano.sam.anagrafica.RapportoLavoro;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.anagrafica.RapportoSintesi;
import com.fcusumano.sam.calcoli.CalcoliRapporto;
import com.fcusumano.sam.calcoli.FonteDato;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.IndicatoriMensilita;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.SaldoRateo;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.VoceLiquidazione;
import com.fcusumano.sam.calcoli.TipoVoceLiquidazione;
import com.fcusumano.sam.calcoli.domain.CalcolatoreRiepilogo;
import com.fcusumano.sam.calcoli.domain.ContestoRapporto;
import com.fcusumano.sam.ccnl.CatalogoCcnl;
import com.fcusumano.sam.cedolini.ArchivioCedolini;
import com.fcusumano.sam.cedolini.Caricamento;
import com.fcusumano.sam.cedolini.CaricamentoCedolini;
import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.cedolini.FileCaricato;
import com.fcusumano.sam.cedolini.StatoCaricamento;
import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.UnitaMisura;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Ricalcolo dei dati derivati end-to-end sul database H2 (schema Liquibase): caricamento dei cedolini di fixture
 * (dati inventati) con il lettore a fixture, ricalcolo sincrono sugli eventi di acquisizione, cambio CCNL ed
 * eliminazione, persistenza e rilettura del riepilogo.
 */
@SpringBootTest
@ActiveProfiles("test")
class CalcoliRapportoIntegrationTest {

    /** Password di apertura del PDF di fixture protetto (codice fiscale fittizio in minuscolo). */
    private static final String PASSWORD_FIXTURE = "rssmra80a01h501u";
    private static final Duration ATTESA_MASSIMA = Duration.ofSeconds(60);

    @Autowired
    CaricamentoCedolini caricamento;
    @Autowired
    ArchivioCedolini archivio;
    @Autowired
    ConsultazioneAnagrafica anagrafica;
    @Autowired
    GestioneRapporti gestioneRapporti;
    @Autowired
    CatalogoCcnl catalogo;
    @Autowired
    CalcoliRapporto calcoli;
    @Autowired
    CalcolatoreRiepilogo calcolatore;
    @Autowired
    DSLContext dsl;

    /** Il contesto (e il database in memoria) e' condiviso con altre classi di test: si parte e si lascia vuoto. */
    @BeforeEach
    @AfterEach
    void databaseVuoto() {
        dsl.execute("DELETE FROM caricamento");
        dsl.execute("DELETE FROM password_pdf");
        dsl.execute("DELETE FROM cedolino");
        dsl.execute("DELETE FROM rapporto_lavoro");
        dsl.execute("DELETE FROM persona");
        dsl.execute("DELETE FROM azienda");
    }

    @Test
    void rapportoCessatoConTfrLiquidato() {
        var alfa = caricaAlfa();

        var r = calcoli.riepilogo(alfa.id()).orElseThrow();

        assertThat(r.anzianita().dal()).isEqualTo(YearMonth.of(2021, 9));
        assertThat(r.anzianita().al()).isEqualTo(YearMonth.of(2024, 6));
        assertThat(r.anzianita().anni()).isEqualTo(2);
        assertThat(r.anzianita().mesi()).isEqualTo(10);
        assertThat(r.anzianita().inCorso()).isFalse();
        assertThat(r.anzianita().fonteInizio()).isEqualTo(FonteDato.DATA_CONTRATTUALE);
        assertThat(r.anzianita().fonteFine()).isEqualTo(FonteDato.DATA_CONTRATTUALE);

        assertThat(r.scatti().attuali()).isEqualTo(1);
        assertThat(r.scatti().fonteAttuali()).isEqualTo(FonteDato.CEDOLINI);
        assertThat(r.scatti().massimo()).isEqualTo(5);
        assertThat(r.scatti().prossimoScatto()).as("rapporto cessato").isNull();

        var giugno2024 = cedolino(alfa, new Competenza.Mensilita(2024, 6));
        assertThat(r.retribuzione().ral()).isEqualByComparingTo("30225.00");
        assertThat(r.retribuzione().lordoMensile()).isEqualByComparingTo("2325.00");
        assertThat(r.retribuzione().mensilita()).isEqualTo(13);
        assertThat(r.retribuzione().minimo()).isEqualByComparingTo("2123.50");
        assertThat(r.retribuzione().superminimo()).isEqualByComparingTo("176.50");
        assertThat(r.retribuzione().importoScatti()).isEqualByComparingTo("25.00");
        assertThat(r.retribuzione().stimata()).isFalse();
        assertThat(r.retribuzione().riferimento()).isEqualTo(giugno2024.id());
        assertThat(r.buoniPasto()).isFalse();

        var fineRapporto = archivio.normalizzatiDiRapporto(alfa.id()).getLast();
        assertThat(r.ratei().riferimento()).isEqualTo(fineRapporto.id());
        assertThat(r.ratei().saldi()).extracting(SaldoRateo::tipo, SaldoRateo::unita).containsExactly(
                org.assertj.core.groups.Tuple.tuple(TipoRateo.FERIE, UnitaMisura.GIORNI),
                org.assertj.core.groups.Tuple.tuple(TipoRateo.ROL, UnitaMisura.ORE));
        assertThat(r.ratei().saldi()).allSatisfy(s -> assertThat(s.saldo()).isEqualByComparingTo("0"));

        assertThat(r.tfr().liquidato()).isTrue();
        assertThat(r.tfr().totaleAccumulato()).isEqualByComparingTo("0");
        assertThat(r.tfr().importoLiquidato()).isEqualByComparingTo("2435.52");
        assertThat(r.tfr().nettoLiquidato()).isEqualByComparingTo("1937.63");
        assertThat(r.tfr().riferimento()).isEqualTo(YearMonth.of(2024, 7));
        assertThat(r.tfr().nota()).contains("Luglio 2024");
        assertThat(r.liquidazione().presente()).as("TFR gia' liquidato").isFalse();

        var conScatto = r.mensilita().stream().filter(IndicatoriMensilita::scattoInBusta)
                .map(IndicatoriMensilita::cedolinoId).toList();
        assertThat(conScatto).containsExactly(cedolino(alfa, new Competenza.Mensilita(2023, 9)).id());
        assertThat(r.mensilita()).as("rinnovo con riassorbimento del superminimo a parita' di totale")
                .noneMatch(IndicatoriMensilita::aumentoFuoriScatti);
        assertThat(r.mensilita()).extracting(IndicatoriMensilita::cedolinoId)
                .containsExactlyElementsOf(archivio.normalizzatiDiRapporto(alfa.id()).stream()
                        .map(CedolinoNormalizzato::id).toList());
    }

    @Test
    void rileggeEsattamenteIlRiepilogoCalcolato() {
        var alfa = caricaAlfa();
        var beta = caricaBeta();

        for (var rapporto : List.of(alfa, beta)) {
            var salvato = calcoli.riepilogo(rapporto.id()).orElseThrow();
            var atteso = calcolatore.calcola(contesto(rapporto.id()), archivio.normalizzatiDiRapporto(rapporto.id()),
                    salvato.calcolatoIl());
            assertThat(salvato).usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(atteso);
        }
    }

    @Test
    void ricalcoloIdempotente() {
        var alfa = caricaAlfa();
        var prima = calcoli.riepilogo(alfa.id()).orElseThrow();

        calcoli.ricalcola(alfa.id());
        calcoli.ricalcola(alfa.id());
        var dopo = calcoli.riepilogo(alfa.id()).orElseThrow();

        assertThat(dopo).usingRecursiveComparison().ignoringFields("calcolatoIl").isEqualTo(prima);
        assertThat(dsl.fetchCount(dsl.selectFrom("riepilogo_rateo"))).isEqualTo(2);
        assertThat(dsl.fetchCount(dsl.selectFrom("indicatore_mensilita")))
                .isEqualTo(archivio.normalizzatiDiRapporto(alfa.id()).size());
    }

    @Test
    void ccnlDaIndicareERicalcoloAlCambioDiCcnl() {
        var beta = caricaBeta();

        var senzaCcnl = calcoli.riepilogo(beta.id()).orElseThrow();
        assertThat(senzaCcnl.retribuzione().ral()).isEqualByComparingTo("33800.00");
        assertThat(senzaCcnl.retribuzione().mensilita()).isEqualTo(13);
        assertThat(senzaCcnl.retribuzione().stimata()).isTrue();
        assertThat(senzaCcnl.retribuzione().nota()).isEqualTo("CCNL non indicato: RAL calcolata su 13 mensilità.");
        assertThat(senzaCcnl.retribuzione().retribuzioneBase()).isEqualByComparingTo("2600.00");
        assertThat(senzaCcnl.scatti().attuali()).isNull();
        assertThat(senzaCcnl.buoniPasto()).isTrue();
        assertThat(senzaCcnl.anzianita().dal()).isEqualTo(YearMonth.of(2024, 6));
        assertThat(senzaCcnl.anzianita().al()).isEqualTo(YearMonth.of(2025, 2));
        assertThat(senzaCcnl.anzianita().inCorso()).isTrue();
        // TFR: fondo al 31/12/2024 + quota 2025
        assertThat(senzaCcnl.tfr().totaleAccumulato()).isEqualByComparingTo("1631.29");
        assertThat(senzaCcnl.tfr().quotaMensile()).isEqualByComparingTo("179.59");
        assertThat(senzaCcnl.tfr().stimaNetto()).isEqualByComparingTo("1256.09");
        assertThat(senzaCcnl.ratei().saldi()).hasSize(3);
        assertThat(senzaCcnl.ratei().saldi()).allSatisfy(s -> assertThat(s.valoreStimato()).isNotNull());

        // con la quattordicesima 2024 tra i cedolini la RAL senza CCNL e' stimata su 14 mensilita'
        carica(List.of("rossi-beta-2024-07-14"));
        var conQuattordicesima = calcoli.riepilogo(beta.id()).orElseThrow();
        assertThat(conQuattordicesima.retribuzione().ral()).isEqualByComparingTo("36400.00");
        assertThat(conQuattordicesima.retribuzione().mensilita()).isEqualTo(14);
        assertThat(conQuattordicesima.retribuzione().stimata()).isTrue();
        assertThat(conQuattordicesima.retribuzione().nota())
                .isEqualTo("CCNL non indicato: RAL calcolata su 14 mensilità (quattordicesima presente nei cedolini).");

        gestioneRapporti.impostaCcnl(beta.id(), "COMMERCIO");

        var commercio = calcoli.riepilogo(beta.id()).orElseThrow();
        assertThat(commercio.retribuzione().ral()).isEqualByComparingTo("36400.00");
        assertThat(commercio.retribuzione().mensilita()).isEqualTo(14);
        assertThat(commercio.retribuzione().stimata()).isFalse();
        assertThat(commercio.retribuzione().nota()).isNull();
        assertThat(commercio.scatti().attuali()).isZero();
        assertThat(commercio.scatti().attualiStimati()).isTrue();
        assertThat(commercio.scatti().prossimoScatto()).isEqualTo(LocalDate.of(2027, 7, 1));
        assertThat(commercio.scatti().fonteProssimo()).isEqualTo(FonteDato.CCNL);
        assertThat(commercio.liquidazione().dataRiferimento()).isEqualTo(LocalDate.of(2025, 2, 28));
        assertThat(commercio.liquidazione().voci()).extracting(VoceLiquidazione::tipo)
                .contains(TipoVoceLiquidazione.TFR, TipoVoceLiquidazione.RATEI_TREDICESIMA,
                        TipoVoceLiquidazione.RATEI_QUATTORDICESIMA);
        assertThat(commercio.liquidazione().voci()).allSatisfy(v -> assertThat(v.netto()).isNotNull());
        assertThat(dsl.fetchCount(dsl.selectFrom("riepilogo_liquidazione_voce")))
                .isEqualTo(commercio.liquidazione().voci().size());
    }

    @Test
    void eliminazioneDiUnCedolinoRicalcolaDaZero() {
        var beta = caricaBeta();
        var febbraio = cedolino(beta, new Competenza.Mensilita(2025, 2));
        var gennaio = cedolino(beta, new Competenza.Mensilita(2025, 1));

        archivio.elimina(febbraio.id());

        var r = calcoli.riepilogo(beta.id()).orElseThrow();
        assertThat(r.anzianita().al()).isEqualTo(YearMonth.of(2025, 1));
        assertThat(r.retribuzione().riferimento()).isEqualTo(gennaio.id());
        assertThat(r.ratei().riferimento()).isEqualTo(gennaio.id());
        assertThat(r.tfr().riferimento()).isEqualTo(YearMonth.of(2025, 1));
        assertThat(r.tfr().totaleAccumulato()).isEqualByComparingTo("1451.70");
        assertThat(r.mensilita()).extracting(IndicatoriMensilita::cedolinoId).doesNotContain(febbraio.id());
    }

    @Test
    void eliminandoTuttiICedoliniSparisconoIDatiDerivati() {
        var beta = caricaBeta();

        archivio.normalizzatiDiRapporto(beta.id()).forEach(c -> archivio.elimina(c.id()));

        assertThat(calcoli.riepilogo(beta.id())).isEmpty();
        assertThat(dsl.fetchCount(dsl.selectFrom("riepilogo_rapporto"))).isZero();
        assertThat(dsl.fetchCount(dsl.selectFrom("riepilogo_rateo"))).isZero();
        assertThat(dsl.fetchCount(dsl.selectFrom("indicatore_mensilita"))).isZero();
    }

    @Test
    void rapportoInesistenteNessunRiepilogo() {
        var inesistente = new RapportoLavoroId(999_999);

        calcoli.ricalcola(inesistente);

        assertThat(calcoli.riepilogo(inesistente)).isEmpty();
    }

    // ------------------------------------------------------------------------------------------------ supporto

    private RapportoLavoro caricaAlfa() {
        var nomi = new ArrayList<String>();
        IntStream.rangeClosed(1, 12).filter(m -> m != 5).forEach(m -> nomi.add("rossi-alfa-2023-%02d".formatted(m)));
        nomi.add("rossi-alfa-2023-13");
        IntStream.rangeClosed(1, 6).forEach(m -> nomi.add("rossi-alfa-2024-%02d".formatted(m)));
        nomi.add("rossi-alfa-2024-tfr01");
        carica(nomi);
        return rapporto("Alfa");
    }

    private RapportoLavoro caricaBeta() {
        var nomi = Stream.concat(
                IntStream.rangeClosed(6, 12).mapToObj(m -> "rossi-beta-2024-%02d".formatted(m)),
                Stream.of("rossi-beta-2025-01", "rossi-beta-2025-02")).toList();
        carica(nomi);
        return rapporto("Beta");
    }

    private void carica(List<String> nomi) {
        var files = nomi.stream().map(n -> new FileCaricato(n + ".pdf", pdf(n))).toList();
        var lotto = caricamento.avvia(files, PASSWORD_FIXTURE).getFirst().lotto();
        await().atMost(ATTESA_MASSIMA).pollInterval(Duration.ofMillis(50))
                .until(() -> caricamento.statoLotto(lotto).stream().allMatch(c -> c.stato().terminale()));
        assertThat(caricamento.statoLotto(lotto)).extracting(Caricamento::stato)
                .containsOnly(StatoCaricamento.ACQUISITO);
    }

    private RapportoLavoro rapporto(String azienda) {
        var rapporti = anagrafica.elencoPersone().stream()
                .flatMap(p -> anagrafica.rapportiDiPersona(p.id()).stream())
                .filter(r -> r.ragioneSociale().contains(azienda))
                .map(RapportoSintesi::id)
                .toList();
        assertThat(rapporti).hasSize(1);
        return anagrafica.trovaRapporto(rapporti.getFirst()).orElseThrow();
    }

    private CedolinoNormalizzato cedolino(RapportoLavoro rapporto, Competenza competenza) {
        return archivio.normalizzatiDiRapporto(rapporto.id()).stream()
                .filter(c -> c.competenza().equals(competenza))
                .min(Comparator.comparingLong(c -> c.id().valore()))
                .orElseThrow();
    }

    private ContestoRapporto contesto(RapportoLavoroId id) {
        var rapporto = anagrafica.trovaRapporto(id).orElseThrow();
        var ccnl = rapporto.ccnlCodice() == null ? null : catalogo.trova(rapporto.ccnlCodice()).orElseThrow();
        return new ContestoRapporto(id, rapporto.dataAssunzione(), rapporto.dataCessazione(), ccnl);
    }

    private static byte[] pdf(String nome) {
        try (InputStream in = CalcoliRapportoIntegrationTest.class.getResourceAsStream("/fixtures/pdf/" + nome + ".pdf")) {
            if (in == null) {
                throw new IllegalArgumentException("PDF di fixture assente: " + nome);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
