package com.fcusumano.sam.web.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class GestoreErroriTest {

    @Test
    void neiLogSoloClassiEPuntiDiOrigineMaiIMessaggi() {
        var causa = new SQLException("Violazione di vincolo univoco: RSSMRA80A01H501U");
        var errore = new IllegalStateException("Salvataggio di Mario Rossi fallito", causa);

        var descrizione = GestoreErrori.senzaMessaggi(errore);

        assertThat(descrizione)
                .startsWith("java.lang.IllegalStateException at ")
                .contains(" <- causato da java.sql.SQLException at ")
                .contains(GestoreErroriTest.class.getName())
                .doesNotContain("RSSMRA80A01H501U")
                .doesNotContain("Mario Rossi");
    }
}
