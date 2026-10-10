package com.fcusumano.sam.shared;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Workspace esplicito per il lavoro eseguito fuori da una richiesta HTTP (thread virtuali dei caricamenti, scritture
 * serializzate): un {@link ScopedValue} visibile a tutto il codice chiamato, compresi i servizi degli altri moduli e i
 * listener sincroni degli eventi. Le implementazioni di {@link CurrentWorkspaceProvider} lo restituiscono per primo,
 * cosi' censimento, ricalcoli e letture lavorano nel workspace del lotto anche quando il workspace della richiesta
 * (es. dall'utente autenticato) non e' disponibile o e' diverso.
 */
public final class ContestoWorkspace {

    private static final ScopedValue<WorkspaceId> WORKSPACE = ScopedValue.newInstance();

    private ContestoWorkspace() {
    }

    /** Esegue l'azione con il workspace indicato come workspace corrente. */
    public static void esegui(WorkspaceId workspace, Runnable azione) {
        ScopedValue.where(WORKSPACE, Objects.requireNonNull(workspace, "workspace")).run(azione);
    }

    /** Esegue l'azione con il workspace indicato come workspace corrente e ne restituisce il risultato. */
    public static <T> T calcola(WorkspaceId workspace, Supplier<T> azione) {
        return ScopedValue.where(WORKSPACE, Objects.requireNonNull(workspace, "workspace")).call(azione::get);
    }

    /** Workspace impostato da {@link #esegui} o {@link #calcola} sul thread corrente, se presente. */
    public static Optional<WorkspaceId> attivo() {
        return WORKSPACE.isBound() ? Optional.of(WORKSPACE.get()) : Optional.empty();
    }
}
