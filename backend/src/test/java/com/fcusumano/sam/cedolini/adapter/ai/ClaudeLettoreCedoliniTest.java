package com.fcusumano.sam.cedolini.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.core.http.Headers;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.errors.UnexpectedStatusCodeException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.services.blocking.MessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fcusumano.sam.cedolini.application.estrazione.CedolinoEstratto.TipoCedolinoEstratto;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException.Motivo;
import com.fcusumano.sam.cedolini.application.estrazione.InterpreteRispostaEstrazione;
import com.fcusumano.sam.cedolini.config.AiProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Adapter Anthropic con client mockato: costruzione della richiesta, gestione degli stop reason, traduzione degli
 * errori dell'SDK, streaming e concorrenza. Risposte canned con dati inventati.
 */
class ClaudeLettoreCedoliniTest {

    private static final byte[] PDF = "%PDF-1.7 cedolino di prova".getBytes(StandardCharsets.US_ASCII);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);

    private AnthropicClient client;
    private MessageService messaggi;

    @BeforeEach
    void preparaClient() {
        client = mock(AnthropicClient.class);
        messaggi = mock(MessageService.class);
        when(client.messages()).thenReturn(messaggi);
    }

    private static AiProperties properties(String effort, int maxTokens, int concorrenza, boolean fallback) {
        return new AiProperties("claude-opus-5-5", effort, maxTokens, Duration.ofMinutes(5), concorrenza, 2, fallback);
    }

    private static AiProperties properties(String effort, int maxTokens, int concorrenza) {
        return properties(effort, maxTokens, concorrenza, true);
    }

    private ClaudeLettoreCedolini lettore(AiProperties properties) {
        return new ClaudeLettoreCedolini(properties, () -> client, InterpreteRispostaEstrazione.standard(CLOCK));
    }

    private ClaudeLettoreCedolini lettore() {
        return lettore(properties("medium", 16000, 2));
    }

    static String fixture(String nome) throws IOException {
        try (InputStream in = ClaudeLettoreCedoliniTest.class.getResourceAsStream("/fixtures/cedolini/" + nome + ".json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Risposta Messages canned, come arriverebbe dall'API (thinking + testo JSON). */
    static Message risposta(String stopReason, String testo, String stopDetails) throws IOException {
        var mapper = ObjectMappers.jsonMapper();
        var json = mapper.createObjectNode()
                .put("id", "msg_test")
                .put("type", "message")
                .put("role", "assistant")
                .put("model", "claude-opus-5-5")
                .put("stop_reason", stopReason)
                .putNull("stop_sequence");
        var contenuto = json.putArray("content");
        contenuto.addObject().put("type", "thinking").put("thinking", "").put("signature", "firma");
        if (testo != null) {
            contenuto.addObject().put("type", "text").put("text", testo);
        }
        if (stopDetails != null) {
            json.set("stop_details", mapper.readTree(stopDetails));
        }
        json.putObject("usage").put("input_tokens", 2500).put("output_tokens", 900);
        return mapper.treeToValue(json, Message.class);
    }

    @Test
    void laRichiestaUsaModelloEffortStructuredOutputsEIlDocumentoPrimaDelTesto() {
        var params = lettore().richiesta(PDF);

        assertThat(params.model().asString()).isEqualTo("claude-opus-5-5");
        assertThat(params.maxTokens()).isEqualTo(16000);
        assertThat(params.outputConfig().orElseThrow().effort()).contains(OutputConfig.Effort.MEDIUM);

        JsonNode corpo = ObjectMappers.jsonMapper().valueToTree(params._body());
        assertThat(corpo.has("thinking")).as("thinking sempre attivo su claude-opus-5-5: non va inviato").isFalse();
        assertThat(corpo.has("tool_choice")).isFalse();
        var formato = corpo.at("/output_config/format");
        assertThat(formato.get("type").asText()).isEqualTo("json_schema");
        assertThat(formato.at("/schema/type").asText()).isEqualTo("object");
        assertThat(formato.at("/schema/additionalProperties").asBoolean(true)).isFalse();
        assertThat(formato.at("/schema/properties/periodo/properties/tipo/enum").toString())
                .contains("TREDICESIMA", "FINE_RAPPORTO", "AGGIUNTIVA");
        assertThat(formato.toString()).as("schema espanso, senza riferimenti").doesNotContain("$ref", "$defs");

        var sistema = corpo.at("/system/0");
        assertThat(sistema.get("text").asText()).contains("tredicesima", "IBAN", "Continua su foglio");
        assertThat(sistema.at("/cache_control/type").asText()).isEqualTo("ephemeral");

        var blocchi = corpo.at("/messages/0/content");
        assertThat(corpo.at("/messages/0/role").asText()).isEqualTo("user");
        assertThat(blocchi.size()).isEqualTo(2);
        assertThat(blocchi.get(0).get("type").asText()).isEqualTo("document");
        assertThat(blocchi.get(0).at("/source/type").asText()).isEqualTo("base64");
        assertThat(blocchi.get(0).at("/source/media_type").asText()).isEqualTo("application/pdf");
        assertThat(Base64.getDecoder().decode(blocchi.get(0).at("/source/data").asText())).isEqualTo(PDF);
        assertThat(blocchi.get(1).get("type").asText()).isEqualTo("text");
    }

    @Test
    void ilFallbackSuiRifiutiAggiungeHeaderBetaECampoFallbacks() {
        var conFallback = lettore().richiesta(PDF);
        assertThat(conFallback._headers().values("anthropic-beta")).containsExactly("server-side-fallback-2026-07-01");
        JsonNode corpo = ObjectMappers.jsonMapper().valueToTree(conFallback._body());
        assertThat(corpo.get("fallbacks").asText()).isEqualTo("default");

        var senza = lettore(properties("high", 16000, 2, false)).richiesta(PDF);
        assertThat(senza._headers().values("anthropic-beta")).isEmpty();
        JsonNode corpoSenza = ObjectMappers.jsonMapper().valueToTree(senza._body());
        assertThat(corpoSenza.has("fallbacks")).isFalse();
        assertThat(senza.outputConfig().orElseThrow().effort()).contains(OutputConfig.Effort.HIGH);
    }

    @Test
    void unEffortNonValidoEUnErroreDiConfigurazione() {
        assertThatThrownBy(() -> lettore(properties("altissimo", 16000, 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("salary-manager.ai.effort");
    }

    @Test
    void estraeEInterpretaLaRispostaJson() throws IOException {
        when(messaggi.create(any(MessageCreateParams.class))).thenReturn(risposta("end_turn", fixture("rossi-alfa-2023-13"), null));

        var risultato = lettore().estrai(PDF);

        assertThat(risultato.modello()).isEqualTo("claude-opus-5-5");
        assertThat(risultato.cedolino().periodo().tipo()).isEqualTo(TipoCedolinoEstratto.TREDICESIMA);
        assertThat(risultato.cedolino().periodo().anno()).isEqualTo(2023);
        assertThat(risultato.cedolino().contieneTredicesima()).isTrue();
        assertThat(risultato.jsonOriginale()).contains("\"tipo\":\"TREDICESIMA\"");
        verify(messaggi, never()).createStreaming(any(MessageCreateParams.class));
    }

    @Test
    void ilRifiutoEUnErroreDiEstrazioneConLaCategoria() throws IOException {
        when(messaggi.create(any(MessageCreateParams.class))).thenReturn(
                risposta("refusal", null, "{\"type\":\"refusal\",\"category\":\"cyber\",\"explanation\":null}"));

        assertThatThrownBy(() -> lettore().estrai(PDF))
                .isInstanceOfSatisfying(EstrazioneCedolinoException.class, e -> {
                    assertThat(e.motivo()).isEqualTo(Motivo.RIFIUTO);
                    assertThat(e.getMessage()).contains("rifiutato").contains("cyber");
                });
    }

    @Test
    void ilRifiutoSenzaDettagliERestaUnRifiuto() throws IOException {
        when(messaggi.create(any(MessageCreateParams.class))).thenReturn(risposta("refusal", null, null));

        assertThatThrownBy(() -> lettore().estrai(PDF))
                .isInstanceOfSatisfying(EstrazioneCedolinoException.class, e -> assertThat(e.motivo()).isEqualTo(Motivo.RIFIUTO));
    }

    @Test
    void laRispostaTroncataPerMaxTokensEUnErroreDedicato() throws IOException {
        when(messaggi.create(any(MessageCreateParams.class))).thenReturn(risposta("max_tokens", "{\"numeroCedol", null));

        assertThatThrownBy(() -> lettore().estrai(PDF))
                .isInstanceOfSatisfying(EstrazioneCedolinoException.class, e -> {
                    assertThat(e.motivo()).isEqualTo(Motivo.LIMITE_TOKEN);
                    assertThat(e.getMessage()).contains("16000");
                });
    }

    @Test
    void stopReasonInattesiERisposteSenzaTestoSonoOutputNonValidi() throws IOException {
        when(messaggi.create(any(MessageCreateParams.class))).thenReturn(risposta("tool_use", "{}", null));
        assertThatThrownBy(() -> lettore().estrai(PDF))
                .isInstanceOfSatisfying(EstrazioneCedolinoException.class,
                        e -> assertThat(e.motivo()).isEqualTo(Motivo.OUTPUT_NON_VALIDO));

        when(messaggi.create(any(MessageCreateParams.class))).thenReturn(risposta("end_turn", null, null));
        assertThatThrownBy(() -> lettore().estrai(PDF))
                .isInstanceOfSatisfying(EstrazioneCedolinoException.class,
                        e -> assertThat(e.motivo()).isEqualTo(Motivo.OUTPUT_NON_VALIDO));

        when(messaggi.create(any(MessageCreateParams.class))).thenReturn(risposta("end_turn", "non e' json", null));
        assertThatThrownBy(() -> lettore().estrai(PDF))
                .isInstanceOfSatisfying(EstrazioneCedolinoException.class,
                        e -> assertThat(e.motivo()).isEqualTo(Motivo.OUTPUT_NON_VALIDO));
    }

    @Test
    void conPiuBlocchiDiTestoSiUsaLUltimo() throws IOException {
        var mapper = ObjectMappers.jsonMapper();
        ObjectNode json = mapper.valueToTree(risposta("end_turn", fixture("rossi-beta-2024-12"), null));
        // output parziale del modello che ha rifiutato, poi la risposta completa del modello di fallback
        ((com.fasterxml.jackson.databind.node.ArrayNode) json.get("content")).insert(0,
                mapper.createObjectNode().put("type", "text").put("text", "{\"numeroCedoliniNel"));
        json.put("model", "claude-opus-5");
        when(messaggi.create(any(MessageCreateParams.class))).thenReturn(mapper.treeToValue(json, Message.class));

        var risultato = lettore().estrai(PDF);

        assertThat(risultato.modello()).isEqualTo("claude-opus-5");
        assertThat(risultato.cedolino().contieneTredicesima()).isTrue();
    }

    static Stream<Object[]> erroriSdk() {
        var vuoto = Headers.builder().build();
        var corpo = JsonValue.from(Map.of());
        return Stream.of(
                new Object[] {RateLimitException.builder().headers(vuoto).body(corpo).build(), Motivo.SERVIZIO_NON_DISPONIBILE},
                new Object[] {InternalServerException.builder().statusCode(500).headers(vuoto).body(corpo).build(),
                        Motivo.SERVIZIO_NON_DISPONIBILE},
                new Object[] {UnexpectedStatusCodeException.builder().statusCode(529).headers(vuoto).body(corpo).build(),
                        Motivo.SERVIZIO_NON_DISPONIBILE},
                new Object[] {new AnthropicIoException("timeout"), Motivo.SERVIZIO_NON_DISPONIBILE},
                new Object[] {UnauthorizedException.builder().headers(vuoto).body(corpo).build(), Motivo.CONFIGURAZIONE},
                new Object[] {NotFoundException.builder().headers(vuoto).body(corpo).build(), Motivo.CONFIGURAZIONE},
                new Object[] {BadRequestException.builder().headers(vuoto).body(corpo).build(), Motivo.DOCUMENTO_NON_VALIDO},
                new Object[] {UnexpectedStatusCodeException.builder().statusCode(413).headers(vuoto).body(corpo).build(),
                        Motivo.DOCUMENTO_NON_VALIDO});
    }

    @ParameterizedTest
    @MethodSource("erroriSdk")
    void gliErroriDellSdkDiventanoMotiviDiEstrazione(AnthropicException errore, Motivo atteso) {
        when(messaggi.create(any(MessageCreateParams.class))).thenThrow(errore);

        assertThatThrownBy(() -> lettore().estrai(PDF))
                .isInstanceOfSatisfying(EstrazioneCedolinoException.class, e -> {
                    assertThat(e.motivo()).isEqualTo(atteso);
                    assertThat(e.getCause()).isSameAs(errore);
                });
    }

    @Test
    void senzaChiaveApiLErroreArrivaAllUsoComeConfigurazione() {
        var fornitore = new FornitoreClientAnthropic(properties("medium", 16000, 1), variabile -> null);
        var lettore = new ClaudeLettoreCedolini(properties("medium", 16000, 1), fornitore,
                InterpreteRispostaEstrazione.standard(CLOCK));

        assertThatThrownBy(() -> lettore.estrai(PDF))
                .isInstanceOfSatisfying(EstrazioneCedolinoException.class, e -> {
                    assertThat(e.motivo()).isEqualTo(Motivo.CONFIGURAZIONE);
                    assertThat(e.getMessage()).contains("ANTHROPIC_API_KEY");
                });
        fornitore.close();
    }

    @Test
    void conUnaChiaveIlClientVieneCreatoUnaSolaVolta() {
        var fornitore = new FornitoreClientAnthropic(properties("medium", 16000, 1), variabile -> "sk-ant-chiave-di-prova");
        try {
            assertThat(fornitore.get()).isSameAs(fornitore.get());
        } finally {
            fornitore.close();
        }
    }

    @Test
    void conMoltiTokenSiUsaLoStreaming() throws IOException {
        var json = fixture("rossi-alfa-2024-tfr01");
        var mapper = ObjectMappers.jsonMapper();
        var eventi = new ArrayList<RawMessageStreamEvent>();
        var inizio = mapper.createObjectNode().put("type", "message_start");
        inizio.putObject("message").put("id", "msg_stream").put("type", "message").put("role", "assistant")
                .put("model", "claude-opus-5-5").putNull("stop_reason").putNull("stop_sequence")
                .set("content", mapper.createArrayNode());
        ((ObjectNode) inizio.get("message")).putObject("usage").put("input_tokens", 3000).put("output_tokens", 1);
        eventi.add(mapper.treeToValue(inizio, RawMessageStreamEvent.class));
        var blocco = mapper.createObjectNode().put("type", "content_block_start").put("index", 0);
        blocco.putObject("content_block").put("type", "text").put("text", "");
        eventi.add(mapper.treeToValue(blocco, RawMessageStreamEvent.class));
        int meta = json.length() / 2;
        for (var parte : List.of(json.substring(0, meta), json.substring(meta))) {
            var delta = mapper.createObjectNode().put("type", "content_block_delta").put("index", 0);
            delta.putObject("delta").put("type", "text_delta").put("text", parte);
            eventi.add(mapper.treeToValue(delta, RawMessageStreamEvent.class));
        }
        eventi.add(mapper.treeToValue(mapper.createObjectNode().put("type", "content_block_stop").put("index", 0),
                RawMessageStreamEvent.class));
        var fine = mapper.createObjectNode().put("type", "message_delta");
        fine.putObject("delta").put("stop_reason", "end_turn").putNull("stop_sequence");
        fine.putObject("usage").put("output_tokens", 4200);
        eventi.add(mapper.treeToValue(fine, RawMessageStreamEvent.class));
        eventi.add(mapper.treeToValue(mapper.createObjectNode().put("type", "message_stop"), RawMessageStreamEvent.class));

        @SuppressWarnings("unchecked")
        StreamResponse<RawMessageStreamEvent> stream = mock(StreamResponse.class);
        when(stream.stream()).thenReturn(eventi.stream());
        when(messaggi.createStreaming(any(MessageCreateParams.class))).thenReturn(stream);

        var risultato = lettore(properties("medium", 32000, 2)).estrai(PDF);

        assertThat(risultato.cedolino().periodo().tipo()).isEqualTo(TipoCedolinoEstratto.FINE_RAPPORTO);
        assertThat(risultato.cedolino().tfr().liquidatoLordo()).isNotNull();
        verify(messaggi, never()).create(any(MessageCreateParams.class));
        verify(stream).close();
    }

    @Test
    void leEstrazioniContemporaneeSonoLimitateDallaConcorrenza() throws Exception {
        var risposta = risposta("end_turn", fixture("rossi-beta-2024-07"), null);
        var inCorso = new AtomicInteger();
        var massimo = new AtomicInteger();
        when(messaggi.create(any(MessageCreateParams.class))).thenAnswer(invocazione -> {
            massimo.accumulateAndGet(inCorso.incrementAndGet(), Math::max);
            Thread.sleep(80);
            inCorso.decrementAndGet();
            return risposta;
        });
        var lettore = lettore(properties("medium", 16000, 2));

        try (var esecutore = Executors.newVirtualThreadPerTaskExecutor()) {
            var esiti = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 6; i++) {
                esiti.add(esecutore.submit(() -> lettore.estrai(PDF)));
            }
            for (var esito : esiti) {
                esito.get();
            }
        }
        assertThat(massimo.get()).isBetween(1, 2);
    }
}
