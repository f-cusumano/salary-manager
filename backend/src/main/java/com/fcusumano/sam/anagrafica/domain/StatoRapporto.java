package com.fcusumano.sam.anagrafica.domain;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Dati del rapporto aggiornabili dai cedolini, con il periodo del cedolino da cui provengono.
 *
 * @param ccnlCodice         null = "CCNL da indicare"
 * @param ccnlSceltoDaUtente true se il CCNL e' stato scelto dall'utente (l'estrazione non lo modifica piu')
 * @param riferimentoDati    periodo del cedolino piu' recente da cui provengono date, qualifica, livello e codice
 *                           dipendente; null se non noto
 */
public record StatoRapporto(
        LocalDate dataAssunzione,
        LocalDate dataCessazione,
        String qualifica,
        String livello,
        String codiceDipendente,
        String ccnlCodice,
        boolean ccnlSceltoDaUtente,
        YearMonth riferimentoDati) {

    /** Stato di un rapporto appena creato, prima di applicare i dati del primo cedolino. */
    public static StatoRapporto vuoto() {
        return new StatoRapporto(null, null, null, null, null, null, false, null);
    }

    /** True se le date sono coerenti (cessazione non precedente all'assunzione). */
    public boolean dateCoerenti() {
        return dataAssunzione == null || dataCessazione == null || !dataCessazione.isBefore(dataAssunzione);
    }
}
