package com.fcusumano.sam.calcoli.domain;

import com.fcusumano.sam.calcoli.RiepilogoRapporto.IndicatoriMensilita;
import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.shared.Importi;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Indicatori per cedolino, calcolati per ogni mensilita' ordinaria rispetto alla mensilita' ordinaria precedente
 * (tredicesime, quattordicesime e fine rapporto hanno indicatori falsi e non fanno da termine di confronto).
 * <ul>
 *   <li>Scatto in busta: il numero di scatti stampato aumenta, oppure l'importo degli scatti aumenta oltre la
 *       tolleranza. Con retribuzione scomposta (minimo stampato su entrambi) la riga scatti assente vale 0.</li>
 *   <li>Aumento fuori scatti: il lordo contrattuale al netto degli scatti aumenta oltre la tolleranza. Il riassorbimento
 *       del superminimo a parita' di totale (minimo su, superminimo giu') non cambia il lordo: non e' un aumento. Se
 *       l'importo degli scatti non e' confrontabile (stampato su uno solo dei due cedolini, oppure su nessuno dei due
 *       mentre il numero di scatti aumenta) non si puo' separare l'effetto degli scatti: nessun indicatore.</li>
 * </ul>
 * Senza lordo contrattuale su uno dei due cedolini non si segnala nessun aumento.
 */
public final class CalcoloIndicatori {

    private CalcoloIndicatori() {
    }

    public static List<IndicatoriMensilita> calcola(CedoliniOrdinati cedolini, ParametriCalcolo parametri) {
        var risultato = new ArrayList<IndicatoriMensilita>();
        CedolinoNormalizzato precedente = null;
        for (var cedolino : cedolini.tutti()) {
            var lordo = Importi.normalizza(CalcoloRetribuzione.lordoDi(cedolino));
            if (!CedoliniOrdinati.ordinario(cedolino)) {
                risultato.add(new IndicatoriMensilita(cedolino.id(), false, false, lordo));
                continue;
            }
            var scatto = precedente != null && scattoInBusta(precedente, cedolino, parametri);
            var aumento = precedente != null && aumentoFuoriScatti(precedente, cedolino, scatto, parametri);
            risultato.add(new IndicatoriMensilita(cedolino.id(), scatto, aumento, lordo));
            precedente = cedolino;
        }
        return risultato;
    }

    static boolean scattoInBusta(CedolinoNormalizzato precedente, CedolinoNormalizzato corrente,
                                 ParametriCalcolo parametri) {
        var numeroPrima = precedente.numeroScatti();
        var numeroDopo = corrente.numeroScatti();
        if (numeroPrima != null && numeroDopo != null && numeroDopo > numeroPrima) {
            return true;
        }
        var importi = importiScatti(precedente, corrente);
        return importi != null && importi.dopo().subtract(importi.prima()).compareTo(parametri.tolleranzaAumento()) > 0;
    }

    static boolean aumentoFuoriScatti(CedolinoNormalizzato precedente, CedolinoNormalizzato corrente,
                                      boolean scattoInBusta, ParametriCalcolo parametri) {
        var lordoPrima = CalcoloRetribuzione.lordoDi(precedente);
        var lordoDopo = CalcoloRetribuzione.lordoDi(corrente);
        if (lordoPrima == null || lordoDopo == null) {
            return false;
        }
        var importi = importiScatti(precedente, corrente);
        if (importi == null) {
            var nessunoStampato = precedente.elementiRetributivi().importoScatti() == null
                    && corrente.elementiRetributivi().importoScatti() == null;
            if (!nessunoStampato || scattoInBusta) {
                return false;
            }
            importi = new ImportiScatti(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        var variazione = lordoDopo.subtract(importi.dopo()).subtract(lordoPrima.subtract(importi.prima()));
        return variazione.compareTo(parametri.tolleranzaAumento()) > 0;
    }

    private record ImportiScatti(BigDecimal prima, BigDecimal dopo) {
    }

    /**
     * Importi degli scatti confrontabili: stampati su entrambi, oppure con retribuzione scomposta su entrambi (la riga
     * scatti assente vale 0); null se non confrontabili.
     */
    private static ImportiScatti importiScatti(CedolinoNormalizzato precedente, CedolinoNormalizzato corrente) {
        var prima = precedente.elementiRetributivi().importoScatti();
        var dopo = corrente.elementiRetributivi().importoScatti();
        if (prima != null && dopo != null) {
            return new ImportiScatti(prima, dopo);
        }
        var scomposti = precedente.elementiRetributivi().minimo() != null
                && corrente.elementiRetributivi().minimo() != null;
        if (scomposti && (prima != null || dopo != null)) {
            return new ImportiScatti(Importi.zeroSeNull(prima), Importi.zeroSeNull(dopo));
        }
        return null;
    }
}
