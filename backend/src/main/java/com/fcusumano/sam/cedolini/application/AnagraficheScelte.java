package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.anagrafica.Azienda;
import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.AziendaRiconosciuta;
import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.anagrafica.DatiAzienda;
import com.fcusumano.sam.anagrafica.Persona;
import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.anagrafica.PersonaSintesi;
import com.fcusumano.sam.cedolini.CedolinoManuale;
import com.fcusumano.sam.shared.DatiNonValidiException;
import com.fcusumano.sam.shared.DatiNonValidiException.ErroreCampo;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Regole delle anagrafiche dell'inserimento a mano: persona e azienda si scelgono dall'elenco ({@code personaId},
 * {@code aziendaId}) oppure sono nuove ("Crea nuovo", id null).
 * <ul>
 *   <li>un'anagrafica scelta deve essere in archivio ({@code risolvi}); i suoi dati anagrafici sono quelli in
 *       archivio, qualunque cosa sia stata inviata ({@link #conDatiInArchivio});</li>
 *   <li>un'anagrafica nuova non deve essere gia' in archivio ({@link #giaInArchivio}): un codice fiscale di una persona
 *       censita, o dati che il censimento abbinerebbe a un'azienda censita, vanno scelti dall'elenco invece di essere
 *       uniti in silenzio.</li>
 * </ul>
 * Le regole valgono alla preparazione della richiesta e di nuovo nella transazione serializzata dell'acquisizione
 * (un caricamento concorrente puo' creare o eliminare le anagrafiche nel frattempo). I messaggi non ripetono mai i
 * valori.
 */
final class AnagraficheScelte {

    static final String CAMPO_PERSONA = "personaId";
    static final String CAMPO_AZIENDA = "aziendaId";

    private AnagraficheScelte() {
    }

    /**
     * Persona e azienda scelte dall'elenco nell'inserimento a mano; null = nuova.
     */
    record Scelte(PersonaId persona, AziendaId azienda) {

        /** Nessuna scelta: cedolini letti dai PDF, il censimento riconosce o crea le anagrafiche. */
        static final Scelte NESSUNA = new Scelte(null, null);
    }

    /**
     * Anagrafiche scelte lette dall'archivio.
     *
     * @param persona null se la persona e' nuova
     * @param azienda null se l'azienda e' nuova
     */
    record Risolte(Persona persona, Azienda azienda) {

        Scelte scelte() {
            return new Scelte(persona == null ? null : persona.id(), azienda == null ? null : azienda.id());
        }
    }

    /**
     * Anagrafiche scelte lette dall'archivio del workspace corrente.
     *
     * @param personaId id inviato, null per una persona nuova
     * @param aziendaId id inviato, null per un'azienda nuova
     * @throws DatiNonValidiException su {@code personaId} e/o {@code aziendaId} (in quest'ordine) se l'id non e'
     *                                positivo o l'anagrafica non e' (piu') in archivio
     */
    static Risolte risolvi(ConsultazioneAnagrafica consultazione, Long personaId, Long aziendaId) {
        var errori = new ArrayList<ErroreCampo>();
        Persona persona = null;
        if (personaId != null) {
            persona = personaId > 0 ? consultazione.trovaPersona(new PersonaId(personaId)).orElse(null) : null;
            if (persona == null) {
                errori.add(new ErroreCampo(CAMPO_PERSONA, MessaggiCaricamento.PERSONA_NON_IN_ARCHIVIO));
            }
        }
        Azienda azienda = null;
        if (aziendaId != null) {
            azienda = aziendaId > 0 ? consultazione.trovaAzienda(new AziendaId(aziendaId)).orElse(null) : null;
            if (azienda == null) {
                errori.add(new ErroreCampo(CAMPO_AZIENDA, MessaggiCaricamento.AZIENDA_NON_IN_ARCHIVIO));
            }
        }
        if (!errori.isEmpty()) {
            throw new DatiNonValidiException(errori);
        }
        return new Risolte(persona, azienda);
    }

    /** Come {@link #risolvi(ConsultazioneAnagrafica, Long, Long)} per scelte gia' risolte in precedenza. */
    static Risolte risolvi(ConsultazioneAnagrafica consultazione, Scelte scelte) {
        return risolvi(consultazione, scelte.persona() == null ? null : scelte.persona().valore(),
                scelte.azienda() == null ? null : scelte.azienda().valore());
    }

    /**
     * Il cedolino con i dati anagrafici delle anagrafiche scelte al posto di quelli inseriti: codice fiscale, nome e
     * cognome della persona e la sua data di nascita se nota in archivio (altrimenti quella inserita, che completa la
     * persona); ragione sociale, partita IVA, codice fiscale e posizione INPS dell'azienda (null in archivio resta
     * null). Gli altri campi restano quelli inseriti; gli id restano quelli inviati.
     */
    static CedolinoManuale conDatiInArchivio(CedolinoManuale dati, Risolte risolte) {
        var dipendente = dati.dipendente();
        if (risolte.persona() != null) {
            var p = risolte.persona();
            var inserito = dipendente == null
                    ? new CedolinoManuale.Dipendente(null, null, null, null, null) : dipendente;
            dipendente = new CedolinoManuale.Dipendente(p.codiceFiscale(), p.nome(), p.cognome(),
                    p.dataNascita() != null ? p.dataNascita() : inserito.dataNascita(), inserito.codiceDipendente());
        }
        var datore = dati.datoreLavoro();
        if (risolte.azienda() != null) {
            var a = risolte.azienda();
            var inserito = datore == null
                    ? new CedolinoManuale.DatoreLavoro(null, null, null, null, null, null, null) : datore;
            datore = new CedolinoManuale.DatoreLavoro(a.codiceFiscale(), a.partitaIva(), a.posizioneInps(),
                    inserito.posizioneInail(), a.ragioneSociale(), inserito.indirizzo(), inserito.codiceAzienda());
        }
        return new CedolinoManuale(dati.personaId(), dati.aziendaId(), dipendente, datore, dati.datiContrattuali(),
                dati.periodo(), dati.totali(), dati.retribuzione(), dati.voci(), dati.ratei(), dati.tfr(),
                dati.presenze(), dati.scatti(), dati.buoniPasto(), dati.progressivi(), dati.contieneTredicesima(),
                dati.contieneQuattordicesima());
    }

    /**
     * Errori "gia' in archivio" delle anagrafiche non scelte: codice fiscale di una persona censita su
     * {@code dipendente.codiceFiscale}; dati che il censimento abbinerebbe a un'azienda censita sul campo che determina
     * l'abbinamento ({@link #erroreAzienda}).
     *
     * @param codiceFiscale codice fiscale del dipendente come verra' censito (maiuscolo, senza spazi), null se assente
     * @param datore        dati dell'azienda come verranno censiti, null se assenti
     * @return errori nell'ordine dei campi, vuota se nessuno
     */
    static List<ErroreCampo> giaInArchivio(ConsultazioneAnagrafica consultazione, String codiceFiscale,
                                           DatiAzienda datore, Scelte scelte) {
        var errori = new ArrayList<ErroreCampo>();
        if (scelte.persona() == null && codiceFiscale != null && consultazione.elencoPersone().stream()
                .map(PersonaSintesi::codiceFiscale).anyMatch(codiceFiscale::equals)) {
            errori.add(new ErroreCampo("dipendente.codiceFiscale",
                    MessaggiCaricamento.CODICE_FISCALE_PERSONA_IN_ARCHIVIO));
        }
        if (scelte.azienda() == null && datore != null) {
            consultazione.aziendaRiconosciuta(datore).map(r -> erroreAzienda(r.dato())).ifPresent(errori::add);
        }
        return List.copyOf(errori);
    }

    /** Errore sul campo dell'azienda che la farebbe riconoscere come gia' censita. */
    static ErroreCampo erroreAzienda(AziendaRiconosciuta.Dato dato) {
        return switch (dato) {
            case PARTITA_IVA -> new ErroreCampo("datoreLavoro.partitaIva", MessaggiCaricamento.PARTITA_IVA_IN_ARCHIVIO);
            case CODICE_FISCALE -> new ErroreCampo("datoreLavoro.codiceFiscale",
                    MessaggiCaricamento.CODICE_FISCALE_AZIENDA_IN_ARCHIVIO);
            case POSIZIONE_INPS -> new ErroreCampo("datoreLavoro.posizioneInps",
                    MessaggiCaricamento.POSIZIONE_INPS_IN_ARCHIVIO);
            case RAGIONE_SOCIALE -> new ErroreCampo("datoreLavoro.ragioneSociale",
                    MessaggiCaricamento.RAGIONE_SOCIALE_IN_ARCHIVIO);
        };
    }

    /** Dati dell'azienda inserita, nella forma del censimento (senza periodo di riferimento: servono al confronto). */
    static Optional<DatiAzienda> datiAzienda(CedolinoManuale.DatoreLavoro datore) {
        if (datore == null) {
            return Optional.empty();
        }
        return Optional.of(new DatiAzienda(datore.codiceFiscale(), datore.partitaIva(), datore.posizioneInps(),
                datore.ragioneSociale(), datore.indirizzo(), datore.codiceAzienda()));
    }
}
