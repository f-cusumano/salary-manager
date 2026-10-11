package com.fcusumano.sam.anagrafica;

import java.util.List;
import java.util.Optional;

/**
 * Letture anagrafiche per la UI e per gli altri moduli (workspace corrente).
 */
public interface ConsultazioneAnagrafica {

    /** Persone del workspace ordinate per cognome e nome, con etichetta anti-omonimia. */
    List<PersonaSintesi> elencoPersone();

    /**
     * Aziende del workspace ordinate per ragione sociale (senza distinzione tra maiuscole e minuscole), poi per id,
     * con l'etichetta anti-omonimia calcolata su tutte le aziende del workspace.
     */
    List<AziendaSelezionabile> elencoAziende();

    /**
     * Prova a vuoto del riconoscimento di {@link CensimentoAnagrafica#trovaOCreaAzienda}: l'azienda gia' censita a cui
     * verrebbero abbinati i dati, con il dato che determina l'abbinamento; vuoto se verrebbe creata una nuova azienda
     * o se manca la ragione sociale (il censimento rifiuterebbe i dati). Non scrive nulla e non solleva eccezioni sui
     * dati, cosi' si puo' usare anche dentro una transazione da non annullare.
     */
    Optional<AziendaRiconosciuta> aziendaRiconosciuta(DatiAzienda dati);

    Optional<Persona> trovaPersona(PersonaId id);

    Optional<Azienda> trovaAzienda(AziendaId id);

    Optional<RapportoLavoro> trovaRapporto(RapportoLavoroId id);

    /**
     * Rapporti della persona in ordine cronologico inverso (per inizio periodo decrescente; i rapporti attuali
     * per primi), con periodo ed etichetta anti-omonimia dell'azienda.
     *
     * @throws com.fcusumano.sam.shared.RisorsaNonTrovataException se la persona non esiste
     */
    List<RapportoSintesi> rapportiDiPersona(PersonaId personaId);

    Optional<RapportoLavoroDettaglio> dettaglioRapporto(RapportoLavoroId id);
}
