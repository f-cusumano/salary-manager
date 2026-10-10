package com.fcusumano.sam.anagrafica.adapter.persistence;

import static com.fcusumano.sam.shared.db.jooq.Tables.AZIENDA;

import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.application.AziendeRepository;
import com.fcusumano.sam.anagrafica.domain.ProfiloAzienda;
import com.fcusumano.sam.shared.Periodi;
import com.fcusumano.sam.shared.WorkspaceId;
import com.fcusumano.sam.shared.db.jooq.tables.records.AziendaRecord;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/**
 * Accesso jOOQ alla tabella {@code azienda}.
 */
@Repository
class JooqAziendeRepository implements AziendeRepository {

    private final DSLContext dsl;
    private final Clock clock;

    JooqAziendeRepository(DSLContext dsl, Clock clock) {
        this.dsl = dsl;
        this.clock = clock;
    }

    @Override
    public Optional<ProfiloAzienda> trova(WorkspaceId workspace, AziendaId id) {
        return dsl.selectFrom(AZIENDA)
                .where(AZIENDA.WORKSPACE_ID.eq(workspace.valore()), AZIENDA.ID.eq(id.valore()))
                .fetchOptional(JooqAziendeRepository::comeProfilo);
    }

    @Override
    public List<ProfiloAzienda> trova(WorkspaceId workspace, Collection<AziendaId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return dsl.selectFrom(AZIENDA)
                .where(AZIENDA.WORKSPACE_ID.eq(workspace.valore()),
                        AZIENDA.ID.in(ids.stream().map(AziendaId::valore).toList()))
                .orderBy(AZIENDA.ID)
                .fetch(JooqAziendeRepository::comeProfilo);
    }

    @Override
    public List<ProfiloAzienda> candidate(WorkspaceId workspace, Set<String> identificativiFiscali,
                                          String posizioneInps, String ragioneSocialeNormalizzata) {
        var condizioni = new ArrayList<Condition>();
        if (!identificativiFiscali.isEmpty()) {
            condizioni.add(AZIENDA.CODICE_FISCALE.in(identificativiFiscali));
            condizioni.add(AZIENDA.PARTITA_IVA.in(identificativiFiscali));
        }
        if (posizioneInps != null) {
            condizioni.add(AZIENDA.POSIZIONE_INPS.eq(posizioneInps));
        }
        if (ragioneSocialeNormalizzata != null) {
            condizioni.add(AZIENDA.RAGIONE_SOCIALE_NORMALIZZATA.eq(ragioneSocialeNormalizzata));
        }
        if (condizioni.isEmpty()) {
            return List.of();
        }
        return dsl.selectFrom(AZIENDA)
                .where(AZIENDA.WORKSPACE_ID.eq(workspace.valore()), DSL.or(condizioni))
                .orderBy(AZIENDA.ID)
                .fetch(JooqAziendeRepository::comeProfilo);
    }

    @Override
    public Set<String> identificativiFiscaliUsati(WorkspaceId workspace, Set<String> valori, AziendaId esclusa) {
        if (valori.isEmpty()) {
            return Set.of();
        }
        var usati = new HashSet<String>();
        dsl.select(AZIENDA.CODICE_FISCALE, AZIENDA.PARTITA_IVA)
                .from(AZIENDA)
                .where(AZIENDA.WORKSPACE_ID.eq(workspace.valore()),
                        AZIENDA.ID.ne(esclusa.valore()),
                        AZIENDA.CODICE_FISCALE.in(valori).or(AZIENDA.PARTITA_IVA.in(valori)))
                .forEach(r -> {
                    if (valori.contains(r.value1())) {
                        usati.add(r.value1());
                    }
                    if (valori.contains(r.value2())) {
                        usati.add(r.value2());
                    }
                });
        return usati;
    }

    @Override
    public AziendaId inserisci(WorkspaceId workspace, ProfiloAzienda azienda) {
        var id = dsl.insertInto(AZIENDA)
                .set(AZIENDA.WORKSPACE_ID, workspace.valore())
                .set(AZIENDA.CODICE_FISCALE, azienda.codiceFiscale())
                .set(AZIENDA.PARTITA_IVA, azienda.partitaIva())
                .set(AZIENDA.POSIZIONE_INPS, azienda.posizioneInps())
                .set(AZIENDA.RAGIONE_SOCIALE, azienda.ragioneSociale())
                .set(AZIENDA.RAGIONE_SOCIALE_NORMALIZZATA, azienda.ragioneSocialeNormalizzata())
                .set(AZIENDA.INDIRIZZO, azienda.indirizzo())
                .set(AZIENDA.CODICE_AZIENDA, azienda.codiceAzienda())
                .set(AZIENDA.RIFERIMENTO_DATI, Periodi.primoGiorno(azienda.riferimento()))
                .set(AZIENDA.CREATO_IL, adesso())
                .set(AZIENDA.AGGIORNATO_IL, adesso())
                .returningResult(AZIENDA.ID)
                .fetchSingle()
                .value1();
        return new AziendaId(id);
    }

    @Override
    public void aggiorna(WorkspaceId workspace, ProfiloAzienda azienda) {
        dsl.update(AZIENDA)
                .set(AZIENDA.CODICE_FISCALE, azienda.codiceFiscale())
                .set(AZIENDA.PARTITA_IVA, azienda.partitaIva())
                .set(AZIENDA.POSIZIONE_INPS, azienda.posizioneInps())
                .set(AZIENDA.RAGIONE_SOCIALE, azienda.ragioneSociale())
                .set(AZIENDA.RAGIONE_SOCIALE_NORMALIZZATA, azienda.ragioneSocialeNormalizzata())
                .set(AZIENDA.INDIRIZZO, azienda.indirizzo())
                .set(AZIENDA.CODICE_AZIENDA, azienda.codiceAzienda())
                .set(AZIENDA.RIFERIMENTO_DATI, Periodi.primoGiorno(azienda.riferimento()))
                .set(AZIENDA.AGGIORNATO_IL, adesso())
                .where(AZIENDA.WORKSPACE_ID.eq(workspace.valore()), AZIENDA.ID.eq(azienda.id().valore()))
                .execute();
    }

    @Override
    public void elimina(WorkspaceId workspace, AziendaId id) {
        dsl.deleteFrom(AZIENDA)
                .where(AZIENDA.WORKSPACE_ID.eq(workspace.valore()), AZIENDA.ID.eq(id.valore()))
                .execute();
    }

    private OffsetDateTime adesso() {
        return OffsetDateTime.now(clock);
    }

    private static ProfiloAzienda comeProfilo(AziendaRecord r) {
        return new ProfiloAzienda(
                new AziendaId(r.getId()),
                r.getCodiceFiscale(),
                r.getPartitaIva(),
                r.getPosizioneInps(),
                r.getRagioneSociale(),
                r.getRagioneSocialeNormalizzata(),
                r.getIndirizzo(),
                r.getCodiceAzienda(),
                Periodi.meseDi(r.getRiferimentoDati()));
    }
}
