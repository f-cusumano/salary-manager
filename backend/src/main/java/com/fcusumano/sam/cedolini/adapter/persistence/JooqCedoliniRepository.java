package com.fcusumano.sam.cedolini.adapter.persistence;

import static com.fcusumano.sam.shared.db.jooq.Tables.CEDOLINO;
import static com.fcusumano.sam.shared.db.jooq.Tables.CEDOLINO_DATI;
import static com.fcusumano.sam.shared.db.jooq.Tables.CEDOLINO_ELEMENTO_RETRIBUTIVO;
import static com.fcusumano.sam.shared.db.jooq.Tables.CEDOLINO_PDF;
import static com.fcusumano.sam.shared.db.jooq.Tables.CEDOLINO_RATEO;
import static com.fcusumano.sam.shared.db.jooq.Tables.CEDOLINO_TFR;
import static com.fcusumano.sam.shared.db.jooq.Tables.CEDOLINO_VOCE;

import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.cedolini.BuoniPasto;
import com.fcusumano.sam.cedolini.CategoriaVoce;
import com.fcusumano.sam.cedolini.CedolinoDettaglio;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.cedolini.CedolinoSintesi;
import com.fcusumano.sam.cedolini.DatiTfr;
import com.fcusumano.sam.cedolini.ElementiRetributivi;
import com.fcusumano.sam.cedolini.PdfCedolino;
import com.fcusumano.sam.cedolini.Presenze;
import com.fcusumano.sam.cedolini.ProgressiviAnnui;
import com.fcusumano.sam.cedolini.Rateo;
import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.cedolini.Voce;
import com.fcusumano.sam.cedolini.application.CedoliniRepository;
import com.fcusumano.sam.cedolini.application.ModelliEstrazione;
import com.fcusumano.sam.cedolini.domain.ClassificazioneRapporto;
import com.fcusumano.sam.cedolini.domain.ConfrontoCedolini.ImportiChiave;
import com.fcusumano.sam.cedolini.domain.EstremiCedolini.PeriodoCedolino;
import com.fcusumano.sam.cedolini.domain.TipoDichiarato;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.Importi;
import com.fcusumano.sam.shared.Periodi;
import com.fcusumano.sam.shared.TipoMensilita;
import com.fcusumano.sam.shared.UnitaMisura;
import com.fcusumano.sam.shared.WorkspaceId;
import com.fcusumano.sam.shared.db.jooq.tables.records.CedolinoDatiRecord;
import com.fcusumano.sam.shared.db.jooq.tables.records.CedolinoElementoRetributivoRecord;
import com.fcusumano.sam.shared.db.jooq.tables.records.CedolinoRecord;
import com.fcusumano.sam.shared.db.jooq.tables.records.CedolinoTfrRecord;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/**
 * Accesso jOOQ alle tabelle {@code cedolino}, {@code cedolino_pdf}, {@code cedolino_voce},
 * {@code cedolino_elemento_retributivo}, {@code cedolino_rateo}, {@code cedolino_tfr}, {@code cedolino_dati}.
 * I periodi (mesi) sono memorizzati come primo giorno del mese, gli importi con scala 5.
 */
@Repository
class JooqCedoliniRepository implements CedoliniRepository {

    /** Separatore delle righe nelle colonne {@code campi_non_trovati} e {@code avvisi_estrazione}. */
    private static final String SEPARATORE_RIGHE = "\n";
    /** Lunghezza delle colonne {@code campi_non_trovati} e {@code avvisi_estrazione}. */
    private static final int LUNGHEZZA_ELENCHI = 4000;
    /** Scostamento dei progressivi temporanei usati durante la rinumerazione (oltre il massimo ammesso, 49). */
    private static final int PROGRESSIVO_TEMPORANEO = 1000;

    private final DSLContext dsl;
    private final Clock clock;

    JooqCedoliniRepository(DSLContext dsl, Clock clock) {
        this.dsl = dsl;
        this.clock = clock;
    }

    @Override
    public Optional<CedolinoId> perHash(WorkspaceId workspace, String hash) {
        return dsl.select(CEDOLINO.ID).from(CEDOLINO)
                .where(CEDOLINO.WORKSPACE_ID.eq(workspace.valore()), CEDOLINO.HASH_CONTENUTO.eq(hash))
                .fetchOptional(r -> new CedolinoId(r.value1()));
    }

