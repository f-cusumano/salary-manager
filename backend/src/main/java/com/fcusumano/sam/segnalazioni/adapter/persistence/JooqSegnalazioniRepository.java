package com.fcusumano.sam.segnalazioni.adapter.persistence;

import static com.fcusumano.sam.shared.db.jooq.Tables.SEGNALAZIONE;

import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.Segnalazione;
import com.fcusumano.sam.segnalazioni.SegnalazioneId;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import com.fcusumano.sam.segnalazioni.application.SegnalazioniRepository;
import com.fcusumano.sam.segnalazioni.domain.SegnalazioneRilevata;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.WorkspaceId;
import com.fcusumano.sam.shared.db.jooq.tables.records.SegnalazioneRecord;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/**
 * Accesso jOOQ alla tabella {@code segnalazione}. Le evidenze sono salvate come JSON ({@link EvidenzeJson}); la colonna
 * {@code ordine} conserva l'ordinamento calcolato dal motore.
 */
@Repository
class JooqSegnalazioniRepository implements SegnalazioniRepository {

    private static final int MAX_TITOLO = 200;
    private static final int MAX_DESCRIZIONE = 2000;

    private final DSLContext dsl;
    private final Clock clock;

    JooqSegnalazioniRepository(DSLContext dsl, Clock clock) {
        this.dsl = dsl;
        this.clock = clock;
    }

    @Override
    public void sostituisci(WorkspaceId workspace, RapportoLavoroId rapportoId, List<SegnalazioneRilevata> segnalazioni) {
        dsl.deleteFrom(SEGNALAZIONE)
                .where(SEGNALAZIONE.WORKSPACE_ID.eq(workspace.valore()), SEGNALAZIONE.RAPPORTO_ID.eq(rapportoId.valore()))
                .execute();
        if (segnalazioni.isEmpty()) {
            return;
        }
        var adesso = OffsetDateTime.now(clock);
        var record = new ArrayList<SegnalazioneRecord>(segnalazioni.size());
        for (int i = 0; i < segnalazioni.size(); i++) {
            var s = segnalazioni.get(i);
            var r = dsl.newRecord(SEGNALAZIONE);
            r.setWorkspaceId(workspace.valore());
            r.setRapportoId(rapportoId.valore());
            r.setCedolinoId(s.cedolinoId() == null ? null : s.cedolinoId().valore());
            r.setCodiceRegola(s.codiceRegola());
            r.setTipo(s.tipo().name());
            r.setGravita(s.gravita().name());
            r.setTitolo(tronca(s.titolo(), MAX_TITOLO));
            r.setDescrizione(tronca(s.descrizione(), MAX_DESCRIZIONE));
            r.setEvidenze(EvidenzeJson.scrivi(s.evidenze()));
            r.setCompetenzaAnno(s.competenza() == null ? null : s.competenza().anno());
            r.setCompetenzaMese(s.competenza() == null ? null : s.competenza().mese());
            r.setOrdine(i);
            r.setCreatoIl(adesso);
            record.add(r);
        }
        dsl.batchInsert(record).execute();
    }

    @Override
    public List<Segnalazione> perRapporto(WorkspaceId workspace, RapportoLavoroId rapportoId) {
        return dsl.selectFrom(SEGNALAZIONE)
                .where(SEGNALAZIONE.WORKSPACE_ID.eq(workspace.valore()), SEGNALAZIONE.RAPPORTO_ID.eq(rapportoId.valore()))
                .orderBy(SEGNALAZIONE.ORDINE, SEGNALAZIONE.ID)
                .fetch(JooqSegnalazioniRepository::comeSegnalazione);
    }

    @Override
    public boolean esistono(WorkspaceId workspace, RapportoLavoroId rapportoId) {
        return dsl.fetchExists(SEGNALAZIONE,
                SEGNALAZIONE.WORKSPACE_ID.eq(workspace.valore()), SEGNALAZIONE.RAPPORTO_ID.eq(rapportoId.valore()));
    }

    @Override
    public List<Competenza.Mensilita> competenze(WorkspaceId workspace, RapportoLavoroId rapportoId,
                                                 Set<TipoSegnalazione> tipi) {
        if (tipi.isEmpty()) {
            return List.of();
        }
        return dsl.selectDistinct(SEGNALAZIONE.COMPETENZA_ANNO, SEGNALAZIONE.COMPETENZA_MESE)
                .from(SEGNALAZIONE)
                .where(SEGNALAZIONE.WORKSPACE_ID.eq(workspace.valore()),
                        SEGNALAZIONE.RAPPORTO_ID.eq(rapportoId.valore()),
                        SEGNALAZIONE.TIPO.in(tipi.stream().map(Enum::name).toList()),
                        SEGNALAZIONE.COMPETENZA_ANNO.isNotNull(),
                        SEGNALAZIONE.COMPETENZA_MESE.isNotNull())
                .fetch(r -> new Competenza.Mensilita(r.value1(), r.value2()))
                .stream()
                .sorted(Competenza.ORDINE_CRONOLOGICO)
                .toList();
    }

    private static Segnalazione comeSegnalazione(SegnalazioneRecord r) {
        var competenza = r.getCompetenzaAnno() == null || r.getCompetenzaMese() == null ? null
                : new Competenza.Mensilita(r.getCompetenzaAnno(), r.getCompetenzaMese());
        return new Segnalazione(
                new SegnalazioneId(r.getId()),
                new RapportoLavoroId(r.getRapportoId()),
                r.getCedolinoId() == null ? null : new CedolinoId(r.getCedolinoId()),
                competenza,
                TipoSegnalazione.valueOf(r.getTipo()),
                Gravita.valueOf(r.getGravita()),
                r.getTitolo(),
                r.getDescrizione(),
                EvidenzeJson.leggi(r.getEvidenze()));
    }

    private static String tronca(String testo, int massimo) {
        return testo.length() <= massimo ? testo : testo.substring(0, massimo - 1) + "…";
    }
}
