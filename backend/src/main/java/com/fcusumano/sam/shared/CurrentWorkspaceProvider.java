package com.fcusumano.sam.shared;

/**
 * Fornisce il workspace corrente. Tutte le letture e scritture dei moduli vanno filtrate per questo workspace.
 * <p>
 * Le implementazioni restituiscono per primo il workspace esplicito di {@link ContestoWorkspace#attivo()} (lavoro
 * asincrono dei caricamenti, scritture serializzate), poi quello della richiesta. Fase 1: in mancanza del contesto
 * esplicito restituisce {@link WorkspaceId#DEFAULT}; con il login restituira' il workspace dell'utente autenticato.
 */
public interface CurrentWorkspaceProvider {

    WorkspaceId corrente();
}
