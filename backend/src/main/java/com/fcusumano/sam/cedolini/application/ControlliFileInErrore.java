package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.cedolini.CaricamentoId;
import com.fcusumano.sam.cedolini.StatoCaricamento;
import com.fcusumano.sam.cedolini.application.CaricamentiRepository.FileDelCaricamento;
import com.fcusumano.sam.shared.OperazioneNonConsentitaException;
import com.fcusumano.sam.shared.RisorsaNonTrovataException;
import com.fcusumano.sam.shared.WorkspaceId;

/**
 * Controlli comuni alle azioni su un file in errore, da eseguire nella transazione serializzata dell'azione: il
 * caricamento esiste nel workspace, e' in ERRORE, il suo file e' ancora in errore e nessun suo tentativo e' in
 * elaborazione.
 */
final class ControlliFileInErrore {

    private ControlliFileInErrore() {
    }

    /**
     * @return il caricamento con lo stato del suo file
     * @throws RisorsaNonTrovataException       se il caricamento non esiste nel workspace
     * @throws OperazioneNonConsentitaException se il file non e' piu' in errore o e' in elaborazione
     */
    static FileDelCaricamento daRisolvere(CaricamentiRepository caricamenti, WorkspaceId workspace, CaricamentoId id) {
        var file = caricamenti.fileDelCaricamento(workspace, id)
                .orElseThrow(() -> new RisorsaNonTrovataException("Caricamento", id.valore()));
        if (file.stato() != StatoCaricamento.ERRORE || !file.inErrore()) {
            throw new OperazioneNonConsentitaException(MessaggiCaricamento.NON_IN_ERRORE);
        }
        if (file.inElaborazione()) {
            throw new OperazioneNonConsentitaException(MessaggiCaricamento.IN_ELABORAZIONE);
        }
        return file;
    }
}
