package com.fcusumano.sam.shared.workspace;

import com.fcusumano.sam.shared.ContestoWorkspace;
import com.fcusumano.sam.shared.CurrentWorkspaceProvider;
import com.fcusumano.sam.shared.WorkspaceId;
import org.springframework.stereotype.Component;

/**
 * Fase 1: un solo utente, un solo workspace. Il workspace esplicito di {@link ContestoWorkspace} (lavoro asincrono dei
 * caricamenti) prevale.
 */
@Component
class DefaultWorkspaceProvider implements CurrentWorkspaceProvider {

    @Override
    public WorkspaceId corrente() {
        return ContestoWorkspace.attivo().orElse(WorkspaceId.DEFAULT);
    }
}
