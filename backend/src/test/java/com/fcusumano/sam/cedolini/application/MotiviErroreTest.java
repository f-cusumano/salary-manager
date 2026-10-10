package com.fcusumano.sam.cedolini.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.cedolini.MotivoErrore;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException.Motivo;
import com.fcusumano.sam.cedolini.application.estrazione.PdfNonLeggibileException;
import com.fcusumano.sam.cedolini.application.estrazione.PdfNonLeggibileException.Causa;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MotiviErroreTest {

    @ParameterizedTest
    @CsvSource({
            "PROTETTO_DA_PASSWORD, PDF_PROTETTO",
            "TROPPO_GRANDE, PDF_TROPPO_GRANDE",
            "TROPPE_PAGINE, PDF_TROPPE_PAGINE",
            "NON_PDF, PDF_NON_VALIDO",
            "DANNEGGIATO, PDF_NON_VALIDO"})
    void laCausaDelPdfNonLeggibilePrevaleSulMotivoGenerico(Causa causa, MotivoErrore atteso) {
        assertThat(MotiviErrore.di(new PdfNonLeggibileException(causa, "PDF non leggibile"))).isEqualTo(atteso);
    }

    @ParameterizedTest
    @CsvSource({
            "PDF_SENZA_TESTO, PDF_SENZA_TESTO",
            "LAYOUT_NON_RICONOSCIUTO, LAYOUT_NON_RICONOSCIUTO",
            "PIU_CEDOLINI, PIU_CEDOLINI",
            "SERVIZIO_NON_DISPONIBILE, SERVIZIO_NON_DISPONIBILE",
            "CONFIGURAZIONE, SERVIZIO_NON_DISPONIBILE",
            "DOCUMENTO_NON_VALIDO, LETTURA_NON_RIUSCITA",
            "OUTPUT_NON_VALIDO, LETTURA_NON_RIUSCITA",
            "RIFIUTO, LETTURA_NON_RIUSCITA",
            "LIMITE_TOKEN, LETTURA_NON_RIUSCITA"})
    void ogniMotivoDiEstrazioneHaIlSuoMotivoDiErrore(Motivo motivo, MotivoErrore atteso) {
        assertThat(MotiviErrore.di(new EstrazioneCedolinoException(motivo, "Lettura non riuscita"))).isEqualTo(atteso);
    }

    @Test
    void ogniMotivoEOgniCausaSonoMappati() {
        var mappati = EnumSet.noneOf(MotivoErrore.class);
        Arrays.stream(Motivo.values())
                .forEach(m -> mappati.add(MotiviErrore.di(new EstrazioneCedolinoException(m, "Errore"))));
        Arrays.stream(Causa.values())
                .forEach(c -> mappati.add(MotiviErrore.di(new PdfNonLeggibileException(c, "Errore"))));

        // i motivi che non vengono dalla lettura sono assegnati dall'elaborazione o dal ripristino all'avvio
        assertThat(EnumSet.complementOf(mappati)).containsExactlyInAnyOrder(MotivoErrore.DATI_INCOMPLETI,
                MotivoErrore.INTERROTTO, MotivoErrore.ERRORE_IMPREVISTO);
    }

    @Test
    void ilPdfNonSiConservaSoloPerIFileNonCompletabili() {
        var nonConservati = Arrays.stream(MotivoErrore.values()).filter(m -> !m.conservaPdf())
                .collect(Collectors.toSet());

        assertThat(nonConservati).containsExactlyInAnyOrder(MotivoErrore.PDF_NON_VALIDO,
                MotivoErrore.PDF_TROPPE_PAGINE, MotivoErrore.PIU_CEDOLINI);
    }

    @Test
    void ogniMotivoHaUnaDescrizioneBreve() {
        assertThat(MotivoErrore.PDF_PROTETTO.descrizione()).isEqualTo("Protetto da password");
        assertThat(MotivoErrore.PDF_NON_VALIDO.descrizione()).isEqualTo("File danneggiato o non PDF");
        assertThat(MotivoErrore.PIU_CEDOLINI.descrizione()).isEqualTo("Più cedolini nello stesso PDF");
        assertThat(MotivoErrore.values()).allSatisfy(m -> assertThat(m.descrizione()).isNotBlank().hasSizeLessThan(40));
    }
}
