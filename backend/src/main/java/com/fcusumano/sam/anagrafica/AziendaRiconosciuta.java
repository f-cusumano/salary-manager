package com.fcusumano.sam.anagrafica;

/**
 * Azienda gia' censita a cui il censimento abbinerebbe dei dati, con il dato che determina l'abbinamento.
 *
 * @param dato dato dei dati indicati che fa riconoscere l'azienda
 */
public record AziendaRiconosciuta(AziendaId id, Dato dato) {

    /** Dato che fa riconoscere l'azienda (criteri di {@link CensimentoAnagrafica#trovaOCreaAzienda}). */
    public enum Dato {
        /** Partita IVA uguale a un identificativo fiscale dell'azienda. */
        PARTITA_IVA,
        /** Codice fiscale uguale a un identificativo fiscale dell'azienda (anche numerico, uguale alla P.IVA). */
        CODICE_FISCALE,
        /** Stessa matricola INPS. */
        POSIZIONE_INPS,
        /** Stessa ragione sociale normalizzata. */
        RAGIONE_SOCIALE
    }
}