    @Override
    public Optional<CedolinoConImporti> perChiave(WorkspaceId workspace, RapportoLavoroId rapportoId,
                                                  Competenza competenza) {
        var condizione = switch (competenza) {
            case Competenza.Mensilita m -> CEDOLINO.ANNO.eq(m.anno()).and(CEDOLINO.MESE.eq(m.mese()));
            case Competenza.FineRapporto f ->
                    CEDOLINO.PERIODO_FINE_RAPPORTO.eq(Periodi.primoGiorno(f.periodoRetribuzione()));
        };
        return dsl.selectFrom(CEDOLINO)
                .where(CEDOLINO.WORKSPACE_ID.eq(workspace.valore()), CEDOLINO.RAPPORTO_ID.eq(rapportoId.valore()))
                .and(condizione)
                .fetchOptional(r -> new CedolinoConImporti(new CedolinoId(r.getId()), competenza(r),
                        new ImportiChiave(r.getNetto(), r.getLordo(), r.getTotaleCompetenze(), r.getTotaleTrattenute())));
    }

    @Override
    public List<ClassificazioneRapporto.Cedolino> classificabili(WorkspaceId workspace, RapportoLavoroId rapportoId) {
        return dsl.select(CEDOLINO.ID, CEDOLINO.TIPO, CEDOLINO.TIPO_DICHIARATO, CEDOLINO.ANNO, CEDOLINO.MESE,
                        CEDOLINO.PROGRESSIVO_TFR, CEDOLINO.PERIODO_RETRIBUZIONE, CEDOLINO.PERIODO_FINE_RAPPORTO,
                        CEDOLINO_DATI.DATA_ASSUNZIONE)
                .from(CEDOLINO)
                .leftJoin(CEDOLINO_DATI).on(CEDOLINO_DATI.CEDOLINO_ID.eq(CEDOLINO.ID))
                .where(CEDOLINO.WORKSPACE_ID.eq(workspace.valore()), CEDOLINO.RAPPORTO_ID.eq(rapportoId.valore()))
                .orderBy(CEDOLINO.ID)
                .fetch(r -> {
                    var cedolino = r.into(CEDOLINO);
                    return new ClassificazioneRapporto.Cedolino(cedolino.getId(),
                            TipoDichiarato.valueOf(cedolino.getTipoDichiarato()),
                            YearMonth.from(cedolino.getPeriodoRetribuzione()), r.get(CEDOLINO_DATI.DATA_ASSUNZIONE),
                            competenza(cedolino), false);
                });
    }

    @Override
    public List<PeriodoCedolino> periodi(WorkspaceId workspace, RapportoLavoroId rapportoId) {
        return dsl.select(CEDOLINO.TIPO, CEDOLINO.PERIODO_RETRIBUZIONE).from(CEDOLINO)
                .where(CEDOLINO.WORKSPACE_ID.eq(workspace.valore()), CEDOLINO.RAPPORTO_ID.eq(rapportoId.valore()))
                .fetch(r -> new PeriodoCedolino(TipoMensilita.valueOf(r.value1()), YearMonth.from(r.value2())));
    }

    @Override
    public CedolinoId inserisci(WorkspaceId workspace, NuovoCedolino nuovo) {
        var d = nuovo.dati();
        var competenza = d.competenza();
        var record = dsl.newRecord(CEDOLINO);
        record.setWorkspaceId(workspace.valore());
        record.setRapportoId(d.rapportoId().valore());
        record.setTipo(competenza.tipo().name());
        record.setTipoDichiarato(nuovo.tipoDichiarato().name());
        record.setAnno(competenza.anno());
        record.setPeriodoRetribuzione(Periodi.primoGiorno(d.periodoRetribuzione()));
        switch (competenza) {
            case Competenza.Mensilita m -> record.setMese(m.mese());
            case Competenza.FineRapporto f -> {
                record.setProgressivoTfr(f.progressivo());
                record.setPeriodoFineRapporto(Periodi.primoGiorno(f.periodoRetribuzione()));
            }
        }
        record.setDataElaborazione(d.dataElaborazione());
        record.setLordo(Importi.normalizza(d.lordo()));
        record.setNetto(Importi.normalizza(d.netto()));
        record.setTotaleCompetenze(Importi.normalizza(d.totaleCompetenze()));
        record.setTotaleTrattenute(Importi.normalizza(d.totaleTrattenute()));
        record.setArrotondamento(Importi.normalizza(d.arrotondamento()));
        record.setNomeFile(nuovo.nomeFile());
        record.setHashContenuto(nuovo.hash());
        record.setEstrattoJson(nuovo.jsonEstrazione());
        record.setConfidenza(nuovo.confidenza());
        record.setCampiNonTrovati(Testo.tronca(String.join(SEPARATORE_RIGHE, nuovo.campiNonTrovati()), LUNGHEZZA_ELENCHI));
        record.setAvvisiEstrazione(Testo.elenco(nuovo.avvisiEstrazione(), SEPARATORE_RIGHE, LUNGHEZZA_ELENCHI));
        record.setModelloEstrazione(Testo.tronca(nuovo.modello(), 100));
        record.setAcquisitoIl(OffsetDateTime.now(clock));
        record.store();
        long id = record.getId();

        inserisciPdf(workspace, id, nuovo.pdf(), nuovo.pdfProtetto());
        inserisciDati(workspace, id, d);
        inserisciElementi(workspace, id, d.elementiRetributivi());
        if (d.tfr() != null) {
            inserisciTfr(workspace, id, d.tfr());
        }
        inserisciVoci(workspace, id, d.voci());
        inserisciRatei(workspace, id, d.ratei());
        return new CedolinoId(id);
    }

