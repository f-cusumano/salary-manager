package com.fcusumano.sam.cedolini.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fcusumano.sam.cedolini.CategoriaVoce;
import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.cedolini.application.estrazione.CedolinoEstratto.TipoCedolinoEstratto;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException.Motivo;
import com.fcusumano.sam.cedolini.application.estrazione.InterpreteRispostaEstrazione;
import com.fcusumano.sam.cedolini.application.estrazione.SchemaEstrazione;
import com.fcusumano.sam.shared.UnitaMisura;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Interpretazione del JSON di estrazione: parsing di una risposta canned, validazione rispetto allo schema,
 * normalizzazione, mascheramento dei dati bancari, controllo del numero di cedolini. Dati inventati.
 */
class InterpreteRispostaEstrazioneTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final InterpreteRispostaEstrazione interprete = InterpreteRispostaEstrazione.standard(CLOCK);

    private static String modificata(String fixture, Consumer<ObjectNode> modifica) throws IOException {
        var radice = (ObjectNode) MAPPER.readTree(ClaudeLettoreCedoliniTest.fixture(fixture));
        modifica.accept(radice);
        return MAPPER.writeValueAsString(radice);
    }

    @Test
    void mappaUnaRispostaCompletaNelRecord() throws IOException {
        var risultato = interprete.interpreta(ClaudeLettoreCedoliniTest.fixture("rossi-alfa-2023-09"), "claude-opus-5-5");
        var c = risultato.cedolino();

        assertThat(risultato.avvisi()).isEmpty();
        assertThat(c.numeroCedoliniNelDocumento()).isEqualTo(1);
        assertThat(c.dipendente().codiceFiscale()).isEqualTo("RSSMRA80A01H501U");
        assertThat(c.dipendente().dataNascita()).isEqualTo("1980-01-01");
        assertThat(c.datoreLavoro().partitaIva()).isEqualTo("01234567897");
        assertThat(c.datoreLavoro().posizioneInps()).isEqualTo("1122334455");
        assertThat(c.datiContrattuali().dataAssunzione()).isEqualTo("2021-09-01");
        assertThat(c.datiContrattuali().dataCessazione()).isNull();
        assertThat(c.datiContrattuali().ccnl()).isEqualTo("CCNL Metalmeccanica Industria");
        assertThat(c.periodo().anno()).isEqualTo(2023);
        assertThat(c.periodo().mese()).isEqualTo(9);
        assertThat(c.periodo().tipo()).isEqualTo(TipoCedolinoEstratto.ORDINARIA);
        assertThat(c.periodo().annoVariabili()).isEqualTo(2023);
        assertThat(c.periodo().meseVariabili()).isEqualTo(8);
        assertThat(c.retribuzione().minimo()).isEqualByComparingTo("2123.50");
        assertThat(c.retribuzione().superminimo()).isEqualByComparingTo("176.50");
        assertThat(c.retribuzione().superminimoAssorbibile()).isTrue();
        assertThat(c.retribuzione().importoScatti()).isEqualByComparingTo("25.00");
        assertThat(c.retribuzione().retribuzioneBase()).isNull();
        assertThat(c.scatti().numeroScatti()).isEqualTo(1);
        assertThat(c.scatti().dataProssimoScatto()).isEqualTo("2025-09-01");
        assertThat(c.voci()).extracting(v -> v.categoria())
                .contains(CategoriaVoce.COMPETENZA, CategoriaVoce.TRATTENUTA_PREVIDENZIALE, CategoriaVoce.TRATTENUTA_FISCALE,
                        CategoriaVoce.INFORMATIVA);
        assertThat(c.ratei()).extracting(r -> r.tipo() + "/" + r.unita()).containsExactly("FERIE/GIORNI", "ROL/ORE");
        assertThat(c.tfr().fondo31DicembreAnnoPrecedente()).isEqualByComparingTo("2410.35");
        assertThat(c.tfr().liquidatoLordo()).isNull();
        assertThat(c.buoniPasto().presenti()).isFalse();
        assertThat(c.contieneTredicesima()).isFalse();
        assertThat(c.confidenza().complessiva()).isEqualByComparingTo("0.95");
        assertThat(c.campiNonTrovati()).contains("datiContrattuali.codiceCnel");
        assertThat(risultato.modello()).isEqualTo("claude-opus-5-5");
    }

    @Test
    void leNoteDelModelloDiventanoAvvisi() throws IOException {
        var risultato = interprete.interpreta(ClaudeLettoreCedoliniTest.fixture("rossi-alfa-2023-13"), "m");

        assertThat(risultato.cedolino().periodo().tipo()).isEqualTo(TipoCedolinoEstratto.TREDICESIMA);
        assertThat(risultato.cedolino().periodo().mese()).isEqualTo(12);
        assertThat(risultato.avvisi()).singleElement().asString().startsWith("Nota estrazione: ").contains("TREDICESIMA");
    }

    @Test
    void normalizzaImportiDateEFormatiItaliani() throws IOException {
        var json = modificata("rossi-beta-2025-01", r -> {
            r.withObject("/dipendente").put("dataNascita", "01/01/1980").put("codiceFiscale", " rssmra80a01h501u ");
            r.withObject("/datoreLavoro").put("partitaIva", "IT 01234567897").put("ragioneSociale", "  Beta   Consulting S.p.A. ");
            r.withObject("/datiContrattuali").put("dataAssunzione", "16062024").put("ccnl", "Commercio terziario")
                    .put("codiceCnel", "h011");
            r.withObject("/periodo").put("meseVariabili", "12/2024");
            r.withObject("/retribuzione").put("retribuzioneBase", "2.600,00").put("superminimoAssorbibile", "NO");
            r.withObject("/scatti").put("dataProssimoScatto", "062026").put("numeroScatti", "2,00");
            r.withObject("/tfr").put("anticipi", "1.500,50-");
        });

        var c = interprete.interpreta(json, "m").cedolino();

        assertThat(c.dipendente().dataNascita()).isEqualTo("1980-01-01");
        assertThat(c.dipendente().codiceFiscale()).isEqualTo("RSSMRA80A01H501U");
        assertThat(c.datoreLavoro().partitaIva()).isEqualTo("01234567897");
        assertThat(c.datoreLavoro().ragioneSociale()).isEqualTo("Beta Consulting S.p.A.");
        assertThat(c.datiContrattuali().dataAssunzione()).isEqualTo("2024-06-16");
        assertThat(c.datiContrattuali().ccnl()).isEqualTo("Commercio terziario (CNEL H011)");
        assertThat(c.periodo().annoVariabili()).isEqualTo(2024);
        assertThat(c.periodo().meseVariabili()).isEqualTo(12);
        assertThat(c.retribuzione().retribuzioneBase()).isEqualByComparingTo("2600.00");
        assertThat(c.retribuzione().superminimoAssorbibile()).isFalse();
        assertThat(c.scatti().dataProssimoScatto()).isEqualTo("2026-06-01");
        assertThat(c.scatti().numeroScatti()).isEqualTo(2);
        assertThat(c.tfr().anticipi()).isEqualByComparingTo("-1500.50");
    }

    @Test
    void iValoriNonInterpretabiliDiventanoNullConAvviso() throws IOException {
        var json = modificata("rossi-beta-2025-01", r -> {
            r.withObject("/totali").put("lordo", "circa duemila");
            r.withObject("/datiContrattuali").put("dataAssunzione", "31/02/2024");
        });

        var risultato = interprete.interpreta(json, "m");

        assertThat(risultato.cedolino().totali().lordo()).isNull();
        assertThat(risultato.cedolino().datiContrattuali().dataAssunzione()).isNull();
        assertThat(risultato.avvisi()).contains("Valore non interpretabile ignorato: totali.lordo",
                "Valore non interpretabile ignorato: datiContrattuali.dataAssunzione");
        assertThat(risultato.avvisi()).noneMatch(a -> a.contains("duemila"));
    }

    @Test
    void sezioniVuoteECampiVuotiDiventanoNull() throws IOException {
        var c = interprete.interpreta(ClaudeLettoreCedoliniTest.fixture("rossi-beta-2024-06"), "m").cedolino();

        assertThat(c.tfr()).as("nessun fondo al 31/12 ma quote presenti").isNotNull();
        assertThat(c.tfr().fondo31DicembreAnnoPrecedente()).isNull();
        assertThat(c.datoreLavoro().codiceFiscale()).isNull();
        assertThat(c.datoreLavoro().partitaIva()).isNull();
        assertThat(c.datiContrattuali().ccnl()).isNull();
        assertThat(c.scatti().numeroScatti()).isNull();
        assertThat(c.buoniPasto().presenti()).isTrue();
        assertThat(c.buoniPasto().numero()).isEqualByComparingTo("10");
        assertThat(c.ratei()).extracting(r -> r.tipo()).containsExactly(TipoRateo.FERIE, TipoRateo.ROL, TipoRateo.EX_FESTIVITA);
        assertThat(c.ratei().get(2).unita()).isEqualTo(UnitaMisura.ORE);

        var senzaTfr = modificata("rossi-alfa-2024-03-aggiuntivo", r -> { });
        assertThat(interprete.interpreta(senzaTfr, "m").cedolino().tfr()).isNull();
    }

    @Test
    void iDatiBancariVengonoMascheratiAncheNelJsonDiAudit() throws IOException {
        var json = modificata("rossi-beta-2025-01", r -> {
            r.withObject("/datoreLavoro").put("indirizzo", "Corso Italia 10 - IBAN IT60 X054 2811 1010 0000 0123 456");
            r.withArray("/voci").addObject()
                    .put("codice", "").put("descrizione", "Accredito su IT60X0542811101000000123456").put("categoria", "INFORMATIVA")
                    .put("quantita", "").put("unita", "").put("base", "").put("importoCompetenza", "").put("importoTrattenuta", "");
        });

        var risultato = interprete.interpreta(json, "m");

        assertThat(risultato.jsonOriginale()).doesNotContain("IT60", "X054", "2811").contains("[dato bancario rimosso]");
        assertThat(risultato.cedolino().datoreLavoro().indirizzo()).isEqualTo("Corso Italia 10 - IBAN [dato bancario rimosso]");
        assertThat(risultato.cedolino().voci().getLast().descrizione()).isEqualTo("Accredito su [dato bancario rimosso]");
        assertThat(risultato.avvisi()).anyMatch(a -> a.contains("dati bancari"));
    }

    @Test
    void ancheGliIbanEsteriVengonoMascheratiNelleVociNegliAvvisiENelJsonDiAudit() throws IOException {
        var estero = "LT" + "71" + "7300010123456789";
        var simileMaErrato = "LT" + "72" + "7300010123456789";
        var json = modificata("rossi-beta-2025-01", r -> {
            r.withArray("/voci").addObject()
                    .put("codice", "").put("descrizione", "Accredito su " + estero).put("categoria", "INFORMATIVA")
                    .put("quantita", "").put("unita", "").put("base", "").put("importoCompetenza", "").put("importoTrattenuta", "");
            r.withArray("/avvisi").add("Stampato il conto " + estero);
            r.withObject("/datoreLavoro").put("codiceAzienda", simileMaErrato);
        });

        var risultato = interprete.interpreta(json, "m");

        assertThat(risultato.jsonOriginale()).doesNotContain(estero).contains(simileMaErrato);
        assertThat(risultato.cedolino().voci().getLast().descrizione()).isEqualTo("Accredito su [dato bancario rimosso]");
        assertThat(risultato.cedolino().datoreLavoro().codiceAzienda()).isEqualTo(simileMaErrato);
        assertThat(risultato.avvisi()).anyMatch(a -> a.contains("dati bancari"))
                .contains("Nota estrazione: Stampato il conto [dato bancario rimosso]")
                .noneMatch(a -> a.contains(estero));
    }

    @Test
    void unOutputNonConformeAlloSchemaVieneScartato() throws IOException {
        var mancante = modificata("rossi-beta-2025-01", r -> r.withObject("/totali").remove("netto"));
        var inPiu = modificata("rossi-beta-2025-01", r -> r.withObject("/dipendente").put("iban", "x"));
        var tipoErrato = modificata("rossi-beta-2025-01", r -> r.withObject("/periodo").put("anno", "2025"));
        var enumErrato = modificata("rossi-beta-2025-01", r -> r.withObject("/periodo").put("tipo", "CONGUAGLIO"));
        var voceErrata = modificata("rossi-beta-2025-01", r -> r.withArray("/voci").addObject().put("descrizione", "x"));

        for (var json : new String[] {mancante, inPiu, tipoErrato, enumErrato, voceErrata, "[]", "{\"a\":"}) {
            assertThatThrownBy(() -> interprete.interpreta(json, "m"))
                    .isInstanceOfSatisfying(EstrazioneCedolinoException.class,
                            e -> assertThat(e.motivo()).isEqualTo(Motivo.OUTPUT_NON_VALIDO));
        }
    }

    @Test
    void piuCedoliniONessunCedolinoRendonoIlDocumentoNonUtilizzabile() throws IOException {
        var due = modificata("rossi-beta-2025-01", r -> r.put("numeroCedoliniNelDocumento", 2));
        var nessuno = modificata("rossi-beta-2025-01", r -> r.put("numeroCedoliniNelDocumento", 0));

        assertThatThrownBy(() -> interprete.interpreta(due, "m"))
                .isInstanceOfSatisfying(EstrazioneCedolinoException.class, e -> {
                    assertThat(e.motivo()).isEqualTo(Motivo.PIU_CEDOLINI);
                    assertThat(e.getMessage()).contains("2 cedolini");
                });
        assertThatThrownBy(() -> interprete.interpreta(nessuno, "m"))
                .isInstanceOfSatisfying(EstrazioneCedolinoException.class,
                        e -> assertThat(e.motivo()).isEqualTo(Motivo.DOCUMENTO_NON_VALIDO));
    }

    @Test
    void loSchemaEspansoNonHaRiferimentiNeCampiOpzionaliNeUnioni() {
        var schema = SchemaEstrazione.carica().schema();
        var testo = MAPPER.writeValueAsString(schema);

        assertThat(testo).doesNotContain("$ref", "$defs", "anyOf", "oneOf", "\"null\"", "minimum", "maxLength");
        assertThat(schema.get("additionalProperties")).isEqualTo(false);
        // ogni proprieta' di ogni oggetto e' richiesta: nessun parametro opzionale (limite API: 24)
        verificaTuttiRichiesti(MAPPER.valueToTree(schema));
    }

    private static void verificaTuttiRichiesti(tools.jackson.databind.JsonNode nodo) {
        if (nodo.isObject()) {
            if (nodo.has("properties")) {
                var richiesti = new java.util.HashSet<String>();
                nodo.get("required").forEach(r -> richiesti.add(r.asString()));
                assertThat(richiesti).containsExactlyInAnyOrderElementsOf(nodo.get("properties").propertyNames());
                assertThat(nodo.get("additionalProperties").asBoolean()).isFalse();
            }
            nodo.forEach(InterpreteRispostaEstrazioneTest::verificaTuttiRichiesti);
        } else if (nodo.isArray()) {
            nodo.forEach(InterpreteRispostaEstrazioneTest::verificaTuttiRichiesti);
        }
    }

    @Test
    void laConfidenzaVieneLimitataTraZeroEUno() throws IOException {
        var json = modificata("rossi-beta-2025-01", r -> r.withObject("/confidenza").put("complessiva", 1.7));

        assertThat(interprete.interpreta(json, "m").cedolino().confidenza().complessiva()).isEqualByComparingTo(BigDecimal.ONE);
    }
}
