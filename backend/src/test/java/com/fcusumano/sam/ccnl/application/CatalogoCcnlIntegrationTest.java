package com.fcusumano.sam.ccnl.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.ccnl.CatalogoCcnl;
import com.fcusumano.sam.ccnl.Ccnl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Catalogo CCNL sulla tabella reale (seed Liquibase su H2).
 */
@ApplicationModuleTest
@ActiveProfiles("test")
class CatalogoCcnlIntegrationTest {

    @Autowired
    CatalogoCcnl catalogo;

    @Test
    void elencoOrdinatoPerDescrizioneConValoriCertiEIncertiDistinti() {
        var elenco = catalogo.elenco();
        assertThat(elenco).extracting(Ccnl::descrizione).isSorted();
        assertThat(elenco).extracting(Ccnl::codice)
                .contains("COMMERCIO", "METALMECCANICO_INDUSTRIA", "ALTRO_13", "ALTRO_14");

        var commercio = catalogo.trova("COMMERCIO").orElseThrow();
        assertThat(commercio.mensilita()).isEqualTo(14);
        assertThat(commercio.regoleScattiNote()).isTrue();
        assertThat(commercio.meseQuattordicesima()).isEqualTo(7);
        assertThat(commercio.noteVerifica()).isNull();

        var metalmeccanico = catalogo.trova("METALMECCANICO_INDUSTRIA").orElseThrow();
        assertThat(metalmeccanico.mensilita()).isEqualTo(13);
        assertThat(metalmeccanico.periodicitaScattiMesi()).isEqualTo(24);
        assertThat(metalmeccanico.maxScatti()).isEqualTo(5);

        // Valori non certi: NULL con nota di verifica
        var turismo = catalogo.trova("TURISMO").orElseThrow();
        assertThat(turismo.meseQuattordicesima()).isNull();
        assertThat(turismo.regoleScattiNote()).isFalse();
        assertThat(turismo.noteVerifica()).isEqualTo("Verificare periodicità e numero massimo scatti di anzianità e "
                + "mese di pagamento della quattordicesima (non impostato).");
    }

    @Test
    void descrizioniENoteConLeLettereAccentate() {
        assertThat(catalogo.trova("ALTRO_13").orElseThrow().descrizione()).isEqualTo("Altro CCNL (13 mensilità)");
        assertThat(catalogo.trova("ALTRO_14").orElseThrow().descrizione()).isEqualTo("Altro CCNL (14 mensilità)");
        assertThat(catalogo.elenco()).extracting(Ccnl::noteVerifica).filteredOn(nota -> nota != null)
                .noneMatch(nota -> nota.matches(".*\\b(mensilita|periodicita|anzianita)\\b.*"));
    }

    @Test
    void trovaSoloPerCodiceEsatto() {
        assertThat(catalogo.trova("COMMERCIO")).isPresent();
        assertThat(catalogo.trova("commercio")).isEmpty();
        assertThat(catalogo.trova("INESISTENTE")).isEmpty();
        assertThat(catalogo.trova(null)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "Metalmeccanici imp. (p.ind);METALMECCANICO_INDUSTRIA",
            "CONTRATTO DI LAVORO Metalmeccanici imp. (p.ind) CNEL C018;METALMECCANICO_INDUSTRIA",
            "CCNL Commercio Terziario;COMMERCIO",
            "Terziario, distribuzione e servizi;COMMERCIO",
            "Metalmeccanica PMI Confapi;METALMECCANICO_PMI",
            "Telecomunicazioni;TELECOMUNICAZIONI",
            "Studi professionali;STUDI_PROFESSIONALI",
            "Turismo - Pubblici esercizi;TURISMO",
            "Edilizia industria;EDILIZIA_INDUSTRIA",
            "Chimico farmaceutico;CHIMICO_FARMACEUTICO_INDUSTRIA"})
    void riconosceITestiStampatiTipici(String testo, String atteso) {
        assertThat(catalogo.riconosci(testo)).map(Ccnl::codice).contains(atteso);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Metalmeccanica artigianato",
            "Commercio Confesercenti",
            "Dirigenti commercio",
            "Credito cooperativo",
            "Chimica gomma plastica vetro PMI",
            "Commercio e turismo",
            "Agenzia commerciale",
            "Contratto collettivo nazionale di lavoro",
            "Abi"})
    void inCasoDiDubbioNonRiconosceNulla(String testo) {
        assertThat(catalogo.riconosci(testo)).isEmpty();
    }
}
