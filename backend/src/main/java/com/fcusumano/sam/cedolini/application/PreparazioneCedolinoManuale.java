package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.anagrafica.AziendaRiconosciuta;
import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.anagrafica.PersonaSintesi;
import com.fcusumano.sam.cedolini.CedolinoManuale;
import com.fcusumano.sam.cedolini.application.AnagraficheScelte.Scelte;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException;
import com.fcusumano.sam.cedolini.application.estrazione.InterpreteRispostaEstrazione;
import com.fcusumano.sam.cedolini.application.estrazione.LettoreCedolini.RisultatoEstrazione;
import com.fcusumano.sam.shared.DatiNonValidiException;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

/**
 * Preparazione comune all'inserimento a mano di un file in errore e alla modifica a mano di un cedolino: il cedolino
 * del modulo diventa un risultato equivalente a una lettura, e gli errori dell'acquisizione diventano errori sui campi.
 * Nei log solo id, dati mancanti e tipi di eccezione.
 */
final class PreparazioneCedolinoManuale {

    private static final Logger log = LoggerFactory.getLogger(PreparazioneCedolinoManuale.class);

    private final ConsultazioneAnagrafica consultazione;
    private final ValidazioneCedolinoManuale validazione;
    private final InterpreteRispostaEstrazione interprete;

    PreparazioneCedolinoManuale(ConsultazioneAnagrafica consultazione, Clock clock) {
        this.consultazione = consultazione;
        this.validazione = new ValidazioneCedolinoManuale(clock);
        this.interprete = InterpreteRispostaEstrazione.standard(clock);
    }

    /** Risultato equivalente a una lettura e anagrafiche scelte dall'elenco. */
    record Preparato(RisultatoEstrazione risultato, Scelte scelte) {
    }

    /**
     * Anagrafiche scelte (in archivio, con i loro dati al posto di quelli inseriti), validazione bloccante (anche
     * "gia' in archivio" per le anagrafiche nuove), normalizzazione, JSON dello schema di lettura e interpretazione
     * (come per ogni lettore); confidenza azzerata (nessuna lettura) e avvisi propri dell'inserimento a mano aggiunti a
     * quelli dei controlli.
     *
     * @param modello identificativo registrato con il cedolino ({@link ModelliEstrazione})
     * @throws DatiNonValidiException se ci sono errori bloccanti: prima, da soli, quelli delle anagrafiche scelte
     */
    Preparato prepara(CedolinoManuale dati, String modello) {
        var risolte = AnagraficheScelte.risolvi(consultazione, dati.personaId(), dati.aziendaId());
        var conDati = AnagraficheScelte.conDatiInArchivio(dati, risolte);
        var codiciFiscali = consultazione.elencoPersone().stream().map(PersonaSintesi::codiceFiscale)
                .collect(Collectors.toSet());
        var errori = validazione.verifica(conDati, codiciFiscali, datore -> AnagraficheScelte.datiAzienda(datore)
                .flatMap(consultazione::aziendaRiconosciuta)
                .map(AziendaRiconosciuta::dato), risolte.dataNascitaInArchivio());
        if (!errori.isEmpty()) {
            throw new DatiNonValidiException(errori);
        }
        var normalizzato = ValidazioneCedolinoManuale.normalizza(conDati);
        RisultatoEstrazione letto;
        try {
            letto = interprete.interpreta(JsonCedolinoManuale.scrivi(normalizzato), modello);
        } catch (EstrazioneCedolinoException e) {
            // la validazione rende il JSON sempre conforme: e' un difetto, non un errore dell'utente
            throw new IllegalStateException("Cedolino inserito a mano non interpretabile (" + e.motivo() + ")", e);
        }
        var avvisi = new ArrayList<>(letto.avvisi());
        avvisi.addAll(ValidazioneCedolinoManuale.avvisi(normalizzato));
        return new Preparato(new RisultatoEstrazione(letto.cedolino().conConfidenza(null), letto.jsonOriginale(),
                letto.modello(), avvisi), risolte.scelte());
    }

    /**
     * Acquisizione (o prova) del cedolino inserito. I dati indispensabili mancanti e i valori rifiutati da anagrafica o
     * competenza, che la validazione dovrebbe gia' escludere, diventano errori sul campo corrispondente; gli errori
     * delle anagrafiche ricontrollate nella transazione arrivano gia' campo per campo.
     *
     * @param descrizione cosa si acquisisce, per i log (es. "Inserimento a mano del caricamento 12"): solo id
     */
    static <T> T conErroriSuiCampi(String descrizione, Supplier<T> acquisizione) {
        try {
            return acquisizione.get();
        } catch (CedolinoNonAcquisibileException e) {
            log.info("{} non acquisibile: dato {}", descrizione, e.dato());
            throw new DatiNonValidiException(campo(e.dato()), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.info("{} rifiutato: {}", descrizione, e.getClass().getSimpleName());
            throw new DatiNonValidiException(null, e.getMessage());
        } catch (DataAccessException e) {
            if (!valoreFuoriColonna(e)) {
                throw e;
            }
            // importi validi uno per uno ma con dati calcolati (RAL, valore dei ratei...) che non stanno nelle colonne
            log.info("{} rifiutato dal database: {}", descrizione, e.getClass().getSimpleName());
            throw new DatiNonValidiException(null, MessaggiCaricamento.VALORI_TROPPO_GRANDI);
        }
    }

    /** Errore SQL della classe 22 (data exception: valore troppo lungo o fuori dall'intervallo della colonna). */
    private static boolean valoreFuoriColonna(DataAccessException e) {
        for (Throwable causa = e; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith("22")) {
                return true;
            }
        }
        return false;
    }

    private static String campo(CedolinoNonAcquisibileException.Dato dato) {
        return switch (dato) {
            case PERIODO -> "periodo.mese";
            case CODICE_FISCALE -> "dipendente.codiceFiscale";
            case DATORE -> "datoreLavoro.ragioneSociale";
        };
    }
}
