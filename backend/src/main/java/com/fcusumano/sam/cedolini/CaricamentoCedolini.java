package com.fcusumano.sam.cedolini;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Caricamento multiplo asincrono dei PDF.
 */
public interface CaricamentoCedolini {

    /**
     * Registra i file come IN_CODA (stesso lotto) e ne avvia l'elaborazione asincrona (thread virtuali, concorrenza
     * limitata da {@code salary-manager.letture-contemporanee}). Ritorna subito, senza attendere la lettura.
     * Flusso per file: hash gia' presente -> DUPLICATO; lettura del cedolino; persona/azienda/rapporto; controllo unicita'
     * (rapporto + anno + mese, oppure periodo per i cedolini di fine rapporto) -> DUPLICATO o AGGIUNTIVO_NON_GESTITO;
     * salvataggio; evento {@link CedolinoAcquisito}.
     * Equivale a {@link #avvia(List, String)} senza password.
     */
    default List<Caricamento> avvia(List<FileCaricato> files) {
        return avvia(files, null);
    }

    /**
     * Come {@link #avvia(List)}, con la password di apertura indicata dall'utente per i PDF protetti del lotto.
     * Per un PDF protetto si provano, in ordine: la password del lotto, le password configurate
     * ({@code salary-manager.pdf.password-candidate}) e i codici fiscali delle persone gia' censite (maiuscoli e
     * minuscoli); se nessuna funziona il caricamento va in ERRORE con un messaggio che chiede la password.
     * La password resta solo in memoria per l'elaborazione del lotto: non viene salvata ne' registrata nei log.
     * Al lettore dei cedolini va la copia decifrata, a DB si conserva il PDF originale (su cui si calcola l'hash).
     *
     * @param password password di apertura per i PDF protetti del lotto; null o vuota se non indicata
     */
    List<Caricamento> avvia(List<FileCaricato> files, String password);

    /** Stato dei caricamenti di un lotto, nell'ordine di caricamento. */
    List<Caricamento> statoLotto(UUID lotto);

    /** Stato dei caricamenti indicati (gli id inesistenti vengono ignorati). */
    List<Caricamento> stato(Collection<CaricamentoId> ids);

    /** Ultimi caricamenti del workspace, dal piu' recente. */
    List<Caricamento> recenti(int limite);
}
