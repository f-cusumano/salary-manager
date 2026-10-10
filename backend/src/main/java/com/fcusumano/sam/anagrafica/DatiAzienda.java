package com.fcusumano.sam.anagrafica;

import java.time.YearMonth;

/**
 * Dati dell'azienda letti dal cedolino (input di {@link CensimentoAnagrafica#trovaOCreaAzienda}).
 * Tutti opzionali tranne {@code ragioneSociale}.
 *
 * @param posizioneInps posizione/matricola INPS come stampata: alcuni layout la stampano concatenata alla posizione
 *                      INAIL; come chiave si usa la matricola aziendale INPS a 10 cifre
 * @param riferimento   periodo di retribuzione del cedolino da cui provengono i dati: gli attributi aggiornabili
 *                      (ragione sociale, indirizzo, codice azienda) vengono sovrascritti solo da un cedolino con
 *                      riferimento uguale o piu' recente; null = dati da considerare i piu' recenti
 */
public record DatiAzienda(
        String codiceFiscale,
        String partitaIva,
        String posizioneInps,
        String ragioneSociale,
        String indirizzo,
        String codiceAzienda,
        YearMonth riferimento) {

    /** Dati senza periodo di riferimento (considerati i piu' recenti). */
    public DatiAzienda(String codiceFiscale, String partitaIva, String posizioneInps, String ragioneSociale,
                       String indirizzo, String codiceAzienda) {
        this(codiceFiscale, partitaIva, posizioneInps, ragioneSociale, indirizzo, codiceAzienda, null);
    }
}
