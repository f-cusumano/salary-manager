package com.fcusumano.sam.calcoli.domain;

import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.COMMERCIO;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.METALMECCANICO;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.PARAMETRI;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.PARAMETRI_LIQUIDAZIONE;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.RAPPORTO;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.contesto;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.datiTfr;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.fineRapporto;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.ordinario;
import static com.fcusumano.sam.calcoli.domain.CedolinoDiProva.tredicesima;
import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.calcoli.FonteDato;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.IndicatoriMensilita;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.shared.UnitaMisura;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CalcolatoreRiepilogoTest {

    private static final OffsetDateTime ORA = OffsetDateTime.of(2026, 10, 7, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final LocalDate ASSUNZIONE = LocalDate.of(2021, 9, 1);
    private static final LocalDate CESSAZIONE = LocalDate.of(2024, 6, 15);

    private final CalcolatoreRiepilogo calcolatore = new CalcolatoreRiepilogo(PARAMETRI, PARAMETRI_LIQUIDAZIONE);

    /** Rapporto Alfa inventato: rinnovo a giugno 2023, scatto a settembre 2023, cessazione 15/06/2024, TFR liquidato. */
    private static List<CedolinoNormalizzato> rapportoCessato() {
        var cedolini = new ArrayList<CedolinoNormalizzato>();
        for (int mese = 1; mese <= 12; mese++) {
            var c = ordinario(2023, mese).date(ASSUNZIONE, null)
                    .scomposti(mese < 6 ? "2000.00" : "2123.50", mese < 6 ? "300.00" : "176.50", mese < 9 ? null : "25.00")
                    .scatti(mese < 9 ? 0 : 1, LocalDate.of(mese < 9 ? 2023 : 2025, 9, 1))
                    .rateo(TipoRateo.FERIE, UnitaMisura.GIORNI, "10")
                    .tfr(datiTfr("2410.35", null, null, String.valueOf(150 * mese), "150.00", null, null, null, null, null, null));
            cedolini.add(c.build());
        }
        cedolini.add(tredicesima(2023).scomposti("2123.50", "176.50", "25.00").build());
        for (int mese = 1; mese <= 6; mese++) {
            cedolini.add(ordinario(2024, mese).date(ASSUNZIONE, mese == 6 ? CESSAZIONE : null)
                    .scomposti("2123.50", "176.50", "25.00")
                    .scatti(1, LocalDate.of(2025, 9, 1))
                    .rateo(TipoRateo.FERIE, UnitaMisura.GIORNI, "24.91673")
                    .tfr(datiTfr("4526.32", null, null, String.valueOf(160 * mese), "160.00", null, null, null, null, null, null))
                    .build());
        }
        cedolini.add(fineRapporto(1, 2024, 7).date(ASSUNZIONE, CESSAZIONE)
                .rateo(TipoRateo.FERIE, UnitaMisura.GIORNI, "0")
                .tfr(datiTfr("4526.32", "31.20", "5.30", "883.30", null, null, null, "3000.00", null, "2435.52", "497.89"))
                .build());
        return cedolini;
    }

    @Test
    void riepilogoCompletoDiUnRapportoCessato() {
        var r = calcolatore.calcola(contesto(ASSUNZIONE, CESSAZIONE, METALMECCANICO), rapportoCessato(), ORA);

        assertThat(r.rapportoId()).isEqualTo(RAPPORTO);
        assertThat(r.calcolatoIl()).isEqualTo(ORA);
        assertThat(r.anzianita().dal()).isEqualTo(YearMonth.of(2021, 9));
        assertThat(r.anzianita().al()).isEqualTo(YearMonth.of(2024, 6));
        assertThat(r.anzianita().anni()).isEqualTo(2);
        assertThat(r.anzianita().mesi()).isEqualTo(10);
        assertThat(r.anzianita().inCorso()).isFalse();

        assertThat(r.scatti().attuali()).isEqualTo(1);
        assertThat(r.scatti().massimo()).isEqualTo(5);
        assertThat(r.scatti().prossimoScatto()).as("rapporto cessato").isNull();

        assertThat(r.retribuzione().ral()).isEqualByComparingTo("30225.00");
        assertThat(r.retribuzione().riferimento()).isEqualTo(new CedolinoId(20240600));
        assertThat(r.buoniPasto()).isFalse();

        assertThat(r.ratei().riferimento()).isEqualTo(new CedolinoId(20240751));
        assertThat(r.ratei().saldi()).singleElement().satisfies(s -> {
            assertThat(s.saldo()).isEqualByComparingTo("0");
            assertThat(s.valoreStimato()).isEqualByComparingTo("0");
        });

        assertThat(r.tfr().liquidato()).isTrue();
        assertThat(r.tfr().totaleAccumulato()).isEqualByComparingTo("0");
        assertThat(r.tfr().importoLiquidato()).isEqualByComparingTo("2435.52");

        var scatti = r.mensilita().stream().filter(IndicatoriMensilita::scattoInBusta).map(IndicatoriMensilita::cedolinoId);
        assertThat(scatti).containsExactly(new CedolinoId(20230900));
        assertThat(r.mensilita()).noneMatch(IndicatoriMensilita::aumentoFuoriScatti);
        assertThat(r.mensilita()).hasSize(20);
    }

    @Test
    void deterministicoEIndipendenteDallOrdineDeiCedolini() {
        var contesto = contesto(ASSUNZIONE, CESSAZIONE, METALMECCANICO);
        var primo = calcolatore.calcola(contesto, rapportoCessato(), ORA);
        var mescolati = new ArrayList<>(rapportoCessato());
        Collections.shuffle(mescolati, new Random(42));

        assertThat(calcolatore.calcola(contesto, rapportoCessato(), ORA)).isEqualTo(primo);
        assertThat(calcolatore.calcola(contesto, mescolati, ORA)).isEqualTo(primo);
    }

    @Test
    void dateContrattualiDaiCedoliniSeIlRapportoNonLeRiporta() {
        var r = calcolatore.calcola(contesto(null, null, METALMECCANICO), rapportoCessato(), ORA);

        assertThat(r.anzianita().dal()).isEqualTo(YearMonth.of(2021, 9));
        assertThat(r.anzianita().fonteInizio()).isEqualTo(FonteDato.DATA_CONTRATTUALE);
        assertThat(r.anzianita().al()).isEqualTo(YearMonth.of(2024, 6));
        assertThat(r.anzianita().inCorso()).isFalse();
    }

    @Test
    void cambioCcnlRicalcolaRalEScatti() {
        var cedolini = List.of(
                ordinario(2025, 1).base("2600.00").buoniPasto().build(),
                ordinario(2025, 2).base("2600.00").build());
        var assunzione = LocalDate.of(2024, 6, 16);

        var senzaCcnl = calcolatore.calcola(contesto(assunzione, null, null), cedolini, ORA);
        var commercio = calcolatore.calcola(contesto(assunzione, null, COMMERCIO), cedolini, ORA);

        assertThat(senzaCcnl.retribuzione().ral()).isEqualByComparingTo("33800.00");
        assertThat(senzaCcnl.retribuzione().stimata()).isTrue();
        assertThat(senzaCcnl.scatti().attuali()).isNull();
        assertThat(senzaCcnl.buoniPasto()).isTrue();

        assertThat(commercio.retribuzione().ral()).isEqualByComparingTo("36400.00");
        assertThat(commercio.retribuzione().stimata()).isFalse();
        assertThat(commercio.retribuzione().nota()).isNull();
        assertThat(commercio.scatti().attuali()).isZero();
        assertThat(commercio.scatti().attualiStimati()).isTrue();
        assertThat(commercio.scatti().prossimoScatto()).isEqualTo(LocalDate.of(2027, 7, 1));
        assertThat(commercio.scatti().fonteProssimo()).isEqualTo(FonteDato.CCNL);
    }

    @Test
    void senzaCedoliniRiepilogoVuotoMaCoerente() {
        var r = calcolatore.calcola(contesto(null, null, null), List.of(), ORA);

        assertThat(r.anzianita().dal()).isNull();
        assertThat(r.retribuzione().ral()).isNull();
        assertThat(r.ratei().saldi()).isEmpty();
        assertThat(r.tfr().totaleAccumulato()).isNull();
        assertThat(r.mensilita()).isEmpty();
        assertThat(r.buoniPasto()).isFalse();
    }
}
