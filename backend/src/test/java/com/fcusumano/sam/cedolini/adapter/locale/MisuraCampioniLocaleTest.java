package com.fcusumano.sam.cedolini.adapter.locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fcusumano.sam.cedolini.application.estrazione.CedolinoEstratto.TipoCedolinoEstratto;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException;
import com.fcusumano.sam.cedolini.application.estrazione.LettorePdf;
import com.fcusumano.sam.cedolini.application.estrazione.PdfNonLeggibileException;
import com.fcusumano.sam.cedolini.config.CampioniProperties;
import com.fcusumano.sam.shared.config.VariabiliAmbiente;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.test.context.ActiveProfiles;

/**
 * Misura del lettore locale sui cedolini reali in {@code salary-manager.samples-dir} (dati personali, esclusi da git;
 * piu' cartelle separate da {@code :}). Esclusa dalla build di default (property Maven
 * {@code sam.test.excludedGroups=live,campioni}); si esegue dalla cartella {@code backend/} con
 * {@code ./mvnw test -Dgroups=campioni -Dsam.test.excludedGroups=none}. Non usa la rete. I PDF protetti si aprono con
 * {@code SAM_SAMPLES_PASSWORD}, {@code SAM_PDF_PASSWORD_CANDIDATE} o con i codici fiscali letti dagli altri campioni
 * (come fa l'applicazione con le persone gia' censite).
 * <p>
 * Stampa per ogni file solo numero progressivo, layout, esito, tipo, conteggi (campi valorizzati, campi chiave non
 * trovati, voci, ratei, avvisi) ed esiti dei controlli di coerenza: netto = competenze - trattenute + arrotondamento,
 * somme delle voci = totali stampati, ratei (residuo + maturato - goduto = saldo), TFR (fondo + quota anno = TFR
 * spettante, dove stampato). Poi confronta i mesi consecutivi dello stesso rapporto: progressivi annui non decrescenti
 * nell'anno, fondo TFR al 31/12 costante nell'anno e non decrescente al cambio d'anno. Mai nomi file, nomi, codici
 * fiscali, periodi o importi; degli errori stampa solo il motivo.
 * <p>
 * Fallisce se un cedolino letto non supera i controlli di coerenza del singolo documento.
 */
@Tag("campioni")
@SpringBootTest(properties = {"salary-manager.lettore=locale", "SAM_ENV_FILE=../.env"})
@ActiveProfiles("test")
class MisuraCampioniLocaleTest {

    private static final BigDecimal CENTESIMO = new BigDecimal("0.01");

    @Autowired
    LettoreLocaleCedolini lettore;

    @Autowired
    LettorePdf lettorePdf;

    @Autowired
    CampioniProperties campioni;

    @Autowired
    ConfigurableEnvironment ambiente;

    /** Esito di un file: solo dati non personali, piu' la lettura tenuta in memoria per i confronti tra mesi. */
    private record Esito(int numero, String layout, String esito, boolean protetto, CedolinoLetto letto,
                         int avvisi, List<String> incoerenze) {
    }

    @Test
    void misuraIlLettoreLocaleSuiCampioni() throws IOException {
        var pdf = new ArrayList<Path>();
        for (var cartella : campioni.cartelle()) {
            if (!Files.isDirectory(cartella)) {
                continue;
            }
            try (var file = Files.list(cartella)) {
                file.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
                        .sorted()
                        .forEach(pdf::add);
            }
        }
        assumeTrue(!pdf.isEmpty(), "Nessun PDF nelle cartelle dei campioni: misura saltata");
        var password = VariabiliAmbiente.leggi(ambiente, "SAM_SAMPLES_PASSWORD").orElse(null);

        // codici fiscali letti, solo in memoria: aprono i PDF protetti come le persone censite nell'applicazione
        Set<String> codiciFiscali = new LinkedHashSet<>();
        var esiti = new TreeMap<Integer, Esito>();
        var protetti = new ArrayList<Integer>();
        for (int i = 0; i < pdf.size(); i++) {
            var esito = misura(i + 1, Files.readAllBytes(pdf.get(i)), password, Set.of());
            if (esito == null) {
                protetti.add(i);
            } else {
                esiti.put(i + 1, esito);
                if (esito.letto() != null && esito.letto().codiceFiscale != null) {
                    codiciFiscali.add(esito.letto().codiceFiscale);
                }
            }
        }
        for (int i : protetti) {
            var esito = misura(i + 1, Files.readAllBytes(pdf.get(i)), password, codiciFiscali);
            esiti.put(i + 1, esito != null ? esito
                    : new Esito(i + 1, "-", "ERRORE PROTETTO_DA_PASSWORD", true, null, 0, List.of()));
        }

        esiti.values().forEach(MisuraCampioniLocaleTest::stampa);
        var riepilogo = riepilogo(esiti.values());
        riepilogo.forEach(System.out::println);
        confrontaMesiConsecutivi(esiti.values()).forEach(System.out::println);

        var letti = esiti.values().stream().filter(e -> e.letto() != null).toList();
        assertThat(letti).as("cedolini letti").isNotEmpty();
        assertThat(letti.stream().filter(e -> !e.incoerenze().isEmpty()).map(Esito::numero).toList())
                .as("cedolini letti con controlli di coerenza non superati")
                .isEmpty();
    }

