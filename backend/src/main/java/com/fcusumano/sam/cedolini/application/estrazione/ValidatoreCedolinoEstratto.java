package com.fcusumano.sam.cedolini.application.estrazione;

import com.fcusumano.sam.cedolini.application.estrazione.CedolinoEstratto.ConfidenzaCampo;
import com.fcusumano.sam.cedolini.application.estrazione.CedolinoEstratto.TipoCedolinoEstratto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Controlli semantici sul cedolino estratto. Un controllo non superato non scarta il cedolino: abbassa la confidenza
 * (complessiva e del campo coinvolto) e aggiunge un avviso. Gli avvisi non contengono valori estratti.
 * <ul>
 *   <li>anno del periodo plausibile (dal {@value #PRIMO_ANNO_PLAUSIBILE} all'anno prossimo) e mese 1..12;</li>
 *   <li>codice fiscale del dipendente formalmente valido (carattere di controllo, omocodie ammesse);</li>
 *   <li>partita IVA / codice fiscale numerico dell'azienda formalmente validi, se presenti;</li>
 *   <li>totale competenze - totale trattenute (+ arrotondamento) = netto, entro {@link #TOLLERANZA_NETTO};</li>
 *   <li>date contrattuali coerenti; cedolino di fine rapporto successivo alla cessazione;</li>
 *   <li>ratei: residuo AP + maturato - goduto = saldo, entro {@link #TOLLERANZA_RATEI}.</li>
 * </ul>
 */
public final class ValidatoreCedolinoEstratto {

    static final int PRIMO_ANNO_PLAUSIBILE = 1990;
    static final BigDecimal TOLLERANZA_NETTO = new BigDecimal("1.00");
    static final BigDecimal TOLLERANZA_RATEI = new BigDecimal("0.05");
    /** Fattore applicato alla confidenza complessiva per ogni controllo non superato. */
    static final BigDecimal FATTORE_RIDUZIONE = new BigDecimal("0.8");
    /** Tetto alla confidenza del campo coinvolto in un controllo non superato. */
    static final BigDecimal CONFIDENZA_MASSIMA_CAMPO_DUBBIO = new BigDecimal("0.5");
    private static final int SCALA_CONFIDENZA = 5;

    private static final Pattern CODICE_FISCALE = Pattern.compile(
            "[A-Z]{6}[0-9LMNPQRSTUV]{2}[ABCDEHLMPRST][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]");
    private static final Pattern UNDICI_CIFRE = Pattern.compile("\\d{11}");
    /** Valori dei caratteri in posizione dispari (1-based) per il carattere di controllo del codice fiscale. */
    private static final int[] DISPARI = {1, 0, 5, 7, 9, 13, 15, 17, 19, 21, 2, 4, 18, 20, 11, 3, 6, 8, 12, 14, 16, 10,
            22, 25, 24, 23};

    private final Clock clock;

    public ValidatoreCedolinoEstratto(Clock clock) {
        this.clock = clock;
    }

    /** Esito: cedolino con la confidenza aggiornata e avvisi dei controlli non superati. */
    public record Verifica(CedolinoEstratto cedolino, List<String> avvisi) {

        public Verifica {
            avvisi = List.copyOf(avvisi);
        }
    }

    public Verifica verifica(CedolinoEstratto c) {
        var problemi = new ArrayList<Problema>();
        verificaPeriodo(c, problemi);
        verificaIdentificativi(c, problemi);
        verificaTotali(c.totali(), problemi);
        verificaDate(c, problemi);
        verificaRatei(c, problemi);
        if (problemi.isEmpty()) {
            return new Verifica(c, List.of());
        }
        return new Verifica(conConfidenzaRidotta(c, problemi), problemi.stream().map(Problema::avviso).toList());
    }

    private record Problema(String campo, String avviso) {
    }

    private void verificaPeriodo(CedolinoEstratto c, List<Problema> problemi) {
        var periodo = c.periodo();
        Integer anno = periodo == null ? null : periodo.anno();
        Integer mese = periodo == null ? null : periodo.mese();
        int annoMassimo = Year.now(clock).getValue() + 1;
        if (anno == null) {
            problemi.add(new Problema("periodo.anno", "Anno del periodo di retribuzione non determinato"));
        } else if (anno < PRIMO_ANNO_PLAUSIBILE || anno > annoMassimo) {
            problemi.add(new Problema("periodo.anno", "Anno del periodo di retribuzione non plausibile"));
        }
        if (mese == null) {
            problemi.add(new Problema("periodo.mese", "Mese del periodo di retribuzione non determinato"));
        } else if (mese > 12) {
            problemi.add(new Problema("periodo.mese", "Mese del periodo di retribuzione non valido (atteso 1..12)"));
        }
        if (periodo != null && periodo.tipo() == TipoCedolinoEstratto.TREDICESIMA && !Boolean.TRUE.equals(c.contieneTredicesima())) {
            problemi.add(new Problema("periodo.tipo", "Cedolino di tredicesima senza voce di tredicesima"));
        }
        if (periodo != null && periodo.tipo() == TipoCedolinoEstratto.QUATTORDICESIMA
                && !Boolean.TRUE.equals(c.contieneQuattordicesima())) {
            problemi.add(new Problema("periodo.tipo", "Cedolino di quattordicesima senza voce di quattordicesima"));
        }
    }

    private static void verificaIdentificativi(CedolinoEstratto c, List<Problema> problemi) {
        var cf = c.dipendente() == null ? null : c.dipendente().codiceFiscale();
        if (cf == null) {
            problemi.add(new Problema("dipendente.codiceFiscale", "Codice fiscale del dipendente non trovato"));
        } else if (!codiceFiscaleValido(cf)) {
            problemi.add(new Problema("dipendente.codiceFiscale", "Codice fiscale del dipendente formalmente non valido"));
        }
        var azienda = c.datoreLavoro();
        if (azienda == null) {
            return;
        }
        if (azienda.partitaIva() != null && !partitaIvaValida(azienda.partitaIva())) {
            problemi.add(new Problema("datoreLavoro.partitaIva", "Partita IVA dell'azienda formalmente non valida"));
        }
        var cfAzienda = azienda.codiceFiscale();
        if (cfAzienda != null && !(partitaIvaValida(cfAzienda) || codiceFiscaleValido(cfAzienda))) {
            problemi.add(new Problema("datoreLavoro.codiceFiscale", "Codice fiscale dell'azienda formalmente non valido"));
        }
    }

    private static void verificaTotali(CedolinoEstratto.Totali t, List<Problema> problemi) {
        if (t == null || t.totaleCompetenze() == null || t.totaleTrattenute() == null || t.netto() == null) {
            return;
        }
        var differenza = t.totaleCompetenze().subtract(t.totaleTrattenute());
        var arrotondamento = t.arrotondamento() == null ? BigDecimal.ZERO : t.arrotondamento();
        boolean coerente = entro(differenza.add(arrotondamento), t.netto(), TOLLERANZA_NETTO)
                || entro(differenza, t.netto(), TOLLERANZA_NETTO);
        if (!coerente) {
            problemi.add(new Problema("totali.netto",
                    "Totale competenze meno totale trattenute non corrisponde al netto"));
        }
    }

    private static void verificaDate(CedolinoEstratto c, List<Problema> problemi) {
        var contratto = c.datiContrattuali();
        var assunzione = contratto == null ? null : data(contratto.dataAssunzione());
        var cessazione = contratto == null ? null : data(contratto.dataCessazione());
        if (assunzione != null && cessazione != null && cessazione.isBefore(assunzione)) {
            problemi.add(new Problema("datiContrattuali.dataCessazione", "Data di cessazione precedente all'assunzione"));
        }
        var nascita = c.dipendente() == null ? null : data(c.dipendente().dataNascita());
        if (nascita != null && assunzione != null && !nascita.isBefore(assunzione)) {
            problemi.add(new Problema("dipendente.dataNascita", "Data di nascita non precedente all'assunzione"));
        }
        var periodo = c.periodo();
        if (periodo == null || periodo.tipo() != TipoCedolinoEstratto.FINE_RAPPORTO) {
            return;
        }
        if (cessazione == null) {
            problemi.add(new Problema("periodo.tipo", "Cedolino di fine rapporto senza data di cessazione stampata"));
        } else if (periodo.anno() != null && periodo.mese() != null && periodo.mese() <= 12
                && !YearMonth.of(periodo.anno(), periodo.mese()).isAfter(YearMonth.from(cessazione))) {
            problemi.add(new Problema("periodo.tipo",
                    "Cedolino di fine rapporto con periodo non successivo al mese di cessazione"));
        }
    }

    private static void verificaRatei(CedolinoEstratto c, List<Problema> problemi) {
        if (c.ratei() == null) {
            return;
        }
        for (var r : c.ratei()) {
            if (r.residuoAnnoPrecedente() == null || r.maturato() == null || r.goduto() == null || r.saldo() == null) {
                continue;
            }
            var atteso = r.residuoAnnoPrecedente().add(r.maturato()).subtract(r.goduto());
            if (!entro(atteso, r.saldo(), TOLLERANZA_RATEI)) {
                problemi.add(new Problema("ratei", "Saldo del rateo " + r.tipo() + " (" + r.unita()
                        + ") diverso da residuo + maturato - goduto"));
            }
        }
    }

    private static CedolinoEstratto conConfidenzaRidotta(CedolinoEstratto c, List<Problema> problemi) {
        var originale = c.confidenza();
        var complessiva = originale == null || originale.complessiva() == null ? BigDecimal.ONE : originale.complessiva();
        for (int i = 0; i < problemi.size(); i++) {
            complessiva = complessiva.multiply(FATTORE_RIDUZIONE);
        }
        var campi = new LinkedHashMap<String, BigDecimal>();
        if (originale != null && originale.campi() != null) {
            originale.campi().forEach(cc -> campi.put(cc.campo(), cc.valore()));
        }
        for (var p : problemi) {
            campi.merge(p.campo(), CONFIDENZA_MASSIMA_CAMPO_DUBBIO, BigDecimal::min);
        }
        var nuoviCampi = campi.entrySet().stream()
                .map((Map.Entry<String, BigDecimal> e) -> new ConfidenzaCampo(e.getKey(), scala(e.getValue())))
                .toList();
        var confidenza = new CedolinoEstratto.Confidenza(scala(complessiva), nuoviCampi);
        return new CedolinoEstratto(c.numeroCedoliniNelDocumento(), c.dipendente(), c.datoreLavoro(), c.datiContrattuali(),
                c.periodo(), c.totali(), c.retribuzione(), c.voci(), c.ratei(), c.tfr(), c.presenze(), c.scatti(),
                c.buoniPasto(), c.progressivi(), c.contieneTredicesima(), c.contieneQuattordicesima(), confidenza,
                c.campiNonTrovati());
    }

    private static BigDecimal scala(BigDecimal valore) {
        return valore == null ? null : valore.setScale(SCALA_CONFIDENZA, RoundingMode.HALF_UP);
    }

    private static boolean entro(BigDecimal a, BigDecimal b, BigDecimal tolleranza) {
        return a.subtract(b).abs().compareTo(tolleranza) <= 0;
    }

    private static LocalDate data(String iso) {
        return iso == null ? null : LocalDate.parse(iso);
    }

    /** Verifica formale del codice fiscale di una persona fisica (16 caratteri, omocodie ammesse). */
    static boolean codiceFiscaleValido(String cf) {
        if (cf == null || !CODICE_FISCALE.matcher(cf).matches()) {
            return false;
        }
        int somma = 0;
        for (int i = 0; i < 15; i++) {
            char ch = cf.charAt(i);
            int indice = Character.isDigit(ch) ? ch - '0' : ch - 'A';
            somma += i % 2 == 0 ? DISPARI[indice] : indice;
        }
        return cf.charAt(15) == (char) ('A' + somma % 26);
    }

    /** Verifica formale di una partita IVA (o codice fiscale numerico) di 11 cifre. */
    static boolean partitaIvaValida(String piva) {
        if (piva == null || !UNDICI_CIFRE.matcher(piva).matches() || piva.equals("00000000000")) {
            return false;
        }
        int somma = 0;
        for (int i = 0; i < 10; i++) {
            int cifra = piva.charAt(i) - '0';
            if (i % 2 == 1) {
                cifra *= 2;
                if (cifra > 9) {
                    cifra -= 9;
                }
            }
            somma += cifra;
        }
        return (10 - somma % 10) % 10 == piva.charAt(10) - '0';
    }
}
