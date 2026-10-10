package com.fcusumano.sam.calcoli.domain;

import com.fcusumano.sam.calcoli.RiepilogoRapporto.Ratei;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.SaldoRateo;
import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.cedolini.ElementiRetributivi;
import com.fcusumano.sam.cedolini.Rateo;
import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.shared.Importi;
import com.fcusumano.sam.shared.UnitaMisura;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Ratei residui (ferie, permessi, ROL, ex festivita', banca ore) dall'ultimo cedolino che riporta almeno un saldo, per
 * tipo e unita' come stampati (un tipo puo' comparire sia in giorni sia in ore). I saldi di cedolini precedenti non
 * vengono mescolati: un tipo non piu' stampato non e' riportato con un valore vecchio.
 * <p>
 * Valore stimato se liquidati (sempre una stima, arrotondata al centesimo):
 * <ul>
 *   <li>giorni x paga giornaliera stampata; altrimenti lordo mensile / divisore giornaliero (stampato, altrimenti da
 *       configurazione); in mancanza del lordo, giorni x ore giornaliere (configurazione) x paga oraria stampata;</li>
 *   <li>ore x paga oraria stampata; altrimenti lordo mensile / divisore orario (stampato, altrimenti da
 *       configurazione).</li>
 * </ul>
 * La paga stampata si cerca sul cedolino dei ratei, poi sull'ultima mensilita' ordinaria con elementi fissi (quella
 * della RAL), da cui si prende anche il lordo mensile.
 */
public final class CalcoloRatei {

    private static final int SCALA_PAGA = 5;

    private CalcoloRatei() {
    }

    public static Ratei calcola(CedoliniOrdinati cedolini, ParametriCalcolo parametri) {
        var riferimento = cedolini.ultimo(c -> c.ratei().stream().anyMatch(r -> r.saldo() != null));
        if (riferimento.isEmpty()) {
            return new Ratei(null, List.of());
        }
        var cedolino = riferimento.get();
        var retribuzione = cedolini.ultimoOrdinario(c -> LordoContrattuale.di(c.elementiRetributivi()).isPresent());
        var paghe = new Paghe(cedolino, retribuzione, parametri);

        var visti = new HashSet<Chiave>();
        var saldi = new ArrayList<SaldoRateo>();
        for (Rateo rateo : cedolino.ratei()) {
            if (rateo.saldo() == null || rateo.tipo() == null || rateo.unita() == null
                    || !visti.add(new Chiave(rateo.tipo(), rateo.unita()))) {
                continue;
            }
            var stima = paghe.stima(rateo.unita());
            saldi.add(new SaldoRateo(
                    rateo.tipo(),
                    rateo.unita(),
                    Importi.normalizza(rateo.saldo()),
                    stima.map(s -> Importi.normalizza(Importi.euro(rateo.saldo().multiply(s.paga())))).orElse(null),
                    stima.map(Stima::nota).orElse(null)));
        }
        return new Ratei(cedolino.id(), saldi);
    }

    private record Chiave(TipoRateo tipo, UnitaMisura unita) {
    }

    /** Paga per unita' di misura con la descrizione della base usata. */
    record Stima(BigDecimal paga, String nota) {
    }

    /** Paghe giornaliera e oraria disponibili per la stima, in ordine di preferenza. */
    private static final class Paghe {

        private final CedolinoNormalizzato ratei;
        private final Optional<CedolinoNormalizzato> retribuzione;
        private final ParametriCalcolo parametri;

        Paghe(CedolinoNormalizzato ratei, Optional<CedolinoNormalizzato> retribuzione, ParametriCalcolo parametri) {
            this.ratei = ratei;
            this.retribuzione = retribuzione;
            this.parametri = parametri;
        }

        Optional<Stima> stima(UnitaMisura unita) {
            return unita == UnitaMisura.GIORNI ? giornaliera() : oraria();
        }

        private Optional<Stima> giornaliera() {
            var stampata = stampato(ElementiRetributivi::pagaGiornaliera);
            if (stampata.isPresent()) {
                return Optional.of(new Stima(stampata.get(), "Giorni x paga giornaliera stampata sul cedolino."));
            }
            var daLordo = daLordo(ElementiRetributivi::divisoreGiornaliero, parametri.divisoreGiornaliero(),
                    "Giorni x lordo mensile / divisore giornaliero %s (%s).");
            if (daLordo.isPresent()) {
                return daLordo;
            }
            return stampato(ElementiRetributivi::pagaOraria).map(oraria -> new Stima(
                    oraria.multiply(parametri.oreGiornaliere()),
                    "Giorni x %s ore (da configurazione) x paga oraria stampata sul cedolino."
                            .formatted(parametri.oreGiornaliere().stripTrailingZeros().toPlainString())));
        }

        private Optional<Stima> oraria() {
            var stampata = stampato(ElementiRetributivi::pagaOraria);
            if (stampata.isPresent()) {
                return Optional.of(new Stima(stampata.get(), "Ore x paga oraria stampata sul cedolino."));
            }
            return daLordo(ElementiRetributivi::divisoreOrario, parametri.divisoreOrario(),
                    "Ore x lordo mensile / divisore orario %s (%s).");
        }

        /** Primo valore positivo stampato sul cedolino dei ratei o su quello della retribuzione. */
        private Optional<BigDecimal> stampato(Function<ElementiRetributivi, BigDecimal> campo) {
            var candidati = new ArrayList<CedolinoNormalizzato>();
            candidati.add(ratei);
            retribuzione.ifPresent(candidati::add);
            return candidati.stream()
                    .map(c -> campo.apply(c.elementiRetributivi()))
                    .filter(v -> v != null && v.signum() > 0)
                    .findFirst();
        }

        private Optional<Stima> daLordo(Function<ElementiRetributivi, BigDecimal> divisoreStampato,
                                        BigDecimal divisoreConfigurato, String nota) {
            var lordo = retribuzione.flatMap(c -> LordoContrattuale.di(c.elementiRetributivi()));
            if (lordo.isEmpty()) {
                return Optional.empty();
            }
            var stampato = stampato(divisoreStampato);
            var divisore = stampato.orElse(divisoreConfigurato);
            var origine = stampato.isPresent() ? "stampato sul cedolino" : "da configurazione";
            return Optional.of(new Stima(
                    lordo.get().importo().divide(divisore, SCALA_PAGA, RoundingMode.HALF_UP),
                    nota.formatted(divisore.stripTrailingZeros().toPlainString(), origine)));
        }
    }
}
