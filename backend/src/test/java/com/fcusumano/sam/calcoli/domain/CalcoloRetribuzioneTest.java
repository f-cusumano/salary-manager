package com.fcusumano.sam.calcoli.domain;

import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.COMMERCIO;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.METALMECCANICO;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.PARAMETRI;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.contesto;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.euro;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.fineRapporto;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.ordinario;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.quattordicesima;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.tredicesima;
import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.calcoli.RiepilogoRapporto.Retribuzione;
import com.fcusumano.sam.cedolini.CategoriaVoce;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.ElementiRetributivi;
import com.fcusumano.sam.ccnl.Ccnl;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalcoloRetribuzioneTest {

    private static Retribuzione retribuzione(Ccnl ccnl, CedolinoDiProva... cedolini) {
        return CalcoloRetribuzione.calcola(contesto(null, null, ccnl),
                CedoliniOrdinati.di(List.of(cedolini).stream().map(CedolinoDiProva::build).toList()), PARAMETRI);
    }

    @Test
    void ralDalTotaleElementiFissiPerMensilitaDelCcnl() {
        var r = retribuzione(METALMECCANICO, ordinario(2024, 1).scomposti("2123.50", "176.50", "25.00"));

        assertThat(r.lordoMensile()).isEqualByComparingTo("2325.00");
        assertThat(r.mensilita()).isEqualTo(13);
        assertThat(r.ral()).isEqualByComparingTo("30225.00");
        assertThat(r.minimo()).isEqualByComparingTo("2123.50");
        assertThat(r.superminimo()).isEqualByComparingTo("176.50");
        assertThat(r.importoScatti()).isEqualByComparingTo("25.00");
        assertThat(r.contingenza()).isNull();
        assertThat(r.altriElementiFissi()).isNull();
        assertThat(r.retribuzioneBase()).isNull();
        assertThat(r.stimata()).isFalse();
        assertThat(r.nota()).isNull();
        assertThat(r.riferimento()).isEqualTo(new CedolinoId(20240100));
    }

    @Test
    void ccnlCon14Mensilita() {
        var r = retribuzione(COMMERCIO, ordinario(2024, 1).base("2000.00"));

        assertThat(r.mensilita()).isEqualTo(14);
        assertThat(r.ral()).isEqualByComparingTo("28000.00");
    }

    @Test
    void sommaDegliElementiSeIlTotaleNonEStampato() {
        var elementi = new ElementiRetributivi(euro("1800.00"), euro("530.00"), euro("200.00"), Boolean.FALSE,
                euro("40.00"), euro("10.33"), null, null, null, null, null, null);
        var r = retribuzione(METALMECCANICO, ordinario(2024, 1).elementi(elementi));

        assertThat(r.lordoMensile()).isEqualByComparingTo("2580.33");
        assertThat(r.ral()).isEqualByComparingTo("33544.29");
        assertThat(r.contingenza()).isEqualByComparingTo("530.00");
        assertThat(r.altriElementiFissi()).isEqualByComparingTo("10.33");
    }

    @Test
    void soloRetribuzioneBaseSenzaCcnlRalStimataSu13Mensilita() {
        var r = retribuzione(null, ordinario(2025, 2).base("2600.00"));

        assertThat(r.lordoMensile()).isEqualByComparingTo("2600.00");
        assertThat(r.retribuzioneBase()).isEqualByComparingTo("2600.00");
        assertThat(r.minimo()).isNull();
        assertThat(r.superminimo()).isNull();
        assertThat(r.mensilita()).isEqualTo(13);
        assertThat(r.ral()).isEqualByComparingTo("33800.00");
        assertThat(r.stimata()).isTrue();
        assertThat(r.nota()).contains("CCNL non indicato");
    }

    @Test
    void senzaCcnlConQuattordicesimaNeiCedoliniRalStimataSu14Mensilita() {
        var r = retribuzione(null, ordinario(2024, 6).base("2600.00"), quattordicesima(2024).base("2600.00"),
                ordinario(2024, 7).base("2600.00"));

        assertThat(r.mensilita()).isEqualTo(14);
        assertThat(r.ral()).isEqualByComparingTo("36400.00");
        assertThat(r.stimata()).isTrue();
        assertThat(r.nota()).isEqualTo(CalcoloRetribuzione.NOTA_CCNL_NON_INDICATO_QUATTORDICESIMA);
    }

    @Test
    void senzaCcnlConQuattordicesimaPagataInsiemeAllaMensilitaRalSu14Mensilita() {
        var r = retribuzione(null, ordinario(2024, 6).base("2600.00").conQuattordicesima(),
                ordinario(2024, 7).base("2600.00"));

        assertThat(r.mensilita()).isEqualTo(14);
        assertThat(r.nota()).isEqualTo(CalcoloRetribuzione.NOTA_CCNL_NON_INDICATO_QUATTORDICESIMA);
    }

    @Test
    void conCcnlLeMensilitaRestanoQuelleDelContrattoAncheConQuattordicesimaNeiCedolini() {
        var r = retribuzione(METALMECCANICO, ordinario(2024, 6).base("2600.00"), quattordicesima(2024).base("2600.00"));

        assertThat(r.mensilita()).isEqualTo(13);
        assertThat(r.stimata()).isFalse();
    }

    @Test
    void retribuzioneBaseSenzaTotaleStampato() {
        var elementi = new ElementiRetributivi(null, null, null, null, null, null, euro("2100.00"), null,
                null, null, null, null);
        var r = retribuzione(METALMECCANICO, ordinario(2024, 1).elementi(elementi));

        assertThat(r.lordoMensile()).isEqualByComparingTo("2100.00");
        assertThat(r.retribuzioneBase()).isEqualByComparingTo("2100.00");
    }

    @Test
    void escludeVociVariabiliRimborsiStraordinariEBuoniPasto() {
        var r = retribuzione(METALMECCANICO, ordinario(2024, 1).scomposti("2000.00", "300.00", null)
                .voce("Retribuzione ordinaria", CategoriaVoce.COMPETENZA, "2300.00")
                .voce("Straordinario 25%", CategoriaVoce.COMPETENZA, "350.00")
                .voce("Premio di produzione", CategoriaVoce.COMPETENZA, "500.00")
                .voce("Rimborso chilometrico", CategoriaVoce.NON_RETRIBUTIVA, "120.00")
                .voce("Buoni pasto", CategoriaVoce.NON_RETRIBUTIVA, "160.00")
                .buoniPasto());

        assertThat(r.lordoMensile()).isEqualByComparingTo("2300.00");
        assertThat(r.ral()).isEqualByComparingTo("29900.00");
    }

    @Test
    void usaLUltimaMensilitaOrdinariaNonTredicesimaNeFineRapporto() {
        var r = retribuzione(METALMECCANICO,
                ordinario(2024, 5).scomposti("2000.00", "300.00", null),
                ordinario(2024, 6).scomposti("2100.00", "300.00", null),
                tredicesima(2024).scomposti("9999.00", null, null),
                fineRapporto(1, 2024, 7).scomposti("8888.00", null, null));

        assertThat(r.lordoMensile()).isEqualByComparingTo("2400.00");
        assertThat(r.riferimento()).isEqualTo(new CedolinoId(20240600));
    }

    @Test
    void ultimaOrdinariaSenzaElementiUsaLaPrecedenteConNota() {
        var r = retribuzione(METALMECCANICO,
                ordinario(2024, 5).scomposti("2000.00", "300.00", null),
                ordinario(2024, 6));

        assertThat(r.lordoMensile()).isEqualByComparingTo("2300.00");
        assertThat(r.riferimento()).isEqualTo(new CedolinoId(20240500));
        assertThat(r.stimata()).isFalse();
        assertThat(r.nota()).isEqualTo("L'ultima mensilità ordinaria non riporta gli elementi fissi: usata Maggio 2024.");
    }

    @Test
    void notaCumulataConCcnlNonIndicato() {
        var r = retribuzione(null, ordinario(2024, 5).base("2000.00"), ordinario(2024, 6));

        assertThat(r.stimata()).isTrue();
        assertThat(r.nota()).contains("Maggio 2024").contains("CCNL non indicato");
    }

    @Test
    void senzaElementiFissiRalNonDeterminabile() {
        var r = retribuzione(METALMECCANICO, ordinario(2024, 5)
                .voce("Retribuzione ordinaria", CategoriaVoce.COMPETENZA, "2300.00"));

        assertThat(r.ral()).isNull();
        assertThat(r.lordoMensile()).isNull();
        assertThat(r.mensilita()).isNull();
        assertThat(r.riferimento()).isNull();
        assertThat(r.stimata()).isFalse();
        assertThat(r.nota()).isEqualTo(CalcoloRetribuzione.NOTA_ELEMENTI_ASSENTI);
    }

    @Test
    void senzaMensilitaOrdinarieRalNonDeterminabile() {
        var r = retribuzione(METALMECCANICO, fineRapporto(1, 2024, 7).scomposti("2000.00", null, null));

        assertThat(r.ral()).isNull();
        assertThat(r.nota()).isEqualTo(CalcoloRetribuzione.NOTA_NESSUN_ORDINARIO);
    }

    @Test
    void ilCambioDiCcnlRicalcolaLaRal() {
        var cedolino = ordinario(2024, 1).base("2000.00");

        assertThat(retribuzione(null, cedolino).ral()).isEqualByComparingTo("26000.00");
        assertThat(retribuzione(METALMECCANICO, cedolino).ral()).isEqualByComparingTo("26000.00");
        assertThat(retribuzione(METALMECCANICO, cedolino).stimata()).isFalse();
        assertThat(retribuzione(COMMERCIO, cedolino).ral()).isEqualByComparingTo("28000.00");
    }
}
