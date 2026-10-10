package com.fcusumano.sam.segnalazioni.domain;

import com.fcusumano.sam.segnalazioni.Evidenza;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import java.util.ArrayList;
import java.util.List;

/**
 * CCNL_NON_DETERMINATO (informativa): il rapporto, con almeno un cedolino, non ha un CCNL. Se sui cedolini e' stampato
 * un testo di CCNL non riconosciuto in modo univoco, lo si riporta come evidenza.
 */
public final class RegolaCcnlNonDeterminato implements RegolaSegnalazione {

    public static final String CODICE = TipoSegnalazione.CCNL_NON_DETERMINATO.name();

    private static final int LUNGHEZZA_MASSIMA_TESTO = 120;

    @Override
    public String codice() {
        return CODICE;
    }

    @Override
    public List<SegnalazioneRilevata> valuta(ContestoRapporto contesto) {
        if (contesto.ccnl() != null || contesto.cedolini().isEmpty()) {
            return List.of();
        }
        var stampato = contesto.cedolini().stream()
                .filter(c -> c.ccnlTesto() != null && !c.ccnlTesto().isBlank())
                .reduce((a, b) -> b);
        var evidenze = new ArrayList<Evidenza>();
        String descrizione;
        if (stampato.isPresent()) {
            var testo = Attivita.abbrevia(stampato.get().ccnlTesto(), LUNGHEZZA_MASSIMA_TESTO);
            evidenze.add(Evidenze.valore("CCNL stampato", stampato.get(), testo));
            descrizione = "Il CCNL stampato sui cedolini («" + testo + "») non è stato riconosciuto in modo univoco: "
                    + "indicalo nel riepilogo del rapporto.";
        } else {
            descrizione = "Il CCNL non è stampato sui cedolini: indicalo nel riepilogo del rapporto.";
        }
        descrizione += " Finché non è indicato la RAL è solo stimata e non è possibile controllare quattordicesima, "
                + "scatti di anzianità e livello secondo le regole del contratto.";
        return List.of(new SegnalazioneRilevata(CODICE, TipoSegnalazione.CCNL_NON_DETERMINATO, Gravita.INFORMATIVA,
                null, null, "CCNL non determinato", descrizione, evidenze));
    }
}
