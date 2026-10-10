package com.fcusumano.sam.calcoli.domain;

import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.PARAMETRI;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.euro;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.fineRapporto;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.ordinario;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.tredicesima;
import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.calcoli.RiepilogoRapporto.Ratei;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.SaldoRateo;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.ElementiRetributivi;
import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.shared.UnitaMisura;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalcoloRateiTest {

    private static Ratei ratei(CedolinoDiProva... cedolini) {
        return CalcoloRatei.calcola(
                CedoliniOrdinati.di(List.of(cedolini).stream().map(CedolinoDiProva::build).toList()), PARAMETRI);
    }

    private static ElementiRetributivi conPaghe(String totale, String pagaOraria, String pagaGiornaliera,
                                                String divisoreOrario, String divisoreGiornaliero) {
        return new ElementiRetributivi(null, null, null, null, null, null, totale == null ? null : euro(totale),
                totale == null ? null : euro(totale),
                pagaOraria == null ? null : euro(pagaOraria), pagaGiornaliera == null ? null : euro(pagaGiornaliera),
                divisoreOrario == null ? null : euro(divisoreOrario),
                divisoreGiornaliero == null ? null : euro(divisoreGiornaliero));
    }

    private static SaldoRateo saldo(Ratei ratei, TipoRateo tipo, UnitaMisura unita) {
        return ratei.saldi().stream().filter(s -> s.tipo() == tipo && s.unita() == unita).findFirst().orElseThrow();
    }

    @Test
    void saldiDellUltimoCedolinoCheLiRiportaConPagaStampata() {
        var r = ratei(
                ordinario(2024, 1).rateo(TipoRateo.FERIE, UnitaMisura.GIORNI, "17.16671")
                        .rateo(TipoRateo.ROL, UnitaMisura.ORE, "76"),
                ordinario(2024, 2).elementi(conPaghe("2325.00", "13.43931", "89.42308", null, null))
                        .rateo(TipoRateo.FERIE, UnitaMisura.GIORNI, "19.33338")
                        .rateo(TipoRateo.ROL, UnitaMisura.ORE, "82"),
                tredicesima(2024));

        assertThat(r.riferimento()).isEqualTo(new CedolinoId(20240200));
        assertThat(r.saldi()).hasSize(2);
        var ferie = saldo(r, TipoRateo.FERIE, UnitaMisura.GIORNI);
        assertThat(ferie.saldo()).isEqualByComparingTo("19.33338");
        // 19,33338 x 89,42308 = 1728,85 (arrotondato al centesimo)
        assertThat(ferie.valoreStimato()).isEqualByComparingTo("1728.85");
        assertThat(ferie.nota()).isEqualTo("Giorni x paga giornaliera stampata sul cedolino.");
        var rol = saldo(r, TipoRateo.ROL, UnitaMisura.ORE);
        assertThat(rol.valoreStimato()).isEqualByComparingTo("1102.02");
        assertThat(rol.nota()).isEqualTo("Ore x paga oraria stampata sul cedolino.");
    }

    @Test
    void senzaPagaStampataLordoMensileSuDivisoriStampati() {
        var r = ratei(ordinario(2025, 2).elementi(conPaghe("2600.00", null, null, "168", "26"))
                .rateo(TipoRateo.FERIE, UnitaMisura.GIORNI, "10")
                .rateo(TipoRateo.EX_FESTIVITA, UnitaMisura.ORE, "16"));

        var ferie = saldo(r, TipoRateo.FERIE, UnitaMisura.GIORNI);
        assertThat(ferie.valoreStimato()).isEqualByComparingTo("1000.00");
        assertThat(ferie.nota()).isEqualTo("Giorni x lordo mensile / divisore giornaliero 26 (stampato sul cedolino).");
        var exFest = saldo(r, TipoRateo.EX_FESTIVITA, UnitaMisura.ORE);
        // 2600 / 168 = 15,47619 x 16 = 247,62
        assertThat(exFest.valoreStimato()).isEqualByComparingTo("247.62");
    }

    @Test
    void senzaDivisoriStampatiUsaQuelliConfigurati() {
        var r = ratei(ordinario(2025, 2).base("2595.00")
                .rateo(TipoRateo.FERIE, UnitaMisura.GIORNI, "2")
                .rateo(TipoRateo.PERMESSI, UnitaMisura.ORE, "10"));

        // 2595 / 26 = 99,80769 x 2 = 199,62
        assertThat(saldo(r, TipoRateo.FERIE, UnitaMisura.GIORNI).valoreStimato()).isEqualByComparingTo("199.62");
        assertThat(saldo(r, TipoRateo.FERIE, UnitaMisura.GIORNI).nota()).contains("26 (da configurazione)");
        // 2595 / 173 = 15 x 10 = 150
        assertThat(saldo(r, TipoRateo.PERMESSI, UnitaMisura.ORE).valoreStimato()).isEqualByComparingTo("150.00");
        assertThat(saldo(r, TipoRateo.PERMESSI, UnitaMisura.ORE).nota()).contains("173 (da configurazione)");
    }

    @Test
    void pagaEdElementiPresiDallUltimaOrdinariaSeIlCedolinoDeiRateiNonLiRiporta() {
        var r = ratei(
                ordinario(2024, 6).elementi(conPaghe("2325.00", "13.43931", "89.42308", null, null))
                        .rateo(TipoRateo.FERIE, UnitaMisura.GIORNI, "24.91673"),
                fineRapporto(1, 2024, 7).rateo(TipoRateo.FERIE, UnitaMisura.GIORNI, "3"));

        assertThat(r.riferimento()).isEqualTo(new CedolinoId(20240751));
        assertThat(saldo(r, TipoRateo.FERIE, UnitaMisura.GIORNI).valoreStimato()).isEqualByComparingTo("268.27");
    }

    @Test
    void giorniConSolaPagaOrariaESenzaLordoUsanoLeOreGiornaliere() {
        var r = ratei(ordinario(2024, 6).elementi(conPaghe(null, "15.00", null, null, null))
                .rateo(TipoRateo.FERIE, UnitaMisura.GIORNI, "2"));

        var ferie = saldo(r, TipoRateo.FERIE, UnitaMisura.GIORNI);
        assertThat(ferie.valoreStimato()).isEqualByComparingTo("240.00");
        assertThat(ferie.nota()).contains("8 ore");
    }

    @Test
    void senzaPagaNeLordoValoreNonStimabile() {
        var r = ratei(ordinario(2024, 6).rateo(TipoRateo.FERIE, UnitaMisura.GIORNI, "5")
                .rateo(TipoRateo.ROL, UnitaMisura.ORE, "20"));

        assertThat(r.saldi()).extracting(SaldoRateo::valoreStimato).containsOnlyNulls();
        assertThat(r.saldi()).extracting(SaldoRateo::nota).containsOnlyNulls();
    }

    @Test
    void stessoTipoInGiorniEOreSonoDueSaldiIDuplicatiNo() {
        var r = ratei(ordinario(2024, 6).elementi(conPaghe("2000.00", "12.00", "80.00", null, null))
                .rateo(TipoRateo.FERIE, UnitaMisura.GIORNI, "5")
                .rateo(TipoRateo.FERIE, UnitaMisura.ORE, "40")
                .rateo(TipoRateo.FERIE, UnitaMisura.GIORNI, "99")
                .rateo(TipoRateo.BANCA_ORE, UnitaMisura.ORE, null));

        assertThat(r.saldi()).extracting(SaldoRateo::tipo, SaldoRateo::unita).containsExactly(
                org.assertj.core.groups.Tuple.tuple(TipoRateo.FERIE, UnitaMisura.GIORNI),
                org.assertj.core.groups.Tuple.tuple(TipoRateo.FERIE, UnitaMisura.ORE));
        assertThat(saldo(r, TipoRateo.FERIE, UnitaMisura.GIORNI).saldo()).isEqualByComparingTo("5");
        assertThat(saldo(r, TipoRateo.FERIE, UnitaMisura.ORE).valoreStimato()).isEqualByComparingTo("480.00");
    }

    @Test
    void saldoNegativoDaUnValoreNegativo() {
        var r = ratei(ordinario(2024, 6).elementi(conPaghe("2000.00", null, "80.00", null, null))
                .rateo(TipoRateo.FERIE, UnitaMisura.GIORNI, "-1.5"));

        assertThat(saldo(r, TipoRateo.FERIE, UnitaMisura.GIORNI).valoreStimato()).isEqualByComparingTo("-120.00");
    }

    @Test
    void nessunCedolinoConRatei() {
        var r = ratei(ordinario(2024, 6), ordinario(2024, 7).rateo(TipoRateo.FERIE, UnitaMisura.GIORNI, null));

        assertThat(r.riferimento()).isNull();
        assertThat(r.saldi()).isEmpty();
    }
}