    private void inserisciPdf(WorkspaceId workspace, long id, byte[] pdf, Boolean protetto) {
        dsl.insertInto(CEDOLINO_PDF)
                .set(CEDOLINO_PDF.CEDOLINO_ID, id)
                .set(CEDOLINO_PDF.WORKSPACE_ID, workspace.valore())
                .set(CEDOLINO_PDF.CONTENT_TYPE, "application/pdf")
                .set(CEDOLINO_PDF.DIMENSIONE, (long) pdf.length)
                .set(CEDOLINO_PDF.CONTENUTO, pdf)
                .set(CEDOLINO_PDF.PROTETTO, protetto)
                .execute();
    }

    @Override
    public List<PdfDaSbloccare> pdfDaSbloccare() {
        return dsl.select(CEDOLINO_PDF.WORKSPACE_ID, CEDOLINO_PDF.CEDOLINO_ID)
                .from(CEDOLINO_PDF)
                .where(CEDOLINO_PDF.PROTETTO.isNull().or(CEDOLINO_PDF.PROTETTO.isTrue()))
                .orderBy(CEDOLINO_PDF.WORKSPACE_ID, CEDOLINO_PDF.CEDOLINO_ID)
                .fetch(r -> new PdfDaSbloccare(new WorkspaceId(r.value1()), new CedolinoId(r.value2())));
    }

    @Override
    public List<CedolinoId> pdfDaSbloccare(WorkspaceId workspace) {
        return dsl.select(CEDOLINO_PDF.CEDOLINO_ID)
                .from(CEDOLINO_PDF)
                .where(CEDOLINO_PDF.WORKSPACE_ID.eq(workspace.valore()),
                        CEDOLINO_PDF.PROTETTO.isNull().or(CEDOLINO_PDF.PROTETTO.isTrue()))
                .orderBy(CEDOLINO_PDF.CEDOLINO_ID)
                .fetch(r -> new CedolinoId(r.value1()));
    }

    @Override
    public void aggiornaPdf(WorkspaceId workspace, CedolinoId id, byte[] contenuto, boolean protetto) {
        var aggiornamento = dsl.update(CEDOLINO_PDF).set(CEDOLINO_PDF.PROTETTO, protetto);
        if (contenuto != null) {
            aggiornamento = aggiornamento.set(CEDOLINO_PDF.CONTENUTO, contenuto)
                    .set(CEDOLINO_PDF.DIMENSIONE, (long) contenuto.length);
        }
        aggiornamento.where(CEDOLINO_PDF.WORKSPACE_ID.eq(workspace.valore()), CEDOLINO_PDF.CEDOLINO_ID.eq(id.valore()))
                .execute();
    }

