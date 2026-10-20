package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.shared.WorkspaceId;

/**
 * Porta di uscita: cifratura delle password dei PDF da ricordare. Il testo cifrato vale solo per il workspace con cui
 * e' stato prodotto.
 */
public interface CifraturaPassword {

    byte[] cifra(WorkspaceId workspace, String password);

    /**
     * @throws IllegalArgumentException se il testo cifrato non e' valido per il workspace o con la chiave attuale (per
     *                                  esempio dopo un cambio di chiave)
     */
    String decifra(WorkspaceId workspace, byte[] cifrata);
}
