package com.fcusumano.sam.calcoli;

import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import java.util.Optional;

/**
 * Dati derivati di un rapporto.
 */
public interface CalcoliRapporto {

    /** Ultimo riepilogo calcolato; vuoto se il rapporto non ha ancora un riepilogo. */
    Optional<RiepilogoRapporto> riepilogo(RapportoLavoroId rapportoId);

    /**
     * Ricalcola da zero il riepilogo del rapporto e lo salva (sostituendo il precedente). Deterministico e idempotente.
     * Se il rapporto non esiste o non ha cedolini, rimuove i dati derivati.
     */
    void ricalcola(RapportoLavoroId rapportoId);
}
