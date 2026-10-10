package com.fcusumano.sam.shared.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import org.springframework.boot.env.PropertySourceInfo;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginLookup;
import org.springframework.boot.origin.TextResourceOrigin;
import org.springframework.boot.origin.TextResourceOrigin.Location;
import org.springframework.core.env.AbstractPropertyResolver;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.util.PropertyPlaceholderHelper;

/**
 * Legge un file {@code .env} con la sintassi di Docker Compose, cosi' lo stesso file vale per Compose e per il backend
 * avviato con {@code mvnw}. Registrato in {@code META-INF/spring.factories} per l'estensione {@code env}:
 * {@code application.yml} lo usa con {@code spring.config.import: optional:file:${SAM_ENV_FILE:../.env}[.env]}.
 * <p>
 * Formato: testo UTF-8 (BOM tolto), righe chiuse da LF o CRLF. Le righe vuote e quelle che iniziano con {@code #} sono
 * ignorate; le altre hanno la forma {@code NOME=valore}, con il prefisso {@code export } facoltativo e gli spazi
 * attorno al nome e al segno {@code =} ignorati. Il nome rispetta {@code [A-Za-z_][A-Za-z0-9_.-]*}. Il valore:
 * <ul>
 *   <li>senza virgolette: la {@code \} non e' un carattere di escape (percorsi Windows e password restano intatti);
 *       sono tolti gli spazi finali e il commento in linea, che inizia con uno spazio seguito da {@code #} (un
 *       {@code #} all'inizio del valore o dopo una tabulazione ne fa parte, come in Compose); poi si sostituiscono
 *       le variabili ({@link InterpolazioneCompose#sostituisci});</li>
 *   <li>tra apici singoli: letterale, compresi {@code \}, {@code $}, {@code #} e spazi; solo {@code \'} vale come
 *       apice (quindi il valore non puo' finire con una {@code \});</li>
 *   <li>tra virgolette doppie: gli escape di Compose ({@link InterpolazioneCompose#espandiEscape}), poi la
 *       sostituzione delle variabili.</li>
 * </ul>
 * La sostituzione cerca le variabili d'ambiente vere, poi quelle delle righe precedenti dello stesso file; a
 * differenza di Compose, una variabile non definita blocca l'avvio invece di diventare un testo vuoto. Dopo le
 * virgolette o gli apici di chiusura puo' esserci solo un commento; un valore tra virgolette non continua sulla riga
 * successiva. Se un nome compare piu' volte vale l'ultimo. Un valore vuoto ({@code NOME=}, {@code NOME=''}) equivale a
 * una variabile non impostata, come per i segnaposto {@code ${NOME:-default}} di {@code docker-compose.yml}: valgono
 * i default di {@code application.yml}. Una riga non valida o un file non UTF-8 bloccano l'avvio con un errore che
 * indica file e numero di riga, mai il contenuto della riga (puo' contenere la chiave API).
 * <p>
 * Spring risolve i segnaposto {@code ${...}} anche nei valori che entrano in un segnaposto di {@code application.yml};
 * perche' il valore arrivi com'e' (come con Compose), le sorgenti create lo conservano con la {@code \} di escape di
 * Spring davanti a ogni <code>${</code>. Hanno nome {@value #PREFISSO_SORGENTE} seguito dalla descrizione di Spring
 * Boot e conservano per ogni valore l'origine (file, riga e colonna), riportata negli errori di binding. Come per ogni
 * import, Spring Boot legge anche le varianti dei profili attivi ({@code .env-<profilo>}, oppure {@code .env-default}
 * senza profili attivi), con precedenza sul file base. Avendo l'estensione {@code env} un caricatore, Spring Boot
 * cercherebbe anche {@code application.env} nelle posizioni standard della configurazione: quei file sono ignorati, si
 * leggono solo quelli importati con l'indicazione {@code [.env]}.
 */
public class CaricatoreFileEnv implements PropertySourceLoader {

    /** Prefisso del nome delle sorgenti lette da un file {@code .env} (vedi {@link VariabiliAmbiente}). */
    public static final String PREFISSO_SORGENTE = "dotenv: ";

    /**
     * Fine del nome che Spring Boot assegna alle risorse di un import con l'indicazione {@code [.env]}
     * ({@code Config resource '...' via location 'optional:file:../.env[.env]'}).
     */
    private static final String FINE_NOME_IMPORT = "[.env]'";
    private static final Pattern NOME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");
    private static final Pattern EXPORT = Pattern.compile("export[ \t]+");

    /** Variabili d'ambiente vere, per la sostituzione: {@code null} se non definita. */
    private final UnaryOperator<String> ambiente;

    public CaricatoreFileEnv() {
        this(System::getenv);
    }

    CaricatoreFileEnv(UnaryOperator<String> ambiente) {
        this.ambiente = ambiente;
    }

    @Override
    public String[] getFileExtensions() {
        return new String[] {"env"};
    }

