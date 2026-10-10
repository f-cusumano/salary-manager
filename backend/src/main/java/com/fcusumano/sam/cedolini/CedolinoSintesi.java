package com.fcusumano.sam.cedolini;

import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.shared.Competenza;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Riga della lista mensilita' (senza voci).
 *
 * @param ratei saldi dei ratei stampati sul cedolino
 */
public record CedolinoSintesi(
        CedolinoId id,
        RapportoLavoroId rapportoId,
        Competenza competenza,
        YearMonth periodoRetribuzione,
        LocalDate dataElaborazione,
        BigDecimal lordo,
        BigDecimal netto,
        List<Rateo> ratei,
        String nomeFile) {

    public CedolinoSintesi {
        ratei = ratei == null ? List.of() : List.copyOf(ratei);
    }
}
