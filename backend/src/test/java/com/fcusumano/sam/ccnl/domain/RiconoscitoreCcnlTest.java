package com.fcusumano.sam.ccnl.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RiconoscitoreCcnlTest {

    private static final RiconoscitoreCcnl RICONOSCITORE = new RiconoscitoreCcnl(List.of(
            RegolaRiconoscimento.daColonne("COMMERCIO", "H011",
                    "commercio|terziario|distribuzione e servizi", "confesercenti|dirigenti|turismo"),
            RegolaRiconoscimento.daColonne("COMMERCIO_CONFESERCENTI", "H012",
                    "confesercenti|commercio confesercenti", "turismo|dirigenti"),
            RegolaRiconoscimento.daColonne("METALMECCANICO_INDUSTRIA", "C011",
                    "metalmeccanico|metalmeccanici|metalmeccanica|industria metalmeccanica",
                    "artigianato|artigiani|area meccanica|pmi|confapi|piccola e media industria|p ind"),
            RegolaRiconoscimento.daColonne("METALMECCANICO_PMI", "C018",
                    "unionmeccanica|piccola e media industria metalmeccanica", "artigianato"),
            RegolaRiconoscimento.daColonne("METALMECCANICO_ARTIGIANATO", "C030",
                    "metalmeccanica artigianato|metalmeccanici artigiani|area meccanica", "confapi|unionmeccanica"),
            RegolaRiconoscimento.daColonne("TURISMO", "H052", "turismo|pubblici esercizi",
                    "confesercenti|commercio|terziario"),
            RegolaRiconoscimento.daColonne("ALTRO_13", null, null, null)));

    @ParameterizedTest
    @ValueSource(strings = {
            "Metalmeccanici imp.",
            "CONTRATTO DI LAVORO Metalmeccanici imp.",
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
        var senzaEsclusioni = new RiconoscitoreCcnl(List.of(
                RegolaRiconoscimento.daColonne("COMMERCIO", null, "commercio", null),
                RegolaRiconoscimento.daColonne("TURISMO", null, "turismo", null)));
        assertThat(senzaEsclusioni.riconosci("Commercio e turismo")).isEmpty();
    }

    @Test
    void leParoleEscluseEliminanoIlCandidato() {
        // con entrambi i fratelli esclusi a vicenda non resta nessun candidato
        assertThat(RICONOSCITORE.riconosci("Commercio e turismo")).isEmpty();
        assertThat(RICONOSCITORE.riconosci("Turismo Confesercenti")).isEmpty();
        assertThat(RICONOSCITORE.riconosci("Commercio dirigenti")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "Metalmeccanica artigianato;METALMECCANICO_ARTIGIANATO",
            "Area meccanica;METALMECCANICO_ARTIGIANATO",
            "Metalmeccanica PMI Unionmeccanica;METALMECCANICO_PMI",
            "Commercio Confesercenti;COMMERCIO_CONFESERCENTI",
            "Turismo Confcommercio;TURISMO"})
    void laParolaEsclusaDiUnCcnlLasciaIlFratelloComeUnicoCandidato(String testo, String atteso) {
        assertThat(RICONOSCITORE.riconosci(testo)).contains(atteso);
    }

    @Test
    void unaSiglaAmbiguaEscludeSenzaRiconoscereIlFratello() {
        // "p.ind" (piccola industria) esclude l'industria ma non basta per la PMI Confapi (esiste anche Confimi)
        assertThat(RICONOSCITORE.riconosci("Metalmeccanici imp. (p.ind)")).isEmpty();
        assertThat(RICONOSCITORE.riconosci("Metalmeccanici imp. (p.ind) CNEL C011")).isEmpty();
        assertThat(RICONOSCITORE.riconosci("Metalmeccanici imp. (p.ind) CNEL C018")).contains("METALMECCANICO_PMI");
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
        assertThat(RICONOSCITORE.riconosci("Metalmeccanica artigianato CNEL C018")).isEmpty();
        assertThat(RICONOSCITORE.riconosci("C011 H052")).isEmpty();
    }

    @Test
    void codiceCnelNonInTabellaNonImpedisceIlRiconoscimentoPerParoleChiave() {
        assertThat(RICONOSCITORE.riconosci("Metalmeccanici imp. CNEL C099")).contains("METALMECCANICO_INDUSTRIA");
        assertThat(RICONOSCITORE.riconosci("CNEL C099")).isEmpty();
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