    /** Misura un file; null se e' protetto e nessuna password nota lo apre (si riprova a fine giro). */
    private Esito misura(int numero, byte[] contenuto, String password, Set<String> codiciFiscali) {
        LettorePdf.DocumentoPdf documento;
        try {
            documento = lettorePdf.apri(contenuto, password, () -> codiciFiscali);
        } catch (PdfNonLeggibileException e) {
            if (e.causa() == PdfNonLeggibileException.Causa.PROTETTO_DA_PASSWORD && codiciFiscali.isEmpty()) {
                return null;
            }
            return new Esito(numero, "-", "ERRORE " + e.causa(), false, null, 0, List.of());
        }
        try {
            var lettura = lettore.leggi(documento.contenutoPerEstrazione());
            var risultato = lettore.estrai(documento.contenutoPerEstrazione());
            var c = lettura.cedolino();
            return new Esito(numero, lettura.layout().id(), "OK", documento.protetto(), c,
                    risultato.avvisi().size(), incoerenze(c));
        } catch (EstrazioneCedolinoException e) {
            return new Esito(numero, "-", "ERRORE " + e.motivo(), documento.protetto(), null, 0, List.of());
        } catch (RuntimeException e) {
            return new Esito(numero, "-", "ERRORE " + e.getClass().getSimpleName(), documento.protetto(), null, 0,
                    List.of());
        }
    }

    /** Controlli di coerenza del singolo cedolino (nomi dei controlli, mai valori). */
    private static List<String> incoerenze(CedolinoLetto c) {
        var incoerenze = new ArrayList<String>();
        if (!nettoCoerente(c)) {
            incoerenze.add("netto");
        }
        if (!c.sommeCoerenti()) {
            incoerenze.add("somme");
        }
        if (!rateiCoerenti(c)) {
            incoerenze.add("ratei");
        }
        if (Boolean.FALSE.equals(c.tfrCoerente)) {
            incoerenze.add("tfr");
        }
        return incoerenze;
    }

    private static boolean nettoCoerente(CedolinoLetto c) {
        if (c.totaleCompetenze == null || c.totaleTrattenute == null || c.netto == null) {
            return false;
        }
        var atteso = c.totaleCompetenze.subtract(c.totaleTrattenute)
                .add(c.arrotondamento == null ? BigDecimal.ZERO : c.arrotondamento);
        return atteso.subtract(c.netto).abs().compareTo(CENTESIMO) <= 0;
    }

    private static boolean rateiCoerenti(CedolinoLetto c) {
        return c.ratei.stream().allMatch(r -> r.residuoAnnoPrecedente().add(r.maturato()).subtract(r.goduto())
                .subtract(r.saldo()).abs().compareTo(CENTESIMO) <= 0);
    }

    private static void stampa(Esito e) {
        if (e.letto() == null) {
            System.out.printf("#%02d %-14s %s%s%n", e.numero(), e.layout(), e.esito(), e.protetto() ? " protetto" : "");
            return;
        }
        var c = e.letto();
        System.out.printf("#%02d %-14s OK tipo=%-13s campi=%d nonTrovati=%d voci=%d ratei=%d avvisi=%d "
                        + "netto=%s somme=%s ratei=%s tfr=%s%s%n",
                e.numero(), e.layout(), c.tipo, c.campiValorizzati(), c.campiNonTrovati().size(), c.voci.size(),
                c.ratei.size(), e.avvisi(), ok(nettoCoerente(c)), ok(c.sommeCoerenti()), ok(rateiCoerenti(c)),
                c.tfrCoerente == null ? "-" : ok(c.tfrCoerente), e.protetto() ? " protetto" : "");
    }

    private static String ok(boolean ok) {
        return ok ? "ok" : "KO";
    }

    /** Tabella aggregata per layout ed esito: solo conteggi. */
    private static List<String> riepilogo(Collection<Esito> esiti) {
        var righe = new ArrayList<String>();
        righe.add("--- riepilogo (" + esiti.size() + " PDF)");
        var perEsito = new TreeMap<String, Integer>();
        esiti.forEach(e -> perEsito.merge(e.layout() + " " + e.esito() + (e.protetto() ? " protetto" : ""), 1,
                Integer::sum));
        perEsito.forEach((k, v) -> righe.add(String.format("%-50s %3d", k, v)));
        var perLayout = new TreeMap<String, List<Esito>>();
        esiti.stream().filter(e -> e.letto() != null)
                .forEach(e -> perLayout.computeIfAbsent(e.layout(), k -> new ArrayList<>()).add(e));
        perLayout.forEach((layout, lista) -> righe.add(String.format(
                "%-14s letti=%d coerenti=%d campi(min/max)=%d/%d nonTrovati(max)=%d avvisi(tot)=%d", layout,
                lista.size(), lista.stream().filter(e -> e.incoerenze().isEmpty()).count(),
                lista.stream().mapToInt(e -> e.letto().campiValorizzati()).min().orElse(0),
                lista.stream().mapToInt(e -> e.letto().campiValorizzati()).max().orElse(0),
                lista.stream().mapToInt(e -> e.letto().campiNonTrovati().size()).max().orElse(0),
                lista.stream().mapToInt(Esito::avvisi).sum())));
        var nonTrovati = new TreeMap<String, Integer>();
        esiti.stream().filter(e -> e.letto() != null)
                .forEach(e -> e.letto().campiNonTrovati().forEach(campo -> nonTrovati.merge(campo, 1, Integer::sum)));
        nonTrovati.forEach((campo, n) -> righe.add(String.format("campo chiave non trovato: %-40s %3d", campo, n)));
        return righe;
    }

