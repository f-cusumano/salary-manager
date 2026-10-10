package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.shared.ContestoWorkspace;
import com.fcusumano.sam.shared.WorkspaceId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Serializza per workspace le transazioni che scrivono cedolini e anagrafica (acquisizione ed eliminazione): evita
 * doppioni di persona/azienda quando piu' file della stessa persona nuova arrivano insieme e collisioni nella
 * rinumerazione dei progressivi TFR. Il lock e' preso prima dell'inizio della transazione e rilasciato dopo il commit,
 * cosi' chi entra dopo vede sempre i dati gia' committati. Le estrazioni IA restano parallele: qui passa solo la fase
 * di persistenza, che e' breve.
 */
@Component
class ScrittureSerializzate {

    private final Map<WorkspaceId, ReentrantLock> lock = new ConcurrentHashMap<>();
    private final TransactionTemplate transazione;

    ScrittureSerializzate(PlatformTransactionManager transactionManager) {
        this.transazione = new TransactionTemplate(transactionManager);
        this.transazione.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Esegue l'azione in una nuova transazione, in mutua esclusione con le altre scritture del workspace e con il
     * workspace come workspace corrente ({@link ContestoWorkspace}) anche per i servizi degli altri moduli e i
     * listener sincroni. Un'eccezione annulla la transazione e viene rilanciata.
     */
    <T> T inTransazione(WorkspaceId workspace, TransazioneSerializzata<T> azione) {
        return ContestoWorkspace.calcola(workspace, () -> conLock(workspace, () -> transazione.execute(azione::esegui)));
    }

    private <T> T conLock(WorkspaceId workspace, Supplier<T> azione) {
        var l = lock.computeIfAbsent(workspace, w -> new ReentrantLock());
        l.lock();
        try {
            return azione.get();
        } finally {
            l.unlock();
        }
    }

    /** Corpo di una transazione serializzata. */
    @FunctionalInterface
    interface TransazioneSerializzata<T> {

        T esegui(TransactionStatus stato);
    }
}
