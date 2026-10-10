package com.fcusumano.sam.cedolini.adapter.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException.Motivo;
import com.fcusumano.sam.cedolini.config.AiProperties;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Crea il client Anthropic al primo uso, non all'avvio: l'applicazione parte anche senza chiave API e l'assenza della
 * chiave diventa un errore di estrazione {@link Motivo#CONFIGURAZIONE} al primo cedolino caricato.
 * La chiave {@value #VARIABILE_CHIAVE} arriva dalla funzione passata al costruttore: in produzione
 * {@link com.fcusumano.sam.shared.config.VariabiliAmbiente}, cioe' la variabile d'ambiente non vuota e poi il file
 * {@code .env}, mai {@code application*.yml}, system property o argomenti. Prima di creare il client la chiave viene
 * ripulita dagli spazi ai lati e verificata: i messaggi d'errore non ne riportano mai il valore. Thread-safe.
 */
final class FornitoreClientAnthropic implements Supplier<AnthropicClient>, AutoCloseable {

    static final String VARIABILE_CHIAVE = "ANTHROPIC_API_KEY";

    private final AiProperties properties;
    private final Function<String, String> variabili;
    private volatile AnthropicClient client;

    /**
     * @param variabili lettura di una variabile per nome, {@code null} se assente
     */
    FornitoreClientAnthropic(AiProperties properties, Function<String, String> variabili) {
        this.properties = properties;
        this.variabili = variabili;
    }

    @Override
    public AnthropicClient get() {
        var corrente = client;
        if (corrente != null) {
            return corrente;
        }
        synchronized (this) {
            if (client == null) {
                var chiave = chiaveValida(variabili.apply(VARIABILE_CHIAVE));
                // Retry automatici dell'SDK su 408/409/429/5xx ed errori di rete, con backoff esponenziale
                client = AnthropicOkHttpClient.builder()
                        .apiKey(chiave)
                        .timeout(properties.timeout())
                        .maxRetries(properties.maxRetries())
                        .build();
            }
            return client;
        }
    }

    /**
     * Chiave senza spazi ai lati, se valida. Va in un header HTTP: sono ammessi solo caratteri ASCII stampabili, senza
     * spazi. Con un carattere diverso (virgolette tipografiche o spazio non separabile incollati con la chiave) l'SDK
     * fallirebbe con un errore che contiene la chiave in chiaro; qui l'errore indica solo posizione e tipo del
     * carattere.
     */
    static String chiaveValida(String valore) {
        var chiave = valore == null ? "" : valore.strip();
        if (chiave.isEmpty()) {
            throw new EstrazioneCedolinoException(Motivo.CONFIGURAZIONE,
                    "Chiave API Anthropic assente: imposta " + VARIABILE_CHIAVE + " nel file .env della radice"
                            + " del progetto o come variabile d'ambiente, poi riavvia il backend");
        }
        for (int i = 0; i < chiave.length(); i++) {
            char c = chiave.charAt(i);
            if (c < '!' || c > '~') {
                throw new EstrazioneCedolinoException(Motivo.CONFIGURAZIONE,
                        "Chiave API Anthropic non valida: " + VARIABILE_CHIAVE + " contiene " + tipo(c)
                                + " (carattere n. " + (i + 1) + "). La chiave è fatta solo di caratteri ASCII"
                                + " stampabili senza spazi: correggila nel file .env o nella variabile d'ambiente e"
                                + " riavvia il backend");
            }
        }
        return chiave;
    }

    private static String tipo(char c) {
        if (c == ' ' || c == '\t') {
            return "uno spazio";
        }
        return c < ' ' || c == '\u007f' ? "un carattere di controllo"
                : "un carattere non ASCII (per esempio virgolette tipografiche o uno spazio non separabile)";
    }

    @Override
    public synchronized void close() {
        if (client != null) {
            client.close();
            client = null;
        }
    }
}
