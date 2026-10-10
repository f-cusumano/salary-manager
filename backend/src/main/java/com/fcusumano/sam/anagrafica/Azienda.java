package com.fcusumano.sam.anagrafica;

/**
 * Azienda (datore di lavoro). Tutti gli identificativi sono opzionali tranne la ragione sociale:
 * il codice fiscale/P.IVA non e' sempre stampato sul cedolino. Indirizzo e codice azienda sono attributi
 * aggiornabili, non chiavi.
 */
public record Azienda(
        AziendaId id,
        String codiceFiscale,
        String partitaIva,
        String posizioneInps,
        String ragioneSociale,
        String indirizzo,
        String codiceAzienda) {
}
