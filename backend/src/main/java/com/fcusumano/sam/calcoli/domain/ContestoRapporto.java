package com.fcusumano.sam.calcoli.domain;

import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.ccnl.Ccnl;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Dati del rapporto che servono ai calcoli, oltre ai cedolini.
 *
 * @param dataAssunzione data di assunzione del rapporto (stampata sui cedolini); null se mai stampata
 * @param dataCessazione data di cessazione del rapporto (stampata sui cedolini); null se in corso o mai stampata
 * @param ccnl           CCNL del rapporto (riconosciuto o scelto dall'utente); null se "da indicare"
 */
public record ContestoRapporto(RapportoLavoroId rapportoId, LocalDate dataAssunzione, LocalDate dataCessazione, Ccnl ccnl) {

    public ContestoRapporto {
        Objects.requireNonNull(rapportoId, "rapportoId");
    }
}
