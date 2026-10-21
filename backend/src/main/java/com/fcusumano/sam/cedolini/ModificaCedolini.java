package com.fcusumano.sam.cedolini;

/**
 * Modifica a mano dei dati di un cedolino archiviato (workspace corrente), con lo stesso modulo dell'inserimento a
 * mano: i dati attuali come {@link CedolinoManuale}, la prova senza salvataggio e il salvataggio.
 * <p>
 * Salvare equivale a eliminare il cedolino e ad archiviare di nuovo il suo PDF con i dati modificati, in una sola
 * transazione serializzata con le acquisizioni: competenza, classificazione dei cedolini del rapporto, dati del
 * rapporto e dati derivati diventano quelli che si avrebbero caricando da zero il PDF con questi dati. Il cedolino
 * modificato ha un nuovo id. Un id inesistente nel workspace solleva
 * {@link com.fcusumano.sam.shared.RisorsaNonTrovataException}.
 */
public interface ModificaCedolini {

    /**
     * Dati attuali del cedolino come li riceve il modulo: quelli dell'estrazione archiviata (lettura del PDF o
     * inserimento a mano precedente), con persona e azienda del suo rapporto scelte dall'elenco.
     */
    CedolinoManuale dati(CedolinoId id);

    /**
     * Prova la modifica senza salvare nulla: competenza, conflitti con altri cedolini, persona, azienda e rapporto
     * nuovi, avvisi.
     *
     * @throws com.fcusumano.sam.shared.DatiNonValidiException dati mancanti o non validi, campo per campo, come
     *                                                         nell'inserimento a mano
     */
    VerificaCedolinoManuale verifica(CedolinoId id, CedolinoManuale dati);

    /**
     * Salva i dati modificati.
     *
     * @return il cedolino archiviato con i dati modificati
     * @throws com.fcusumano.sam.shared.DatiNonValidiException          dati mancanti o non validi, come nella verifica
     * @throws com.fcusumano.sam.shared.OperazioneNonConsentitaException con i dati modificati il cedolino sarebbe un
     *                                                                  duplicato o un aggiuntivo di un altro cedolino
     *                                                                  (nulla cambia)
     */
    CedolinoModificato modifica(CedolinoId id, CedolinoManuale dati);
}
