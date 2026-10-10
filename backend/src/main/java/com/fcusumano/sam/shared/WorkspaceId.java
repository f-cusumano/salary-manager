package com.fcusumano.sam.shared;

/**
 * Identificativo del workspace a cui appartengono i dati. In fase 1 esiste solo {@link #DEFAULT}.
 */
public record WorkspaceId(long valore) {

    /** Workspace di default (id=1), creato da Liquibase. */
    public static final WorkspaceId DEFAULT = new WorkspaceId(1L);

    public WorkspaceId {
        if (valore <= 0) {
            throw new IllegalArgumentException("Id workspace non valido: " + valore);
        }
    }
}
