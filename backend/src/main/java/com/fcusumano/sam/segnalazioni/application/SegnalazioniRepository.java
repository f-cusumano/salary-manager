package com.fcusumano.sam.segnalazioni.application;

import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.segnalazioni.Segnalazione;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import com.fcusumano.sam.segnalazioni.domain.SegnalazioneRilevata;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.WorkspaceId;
import java.util.List;
import java.util.Set;

/**
 * Porta di persistenza delle segnalazioni (tabella {@code segnalazione}).
 */
public interface SegnalazioniRepository {

    /** Sostituisce tutte le segnalazioni del rapporto con quelle indicate, conservandone l'ordine. */
    void sostituisci(WorkspaceId workspace, RapportoLavoroId rapportoId, List<SegnalazioneRilevata> segnalazioni);

    /** Segnalazioni del rapporto nell'ordine in cui sono state salvate. */
    List<Segnalazione> perRapporto(WorkspaceId workspace, RapportoLavoroId rapportoId);

    boolean esistono(WorkspaceId workspace, RapportoLavoroId rapportoId);

    /** Competenze (anno + mese) delle segnalazioni dei tipi indicati, senza duplicati, in ordine cronologico. */
    List<Competenza.Mensilita> competenze(WorkspaceId workspace, RapportoLavoroId rapportoId, Set<TipoSegnalazione> tipi);
}
