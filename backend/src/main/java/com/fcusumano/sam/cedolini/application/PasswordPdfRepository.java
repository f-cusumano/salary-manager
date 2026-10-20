package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.shared.WorkspaceId;
import java.util.List;

/** Porta di persistenza delle password ricordate dei PDF (tabella {@code password_pdf}), sempre cifrate. */
public interface PasswordPdfRepository {

    /** Password cifrate del workspace, dalla prima ricordata. */
    List<byte[]> cifrate(WorkspaceId workspace);

    void inserisci(WorkspaceId workspace, byte[] cifrata);
}
