package com.fcusumano.sam.web.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Il documento OpenAPI generato a runtime da springdoc ({@code /v3/api-docs}) deve rispecchiare il contratto
 * {@code docs/openapi.yaml} usato dal frontend: stessi path, metodi, operationId, parametri, corpi delle richieste,
 * codici di risposta, schemi principali (oggetti con proprieta') e loro proprieta'.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContrattoOpenApiTest {

    private static final Path CONTRATTO = Path.of("..", "docs", "openapi.yaml");
    private static final Set<String> METODI = Set.of("get", "put", "post", "delete", "patch");

    @LocalServerPort
    int porta;

    private Map<String, Object> contratto;
    private Map<String, Object> runtime;

    @BeforeAll
    void leggiDocumenti() throws IOException {
        contratto = carica(Files.readString(CONTRATTO));
        runtime = carica(RestClient.create("http://localhost:" + porta).get().uri("/v3/api-docs")
                .retrieve().body(String.class));
    }

    @Test
    void stessiPathEMetodi() {
        var pathRuntime = mappa(runtime, "paths");
        assertThat(pathRuntime.keySet()).containsExactlyInAnyOrderElementsOf(mappa(contratto, "paths").keySet());
        mappa(contratto, "paths").forEach((path, operazioni) ->
                assertThat(metodi(mappa(pathRuntime, path))).as("metodi di %s", path).isEqualTo(metodi(cast(operazioni))));
    }

    @Test
    void stessiOperationIdParametriCorpiERisposte() {
        var operazioniRuntime = operazioni(runtime);
        operazioni(contratto).forEach((chiave, attesa) -> {
            var effettiva = operazioniRuntime.get(chiave);
            assertThat(effettiva).as("operazione %s", chiave).isNotNull();
            assertThat(effettiva.get("operationId")).as("operationId di %s", chiave).isEqualTo(attesa.get("operationId"));
            assertThat(nomiParametri(effettiva, runtime)).as("parametri di %s", chiave)
                    .isEqualTo(nomiParametri(attesa, contratto));
            assertThat(mappa(effettiva, "responses").keySet()).as("codici di risposta di %s", chiave)
                    .containsExactlyInAnyOrderElementsOf(mappa(attesa, "responses").keySet());
            assertThat(tipiCorpo(effettiva)).as("tipi del corpo di %s", chiave).isEqualTo(tipiCorpo(attesa));
        });
    }

    @Test
    void ilCaricamentoAccettaFileEPasswordFacoltativa() {
        var attesa = schemaMultipart(contratto);
        var effettiva = schemaMultipart(runtime);
        assertThat(mappa(effettiva, "properties").keySet())
                .containsExactlyInAnyOrderElementsOf(mappa(attesa, "properties").keySet())
                .containsExactlyInAnyOrder("files", "password");
        assertThat(effettiva.get("required")).isEqualTo(attesa.get("required")).isEqualTo(List.of("files"));
        var passwordAttesa = mappa(mappa(attesa, "properties"), "password");
        var password = mappa(mappa(effettiva, "properties"), "password");
        for (var chiave : List.of("type", "format", "writeOnly", "maxLength")) {
            assertThat(password.get(chiave)).as("password.%s", chiave).isEqualTo(passwordAttesa.get(chiave));
        }
    }

    @Test
    void stessiSchemiPrincipaliConLeStesseProprieta() {
        var schemiRuntime = mappa(mappa(runtime, "components"), "schemas");
        mappa(mappa(contratto, "components"), "schemas").forEach((nome, schema) -> {
            var proprieta = cast(schema).get("properties");
            if (proprieta == null) {
                return;
            }
            assertThat(schemiRuntime).as("schema %s", nome).containsKey(nome);
            assertThat(mappa(cast(schemiRuntime.get(nome)), "properties").keySet())
                    .as("proprieta' dello schema %s", nome)
                    .containsExactlyInAnyOrderElementsOf(cast(proprieta).keySet());
        });
    }

    @Test
    void iMesiSonoStringheNelFormatoDelContratto() {
        var mese = mappa(mappa(mappa(contratto, "components"), "schemas"), "Mese");
        var periodo = mappa(mappa(mappa(mappa(runtime, "components"), "schemas"), "Periodo"), "properties");
        assertThat(mappa(periodo, "da")).containsEntry("type", "string").containsEntry("pattern", mese.get("pattern"));
    }

    // ------------------------------------------------------------------------------------------ supporto

    private static Map<String, Object> carica(String testo) {
        return new Yaml(new SafeConstructor(new LoaderOptions())).load(testo);
    }

    private static Set<String> metodi(Map<String, Object> operazioni) {
        var metodi = new TreeSet<>(operazioni.keySet());
        metodi.retainAll(METODI);
        return metodi;
    }

    /** "GET /api/v1/persone" -> operazione. */
    private static Map<String, Map<String, Object>> operazioni(Map<String, Object> documento) {
        var risultato = new LinkedHashMap<String, Map<String, Object>>();
        mappa(documento, "paths").forEach((path, voce) -> cast(voce).forEach((metodo, operazione) -> {
            if (METODI.contains(metodo)) {
                risultato.put(metodo.toUpperCase(Locale.ROOT) + " " + path, cast(operazione));
            }
        }));
        return risultato;
    }

    /** Parametri come "in:nome", risolvendo i riferimenti a components/parameters. */
    private static Set<String> nomiParametri(Map<String, Object> operazione, Map<String, Object> documento) {
        var nomi = new TreeSet<String>();
        if (operazione.get("parameters") instanceof List<?> parametri) {
            for (var parametro : parametri) {
                var risolto = risolvi(cast(parametro), documento);
                nomi.add(risolto.get("in") + ":" + risolto.get("name"));
            }
        }
        return nomi;
    }

    private static Set<String> tipiCorpo(Map<String, Object> operazione) {
        var corpo = operazione.get("requestBody");
        return corpo == null ? Set.of() : new TreeSet<>(mappa(cast(corpo), "content").keySet());
    }

    private static Map<String, Object> schemaMultipart(Map<String, Object> documento) {
        var post = mappa(mappa(mappa(documento, "paths"), "/api/v1/caricamenti"), "post");
        return mappa(mappa(mappa(mappa(post, "requestBody"), "content"), "multipart/form-data"), "schema");
    }

    private static Map<String, Object> risolvi(Map<String, Object> nodo, Map<String, Object> documento) {
        if (!(nodo.get("$ref") instanceof String riferimento)) {
            return nodo;
        }
        var corrente = documento;
        for (var parte : riferimento.substring("#/".length()).split("/")) {
            corrente = mappa(corrente, parte);
        }
        return corrente;
    }

    private static Map<String, Object> mappa(Map<String, Object> nodo, String chiave) {
        var valore = nodo.get(chiave);
        assertThat(valore).as("nodo %s", chiave).isInstanceOf(Map.class);
        return cast(valore);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object valore) {
        return (Map<String, Object>) valore;
    }
}