    @Override
    public List<PropertySource<?>> load(String name, Resource resource) throws IOException {
        if (!name.endsWith(FINE_NOME_IMPORT)) {
            return List.of();
        }
        byte[] contenuto;
        try (InputStream in = resource.getInputStream()) {
            contenuto = in.readAllBytes();
        }
        var lettura = new Lettura(resource, ambiente);
        lettura.leggi(contenuto);
        if (lettura.valori.isEmpty()) {
            return List.of();
        }
        return List.of(new SorgenteFileEnv(PREFISSO_SORGENTE + name, lettura));
    }

    /**
     * Sorgente di un file {@code .env}: i valori per Spring, con <code>${</code> protetto dall'escape, l'origine di
     * ciascuno (file, riga e colonna) e i valori cosi' come risultano dal file, per {@link VariabiliAmbiente}.
     */
    static final class SorgenteFileEnv extends MapPropertySource implements OriginLookup<String>, PropertySourceInfo {

        private final Map<String, Origin> origini;
        private final Map<String, String> letterali;

        SorgenteFileEnv(String name, Lettura lettura) {
            super(name, Collections.unmodifiableMap(new LinkedHashMap<>(lettura.valori)));
            this.origini = Map.copyOf(lettura.origini);
            this.letterali = Map.copyOf(lettura.letterali);
        }

        @Override
        public Origin getOrigin(String nome) {
            return origini.get(nome);
        }

        @Override
        public boolean isImmutable() {
            return true;
        }

        /** Valore della variabile {@code nome} come risulta dal file, senza l'escape per Spring. */
        String letterale(String nome) {
            return letterali.get(nome);
        }
    }

    /** Lettura di un file: conosce la risorsa per le origini dei valori e per i messaggi d'errore. */
    private static final class Lettura {

        private final Resource resource;
        private final UnaryOperator<String> ambiente;
        /** Variabili gia' lette, anche vuote: servono alla sostituzione nelle righe successive. */
        private final Map<String, String> definite = new HashMap<>();
        final Map<String, Object> valori = new LinkedHashMap<>();
        final Map<String, Origin> origini = new HashMap<>();
        final Map<String, String> letterali = new HashMap<>();

        Lettura(Resource resource, UnaryOperator<String> ambiente) {
            this.resource = resource;
            this.ambiente = ambiente;
        }

        void leggi(byte[] contenuto) {
            if (contenuto.length >= 2 && ((contenuto[0] == (byte) 0xFF && contenuto[1] == (byte) 0xFE)
                    || (contenuto[0] == (byte) 0xFE && contenuto[1] == (byte) 0xFF))) {
                throw errore(1, "il file e' in UTF-16: salvalo con la codifica UTF-8");
            }
            boolean bom = contenuto.length >= 3 && contenuto[0] == (byte) 0xEF && contenuto[1] == (byte) 0xBB
                    && contenuto[2] == (byte) 0xBF;
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            int numero = 0;
            int inizio = bom ? 3 : 0;
            // In UTF-8 il byte '\n' non fa mai parte di un carattere multibyte: si divide in righe, poi si decodifica
            while (inizio <= contenuto.length) {
                int fine = inizio;
                while (fine < contenuto.length && contenuto[fine] != '\n') {
                    fine++;
                }
                numero++;
                String riga;
                try {
                    riga = decoder.decode(ByteBuffer.wrap(contenuto, inizio, fine - inizio)).toString();
                } catch (CharacterCodingException e) {
                    throw errore(numero, "testo non UTF-8: salva il file con la codifica UTF-8");
                }
                if (riga.endsWith("\r")) {
                    riga = riga.substring(0, riga.length() - 1);
                }
                leggiRiga(riga, numero);
                inizio = fine + 1;
            }
        }

        private void leggiRiga(String riga, int numero) {
            int pos = saltaSpazi(riga, 0);
            if (pos == riga.length() || riga.charAt(pos) == '#') {
                return;
            }
            var export = EXPORT.matcher(riga).region(pos, riga.length());
            if (export.lookingAt()) {
                pos = export.end();
            }
            int uguale = riga.indexOf('=', pos);
            if (uguale < 0) {
                throw errore(numero, "manca il segno '=' (forma attesa NOME=valore)");
            }
            var nome = riga.substring(pos, uguale).strip();
            if (!NOME.matcher(nome).matches()) {
                throw errore(numero, "nome di variabile non valido (ammessi lettere ASCII, cifre, '_', '.' e '-', "
                        + "senza spazi e non una cifra come primo carattere)");
            }
            int inizioValore = saltaSpazi(riga, uguale + 1);
            var valore = valore(riga, inizioValore, numero);
            definite.put(nome, valore);
            if (valore.isEmpty()) {
                // come una variabile non impostata: vale il default di application.yml
                valori.remove(nome);
                origini.remove(nome);
                letterali.remove(nome);
                return;
            }
            valori.put(nome, protettoPerSpring(valore, numero));
            origini.put(nome, new TextResourceOrigin(resource, new Location(numero - 1, inizioValore)));
            letterali.put(nome, valore);
        }

