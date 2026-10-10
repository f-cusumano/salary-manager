package com.fcusumano.sam.calcoli.application;

import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.calcoli.RiepilogoRapporto;
import com.fcusumano.sam.shared.WorkspaceId;
import java.util.Optional;

/**
 * Porta di persistenza dei dati derivati di un rapporto (riepilogo, saldi dei ratei, indicatori per cedolino).
 */
public interface RiepiloghiRepository {

    Optional<RiepilogoRapporto> trova(WorkspaceId workspace, RapportoLavoroId rapportoId);

    /** Sostituisce tutti i dati derivati del rapporto con quelli del riepilogo (cancella e reinserisce). */
    void sostituisci(WorkspaceId workspace, RiepilogoRapporto riepilogo);

    /** Elimina tutti i dati derivati del rapporto (nessun effetto se assenti). */
    void elimina(WorkspaceId workspace, RapportoLavoroId rapportoId);
}
