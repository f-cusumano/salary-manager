package com.fcusumano.sam.cedolini.adapter.locale;

import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.cedolini.CategoriaVoce;
import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.shared.UnitaMisura;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Regole comuni ai layout: lettura dei valori stampati e classificazione (dati inventati). */
class RegoleLetturaTest {

    @Test
    void leggeGliImportiStampati() {
        assertThat(Valori.importo("1.234,56")).isTrue();
        assertThat(Valori.importo("1234,56")).isTrue();
        assertThat(Valori.importo("305,73-")).isTrue();
        assertThat(Valori.importo("1.234")).isFalse();
        assertThat(Valori.importo("12/2024")).isFalse();
        assertThat(Valori.leggiImporto("1.234,56")).contains(new BigDecimal("1234.56"));
        assertThat(Valori.leggiImporto("305,73-")).contains(new BigDecimal("-305.73"));
        assertThat(Valori.leggiImporto("-0,01")).contains(new BigDecimal("-0.01"));
        assertThat(Valori.leggiNumero("26")).contains(new BigDecimal("26"));
        assertThat(Valori.leggiNumero("N.")).isEmpty();
    }

    @Test
    void leggeDateEMesi() {
        assertThat(Valori.leggiData("15/01/1985")).contains(LocalDate.of(1985, 1, 15));
        assertThat(Valori.leggiData("6-06-2022")).contains(LocalDate.of(2022, 6, 6));
        assertThat(Valori.leggiData("31/02/2022")).isEmpty();
        assertThat(Valori.leggiDataCompatta("16032020")).contains(LocalDate.of(2020, 3, 16));
        assertThat(Valori.leggiMeseAnno("3/2024")).contains(YearMonth.of(2024, 3));
        assertThat(Valori.leggiMeseAnno("092025")).contains(YearMonth.of(2025, 9));
        assertThat(Valori.leggiMeseAnno("13/2024")).isEmpty();
        assertThat(Valori.mese("Dicembre")).contains(12);
        assertThat(Valori.mese("Mensilita")).isEmpty();
        assertThat(Valori.codiceFiscale("RSSMRA80A01H501U")).isTrue();
        assertThat(Valori.codiceFiscale("01234567897")).isFalse();
    }

    @Test
    void divideCognomeENomeConIlCodiceFiscale() {
        // doppio cognome senza particella: lo risolve solo il codice fiscale
        assertThat(Classificazione.cognomeNome("ROSSI BIANCHI MARIO", "RSSMRA80A01H501U"))
                .containsExactly("ROSSI BIANCHI", "MARIO");
        assertThat(Classificazione.cognomeNome("ROSSI BIANCHI MARIO", null)).containsExactly("ROSSI", "BIANCHI MARIO");
        // particelle del cognome
        assertThat(Classificazione.cognomeNome("DE LUCA MARIO", null)).containsExactly("DE LUCA", "MARIO");
        assertThat(Classificazione.cognomeNome("DE LUCA MARIO", "DLCMRA80A01H501X")).containsExactly("DE LUCA", "MARIO");
        assertThat(Classificazione.codiceNome("MARIA ANNA")).isEqualTo("MNN");
        assertThat(Classificazione.codiceCognome("FO")).isEqualTo("FOX");
    }

    @Test
    void classificaLeVociPerDescrizioneEColonna() {
        var importo = new BigDecimal("10.00");
        assertThat(Classificazione.categoria("Retribuzione ordinaria", importo, null, true, true))
                .isEqualTo(CategoriaVoce.COMPETENZA);
        assertThat(Classificazione.categoria("Contributo IVS", null, importo, null, true))
                .isEqualTo(CategoriaVoce.TRATTENUTA_PREVIDENZIALE);
        assertThat(Classificazione.categoria("Ritenute IRPEF", null, importo, null, false))
                .isEqualTo(CategoriaVoce.TRATTENUTA_FISCALE);
        assertThat(Classificazione.categoria("Quota sindacale", null, importo, true, false))
                .isEqualTo(CategoriaVoce.ALTRA_TRATTENUTA);
        assertThat(Classificazione.categoria("Rimborso chilometrico", importo, null, null, true))
                .isEqualTo(CategoriaVoce.NON_RETRIBUTIVA);
        assertThat(Classificazione.categoria("Buoni pasto", importo, null, false, true))
                .isEqualTo(CategoriaVoce.INFORMATIVA);
        assertThat(Classificazione.categoria("Ferie godute", null, null, true, true))
                .isEqualTo(CategoriaVoce.PRESENZA);
        assertThat(Classificazione.categoria("Imponibile IRPEF", null, null, null, false))
                .isEqualTo(CategoriaVoce.INFORMATIVA);
    }

    @Test
    void riconosceRateiEUnita() {
        assertThat(Classificazione.tipoRateo("FERIE")).contains(TipoRateo.FERIE);
        assertThat(Classificazione.tipoRateo("PERMESSI (R.O.L.)")).contains(TipoRateo.ROL);
        assertThat(Classificazione.tipoRateo("Permessi")).contains(TipoRateo.PERMESSI);
        assertThat(Classificazione.tipoRateo("EX FESTIVITA'")).contains(TipoRateo.EX_FESTIVITA);
        assertThat(Classificazione.tipoRateo("Banca ore")).contains(TipoRateo.BANCA_ORE);
        assertThat(Classificazione.tipoRateo("TOTALE")).isEqualTo(Optional.empty());
        assertThat(Classificazione.unitaRateo("(GIORNI)")).contains(UnitaMisura.GIORNI);
        assertThat(Classificazione.unitaRateo("GG.")).contains(UnitaMisura.GIORNI);
        assertThat(Classificazione.unitaRateo("Ore")).contains(UnitaMisura.ORE);
        assertThat(Classificazione.unitaVoce("GG")).isEqualTo("giorni");
        assertThat(Classificazione.unitaVoce("HH")).isEqualTo("ore");
        assertThat(Classificazione.unitaVoce("%")).isEqualTo("%");
        assertThat(Classificazione.unitaVoce(null)).isNull();
    }

    @Test
    void riconosceTredicesimaEQuattordicesima() {
        assertThat(Classificazione.tredicesima("13^ MENSILITA' 2024")).isTrue();
        assertThat(Classificazione.tredicesima("Tredicesima mensilita'")).isTrue();
        assertThat(Classificazione.tredicesima("Rateo 13ma")).isFalse();
        assertThat(Classificazione.quattordicesima("14a mensilita'")).isTrue();
    }
}
