package com.fcusumano.sam.segnalazioni.adapter.persistence;

import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.segnalazioni.Evidenza;
import com.fcusumano.sam.shared.Competenza;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Conversione delle evidenze da e verso il JSON della colonna {@code segnalazione.evidenze}: array di oggetti
 * {@code {descrizione, cedolinoId, competenza, valore}} con competenza
 * {@code {"tipo":"MENSILITA","anno":2024,"mese":1}} oppure
 * {@code {"tipo":"FINE_RAPPORTO","progressivo":1,"periodo":"2024-07"}}.
 */
final class EvidenzeJson {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String MENSILITA = "MENSILITA";
    private static final String FINE_RAPPORTO = "FINE_RAPPORTO";

    private EvidenzeJson() {
    }

    static String scrivi(List<Evidenza> evidenze) {
        var array = JSON.createArrayNode();
        for (var evidenza : evidenze) {
            var nodo = array.addObject();
            nodo.put("descrizione", evidenza.descrizione());
            if (evidenza.cedolinoId() != null) {
                nodo.put("cedolinoId", evidenza.cedolinoId().valore());
            } else {
                nodo.putNull("cedolinoId");
            }
            if (evidenza.competenza() != null) {
                scriviCompetenza(nodo.putObject("competenza"), evidenza.competenza());
            } else {
                nodo.putNull("competenza");
            }
            nodo.put("valore", evidenza.valore());
        }
        return JSON.writeValueAsString(array);
    }

    static List<Evidenza> leggi(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        var risultato = new ArrayList<Evidenza>();
        for (var nodo : JSON.readTree(json)) {
            var cedolino = nodo.path("cedolinoId");
            risultato.add(new Evidenza(
                    testo(nodo.path("descrizione")),
                    cedolino.isIntegralNumber() ? new CedolinoId(cedolino.longValue()) : null,
                    leggiCompetenza(nodo.path("competenza")),
                    testo(nodo.path("valore"))));
        }
        return risultato;
    }

    private static void scriviCompetenza(ObjectNode nodo, Competenza competenza) {
        switch (competenza) {
            case Competenza.Mensilita m -> {
                nodo.put("tipo", MENSILITA);
                nodo.put("anno", m.anno());
                nodo.put("mese", m.mese());
            }
            case Competenza.FineRapporto f -> {
                nodo.put("tipo", FINE_RAPPORTO);
                nodo.put("progressivo", f.progressivo());
                nodo.put("periodo", f.periodoRetribuzione().toString());
            }
        }
    }

    private static Competenza leggiCompetenza(JsonNode nodo) {
        if (!nodo.isObject()) {
            return null;
        }
        return switch (testo(nodo.path("tipo"))) {
            case MENSILITA -> new Competenza.Mensilita(nodo.path("anno").intValue(), nodo.path("mese").intValue());
            case FINE_RAPPORTO -> new Competenza.FineRapporto(nodo.path("progressivo").intValue(),
                    YearMonth.parse(testo(nodo.path("periodo"))));
            case null, default -> null;
        };
    }

    private static String testo(JsonNode nodo) {
        return nodo.isString() ? nodo.asString() : null;
    }
}
