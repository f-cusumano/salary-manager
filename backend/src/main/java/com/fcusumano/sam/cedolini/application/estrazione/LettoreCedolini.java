package com.fcusumano.sam.cedolini.application.estrazione;

import java.util.List;

/**
 * Porta di uscita: lettore dei cedolini, cioe' l'interpretazione di un PDF di cedolino in dati strutturati
 * ({@link CedolinoEstratto}). Implementazioni, scelte con {@code salary-manager.lettore} ({@code SAM_LETTORE}):
 * <ul>
 *   <li>{@code locale} (predefinito): lettore locale, gratuito e offline, che legge il testo del PDF e ne riconosce
 *       il layout (adapter {@code adapter.locale}); non legge scansioni ne' layout che non conosce;</li>
 *   <li>{@code claude}: Claude tramite l'API Anthropic (structured outputs vincolati allo schema di
 *       {@link CedolinoEstratto}), a pagamento, legge anche scansioni e layout nuovi (adapter {@code adapter.ai});</li>
 *   <li>{@code fixture}: JSON di fixture per test, sviluppo offline e modalita' demo (adapter {@code adapter.fixture}).</li>
 * </ul>
 * Tutte le implementazioni producono lo stesso JSON (schema {@code prompts/cedolino-schema-v1.json}) e lo fanno passare
 * da {@link InterpreteRispostaEstrazione}: stessa normalizzazione, stessi controlli semantici, stesso JSON di audit.
 * L'interfaccia permette anche di spostare la lettura in un servizio separato.
 * <p>
 * Il PDF passato deve essere leggibile senza password: i PDF protetti vanno prima decifrati con {@link LettorePdf}.
 * Le implementazioni non devono loggare dati personali ne' il contenuto del documento.
 */
public interface LettoreCedolini {

    /**
     * Estrae i dati da un PDF che deve contenere un solo cedolino (anche su piu' fogli).
     *
     * @param pdf byte del PDF (in chiaro)
     * @return dati estratti e validati rispetto allo schema, con il JSON originale per audit
     * @throws EstrazioneCedolinoException se la lettura non va a buon fine (documento non valido o con piu' cedolini,
     *                                     PDF senza testo o layout non riconosciuto dal lettore locale, rifiuto o
     *                                     output non valido del modello, servizio non disponibile, configurazione
     *                                     mancante)
     */
    RisultatoEstrazione estrai(byte[] pdf);

    /**
     * Come {@link #estrai(byte[])}, con il nome del file caricato. Il nome non viene mai inviato al modello: serve solo
     * ai lettori che associano un risultato al file (lettore a fixture). Per default lo ignora.
     *
     * @param nomeFileOriginale nome del file come caricato dall'utente, puo' essere null
     */
    default RisultatoEstrazione estrai(byte[] pdf, String nomeFileOriginale) {
        return estrai(pdf);
    }

    /**
     * Esito di una lettura riuscita.
     *
     * @param cedolino       dati estratti, gia' normalizzati (date ISO, importi decimali, testi ripuliti) e con la
     *                       confidenza ridotta per i controlli semantici non superati
     * @param jsonOriginale  JSON prodotto dal lettore (salvato in {@code cedolino.estratto_json} per audit); eventuali
     *                       dati bancari sono mascherati
     * @param modello        identificativo del lettore usato (es. "claude-opus-5-5", "lettore-locale-1/job-sistemi",
     *                       "fixture")
     * @param avvisi         avvisi della validazione semantica e note interpretative del lettore (mai dati personali)
     */
    record RisultatoEstrazione(CedolinoEstratto cedolino, String jsonOriginale, String modello, List<String> avvisi) {

        public RisultatoEstrazione {
            avvisi = avvisi == null ? List.of() : List.copyOf(avvisi);
        }

        public RisultatoEstrazione(CedolinoEstratto cedolino, String jsonOriginale, String modello) {
            this(cedolino, jsonOriginale, modello, List.of());
        }
    }
}
