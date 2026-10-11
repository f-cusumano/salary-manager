package com.fcusumano.sam.segnalazioni.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.anagrafica.GestioneRapporti;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.anagrafica.RapportoSintesi;
import com.fcusumano.sam.cedolini.ArchivioCedolini;
import com.fcusumano.sam.cedolini.Caricamento;
import com.fcusumano.sam.cedolini.CaricamentoCedolini;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.CedolinoSintesi;
import com.fcusumano.sam.cedolini.FileCaricato;
import com.fcusumano.sam.cedolini.StatoCaricamento;
import com.fcusumano.sam.segnalazioni.Evidenza;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.Segnalazione;
import com.fcusumano.sam.segnalazioni.SegnalazioniRapporto;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import com.fcusumano.sam.segnalazioni.config.SegnalazioniProperties;
import com.fcusumano.sam.shared.Competenza;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Segnalazioni end-to-end sul database H2: caricamento delle fixture (dati inventati) con il lettore a fixture,
 * ricalcolo sincrono agli eventi di acquisizione, eliminazione e cambio CCNL, persistenza ed evidenze.
 */
@SpringBootTest
@ActiveProfiles("test")
class SegnalazioniIntegrationTest {

    private static final Duration ATTESA_MASSIMA = Duration.ofSeconds(60);
    private static final String CF_MARIO = "RSSMRA80A01H501U";

    @Autowired
    CaricamentoCedolini caricamento;
    @Autowired
    ArchivioCedolini archivio;
    @Autowired
    ConsultazioneAnagrafica anagrafica;
    @Autowired
    GestioneRapporti gestioneRapporti;
    @Autowired
    SegnalazioniRapporto segnalazioni;
    @Autowired
    SegnalazioniProperties proprieta;
    @Autowired
    DSLContext dsl;

    @BeforeEach
    void databaseVuoto() {
        dsl.execute("DELETE FROM caricamento");
        dsl.execute("DELETE FROM cedolino");
        dsl.execute("DELETE FROM rapporto_lavoro");
        dsl.execute("DELETE FROM persona");
        dsl.execute("DELETE FROM azienda");
    }

    @Test
    void rapportoAlfaSegnalaSoloMaggio2023Mancante() {
        var fixture = new ArrayList<>(List.of("rossi-alfa-2023-13", "rossi-alfa-2024-tfr01"));
        for (var mese : List.of("2023-01", "2023-02", "2023-03", "2023-04", "2023-06", "2023-07", "2023-08", "2023-09",
                "2023-10", "2023-11", "2023-12", "2024-01", "2024-02", "2024-03", "2024-04", "2024-05", "2024-06")) {
            fixture.add("rossi-alfa-" + mese);
        }
        assertThat(carica(fixture.toArray(String[]::new))).extracting(Caricamento::stato)
                .containsOnly(StatoCaricamento.ACQUISITO);
        var rapporto = rapportoDi("Alfa Software S.r.l.");

        var elenco = segnalazioni.segnalazioni(rapporto);

        // Tredicesima 2023 separata, ratei nel fine rapporto, fondo TFR coerente, scatto pagato, CCNL riconosciuto
        assertThat(elenco).singleElement().satisfies(s -> {
            assertThat(s.id()).isNotNull();
            assertThat(s.rapportoId()).isEqualTo(rapporto);
            assertThat(s.tipo()).isEqualTo(TipoSegnalazione.MENSILITA_MANCANTE);
            assertThat(s.gravita()).isEqualTo(Gravita.ANOMALIA);
            assertThat(s.competenza()).isEqualTo(new Competenza.Mensilita(2023, 5));
            assertThat(s.titolo()).isEqualTo("Mensilità mancante: Maggio 2023");
            assertThat(s.evidenze()).extracting(Evidenza::descrizione)
                    .containsExactly("Cedolino precedente: Aprile 2023", "Cedolino successivo: Giugno 2023");
            assertThat(s.evidenze()).extracting(Evidenza::cedolinoId)
                    .containsExactly(cedolino(rapporto, 2023, 4), cedolino(rapporto, 2023, 6));
            assertThat(s.evidenze()).extracting(Evidenza::competenza)
                    .containsExactly(new Competenza.Mensilita(2023, 4), new Competenza.Mensilita(2023, 6));
        });
        assertThat(segnalazioni.haSegnalazioni(rapporto)).isTrue();
        assertThat(segnalazioni.mensilitaMancanti(rapporto)).containsExactly(new Competenza.Mensilita(2023, 5));
    }

    @Test
    void ricalcoloIdempotente() {
        carica("rossi-alfa-2023-01", "rossi-alfa-2023-03");
        var rapporto = rapportoDi("Alfa Software S.r.l.");
        var prima = segnalazioni.segnalazioni(rapporto);

        segnalazioni.ricalcola(rapporto);
        segnalazioni.ricalcola(rapporto);

        assertThat(segnalazioni.segnalazioni(rapporto)).usingRecursiveFieldByFieldElementComparatorIgnoringFields("id")
                .containsExactlyElementsOf(prima);
    }