    private void inserisciDati(WorkspaceId workspace, long id, CedolinoNormalizzato d) {
        dsl.insertInto(CEDOLINO_DATI)
                .set(CEDOLINO_DATI.CEDOLINO_ID, id)
                .set(CEDOLINO_DATI.WORKSPACE_ID, workspace.valore())
                .set(CEDOLINO_DATI.DATA_ASSUNZIONE, d.dataAssunzione())
                .set(CEDOLINO_DATI.DATA_CESSAZIONE, d.dataCessazione())
                .set(CEDOLINO_DATI.QUALIFICA, Testo.tronca(d.qualifica(), 100))
                .set(CEDOLINO_DATI.LIVELLO, Testo.tronca(d.livello(), 50))
                .set(CEDOLINO_DATI.CODICE_DIPENDENTE, Testo.tronca(d.codiceDipendente(), 50))
                .set(CEDOLINO_DATI.CCNL_TESTO, Testo.tronca(d.ccnlTesto(), 300))
                .set(CEDOLINO_DATI.NUMERO_SCATTI, d.numeroScatti())
                .set(CEDOLINO_DATI.PROSSIMO_SCATTO, d.prossimoScatto())
                .set(CEDOLINO_DATI.BUONI_PASTO, d.buoniPasto().presenti())
                .set(CEDOLINO_DATI.BUONI_PASTO_NUMERO, Importi.normalizza(d.buoniPasto().numero()))
                .set(CEDOLINO_DATI.BUONI_PASTO_VALORE_UNITARIO, Importi.normalizza(d.buoniPasto().valoreUnitario()))
                .set(CEDOLINO_DATI.BUONI_PASTO_IMPORTO, Importi.normalizza(d.buoniPasto().importo()))
                .set(CEDOLINO_DATI.CONTIENE_TREDICESIMA, d.contieneTredicesima())
                .set(CEDOLINO_DATI.CONTIENE_QUATTORDICESIMA, d.contieneQuattordicesima())
                .set(CEDOLINO_DATI.GIORNI_LAVORATI, Importi.normalizza(d.presenze().giorniLavorati()))
                .set(CEDOLINO_DATI.ORE_LAVORATE, Importi.normalizza(d.presenze().oreLavorate()))
                .set(CEDOLINO_DATI.GIORNI_RETRIBUITI, Importi.normalizza(d.presenze().giorniRetribuiti()))
                .set(CEDOLINO_DATI.MESE_VARIABILI, Periodi.primoGiorno(d.meseVariabili()))
                .set(CEDOLINO_DATI.PROGRESSIVO_IMPONIBILE_INPS,
                        Importi.normalizza(d.progressivi().imponibilePrevidenziale()))
                .set(CEDOLINO_DATI.PROGRESSIVO_IMPONIBILE_IRPEF, Importi.normalizza(d.progressivi().imponibileIrpef()))
                .set(CEDOLINO_DATI.PROGRESSIVO_IRPEF, Importi.normalizza(d.progressivi().irpefTrattenuta()))
                .execute();
    }

    private void inserisciElementi(WorkspaceId workspace, long id, ElementiRetributivi e) {
        if (e.equals(ElementiRetributivi.VUOTI)) {
            return;
        }
        dsl.insertInto(CEDOLINO_ELEMENTO_RETRIBUTIVO)
                .set(CEDOLINO_ELEMENTO_RETRIBUTIVO.CEDOLINO_ID, id)
                .set(CEDOLINO_ELEMENTO_RETRIBUTIVO.WORKSPACE_ID, workspace.valore())
                .set(CEDOLINO_ELEMENTO_RETRIBUTIVO.MINIMO, Importi.normalizza(e.minimo()))
                .set(CEDOLINO_ELEMENTO_RETRIBUTIVO.CONTINGENZA, Importi.normalizza(e.contingenza()))
                .set(CEDOLINO_ELEMENTO_RETRIBUTIVO.SUPERMINIMO, Importi.normalizza(e.superminimo()))
                .set(CEDOLINO_ELEMENTO_RETRIBUTIVO.SUPERMINIMO_ASSORBIBILE, e.superminimoAssorbibile())
                .set(CEDOLINO_ELEMENTO_RETRIBUTIVO.IMPORTO_SCATTI, Importi.normalizza(e.importoScatti()))
                .set(CEDOLINO_ELEMENTO_RETRIBUTIVO.ALTRI_ELEMENTI_FISSI, Importi.normalizza(e.altriElementiFissi()))
                .set(CEDOLINO_ELEMENTO_RETRIBUTIVO.RETRIBUZIONE_BASE, Importi.normalizza(e.retribuzioneBase()))
                .set(CEDOLINO_ELEMENTO_RETRIBUTIVO.TOTALE, Importi.normalizza(e.totale()))
                .set(CEDOLINO_ELEMENTO_RETRIBUTIVO.PAGA_ORARIA, Importi.normalizza(e.pagaOraria()))
                .set(CEDOLINO_ELEMENTO_RETRIBUTIVO.PAGA_GIORNALIERA, Importi.normalizza(e.pagaGiornaliera()))
                .set(CEDOLINO_ELEMENTO_RETRIBUTIVO.DIVISORE_ORARIO, Importi.normalizza(e.divisoreOrario()))
                .set(CEDOLINO_ELEMENTO_RETRIBUTIVO.DIVISORE_GIORNALIERO, Importi.normalizza(e.divisoreGiornaliero()))
                .execute();
    }

