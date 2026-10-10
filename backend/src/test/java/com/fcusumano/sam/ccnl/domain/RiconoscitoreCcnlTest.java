package com.fcusumano.sam.ccnl.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RiconoscitoreCcnlTest {

    private static final RiconoscitoreCcnl RICONOSCITORE = new RiconoscitoreCcnl(List.of(
            RegolaRiconoscimento.daColonne("COMMERCIO", null,
                    "commercio|terziario|distribuzione e servizi", "confesercenti|dirigenti"),
            RegolaRiconoscimento.daColonne("METALMECCANICO_INDUSTRIA", "C011",
                    "metalmeccanico|metalmeccanici|metalmeccanica|industria metalmeccanica",
                    "artigianato|pmi|confapi|piccola e media industria"),
            RegolaRiconoscimento.daColonne("METALMECCANICO_PMI", null,
                    "unionmeccanica|piccola e media industria metalmeccanica", "artigianato"),
            RegolaRiconoscimento.daColonne("TURISMO", "H052", "turismo|pubblici esercizi", null),
            RegolaRiconoscimento.daColonne("ALTRO_13", null, null, null)));

    @ParameterizedTest
    @ValueSource(strings = {
            "Metalmeccanici imp. (p.ind)",
            "CONTRATTO DI LAVORO Metalmeccanici imp. (p.ind)",
            "C.C.N.L. METALMECCANICO",
            "Industria Metalmeccanica"})
    void riconosceIlMetalmeccanicoIndustriaDaParoleIntere(String testo) {
        assertThat(RICONOSCITORE.riconosci(testo)).contains("METALMECCANICO_INDUSTRIA");
    }

    @Test
    void ignoraAccentiMaiuscoleEPunteggiatura() {
        assertThat(RICONOSCITORE.riconosci("CCNL TERZIARIO: DISTRIBUZIONE & SERVIZI")).contains("COMMERCIO");
        assertThat(RICONOSCITORE.riconosci("ccnl commèrcio")).contains("COMMERCIO");
    }

    @Test
    void nonRiconosceFrammentiDiParola() {
        // "commerciale" contiene "commercio" solo come frammento: nessun riconoscimento
        assertThat(RICONOSCITORE.riconosci("Agente commerciale")).isEmpty();
        assertThat(RICONOSCITORE.riconosci("Metalmeccanicità")).isEmpty();
    }

    @Test
    void piuCcnlCandidatiNonDannoRiconoscimento() {
        assertThat(RICONOSCITORE.riconosci("Commercio e turismo")).isEmpty();
    }

    @Test
    void leParoleEscluseEliminanoIlCandidato() {
        assertThat(RICONOSCITORE.riconosci("Metalmeccanica artigianato")).isEmpty();
        assertThat(RICONOSCITORE.riconosci("Commercio Confesercenti")).isEmpty();
        assertThat(RICONOSCITORE.riconosci("Metalmeccanica PMI Unionmeccanica")).contains("METALMECCANICO_PMI");
    }

    @Test
    void unaCorrispondenzaContenutaInUnaPiuLungaDiUnAltroCcnlNonConta() {
        // "industria metalmeccanica" e' dentro "piccola e media industria metalmeccanica", che pero' esclude
        // gia' l'industria; senza esclusione vince comunque la frase piu' lunga
        var senzaEsclusioni = new RiconoscitoreCcnl(List.of(
                RegolaRiconoscimento.daColonne("IND", null, "industria metalmeccanica|metalmeccanica", null),
                RegolaRiconoscimento.daColonne("PMI", null, "piccola e media industria metalmeccanica", null)));
        assertThat(senzaEsclusioni.riconosci("Piccola e media industria metalmeccanica")).contains("PMI");
        assertThat(senzaEsclusioni.riconosci("Industria metalmeccanica")).contains("IND");
    }

    @Test
    void ilCodiceCnelRiconosceDaSoloOSeConcordaConLeParoleChiave() {
        assertThat(RICONOSCITORE.riconosci("CNEL C011")).contains("METALMECCANICO_INDUSTRIA");
        assertThat(RICONOSCITORE.riconosci("Metalmeccanici CNEL C011")).contains("METALMECCANICO_INDUSTRIA");
        assertThat(RICONOSCITORE.riconosci("Codice cnel h052")).contains("TURISMO");
    }

    @Test
    void codiceCnelInContrastoConLeParoleChiaveNonRiconosceNulla() {
        assertThat(RICONOSCITORE.riconosci("Commercio CNEL C011")).isEmpty();
        assertThat(RICONOSCITORE.riconosci("C011 H052")).isEmpty();
    }

    @Test
    void codiceCnelNonInTabellaNonImpedisceIlRiconoscimentoPerParoleChiave() {
        assertThat(RICONOSCITORE.riconosci("Metalmeccanici imp. (p.ind) CNEL C018")).contains("METALMECCANICO_INDUSTRIA");
        assertThat(RICONOSCITORE.riconosci("CNEL C018")).isEmpty();
    }

    @Test
    void codiceCnelConParolaEsclusaNonRiconosce() {
        assertThat(RICONOSCITORE.riconosci("C011 artigianato")).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "...", "CCNL", "Contratto collettivo nazionale"})
    void testoVuotoOGenericoNonRiconosceNulla(String testo) {
        assertThat(RICONOSCITORE.riconosci(testo)).isEmpty();
    }
}
