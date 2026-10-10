package com.fcusumano.sam.calcoli.domain;

import com.fcusumano.sam.calcoli.FonteDato;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.Anzianita;
import com.fcusumano.sam.shared.Periodi;
import java.time.YearMonth;

/**
 * Anzianita' aziendale in mesi inclusi, estremi compresi.
 * <ul>
 *   <li>inizio: mese della data di assunzione se nota (fonte DATA_CONTRATTUALE), altrimenti mese del primo cedolino
 *       ordinario (fonte CEDOLINI);</li>
 *   <li>fine: mese della data di cessazione se nota (DATA_CONTRATTUALE), altrimenti mese dell'ultimo cedolino
 *       ordinario (CEDOLINI).</li>
 * </ul>
 * Tredicesime, quattordicesime e cedolini di fine rapporto non sono mesi: non spostano gli estremi.
 * Gennaio 2024 - Febbraio 2026 = 26 mesi = 2 anni e 2 mesi; Gennaio 2024 - Dicembre 2025 = 24 mesi = 2 anni.
 * Se la fine precede l'inizio (dati incoerenti) l'anzianita' e' 0.
 */
public final class CalcoloAnzianita {

    private CalcoloAnzianita() {
    }

    public static Anzianita calcola(ContestoRapporto contesto, CedoliniOrdinati cedolini) {
        var ordinari = cedolini.ordinari();
        var inCorso = contesto.dataCessazione() == null;

        YearMonth dal = null;
        var fonteInizio = FonteDato.NON_DISPONIBILE;
        if (contesto.dataAssunzione() != null) {
            dal = YearMonth.from(contesto.dataAssunzione());
            fonteInizio = FonteDato.DATA_CONTRATTUALE;
        } else if (!ordinari.isEmpty()) {
            dal = CedoliniOrdinati.mese(ordinari.getFirst());
            fonteInizio = FonteDato.CEDOLINI;
        }

        YearMonth al = null;
        var fonteFine = FonteDato.NON_DISPONIBILE;
        if (!inCorso) {
            al = YearMonth.from(contesto.dataCessazione());
            fonteFine = FonteDato.DATA_CONTRATTUALE;
        } else if (!ordinari.isEmpty()) {
            al = CedoliniOrdinati.mese(ordinari.getLast());
            fonteFine = FonteDato.CEDOLINI;
        }

        var mesiTotali = dal == null || al == null || al.isBefore(dal) ? 0 : Periodi.mesiInclusi(dal, al);
        return new Anzianita(dal, al, inCorso, mesiTotali / 12, mesiTotali % 12, fonteInizio, fonteFine);
    }
}
