package com.fcusumano.sam.calcoli.domain;

import com.fcusumano.sam.calcoli.Preavviso;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.Anzianita;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.Retribuzione;
import com.fcusumano.sam.calcoli.StatoPreavviso;
import com.fcusumano.sam.ccnl.Ccnl;
import com.fcusumano.sam.ccnl.DurataPreavviso;
import com.fcusumano.sam.ccnl.RegolaPreavviso;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * Preavviso del rapporto in corso: termini del CCNL (solo quelli verificati in tabella) per il gruppo che comprende il
 * livello stampato e per la fascia di anzianita' all'ultimo cedolino. Fascia: la regola con la soglia piu' alta
 * superata dall'anzianita' ("oltre 5 anni" vale da 5 anni e 1 mese).
 * <p>
 * Indennita' sostitutiva (sempre una stima, lorda): lordo mensile x mensilita' / 12 per ogni mese di preavviso, /30 per
 * ogni giorno di calendario. Non comprende voci variabili, ne' contributi e imposte.
 */
public final class CalcoloPreavviso {

    static final String NOTA_CCNL_DA_INDICARE = "Indica il CCNL del rapporto per conoscere il preavviso.";
    static final String NOTA_CCNL_NON_VERIFICATO = "Termini di preavviso del CCNL %s non ancora verificati: "
            + "controlla il contratto o chiedi all'ufficio del personale.";
    static final String NOTA_LIVELLO_NON_INDICATO = "Livello non stampato sui cedolini: preavviso non determinabile.";
    static final String NOTA_LIVELLO_NON_RICONOSCIUTO =
            "Livello \"%s\" non riconosciuto tra quelli del CCNL %s: preavviso non determinabile.";
    static final String NOTA_ANZIANITA = "Anzianità non determinabile: preavviso non determinabile.";
    static final String NOTA_INDENNITA = "Indennità stimata sul lordo mensile di %s con i ratei delle mensilità "
            + "aggiuntive (x %d / 12), al lordo di contributi e imposte. Anzianità all'ultimo cedolino.";
    static final String NOTA_INDENNITA_ASSENTE =
            "Retribuzione non determinabile: indennità sostitutiva non stimabile. Anzianità all'ultimo cedolino.";

    private static final BigDecimal DODICI = BigDecimal.valueOf(12);
    private static final BigDecimal TRENTA = BigDecimal.valueOf(30);

    private CalcoloPreavviso() {
    }

    /**
     * @param ccnl    CCNL del rapporto; null se da indicare
     * @param livello livello stampato sui cedolini; null se non stampato
     * @param regole  termini verificati del CCNL (vuota se non ce ne sono)
     */
    public static Preavviso calcola(Ccnl ccnl, String livello, Anzianita anzianita, Retribuzione retribuzione,
                                    List<RegolaPreavviso> regole) {
        if (ccnl == null) {
            return Preavviso.nonDeterminabile(StatoPreavviso.CCNL_DA_INDICARE, livello, NOTA_CCNL_DA_INDICARE);
        }
        if (regole.isEmpty()) {
            return Preavviso.nonDeterminabile(StatoPreavviso.CCNL_NON_VERIFICATO, livello,
                    NOTA_CCNL_NON_VERIFICATO.formatted(ccnl.descrizione()));
        }
        if (livello == null || RegolaPreavviso.normalizzaLivello(livello) == null) {
            return Preavviso.nonDeterminabile(StatoPreavviso.LIVELLO_NON_INDICATO, null, NOTA_LIVELLO_NON_INDICATO);
        }
        var delGruppo = regole.stream()
                .filter(r -> r.comprende(livello))
                .sorted(Comparator.comparingInt(RegolaPreavviso::oltreAnni))
                .toList();
        if (delGruppo.isEmpty()) {
            return Preavviso.nonDeterminabile(StatoPreavviso.LIVELLO_NON_RICONOSCIUTO, livello,
                    NOTA_LIVELLO_NON_RICONOSCIUTO.formatted(livello.strip(), ccnl.descrizione()));
        }
        if (anzianita == null || anzianita.al() == null) {
            return Preavviso.nonDeterminabile(StatoPreavviso.ANZIANITA_NON_DETERMINABILE, livello, NOTA_ANZIANITA);
        }
        var mesi = anzianita.anni() * 12 + anzianita.mesi();
        var indice = 0;
        for (int i = 0; i < delGruppo.size(); i++) {
            if (mesi > delGruppo.get(i).oltreAnni() * 12) {
                indice = i;
            }
        }
        var regola = delGruppo.get(indice);
        var successiva = indice + 1 < delGruppo.size() ? delGruppo.get(indice + 1) : null;
        var prossimaFascia = successiva == null || anzianita.dal() == null
                ? null : anzianita.dal().plusMonths(successiva.oltreAnni() * 12L);
        var lordoMensile = retribuzione == null ? null : retribuzione.lordoMensile();
        var mensilita = retribuzione == null ? null : retribuzione.mensilita();
        var stimabile = lordoMensile != null && mensilita != null;
        var nota = stimabile
                ? NOTA_INDENNITA.formatted(CalcoloLiquidazione.euro(lordoMensile), mensilita)
                : NOTA_INDENNITA_ASSENTE;
        return new Preavviso(
                StatoPreavviso.VERIFICATO,
                livello.strip(),
                regola.descrizioneLivelli(),
                fascia(regola.oltreAnni(), successiva == null ? null : successiva.oltreAnni()),
                prossimaFascia,
                regola.dimissioni(),
                regola.licenziamento(),
                stimabile ? indennita(lordoMensile, mensilita, regola.dimissioni()) : null,
                stimabile ? indennita(lordoMensile, mensilita, regola.licenziamento()) : null,
                regola.riferimento(),
                nota);
    }

    static BigDecimal indennita(BigDecimal lordoMensile, int mensilita, DurataPreavviso durata) {
        var mensile = lordoMensile.multiply(BigDecimal.valueOf(mensilita)).divide(DODICI, 10, RoundingMode.HALF_UP);
        var mesi = BigDecimal.valueOf(durata.mesi())
                .add(BigDecimal.valueOf(durata.giorni()).divide(TRENTA, 10, RoundingMode.HALF_UP));
        return mensile.multiply(mesi).setScale(2, RoundingMode.HALF_UP);
    }

    static String fascia(int oltreAnni, Integer finoAnni) {
        if (oltreAnni == 0) {
            return finoAnni == null ? "qualunque anzianità" : "fino a %d anni".formatted(finoAnni);
        }
        return finoAnni == null
                ? "oltre %d anni".formatted(oltreAnni)
                : "oltre %d e fino a %d anni".formatted(oltreAnni, finoAnni);
    }
}
