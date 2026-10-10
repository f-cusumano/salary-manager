package com.fcusumano.sam.anagrafica.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IdentificativiAziendaTest {

    @Test
    void partitaIvaSenzaSeparatoriNePrefissoPaese() {
        assertThat(IdentificativiAzienda.normalizzaPartitaIva("IT 012.345.678-90")).isEqualTo("01234567890");
        assertThat(IdentificativiAzienda.normalizzaPartitaIva("01234567890")).isEqualTo("01234567890");
        assertThat(IdentificativiAzienda.normalizzaPartitaIva("0123456789")).isNull();
        assertThat(IdentificativiAzienda.normalizzaPartitaIva(" ")).isNull();
    }

    @Test
    void codiceFiscaleAziendaNumericoOAlfanumerico() {
        assertThat(IdentificativiAzienda.normalizzaCodiceFiscale(" 01234567890 ")).isEqualTo("01234567890");
        assertThat(IdentificativiAzienda.normalizzaCodiceFiscale("rssmra80a01h501u")).isEqualTo("RSSMRA80A01H501U");
        assertThat(IdentificativiAzienda.normalizzaCodiceFiscale("RSSMRA80A01H501U-EXTRA")).isNull();
        assertThat(IdentificativiAzienda.codiceFiscaleNumerico("01234567890")).isTrue();
        assertThat(IdentificativiAzienda.codiceFiscaleNumerico("RSSMRA80A01H501U")).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "1234567890;1234567890",
            "12 3456 7890;1234567890",
            "1234567890/12345678;1234567890",
            // INPS e INAIL concatenati senza separatori: matricola INPS = prime 10 cifre
            "123456789012345678;1234567890",
            // INAIL stampato prima, separato: si sceglie il gruppo di 10 cifre
            "INAIL 12345678 INPS 1234567890;1234567890",
            "INPS: 1234567890 - INAIL: 98765432/11;1234567890",
            "12345;12345",
            "PA-X;PAX"})
    void matricolaInpsNormalizzata(String stampata, String attesa) {
        assertThat(IdentificativiAzienda.normalizzaPosizioneInps(stampata)).isEqualTo(attesa);
    }

    @Test
    void posizioneInpsVuota() {
        assertThat(IdentificativiAzienda.normalizzaPosizioneInps(" - ")).isNull();
        assertThat(IdentificativiAzienda.normalizzaPosizioneInps(null)).isNull();
    }
}
