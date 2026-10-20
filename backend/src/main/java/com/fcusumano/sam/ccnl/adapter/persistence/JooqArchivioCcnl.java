package com.fcusumano.sam.ccnl.adapter.persistence;

import static com.fcusumano.sam.shared.db.jooq.Tables.CCNL;
import static com.fcusumano.sam.shared.db.jooq.Tables.CCNL_PREAVVISO;

import com.fcusumano.sam.ccnl.Ccnl;
import com.fcusumano.sam.ccnl.DurataPreavviso;
import com.fcusumano.sam.ccnl.RegolaPreavviso;
import com.fcusumano.sam.ccnl.application.ArchivioCcnl;
import com.fcusumano.sam.ccnl.domain.RegolaRiconoscimento;
import com.fcusumano.sam.shared.db.jooq.tables.records.CcnlRecord;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/**
 * Accesso jOOQ alle tabelle {@code ccnl} e {@code ccnl_preavviso}.
 */
@Repository
class JooqArchivioCcnl implements ArchivioCcnl {

    private final DSLContext dsl;

    JooqArchivioCcnl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<Ccnl> attivi() {
        return dsl.selectFrom(CCNL)
                .where(CCNL.ATTIVO.isTrue())
                .orderBy(CCNL.DESCRIZIONE, CCNL.CODICE)
                .fetch(JooqArchivioCcnl::comeCcnl);
    }

    @Override
    public Optional<Ccnl> perCodice(String codice) {
        return dsl.selectFrom(CCNL).where(CCNL.CODICE.eq(codice)).fetchOptional(JooqArchivioCcnl::comeCcnl);
    }

    @Override
    public List<RegolaRiconoscimento> regoleRiconoscimento() {
        return dsl.select(CCNL.CODICE, CCNL.CODICE_CNEL, CCNL.PAROLE_CHIAVE, CCNL.PAROLE_ESCLUSE)
                .from(CCNL)
                .where(CCNL.ATTIVO.isTrue())
                .orderBy(CCNL.CODICE)
                .fetch(r -> RegolaRiconoscimento.daColonne(r.value1(), r.value2(), r.value3(), r.value4()));
    }

    @Override
    public List<RegolaPreavviso> regolePreavviso(String codice) {
        return dsl.selectFrom(CCNL_PREAVVISO)
                .where(CCNL_PREAVVISO.CCNL_CODICE.eq(codice))
                .orderBy(CCNL_PREAVVISO.ORDINE)
                .fetch(r -> new RegolaPreavviso(
                        Set.of(r.getLivelli().split(",")),
                        r.getDescrizioneLivelli(),
                        r.getOltreAnni(),
                        new DurataPreavviso(r.getDimissioniMesi(), r.getDimissioniGiorni()),
                        new DurataPreavviso(r.getLicenziamentoMesi(), r.getLicenziamentoGiorni()),
                        r.getRiferimento()));
    }

    private static Ccnl comeCcnl(CcnlRecord r) {
        return new Ccnl(
                r.getCodice(),
                r.getDescrizione(),
                r.getMensilita(),
                r.getPeriodicitaScattiMesi(),
                r.getMaxScatti(),
                r.getMeseQuattordicesima(),
                r.getNoteVerifica());
    }
}
