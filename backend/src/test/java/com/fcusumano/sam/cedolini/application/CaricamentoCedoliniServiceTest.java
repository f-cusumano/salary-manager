package com.fcusumano.sam.cedolini.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CaricamentoCedoliniServiceTest {

    @Test
    void ilNomeVisualizzatoPerdeIlPercorsoENonEMaiVuoto() {
        assertThat(CaricamentoCedoliniService.nomeVisualizzato("C:\\Documenti\\buste\\gennaio.pdf")).isEqualTo("gennaio.pdf");
        assertThat(CaricamentoCedoliniService.nomeVisualizzato("buste/febbraio.pdf")).isEqualTo("febbraio.pdf");
        assertThat(CaricamentoCedoliniService.nomeVisualizzato(null)).isEqualTo(CaricamentoCedoliniService.NOME_FILE_PREDEFINITO);
        assertThat(CaricamentoCedoliniService.nomeVisualizzato("cartella/ ")).isEqualTo(CaricamentoCedoliniService.NOME_FILE_PREDEFINITO);
    }

    @Test
    void unNomeTroppoLungoConservaLaParteFinale() {
        var nome = "x".repeat(300) + ".pdf";
        var visualizzato = CaricamentoCedoliniService.nomeVisualizzato(nome);
        assertThat(visualizzato).hasSize(CaricamentoCedoliniService.LUNGHEZZA_NOME_FILE).endsWith(".pdf");
    }

    @Test
    void hashSha256Esadecimale() {
        assertThat(CaricamentoCedoliniService.sha256("abc".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