    @Test
    void ilCambioDiCcnlRicalcolaLeSegnalazioni() {
        carica("rossi-beta-2024-06", "rossi-beta-2024-07", "rossi-beta-2024-08", "rossi-beta-2024-10",
                "rossi-beta-2024-11", "rossi-beta-2024-12", "rossi-beta-2025-01", "rossi-beta-2025-02");
        var rapporto = rapportoDi("Beta Consulting S.p.A.");

        assertThat(segnalazioni.segnalazioni(rapporto)).extracting(Segnalazione::tipo).containsExactly(
                TipoSegnalazione.MENSILITA_MANCANTE, TipoSegnalazione.CCNL_NON_DETERMINATO);
        assertThat(segnalazioni.mensilitaMancanti(rapporto)).containsExactly(new Competenza.Mensilita(2024, 9));

        gestioneRapporti.impostaCcnl(rapporto, "COMMERCIO");

        // Assunzione il 16/06/2024: 15 giorni nel periodo di maturazione bastano per la quattordicesima di luglio 2024
        var dopo = segnalazioni.segnalazioni(rapporto);
        assertThat(dopo).extracting(Segnalazione::tipo).containsExactly(
                TipoSegnalazione.QUATTORDICESIMA_MANCANTE, TipoSegnalazione.MENSILITA_MANCANTE);
        assertThat(dopo.getFirst().descrizione()).contains("a Luglio", "per il 2024");
        assertThat(segnalazioni.mensilitaMancanti(rapporto)).containsExactly(
                Competenza.Mensilita.quattordicesima(2024), new Competenza.Mensilita(2024, 9));
    }

    @Test
    void lEliminazioneDiUnCedolinoRicalcolaDaZero() {
        carica("rossi-alfa-2023-03", "rossi-alfa-2023-04", "rossi-alfa-2023-06");
        var rapporto = rapportoDi("Alfa Software S.r.l.");
        assertThat(segnalazioni.mensilitaMancanti(rapporto)).containsExactly(new Competenza.Mensilita(2023, 5));

        archivio.elimina(cedolino(rapporto, 2023, 4));

        assertThat(segnalazioni.mensilitaMancanti(rapporto))
                .containsExactly(new Competenza.Mensilita(2023, 4), new Competenza.Mensilita(2023, 5));

        archivio.elimina(cedolino(rapporto, 2023, 3));

        assertThat(segnalazioni.segnalazioni(rapporto)).isEmpty();
        assertThat(segnalazioni.haSegnalazioni(rapporto)).isFalse();
        assertThat(segnalazioni.mensilitaMancanti(rapporto)).isEmpty();
    }

    @Test
    void rapportoInesistenteNessunaSegnalazione() {
        var inesistente = new RapportoLavoroId(999_999);

        segnalazioni.ricalcola(inesistente);

        assertThat(segnalazioni.segnalazioni(inesistente)).isEmpty();
        assertThat(segnalazioni.haSegnalazioni(inesistente)).isFalse();
    }

    @Test
    void parametriDaApplicationYml() {
        assertThat(proprieta.tolleranzaTfr()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(proprieta.tolleranzaImporti()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(proprieta.mesiAttesaFineRapporto()).isEqualTo(2);
        assertThat(proprieta.ccnlTredicesimaCassaEdile())
                .containsExactlyInAnyOrder("EDILIZIA_INDUSTRIA", "EDILIZIA_ARTIGIANATO");
    }

    // ------------------------------------------------------------------------------------------ supporto

    private List<Caricamento> carica(String... fixture) {
        var files = Arrays.stream(fixture).map(n -> new FileCaricato(n + ".pdf", pdf(n))).toList();
        var registrati = caricamento.avvia(files);
        var lotto = registrati.getFirst().lotto();
        await().atMost(ATTESA_MASSIMA).pollInterval(Duration.ofMillis(50))
                .until(() -> caricamento.statoLotto(lotto).stream().allMatch(c -> c.stato().terminale()));
        return caricamento.statoLotto(lotto);
    }

    private RapportoLavoroId rapportoDi(String ragioneSociale) {
        var persona = anagrafica.elencoPersone().stream().filter(p -> p.codiceFiscale().equals(CF_MARIO)).findFirst()
                .orElseThrow();
        return anagrafica.rapportiDiPersona(persona.id()).stream()
                .filter(r -> r.ragioneSociale().equals(ragioneSociale))
                .map(RapportoSintesi::id)
                .findFirst().orElseThrow();
    }

    private CedolinoId cedolino(RapportoLavoroId rapporto, int anno, int mese) {
        var periodo = YearMonth.of(anno, mese);
        return archivio.sintesiDiRapporto(rapporto).stream()
                .filter(c -> c.competenza().equals(Competenza.Mensilita.ordinaria(periodo)))
                .map(CedolinoSintesi::id)
                .findFirst().orElseThrow();
    }

    private static byte[] pdf(String nome) {
        try (InputStream in = SegnalazioniIntegrationTest.class.getResourceAsStream("/fixtures/pdf/" + nome + ".pdf")) {
            if (in == null) {
                throw new IllegalArgumentException("PDF di fixture assente: " + nome);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
