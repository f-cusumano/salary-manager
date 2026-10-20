package com.fcusumano.sam.cedolini.adapter.persistence;

import static com.fcusumano.sam.shared.db.jooq.Tables.CARICAMENTO;
import static com.fcusumano.sam.shared.db.jooq.Tables.CARICAMENTO_ATTRIBUZIONE;
import static com.fcusumano.sam.shared.db.jooq.Tables.CARICAMENTO_PDF;
import static com.fcusumano.sam.shared.db.jooq.Tables.CEDOLINO;

import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.cedolini.AttribuzioneCaricamento;
import com.fcusumano.sam.cedolini.Caricamento;
import com.fcusumano.sam.cedolini.CaricamentoId;
import com.fcusumano.sam.cedolini.CaricamentoInErrore;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.MotivoErrore;
import com.fcusumano.sam.cedolini.StatoCaricamento;
import com.fcusumano.sam.cedolini.application.CaricamentiRepository;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.WorkspaceId;
import com.fcusumano.sam.shared.db.jooq.tables.records.CaricamentoRecord;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/**
 * Accesso jOOQ alle tabelle {@code caricamento}, {@code caricamento_pdf} e {@code caricamento_attribuzione}. Ogni
 * aggiornamento dello stato e' una singola istruzione: fuori da una transazione viene committato subito (stato
 * visibile al polling). Nelle letture il rapporto si ricava dal cedolino creato o da quello gia' presente (tabella
 * {@code cedolino}, dello stesso modulo): se il cedolino viene eliminato il riferimento si azzera da solo.
 */
@Repository
class JooqCaricamentiRepository implements CaricamentiRepository {

