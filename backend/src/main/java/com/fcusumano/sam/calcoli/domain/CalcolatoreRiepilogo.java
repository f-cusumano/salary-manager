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

    public CalcolatoreRiepilogo(ParametriCalcolo parametri) {
        this.parametri = Objects.requireNonNull(parametri, "parametri");
    }

    public RiepilogoRapporto calcola(ContestoRapporto contesto, List<CedolinoNormalizzato> cedolini,
                                     OffsetDateTime calcolatoIl) {
        var ordinati = CedoliniOrdinati.di(cedolini);
        var completo = conDateDaiCedolini(contesto, ordinati);
        var anzianita = CalcoloAnzianita.calcola(completo, ordinati);
        return new RiepilogoRapporto(
                completo.rapportoId(),
                calcolatoIl,
                anzianita,
                CalcoloScatti.calcola(completo, ordinati, anzianita),
                CalcoloRetribuzione.calcola(completo, ordinati, parametri),
                CalcoloBuoniPasto.calcola(ordinati, parametri),
                CalcoloRatei.calcola(ordinati, parametri),
                CalcoloTfr.calcola(completo, ordinati, anzianita, parametri),
                CalcoloIndicatori.calcola(ordinati, parametri));
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
