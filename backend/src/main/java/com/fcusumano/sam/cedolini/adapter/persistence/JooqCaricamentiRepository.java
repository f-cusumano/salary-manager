package com.fcusumano.sam.cedolini.adapter.persistence;

import static com.fcusumano.sam.shared.db.jooq.Tables.CARICAMENTO;
import static com.fcusumano.sam.shared.db.jooq.Tables.CEDOLINO;

import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.cedolini.Caricamento;
import com.fcusumano.sam.cedolini.CaricamentoId;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.StatoCaricamento;
import com.fcusumano.sam.cedolini.application.CaricamentiRepository;
import com.fcusumano.sam.shared.WorkspaceId;
import com.fcusumano.sam.shared.db.jooq.tables.records.CaricamentoRecord;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/**
 * Accesso jOOQ alla tabella {@code caricamento}. Ogni aggiornamento e' una singola istruzione: fuori da una
 * transazione viene committato subito (stato visibile al polling). Nelle letture il rapporto si ricava dal cedolino
 * creato o da quello gia' presente (tabella {@code cedolino}, dello stesso modulo): se il cedolino viene eliminato il
 * riferimento si azzera da solo.
 */
@Repository
class JooqCaricamentiRepository implements CaricamentiRepository {

    /** Lunghezza massima del messaggio (colonna {@code caricamento.messaggio}). */
    private static final int LUNGHEZZA_MESSAGGIO = 2000;

    private final DSLContext dsl;
    private final Clock clock;

    JooqCaricamentiRepository(DSLContext dsl, Clock clock) {
        this.dsl = dsl;
        this.clock = clock;
    }

    @Override
    public List<Caricamento> registra(WorkspaceId workspace, UUID lotto, List<FileInCoda> files) {
        var registrati = new ArrayList<Caricamento>(files.size());
        var adesso = OffsetDateTime.now(clock);
        for (var file : files) {
            var record = dsl.newRecord(CARICAMENTO);
            record.setWorkspaceId(workspace.valore());
            record.setLotto(lotto);
            record.setNomeFileOriginale(Testo.tronca(file.nomeFileOriginale(), 255));
            record.setDimensione(file.dimensione());
            record.setHashContenuto(file.hash());
            record.setStato(StatoCaricamento.IN_CODA.name());
            record.setCreatoIl(adesso);
            record.setAggiornatoIl(adesso);
            record.store();
            registrati.add(comeCaricamento(record));
        }
        return registrati;
    }

    @Override
    public void aggiorna(WorkspaceId workspace, CaricamentoId id, StatoCaricamento stato, String messaggio,
                         CedolinoId cedolinoId, CedolinoId cedolinoEsistenteId) {
        dsl.update(CARICAMENTO)
                .set(CARICAMENTO.STATO, stato.name())
                .set(CARICAMENTO.MESSAGGIO, Testo.tronca(messaggio, LUNGHEZZA_MESSAGGIO))
                .set(CARICAMENTO.CEDOLINO_ID, cedolinoId == null ? null : cedolinoId.valore())
                .set(CARICAMENTO.CEDOLINO_ESISTENTE_ID, cedolinoEsistenteId == null ? null : cedolinoEsistenteId.valore())
                .set(CARICAMENTO.AGGIORNATO_IL, OffsetDateTime.now(clock))
                .where(CARICAMENTO.WORKSPACE_ID.eq(workspace.valore()), CARICAMENTO.ID.eq(id.valore()))
                .execute();
    }

    @Override
    public List<Caricamento> perLotto(WorkspaceId workspace, UUID lotto) {
        return leggi(CARICAMENTO.WORKSPACE_ID.eq(workspace.valore()).and(CARICAMENTO.LOTTO.eq(lotto)))
                .orderBy(CARICAMENTO.ID)
                .fetch(JooqCaricamentiRepository::comeCaricamento);
    }

    @Override
    public List<Caricamento> perId(WorkspaceId workspace, Collection<CaricamentoId> ids) {
        return leggi(CARICAMENTO.WORKSPACE_ID.eq(workspace.valore())
                .and(CARICAMENTO.ID.in(ids.stream().map(CaricamentoId::valore).toList())))
                .orderBy(CARICAMENTO.ID)
                .fetch(JooqCaricamentiRepository::comeCaricamento);
    }

    @Override
    public List<Caricamento> recenti(WorkspaceId workspace, int limite) {
        return leggi(CARICAMENTO.WORKSPACE_ID.eq(workspace.valore()))
                .orderBy(CARICAMENTO.CREATO_IL.desc(), CARICAMENTO.ID.desc())
                .limit(limite)
                .fetch(JooqCaricamentiRepository::comeCaricamento);
    }

    /** Caricamenti con il rapporto del cedolino creato o, in mancanza, di quello gia' presente. */
    private SelectConditionStep<Record> leggi(Condition condizione) {
        return dsl.select(CARICAMENTO.fields()).select(CEDOLINO.RAPPORTO_ID)
                .from(CARICAMENTO)
                .leftJoin(CEDOLINO).on(CEDOLINO.ID.eq(DSL.coalesce(CARICAMENTO.CEDOLINO_ID, CARICAMENTO.CEDOLINO_ESISTENTE_ID)))
                .where(condizione);
    }

    @Override
    public int interrompiNonTerminati(String messaggio) {
        return dsl.update(CARICAMENTO)
                .set(CARICAMENTO.STATO, StatoCaricamento.ERRORE.name())
                .set(CARICAMENTO.MESSAGGIO, Testo.tronca(messaggio, LUNGHEZZA_MESSAGGIO))
                .set(CARICAMENTO.AGGIORNATO_IL, OffsetDateTime.now(clock))
                .where(CARICAMENTO.STATO.in(StatoCaricamento.IN_CODA.name(), StatoCaricamento.IN_LETTURA.name()))
                .execute();
    }

    private static Caricamento comeCaricamento(CaricamentoRecord r) {
        return comeCaricamento(r, null);
    }

    private static Caricamento comeCaricamento(Record riga) {
        return comeCaricamento(riga.into(CARICAMENTO), riga.get(CEDOLINO.RAPPORTO_ID));
    }

    private static Caricamento comeCaricamento(CaricamentoRecord r, Long rapportoId) {
        return new Caricamento(
                new CaricamentoId(r.getId()),
                r.getLotto(),
                r.getNomeFileOriginale(),
                r.getDimensione(),
                StatoCaricamento.valueOf(r.getStato()),
                r.getMessaggio(),
                r.getCedolinoId() == null ? null : new CedolinoId(r.getCedolinoId()),
                r.getCedolinoEsistenteId() == null ? null : new CedolinoId(r.getCedolinoEsistenteId()),
                null,
                rapportoId == null ? null : new RapportoLavoroId(rapportoId),
                r.getCreatoIl(),
                r.getAggiornatoIl());
    }
}