        /** Valore della riga che inizia (dopo il segno {@code =} e gli spazi) alla posizione {@code inizio}. */
        private String valore(String riga, int inizio, int numero) {
            if (inizio < riga.length() && riga.charAt(inizio) == '\'') {
                return traVirgolette(riga, inizio, numero);
            }
            if (inizio < riga.length() && riga.charAt(inizio) == '"') {
                return sostituisci(InterpolazioneCompose.espandiEscape(traVirgolette(riga, inizio, numero)), numero);
            }
            int commento = riga.indexOf(" #", inizio);
            return sostituisci(riga.substring(inizio, commento < 0 ? riga.length() : commento).strip(), numero);
        }

        /**
         * Testo tra le virgolette (o gli apici) che si aprono in {@code inizio}, come lo raccoglie Compose: la
         * {@code \} davanti al carattere di chiusura lo rende parte del valore (e sparisce), davanti a ogni altro
         * carattere resta; {@code \\} resta doppia (tra virgolette doppie la riduce poi l'escape).
         */
        private String traVirgolette(String riga, int inizio, int numero) {
            char chiusa = riga.charAt(inizio);
            var testo = new StringBuilder();
            for (int i = inizio + 1; i < riga.length(); i++) {
                char c = riga.charAt(i);
                if (c == chiusa) {
                    verificaCoda(riga, i + 1, numero);
                    return testo.toString();
                }
                if (c == '\\' && i + 1 < riga.length()) {
                    char dopo = riga.charAt(++i);
                    if (dopo != chiusa) {
                        testo.append(c);
                    }
                    testo.append(dopo);
                    continue;
                }
                testo.append(c);
            }
            throw errore(numero, chiusa == '"' ? "virgolette non chiuse sulla stessa riga"
                    : "apice singolo non chiuso sulla stessa riga (\\' vale come apice)");
        }

        /** Sostituzione delle variabili: prima le variabili d'ambiente vere, poi le righe precedenti. */
        private String sostituisci(String testo, int numero) {
            try {
                return InterpolazioneCompose.sostituisci(testo, nome -> {
                    var valore = ambiente.apply(nome);
                    return valore != null ? valore : definite.get(nome);
                });
            } catch (IllegalArgumentException e) {
                throw errore(numero, e.getMessage());
            }
        }

        /**
         * Il valore con la {@code \} di escape dei segnaposto di Spring davanti a ogni <code>${</code> che Spring
         * leggerebbe come segnaposto (seguito dalla sua <code>}</code>). Se il risultato non torna al valore (escape
         * dei segnaposto disattivato, o una forma che Spring legge diversamente) l'avvio si blocca.
         */
        private String protettoPerSpring(String valore, int numero) {
            if (!valore.contains("${")) {
                return valore;
            }
            var protetto = new StringBuilder(valore.length() + 4);
            for (int i = 0; i < valore.length(); i++) {
                if (valore.startsWith("${", i) && chiusoPerSpring(valore, i + 2)) {
                    protetto.append('\\');
                }
                protetto.append(valore.charAt(i));
            }
            String risolto;
            try {
                risolto = new PropertyPlaceholderHelper("${", "}", ":",
                        AbstractPropertyResolver.getDefaultEscapeCharacter(), false)
                        .replacePlaceholders(protetto.toString(), nome -> {
                            throw new IllegalStateException("segnaposto non protetto");
                        });
            } catch (RuntimeException e) {
                risolto = null;
            }
            if (!valore.equals(risolto)) {
                throw errore(numero, "il valore contiene '${' in una forma che Spring leggerebbe come segnaposto");
            }
            return protetto.toString();
        }

        /**
         * Se il segnaposto che inizia prima di {@code da} ha la sua <code>}</code>, contata come Spring: ogni
         * <code>{</code> successiva apre un livello annidato.
         */
        private static boolean chiusoPerSpring(String valore, int da) {
            int annidati = 0;
            for (int i = da; i < valore.length(); i++) {
                char c = valore.charAt(i);
                if (c == '}' && annidati-- == 0) {
                    return true;
                }
                if (c == '{') {
                    annidati++;
                }
            }
            return false;
        }

        /** Dopo le virgolette di chiusura sono ammessi solo spazi e un commento. */
        private void verificaCoda(String riga, int dopoChiusura, int numero) {
            int pos = saltaSpazi(riga, dopoChiusura);
            if (pos < riga.length() && riga.charAt(pos) != '#') {
                throw errore(numero,
                        "testo dopo la chiusura delle virgolette o dell'apice (un commento inizia con '#')");
            }
        }

        private IllegalStateException errore(int numero, String problema) {
            return new IllegalStateException("File .env non valido (" + percorso() + "), riga " + numero + ": "
                    + problema);
        }

        private String percorso() {
            try {
                if (resource.isFile()) {
                    return resource.getFile().toPath().toAbsolutePath().normalize().toString();
                }
            } catch (IOException | RuntimeException e) {
                // si ripiega sulla descrizione della risorsa
            }
            return resource.getDescription();
        }

        private static int saltaSpazi(String riga, int da) {
            int pos = da;
            while (pos < riga.length() && spazio(riga.charAt(pos))) {
                pos++;
            }
            return pos;
        }

        private static boolean spazio(char c) {
            return c == ' ' || c == '\t';
        }
    }
}
