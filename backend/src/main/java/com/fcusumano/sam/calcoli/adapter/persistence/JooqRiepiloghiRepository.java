package com.fcusumano.sam.calcoli.adapter.persistence;

import static com.fcusumano.sam.shared.db.jooq.Tables.INDICATORE_MENSILITA;
import static com.fcusumano.sam.shared.db.jooq.Tables.RIEPILOGO_RAPPORTO;
import static com.fcusumano.sam.shared.db.jooq.Tables.RIEPILOGO_RATEO;

import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.calcoli.FonteDato;
import com.fcusumano.sam.calcoli.RiepilogoRapporto;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.Anzianita;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.IndicatoriMensilita;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.Ratei;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.Retribuzione;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.SaldoRateo;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.Scatti;
import com.fcusumano.sam.calcoli.RiepilogoRapporto.Tfr;
import com.fcusumano.sam.calcoli.application.RiepiloghiRepository;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.shared.Importi;
import com.fcusumano.sam.shared.UnitaMisura;
import com.fcusumano.sam.shared.WorkspaceId;
import com.fcusumano.sam.shared.db.jooq.tables.records.RiepilogoRapportoRecord;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/**
 * Accesso jOOQ alle tabelle {@code riepilogo_rapporto}, {@code riepilogo_rateo} e {@code indicatore_mensilita}.
 * I mesi sono memorizzati come primo giorno del mese, gli importi con scala 5, le note troncate alla lunghezza della
 * colonna. La sostituzione cancella e reinserisce tutte le righe del rapporto (ricalcolo da zero).
 */
@Repository
class JooqRiepiloghiRepository implements RiepiloghiRepository {

    private static final int LUNGHEZZA_NOTA_RIEPILOGO = 1000;
    private static final int LUNGHEZZA_NOTA_RATEO = 500;

    private final DSLContext dsl;

    JooqRiepiloghiRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<RiepilogoRapporto> trova(WorkspaceId workspace, RapportoLavoroId rapportoId) {
        return dsl.selectFrom(RIEPILOGO_RAPPORTO)
                .where(RIEPILOGO_RAPPORTO.WORKSPACE_ID.eq(workspace.valore()),
                        RIEPILOGO_RAPPORTO.RAPPORTO_ID.eq(rapportoId.valore()))
                .fetchOptional()
                .map(r -> riepilogo(workspace, r));
    }

    @Override
    public void sostituisci(WorkspaceId workspace, RiepilogoRapporto riepilogo) {
        var rapporto = riepilogo.rapportoId();
        elimina(workspace, rapporto);
        inserisciRiepilogo(workspace, riepilogo);
        for (var saldo : riepilogo.ratei().saldi()) {
            dsl.insertInto(RIEPILOGO_RATEO)
                    .set(RIEPILOGO_RATEO.WORKSPACE_ID, workspace.valore())
                    .set(RIEPILOGO_RATEO.RAPPORTO_ID, rapporto.valore())
                    .set(RIEPILOGO_RATEO.TIPO, saldo.tipo().name())
                    .set(RIEPILOGO_RATEO.UNITA, saldo.unita().name())
                    .set(RIEPILOGO_RATEO.SALDO, Importi.normalizza(saldo.saldo()))
                    .set(RIEPILOGO_RATEO.VALORE_STIMATO, Importi.normalizza(saldo.valoreStimato()))
                    .set(RIEPILOGO_RATEO.NOTA, tronca(saldo.nota(), LUNGHEZZA_NOTA_RATEO))
                    .execute();
        }
        var ordine = 0;
        for (var indicatori : riepilogo.mensilita()) {
            dsl.insertInto(INDICATORE_MENSILITA)
                    .set(INDICATORE_MENSILITA.CEDOLINO_ID, indicatori.cedolinoId().valore())
                    .set(INDICATORE_MENSILITA.WORKSPACE_ID, workspace.valore())
                    .set(INDICATORE_MENSILITA.RAPPORTO_ID, rapporto.valore())
                    .set(INDICATORE_MENSILITA.SCATTO_IN_BUSTA, indicatori.scattoInBusta())
                    .set(INDICATORE_MENSILITA.AUMENTO_FUORI_SCATTI, indicatori.aumentoFuoriScatti())
                    .set(INDICATORE_MENSILITA.LORDO_CONTRATTUALE, Importi.normalizza(indicatori.lordoContrattuale()))
                    .set(INDICATORE_MENSILITA.ORDINE, ordine++)
                    .execute();
        }
    }

