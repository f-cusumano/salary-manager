package com.fcusumano.sam.anagrafica;

import java.util.List;
import java.util.Optional;

/**
 * Letture anagrafiche per la UI e per gli altri moduli (workspace corrente).
 */
public interface ConsultazioneAnagrafica {

    /** Persone del workspace ordinate per cognome e nome, con etichetta anti-omonimia. */
    List<PersonaSintesi> elencoPersone();

    Optional<Persona> trovaPersona(PersonaId id);

    Optional<Azienda> trovaAzienda(AziendaId id);

    Optional<RapportoLavoro> trovaRapporto(RapportoLavoroId id);

    /**
     * Rapporti della persona in ordine cronologico inverso (per inizio periodo decrescente; i rapporti attuali
     * per primi), con periodo ed etichetta anti-omonimia dell'azienda.
     *
     * @throws com.fcusumano.sam.shared.RisorsaNonTrovataException se la persona non esiste
     */
    List<RapportoSintesi> rapportiDiPersona(PersonaId personaId);

    Optional<RapportoLavoroDettaglio> dettaglioRapporto(RapportoLavoroId id);
}
