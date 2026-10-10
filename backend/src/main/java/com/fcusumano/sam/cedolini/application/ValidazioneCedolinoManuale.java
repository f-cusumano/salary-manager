package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.cedolini.BuoniPasto;
import com.fcusumano.sam.cedolini.CategoriaVoce;
import com.fcusumano.sam.cedolini.CedolinoManuale;
import com.fcusumano.sam.cedolini.DatiTfr;
import com.fcusumano.sam.cedolini.ElementiRetributivi;
import com.fcusumano.sam.cedolini.Presenze;
import com.fcusumano.sam.cedolini.ProgressiviAnnui;
import com.fcusumano.sam.cedolini.Rateo;
import com.fcusumano.sam.cedolini.Voce;
import com.fcusumano.sam.cedolini.application.estrazione.ValidatoreCedolinoEstratto;
import com.fcusumano.sam.shared.DatiNonValidiException.ErroreCampo;
import com.fcusumano.sam.shared.TipoMensilita;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Validazione dei cedolini inseriti a mano, prima di trasformarli nel JSON dello schema di lettura (i valori che
 * l'interprete non sa leggere diventerebbero null in silenzio):
 * <ul>
 *   <li>errori bloccanti campo per campo ({@link #verifica}): dati indispensabili all'archiviazione e al confronto con
 *       i duplicati, identificativi, limiti delle colonne. Il client li rispecchia, ma l'autorita' e' il server;</li>
 *   <li>normalizzazione ({@link #normalizza}): testi ripuliti, codici maiuscoli, flag di tredicesima e quattordicesima
 *       coerenti con il tipo, buoni pasto senza importi se assenti;</li>
 *   <li>avvisi non bloccanti salvati con il cedolino ({@link #avvisi}), oltre ai controlli del validatore comune a
 *       tutti i lettori: somme delle voci diverse dai totali, codice fiscale provvisorio.</li>
 * </ul>
 * I campi sono indicati con il percorso dello schema (es. "totali.netto", "voci[2].descrizione"); un campo ha al piu'
 * un errore. I messaggi non ripetono mai i valori inseriti.
 */
final class ValidazioneCedolinoManuale {

    static final int PRIMO_ANNO = 1900;
    /** Anno massimo delle date (le colonne accettano anni fino al 2999, come le competenze). */
    static final int ULTIMO_ANNO_DATE = 2999;
    /** Valore assoluto massimo di importi e quantita' (colonne NUMERIC(15,5): 10 cifre intere). */
    static final BigDecimal LIMITE_IMPORTI = BigDecimal.TEN.pow(10);
    static final int DECIMALI_MASSIMI = 5;
    static final int SCATTI_MASSIMI = 99;
    /** Tolleranza del confronto tra la somma delle voci e i totali stampati (come il lettore locale). */
    static final BigDecimal TOLLERANZA_SOMME = new BigDecimal("0.05");

    static final String AVVISO_SOMMA_COMPETENZE =
            "La somma delle voci di competenza non corrisponde al totale competenze stampato";
    static final String AVVISO_SOMMA_TRATTENUTE =
            "La somma delle voci di trattenuta non corrisponde al totale trattenute stampato";
    static final String AVVISO_CODICE_FISCALE_PROVVISORIO = "Codice fiscale provvisorio (11 cifre)";

    private static final Pattern SPAZI = Pattern.compile("\\s+");
    private static final Pattern UNDICI_CIFRE = Pattern.compile("\\d{11}");
    private static final Pattern PARTITA_IVA_CON_PREFISSO = Pattern.compile("IT(\\d{11})");
    private static final Pattern CODICE_FISCALE_AZIENDA = Pattern.compile("\\d{11}|[A-Z0-9]{16}");
    private static final Pattern LETTERA_O_CIFRA = Pattern.compile("[\\p{L}\\p{N}]");

    private static final CedolinoManuale.Dipendente DIPENDENTE_VUOTO =
            new CedolinoManuale.Dipendente(null, null, null, null, null);
    private static final CedolinoManuale.DatoreLavoro DATORE_VUOTO =
            new CedolinoManuale.DatoreLavoro(null, null, null, null, null, null, null);
    private static final CedolinoManuale.DatiContrattuali CONTRATTO_VUOTO =
            new CedolinoManuale.DatiContrattuali(null, null, null, null, null, null);
    private static final CedolinoManuale.Periodo PERIODO_VUOTO =
            new CedolinoManuale.Periodo(null, null, null, null, null);
    private static final CedolinoManuale.Totali TOTALI_VUOTI = new CedolinoManuale.Totali(null, null, null, null, null);
    private static final CedolinoManuale.Scatti SCATTI_VUOTI = new CedolinoManuale.Scatti(null, null);
    private static final Voce VOCE_VUOTA = new Voce(null, null, null, null, null, null, null, null);
    private static final Rateo RATEO_VUOTO = new Rateo(null, null, null, null, null, null);

    private final Clock clock;

    ValidazioneCedolinoManuale(Clock clock) {
        this.clock = clock;
    }

    // ------------------------------------------------------------------------------------------ errori bloccanti

    /**
     * Errori bloccanti, nell'ordine dei campi dello schema (sui dati normalizzati).
     *
     * @param codiciFiscaliCensiti codici fiscali delle persone del workspace: un codice formalmente non valido e'
     *                             accettato se corrisponde a una persona gia' censita
     * @return errori, vuota se il cedolino si puo' acquisire
     */
    List<ErroreCampo> verifica(CedolinoManuale dati, Set<String> codiciFiscaliCensiti) {
        var c = normalizza(dati);
        var errori = new Errori();
        dipendente(c.dipendente(), codiciFiscaliCensiti, errori);
        datoreLavoro(c.datoreLavoro(), errori);
        datiContrattuali(c.datiContrattuali(), errori);
        periodo(c.periodo(), errori);
        totali(c.totali(), errori);
        retribuzione(c.retribuzione(), errori);
        voci(c.voci(), errori);
        ratei(c.ratei(), errori);
        tfr(c.tfr(), errori);
        var p = c.presenze();
        errori.importo("presenze.giorniLavorati", p.giorniLavorati());
        errori.importo("presenze.oreLavorate", p.oreLavorate());
        errori.importo("presenze.giorniRetribuiti", p.giorniRetribuiti());
        var numeroScatti = c.scatti().numeroScatti();
        if (numeroScatti != null && (numeroScatti < 0 || numeroScatti > SCATTI_MASSIMI)) {
            errori.aggiungi("scatti.numeroScatti", "Numero di scatti non valido");
        }
        errori.data("scatti.dataProssimoScatto", c.scatti().dataProssimoScatto());
        var b = c.buoniPasto();
        errori.importo("buoniPasto.numero", b.numero());
        errori.importo("buoniPasto.valoreUnitario", b.valoreUnitario());
        errori.importo("buoniPasto.importo", b.importo());
        var g = c.progressivi();
        errori.importo("progressivi.imponibilePrevidenziale", g.imponibilePrevidenziale());
        errori.importo("progressivi.imponibileIrpef", g.imponibileIrpef());
        errori.importo("progressivi.irpefTrattenuta", g.irpefTrattenuta());
        return errori.elenco();
    }

    private void dipendente(CedolinoManuale.Dipendente d, Set<String> codiciFiscaliCensiti, Errori errori) {
        var cf = d.codiceFiscale();
        if (cf == null) {
            errori.aggiungi("dipendente.codiceFiscale", "Indica il codice fiscale del dipendente");
        } else if (!(ValidatoreCedolinoEstratto.codiceFiscaleValido(cf) || UNDICI_CIFRE.matcher(cf).matches()
                || codiciFiscaliCensiti.contains(cf))) {
            errori.aggiungi("dipendente.codiceFiscale", "Codice fiscale non valido: controlla i 16 caratteri");
        }
        errori.obbligatorio("dipendente.nome", d.nome(), "Indica il nome");
        errori.testo("dipendente.nome", d.nome(), 100);
        errori.obbligatorio("dipendente.cognome", d.cognome(), "Indica il cognome");
        errori.testo("dipendente.cognome", d.cognome(), 100);
        if (d.dataNascita() != null && d.dataNascita().isAfter(LocalDate.now(clock))) {
            errori.aggiungi("dipendente.dataNascita", "La data di nascita è nel futuro");
        }
        errori.data("dipendente.dataNascita", d.dataNascita());
        errori.testo("dipendente.codiceDipendente", d.codiceDipendente(), 50);
    }

    private static void datoreLavoro(CedolinoManuale.DatoreLavoro a, Errori errori) {
        if (a.codiceFiscale() != null && !CODICE_FISCALE_AZIENDA.matcher(a.codiceFiscale()).matches()) {
            errori.aggiungi("datoreLavoro.codiceFiscale", "Codice fiscale dell'azienda non valido");
        }
        if (a.partitaIva() != null && !UNDICI_CIFRE.matcher(a.partitaIva()).matches()) {
            errori.aggiungi("datoreLavoro.partitaIva", "Partita IVA non valida: deve avere 11 cifre");
        }
        errori.testo("datoreLavoro.posizioneInps", a.posizioneInps(), 50);
        errori.testo("datoreLavoro.posizioneInail", a.posizioneInail(), 50);
        if (a.ragioneSociale() == null || !LETTERA_O_CIFRA.matcher(a.ragioneSociale()).find()) {
            errori.aggiungi("datoreLavoro.ragioneSociale", "Indica la ragione sociale");
        }
        errori.testo("datoreLavoro.ragioneSociale", a.ragioneSociale(), 200);
        errori.testo("datoreLavoro.indirizzo", a.indirizzo(), 300);
        errori.testo("datoreLavoro.codiceAzienda", a.codiceAzienda(), 50);
    }

    private static void datiContrattuali(CedolinoManuale.DatiContrattuali c, Errori errori) {
        errori.data("datiContrattuali.dataAssunzione", c.dataAssunzione());
        if (c.dataAssunzione() != null && c.dataCessazione() != null && c.dataCessazione().isBefore(c.dataAssunzione())) {
            errori.aggiungi("datiContrattuali.dataCessazione", "La data di cessazione precede l'assunzione");
        }
        errori.data("datiContrattuali.dataCessazione", c.dataCessazione());
        errori.testo("datiContrattuali.qualifica", c.qualifica(), 100);
        errori.testo("datiContrattuali.livello", c.livello(), 50);
        errori.testo("datiContrattuali.ccnl", c.ccnl(), 300);
        errori.testo("datiContrattuali.codiceCnel", c.codiceCnel(), 20);
    }

    private void periodo(CedolinoManuale.Periodo p, Errori errori) {
        int annoMassimo = Year.now(clock).getValue() + 1;
        if (p.anno() == null || p.anno() < PRIMO_ANNO || p.anno() > annoMassimo) {
            errori.aggiungi("periodo.anno", "Indica l'anno (dal " + PRIMO_ANNO + " al " + annoMassimo + ")");
        }
        if (p.mese() == null || p.mese() < 1 || p.mese() > 12) {
            errori.aggiungi("periodo.mese",
                    "Indica il mese (da 1 a 12): tredicesima e quattordicesima si indicano con il tipo");
        }
        if (p.tipo() == null) {
            errori.aggiungi("periodo.tipo", "Indica il tipo di cedolino");
        }
        errori.data("periodo.dataElaborazione", p.dataElaborazione());
        if (p.meseVariabili() != null && fuoriIntervallo(p.meseVariabili().getYear())) {
            errori.aggiungi("periodo.meseVariabili", messaggioData());
        }
    }

    private static void totali(CedolinoManuale.Totali t, Errori errori) {
        errori.importo("totali.lordo", t.lordo());
        errori.obbligatorio("totali.totaleCompetenze", t.totaleCompetenze(), "Indica il totale competenze");
        errori.senzaSegno("totali.totaleCompetenze", t.totaleCompetenze());
        errori.importo("totali.totaleCompetenze", t.totaleCompetenze());
        errori.obbligatorio("totali.totaleTrattenute", t.totaleTrattenute(), "Indica il totale trattenute");
        errori.senzaSegno("totali.totaleTrattenute", t.totaleTrattenute());
        errori.importo("totali.totaleTrattenute", t.totaleTrattenute());
        errori.importo("totali.arrotondamento", t.arrotondamento());
        errori.obbligatorio("totali.netto", t.netto(), "Indica il netto");
        errori.importo("totali.netto", t.netto());
    }

    private static void retribuzione(ElementiRetributivi r, Errori errori) {
        errori.importo("retribuzione.minimo", r.minimo());
        errori.importo("retribuzione.contingenza", r.contingenza());
        errori.importo("retribuzione.superminimo", r.superminimo());
        errori.importo("retribuzione.importoScatti", r.importoScatti());
        errori.importo("retribuzione.altriElementiFissi", r.altriElementiFissi());
        errori.importo("retribuzione.retribuzioneBase", r.retribuzioneBase());
        errori.importo("retribuzione.totale", r.totale());
        errori.importo("retribuzione.pagaOraria", r.pagaOraria());
        errori.importo("retribuzione.pagaGiornaliera", r.pagaGiornaliera());
        errori.importo("retribuzione.divisoreOrario", r.divisoreOrario());
        errori.importo("retribuzione.divisoreGiornaliero", r.divisoreGiornaliero());
    }

    private static void voci(List<Voce> voci, Errori errori) {
        for (int i = 0; i < voci.size(); i++) {
            var v = voci.get(i);
            var p = "voci[" + i + "].";
            errori.testo(p + "codice", v.codice(), 30);
            errori.obbligatorio(p + "descrizione", v.descrizione(), "Indica la descrizione della voce");
            errori.testo(p + "descrizione", v.descrizione(), 200);
            errori.obbligatorio(p + "categoria", v.categoria(), "Indica la categoria della voce");
            errori.importo(p + "quantita", v.quantita());
            errori.testo(p + "unita", v.unita(), 20);
            errori.importo(p + "base", v.base());
            errori.senzaSegno(p + "importoCompetenza", v.importoCompetenza());
            errori.importo(p + "importoCompetenza", v.importoCompetenza());
            errori.senzaSegno(p + "importoTrattenuta", v.importoTrattenuta());
            errori.importo(p + "importoTrattenuta", v.importoTrattenuta());
        }
    }

    private static void ratei(List<Rateo> ratei, Errori errori) {
        var visti = new HashSet<String>();
        for (int i = 0; i < ratei.size(); i++) {
            var r = ratei.get(i);
            var rateo = "ratei[" + i + "]";
            errori.obbligatorio(rateo + ".tipo", r.tipo(), "Indica il tipo del rateo");
            errori.obbligatorio(rateo + ".unita", r.unita(), "Indica l'unità del rateo");
            if (r.tipo() != null && r.unita() != null && !visti.add(r.tipo() + "/" + r.unita())) {
                errori.aggiungi(rateo, "Rateo ripetuto: uno solo per tipo e unità");
            }
            if (Stream.of(r.residuoAnnoPrecedente(), r.maturato(), r.goduto(), r.saldo()).allMatch(Objects::isNull)) {
                errori.aggiungi(rateo, "Indica almeno un valore del rateo");
            }
            errori.importo(rateo + ".residuoAnnoPrecedente", r.residuoAnnoPrecedente());
            errori.importo(rateo + ".maturato", r.maturato());
            errori.importo(rateo + ".goduto", r.goduto());
            errori.importo(rateo + ".saldo", r.saldo());
        }
    }

    private static void tfr(DatiTfr t, Errori errori) {
        if (t == null) {
            return;
        }
        errori.importo("tfr.fondo31DicembreAnnoPrecedente", t.fondo31DicembreAnnoPrecedente());
        errori.importo("tfr.rivalutazione", t.rivalutazione());
        errori.importo("tfr.impostaSostitutiva", t.impostaSostitutiva());
        errori.importo("tfr.quotaAnno", t.quotaAnno());
        errori.importo("tfr.quotaMese", t.quotaMese());
        errori.importo("tfr.quotaFondi", t.quotaFondi());
        errori.importo("tfr.quotaBusta", t.quotaBusta());
        errori.importo("tfr.anticipi", t.anticipi());
        errori.importo("tfr.retribuzioneUtile", t.retribuzioneUtile());
        errori.importo("tfr.liquidatoLordo", t.liquidatoLordo());
        errori.importo("tfr.ritenuta", t.ritenuta());
    }

    private static boolean fuoriIntervallo(int anno) {
        return anno < PRIMO_ANNO || anno > ULTIMO_ANNO_DATE;
    }

    private static String messaggioData() {
        return "Data non valida (anno dal " + PRIMO_ANNO + " al " + ULTIMO_ANNO_DATE + ")";
    }

    /** Errori per campo, al piu' uno per campo (il primo rilevato), nell'ordine di inserimento. */
    private static final class Errori {

        private final Map<String, String> perCampo = new LinkedHashMap<>();

        void aggiungi(String campo, String messaggio) {
            perCampo.putIfAbsent(campo, messaggio);
        }

        void obbligatorio(String campo, Object valore, String messaggio) {
            if (valore == null) {
                aggiungi(campo, messaggio);
            }
        }

        void testo(String campo, String valore, int lunghezzaMassima) {
            if (valore == null) {
                return;
            }
            if (valore.chars().anyMatch(Character::isISOControl)) {
                aggiungi(campo, "Il testo contiene caratteri non ammessi");
            } else if (valore.length() > lunghezzaMassima) {
                aggiungi(campo, "Testo troppo lungo (massimo " + lunghezzaMassima + " caratteri)");
            }
        }

        void importo(String campo, BigDecimal valore) {
            if (valore != null && (valore.abs().compareTo(LIMITE_IMPORTI) >= 0
                    || valore.stripTrailingZeros().scale() > DECIMALI_MASSIMI)) {
                aggiungi(campo, "Valore fuori dai limiti (massimo 10 cifre intere e 5 decimali)");
            }
        }

        void senzaSegno(String campo, BigDecimal valore) {
            if (valore != null && valore.signum() < 0) {
                aggiungi(campo, "Indica l'importo senza segno");
            }
        }

        void data(String campo, LocalDate valore) {
            if (valore != null && fuoriIntervallo(valore.getYear())) {
                aggiungi(campo, messaggioData());
            }
        }

        List<ErroreCampo> elenco() {
            var elenco = new ArrayList<ErroreCampo>(perCampo.size());
            perCampo.forEach((campo, messaggio) -> elenco.add(new ErroreCampo(campo, messaggio)));
            return List.copyOf(elenco);
        }
    }

    // ------------------------------------------------------------------------------------------ normalizzazione

    /**
     * Cedolino normalizzato, con tutti i sotto-record presenti (tranne il TFR, null se non stampato): testi senza
     * spazi superflui (vuoto = null), codici fiscali e partita IVA maiuscoli e senza spazi (senza il prefisso "IT"
     * davanti alle 11 cifre), flag di tredicesima e quattordicesima veri per i cedolini di quel tipo, buoni pasto senza
     * importi se non presenti. Idempotente.
     */
    static CedolinoManuale normalizza(CedolinoManuale c) {
        var dipendente = c.dipendente() == null ? DIPENDENTE_VUOTO : c.dipendente();
        var datore = c.datoreLavoro() == null ? DATORE_VUOTO : c.datoreLavoro();
        var contratto = c.datiContrattuali() == null ? CONTRATTO_VUOTO : c.datiContrattuali();
        var periodo = c.periodo() == null ? PERIODO_VUOTO : c.periodo();
        var buoniPasto = c.buoniPasto() == null || !c.buoniPasto().presenti() ? BuoniPasto.ASSENTI : c.buoniPasto();
        return new CedolinoManuale(
                new CedolinoManuale.Dipendente(codice(dipendente.codiceFiscale()), testo(dipendente.nome()),
                        testo(dipendente.cognome()), dipendente.dataNascita(), testo(dipendente.codiceDipendente())),
                new CedolinoManuale.DatoreLavoro(senzaPrefissoIt(codice(datore.codiceFiscale())),
                        senzaPrefissoIt(codice(datore.partitaIva())), testo(datore.posizioneInps()),
                        testo(datore.posizioneInail()), testo(datore.ragioneSociale()), testo(datore.indirizzo()),
                        testo(datore.codiceAzienda())),
                new CedolinoManuale.DatiContrattuali(contratto.dataAssunzione(), contratto.dataCessazione(),
                        testo(contratto.qualifica()), testo(contratto.livello()), testo(contratto.ccnl()),
                        testo(contratto.codiceCnel())),
                periodo,
                c.totali() == null ? TOTALI_VUOTI : c.totali(),
                c.retribuzione() == null ? ElementiRetributivi.VUOTI : c.retribuzione(),
                c.voci().stream().map(ValidazioneCedolinoManuale::voce).toList(),
                c.ratei().stream().map(r -> r == null ? RATEO_VUOTO : r).toList(),
                c.tfr(),
                c.presenze() == null ? Presenze.VUOTE : c.presenze(),
                c.scatti() == null ? SCATTI_VUOTI : c.scatti(),
                buoniPasto,
                c.progressivi() == null ? ProgressiviAnnui.VUOTI : c.progressivi(),
                c.contieneTredicesima() || periodo.tipo() == TipoMensilita.TREDICESIMA,
                c.contieneQuattordicesima() || periodo.tipo() == TipoMensilita.QUATTORDICESIMA);
    }

    private static Voce voce(Voce v) {
        if (v == null) {
            return VOCE_VUOTA;
        }
        return new Voce(testo(v.codice()), testo(v.descrizione()), v.categoria(), v.quantita(), testo(v.unita()),
                v.base(), v.importoCompetenza(), v.importoTrattenuta());
    }

    private static String testo(String valore) {
        if (valore == null) {
            return null;
        }
        var pulito = SPAZI.matcher(valore).replaceAll(" ").strip();
        return pulito.isEmpty() ? null : pulito;
    }

    private static String codice(String valore) {
        if (valore == null) {
            return null;
        }
        var pulito = SPAZI.matcher(valore).replaceAll("").toUpperCase(Locale.ROOT);
        return pulito.isEmpty() ? null : pulito;
    }

    private static String senzaPrefissoIt(String codice) {
        if (codice == null) {
            return null;
        }
        var prefissato = PARTITA_IVA_CON_PREFISSO.matcher(codice);
        return prefissato.matches() ? prefissato.group(1) : codice;
    }

    // ------------------------------------------------------------------------------------------ avvisi

    /**
     * Avvisi non bloccanti propri dell'inserimento a mano, salvati con il cedolino insieme a quelli del validatore
     * comune: somma delle voci che entrano nel netto diversa dai totali (come il lettore locale: solo se almeno una
     * voce ha un importo, tolleranza 0,05 euro) e codice fiscale provvisorio.
     *
     * @param normalizzato cedolino gia' normalizzato
     */
    static List<String> avvisi(CedolinoManuale normalizzato) {
        var avvisi = new ArrayList<String>();
        var nelNetto = normalizzato.voci().stream()
                .filter(v -> v.categoria() != CategoriaVoce.INFORMATIVA && v.categoria() != CategoriaVoce.PRESENZA)
                .filter(v -> v.importoCompetenza() != null || v.importoTrattenuta() != null)
                .toList();
        if (!nelNetto.isEmpty()) {
            var totali = normalizzato.totali();
            var competenze = somma(nelNetto.stream().map(Voce::importoCompetenza));
            var trattenute = somma(nelNetto.stream().map(Voce::importoTrattenuta));
            if (totali.totaleCompetenze() != null && fuoriTolleranza(competenze, totali.totaleCompetenze())) {
                avvisi.add(AVVISO_SOMMA_COMPETENZE);
            }
            if (totali.totaleTrattenute() != null && fuoriTolleranza(trattenute, totali.totaleTrattenute())) {
                avvisi.add(AVVISO_SOMMA_TRATTENUTE);
            }
        }
        var cf = normalizzato.dipendente().codiceFiscale();
        if (cf != null && UNDICI_CIFRE.matcher(cf).matches()) {
            avvisi.add(AVVISO_CODICE_FISCALE_PROVVISORIO);
        }
        return List.copyOf(avvisi);
    }

    private static BigDecimal somma(Stream<BigDecimal> importi) {
        return importi.filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean fuoriTolleranza(BigDecimal somma, BigDecimal totale) {
        return somma.subtract(totale).abs().compareTo(TOLLERANZA_SOMME) > 0;
    }
}
