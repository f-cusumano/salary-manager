package com.fcusumano.sam.anagrafica.application;

import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.anagrafica.RapportoLavoro;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.anagrafica.domain.StatoRapporto;
import com.fcusumano.sam.shared.WorkspaceId;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Porta di persistenza dei rapporti di lavoro (tabella {@code rapporto_lavoro}).
 */
public interface RapportiRepository {

    /** Rapporto con il periodo di riferimento dei dati letti dai cedolini. */
    record RapportoRegistrato(RapportoLavoro rapporto, YearMonth riferimentoDati) {

        public StatoRapporto stato() {
            return new StatoRapporto(rapporto.dataAssunzione(), rapporto.dataCessazione(), rapporto.qualifica(),
                    rapporto.livello(), rapporto.codiceDipendente(), rapporto.ccnlCodice(),
                    rapporto.ccnlSceltoDaUtente(), riferimentoDati);
        }
    }

    Optional<RapportoRegistrato> trova(WorkspaceId workspace, RapportoLavoroId id);

    Optional<RapportoRegistrato> trovaPerPersonaEAzienda(WorkspaceId workspace, PersonaId personaId, AziendaId aziendaId);

    List<RapportoLavoro> perPersona(WorkspaceId workspace, PersonaId personaId);

    RapportoLavoroId inserisci(WorkspaceId workspace, PersonaId personaId, AziendaId aziendaId, StatoRapporto stato);

    void aggiornaStato(WorkspaceId workspace, RapportoLavoroId id, StatoRapporto stato);

    void aggiornaNote(WorkspaceId workspace, RapportoLavoroId id, String note);

    void aggiornaCcnlSceltoDaUtente(WorkspaceId workspace, RapportoLavoroId id, String ccnlCodice);

    void aggiornaEstremiCedolini(WorkspaceId workspace, RapportoLavoroId id, YearMonth primo, YearMonth ultimo);

    boolean esistonoPerPersona(WorkspaceId workspace, PersonaId personaId);

    boolean esistonoPerAzienda(WorkspaceId workspace, AziendaId aziendaId);

    void elimina(WorkspaceId workspace, RapportoLavoroId id);
}
