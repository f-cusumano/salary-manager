package com.fcusumano.sam.cedolini.application.estrazione;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Schema JSON dell'output di estrazione (structured outputs) e relativa validazione.
 * <p>
 * Il sorgente versionato e' {@code prompts/cedolino-schema-v1.json}: usa {@code $defs}/{@code $ref} locali per
 * leggibilita', ma la versione inviata all'API e usata per la validazione e' espansa (nessun {@code $ref}). Lo schema
 * non usa campi opzionali ne' tipi unione (l'API ne limita il numero a 24 e 16 per richiesta): i valori assenti sono
 * stringhe vuote, gli interi assenti valgono 0 e gli oggetti sono sempre presenti.
 * <p>
 * La validazione copre il sottoinsieme di JSON Schema usato dallo schema: {@code type}, {@code properties},
 * {@code required}, {@code additionalProperties: false}, {@code items}, {@code enum}.
 */
public final class SchemaEstrazione {

    /** Versione dello schema e del prompt di estrazione. */
    public static final String VERSIONE = "v1";

    static final String RISORSA = "/prompts/cedolino-schema-" + VERSIONE + ".json";
    private static final String DEFS = "$defs";
    private static final String REF = "$ref";
    private static final String PREFISSO_REF = "#/" + DEFS + "/";
    private static final int MAX_ERRORI = 20;

    private final Map<String, Object> schema;

    private SchemaEstrazione(Map<String, Object> schema) {
        this.schema = schema;
    }

    /** Carica lo schema versionato dal classpath. */
    public static SchemaEstrazione carica() {
        try (InputStream in = SchemaEstrazione.class.getResourceAsStream(RISORSA)) {
            if (in == null) {
                throw new IllegalStateException("Schema di estrazione non trovato: " + RISORSA);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> sorgente = JsonMapper.builder().build().readValue(in, Map.class);
            return new SchemaEstrazione(espandi(sorgente));
        } catch (IOException e) {
            throw new UncheckedIOException("Schema di estrazione non leggibile: " + RISORSA, e);
        }
    }

    /** Schema espanso (mappe e liste immutabili), pronto per {@code output_config.format}. */
    public Map<String, Object> schema() {
        return schema;
    }

    /**
     * Valida un documento JSON rispetto allo schema.
     *
     * @return errori trovati (percorso e problema, mai i valori), vuota se conforme
     */
    public List<String> valida(JsonNode documento) {
        var errori = new ArrayList<String>();
        valida(documento, schema, "$", errori);
        return errori.size() > MAX_ERRORI ? List.copyOf(errori.subList(0, MAX_ERRORI)) : List.copyOf(errori);
    }

    @SuppressWarnings("unchecked")
    private static void valida(JsonNode nodo, Map<String, Object> schema, String percorso, List<String> errori) {
        if (errori.size() > MAX_ERRORI) {
            return;
        }
        var tipo = (String) schema.get("type");
        if (tipo != null && !tipoCorretto(nodo, tipo)) {
            errori.add(percorso + ": atteso " + tipo);
            return;
        }
        if (schema.get("enum") instanceof List<?> ammessi
                && (!nodo.isString() || !ammessi.contains(nodo.asString()))) {
            errori.add(percorso + ": valore non ammesso");
        }
        if (nodo.isObject() && schema.get("properties") instanceof Map<?, ?> proprieta) {
            var richieste = schema.get("required") instanceof List<?> r ? r : List.of();
            for (var campo : richieste) {
                if (!nodo.has((String) campo)) {
                    errori.add(percorso + "." + campo + ": mancante");
                }
            }
            for (var campo : nodo.propertyNames()) {
                var schemaCampo = (Map<String, Object>) proprieta.get(campo);
                if (schemaCampo == null) {
                    if (Boolean.FALSE.equals(schema.get("additionalProperties"))) {
                        errori.add(percorso + "." + campo + ": non previsto");
                    }
                } else {
                    valida(nodo.get(campo), schemaCampo, percorso + "." + campo, errori);
                }
            }
        }
        if (nodo.isArray() && schema.get("items") instanceof Map<?, ?> elementi) {
            int i = 0;
            for (var elemento : nodo) {
                valida(elemento, (Map<String, Object>) elementi, percorso + "[" + i++ + "]", errori);
            }
        }
    }

    private static boolean tipoCorretto(JsonNode nodo, String tipo) {
        return switch (tipo) {
            case "object" -> nodo.isObject();
            case "array" -> nodo.isArray();
            case "string" -> nodo.isString();
            case "integer" -> nodo.isIntegralNumber();
            case "number" -> nodo.isNumber();
            case "boolean" -> nodo.isBoolean();
            case "null" -> nodo.isNull();
            default -> throw new IllegalStateException("Tipo JSON Schema non gestito: " + tipo);
        };
    }

    /** Sostituisce i riferimenti locali {@code #/$defs/x} con la definizione (le proprieta' accanto al ref prevalgono). */
    @SuppressWarnings("unchecked")
    static Map<String, Object> espandi(Map<String, Object> sorgente) {
        var definizioni = (Map<String, Object>) sorgente.getOrDefault(DEFS, Map.of());
        var radice = new LinkedHashMap<>(sorgente);
        radice.remove(DEFS);
        return (Map<String, Object>) espandi(radice, definizioni, new HashSet<>());
    }

    @SuppressWarnings("unchecked")
    private static Object espandi(Object valore, Map<String, Object> definizioni, Set<String> inCorso) {
        if (valore instanceof Map<?, ?> mappa) {
            var risultato = new LinkedHashMap<String, Object>();
            if (mappa.get(REF) instanceof String ref) {
                if (!ref.startsWith(PREFISSO_REF)) {
                    throw new IllegalStateException("Riferimento non supportato nello schema: " + ref);
                }
                var nome = ref.substring(PREFISSO_REF.length());
                var definizione = definizioni.get(nome);
                if (definizione == null || !inCorso.add(nome)) {
                    throw new IllegalStateException("Definizione assente o ricorsiva nello schema: " + nome);
                }
                risultato.putAll((Map<String, Object>) espandi(definizione, definizioni, inCorso));
                inCorso.remove(nome);
            }
            mappa.forEach((chiave, v) -> {
                if (!REF.equals(chiave)) {
                    risultato.put((String) chiave, espandi(v, definizioni, inCorso));
                }
            });
            return Collections.unmodifiableMap(risultato);
        }
        if (valore instanceof List<?> lista) {
            return lista.stream().map(v -> espandi(v, definizioni, inCorso)).toList();
        }
        return valore;
    }
}