    @Override
    public void elimina(WorkspaceId workspace, RapportoLavoroId rapportoId) {
        dsl.deleteFrom(INDICATORE_MENSILITA)
                .where(INDICATORE_MENSILITA.WORKSPACE_ID.eq(workspace.valore()),
                        INDICATORE_MENSILITA.RAPPORTO_ID.eq(rapportoId.valore()))
                .execute();
        dsl.deleteFrom(RIEPILOGO_RATEO)
                .where(RIEPILOGO_RATEO.WORKSPACE_ID.eq(workspace.valore()),
                        RIEPILOGO_RATEO.RAPPORTO_ID.eq(rapportoId.valore()))
                .execute();
        dsl.deleteFrom(RIEPILOGO_RAPPORTO)
                .where(RIEPILOGO_RAPPORTO.WORKSPACE_ID.eq(workspace.valore()),
                        RIEPILOGO_RAPPORTO.RAPPORTO_ID.eq(rapportoId.valore()))
                .execute();
    }

    // ------------------------------------------------------------------------------------------------ scrittura

    private void inserisciRiepilogo(WorkspaceId workspace, RiepilogoRapporto riepilogo) {
        var a = riepilogo.anzianita();
        var s = riepilogo.scatti();
        var r = riepilogo.retribuzione();
        var t = riepilogo.tfr();
        dsl.insertInto(RIEPILOGO_RAPPORTO)
                .set(RIEPILOGO_RAPPORTO.RAPPORTO_ID, riepilogo.rapportoId().valore())
                .set(RIEPILOGO_RAPPORTO.WORKSPACE_ID, workspace.valore())
                .set(RIEPILOGO_RAPPORTO.CALCOLATO_IL, riepilogo.calcolatoIl())
                .set(RIEPILOGO_RAPPORTO.ANZIANITA_DAL, primoGiorno(a.dal()))
                .set(RIEPILOGO_RAPPORTO.ANZIANITA_AL, primoGiorno(a.al()))
                .set(RIEPILOGO_RAPPORTO.ANZIANITA_IN_CORSO, a.inCorso())
                .set(RIEPILOGO_RAPPORTO.ANZIANITA_ANNI, a.anni())
                .set(RIEPILOGO_RAPPORTO.ANZIANITA_MESI, a.mesi())
                .set(RIEPILOGO_RAPPORTO.ANZIANITA_FONTE_INIZIO, a.fonteInizio().name())
                .set(RIEPILOGO_RAPPORTO.ANZIANITA_FONTE_FINE, a.fonteFine().name())
                .set(RIEPILOGO_RAPPORTO.SCATTI_ATTUALI, s.attuali())
                .set(RIEPILOGO_RAPPORTO.SCATTI_MAX, s.massimo())
                .set(RIEPILOGO_RAPPORTO.PROSSIMO_SCATTO, s.prossimoScatto())
                .set(RIEPILOGO_RAPPORTO.PROSSIMO_SCATTO_FONTE, s.fonteProssimo().name())
                .set(RIEPILOGO_RAPPORTO.SCATTI_FONTE_ATTUALI, s.fonteAttuali().name())
                .set(RIEPILOGO_RAPPORTO.SCATTI_ATTUALI_STIMATI, s.attualiStimati())
                .set(RIEPILOGO_RAPPORTO.RAL, Importi.normalizza(r.ral()))
                .set(RIEPILOGO_RAPPORTO.LORDO_MENSILE, Importi.normalizza(r.lordoMensile()))
                .set(RIEPILOGO_RAPPORTO.MENSILITA, r.mensilita())
                .set(RIEPILOGO_RAPPORTO.MINIMO, Importi.normalizza(r.minimo()))
                .set(RIEPILOGO_RAPPORTO.CONTINGENZA, Importi.normalizza(r.contingenza()))
                .set(RIEPILOGO_RAPPORTO.SUPERMINIMO, Importi.normalizza(r.superminimo()))
                .set(RIEPILOGO_RAPPORTO.IMPORTO_SCATTI, Importi.normalizza(r.importoScatti()))
                .set(RIEPILOGO_RAPPORTO.ALTRI_ELEMENTI_FISSI, Importi.normalizza(r.altriElementiFissi()))
                .set(RIEPILOGO_RAPPORTO.RETRIBUZIONE_BASE, Importi.normalizza(r.retribuzioneBase()))
                .set(RIEPILOGO_RAPPORTO.RAL_STIMATA, r.stimata())
                .set(RIEPILOGO_RAPPORTO.RAL_NOTA, tronca(r.nota(), LUNGHEZZA_NOTA_RIEPILOGO))
                .set(RIEPILOGO_RAPPORTO.RAL_CEDOLINO_ID, r.riferimento() == null ? null : r.riferimento().valore())
                .set(RIEPILOGO_RAPPORTO.BUONI_PASTO, riepilogo.buoniPasto())
                .set(RIEPILOGO_RAPPORTO.RATEI_CEDOLINO_ID,
                        riepilogo.ratei().riferimento() == null ? null : riepilogo.ratei().riferimento().valore())
                .set(RIEPILOGO_RAPPORTO.TFR_TOTALE, Importi.normalizza(t.totaleAccumulato()))
                .set(RIEPILOGO_RAPPORTO.TFR_QUOTA_MENSILE, Importi.normalizza(t.quotaMensile()))
                .set(RIEPILOGO_RAPPORTO.TFR_QUOTA_AZIENDA, Importi.normalizza(t.quotaInAzienda()))
                .set(RIEPILOGO_RAPPORTO.TFR_QUOTA_FONDI, Importi.normalizza(t.quotaFondi()))
                .set(RIEPILOGO_RAPPORTO.TFR_QUOTA_BUSTA, Importi.normalizza(t.quotaBusta()))
                .set(RIEPILOGO_RAPPORTO.TFR_ANTICIPI, Importi.normalizza(t.anticipi()))
                .set(RIEPILOGO_RAPPORTO.TFR_STIMA_NETTO, Importi.normalizza(t.stimaNetto()))
                .set(RIEPILOGO_RAPPORTO.TFR_ALIQUOTA, Importi.normalizza(t.aliquotaStimata()))
                .set(RIEPILOGO_RAPPORTO.TFR_STIMATO, t.stimato())
                .set(RIEPILOGO_RAPPORTO.TFR_RIFERIMENTO, primoGiorno(t.riferimento()))
                .set(RIEPILOGO_RAPPORTO.TFR_NOTA, tronca(t.nota(), LUNGHEZZA_NOTA_RIEPILOGO))
                .set(RIEPILOGO_RAPPORTO.TFR_QUOTA_MENSILE_STIMATA, t.quotaMensileStimata())
                .set(RIEPILOGO_RAPPORTO.TFR_LIQUIDATO, t.liquidato())
                .set(RIEPILOGO_RAPPORTO.TFR_IMPORTO_LIQUIDATO, Importi.normalizza(t.importoLiquidato()))
                .set(RIEPILOGO_RAPPORTO.TFR_NETTO_LIQUIDATO, Importi.normalizza(t.nettoLiquidato()))
                .execute();
    }

