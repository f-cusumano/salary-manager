package com.fcusumano.sam.anagrafica;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Rapporto di lavoro tra una persona e un'azienda (uno per coppia persona-azienda nel workspace).
 *
 * @param dataAssunzione     data stampata sui cedolini; null se mai stampata
 * @param dataCessazione     data stampata sui cedolini; null se in corso o mai stampata
 * @param ccnlCodice         codice CCNL; null = "CCNL da indicare"
 * @param ccnlSceltoDaUtente true se il CCNL e' stato scelto dall'utente: l'estrazione non lo sovrascrive piu'
 * @param primoCedolino      periodo di retribuzione del primo cedolino caricato; null se nessun cedolino
 * @param ultimoCedolino     periodo di retribuzione dell'ultimo cedolino caricato; null se nessun cedolino
 */
public record RapportoLavoro(
        RapportoLavoroId id,
        PersonaId personaId,
        AziendaId aziendaId,
        LocalDate dataAssunzione,
        LocalDate dataCessazione,
        String qualifica,
        String livello,
        String codiceDipendente,
        String ccnlCodice,
        boolean ccnlSceltoDaUtente,
        String note,
        YearMonth primoCedolino,
        YearMonth ultimoCedolino) {

    public boolean ccnlDaIndicare() {
        return ccnlCodice == null;
    }
}
