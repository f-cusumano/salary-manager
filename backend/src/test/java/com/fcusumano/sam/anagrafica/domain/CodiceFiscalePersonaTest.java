package com.fcusumano.sam.anagrafica.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CodiceFiscalePersonaTest {

    @Test
    void normalizzaInMaiuscoloSenzaSpazi() {
        assertThat(CodiceFiscalePersona.normalizza(" rssmra 80a01 h501u ")).isEqualTo("RSSMRA80A01H501U");
        assertThat(CodiceFiscalePersona.normalizza("  ")).isNull();
        assertThat(CodiceFiscalePersona.normalizza(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"RSSMRA80A01H501U", "VRDGPP85M41F205X", "BNCLRA90E50L219H", "RSSMRA75C12F205K"})
    void riconosceCodiciValidi(String codice) {
        assertThat(CodiceFiscalePersona.formalmenteValido(codice)).isTrue();
    }

    @Test
    void accettaLeOmocodie() {
        // ultima cifra del comune sostituita dalla lettera M (= 1)
        assertThat(CodiceFiscalePersona.formalmenteValido("RSSMRA80A01H50MM")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "RSSMRA80A01H501A", // carattere di controllo errato
            "RSSMRA80Z01H501U", // mese inesistente
            "RSSMRA80A01H501", // troppo corto
            "12345678901", // codice numerico provvisorio
            "rssmra80a01h501u" // non normalizzato
    })
    void segnalaICodiciNonValidi(String codice) {
        assertThat(CodiceFiscalePersona.formalmenteValido(codice)).isFalse();
    }
}
