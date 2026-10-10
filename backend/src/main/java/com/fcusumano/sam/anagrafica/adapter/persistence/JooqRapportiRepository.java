package com.fcusumano.sam.anagrafica.adapter.persistence;

import static com.fcusumano.sam.shared.db.jooq.Tables.RAPPORTO_LAVORO;

import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.anagrafica.RapportoLavoro;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.anagrafica.application.RapportiRepository;
import com.fcusumano.sam.anagrafica.domain.StatoRapporto;
import com.fcusumano.sam.shared.Periodi;
import com.fcusumano.sam.shared.WorkspaceId;
import com.fcusumano.sam.shared.db.jooq.tables.records.RapportoLavoroRecord;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/**
 * Accesso jOOQ alla tabella {@code rapporto_lavoro}. I mesi (primo/ultimo cedolino, riferimento dei dati) sono
 * memorizzati come primo giorno del mese.
 */
@Repository
class JooqRapportiRepository implements RapportiRepository {

    private final DSLContext dsl;
    private final Clock clock;

    JooqRapportiRepository(DSLContext dsl, Clock clock) {
        this.dsl = dsl;
        this.clock = clock;
    }

    @Override
    public Optional<RapportoRegistrato> trova(WorkspaceId workspace, RapportoLavoroId id) {
        return dsl.selectFrom(RAPPORTO_LAVORO)
                .where(RAPPORTO_LAVORO.WORKSPACE_ID.eq(workspace.valore()), RAPPORTO_LAVORO.ID.eq(id.valore()))
                .fetchOptional(JooqRapportiRepository::comeRegistrato);
    }

    @Override
    public Optional<RapportoRegistrato> trovaPerPersonaEAzienda(WorkspaceId workspace, PersonaId personaId,
                                                                AziendaId aziendaId) {
        return dsl.selectFrom(RAPPORTO_LAVORO)
                .where(RAPPORTO_LAVORO.WORKSPACE_ID.eq(workspace.valore()),
                        RAPPORTO_LAVORO.PERSONA_ID.eq(personaId.valore()),
                        RAPPORTO_LAVORO.AZIENDA_ID.eq(aziendaId.valore()))
                .fetchOptional(JooqRapportiRepository::comeRegistrato);
    }

    @Override
    public List<RapportoLavoro> perPersona(WorkspaceId workspace, PersonaId personaId) {
        return dsl.selectFrom(RAPPORTO_LAVORO)
                .where(RAPPORTO_LAVORO.WORKSPACE_ID.eq(workspace.valore()),
                        RAPPORTO_LAVORO.PERSONA_ID.eq(personaId.valore()))
                .orderBy(RAPPORTO_LAVORO.ID)
                .fetch(r -> comeRegistrato(r).rapporto());
    }

    @Override
    public RapportoLavoroId inserisci(WorkspaceId workspace, PersonaId personaId, AziendaId aziendaId,
                                      StatoRapporto stato) {
        var id = dsl.insertInto(RAPPORTO_LAVORO)
                .set(RAPPORTO_LAVORO.WORKSPACE_ID, workspace.valore())
                .set(RAPPORTO_LAVORO.PERSONA_ID, personaId.valore())
                .set(RAPPORTO_LAVORO.AZIENDA_ID, aziendaId.valore())
                .set(RAPPORTO_LAVORO.DATA_ASSUNZIONE, stato.dataAssunzione())
                .set(RAPPORTO_LAVORO.DATA_CESSAZIONE, stato.dataCessazione())
                .set(RAPPORTO_LAVORO.QUALIFICA, stato.qualifica())
                .set(RAPPORTO_LAVORO.LIVELLO, stato.livello())
                .set(RAPPORTO_LAVORO.CODICE_DIPENDENTE, stato.codiceDipendente())
                .set(RAPPORTO_LAVORO.CCNL_CODICE, stato.ccnlCodice())
                .set(RAPPORTO_LAVORO.CCNL_SCELTO_DA_UTENTE, stato.ccnlSceltoDaUtente())
                .set(RAPPORTO_LAVORO.RIFERIMENTO_DATI, Periodi.primoGiorno(stato.riferimentoDati()))
                .set(RAPPORTO_LAVORO.CREATO_IL, adesso())
                .set(RAPPORTO_LAVORO.AGGIORNATO_IL, adesso())
                .returningResult(RAPPORTO_LAVORO.ID)
                .fetchSingle()
                .value1();
        return new RapportoLavoroId(id);
    }

