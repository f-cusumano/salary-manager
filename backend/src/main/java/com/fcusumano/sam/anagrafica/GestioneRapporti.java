package com.fcusumano.sam.anagrafica;

/**
 * Modifiche al rapporto richieste dall'utente.
 */
public interface GestioneRapporti {

    /**
     * Aggiorna le note libere del rapporto (null o vuoto = nessuna nota; massimo 4000 caratteri).
     *
     * @throws com.fcusumano.sam.shared.RisorsaNonTrovataException se il rapporto non esiste
     */
    RapportoLavoro aggiornaNote(RapportoLavoroId rapportoId, String note);

    /**
     * Imposta il CCNL scelto dall'utente (vale per tutti i cedolini presenti e futuri del rapporto, resta
     * modificabile). Marca il CCNL come scelto dall'utente e, se il codice cambia, pubblica
     * {@link CcnlRapportoModificato} che fa ricalcolare i dati derivati nella stessa transazione.
     *
     * @throws com.fcusumano.sam.shared.RisorsaNonTrovataException se il rapporto o il CCNL non esistono
     */
    RapportoLavoro impostaCcnl(RapportoLavoroId rapportoId, String ccnlCodice);
}
