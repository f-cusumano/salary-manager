package com.fcusumano.sam.cedolini.adapter.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicRetryableException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.CredentialResolutionException;
import com.anthropic.errors.NoCredentialsException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.errors.UnprocessableEntityException;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.Base64PdfSource;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RefusalStopDetails;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.fcusumano.sam.cedolini.application.estrazione.LettoreCedolini;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException.Motivo;
import com.fcusumano.sam.cedolini.application.estrazione.InterpreteRispostaEstrazione;
import com.fcusumano.sam.cedolini.application.estrazione.SchemaEstrazione;
import com.fcusumano.sam.cedolini.config.AiProperties;
import com.fcusumano.sam.shared.config.VariabiliAmbiente;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;

/**
 * Lettore dei cedolini basato su Claude (SDK ufficiale com.anthropic, endpoint Messages), attivo con
 * {@code salary-manager.lettore=claude}: a pagamento (crediti API Anthropic), legge anche scansioni e layout che il
 * lettore locale non conosce.
 * <p>
 * Richiesta: prompt di sistema versionato ({@code prompts/estrazione-cedolino-v1.md}, con cache del prompt), PDF come
 * blocco {@code document} base64 seguito da una breve istruzione, structured outputs ({@code output_config.format})
 * vincolati allo schema {@code prompts/cedolino-schema-v1.json}, effort da configurazione. Il thinking non viene
 * impostato: su claude-opus-5-5 e' sempre attivo e la profondita' si regola solo con l'effort. Con
 * {@code salary-manager.ai.max-tokens} oltre {@value #SOGLIA_STREAMING} si usa lo streaming (risposte lunghe).
 * <p>
 * Fallback sui rifiuti (default attivo, {@code salary-manager.ai.fallback-rifiuti}): header
 * {@code anthropic-beta: server-side-fallback-2026-07-01} e campo {@code "fallbacks": "default"}. Se il modello rifiuta
 * per una categoria di sicurezza, l'API ripete la richiesta sul modello di fallback raccomandato e restituisce la sua
 * risposta; {@code stop_reason = refusal} arriva solo se rifiuta tutta la catena.
 * <p>
 * Esito: {@code refusal} -> {@link Motivo#RIFIUTO}, {@code max_tokens} -> {@link Motivo#LIMITE_TOKEN}; errori 429/5xx e
 * di rete (dopo i retry dell'SDK) -> {@link Motivo#SERVIZIO_NON_DISPONIBILE}; 401/403/404 -> {@link Motivo#CONFIGURAZIONE};
 * 400/413/422 -> {@link Motivo#DOCUMENTO_NON_VALIDO}. L'output e' validato da {@link InterpreteRispostaEstrazione}.
 * Richieste contemporanee limitate da {@code salary-manager.ai.concurrency}. Nei log solo metadati (modello, token, durata).
 */
