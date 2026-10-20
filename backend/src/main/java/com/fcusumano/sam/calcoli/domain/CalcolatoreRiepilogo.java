package com.fcusumano.sam.calcoli.domain;

import com.fcusumano.sam.calcoli.RiepilogoRapporto;
import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Calcolo da zero del riepilogo di un rapporto a partire dai cedolini normalizzati, dal rapporto e dal CCNL.
 * Funzione pura: deterministica e idempotente (a parita' di input produce lo stesso riepilogo, a parte l'istante del
 * calcolo, che e' un parametro), indipendente dall'ordine in cui arrivano i cedolini.
 * <p>
 * Date contrattuali: quelle del rapporto (stampate sui cedolini, la piu' recente prevale); se il rapporto non le
 * riporta, quelle stampate sul cedolino piu' recente che le riporta.
 */
public final class CalcolatoreRiepilogo {

    private final ParametriCalcolo parametri;
    private final ParametriLiquidazione parametriLiquidazione;

    public CalcolatoreRiepilogo(ParametriCalcolo parametri, ParametriLiquidazione parametriLiquidazione) {
        this.parametri = Objects.requireNonNull(parametri, "parametri");
        this.parametriLiquidazione = Objects.requireNonNull(parametriLiquidazione, "parametriLiquidazione");
    }

    public RiepilogoRapporto calcola(ContestoRapporto contesto, List<CedolinoNormalizzato> cedolini,
                                     OffsetDateTime calcolatoIl) {
        var ordinati = CedoliniOrdinati.di(cedolini);
        var completo = conDateDaiCedolini(contesto, ordinati);
        var anzianita = CalcoloAnzianita.calcola(completo, ordinati);
        var retribuzione = CalcoloRetribuzione.calcola(completo, ordinati, parametri);
        var ratei = CalcoloRatei.calcola(ordinati, parametri);
        var tfr = CalcoloTfr.calcola(completo, ordinati, anzianita, parametri);
        return new RiepilogoRapporto(
                completo.rapportoId(),
                calcolatoIl,
                anzianita,
                CalcoloScatti.calcola(completo, ordinati, anzianita),
                retribuzione,
                CalcoloBuoniPasto.calcola(ordinati, parametri),
                ratei,
                tfr,
                CalcoloIndicatori.calcola(ordinati, parametri),
                CalcoloLiquidazione.calcola(completo, ordinati, anzianita, retribuzione, ratei, tfr, parametri,
                        parametriLiquidazione));
    }

    private static ContestoRapporto conDateDaiCedolini(ContestoRapporto contesto, CedoliniOrdinati cedolini) {
        var assunzione = contesto.dataAssunzione() != null
                ? contesto.dataAssunzione() : ultimaStampata(cedolini, CedolinoNormalizzato::dataAssunzione);
        var cessazione = contesto.dataCessazione() != null
                ? contesto.dataCessazione() : ultimaStampata(cedolini, CedolinoNormalizzato::dataCessazione);
        return new ContestoRapporto(contesto.rapportoId(), assunzione, cessazione, contesto.ccnl());
    }

    private static LocalDate ultimaStampata(CedoliniOrdinati cedolini, Function<CedolinoNormalizzato, LocalDate> data) {
        return cedolini.ultimo(c -> data.apply(c) != null).map(data).orElse(null);
    }
}