    @Override
    public void aggiornaStato(WorkspaceId workspace, RapportoLavoroId id, StatoRapporto stato) {
        dsl.update(RAPPORTO_LAVORO)
                .set(RAPPORTO_LAVORO.DATA_ASSUNZIONE, stato.dataAssunzione())
                .set(RAPPORTO_LAVORO.DATA_CESSAZIONE, stato.dataCessazione())
                .set(RAPPORTO_LAVORO.QUALIFICA, stato.qualifica())
                .set(RAPPORTO_LAVORO.LIVELLO, stato.livello())
                .set(RAPPORTO_LAVORO.CODICE_DIPENDENTE, stato.codiceDipendente())
                .set(RAPPORTO_LAVORO.CCNL_CODICE, stato.ccnlCodice())
                .set(RAPPORTO_LAVORO.CCNL_SCELTO_DA_UTENTE, stato.ccnlSceltoDaUtente())
                .set(RAPPORTO_LAVORO.RIFERIMENTO_DATI, Periodi.primoGiorno(stato.riferimentoDati()))
                .set(RAPPORTO_LAVORO.AGGIORNATO_IL, adesso())
                .where(RAPPORTO_LAVORO.WORKSPACE_ID.eq(workspace.valore()), RAPPORTO_LAVORO.ID.eq(id.valore()))
                .execute();
    }

    @Override
    public void aggiornaNote(WorkspaceId workspace, RapportoLavoroId id, String note) {
        dsl.update(RAPPORTO_LAVORO)
                .set(RAPPORTO_LAVORO.NOTE, note)
                .set(RAPPORTO_LAVORO.AGGIORNATO_IL, adesso())
                .where(RAPPORTO_LAVORO.WORKSPACE_ID.eq(workspace.valore()), RAPPORTO_LAVORO.ID.eq(id.valore()))
                .execute();
    }

    @Override
    public void aggiornaCcnlSceltoDaUtente(WorkspaceId workspace, RapportoLavoroId id, String ccnlCodice) {
        dsl.update(RAPPORTO_LAVORO)
                .set(RAPPORTO_LAVORO.CCNL_CODICE, ccnlCodice)
                .set(RAPPORTO_LAVORO.CCNL_SCELTO_DA_UTENTE, true)
                .set(RAPPORTO_LAVORO.AGGIORNATO_IL, adesso())
                .where(RAPPORTO_LAVORO.WORKSPACE_ID.eq(workspace.valore()), RAPPORTO_LAVORO.ID.eq(id.valore()))
                .execute();
    }

    @Override
    public void aggiornaEstremiCedolini(WorkspaceId workspace, RapportoLavoroId id, YearMonth primo, YearMonth ultimo) {
        dsl.update(RAPPORTO_LAVORO)
                .set(RAPPORTO_LAVORO.PRIMO_CEDOLINO, Periodi.primoGiorno(primo))
                .set(RAPPORTO_LAVORO.ULTIMO_CEDOLINO, Periodi.primoGiorno(ultimo))
                .set(RAPPORTO_LAVORO.AGGIORNATO_IL, adesso())
                .where(RAPPORTO_LAVORO.WORKSPACE_ID.eq(workspace.valore()), RAPPORTO_LAVORO.ID.eq(id.valore()))
                .execute();
    }

    @Override
    public boolean esistonoPerPersona(WorkspaceId workspace, PersonaId personaId) {
        return dsl.fetchExists(RAPPORTO_LAVORO, RAPPORTO_LAVORO.WORKSPACE_ID.eq(workspace.valore()),
                RAPPORTO_LAVORO.PERSONA_ID.eq(personaId.valore()));
    }

    @Override
    public boolean esistonoPerAzienda(WorkspaceId workspace, AziendaId aziendaId) {
        return dsl.fetchExists(RAPPORTO_LAVORO, RAPPORTO_LAVORO.WORKSPACE_ID.eq(workspace.valore()),
                RAPPORTO_LAVORO.AZIENDA_ID.eq(aziendaId.valore()));
    }

    @Override
    public void elimina(WorkspaceId workspace, RapportoLavoroId id) {
        dsl.deleteFrom(RAPPORTO_LAVORO)
                .where(RAPPORTO_LAVORO.WORKSPACE_ID.eq(workspace.valore()), RAPPORTO_LAVORO.ID.eq(id.valore()))
                .execute();
    }

    private OffsetDateTime adesso() {
        return OffsetDateTime.now(clock);
    }

    private static RapportoRegistrato comeRegistrato(RapportoLavoroRecord r) {
        var rapporto = new RapportoLavoro(
                new RapportoLavoroId(r.getId()),
                new PersonaId(r.getPersonaId()),
                new AziendaId(r.getAziendaId()),
                r.getDataAssunzione(),
                r.getDataCessazione(),
                r.getQualifica(),
                r.getLivello(),
                r.getCodiceDipendente(),
                r.getCcnlCodice(),
                Boolean.TRUE.equals(r.getCcnlSceltoDaUtente()),
                r.getNote(),
                Periodi.meseDi(r.getPrimoCedolino()),
                Periodi.meseDi(r.getUltimoCedolino()));
        return new RapportoRegistrato(rapporto, Periodi.meseDi(r.getRiferimentoDati()));
    }
}