@Component
@ConditionalOnProperty(name = "salary-manager.lettore", havingValue = "claude")
class ClaudeLettoreCedolini implements LettoreCedolini, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ClaudeLettoreCedolini.class);

    static final String RISORSA_PROMPT = "/prompts/estrazione-cedolino-" + SchemaEstrazione.VERSIONE + ".md";
    static final String ISTRUZIONE_UTENTE = "Estrai i dati del cedolino contenuto nel documento allegato, seguendo le "
            + "istruzioni e lo schema JSON richiesto.";
    static final String HEADER_BETA = "anthropic-beta";
    static final String BETA_FALLBACK = "server-side-fallback-2026-07-01";
    static final String CAMPO_FALLBACKS = "fallbacks";
    /** Oltre questa soglia di max_tokens le richieste non in streaming rischiano il timeout HTTP: si usa lo streaming. */
    static final long SOGLIA_STREAMING = 21_333;
    private static final Set<String> EFFORT_AMMESSI = Set.of("low", "medium", "high", "xhigh", "max");

    private final AiProperties properties;
    private final Supplier<AnthropicClient> client;
    private final InterpreteRispostaEstrazione interprete;
    private final Semaphore permessi;
    private final String promptSistema;
    private final JsonOutputFormat formato;
    private final OutputConfig.Effort effort;

    /** La chiave API si legge con {@link VariabiliAmbiente}: variabile d'ambiente o file {@code .env}. */
    @Autowired
    ClaudeLettoreCedolini(AiProperties properties, Clock clock, ConfigurableEnvironment ambiente) {
        this(properties,
                new FornitoreClientAnthropic(properties, nome -> VariabiliAmbiente.leggi(ambiente, nome).orElse(null)),
                InterpreteRispostaEstrazione.standard(clock));
    }

    ClaudeLettoreCedolini(AiProperties properties, Supplier<AnthropicClient> client,
                                InterpreteRispostaEstrazione interprete) {
        var livello = properties.effort() == null ? "" : properties.effort().strip().toLowerCase(Locale.ROOT);
        if (!EFFORT_AMMESSI.contains(livello)) {
            throw new IllegalArgumentException("salary-manager.ai.effort non valido: " + properties.effort()
                    + " (ammessi: low, medium, high, xhigh, max)");
        }
        this.properties = properties;
        this.client = client;
        this.interprete = interprete;
        this.permessi = new Semaphore(Math.max(1, properties.concurrency()), true);
        this.promptSistema = leggiPrompt();
        this.formato = formatoJson(SchemaEstrazione.carica());
        this.effort = OutputConfig.Effort.of(livello);
    }

    @Override
    public RisultatoEstrazione estrai(byte[] pdf) {
        if (pdf == null || pdf.length == 0) {
            throw new EstrazioneCedolinoException(Motivo.DOCUMENTO_NON_VALIDO, "Documento vuoto");
        }
        var params = richiesta(pdf);
        acquisisciPermesso();
        try {
            long inizio = System.nanoTime();
            var risposta = invia(params);
            long durataMs = (System.nanoTime() - inizio) / 1_000_000;
            var modello = risposta.model().asString();
            log.info("Estrazione cedolino: modello={}, stop={}, token input={}, output={}, durata={} ms",
                    modello, risposta.stopReason().map(StopReason::asString).orElse("-"),
                    risposta.usage().inputTokens(), risposta.usage().outputTokens(), durataMs);
            if (!modello.equals(properties.model())) {
                log.info("Risposta prodotta dal modello {} (richiesto {}): fallback sul rifiuto", modello,
                        properties.model());
            }
            return interprete.interpreta(testoJson(risposta), modello);
        } finally {
            permessi.release();
        }
    }

    /** Richiesta Messages per un PDF: documento prima del testo, structured outputs, effort, eventuale fallback. */
    MessageCreateParams richiesta(byte[] pdf) {
        var documento = DocumentBlockParam.builder()
                .source(Base64PdfSource.builder().data(Base64.getEncoder().encodeToString(pdf)).build())
                .build();
        var builder = MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(properties.maxTokens())
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(promptSistema)
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()))
                .outputConfig(OutputConfig.builder().effort(effort).format(formato).build())
                .addUserMessageOfBlockParams(List.of(
                        ContentBlockParam.ofDocument(documento),
                        ContentBlockParam.ofText(TextBlockParam.builder().text(ISTRUZIONE_UTENTE).build())));
        if (properties.fallbackRifiuti()) {
            builder.putAdditionalHeader(HEADER_BETA, BETA_FALLBACK)
                    .putAdditionalBodyProperty(CAMPO_FALLBACKS, JsonValue.from("default"));
        }
        return builder.build();
    }

    private Message invia(MessageCreateParams params) {
        try {
            var messaggi = client.get().messages();
            if (properties.maxTokens() <= SOGLIA_STREAMING) {
                return messaggi.create(params);
            }
            try (var stream = messaggi.createStreaming(params)) {
                var accumulatore = MessageAccumulator.create();
                stream.stream().forEach(accumulatore::accumulate);
                return accumulatore.message();
            }
        } catch (AnthropicException e) {
            throw traduci(e);
        }
    }

    /** Converte gli errori dell'SDK distinguendo i ritentabili (gia' ritentati dall'SDK) dai definitivi. */
    static EstrazioneCedolinoException traduci(AnthropicException e) {
        log.warn("Errore del servizio Claude: {}", e.getClass().getSimpleName());
        return switch (e) {
            case UnauthorizedException u -> new EstrazioneCedolinoException(Motivo.CONFIGURAZIONE,
                    "Chiave API Anthropic non valida: verifica ANTHROPIC_API_KEY", u);
            case PermissionDeniedException p -> new EstrazioneCedolinoException(Motivo.CONFIGURAZIONE,
                    "La chiave API Anthropic non ha accesso al modello o alla funzionalità richiesta", p);
            case NotFoundException n -> new EstrazioneCedolinoException(Motivo.CONFIGURAZIONE,
                    "Modello Claude non trovato: verifica salary-manager.ai.model", n);
            case BadRequestException b -> new EstrazioneCedolinoException(Motivo.DOCUMENTO_NON_VALIDO,
                    "Richiesta non accettata dal servizio Claude (documento non elaborabile)", b);
            case UnprocessableEntityException u -> new EstrazioneCedolinoException(Motivo.DOCUMENTO_NON_VALIDO,
                    "Richiesta non accettata dal servizio Claude (documento non elaborabile)", u);
            case AnthropicServiceException s when s.statusCode() == 413 -> new EstrazioneCedolinoException(
                    Motivo.DOCUMENTO_NON_VALIDO, "Il PDF è troppo grande per il servizio Claude", s);
            case AnthropicServiceException s -> new EstrazioneCedolinoException(Motivo.SERVIZIO_NON_DISPONIBILE,
                    "Servizio Claude non disponibile (errore " + s.statusCode() + "): riprova più tardi", s);
            case AnthropicIoException io -> new EstrazioneCedolinoException(Motivo.SERVIZIO_NON_DISPONIBILE,
                    "Servizio Claude non raggiungibile (errore di rete o timeout): riprova più tardi", io);
            case AnthropicRetryableException r -> new EstrazioneCedolinoException(Motivo.SERVIZIO_NON_DISPONIBILE,
                    "Servizio Claude temporaneamente non disponibile: riprova più tardi", r);
            case AnthropicInvalidDataException d -> new EstrazioneCedolinoException(Motivo.OUTPUT_NON_VALIDO,
                    "Risposta del servizio Claude non interpretabile", d);
            case NoCredentialsException c -> new EstrazioneCedolinoException(Motivo.CONFIGURAZIONE,
                    "Credenziali Anthropic assenti: imposta ANTHROPIC_API_KEY", c);
            case CredentialResolutionException c -> new EstrazioneCedolinoException(Motivo.CONFIGURAZIONE,
                    "Credenziali Anthropic non utilizzabili: verifica ANTHROPIC_API_KEY", c);
            default -> new EstrazioneCedolinoException(Motivo.SERVIZIO_NON_DISPONIBILE,
                    "Errore del servizio Claude: riprova più tardi", e);
        };
    }

    /**
     * Testo JSON della risposta dopo il controllo dello stop reason. Si usa l'ultimo blocco di testo: con il fallback in
     * streaming l'eventuale output parziale del modello che ha rifiutato precede quello del modello di fallback.
     */
    String testoJson(Message risposta) {
        var stop = risposta.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stop)) {
            var categoria = risposta.stopDetails().flatMap(RefusalStopDetails::category)
                    .map(c -> " (categoria " + c.asString() + ")").orElse("");
            throw new EstrazioneCedolinoException(Motivo.RIFIUTO,
                    "Il modello ha rifiutato l'estrazione del documento" + categoria);
        }
        if (StopReason.MAX_TOKENS.equals(stop)) {
            throw new EstrazioneCedolinoException(Motivo.LIMITE_TOKEN,
                    "Risposta troncata: raggiunto il limite di " + properties.maxTokens()
                            + " token (aumenta salary-manager.ai.max-tokens)");
        }
        if (StopReason.MODEL_CONTEXT_WINDOW_EXCEEDED.equals(stop)) {
            throw new EstrazioneCedolinoException(Motivo.DOCUMENTO_NON_VALIDO,
                    "Il documento è troppo lungo per il contesto del modello");
        }
        if (!StopReason.END_TURN.equals(stop) && !StopReason.STOP_SEQUENCE.equals(stop)) {
            throw new EstrazioneCedolinoException(Motivo.OUTPUT_NON_VALIDO,
                    "Risposta del modello incompleta (stop reason " + (stop == null ? "assente" : stop.asString()) + ")");
        }
        var testi = risposta.content().stream()
                .flatMap((ContentBlock blocco) -> blocco.text().stream())
                .map(TextBlock::text)
                .filter(t -> !t.isBlank())
                .toList();
        if (testi.isEmpty()) {
            throw new EstrazioneCedolinoException(Motivo.OUTPUT_NON_VALIDO, "Risposta del modello senza contenuto");
        }
        return testi.getLast();
    }

    private void acquisisciPermesso() {
        try {
            permessi.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EstrazioneCedolinoException(Motivo.SERVIZIO_NON_DISPONIBILE, "Estrazione interrotta", e);
        }
    }

    private static JsonOutputFormat formatoJson(SchemaEstrazione schema) {
        var proprieta = new LinkedHashMap<String, JsonValue>();
        schema.schema().forEach((chiave, valore) -> proprieta.put(chiave, JsonValue.from(valore)));
        return JsonOutputFormat.builder()
                .schema(JsonOutputFormat.Schema.builder().additionalProperties(proprieta).build())
                .build();
    }

    private static String leggiPrompt() {
        try (InputStream in = ClaudeLettoreCedolini.class.getResourceAsStream(RISORSA_PROMPT)) {
            if (in == null) {
                throw new IllegalStateException("Prompt di estrazione non trovato: " + RISORSA_PROMPT);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException("Prompt di estrazione non leggibile: " + RISORSA_PROMPT, e);
        }
    }

    @Override
    public void close() throws Exception {
        if (client instanceof AutoCloseable chiudibile) {
            chiudibile.close();
        }
    }
}