    /**
     * Confronta i cedolini ordinari di mesi consecutivi dello stesso rapporto (stesso dipendente e stessa azienda,
     * tenuti solo in memoria): nello stesso anno progressivi e quota TFR dell'anno non calano e il fondo TFR al 31/12
     * resta uguale; al cambio d'anno il fondo TFR non cala.
     */
    private static List<String> confrontaMesiConsecutivi(Collection<Esito> esiti) {
        var rapporti = new LinkedHashMap<String, Map<YearMonth, CedolinoLetto>>();
        for (var e : esiti) {
            var c = e.letto();
            if (c == null || c.tipo != TipoCedolinoEstratto.ORDINARIA || c.periodo == null) {
                continue;
            }
            var azienda = Objects.requireNonNullElse(c.codiceFiscaleAzienda,
                    Objects.requireNonNullElse(c.posizioneInps, Objects.requireNonNullElse(c.ragioneSociale, "")));
            rapporti.computeIfAbsent(c.codiceFiscale + "|" + azienda, k -> new TreeMap<>())
                    .putIfAbsent(c.periodo, c);
        }
        var progressivi = new Conteggio();
        var quotaTfr = new Conteggio();
        var fondoNellAnno = new Conteggio();
        var fondoCambioAnno = new Conteggio();
        for (var mesi : rapporti.values()) {
            var ordinati = mesi.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
            for (int i = 1; i < ordinati.size(); i++) {
                var a = ordinati.get(i - 1);
                var b = ordinati.get(i);
                if (!a.getKey().plusMonths(1).equals(b.getKey())) {
                    continue;
                }
                var x = a.getValue();
                var y = b.getValue();
                if (a.getKey().getYear() == b.getKey().getYear()) {
                    progressivi.conta(nonCala(x.imponibilePrevidenziale, y.imponibilePrevidenziale)
                            .and(nonCala(x.imponibileIrpef, y.imponibileIrpef)));
                    quotaTfr.conta(nonCala(x.tfrQuotaAnno, y.tfrQuotaAnno));
                    fondoNellAnno.conta(uguale(x.tfrFondo, y.tfrFondo));
                } else {
                    fondoCambioAnno.conta(nonCala(x.tfrFondo, y.tfrFondo));
                }
            }
        }
        return List.of(
                "--- mesi consecutivi (" + rapporti.size() + " rapporti)",
                "progressivi annui non decrescenti nell'anno: " + progressivi,
                "quota TFR dell'anno non decrescente:        " + quotaTfr,
                "fondo TFR al 31/12 uguale nell'anno:        " + fondoNellAnno,
                "fondo TFR non decrescente al cambio d'anno: " + fondoCambioAnno);
    }

    /** Esito di un confronto: vero, falso o non confrontabile (un valore assente). */
    private enum Confronto {
        SI, NO, ND;

        Confronto and(Confronto altro) {
            if (this == NO || altro == NO) {
                return NO;
            }
            return this == ND && altro == ND ? ND : SI;
        }
    }

    private static Confronto nonCala(BigDecimal prima, BigDecimal dopo) {
        if (prima == null || dopo == null) {
            return Confronto.ND;
        }
        return dopo.compareTo(prima) >= 0 ? Confronto.SI : Confronto.NO;
    }

    private static Confronto uguale(BigDecimal prima, BigDecimal dopo) {
        if (prima == null || dopo == null) {
            return Confronto.ND;
        }
        return dopo.compareTo(prima) == 0 ? Confronto.SI : Confronto.NO;
    }

    private static final class Conteggio {
        private int confrontate;
        private int superate;
        private int nonConfrontabili;

        void conta(Confronto c) {
            switch (c) {
                case SI -> {
                    confrontate++;
                    superate++;
                }
                case NO -> confrontate++;
                case ND -> nonConfrontabili++;
            }
        }

        @Override
        public String toString() {
            return superate + "/" + confrontate + " coppie" + (nonConfrontabili > 0
                    ? " (" + nonConfrontabili + " non confrontabili: valore non stampato)" : "");
        }
    }
}
