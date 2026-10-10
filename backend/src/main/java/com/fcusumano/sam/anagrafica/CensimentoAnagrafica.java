package com.fcusumano.sam.anagrafica;

import java.time.YearMonth;

/**
 * Censimento di persone, aziende e rapporti a partire dai dati estratti dai cedolini.
 * Usata dal modulo cedolini nella stessa transazione di salvataggio del cedolino.
 * Tutte le operazioni lavorano sul workspace corrente.
 */
public interface CensimentoAnagrafica {

    /**
     * Trova la persona per codice fiscale (normalizzato maiuscolo, senza spazi) o la crea.
     * Se esiste, completa i dati mancanti (es. data di nascita) senza sovrascrivere nome e cognome gia' presenti.
     * Un codice fiscale formalmente non valido (carattere di controllo errato, formato inatteso) viene accettato
     * comunque: l'anomalia e' solo registrata nel log a livello DEBUG, senza il valore.
     *
     * @throws IllegalArgumentException se il codice fiscale manca o supera i 16 caratteri, oppure se la persona va
     *                                  creata e mancano nome o cognome
     */
    Persona trovaOCreaPersona(DatiPersona dati);

    /**
     * Trova l'azienda o la crea. Ordine di matching:
     * <ol>
     *   <li>codice fiscale o partita IVA (confrontati in modo incrociato: un CF numerico di 11 cifre equivale alla P.IVA);</li>
     *   <li>posizione/matricola INPS;</li>
     *   <li>ragione sociale normalizzata (maiuscolo, senza punteggiatura, forme societarie uniformate: "S.R.L." = "SRL").</li>
     * </ol>
     * Un'azienda con identificativi fiscali o matricola INPS diversi da quelli letti non viene mai abbinata per
     * ragione sociale. Se trovata, aggiorna gli attributi non chiave (indirizzo, codice azienda, ragione sociale) quando
     * {@code dati.riferimento()} e' uguale o successivo a quello dei dati presenti, e completa gli identificativi
     * mancanti.
     *
     * @throws IllegalArgumentException se manca la ragione sociale
     */
    Azienda trovaOCreaAzienda(DatiAzienda dati);

    /**
     * Azienda gia' censita scelta dall'utente (inserimento a mano): nessun abbinamento, i dati vanno a questa azienda.
     * Stessi aggiornamenti di un'azienda riconosciuta da {@link #trovaOCreaAzienda}: attributi non chiave (indirizzo,
     * codice azienda, ragione sociale) se {@code dati.riferimento()} e' uguale o successivo a quello dei dati presenti,
     * identificativi mancanti completati se non usati da altre aziende.
     *
     * @throws com.fcusumano.sam.shared.RisorsaNonTrovataException se l'azienda non esiste nel workspace
     * @throws IllegalArgumentException                            se manca la ragione sociale
     */
    Azienda aggiornaAziendaScelta(AziendaId id, DatiAzienda dati);

    /**
     * Trova il rapporto persona-azienda o lo crea. Se esiste lo aggiorna: date di assunzione/cessazione, qualifica,
     * livello e codice dipendente vengono sovrascritti solo se {@code dati.riferimento()} e' uguale o successivo al
     * riferimento dei dati gia' presenti (valori null non cancellano dati presenti). Il CCNL riconosciuto viene
     * impostato solo se il rapporto non ha un CCNL scelto dall'utente e se esiste in tabella. Date incoerenti
     * (cessazione precedente all'assunzione, es. riassunzione presso la stessa azienda): si scarta la data proveniente
     * dal cedolino meno recente.
     *
     * @throws com.fcusumano.sam.shared.RisorsaNonTrovataException se la persona o l'azienda non esistono
     */
    RapportoLavoro trovaOCreaRapporto(PersonaId personaId, AziendaId aziendaId, DatiRapporto dati);

    /**
     * Forza i dati del rapporto ignorando il criterio di recenza (es. dopo l'eliminazione di un cedolino, con i dati
     * dell'ultimo cedolino rimasto): date, qualifica, livello e codice dipendente sono sostituiti dai valori indicati,
     * anche se null, e il riferimento diventa {@code dati.riferimento()}. Non modifica un CCNL scelto dall'utente.
     *
     * @throws com.fcusumano.sam.shared.RisorsaNonTrovataException se il rapporto non esiste
     */
    RapportoLavoro sovrascriviDatiRapporto(RapportoLavoroId rapportoId, DatiRapporto dati);

    /**
     * Aggiorna gli estremi dei cedolini caricati per il rapporto (usati per il periodo quando mancano le date
     * contrattuali). Entrambi null se il rapporto non ha piu' cedolini.
     *
     * @throws com.fcusumano.sam.shared.RisorsaNonTrovataException se il rapporto non esiste
     */
    void aggiornaEstremiCedolini(RapportoLavoroId rapportoId, YearMonth primoCedolino, YearMonth ultimoCedolino);

    /**
     * Elimina il rapporto (chiamato dal modulo cedolini quando ne viene eliminato l'ultimo cedolino) e, se rimaste
     * senza rapporti, la persona e l'azienda. Pubblica {@link RapportoLavoroEliminato} prima della cancellazione.
     * I dati derivati (riepilogo, segnalazioni) vengono rimossi in cascata dal database. Se il rapporto non esiste non
     * fa nulla.
     */
    void eliminaRapporto(RapportoLavoroId rapportoId);
}
