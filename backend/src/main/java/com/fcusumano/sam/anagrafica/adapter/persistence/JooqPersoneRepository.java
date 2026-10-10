package com.fcusumano.sam.anagrafica.adapter.persistence;

import static com.fcusumano.sam.shared.db.jooq.Tables.PERSONA;

import com.fcusumano.sam.anagrafica.Persona;
import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.anagrafica.application.PersoneRepository;
import com.fcusumano.sam.shared.WorkspaceId;
import com.fcusumano.sam.shared.db.jooq.tables.records.PersonaRecord;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/**
 * Accesso jOOQ alla tabella {@code persona}.
 */
@Repository
class JooqPersoneRepository implements PersoneRepository {

    private final DSLContext dsl;
    private final Clock clock;

    JooqPersoneRepository(DSLContext dsl, Clock clock) {
        this.dsl = dsl;
        this.clock = clock;
    }

    @Override
    public Optional<Persona> trova(WorkspaceId workspace, PersonaId id) {
        return dsl.selectFrom(PERSONA)
                .where(PERSONA.WORKSPACE_ID.eq(workspace.valore()), PERSONA.ID.eq(id.valore()))
                .fetchOptional(JooqPersoneRepository::comePersona);
    }

    @Override
    public Optional<Persona> trovaPerCodiceFiscale(WorkspaceId workspace, String codiceFiscale) {
        return dsl.selectFrom(PERSONA)
                .where(PERSONA.WORKSPACE_ID.eq(workspace.valore()), PERSONA.CODICE_FISCALE.eq(codiceFiscale))
                .fetchOptional(JooqPersoneRepository::comePersona);
    }

    @Override
    public List<Persona> elenco(WorkspaceId workspace) {
        return dsl.selectFrom(PERSONA)
                .where(PERSONA.WORKSPACE_ID.eq(workspace.valore()))
                .orderBy(PERSONA.ID)
                .fetch(JooqPersoneRepository::comePersona);
    }

    @Override
    public PersonaId inserisci(WorkspaceId workspace, String codiceFiscale, String nome, String cognome,
                               LocalDate dataNascita) {
        var id = dsl.insertInto(PERSONA)
                .set(PERSONA.WORKSPACE_ID, workspace.valore())
                .set(PERSONA.CODICE_FISCALE, codiceFiscale)
                .set(PERSONA.NOME, nome)
                .set(PERSONA.COGNOME, cognome)
                .set(PERSONA.DATA_NASCITA, dataNascita)
                .set(PERSONA.CREATO_IL, adesso())
                .set(PERSONA.AGGIORNATO_IL, adesso())
                .returningResult(PERSONA.ID)
                .fetchSingle()
                .value1();
        return new PersonaId(id);
    }

    @Override
    public void aggiornaDataNascita(WorkspaceId workspace, PersonaId id, LocalDate dataNascita) {
        dsl.update(PERSONA)
                .set(PERSONA.DATA_NASCITA, dataNascita)
                .set(PERSONA.AGGIORNATO_IL, adesso())
                .where(PERSONA.WORKSPACE_ID.eq(workspace.valore()), PERSONA.ID.eq(id.valore()))
                .execute();
    }

    @Override
    public void elimina(WorkspaceId workspace, PersonaId id) {
        dsl.deleteFrom(PERSONA)
                .where(PERSONA.WORKSPACE_ID.eq(workspace.valore()), PERSONA.ID.eq(id.valore()))
                .execute();
    }

    private OffsetDateTime adesso() {
        return OffsetDateTime.now(clock);
    }

    private static Persona comePersona(PersonaRecord r) {
        return new Persona(new PersonaId(r.getId()), r.getCodiceFiscale(), r.getNome(), r.getCognome(),
                r.getDataNascita());
    }
}