    /** Lunghezza massima del messaggio (colonna {@code caricamento.messaggio}). */
    private static final int LUNGHEZZA_MESSAGGIO = 2000;
    /** Caricamento in ERRORE il cui file ha il PDF originale conservato. */
    private static final Field<Boolean> PDF_DISPONIBILE = DSL.field(
            CARICAMENTO.STATO.eq(StatoCaricamento.ERRORE.name()).and(DSL.exists(DSL.selectOne().from(CARICAMENTO_PDF)
                    .where(CARICAMENTO_PDF.WORKSPACE_ID.eq(CARICAMENTO.WORKSPACE_ID),
                            CARICAMENTO_PDF.HASH_CONTENUTO.eq(CARICAMENTO.HASH_CONTENUTO))))).as("pdf_disponibile");
    /** Caricamento non ancora concluso. */
    private static final Condition NON_TERMINATO =
            CARICAMENTO.STATO.in(StatoCaricamento.IN_CODA.name(), StatoCaricamento.IN_LETTURA.name());
    /** Un tentativo dello stesso file del caricamento e' in coda o in lettura. */
    private static final Field<Boolean> IN_ELABORAZIONE = inElaborazione();

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
                .set(CARICAMENTO.MOTIVO, (String) null)
                .set(CARICAMENTO.MESSAGGIO, Testo.tronca(messaggio, LUNGHEZZA_MESSAGGIO))
                .set(CARICAMENTO.CEDOLINO_ID, cedolinoId == null ? null : cedolinoId.valore())
                .set(CARICAMENTO.CEDOLINO_ESISTENTE_ID, cedolinoEsistenteId == null ? null : cedolinoEsistenteId.valore())
                .set(CARICAMENTO.AGGIORNATO_IL, OffsetDateTime.now(clock))
                .where(CARICAMENTO.WORKSPACE_ID.eq(workspace.valore()), CARICAMENTO.ID.eq(id.valore()))
                .execute();
    }

    @Override
    public void registraErrore(WorkspaceId workspace, CaricamentoId id, MotivoErrore motivo, String messaggio) {
        dsl.update(CARICAMENTO)
                .set(CARICAMENTO.STATO, StatoCaricamento.ERRORE.name())
                .set(CARICAMENTO.MOTIVO, motivo == null ? null : motivo.name())
                .set(CARICAMENTO.MESSAGGIO, Testo.tronca(messaggio, LUNGHEZZA_MESSAGGIO))
                .set(CARICAMENTO.CEDOLINO_ID, (Long) null)
                .set(CARICAMENTO.CEDOLINO_ESISTENTE_ID, (Long) null)
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

    /**
     * Caricamenti con il rapporto del cedolino creato o, in mancanza, di quello gia' presente, e la disponibilita' del
     * PDF conservato.
     */
    private SelectConditionStep<Record> leggi(Condition condizione) {
        return dsl.select(CARICAMENTO.fields()).select(CEDOLINO.RAPPORTO_ID, PDF_DISPONIBILE)
                .from(CARICAMENTO)
                .leftJoin(CEDOLINO).on(CEDOLINO.ID.eq(DSL.coalesce(CARICAMENTO.CEDOLINO_ID, CARICAMENTO.CEDOLINO_ESISTENTE_ID)))
                .where(condizione);
    }

    @Override
    public int interrompiNonTerminati(MotivoErrore motivo, String messaggioConPdf, String messaggioSenzaPdf) {
        var pdfConservato = DSL.exists(DSL.selectOne().from(CARICAMENTO_PDF)
                .where(CARICAMENTO_PDF.WORKSPACE_ID.eq(CARICAMENTO.WORKSPACE_ID),
                        CARICAMENTO_PDF.HASH_CONTENUTO.eq(CARICAMENTO.HASH_CONTENUTO)));
        return dsl.update(CARICAMENTO)
                .set(CARICAMENTO.STATO, StatoCaricamento.ERRORE.name())
                .set(CARICAMENTO.MOTIVO, motivo == null ? null : motivo.name())
                .set(CARICAMENTO.MESSAGGIO, DSL.when(pdfConservato, Testo.tronca(messaggioConPdf, LUNGHEZZA_MESSAGGIO))
                        .otherwise(Testo.tronca(messaggioSenzaPdf, LUNGHEZZA_MESSAGGIO)))
                .set(CARICAMENTO.AGGIORNATO_IL, OffsetDateTime.now(clock))
                .where(NON_TERMINATO)
                .execute();
    }

    @Override
    public int scartaNonTerminatiGiaArchiviati(String messaggio) {
        var archiviato = DSL.select(CEDOLINO.ID).from(CEDOLINO)
                .where(CEDOLINO.WORKSPACE_ID.eq(CARICAMENTO.WORKSPACE_ID),
                        CEDOLINO.HASH_CONTENUTO.eq(CARICAMENTO.HASH_CONTENUTO));
        return dsl.update(CARICAMENTO)
                .set(CARICAMENTO.STATO, StatoCaricamento.DUPLICATO.name())
                .set(CARICAMENTO.MOTIVO, (String) null)
                .set(CARICAMENTO.MESSAGGIO, Testo.tronca(messaggio, LUNGHEZZA_MESSAGGIO))
                .set(CARICAMENTO.CEDOLINO_ID, (Long) null)
                .set(CARICAMENTO.CEDOLINO_ESISTENTE_ID, archiviato.asField())
                .set(CARICAMENTO.AGGIORNATO_IL, OffsetDateTime.now(clock))
                .where(NON_TERMINATO)
                .andExists(archiviato)
                .execute();
    }

    @Override
    public Set<RapportoAssociato> rapportiAssociatiANonTerminati() {
        return dsl.selectDistinct(CARICAMENTO_ATTRIBUZIONE.WORKSPACE_ID, CARICAMENTO_ATTRIBUZIONE.RAPPORTO_ID)
                .from(CARICAMENTO_ATTRIBUZIONE)
                .whereExists(DSL.selectOne().from(CARICAMENTO)
                        .where(CARICAMENTO.WORKSPACE_ID.eq(CARICAMENTO_ATTRIBUZIONE.WORKSPACE_ID),
                                CARICAMENTO.HASH_CONTENUTO.eq(CARICAMENTO_ATTRIBUZIONE.HASH_CONTENUTO), NON_TERMINATO))
                .fetchSet(r -> new RapportoAssociato(new WorkspaceId(r.value1()), new RapportoLavoroId(r.value2())));
    }

    @Override
    public Set<RapportoAssociato> rapportiAssociatiAFileNonInErrore() {
        return dsl.selectDistinct(CARICAMENTO_ATTRIBUZIONE.WORKSPACE_ID, CARICAMENTO_ATTRIBUZIONE.RAPPORTO_ID)
                .from(CARICAMENTO_ATTRIBUZIONE)
                .where(DSL.not(fileInErrore(CARICAMENTO_ATTRIBUZIONE.WORKSPACE_ID,
                        CARICAMENTO_ATTRIBUZIONE.HASH_CONTENUTO)))
                .fetchSet(r -> new RapportoAssociato(new WorkspaceId(r.value1()), new RapportoLavoroId(r.value2())));
    }

    @Override
    public int chiudiErroriSuccessivi(WorkspaceId workspace, String hash, CaricamentoId acquisito, CedolinoId cedolino,
                                      String messaggio) {
        return dsl.update(CARICAMENTO)
                .set(CARICAMENTO.STATO, StatoCaricamento.DUPLICATO.name())
                .set(CARICAMENTO.MOTIVO, (String) null)
                .set(CARICAMENTO.MESSAGGIO, Testo.tronca(messaggio, LUNGHEZZA_MESSAGGIO))
                .set(CARICAMENTO.CEDOLINO_ID, (Long) null)
                .set(CARICAMENTO.CEDOLINO_ESISTENTE_ID, cedolino.valore())
                .set(CARICAMENTO.AGGIORNATO_IL, OffsetDateTime.now(clock))
                .where(CARICAMENTO.WORKSPACE_ID.eq(workspace.valore()), CARICAMENTO.HASH_CONTENUTO.eq(hash),
                        CARICAMENTO.STATO.eq(StatoCaricamento.ERRORE.name()), CARICAMENTO.ID.gt(acquisito.valore()))
                .execute();
    }

    @Override
    public boolean fileInErrore(WorkspaceId workspace, String hash) {
        return Boolean.TRUE.equals(dsl.select(DSL.field(fileInErrore(DSL.val(workspace.valore()), DSL.val(hash))))
                .fetchOne(Record1::value1));
    }

    @Override
    public void salvaPdfSeAssente(WorkspaceId workspace, String hash, byte[] contenuto) {
        dsl.insertInto(CARICAMENTO_PDF)
                .set(CARICAMENTO_PDF.WORKSPACE_ID, workspace.valore())
                .set(CARICAMENTO_PDF.HASH_CONTENUTO, hash)
                .set(CARICAMENTO_PDF.DIMENSIONE, (long) contenuto.length)
                .set(CARICAMENTO_PDF.CONTENUTO, contenuto)
                .set(CARICAMENTO_PDF.CREATO_IL, OffsetDateTime.now(clock))
                .onDuplicateKeyIgnore()
                .execute();
    }

    @Override
    public void salvaPdfSbloccato(WorkspaceId workspace, String hash, byte[] contenuto) {
        dsl.insertInto(CARICAMENTO_PDF)
                .set(CARICAMENTO_PDF.WORKSPACE_ID, workspace.valore())
                .set(CARICAMENTO_PDF.HASH_CONTENUTO, hash)
                .set(CARICAMENTO_PDF.DIMENSIONE, (long) contenuto.length)
                .set(CARICAMENTO_PDF.CONTENUTO, contenuto)
                .set(CARICAMENTO_PDF.CREATO_IL, OffsetDateTime.now(clock))
                .onDuplicateKeyUpdate()
                .set(CARICAMENTO_PDF.DIMENSIONE, (long) contenuto.length)
                .set(CARICAMENTO_PDF.CONTENUTO, contenuto)
                .execute();
    }

    @Override
    public void eliminaPdf(WorkspaceId workspace, String hash) {
        dsl.deleteFrom(CARICAMENTO_PDF)
                .where(CARICAMENTO_PDF.WORKSPACE_ID.eq(workspace.valore()), CARICAMENTO_PDF.HASH_CONTENUTO.eq(hash))
                .execute();
    }

    @Override
    public Optional<byte[]> pdf(WorkspaceId workspace, String hash) {
        return dsl.select(CARICAMENTO_PDF.CONTENUTO)
                .from(CARICAMENTO_PDF)
                .where(CARICAMENTO_PDF.WORKSPACE_ID.eq(workspace.valore()), CARICAMENTO_PDF.HASH_CONTENUTO.eq(hash))
                .fetchOptional(Record1::value1);
    }

    @Override
    public Optional<RapportoLavoroId> eliminaAttribuzione(WorkspaceId workspace, String hash) {
        var attribuzioneDelFile = CARICAMENTO_ATTRIBUZIONE.WORKSPACE_ID.eq(workspace.valore())
                .and(CARICAMENTO_ATTRIBUZIONE.HASH_CONTENUTO.eq(hash));
        var rapporto = dsl.select(CARICAMENTO_ATTRIBUZIONE.RAPPORTO_ID)
                .from(CARICAMENTO_ATTRIBUZIONE)
                .where(attribuzioneDelFile)
                .fetchOptional(r -> new RapportoLavoroId(r.value1()));
        if (rapporto.isPresent()) {
            dsl.deleteFrom(CARICAMENTO_ATTRIBUZIONE).where(attribuzioneDelFile).execute();
        }
        return rapporto;
    }

    @Override
    public Optional<AttribuzioneSalvata> attribuzione(WorkspaceId workspace, String hash) {
        return dsl.select(CARICAMENTO_ATTRIBUZIONE.RAPPORTO_ID, CARICAMENTO_ATTRIBUZIONE.COMPETENZA_ANNO,
                        CARICAMENTO_ATTRIBUZIONE.COMPETENZA_MESE)
                .from(CARICAMENTO_ATTRIBUZIONE)
                .where(CARICAMENTO_ATTRIBUZIONE.WORKSPACE_ID.eq(workspace.valore()),
                        CARICAMENTO_ATTRIBUZIONE.HASH_CONTENUTO.eq(hash))
                .fetchOptional(r -> new AttribuzioneSalvata(new RapportoLavoroId(r.value1()),
                        new Competenza.Mensilita(r.value2(), r.value3())));
    }

    @Override
    public void attribuisci(WorkspaceId workspace, String hash, RapportoLavoroId rapportoId,
                            Competenza.Mensilita competenza) {
        dsl.insertInto(CARICAMENTO_ATTRIBUZIONE)
                .set(CARICAMENTO_ATTRIBUZIONE.WORKSPACE_ID, workspace.valore())
                .set(CARICAMENTO_ATTRIBUZIONE.HASH_CONTENUTO, hash)
                .set(CARICAMENTO_ATTRIBUZIONE.RAPPORTO_ID, rapportoId.valore())
                .set(CARICAMENTO_ATTRIBUZIONE.COMPETENZA_ANNO, competenza.anno())
                .set(CARICAMENTO_ATTRIBUZIONE.COMPETENZA_MESE, competenza.mese())
                .set(CARICAMENTO_ATTRIBUZIONE.ATTRIBUITO_IL, OffsetDateTime.now(clock))
                .execute();
    }

    @Override
    public Optional<String> hashAssociatoA(WorkspaceId workspace, RapportoLavoroId rapportoId,
                                           Competenza.Mensilita competenza) {
        return dsl.select(CARICAMENTO_ATTRIBUZIONE.HASH_CONTENUTO)
                .from(CARICAMENTO_ATTRIBUZIONE)
                .where(CARICAMENTO_ATTRIBUZIONE.WORKSPACE_ID.eq(workspace.valore()),
                        CARICAMENTO_ATTRIBUZIONE.RAPPORTO_ID.eq(rapportoId.valore()),
                        CARICAMENTO_ATTRIBUZIONE.COMPETENZA_ANNO.eq(competenza.anno()),
                        CARICAMENTO_ATTRIBUZIONE.COMPETENZA_MESE.eq(competenza.mese()))
                .fetchOptional(Record1::value1);
    }

    @Override
    public int ripulisciFileNonInErrore() {
        int pdf = dsl.deleteFrom(CARICAMENTO_PDF)
                .where(DSL.not(fileInErrore(CARICAMENTO_PDF.WORKSPACE_ID, CARICAMENTO_PDF.HASH_CONTENUTO)))
                .execute();
        int attribuzioni = dsl.deleteFrom(CARICAMENTO_ATTRIBUZIONE)
                .where(DSL.not(fileInErrore(CARICAMENTO_ATTRIBUZIONE.WORKSPACE_ID,
                        CARICAMENTO_ATTRIBUZIONE.HASH_CONTENUTO)))
                .execute();
        return pdf + attribuzioni;
    }

    @Override
    public List<CaricamentoInErrore> inErrore(WorkspaceId workspace) {
        return righeInErrore(CARICAMENTO.WORKSPACE_ID.eq(workspace.valore()));
    }

    @Override
    public List<CaricamentoInErrore> inErroreDiRapporto(WorkspaceId workspace, RapportoLavoroId rapportoId) {
        return righeInErrore(CARICAMENTO.WORKSPACE_ID.eq(workspace.valore())
                .and(CARICAMENTO_ATTRIBUZIONE.RAPPORTO_ID.eq(rapportoId.valore())));
    }

    @Override
    public Optional<CaricamentoInErrore> inErrore(WorkspaceId workspace, String hash) {
        return righeInErrore(CARICAMENTO.WORKSPACE_ID.eq(workspace.valore())
                .and(CARICAMENTO.HASH_CONTENUTO.eq(hash))).stream().findFirst();
    }

    @Override
    public Optional<FileDelCaricamento> fileDelCaricamento(WorkspaceId workspace, CaricamentoId id) {
        var inErrore = DSL.field(fileInErrore(CARICAMENTO.WORKSPACE_ID, CARICAMENTO.HASH_CONTENUTO)).as("in_errore");
        var pdfPresente = DSL.field(DSL.exists(DSL.selectOne().from(CARICAMENTO_PDF)
                .where(CARICAMENTO_PDF.WORKSPACE_ID.eq(CARICAMENTO.WORKSPACE_ID),
                        CARICAMENTO_PDF.HASH_CONTENUTO.eq(CARICAMENTO.HASH_CONTENUTO)))).as("pdf_presente");
        return dsl.select(CARICAMENTO.STATO, CARICAMENTO.NOME_FILE_ORIGINALE, CARICAMENTO.DIMENSIONE,
                        CARICAMENTO.HASH_CONTENUTO, inErrore, IN_ELABORAZIONE, pdfPresente)
                .from(CARICAMENTO)
                .where(CARICAMENTO.WORKSPACE_ID.eq(workspace.valore()), CARICAMENTO.ID.eq(id.valore()))
                .fetchOptional(r -> new FileDelCaricamento(StatoCaricamento.valueOf(r.value1()), r.value2(),
                        r.value3(), r.value4(), Boolean.TRUE.equals(r.value5()), Boolean.TRUE.equals(r.value6()),
                        Boolean.TRUE.equals(r.value7())));
    }

    @Override
    public int eliminaErroriDelFile(WorkspaceId workspace, String hash) {
        return dsl.deleteFrom(CARICAMENTO)
                .where(CARICAMENTO.WORKSPACE_ID.eq(workspace.valore()), CARICAMENTO.HASH_CONTENUTO.eq(hash),
                        CARICAMENTO.STATO.eq(StatoCaricamento.ERRORE.name()))
                .execute();
    }

    /**
     * Righe dei file in errore: tentativi in ERRORE senza tentativi successivi conclusi, di file non archiviati, con
     * l'associazione del file e se nel frattempo e' stato archiviato un cedolino della mensilita' associata.
     */
    private List<CaricamentoInErrore> righeInErrore(Condition condizione) {
        var successivo = CARICAMENTO.as("successivo");
        var competenzaPresente = DSL.field(DSL.exists(DSL.selectOne().from(CEDOLINO)
                .where(CEDOLINO.RAPPORTO_ID.eq(CARICAMENTO_ATTRIBUZIONE.RAPPORTO_ID),
                        CEDOLINO.ANNO.eq(CARICAMENTO_ATTRIBUZIONE.COMPETENZA_ANNO),
                        CEDOLINO.MESE.eq(CARICAMENTO_ATTRIBUZIONE.COMPETENZA_MESE)))).as("competenza_presente");
        return dsl.select(CARICAMENTO.fields())
                .select(PDF_DISPONIBILE, IN_ELABORAZIONE, CARICAMENTO_ATTRIBUZIONE.RAPPORTO_ID,
                        CARICAMENTO_ATTRIBUZIONE.COMPETENZA_ANNO, CARICAMENTO_ATTRIBUZIONE.COMPETENZA_MESE,
                        competenzaPresente)
                .from(CARICAMENTO)
                .leftJoin(CARICAMENTO_ATTRIBUZIONE)
                .on(CARICAMENTO_ATTRIBUZIONE.WORKSPACE_ID.eq(CARICAMENTO.WORKSPACE_ID),
                        CARICAMENTO_ATTRIBUZIONE.HASH_CONTENUTO.eq(CARICAMENTO.HASH_CONTENUTO))
                .where(condizione, CARICAMENTO.STATO.eq(StatoCaricamento.ERRORE.name()))
                .andNotExists(DSL.selectOne().from(CEDOLINO)
                        .where(CEDOLINO.WORKSPACE_ID.eq(CARICAMENTO.WORKSPACE_ID),
                                CEDOLINO.HASH_CONTENUTO.eq(CARICAMENTO.HASH_CONTENUTO)))
                .andNotExists(DSL.selectOne().from(successivo)
                        .where(successivo.WORKSPACE_ID.eq(CARICAMENTO.WORKSPACE_ID),
                                successivo.HASH_CONTENUTO.eq(CARICAMENTO.HASH_CONTENUTO),
                                successivo.ID.gt(CARICAMENTO.ID),
                                successivo.STATO.notIn(StatoCaricamento.IN_CODA.name(),
                                        StatoCaricamento.IN_LETTURA.name())))
                .orderBy(CARICAMENTO.CREATO_IL.desc(), CARICAMENTO.ID.desc())
                .fetch(riga -> {
                    var r = riga.into(CARICAMENTO);
                    var rapporto = riga.get(CARICAMENTO_ATTRIBUZIONE.RAPPORTO_ID);
                    var attribuzione = rapporto == null ? null : new AttribuzioneCaricamento(
                            new RapportoLavoroId(rapporto),
                            new Competenza.Mensilita(riga.get(CARICAMENTO_ATTRIBUZIONE.COMPETENZA_ANNO),
                                    riga.get(CARICAMENTO_ATTRIBUZIONE.COMPETENZA_MESE)),
                            Boolean.TRUE.equals(riga.get(competenzaPresente)));
                    return new CaricamentoInErrore(
                            new CaricamentoId(r.getId()),
                            r.getLotto(),
                            r.getNomeFileOriginale(),
                            r.getDimensione(),
                            r.getMotivo() == null ? null : MotivoErrore.valueOf(r.getMotivo()),
                            r.getMessaggio(),
                            Boolean.TRUE.equals(riga.get(PDF_DISPONIBILE)),
                            Boolean.TRUE.equals(riga.get(IN_ELABORAZIONE)),
                            attribuzione,
                            r.getCreatoIl());
                });
    }

    /**
     * Il file (workspace + hash) e' in errore: il suo ultimo tentativo concluso e' in ERRORE (eventuali tentativi
     * successivi sono ancora in corso) e nessun cedolino archiviato ha quell'hash.
     */
    private static Condition fileInErrore(Field<Long> workspace, Field<String> hash) {
        var errore = CARICAMENTO.as("errore");
        var successivo = CARICAMENTO.as("successivo");
        return DSL.exists(DSL.selectOne().from(errore)
                        .where(errore.WORKSPACE_ID.eq(workspace), errore.HASH_CONTENUTO.eq(hash),
                                errore.STATO.eq(StatoCaricamento.ERRORE.name()))
                        .andNotExists(DSL.selectOne().from(successivo)
                                .where(successivo.WORKSPACE_ID.eq(workspace), successivo.HASH_CONTENUTO.eq(hash),
                                        successivo.ID.gt(errore.ID),
                                        successivo.STATO.notIn(StatoCaricamento.IN_CODA.name(),
                                                StatoCaricamento.IN_LETTURA.name()))))
                .andNotExists(DSL.selectOne().from(CEDOLINO)
                        .where(CEDOLINO.WORKSPACE_ID.eq(workspace), CEDOLINO.HASH_CONTENUTO.eq(hash)));
    }

    private static Field<Boolean> inElaborazione() {
        var tentativo = CARICAMENTO.as("tentativo");
        return DSL.field(DSL.exists(DSL.selectOne().from(tentativo)
                .where(tentativo.WORKSPACE_ID.eq(CARICAMENTO.WORKSPACE_ID),
                        tentativo.HASH_CONTENUTO.eq(CARICAMENTO.HASH_CONTENUTO),
                        tentativo.STATO.in(StatoCaricamento.IN_CODA.name(), StatoCaricamento.IN_LETTURA.name()))))
                .as("in_elaborazione");
    }

    private static Caricamento comeCaricamento(CaricamentoRecord r) {
        return comeCaricamento(r, null, false);
    }

    private static Caricamento comeCaricamento(Record riga) {
        return comeCaricamento(riga.into(CARICAMENTO), riga.get(CEDOLINO.RAPPORTO_ID),
                Boolean.TRUE.equals(riga.get(PDF_DISPONIBILE)));
    }

    private static Caricamento comeCaricamento(CaricamentoRecord r, Long rapportoId, boolean pdfDisponibile) {
        return new Caricamento(
                new CaricamentoId(r.getId()),
                r.getLotto(),
                r.getNomeFileOriginale(),
                r.getDimensione(),
                StatoCaricamento.valueOf(r.getStato()),
                r.getMessaggio(),
                r.getMotivo() == null ? null : MotivoErrore.valueOf(r.getMotivo()),
                pdfDisponibile,
                r.getCedolinoId() == null ? null : new CedolinoId(r.getCedolinoId()),
                r.getCedolinoEsistenteId() == null ? null : new CedolinoId(r.getCedolinoEsistenteId()),
                null,
                rapportoId == null ? null : new RapportoLavoroId(rapportoId),
                r.getCreatoIl(),
                r.getAggiornatoIl());
    }
}
