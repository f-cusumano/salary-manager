package com.fcusumano.sam.calcoli.domain;

import com.fcusumano.sam.calcoli.RiepilogoRapporto.Retribuzione;
import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.shared.Importi;
import com.fcusumano.sam.shared.Periodi;
import com.fcusumano.sam.shared.TipoMensilita;
import java.math.BigDecimal;
import java.util.ArrayList;

/**
 * RAL attuale = lordo mensile contrattuale dell'ultima mensilita' ordinaria x mensilita' del CCNL.
 * <ul>
 *   <li>Lordo mensile: {@link LordoContrattuale} (totale elementi fissi stampato, oppure somma degli elementi, oppure
 *       retribuzione base). Voci variabili, straordinari, rimborsi e buoni pasto non entrano mai.</li>
 *   <li>Riferimento: l'ultima mensilita' ordinaria; se non stampa gli elementi fissi si usa la piu' recente che li
 *       stampa, dichiarandolo in nota.</li>
 *   <li>Mensilita': quelle del CCNL. Se il CCNL non e' indicato la RAL e' marcata come stimata con nota "CCNL non
 *       indicato": 14 mensilita' se i cedolini documentano una quattordicesima (cedolino di quattordicesima o
 *       cedolino che la paga, anche come ratei), altrimenti il default configurato (13).</li>
 * </ul>
 */
public final class CalcoloRetribuzione {

    static final String NOTA_CCNL_NON_INDICATO = "CCNL non indicato: RAL calcolata su %d mensilità.";
    static final String NOTA_CCNL_NON_INDICATO_QUATTORDICESIMA =
            "CCNL non indicato: RAL calcolata su 14 mensilità (quattordicesima presente nei cedolini).";
    static final int MENSILITA_CON_QUATTORDICESIMA = 14;
    static final String NOTA_NESSUN_ORDINARIO = "Nessuna mensilità ordinaria caricata: RAL non determinabile.";
    static final String NOTA_ELEMENTI_ASSENTI =
            "I cedolini ordinari non riportano gli elementi fissi della retribuzione: RAL non determinabile.";
    static final String NOTA_RIFERIMENTO_PRECEDENTE =
            "L'ultima mensilità ordinaria non riporta gli elementi fissi: usata %s.";

    private CalcoloRetribuzione() {
    }

    public static Retribuzione calcola(ContestoRapporto contesto, CedoliniOrdinati cedolini, ParametriCalcolo parametri) {
        var ultimoOrdinario = cedolini.ultimoOrdinario(c -> true);
        if (ultimoOrdinario.isEmpty()) {
            return nonDeterminabile(NOTA_NESSUN_ORDINARIO);
        }
        var riferimento = cedolini.ultimoOrdinario(c -> LordoContrattuale.di(c.elementiRetributivi()).isPresent());
        if (riferimento.isEmpty()) {
            return nonDeterminabile(NOTA_ELEMENTI_ASSENTI);
        }
        var cedolino = riferimento.get();
        var lordo = LordoContrattuale.di(cedolino.elementiRetributivi()).orElseThrow();

        var note = new ArrayList<String>();
        if (!cedolino.equals(ultimoOrdinario.get())) {
            note.add(NOTA_RIFERIMENTO_PRECEDENTE.formatted(Periodi.descrizione(CedoliniOrdinati.mese(cedolino))));
        }
        int mensilita;
        var stimata = false;
        if (contesto.ccnl() != null) {
            mensilita = contesto.ccnl().mensilita();
        } else if (conQuattordicesima(cedolini)) {
            mensilita = MENSILITA_CON_QUATTORDICESIMA;
            stimata = true;
            note.add(NOTA_CCNL_NON_INDICATO_QUATTORDICESIMA);
        } else {
            mensilita = parametri.mensilitaSenzaCcnl();
            stimata = true;
            note.add(NOTA_CCNL_NON_INDICATO.formatted(mensilita));
        }

        var e = cedolino.elementiRetributivi();
        var scomposta = e.minimo() != null;
        return new Retribuzione(
                Importi.normalizza(lordo.importo().multiply(BigDecimal.valueOf(mensilita))),
                Importi.normalizza(lordo.importo()),
                mensilita,
                Importi.normalizza(e.minimo()),
                Importi.normalizza(e.contingenza()),
                Importi.normalizza(e.superminimo()),
                Importi.normalizza(e.importoScatti()),
                Importi.normalizza(e.altriElementiFissi()),
                scomposta ? null : Importi.normalizza(e.retribuzioneBase()),
                stimata,
                note.isEmpty() ? null : String.join(" ", note),
                cedolino.id());
    }

    /** True se un cedolino del rapporto e' una quattordicesima o paga la quattordicesima (o suoi ratei). */
    private static boolean conQuattordicesima(CedoliniOrdinati cedolini) {
        return cedolini.tutti().stream()
                .anyMatch(c -> c.tipo() == TipoMensilita.QUATTORDICESIMA || c.contieneQuattordicesima());
    }

    private static Retribuzione nonDeterminabile(String nota) {
        return new Retribuzione(null, null, null, null, null, null, null, null, null, false, nota, null);
    }

    /** Lordo contrattuale del cedolino, null se non ricavabile dagli elementi fissi. */
    static BigDecimal lordoDi(CedolinoNormalizzato cedolino) {
        return LordoContrattuale.di(cedolino.elementiRetributivi()).map(LordoContrattuale::importo).orElse(null);
    }
}
