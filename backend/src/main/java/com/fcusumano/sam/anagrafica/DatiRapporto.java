package com.fcusumano.sam.anagrafica;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Dati del rapporto letti da un cedolino (input di {@link CensimentoAnagrafica#trovaOCreaRapporto}).
 *
 * @param ccnlCodiceRiconosciuto codice CCNL riconosciuto dal testo stampato; null se non riconosciuto
 * @param riferimento            periodo di retribuzione del cedolino da cui provengono i dati: un dato gia' presente
 *                               viene sovrascritto solo da un cedolino con riferimento uguale o piu' recente
 */
public record DatiRapporto(
        LocalDate dataAssunzione,
        LocalDate dataCessazione,
        String qualifica,
        String livello,
        String codiceDipendente,
        String ccnlCodiceRiconosciuto,
        YearMonth riferimento) {
}