    private void inserisciTfr(WorkspaceId workspace, long id, DatiTfr t) {
        dsl.insertInto(CEDOLINO_TFR)
                .set(CEDOLINO_TFR.CEDOLINO_ID, id)
                .set(CEDOLINO_TFR.WORKSPACE_ID, workspace.valore())
                .set(CEDOLINO_TFR.FONDO_31_12_AP, Importi.normalizza(t.fondo31DicembreAnnoPrecedente()))
                .set(CEDOLINO_TFR.RIVALUTAZIONE, Importi.normalizza(t.rivalutazione()))
                .set(CEDOLINO_TFR.IMPOSTA_SOSTITUTIVA, Importi.normalizza(t.impostaSostitutiva()))
                .set(CEDOLINO_TFR.QUOTA_ANNO, Importi.normalizza(t.quotaAnno()))
                .set(CEDOLINO_TFR.QUOTA_MESE, Importi.normalizza(t.quotaMese()))
                .set(CEDOLINO_TFR.QUOTA_FONDI, Importi.normalizza(t.quotaFondi()))
                .set(CEDOLINO_TFR.QUOTA_BUSTA, Importi.normalizza(t.quotaBusta()))
                .set(CEDOLINO_TFR.ANTICIPI, Importi.normalizza(t.anticipi()))
                .set(CEDOLINO_TFR.RETRIBUZIONE_UTILE, Importi.normalizza(t.retribuzioneUtile()))
                .set(CEDOLINO_TFR.LIQUIDATO_LORDO, Importi.normalizza(t.liquidatoLordo()))
                .set(CEDOLINO_TFR.RITENUTA, Importi.normalizza(t.ritenuta()))
                .execute();
    }

    private void inserisciVoci(WorkspaceId workspace, long id, List<Voce> voci) {
        int ordine = 0;
        for (var v : voci) {
            dsl.insertInto(CEDOLINO_VOCE)
                    .set(CEDOLINO_VOCE.WORKSPACE_ID, workspace.valore())
                    .set(CEDOLINO_VOCE.CEDOLINO_ID, id)
                    .set(CEDOLINO_VOCE.ORDINE, ordine++)
                    .set(CEDOLINO_VOCE.CODICE, Testo.tronca(v.codice(), 30))
                    .set(CEDOLINO_VOCE.DESCRIZIONE, Testo.tronca(v.descrizione(), 200))
                    .set(CEDOLINO_VOCE.CATEGORIA, v.categoria().name())
                    .set(CEDOLINO_VOCE.QUANTITA, Importi.normalizza(v.quantita()))
                    .set(CEDOLINO_VOCE.UNITA, Testo.tronca(v.unita(), 20))
                    .set(CEDOLINO_VOCE.BASE, Importi.normalizza(v.base()))
                    .set(CEDOLINO_VOCE.IMPORTO_COMPETENZA, Importi.normalizza(v.importoCompetenza()))
                    .set(CEDOLINO_VOCE.IMPORTO_TRATTENUTA, Importi.normalizza(v.importoTrattenuta()))
                    .execute();
        }
    }

    private void inserisciRatei(WorkspaceId workspace, long id, List<Rateo> ratei) {
        for (var r : ratei) {
            dsl.insertInto(CEDOLINO_RATEO)
                    .set(CEDOLINO_RATEO.WORKSPACE_ID, workspace.valore())
                    .set(CEDOLINO_RATEO.CEDOLINO_ID, id)
                    .set(CEDOLINO_RATEO.TIPO, r.tipo().name())
                    .set(CEDOLINO_RATEO.UNITA, r.unita().name())
                    .set(CEDOLINO_RATEO.RESIDUO_AP, Importi.normalizza(r.residuoAnnoPrecedente()))
                    .set(CEDOLINO_RATEO.MATURATO, Importi.normalizza(r.maturato()))
                    .set(CEDOLINO_RATEO.GODUTO, Importi.normalizza(r.goduto()))
                    .set(CEDOLINO_RATEO.SALDO, Importi.normalizza(r.saldo()))
                    .execute();
        }
    }

