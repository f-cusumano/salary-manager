package com.fcusumano.sam.cedolini.adapter.locale;

import com.fcusumano.sam.cedolini.CategoriaVoce;
import com.fcusumano.sam.cedolini.TipoRateo;
import com.fcusumano.sam.cedolini.application.estrazione.CedolinoEstratto.TipoCedolinoEstratto;
import com.fcusumano.sam.shared.UnitaMisura;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Dati letti da un riconoscitore di layout, prima della validazione: diventano il JSON dello schema
 * {@code prompts/cedolino-schema-v1.json} (lo stesso prodotto dal lettore Claude), che passa poi da
 * {@code InterpreteRispostaEstrazione}. Un campo null e' un dato non stampato o non trovato: mai valori dedotti.
 * <p>
 * Tiene anche il conto delle etichette attese dal layout ma non trovate e dei controlli interni non superati, per una
 * confidenza onesta ({@link #confidenza()}).
 */
final class CedolinoLetto {

    /** Campi chiave: se mancano finiscono in {@code campiNonTrovati}; i primi undici sono quelli richiesti a Claude. */
    static final List<String> CAMPI_CHIAVE = List.of(
            "dipendente.codiceFiscale", "datoreLavoro.ragioneSociale", "periodo.anno", "periodo.mese", "periodo.tipo",
            "totali.netto", "totali.totaleCompetenze", "retribuzione.totale", "tfr.fondo31DicembreAnnoPrecedente",
            "datiContrattuali.dataAssunzione", "datiContrattuali.ccnl",
            "dipendente.cognome", "dipendente.nome", "dipendente.dataNascita", "datoreLavoro.codiceFiscale",
            "datoreLavoro.partitaIva", "datoreLavoro.posizioneInps", "datiContrattuali.qualifica",
            "datiContrattuali.livello", "totali.totaleTrattenute", "progressivi.imponibileIrpef");

    static final BigDecimal CONFIDENZA_BASE = new BigDecimal("0.95");
    /** Fattore per ogni etichetta attesa dal layout e non trovata (il documento si discosta dal layout noto). */
    static final BigDecimal FATTORE_ETICHETTA_MANCANTE = new BigDecimal("0.85");
    /** Fattore per ogni controllo interno non superato (es. somma delle voci diversa dai totali). */
    static final BigDecimal FATTORE_CONTROLLO = new BigDecimal("0.8");
    /** Fattore per ogni campo chiave non trovato (spesso non e' stampato: penalita' lieve). */
    static final BigDecimal FATTORE_CAMPO_MANCANTE = new BigDecimal("0.98");

    // dipendente
    String codiceFiscale;
    String nome;
    String cognome;
    LocalDate dataNascita;
    String codiceDipendente;
    // datore di lavoro
    String codiceFiscaleAzienda;
    String partitaIva;
    String posizioneInps;
    String posizioneInail;
    String ragioneSociale;
    String indirizzoAzienda;
    String codiceAzienda;
    // dati contrattuali
    LocalDate dataAssunzione;
    LocalDate dataCessazione;
    String qualifica;
    String livello;
    String ccnl;
    String codiceCnel;
    // periodo
    YearMonth periodo;
    TipoCedolinoEstratto tipo;
    LocalDate dataElaborazione;
    YearMonth meseVariabili;
    // totali
    BigDecimal lordo;
    BigDecimal totaleCompetenze;
    BigDecimal totaleTrattenute;
    BigDecimal arrotondamento;
    BigDecimal netto;
    // retribuzione
    BigDecimal minimo;
    BigDecimal contingenza;
    BigDecimal superminimo;
    Boolean superminimoAssorbibile;
    BigDecimal importoScatti;
    BigDecimal altriElementiFissi;
    BigDecimal retribuzioneBase;
    BigDecimal totaleRetribuzione;
    BigDecimal pagaOraria;
    BigDecimal pagaGiornaliera;
    BigDecimal divisoreOrario;
    BigDecimal divisoreGiornaliero;
    // corpo
    final List<Voce> voci = new ArrayList<>();
    final List<Rateo> ratei = new ArrayList<>();
    // tfr
    BigDecimal tfrFondo;
    BigDecimal tfrRivalutazione;
    BigDecimal tfrImpostaSostitutiva;
    BigDecimal tfrQuotaAnno;
    BigDecimal tfrQuotaMese;
    BigDecimal tfrQuotaFondi;
    BigDecimal tfrQuotaBusta;
    BigDecimal tfrAnticipi;
    BigDecimal tfrRetribuzioneUtile;
    BigDecimal tfrLiquidatoLordo;
    BigDecimal tfrRitenuta;
    // presenze, scatti, buoni pasto, progressivi
    BigDecimal giorniLavorati;
    BigDecimal oreLavorate;
    BigDecimal giorniRetribuiti;
    Integer numeroScatti;
    LocalDate dataProssimoScatto;
    BigDecimal buoniPastoNumero;
    BigDecimal buoniPastoValore;
    BigDecimal buoniPastoImporto;
    BigDecimal imponibilePrevidenziale;
    BigDecimal imponibileIrpef;
    BigDecimal irpefTrattenuta;
    /** Intestazione del periodo che dichiara una tredicesima o quattordicesima ("13° MENSILITA'", "AGG."). */
    boolean intestazioneTredicesima;
    boolean intestazioneQuattordicesima;
    boolean intestazioneAggiuntiva;
    int numeroCedolini = 1;

    /** Somme con segno, come stampate, degli importi delle voci che entrano nel netto: controllo con i totali. */
    BigDecimal sommaCompetenze = BigDecimal.ZERO;
    BigDecimal sommaTrattenute = BigDecimal.ZERO;
    int vociNelNetto;
    /** Esito del controllo interno del TFR quando il layout lo consente (null se non applicabile). */
    Boolean tfrCoerente;

    private final Set<String> etichetteMancanti = new LinkedHashSet<>();
    private final List<String> controlliFalliti = new ArrayList<>();
    private final List<String> note = new ArrayList<>();

    /** Tolleranza del confronto tra la somma delle voci e i totali stampati. */
    static final BigDecimal TOLLERANZA_SOMME = new BigDecimal("0.05");

    /**
     * Aggiunge una voce del corpo con la categoria dedotta dalla descrizione e dalla colonna. Gli importi sono quelli
     * stampati, con segno: entrano nelle somme di controllo se la voce entra nel netto.
     *
     * @param nelNetto false se il cedolino indica che la voce non entra nel netto, null se non lo indica
     */
    Voce voce(String codice, String descrizione, BigDecimal quantita, String unita, BigDecimal base,
              BigDecimal competenza, BigDecimal trattenuta, Boolean nelNetto) {
        var categoria = Classificazione.categoria(descrizione, competenza, trattenuta, nelNetto, quantita != null);
        var voce = Voce.di(codice, descrizione, categoria, quantita, unita, base, competenza, trattenuta);
        voci.add(voce);
        if (categoria != CategoriaVoce.INFORMATIVA && categoria != CategoriaVoce.PRESENZA
                && (competenza != null || trattenuta != null)) {
            sommaCompetenze = sommaCompetenze.add(competenza == null ? BigDecimal.ZERO : competenza);
            sommaTrattenute = sommaTrattenute.add(trattenuta == null ? BigDecimal.ZERO : trattenuta);
            vociNelNetto++;
        }
        return voce;
    }

    /** Voce solo informativa con l'importo stampato fuori dalle colonne competenze/trattenute. */
    Voce informativa(String codice, String descrizione, BigDecimal quantita, String unita, BigDecimal base) {
        var voce = new Voce(codice, descrizione, CategoriaVoce.INFORMATIVA, quantita, unita, base, null, null);
        voci.add(voce);
        return voce;
    }

    /** Somma delle voci confrontata con i totali stampati: una differenza diventa un controllo non superato. */
    void controllaSomme() {
        if (vociNelNetto == 0) {
            return;
        }
        if (totaleCompetenze != null
                && sommaCompetenze.subtract(totaleCompetenze).abs().compareTo(TOLLERANZA_SOMME) > 0) {
            controlloFallito("La somma delle voci di competenza non corrisponde al totale competenze stampato");
        }
        if (totaleTrattenute != null
                && sommaTrattenute.subtract(totaleTrattenute).abs().compareTo(TOLLERANZA_SOMME) > 0) {
            controlloFallito("La somma delle voci di trattenuta non corrisponde al totale trattenute stampato");
        }
    }

    /** True se le somme delle voci corrispondono ai totali stampati (per le misure). */
    boolean sommeCoerenti() {
        return vociNelNetto > 0 && totaleCompetenze != null && totaleTrattenute != null
                && sommaCompetenze.subtract(totaleCompetenze).abs().compareTo(TOLLERANZA_SOMME) <= 0
                && sommaTrattenute.subtract(totaleTrattenute).abs().compareTo(TOLLERANZA_SOMME) <= 0;
    }

    /**
     * Voce del corpo del cedolino.
     *
     * @param importoCompetenza importo positivo nella colonna competenze
     * @param importoTrattenuta importo positivo nella colonna trattenute
     */
    record Voce(String codice, String descrizione, CategoriaVoce categoria, BigDecimal quantita, String unita,
                BigDecimal base, BigDecimal importoCompetenza, BigDecimal importoTrattenuta) {

        /**
         * Voce con gli importi riportati alla convenzione dello schema (sempre positivi): un importo negativo in una
         * colonna equivale allo stesso importo positivo nell'altra (il netto non cambia).
         */
        static Voce di(String codice, String descrizione, CategoriaVoce categoria, BigDecimal quantita, String unita,
                       BigDecimal base, BigDecimal competenza, BigDecimal trattenuta) {
            BigDecimal comp = competenza;
            BigDecimal tratt = trattenuta;
            if (comp != null && comp.signum() < 0 && tratt == null) {
                tratt = comp.negate();
                comp = null;
            } else if (tratt != null && tratt.signum() < 0 && comp == null) {
                comp = tratt.negate();
                tratt = null;
            }
            return new Voce(codice, descrizione, categoria, quantita, unita, base, comp, tratt);
        }
    }

    record Rateo(TipoRateo tipo, UnitaMisura unita, BigDecimal residuoAnnoPrecedente, BigDecimal maturato,
                 BigDecimal goduto, BigDecimal saldo) {
    }

    /** Etichetta attesa dal layout e non trovata sul documento (nome descrittivo, mai valori). */
    void etichettaMancante(String etichetta) {
        etichetteMancanti.add(etichetta);
    }

    Set<String> etichetteMancanti() {
        return Set.copyOf(etichetteMancanti);
    }

    /** Controllo interno non superato: diventa un avviso (senza valori) e riduce la confidenza. */
    void controlloFallito(String avviso) {
        controlliFalliti.add(avviso);
    }

    List<String> controlliFalliti() {
        return List.copyOf(controlliFalliti);
    }

    /** Nota su una scelta interpretativa non ovvia (senza dati personali). */
    void nota(String nota) {
        if (!note.contains(nota)) {
            note.add(nota);
        }
    }

    boolean contieneTredicesima() {
        return intestazioneTredicesima || voci.stream().anyMatch(v -> v.importoCompetenza() != null
                && v.categoria() == CategoriaVoce.COMPETENZA && Classificazione.tredicesima(v.descrizione()));
    }

    boolean contieneQuattordicesima() {
        return intestazioneQuattordicesima || voci.stream().anyMatch(v -> v.importoCompetenza() != null
                && v.categoria() == CategoriaVoce.COMPETENZA && Classificazione.quattordicesima(v.descrizione()));
    }

    /** Valore di un campo chiave, null se non trovato. */
    Object campo(String percorso) {
        return switch (percorso) {
            case "dipendente.codiceFiscale" -> codiceFiscale;
            case "dipendente.cognome" -> cognome;
            case "dipendente.nome" -> nome;
            case "dipendente.dataNascita" -> dataNascita;
            case "datoreLavoro.ragioneSociale" -> ragioneSociale;
            case "datoreLavoro.codiceFiscale" -> codiceFiscaleAzienda;
            case "datoreLavoro.partitaIva" -> partitaIva;
            case "datoreLavoro.posizioneInps" -> posizioneInps;
            case "periodo.anno", "periodo.mese" -> periodo;
            case "periodo.tipo" -> tipo;
            case "totali.netto" -> netto;
            case "totali.totaleCompetenze" -> totaleCompetenze;
            case "totali.totaleTrattenute" -> totaleTrattenute;
            case "retribuzione.totale" -> totaleRetribuzione;
            case "tfr.fondo31DicembreAnnoPrecedente" -> tfrFondo;
            case "datiContrattuali.dataAssunzione" -> dataAssunzione;
            case "datiContrattuali.ccnl" -> ccnl;
            case "datiContrattuali.qualifica" -> qualifica;
            case "datiContrattuali.livello" -> livello;
            case "progressivi.imponibileIrpef" -> imponibileIrpef;
            default -> throw new IllegalArgumentException("Campo chiave sconosciuto: " + percorso);
        };
    }

    List<String> campiNonTrovati() {
        return CAMPI_CHIAVE.stream().filter(c -> campo(c) == null).toList();
    }

    /** Confidenza complessiva: base, ridotta per etichette mancanti, controlli non superati e campi chiave assenti. */
    BigDecimal confidenza() {
        var c = CONFIDENZA_BASE;
        for (int i = 0; i < etichetteMancanti.size(); i++) {
            c = c.multiply(FATTORE_ETICHETTA_MANCANTE);
        }
        for (int i = 0; i < controlliFalliti.size(); i++) {
            c = c.multiply(FATTORE_CONTROLLO);
        }
        for (int i = 0; i < campiNonTrovati().size(); i++) {
            c = c.multiply(FATTORE_CAMPO_MANCANTE);
        }
        return c.setScale(3, java.math.RoundingMode.HALF_UP);
    }

    /** Numero di campi valorizzati (di tutto il cedolino), per le misure: solo un conteggio. */
    int campiValorizzati() {
        int n = (int) Stream.of(codiceFiscale, nome, cognome, dataNascita, codiceDipendente, codiceFiscaleAzienda,
                partitaIva, posizioneInps, posizioneInail, ragioneSociale, indirizzoAzienda, codiceAzienda,
                dataAssunzione, dataCessazione, qualifica, livello, ccnl, codiceCnel, periodo, tipo, dataElaborazione,
                meseVariabili, lordo, totaleCompetenze, totaleTrattenute, arrotondamento, netto, minimo, contingenza,
                superminimo, superminimoAssorbibile, importoScatti, altriElementiFissi, retribuzioneBase,
                totaleRetribuzione, pagaOraria, pagaGiornaliera, divisoreOrario, divisoreGiornaliero, tfrFondo,
                tfrRivalutazione, tfrImpostaSostitutiva, tfrQuotaAnno, tfrQuotaMese, tfrQuotaFondi, tfrQuotaBusta,
                tfrAnticipi, tfrRetribuzioneUtile, tfrLiquidatoLordo, tfrRitenuta, giorniLavorati, oreLavorate,
                giorniRetribuiti, numeroScatti, dataProssimoScatto, buoniPastoNumero, buoniPastoValore,
                buoniPastoImporto, imponibilePrevidenziale, imponibileIrpef, irpefTrattenuta)
                .filter(Objects::nonNull).count();
        return n + voci.size() + ratei.size();
    }

    // ----------------------------------------------------------------------------------------------------- JSON

    /** JSON conforme allo schema di estrazione. */
    ObjectNode json(JsonMapper mapper) {
        var r = mapper.createObjectNode();
        r.put("numeroCedoliniNelDocumento", numeroCedolini);
        var d = r.putObject("dipendente");
        d.put("codiceFiscale", t(codiceFiscale));
        d.put("nome", t(nome));
        d.put("cognome", t(cognome));
        d.put("dataNascita", t(dataNascita));
        d.put("codiceDipendente", t(codiceDipendente));
        var a = r.putObject("datoreLavoro");
        a.put("codiceFiscale", t(codiceFiscaleAzienda));
        a.put("partitaIva", t(partitaIva));
        a.put("posizioneInps", t(posizioneInps));
        a.put("posizioneInail", t(posizioneInail));
        a.put("ragioneSociale", t(ragioneSociale));
        a.put("indirizzo", t(indirizzoAzienda));
        a.put("codiceAzienda", t(codiceAzienda));
        var c = r.putObject("datiContrattuali");
        c.put("dataAssunzione", t(dataAssunzione));
        c.put("dataCessazione", t(dataCessazione));
        c.put("qualifica", t(qualifica));
        c.put("livello", t(livello));
        c.put("ccnl", t(ccnl));
        c.put("codiceCnel", t(codiceCnel));
        var p = r.putObject("periodo");
        p.put("anno", periodo == null ? 0 : periodo.getYear());
        p.put("mese", periodo == null ? 0 : periodo.getMonthValue());
        p.put("tipo", (tipo == null ? TipoCedolinoEstratto.ORDINARIA : tipo).name());
        p.put("dataElaborazione", t(dataElaborazione));
        p.put("meseVariabili", t(meseVariabili));
        var tot = r.putObject("totali");
        tot.put("lordo", t(lordo));
        tot.put("totaleCompetenze", t(totaleCompetenze));
        tot.put("totaleTrattenute", t(totaleTrattenute));
        tot.put("arrotondamento", t(arrotondamento));
        tot.put("netto", t(netto));
        var re = r.putObject("retribuzione");
        re.put("minimo", t(minimo));
        re.put("contingenza", t(contingenza));
        re.put("superminimo", t(superminimo));
        re.put("superminimoAssorbibile", superminimoAssorbibile == null ? "NON_INDICATO"
                : superminimoAssorbibile ? "SI" : "NO");
        re.put("importoScatti", t(importoScatti));
        re.put("altriElementiFissi", t(altriElementiFissi));
        re.put("retribuzioneBase", t(retribuzioneBase));
        re.put("totale", t(totaleRetribuzione));
        re.put("pagaOraria", t(pagaOraria));
        re.put("pagaGiornaliera", t(pagaGiornaliera));
        re.put("divisoreOrario", t(divisoreOrario));
        re.put("divisoreGiornaliero", t(divisoreGiornaliero));
        var vv = r.putArray("voci");
        for (var v : voci) {
            var n = vv.addObject();
            n.put("codice", t(v.codice()));
            n.put("descrizione", t(v.descrizione()));
            n.put("categoria", v.categoria().name());
            n.put("quantita", t(v.quantita()));
            n.put("unita", t(v.unita()));
            n.put("base", t(v.base()));
            n.put("importoCompetenza", t(v.importoCompetenza()));
            n.put("importoTrattenuta", t(v.importoTrattenuta()));
        }
        var rr = r.putArray("ratei");
        for (var x : ratei) {
            var n = rr.addObject();
            n.put("tipo", x.tipo().name());
            n.put("unita", x.unita().name());
            n.put("residuoAnnoPrecedente", t(x.residuoAnnoPrecedente()));
            n.put("maturato", t(x.maturato()));
            n.put("goduto", t(x.goduto()));
            n.put("saldo", t(x.saldo()));
        }
        var f = r.putObject("tfr");
        f.put("fondo31DicembreAnnoPrecedente", t(tfrFondo));
        f.put("rivalutazione", t(tfrRivalutazione));
        f.put("impostaSostitutiva", t(tfrImpostaSostitutiva));
        f.put("quotaAnno", t(tfrQuotaAnno));
        f.put("quotaMese", t(tfrQuotaMese));
        f.put("quotaFondi", t(tfrQuotaFondi));
        f.put("quotaBusta", t(tfrQuotaBusta));
        f.put("anticipi", t(tfrAnticipi));
        f.put("retribuzioneUtile", t(tfrRetribuzioneUtile));
        f.put("liquidatoLordo", t(tfrLiquidatoLordo));
        f.put("ritenuta", t(tfrRitenuta));
        var pr = r.putObject("presenze");
        pr.put("giorniLavorati", t(giorniLavorati));
        pr.put("oreLavorate", t(oreLavorate));
        pr.put("giorniRetribuiti", t(giorniRetribuiti));
        var s = r.putObject("scatti");
        s.put("numeroScatti", numeroScatti == null ? "" : numeroScatti.toString());
        s.put("dataProssimoScatto", t(dataProssimoScatto));
        var b = r.putObject("buoniPasto");
        b.put("presenti", buoniPastoPresenti());
        b.put("numero", t(buoniPastoNumero));
        b.put("valoreUnitario", t(buoniPastoValore));
        b.put("importo", t(buoniPastoImporto));
        var g = r.putObject("progressivi");
        g.put("imponibilePrevidenziale", t(imponibilePrevidenziale));
        g.put("imponibileIrpef", t(imponibileIrpef));
        g.put("irpefTrattenuta", t(irpefTrattenuta));
        r.put("contieneTredicesima", contieneTredicesima());
        r.put("contieneQuattordicesima", contieneQuattordicesima());
        var conf = r.putObject("confidenza");
        conf.put("complessiva", confidenza());
        var campi = conf.putArray("campi");
        var valori = new LinkedHashMap<String, BigDecimal>();
        CAMPI_CHIAVE.subList(0, 11).forEach(k -> valori.put(k, campo(k) == null ? BigDecimal.ZERO : CONFIDENZA_BASE));
        if (tipo != null && tipo != TipoCedolinoEstratto.ORDINARIA) {
            valori.put("periodo.tipo", new BigDecimal("0.9"));
        }
        for (Map.Entry<String, BigDecimal> e : valori.entrySet()) {
            var n = campi.addObject();
            n.put("campo", e.getKey());
            n.put("valore", e.getValue());
        }
        var nonTrovati = r.putArray("campiNonTrovati");
        campiNonTrovati().forEach(nonTrovati::add);
        var avvisi = r.putArray("avvisi");
        note.forEach(avvisi::add);
        controlliFalliti.forEach(avvisi::add);
        if (!etichetteMancanti.isEmpty()) {
            avvisi.add("Sezioni attese dal layout e non trovate: " + String.join(", ", etichetteMancanti));
        }
        return r;
    }

    boolean buoniPastoPresenti() {
        return buoniPastoImporto != null || buoniPastoNumero != null
                || voci.stream().anyMatch(v -> Classificazione.buoniPasto(v.descrizione()));
    }

    private static String t(Object valore) {
        return switch (valore) {
            case null -> "";
            case BigDecimal importo -> importo.toPlainString();
            default -> valore.toString().strip();
        };
    }
}
