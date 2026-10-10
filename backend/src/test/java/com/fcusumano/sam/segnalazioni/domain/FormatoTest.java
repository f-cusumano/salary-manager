package com.fcusumano.sam.segnalazioni.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.shared.Competenza;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class FormatoTest {

    @Test
    void importiInFormatoItaliano() {
        assertThat(Formato.euro(new BigDecimal("1234.5"))).isEqualTo("1.234,50 €");
        assertThat(Formato.euro(new BigDecimal("1234567.891"))).isEqualTo("1.234.567,89 €");
        assertThat(Formato.euro(new BigDecimal("0.005"))).isEqualTo("0,01 €");
        assertThat(Formato.euro(new BigDecimal("-12.30000"))).isEqualTo("-12,30 €");
        assertThat(Formato.euro(BigDecimal.ZERO)).isEqualTo("0,00 €");
    }

    @Test
    void mesiEDateConIlMesePerEsteso() {
        assertThat(Formato.mese(YearMonth.of(2024, 1))).isEqualTo("Gennaio 2024");
        assertThat(Formato.data(LocalDate.of(2024, 6, 15))).isEqualTo("15 Giugno 2024");
        assertThat(Formato.fineAnno(2023)).isEqualTo("31/12/2023");
        assertThat(Formato.nomeMese(7)).isEqualTo("Luglio");
    }

    @Test
    void competenze() {
        assertThat(Formato.competenza(new Competenza.Mensilita(2024, 3))).isEqualTo("Marzo 2024");
        assertThat(Formato.competenza(Competenza.Mensilita.tredicesima(2023))).isEqualTo("Tredicesima 2023");
        assertThat(Formato.competenza(Competenza.Mensilita.quattordicesima(2024))).isEqualTo("Quattordicesima 2024");
        assertThat(Formato.competenza(new Competenza.FineRapporto(2, YearMonth.of(2024, 7))))
                .isEqualTo("Fine rapporto 2 – Luglio 2024");
    }
}