    @Override
    public void assegnaCompetenze(WorkspaceId workspace, List<AssegnazioneCompetenza> assegnazioni) {
        // 1) classificazione definitiva (nell'ordine dato: chiavi gia' libere), progressivi e nomi file temporanei:
        //    nessun progressivo o nome file definitivo resta occupato durante gli spostamenti
        for (var a : assegnazioni) {
            var competenza = a.competenza();
            var aggiornamento = dsl.update(CEDOLINO)
                    .set(CEDOLINO.TIPO, competenza.tipo().name())
                    .set(CEDOLINO.ANNO, competenza.anno())
                    .set(CEDOLINO.NOME_FILE, "riclassificazione-" + a.id().valore() + ".pdf");
            var completo = switch (competenza) {
                case Competenza.Mensilita m -> aggiornamento
                        .set(CEDOLINO.MESE, m.mese())
                        .setNull(CEDOLINO.PROGRESSIVO_TFR)
                        .setNull(CEDOLINO.PERIODO_FINE_RAPPORTO);
                case Competenza.FineRapporto f -> aggiornamento
                        .setNull(CEDOLINO.MESE)
                        .set(CEDOLINO.PROGRESSIVO_TFR, PROGRESSIVO_TEMPORANEO + f.progressivo())
                        .set(CEDOLINO.PERIODO_FINE_RAPPORTO, Periodi.primoGiorno(f.periodoRetribuzione()));
            };
            completo.where(CEDOLINO.WORKSPACE_ID.eq(workspace.valore()), CEDOLINO.ID.eq(a.id().valore())).execute();
        }
        // 2) valori definitivi
        for (var a : assegnazioni) {
            var progressivo = a.competenza() instanceof Competenza.FineRapporto f ? f.progressivo() : null;
            dsl.update(CEDOLINO)
                    .set(CEDOLINO.PROGRESSIVO_TFR, progressivo)
                    .set(CEDOLINO.NOME_FILE, a.nomeFile())
                    .where(CEDOLINO.WORKSPACE_ID.eq(workspace.valore()), CEDOLINO.ID.eq(a.id().valore()))
                    .execute();
        }
    }

    @Override
    public Optional<CedolinoRegistrato> trova(WorkspaceId workspace, CedolinoId id) {
        return dsl.selectFrom(CEDOLINO)
                .where(CEDOLINO.WORKSPACE_ID.eq(workspace.valore()), CEDOLINO.ID.eq(id.valore()))
                .fetchOptional(r -> new CedolinoRegistrato(id, new RapportoLavoroId(r.getRapportoId()), competenza(r)));
    }

    @Override
    public List<CedolinoSintesi> sintesi(WorkspaceId workspace, RapportoLavoroId rapportoId) {
        var righe = dsl.select(CEDOLINO.ID, CEDOLINO.TIPO, CEDOLINO.ANNO, CEDOLINO.MESE, CEDOLINO.PROGRESSIVO_TFR,
                        CEDOLINO.PERIODO_RETRIBUZIONE, CEDOLINO.PERIODO_FINE_RAPPORTO, CEDOLINO.DATA_ELABORAZIONE,
                        CEDOLINO.LORDO, CEDOLINO.NETTO, CEDOLINO.NOME_FILE)
                .from(CEDOLINO)
                .where(CEDOLINO.WORKSPACE_ID.eq(workspace.valore()), CEDOLINO.RAPPORTO_ID.eq(rapportoId.valore()))
                .fetch(r -> r.into(CEDOLINO));
        var ratei = ratei(righe.stream().map(CedolinoRecord::getId).toList());
        return righe.stream()
                .map(r -> new CedolinoSintesi(new CedolinoId(r.getId()), rapportoId, competenza(r),
                        YearMonth.from(r.getPeriodoRetribuzione()), r.getDataElaborazione(), r.getLordo(), r.getNetto(),
                        ratei.getOrDefault(r.getId(), List.of()), r.getNomeFile()))
                .toList();
    }

    @Override
    public List<CedolinoNormalizzato> normalizzati(WorkspaceId workspace, RapportoLavoroId rapportoId,
                                                   PersonaId personaId, AziendaId aziendaId) {
        var righe = dsl.selectFrom(CEDOLINO)
                .where(CEDOLINO.WORKSPACE_ID.eq(workspace.valore()), CEDOLINO.RAPPORTO_ID.eq(rapportoId.valore()))
                .fetch();
        return normalizzati(righe, personaId, aziendaId);
    }

    @Override
    public Optional<CedolinoDettaglio> dettaglio(WorkspaceId workspace, CedolinoId id, PersonaId personaId,
                                                 AziendaId aziendaId) {
        return dsl.selectFrom(CEDOLINO)
                .where(CEDOLINO.WORKSPACE_ID.eq(workspace.valore()), CEDOLINO.ID.eq(id.valore()))
                .fetchOptional()
                .map(r -> new CedolinoDettaglio(
                        normalizzati(List.of(r), personaId, aziendaId).getFirst(),
                        r.getNomeFile(),
                        r.getAcquisitoIl(),
                        r.getConfidenza(),
                        ModelliEstrazione.inseritoAMano(r.getModelloEstrazione()),
                        ModelliEstrazione.modificatoAMano(r.getModelloEstrazione()),
                        righe(r.getCampiNonTrovati()),
                        righe(r.getAvvisiEstrazione())));
    }

