package com.fcusumano.sam.anagrafica.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RagioneSocialeTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Alfa Software S.r.l.",
            "ALFA SOFTWARE SRL",
            "Alfa Software - SRL",
            "ALFA SOFTWARE S.R.L.",
            "Alfa  Software   S. R. L.",
            "ALFA SOFTWARE S.R.L.S.U.",
            "Alfa Software S.r.l. S.U.",
            "Alfa Software S.r.l. a socio unico",
            "Alfa Software S.r.l. unipersonale",
            "Alfa Software S.R.L.S.",
            "Alfa Software società a responsabilità limitata",
            "ALFA SOFTWARE SRL IN LIQUIDAZIONE"})
    void formeDiverseDellaStessaSrl(String ragioneSociale) {
        assertThat(RagioneSociale.normalizza(ragioneSociale)).isEqualTo("ALFA SOFTWARE SRL");
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "Beta Consulting S.p.A.;BETA CONSULTING SPA",
            "BETA CONSULTING SPA;BETA CONSULTING SPA",
            "Beta Consulting Società per Azioni;BETA CONSULTING SPA",
            "Gamma Servizi Soc. Coop. a r.l.;GAMMA SERVIZI SCARL",
            "Gamma Servizi S.C.A.R.L.;GAMMA SERVIZI SCARL",
            "Delta di Rossi & C. S.a.s.;DELTA DI ROSSI E C SAS",
            "Delta di Rossi e C. sas;DELTA DI ROSSI E C SAS",
            "Caffè dell'Arte S.n.c.;CAFFE DELL ARTE SNC"})
    void formeSocietarieUniformate(String ragioneSociale, String attesa) {
        assertThat(RagioneSociale.normalizza(ragioneSociale)).isEqualTo(attesa);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "...", "-"})
    void ragioneSocialeVuota(String ragioneSociale) {
        assertThat(RagioneSociale.normalizza(ragioneSociale)).isNull();
    }
}