    // ------------------------------------------------------------------------------------------------ lettura

    private RiepilogoRapporto riepilogo(WorkspaceId workspace, RiepilogoRapportoRecord r) {
        var rapportoId = new RapportoLavoroId(r.getRapportoId());
        var anzianita = new Anzianita(
                mese(r.getAnzianitaDal()),
                mese(r.getAnzianitaAl()),
                r.getAnzianitaInCorso(),
                zeroSeNull(r.getAnzianitaAnni()),
                zeroSeNull(r.getAnzianitaMesi()),
                fonte(r.getAnzianitaFonteInizio()),
                fonte(r.getAnzianitaFonteFine()));
        var scatti = new Scatti(
                r.getScattiAttuali(),
                r.getScattiMax(),
                r.getProssimoScatto(),
                fonte(r.getProssimoScattoFonte()),
                fonte(r.getScattiFonteAttuali()),
                r.getScattiAttualiStimati());
        var retribuzione = new Retribuzione(
                r.getRal(),
                r.getLordoMensile(),
                r.getMensilita(),
                r.getMinimo(),
                r.getContingenza(),
                r.getSuperminimo(),
                r.getImportoScatti(),
                r.getAltriElementiFissi(),
                r.getRetribuzioneBase(),
                r.getRalStimata(),
                r.getRalNota(),
                cedolino(r.getRalCedolinoId()));
        var tfr = new Tfr(
                r.getTfrTotale(),
                r.getTfrQuotaMensile(),
                r.getTfrQuotaAzienda(),
                r.getTfrQuotaFondi(),
                r.getTfrQuotaBusta(),
                r.getTfrAnticipi(),
                r.getTfrStimaNetto(),
                r.getTfrAliquota(),
                r.getTfrStimato(),
                mese(r.getTfrRiferimento()),
                r.getTfrNota(),
                r.getTfrQuotaMensileStimata(),
                r.getTfrLiquidato(),
                r.getTfrImportoLiquidato(),
                r.getTfrNettoLiquidato());

        var saldi = new ArrayList<SaldoRateo>();
        dsl.selectFrom(RIEPILOGO_RATEO)
                .where(RIEPILOGO_RATEO.WORKSPACE_ID.eq(workspace.valore()),
                        RIEPILOGO_RATEO.RAPPORTO_ID.eq(rapportoId.valore()))
                .orderBy(RIEPILOGO_RATEO.ID)
                .forEach(s -> saldi.add(new SaldoRateo(
                        TipoRateo.valueOf(s.getTipo()),
                        UnitaMisura.valueOf(s.getUnita()),
                        s.getSaldo(),
                        s.getValoreStimato(),
                        s.getNota())));
        var mensilita = dsl.selectFrom(INDICATORE_MENSILITA)
                .where(INDICATORE_MENSILITA.WORKSPACE_ID.eq(workspace.valore()),
                        INDICATORE_MENSILITA.RAPPORTO_ID.eq(rapportoId.valore()))
                .orderBy(INDICATORE_MENSILITA.ORDINE, INDICATORE_MENSILITA.CEDOLINO_ID)
                .fetch(i -> new IndicatoriMensilita(
                        new CedolinoId(i.getCedolinoId()),
                        i.getScattoInBusta(),
                        i.getAumentoFuoriScatti(),
                        i.getLordoContrattuale()));

        return new RiepilogoRapporto(
                rapportoId,
                r.getCalcolatoIl(),
                anzianita,
                scatti,
                retribuzione,
                r.getBuoniPasto(),
                new Ratei(cedolino(r.getRateiCedolinoId()), saldi),
                tfr,
                mensilita);
    }

    // ------------------------------------------------------------------------------------------------ conversioni

    private static LocalDate primoGiorno(YearMonth mese) {
        return mese == null ? null : mese.atDay(1);
    }

    private static YearMonth mese(LocalDate data) {
        return data == null ? null : YearMonth.from(data);
    }

    private static FonteDato fonte(String valore) {
        return valore == null ? FonteDato.NON_DISPONIBILE : FonteDato.valueOf(valore);
    }

    /** Cedolino di riferimento; null se assente o eliminato (FK ON DELETE SET NULL). */
    private static CedolinoId cedolino(Long id) {
        return id == null ? null : new CedolinoId(id);
    }

    private static int zeroSeNull(Integer valore) {
        return valore == null ? 0 : valore;
    }

    private static String tronca(String testo, int lunghezza) {
        if (testo == null || testo.length() <= lunghezza) {
            return testo;
        }
        return testo.substring(0, lunghezza - 1) + "…";
    }
}