    @Override
    public Optional<PdfCedolino> pdf(WorkspaceId workspace, CedolinoId id) {
        return dsl.select(CEDOLINO.NOME_FILE, CEDOLINO_PDF.CONTENUTO)
                .from(CEDOLINO)
                .join(CEDOLINO_PDF).on(CEDOLINO_PDF.CEDOLINO_ID.eq(CEDOLINO.ID))
                .where(CEDOLINO.WORKSPACE_ID.eq(workspace.valore()), CEDOLINO.ID.eq(id.valore()))
                .fetchOptional(r -> new PdfCedolino(r.value1(), r.value2()));
    }

    @Override
    public Optional<DocumentoArchiviato> documento(WorkspaceId workspace, CedolinoId id) {
        return dsl.select(CEDOLINO.HASH_CONTENUTO, CEDOLINO_PDF.CONTENUTO, CEDOLINO_PDF.PROTETTO,
                        CEDOLINO.ESTRATTO_JSON, CEDOLINO.MODELLO_ESTRAZIONE)
                .from(CEDOLINO)
                .join(CEDOLINO_PDF).on(CEDOLINO_PDF.CEDOLINO_ID.eq(CEDOLINO.ID))
                .where(CEDOLINO.WORKSPACE_ID.eq(workspace.valore()), CEDOLINO.ID.eq(id.valore()))
                .fetchOptional(r -> new DocumentoArchiviato(r.value1(), r.value2(), r.value3(), r.value4(),
                        r.value5()));
    }

    @Override
    public void elimina(WorkspaceId workspace, CedolinoId id) {
        dsl.deleteFrom(CEDOLINO)
                .where(CEDOLINO.WORKSPACE_ID.eq(workspace.valore()), CEDOLINO.ID.eq(id.valore()))
                .execute();
    }

    // ------------------------------------------------------------------------------------------ lettura

    private List<CedolinoNormalizzato> normalizzati(List<CedolinoRecord> righe, PersonaId personaId, AziendaId aziendaId) {
        if (righe.isEmpty()) {
            return List.of();
        }
        var ids = righe.stream().map(CedolinoRecord::getId).toList();
        Map<Long, CedolinoDatiRecord> dati = dsl.selectFrom(CEDOLINO_DATI)
                .where(CEDOLINO_DATI.CEDOLINO_ID.in(ids)).fetchMap(CEDOLINO_DATI.CEDOLINO_ID);
        Map<Long, CedolinoElementoRetributivoRecord> elementi = dsl.selectFrom(CEDOLINO_ELEMENTO_RETRIBUTIVO)
                .where(CEDOLINO_ELEMENTO_RETRIBUTIVO.CEDOLINO_ID.in(ids))
                .fetchMap(CEDOLINO_ELEMENTO_RETRIBUTIVO.CEDOLINO_ID);
        Map<Long, CedolinoTfrRecord> tfr = dsl.selectFrom(CEDOLINO_TFR)
                .where(CEDOLINO_TFR.CEDOLINO_ID.in(ids)).fetchMap(CEDOLINO_TFR.CEDOLINO_ID);
        var voci = voci(ids);
        var ratei = ratei(ids);
        var risultato = new ArrayList<CedolinoNormalizzato>(righe.size());
        for (var r : righe) {
            var d = dati.get(r.getId());
            risultato.add(new CedolinoNormalizzato(
                    new CedolinoId(r.getId()),
                    new RapportoLavoroId(r.getRapportoId()),
                    personaId,
                    aziendaId,
                    competenza(r),
                    YearMonth.from(r.getPeriodoRetribuzione()),
                    d == null ? null : Periodi.meseDi(d.getMeseVariabili()),
                    r.getDataElaborazione(),
                    d == null ? null : d.getDataAssunzione(),
                    d == null ? null : d.getDataCessazione(),
                    d == null ? null : d.getQualifica(),
                    d == null ? null : d.getLivello(),
                    d == null ? null : d.getCodiceDipendente(),
                    d == null ? null : d.getCcnlTesto(),
                    r.getLordo(),
                    r.getTotaleCompetenze(),
                    r.getTotaleTrattenute(),
                    r.getArrotondamento(),
                    r.getNetto(),
                    elementi(elementi.get(r.getId())),
                    voci.getOrDefault(r.getId(), List.of()),
                    ratei.getOrDefault(r.getId(), List.of()),
                    tfr(tfr.get(r.getId())),
                    d == null ? Presenze.VUOTE
                            : new Presenze(d.getGiorniLavorati(), d.getOreLavorate(), d.getGiorniRetribuiti()),
                    d == null ? null : d.getNumeroScatti(),
                    d == null ? null : d.getProssimoScatto(),
                    d == null || !Boolean.TRUE.equals(d.getBuoniPasto()) ? BuoniPasto.ASSENTI
                            : new BuoniPasto(true, d.getBuoniPastoNumero(), d.getBuoniPastoValoreUnitario(),
                            d.getBuoniPastoImporto()),
                    d != null && Boolean.TRUE.equals(d.getContieneTredicesima()),
                    d != null && Boolean.TRUE.equals(d.getContieneQuattordicesima()),
                    d == null ? ProgressiviAnnui.VUOTI
                            : new ProgressiviAnnui(d.getProgressivoImponibileInps(), d.getProgressivoImponibileIrpef(),
                            d.getProgressivoIrpef())));
        }
        return risultato;
    }

