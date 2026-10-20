package com.fcusumano.sam.cedolini.adapter.persistence;

import static com.fcusumano.sam.shared.db.jooq.Tables.PASSWORD_PDF;

import com.fcusumano.sam.cedolini.application.PasswordPdfRepository;
import com.fcusumano.sam.shared.WorkspaceId;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.springframework.stereotype.Repository;

/** Accesso jOOQ alla tabella {@code password_pdf}: solo testi cifrati. */
@Repository
class JooqPasswordPdfRepository implements PasswordPdfRepository {

    private final DSLContext dsl;

    JooqPasswordPdfRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<byte[]> cifrate(WorkspaceId workspace) {
        return dsl.select(PASSWORD_PDF.PASSWORD_CIFRATA)
                .from(PASSWORD_PDF)
                .where(PASSWORD_PDF.WORKSPACE_ID.eq(workspace.valore()))
                .orderBy(PASSWORD_PDF.ID)
                .fetch(Record1::value1);
    }

    @Override
    public void inserisci(WorkspaceId workspace, byte[] cifrata) {
        dsl.insertInto(PASSWORD_PDF)
                .set(PASSWORD_PDF.WORKSPACE_ID, workspace.valore())
                .set(PASSWORD_PDF.PASSWORD_CIFRATA, cifrata)
                .execute();
    }
}
