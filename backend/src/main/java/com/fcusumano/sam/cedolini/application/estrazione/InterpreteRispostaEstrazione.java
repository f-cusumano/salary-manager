package com.fcusumano.sam.cedolini.application.estrazione;

import com.fcusumano.sam.cedolini.CategoriaVoce;
import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.cedolini.application.estrazione.CedolinoEstratto.TipoCedolinoEstratto;
import com.fcusumano.sam.cedolini.application.estrazione.LettoreCedolini.RisultatoEstrazione;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException.Motivo;
import com.fcusumano.sam.shared.UnitaMisura;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Interpreta il JSON prodotto dall'estrazione (modello o fixture) e lo trasforma in {@link CedolinoEstratto}:
 * <ol>
 *   <li>parsing e mascheramento di eventuali dati bancari (IBAN di qualunque paese) sfuggiti al prompt, anche nel JSON
 *       di audit;</li>
 *   <li>validazione rispetto allo schema ({@link SchemaEstrazione}): se non conforme l'output e' scartato;</li>
 *   <li>normalizzazione (stringhe vuote -> null, importi {@link BigDecimal}, date ISO);</li>
 *   <li>controllo del numero di cedolini nel documento (0 o piu' di 1 -> documento non valido);</li>
 *   <li>controlli semantici ({@link ValidatoreCedolinoEstratto}): se falliscono la confidenza scende e si aggiunge un
 *       avviso, senza scartare il cedolino.</li>
 * </ol>
 * Thread-safe. Non logga valori estratti.
 */
public final class InterpreteRispostaEstrazione {

    private static final Logger log = LoggerFactory.getLogger(InterpreteRispostaEstrazione.class);

    private static final String PREFISSO_NOTA_MODELLO = "Nota estrazione: ";

    private final SchemaEstrazione schema;
    private final ValidatoreCedolinoEstratto validatore;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public InterpreteRispostaEstrazione(SchemaEstrazione schema, ValidatoreCedolinoEstratto validatore) {
        this.schema = schema;
        this.validatore = validatore;
    }

    /** Interprete con lo schema versionato e il validatore semantico sull'orologio indicato. */
    public static InterpreteRispostaEstrazione standard(Clock clock) {
        return new InterpreteRispostaEstrazione(SchemaEstrazione.carica(), new ValidatoreCedolinoEstratto(clock));
    }

    /**
     * @param json    testo JSON restituito dal lettore dei cedolini
     * @param modello identificativo del lettore (modello Claude, "lettore-locale-1/<layout>", "fixture")
     * @throws EstrazioneCedolinoException OUTPUT_NON_VALIDO se il JSON non e' valido o non conforme allo schema,
     *                                     DOCUMENTO_NON_VALIDO se il documento non e' un cedolino o ne contiene piu' di uno
     */
    public RisultatoEstrazione interpreta(String json, String modello) {
        JsonNode radice;
        try {
            radice = mapper.readTree(json);
        } catch (JacksonException e) {
            throw new EstrazioneCedolinoException(Motivo.OUTPUT_NON_VALIDO,
                    "La risposta dell'estrazione non è un JSON valido", e);
        }
        if (radice == null || !radice.isObject()) {
            throw new EstrazioneCedolinoException(Motivo.OUTPUT_NON_VALIDO,
                    "La risposta dell'estrazione non è un oggetto JSON");
        }
        var avvisi = new ArrayList<String>();
        if (mascheraDatiBancari(radice) > 0) {
            avvisi.add("Rimossi dati bancari presenti nell'estrazione: non vengono salvati");
        }
        var errori = schema.valida(radice);
        if (!errori.isEmpty()) {
            log.warn("Output di estrazione non conforme allo schema {}: {}", SchemaEstrazione.VERSIONE, errori);
            throw new EstrazioneCedolinoException(Motivo.OUTPUT_NON_VALIDO,
                    "La risposta dell'estrazione non è conforme allo schema (" + errori.size() + " errori)");
        }

        var lettore = new Lettore(avvisi);
        var cedolino = lettore.cedolino(radice);
        verificaNumeroCedolini(cedolino.numeroCedoliniNelDocumento());

        var verifica = validatore.verifica(cedolino);
        var tutti = new ArrayList<>(avvisi);
        tutti.addAll(verifica.avvisi());
        Normalizzazione.testi(lettore.testi(radice.get("avvisi"))).forEach(n -> tutti.add(PREFISSO_NOTA_MODELLO + n));
        return new RisultatoEstrazione(verifica.cedolino(), mapper.writeValueAsString(radice), modello, tutti);
    }

    private static void verificaNumeroCedolini(Integer numero) {
        if (numero == null || numero == 0) {
            throw new EstrazioneCedolinoException(Motivo.DOCUMENTO_NON_VALIDO,
                    "Il documento non sembra un cedolino paga");
        }
        if (numero > 1) {
            throw new EstrazioneCedolinoException(Motivo.DOCUMENTO_NON_VALIDO,
                    "Il PDF contiene " + numero + " cedolini distinti: carica un file per ogni cedolino");
        }
    }

    /**
     * Sostituisce gli IBAN di qualunque paese ({@link DatiBancari}) in tutti i testi del documento; restituisce il
     * numero di testi modificati.
     */
    private static int mascheraDatiBancari(JsonNode nodo) {
        int sostituzioni = 0;
        if (nodo instanceof ObjectNode oggetto) {
            for (var campo : List.copyOf(oggetto.propertyNames())) {
                var figlio = oggetto.get(campo);
                var mascherato = figlio.isString() ? DatiBancari.maschera(figlio.asString()) : null;
                if (mascherato != null && !mascherato.equals(figlio.asString())) {
                    oggetto.put(campo, mascherato);
                    sostituzioni++;
                } else {
                    sostituzioni += mascheraDatiBancari(figlio);
                }
            }
        } else if (nodo instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                var figlio = array.get(i);
                var mascherato = figlio.isString() ? DatiBancari.maschera(figlio.asString()) : null;
                if (mascherato != null && !mascherato.equals(figlio.asString())) {
                    array.set(i, mascherato);
                    sostituzioni++;
                } else {
                    sostituzioni += mascheraDatiBancari(figlio);
                }
            }
        }
        return sostituzioni;
    }

    /** Mappatura JSON (gia' conforme allo schema) -> record, con gli avvisi sui valori non interpretabili. */
    private static final class Lettore {

        private final List<String> avvisi;

        Lettore(List<String> avvisi) {
            this.avvisi = avvisi;
        }

        CedolinoEstratto cedolino(JsonNode r) {
            var periodo = r.get("periodo");
            var meseVariabili = valore(periodo, "meseVariabili", "periodo.meseVariabili", Normalizzazione::periodo);
            return new CedolinoEstratto(
                    r.get("numeroCedoliniNelDocumento").asInt(),
                    dipendente(r.get("dipendente")),
                    datoreLavoro(r.get("datoreLavoro")),
                    datiContrattuali(r.get("datiContrattuali")),
                    new CedolinoEstratto.Periodo(
                            interoPositivo(periodo.get("anno")),
                            interoPositivo(periodo.get("mese")),
                            TipoCedolinoEstratto.valueOf(periodo.get("tipo").asString()),
                            data(periodo, "dataElaborazione", "periodo.dataElaborazione"),
                            meseVariabili.map(m -> m.getYear()).orElse(null),
                            meseVariabili.map(m -> m.getMonthValue()).orElse(null)),
                    totali(r.get("totali")),
                    retribuzione(r.get("retribuzione")),
                    voci(r.get("voci")),
                    ratei(r.get("ratei")),
                    tfr(r.get("tfr")),
                    presenze(r.get("presenze")),
                    scatti(r.get("scatti")),
                    buoniPasto(r.get("buoniPasto")),
                    progressivi(r.get("progressivi")),
                    r.get("contieneTredicesima").asBoolean(),
                    r.get("contieneQuattordicesima").asBoolean(),
                    confidenza(r.get("confidenza")),
                    Normalizzazione.testi(testi(r.get("campiNonTrovati"))));
        }

        private CedolinoEstratto.Dipendente dipendente(JsonNode n) {
            return new CedolinoEstratto.Dipendente(
                    codice(n, "codiceFiscale"),
                    testo(n, "nome"),
                    testo(n, "cognome"),
                    data(n, "dataNascita", "dipendente.dataNascita"),
                    testo(n, "codiceDipendente"));
        }

        private CedolinoEstratto.DatoreLavoro datoreLavoro(JsonNode n) {
            return new CedolinoEstratto.DatoreLavoro(
                    codice(n, "codiceFiscale"),
                    Normalizzazione.partitaIva(n.get("partitaIva").asString()).orElse(null),
                    codice(n, "posizioneInps"),
                    testo(n, "ragioneSociale"),
                    testo(n, "indirizzo"),
                    testo(n, "codiceAzienda"));
        }

        private CedolinoEstratto.DatiContrattuali datiContrattuali(JsonNode n) {
            return new CedolinoEstratto.DatiContrattuali(
                    data(n, "dataAssunzione", "datiContrattuali.dataAssunzione"),
                    data(n, "dataCessazione", "datiContrattuali.dataCessazione"),
                    testo(n, "qualifica"),
                    testo(n, "livello"),
                    ccnl(testo(n, "ccnl"), codice(n, "codiceCnel")));
        }

        /** Testo del CCNL con il codice CNEL (se stampato e non gia' compreso nel testo): aiuta il riconoscimento. */
        private static String ccnl(String testo, String codiceCnel) {
            if (codiceCnel == null) {
                return testo;
            }
            var cnel = "CNEL " + codiceCnel;
            if (testo == null) {
                return cnel;
            }
            return testo.toUpperCase(Locale.ROOT).contains(codiceCnel) ? testo : testo + " (" + cnel + ")";
        }

        private CedolinoEstratto.Totali totali(JsonNode n) {
            return new CedolinoEstratto.Totali(
                    importo(n, "lordo", "totali.lordo"),
                    importo(n, "totaleCompetenze", "totali.totaleCompetenze"),
                    importo(n, "totaleTrattenute", "totali.totaleTrattenute"),
                    importo(n, "arrotondamento", "totali.arrotondamento"),
                    importo(n, "netto", "totali.netto"));
        }

        private CedolinoEstratto.Retribuzione retribuzione(JsonNode n) {
            return new CedolinoEstratto.Retribuzione(
                    importo(n, "minimo", "retribuzione.minimo"),
                    importo(n, "contingenza", "retribuzione.contingenza"),
                    importo(n, "superminimo", "retribuzione.superminimo"),
                    Normalizzazione.siNo(n.get("superminimoAssorbibile").asString()).orElse(null),
                    importo(n, "importoScatti", "retribuzione.importoScatti"),
                    importo(n, "altriElementiFissi", "retribuzione.altriElementiFissi"),
                    importo(n, "retribuzioneBase", "retribuzione.retribuzioneBase"),
                    importo(n, "totale", "retribuzione.totale"),
                    importo(n, "pagaOraria", "retribuzione.pagaOraria"),
                    importo(n, "pagaGiornaliera", "retribuzione.pagaGiornaliera"),
                    importo(n, "divisoreOrario", "retribuzione.divisoreOrario"),
                    importo(n, "divisoreGiornaliero", "retribuzione.divisoreGiornaliero"));
        }

        private List<CedolinoEstratto.VoceEstratta> voci(JsonNode array) {
            var voci = new ArrayList<CedolinoEstratto.VoceEstratta>();
            int i = 0;
            for (var n : array) {
                var p = "voci[" + i++ + "].";
                var descrizione = testo(n, "descrizione");
                if (descrizione == null) {
                    avvisi.add("Ignorata una voce senza descrizione (" + p.substring(0, p.length() - 1) + ")");
                    continue;
                }
                voci.add(new CedolinoEstratto.VoceEstratta(
                        testo(n, "codice"),
                        descrizione,
                        CategoriaVoce.valueOf(n.get("categoria").asString()),
                        importo(n, "quantita", p + "quantita"),
                        Normalizzazione.testo(n.get("unita").asString())
                                .map(u -> u.toLowerCase(Locale.ROOT)).orElse(null),
                        importo(n, "base", p + "base"),
                        importo(n, "importoCompetenza", p + "importoCompetenza"),
                        importo(n, "importoTrattenuta", p + "importoTrattenuta")));
            }
            return List.copyOf(voci);
        }

        private List<CedolinoEstratto.RateoEstratto> ratei(JsonNode array) {
            var ratei = new ArrayList<CedolinoEstratto.RateoEstratto>();
            int i = 0;
            for (var n : array) {
                var p = "ratei[" + i++ + "].";
                var rateo = new CedolinoEstratto.RateoEstratto(
                        TipoRateo.valueOf(n.get("tipo").asString()),
                        UnitaMisura.valueOf(n.get("unita").asString()),
                        importo(n, "residuoAnnoPrecedente", p + "residuoAnnoPrecedente"),
                        importo(n, "maturato", p + "maturato"),
                        importo(n, "goduto", p + "goduto"),
                        importo(n, "saldo", p + "saldo"));
                boolean doppione = ratei.stream().anyMatch(r -> r.tipo() == rateo.tipo() && r.unita() == rateo.unita());
                if (doppione) {
                    avvisi.add("Ignorato un rateo ripetuto con stesso tipo e unità (" + p.substring(0, p.length() - 1) + ")");
                } else if (Stream.of(rateo.residuoAnnoPrecedente(), rateo.maturato(), rateo.goduto(), rateo.saldo())
                        .anyMatch(Objects::nonNull)) {
                    ratei.add(rateo);
                }
            }
            return List.copyOf(ratei);
        }

        /** Null se il cedolino non riporta alcun dato TFR. */
        private CedolinoEstratto.Tfr tfr(JsonNode n) {
            var tfr = new CedolinoEstratto.Tfr(
                    importo(n, "fondo31DicembreAnnoPrecedente", "tfr.fondo31DicembreAnnoPrecedente"),
                    importo(n, "rivalutazione", "tfr.rivalutazione"),
                    importo(n, "impostaSostitutiva", "tfr.impostaSostitutiva"),
                    importo(n, "quotaAnno", "tfr.quotaAnno"),
                    importo(n, "quotaMese", "tfr.quotaMese"),
                    importo(n, "quotaFondi", "tfr.quotaFondi"),
                    importo(n, "quotaBusta", "tfr.quotaBusta"),
                    importo(n, "anticipi", "tfr.anticipi"),
                    importo(n, "retribuzioneUtile", "tfr.retribuzioneUtile"),
                    importo(n, "liquidatoLordo", "tfr.liquidatoLordo"),
                    importo(n, "ritenuta", "tfr.ritenuta"));
            boolean vuoto = Stream.of(tfr.fondo31DicembreAnnoPrecedente(), tfr.rivalutazione(), tfr.impostaSostitutiva(),
                            tfr.quotaAnno(), tfr.quotaMese(), tfr.quotaFondi(), tfr.quotaBusta(), tfr.anticipi(),
                            tfr.retribuzioneUtile(), tfr.liquidatoLordo(), tfr.ritenuta())
                    .allMatch(Objects::isNull);
            return vuoto ? null : tfr;
        }

        private CedolinoEstratto.Presenze presenze(JsonNode n) {
            return new CedolinoEstratto.Presenze(
                    importo(n, "giorniLavorati", "presenze.giorniLavorati"),
                    importo(n, "oreLavorate", "presenze.oreLavorate"),
                    importo(n, "giorniRetribuiti", "presenze.giorniRetribuiti"));
        }

        private CedolinoEstratto.Scatti scatti(JsonNode n) {
            return new CedolinoEstratto.Scatti(
                    valore(n, "numeroScatti", "scatti.numeroScatti", Normalizzazione::intero).orElse(null),
                    data(n, "dataProssimoScatto", "scatti.dataProssimoScatto"));
        }

        private CedolinoEstratto.BuoniPasto buoniPasto(JsonNode n) {
            return new CedolinoEstratto.BuoniPasto(
                    n.get("presenti").asBoolean(),
                    importo(n, "numero", "buoniPasto.numero"),
                    importo(n, "valoreUnitario", "buoniPasto.valoreUnitario"),
                    importo(n, "importo", "buoniPasto.importo"));
        }

        private CedolinoEstratto.Progressivi progressivi(JsonNode n) {
            return new CedolinoEstratto.Progressivi(
                    importo(n, "imponibilePrevidenziale", "progressivi.imponibilePrevidenziale"),
                    importo(n, "imponibileIrpef", "progressivi.imponibileIrpef"),
                    importo(n, "irpefTrattenuta", "progressivi.irpefTrattenuta"));
        }

        private CedolinoEstratto.Confidenza confidenza(JsonNode n) {
            var campi = new ArrayList<CedolinoEstratto.ConfidenzaCampo>();
            for (var c : n.get("campi")) {
                Normalizzazione.testo(c.get("campo").asString()).ifPresent(campo ->
                        campi.add(new CedolinoEstratto.ConfidenzaCampo(campo, limitata(c.get("valore").decimalValue()))));
            }
            return new CedolinoEstratto.Confidenza(limitata(n.get("complessiva").decimalValue()), List.copyOf(campi));
        }

        private static BigDecimal limitata(BigDecimal valore) {
            return valore.max(BigDecimal.ZERO).min(BigDecimal.ONE);
        }

        List<String> testi(JsonNode array) {
            var testi = new ArrayList<String>();
            if (array != null) {
                array.forEach(t -> testi.add(t.asString()));
            }
            return testi;
        }

        private static Integer interoPositivo(JsonNode n) {
            int valore = n.asInt();
            return valore > 0 ? valore : null;
        }

        private static String testo(JsonNode n, String campo) {
            return Normalizzazione.testo(n.get(campo).asString()).orElse(null);
        }

        private static String codice(JsonNode n, String campo) {
            return Normalizzazione.codice(n.get(campo).asString()).orElse(null);
        }

        private BigDecimal importo(JsonNode n, String campo, String percorso) {
            return valore(n, campo, percorso, Normalizzazione::importo).orElse(null);
        }

        private String data(JsonNode n, String campo, String percorso) {
            return valore(n, campo, percorso, Normalizzazione::data).map(Object::toString).orElse(null);
        }

        /** Applica la normalizzazione; un valore non interpretabile diventa null con un avviso (senza il valore). */
        private <T> Optional<T> valore(JsonNode n, String campo, String percorso, Function<String, Optional<T>> conversione) {
            try {
                return conversione.apply(n.get(campo).asString());
            } catch (NumberFormatException | ArithmeticException | DateTimeException e) {
                avvisi.add("Valore non interpretabile ignorato: " + percorso);
                return Optional.empty();
            }
        }
    }
}