    private Map<Long, List<Voce>> voci(Collection<Long> ids) {
        return dsl.selectFrom(CEDOLINO_VOCE)
                .where(CEDOLINO_VOCE.CEDOLINO_ID.in(ids))
                .orderBy(CEDOLINO_VOCE.CEDOLINO_ID, CEDOLINO_VOCE.ORDINE)
                .stream()
                .collect(Collectors.groupingBy(v -> v.getCedolinoId(), Collectors.mapping(v -> new Voce(
                        v.getCodice(), v.getDescrizione(), CategoriaVoce.valueOf(v.getCategoria()), v.getQuantita(),
                        v.getUnita(), v.getBase(), v.getImportoCompetenza(), v.getImportoTrattenuta()),
                        Collectors.toList())));
    }

    private Map<Long, List<Rateo>> ratei(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return dsl.selectFrom(CEDOLINO_RATEO)
                .where(CEDOLINO_RATEO.CEDOLINO_ID.in(ids))
                .orderBy(CEDOLINO_RATEO.CEDOLINO_ID, CEDOLINO_RATEO.ID)
                .stream()
                .collect(Collectors.groupingBy(r -> r.getCedolinoId(), Collectors.mapping(r -> new Rateo(
                        TipoRateo.valueOf(r.getTipo()), UnitaMisura.valueOf(r.getUnita()), r.getResiduoAp(),
                        r.getMaturato(), r.getGoduto(), r.getSaldo()), Collectors.toList())));
    }

    private static ElementiRetributivi elementi(CedolinoElementoRetributivoRecord e) {
        if (e == null) {
            return ElementiRetributivi.VUOTI;
        }
        return new ElementiRetributivi(e.getMinimo(), e.getContingenza(), e.getSuperminimo(),
                e.getSuperminimoAssorbibile(), e.getImportoScatti(), e.getAltriElementiFissi(), e.getRetribuzioneBase(),
                e.getTotale(), e.getPagaOraria(), e.getPagaGiornaliera(), e.getDivisoreOrario(),
                e.getDivisoreGiornaliero());
    }

    private static DatiTfr tfr(CedolinoTfrRecord t) {
        if (t == null) {
            return null;
        }
        return new DatiTfr(t.getFondo_31_12Ap(), t.getRivalutazione(), t.getImpostaSostitutiva(), t.getQuotaAnno(),
                t.getQuotaMese(), t.getQuotaFondi(), t.getQuotaBusta(), t.getAnticipi(), t.getRetribuzioneUtile(),
                t.getLiquidatoLordo(), t.getRitenuta());
    }

    private static Competenza competenza(CedolinoRecord r) {
        if (TipoMensilita.FINE_RAPPORTO.name().equals(r.getTipo())) {
            return new Competenza.FineRapporto(r.getProgressivoTfr(), YearMonth.from(r.getPeriodoFineRapporto()));
        }
        return new Competenza.Mensilita(r.getAnno(), r.getMese());
    }

    private static List<String> righe(String valore) {
        if (valore == null || valore.isBlank()) {
            return List.of();
        }
        return Arrays.stream(valore.split(SEPARATORE_RIGHE)).filter(s -> !s.isBlank()).toList();
    }
}
