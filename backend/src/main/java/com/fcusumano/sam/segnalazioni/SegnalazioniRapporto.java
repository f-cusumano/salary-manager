package com.fcusumano.sam.segnalazioni;

import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.shared.Competenza;
import java.util.List;

/**
 * Segnalazioni di un rapporto.
 */
public interface SegnalazioniRapporto {

    /** Segnalazioni del rapporto, ordinate per gravita' decrescente e poi per competenza. */
    List<Segnalazione> segnalazioni(RapportoLavoroId rapportoId);

    /** True se il rapporto ha almeno una segnalazione (triangolo arancione accanto all'azienda). */
    boolean haSegnalazioni(RapportoLavoroId rapportoId);

    /**
     * Competenze mancanti da mostrare nella lista mensilita' come righe "mancanti": mensilita' ordinarie (mese 1..12)
     * e, se segnalate, tredicesime (13) e quattordicesime (14) mancanti. Ricavate dalle segnalazioni
     * MENSILITA_MANCANTE, TREDICESIMA_MANCANTE, QUATTORDICESIMA_MANCANTE. Ordine cronologico crescente.
     */
    List<Competenza.Mensilita> mensilitaMancanti(RapportoLavoroId rapportoId);

    /** Rivaluta da zero tutte le regole sul rapporto e sostituisce le segnalazioni salvate. Deterministico e idempotente. */
    void ricalcola(RapportoLavoroId rapportoId);
}
