package com.fcusumano.sam.anagrafica;

import java.time.YearMonth;

/**
 * Periodo di un rapporto per la UI: "da Gennaio 2024 a Febbraio 2026" oppure "da Gennaio 2024" se in corso.
 * <p>
 * Regole: {@code da} = mese della data di assunzione se stampata, altrimenti primo cedolino.
 * {@code a} = mese della data di cessazione se stampata; altrimenti, se il rapporto non e' attuale, ultimo cedolino;
 * null se attuale. Un rapporto e' attuale se non ha data di cessazione ed e' quello con l'ultimo cedolino piu' recente
 * tra i rapporti della persona (a parita', sono attuali tutti quelli a pari merito).
 *
 * @param da    null solo se {@code fonteInizio} = NON_DISPONIBILE
 * @param a     null se il rapporto e' attuale
 */
public record PeriodoRapporto(
        YearMonth da,
        YearMonth a,
        boolean attuale,
        FontePeriodo fonteInizio,
        FontePeriodo fonteFine) {
}
