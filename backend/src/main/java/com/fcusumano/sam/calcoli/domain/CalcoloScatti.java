package com.fcusumano.sam.calcoli.domain;

import com.fcusumano.sam.calcoli.FonteDato;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.Anzianita;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.Scatti;
import com.fcusumano.sam.ccnl.Ccnl;
import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Scatti di anzianita' attuali e prossimo scatto ({@code al} = ultimo mese considerato dall'anzianita').
 * <ul>
 *   <li>attuali: numero stampato, se viene dall'ultima mensilita' ordinaria o da un cedolino del mese {@code al} (o
 *       successivo) (CEDOLINI). Se viene da un cedolino piu' vecchio (layout cambiato, i cedolini recenti non lo
 *       stampano piu'): con regole CCNL note, numero stampato + scatti maturati da allora secondo il CCNL (CCNL, stima,
 *       mai oltre il massimo); senza regole, il numero stampato marcato come stima. Nessun numero stampato: stima
 *       dall'anzianita' contrattuale con le regole CCNL (CCNL, stima, mai oltre il massimo); altrimenti null.</li>
 *   <li>prossimo scatto: data stampata sull'ultimo cedolino che la riporta, se successiva al mese {@code al} (una data
 *       gia' trascorsa e' ignorata); altrimenti, con regole CCNL note e scatti attuali sotto il massimo e coerenti con
 *       le regole (pari a quelli maturabili dall'inizio del rapporto: non lo sono, per esempio, con anzianita'
 *       riconosciuta da un precedente datore), decorrenza dello scatto successivo calcolata dall'inizio del rapporto;
 *       altrimenti null. A rapporto cessato non c'e' un prossimo scatto oltre la cessazione.</li>
 * </ul>
 * Decorrenza dello scatto n: primo giorno del mese successivo a quello in cui si compiono n periodi dall'assunzione
 * (assunzione il 1/9/2021, scatti biennali: primo scatto dal 1/9/2023; assunzione il 16/9/2021: dal 1/10/2023). Senza
 * data di assunzione l'inizio e' il primo giorno del mese del primo cedolino ordinario.
 */
public final class CalcoloScatti {

    private CalcoloScatti() {
    }

    public static Scatti calcola(ContestoRapporto contesto, CedoliniOrdinati cedolini, Anzianita anzianita) {
        var ccnl = contesto.ccnl();
        var massimo = ccnl == null ? null : ccnl.maxScatti();
        var inizio = contesto.dataAssunzione() != null
                ? contesto.dataAssunzione()
                : anzianita.dal() == null ? null : anzianita.dal().atDay(1);
        var al = anzianita.al();
        var regoleNote = ccnl != null && ccnl.regoleScattiNote() && ccnl.periodicitaScattiMesi() > 0
                && inizio != null && al != null;

        Integer attuali = null;
        var fonteAttuali = FonteDato.NON_DISPONIBILE;
        var stimati = false;
        var stampato = cedolini.ultimo(c -> c.numeroScatti() != null);
        if (stampato.isPresent()) {
            var cedolino = stampato.get();
            attuali = cedolino.numeroScatti();
            fonteAttuali = FonteDato.CEDOLINI;
            if (!recente(cedolino, cedolini, al)) {
                stimati = true;
                if (regoleNote) {
                    var maturatiDaAllora = scattiMaturati(ccnl, inizio, al)
                            - scattiMaturati(ccnl, inizio, CedoliniOrdinati.mese(cedolino));
                    attuali = Math.min(ccnl.maxScatti(), attuali + maturatiDaAllora);
                    fonteAttuali = FonteDato.CCNL;
                }
            }
        } else if (regoleNote) {
            attuali = scattiMaturati(ccnl, inizio, al);
            fonteAttuali = FonteDato.CCNL;
            stimati = true;
        }

        LocalDate prossimo = null;
        var fonteProssimo = FonteDato.NON_DISPONIBILE;
        var dataStampata = cedolini.ultimo(c -> c.prossimoScatto() != null).map(CedolinoNormalizzato::prossimoScatto)
                .filter(data -> al == null || YearMonth.from(data).isAfter(al));
        if (dataStampata.isPresent()) {
            prossimo = dataStampata.get();
            fonteProssimo = FonteDato.CEDOLINI;
        } else if (regoleNote && attuali != null && attuali < ccnl.maxScatti()
                && attuali == scattiMaturati(ccnl, inizio, al)) {
            // coerente con le regole: lo scatto successivo decorre dopo il mese al
            prossimo = decorrenza(ccnl, inizio, attuali + 1);
            fonteProssimo = FonteDato.CCNL;
        }
        if (prossimo != null && contesto.dataCessazione() != null && prossimo.isAfter(contesto.dataCessazione())) {
            prossimo = null;
            fonteProssimo = FonteDato.NON_DISPONIBILE;
        }
        return new Scatti(attuali, massimo, prossimo, fonteProssimo, fonteAttuali, stimati);
    }

    /** True se il numero stampato vale come attuale: cedolino dell'ultima ordinaria o del mese {@code al} o successivo. */
    private static boolean recente(CedolinoNormalizzato cedolino, CedoliniOrdinati cedolini, YearMonth al) {
        var ultimaOrdinaria = cedolini.ultimoOrdinario(c -> true);
        return ultimaOrdinaria.isEmpty() || ultimaOrdinaria.get() == cedolino || al == null
                || !CedoliniOrdinati.mese(cedolino).isBefore(al);
    }

    /** Scatti con decorrenza entro il mese indicato, al massimo {@code ccnl.maxScatti()}. */
    static int scattiMaturati(Ccnl ccnl, LocalDate inizio, YearMonth al) {
        var maturati = 0;
        while (maturati < ccnl.maxScatti() && !YearMonth.from(decorrenza(ccnl, inizio, maturati + 1)).isAfter(al)) {
            maturati++;
        }
        return maturati;
    }

    /** Decorrenza dello scatto n-esimo: primo giorno del mese successivo al compimento di n periodi. */
    static LocalDate decorrenza(Ccnl ccnl, LocalDate inizio, int numero) {
        Objects.requireNonNull(ccnl.periodicitaScattiMesi(), "periodicita' scatti");
        var compimento = inizio.plusMonths((long) ccnl.periodicitaScattiMesi() * numero).minusDays(1);
        return compimento.withDayOfMonth(1).plusMonths(1);
    }
}
