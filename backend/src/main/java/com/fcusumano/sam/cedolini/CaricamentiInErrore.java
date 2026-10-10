package com.fcusumano.sam.cedolini;

import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import java.util.List;
import java.util.Optional;

/**
 * File caricati con errore (workspace corrente): elenco, PDF originale conservato, rimozione, nuova lettura,
 * associazione a una mensilita' e inserimento a mano.
 * <p>
 * Un file e' identificato dal contenuto (hash): le azioni accettano l'id di uno qualunque dei suoi tentativi in ERRORE.
 * Le azioni che modificano il file sono serializzate con le acquisizioni del workspace e, se il file non e' piu' in
 * errore (inserito, rimosso, ricaricato con successo) o un suo nuovo tentativo e' in elaborazione, sono rifiutate con
 * {@link com.fcusumano.sam.shared.OperazioneNonConsentitaException}. Un id inesistente nel workspace solleva
 * {@link com.fcusumano.sam.shared.RisorsaNonTrovataException}.
 */
public interface CaricamentiInErrore {

    /** File in errore, uno per file, dal piu' recente; senza limite. */
    List<CaricamentoInErrore> elenco();

    /** File in errore associati a una mensilita' del rapporto (per lista mensilita' e segnalazioni). */
    List<CaricamentoInErrore> associatiARapporto(RapportoLavoroId rapportoId);

    /**
     * PDF originale conservato (come caricato, anche se protetto) con il nome del file originale; vuoto se il
     * caricamento non esiste, non e' in ERRORE o il PDF del file non e' conservato.
     */
    Optional<PdfCedolino> pdf(CaricamentoId id);

    /**
     * Associa il file a una mensilita' di un rapporto (sostituisce un'associazione precedente) e pubblica
     * {@link CaricamentiInErroreModificati} per il rapporto nuovo e quello precedente.
     *
     * @param anno dal 1900 all'anno prossimo
     * @param mese 1..12, 13 tredicesima, 14 quattordicesima
     * @return il file aggiornato
     * @throws com.fcusumano.sam.shared.DatiNonValidiException          rapporto, anno o mese mancanti o fuori
     *                                                                  intervallo (campi rapportoId, anno, mese)
     * @throws com.fcusumano.sam.shared.RisorsaNonTrovataException      caricamento o rapporto inesistente
     * @throws com.fcusumano.sam.shared.OperazioneNonConsentitaException mensilita' gia' presente o gia' associata a un
     *                                                                  altro file in errore
     */
    CaricamentoInErrore associa(CaricamentoId id, RapportoLavoroId rapporto, Integer anno, Integer mese);

    /** Rimuove l'associazione del file (se presente) e pubblica {@link CaricamentiInErroreModificati}. */
    CaricamentoInErrore rimuoviAssociazione(CaricamentoId id);

    /** Elimina i tentativi in ERRORE del file, il PDF conservato e l'associazione. */
    void rimuovi(CaricamentoId id);

    /**
     * Rilegge il PDF conservato in un nuovo lotto di un solo file (stesso nome e hash), come un nuovo caricamento.
     *
     * @param password password di apertura del PDF, null o vuota se non indicata; resta solo in memoria
     * @return il caricamento registrato IN_CODA
     */
    List<Caricamento> rileggi(CaricamentoId id, String password);

    /**
     * Prova l'inserimento a mano senza salvare nulla: competenza, conflitti, persona, azienda e rapporto nuovi, avvisi.
     *
     * @throws com.fcusumano.sam.shared.DatiNonValidiException dati mancanti o non validi, campo per campo: anche
     *                                                         {@code personaId} o {@code aziendaId} se l'anagrafica
     *                                                         scelta non e' (piu') in archivio, e i dati anagrafici
     *                                                         di una persona o un'azienda "nuova" (id null) che e' gia'
     *                                                         in archivio e va scelta dall'elenco
     */
    VerificaCedolinoManuale verifica(CaricamentoId id, CedolinoManuale dati);

    /**
     * Archivia il cedolino inserito a mano con il PDF originale conservato del file, come farebbe un lettore. Persona e
     * azienda scelte dall'elenco sono usate esattamente: il cedolino va al loro rapporto (creato se manca).
     *
     * @return il caricamento ACQUISITO, con cedolino, persona e rapporto
     * @throws com.fcusumano.sam.shared.DatiNonValidiException          dati mancanti o non validi, campo per campo,
     *                                                                  come nella verifica
     * @throws com.fcusumano.sam.shared.OperazioneNonConsentitaException cedolino della stessa mensilita' gia' presente
     *                                                                  (il file resta in errore)
     */
    Caricamento inserisci(CaricamentoId id, CedolinoManuale dati);
}
