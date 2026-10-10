package com.fcusumano.sam.cedolini.application.estrazione;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/**
 * Mascheramento degli IBAN di qualunque paese con verifica delle cifre di controllo. Gli IBAN sono inventati: le cifre
 * di controllo sono calcolate dal test.
 */
class DatiBancariTest {

    /** IBAN formalmente valido costruito da paese e BBAN inventato (ISO 13616: 98 - resto modulo 97). */
    static String iban(String paese, String bban) {
        var numerico = new StringBuilder();
        (bban + paese + "00").chars().forEach(c -> numerico.append(Character.digit(c, 36)));
        int controllo = 98 - new BigInteger(numerico.toString()).mod(BigInteger.valueOf(97)).intValue();
        return paese + String.format("%02d", controllo) + bban;
    }

    private static String aGruppi(String iban) {
        return iban.replaceAll("(.{4})(?!$)", "$1 ");
    }

    @Test
    void ilCalcoloDelleCifreDiControlloProduceIbanValidi() {
        assertThat(DatiBancari.controlloValido(iban("LT", "7300010123456789"))).isTrue();
        assertThat(DatiBancari.controlloValido("IT60X0542811101000000123456")).isTrue();
    }

    @Test
    void mascheraUnIbanEsteroCompattoOAGruppi() {
        var lituano = iban("LT", "7300010123456789");
        var irlandese = iban("IE", "ABCD99000012345678");

        assertThat(DatiBancari.maschera("Accredito su " + lituano + " del mese"))
                .isEqualTo("Accredito su [dato bancario rimosso] del mese");
        assertThat(DatiBancari.maschera("Conto " + aGruppi(irlandese)))
                .isEqualTo("Conto [dato bancario rimosso]");
        assertThat(DatiBancari.maschera(lituano.toLowerCase())).isEqualTo("[dato bancario rimosso]");
    }

    @Test
    void mascheraUnIbanItalianoAGruppiSeguitoDaAltroTesto() {
        assertThat(DatiBancari.maschera("IBAN IT60 X054 2811 1010 0000 0123 456 presso Banca Gamma"))
                .isEqualTo("IBAN [dato bancario rimosso] presso Banca Gamma");
    }

    @Test
    void mascheraUnIbanConSpaziNonSeparabiliTrattiniPuntiOCaratteriInvisibili() {
        // come incollato da una pagina web o da un PDF: spazio non separabile, spazio stretto, trattini, punti
        for (var separatore : new String[] {"\u00A0", "\u202F", "\u2009", "-", ".", " - ", "\t", "\u200B"}) {
            var iban = String.join(separatore, "IT60", "X054", "2811", "1010", "0000", "0123", "456");
            assertThat(DatiBancari.maschera("Via Roma 1 IBAN " + iban + ", Milano"))
                    .as("separatore U+%04X", (int) separatore.charAt(0))
                    .isEqualTo("Via Roma 1 IBAN [dato bancario rimosso], Milano");
        }
    }

    @Test
    void conISeparatoriNonMascheraDateCodiciOImportiConLaStessaForma() {
        var testo = "Periodo 01-01-2023 IT12-3456-7890 cod. AB12.3456.7890.12 importo 1.234.567,89";

        assertThat(DatiBancari.maschera(testo)).isSameAs(testo);
    }

    @Test
    void mascheraPiuIbanNelloStessoTesto() {
        var lituano = iban("LT", "7300010123456789");
        assertThat(DatiBancari.maschera(lituano + " e IT60X0542811101000000123456"))
                .isEqualTo("[dato bancario rimosso] e [dato bancario rimosso]");
    }

    @Test
    void nonMascheraUnaSequenzaConCifreDiControlloErrate() {
        var valido = iban("LT", "7300010123456789");
        var errato = valido.substring(0, 2) + String.format("%02d", (Integer.parseInt(valido.substring(2, 4)) + 1) % 100)
                + valido.substring(4);

        assertThat(DatiBancari.maschera("Codice " + errato)).isEqualTo("Codice " + errato);
    }

    @Test
    void nonMascheraCodiciFiscaliMatricoleOTestiComuni() {
        var testo = "Mario Rossi RSSMRA80A01H501U matricola INPS 1122334455 voce AB12 straordinario 25%";

        assertThat(DatiBancari.maschera(testo)).isSameAs(testo);
        assertThat(DatiBancari.maschera(null)).isNull();
    }
}
