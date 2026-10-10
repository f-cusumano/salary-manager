package com.fcusumano.sam.anagrafica.application;

import com.fcusumano.sam.anagrafica.Persona;
import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.shared.WorkspaceId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Porta di persistenza delle persone (tabella {@code persona}).
 */
public interface PersoneRepository {

    Optional<Persona> trova(WorkspaceId workspace, PersonaId id);

    Optional<Persona> trovaPerCodiceFiscale(WorkspaceId workspace, String codiceFiscale);

    /** Tutte le persone del workspace (ordine non garantito). */
    List<Persona> elenco(WorkspaceId workspace);

    PersonaId inserisci(WorkspaceId workspace, String codiceFiscale, String nome, String cognome, LocalDate dataNascita);

    void aggiornaDataNascita(WorkspaceId workspace, PersonaId id, LocalDate dataNascita);

    void elimina(WorkspaceId workspace, PersonaId id);
}
