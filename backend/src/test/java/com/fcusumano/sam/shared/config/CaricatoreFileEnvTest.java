package com.fcusumano.sam.shared.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.origin.TextResourceOrigin;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/**
 * Sintassi del file {@code .env} (quella di Docker Compose) letta da {@link CaricatoreFileEnv}. Solo valori inventati.
 */
class CaricatoreFileEnvTest {

    /** Nome che Spring Boot da' alla risorsa importata da application.yml. */
    private static final String NOME_IMPORT =
            "Config resource 'file [../.env]' via location 'optional:file:../.env[.env]'";

    /** Variabili d'ambiente vere viste dalla sostituzione: solo queste, indipendenti dalla macchina. */
    private static final Map<String, String> AMBIENTE = Map.of("SAM_PROVA_AMBIENTE", "da-ambiente");

    private final CaricatoreFileEnv caricatore = new CaricatoreFileEnv(AMBIENTE::get);

    @Test
    void registraLEstensioneEnv() {
        assertThat(caricatore.getFileExtensions()).containsExactly("env");
    }

    @Test
    void laSorgenteHaUnNomeRiconoscibile() throws IOException {
        var sorgenti = caricatore.load(NOME_IMPORT, risorsa("A=1\n"));

        assertThat(sorgenti).singleElement()
                .satisfies(s -> assertThat(s.getName()).isEqualTo(CaricatoreFileEnv.PREFISSO_SORGENTE + NOME_IMPORT));
    }

    @Test
    void bomECrlfNonEntranoNeNomiNeValori() throws IOException {
        var contenuto = concatena(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                "ANTHROPIC_API_KEY=sk-ant-chiave-inventata\r\nSAM_AI_MODEL=modello-di-prova\r\n".getBytes(UTF_8));

        assertThat(valori(contenuto)).containsExactly(
                entry("ANTHROPIC_API_KEY", "sk-ant-chiave-inventata"),
                entry("SAM_AI_MODEL", "modello-di-prova"));
    }

    @Test
    void righeVuoteECommentiSonoIgnorati() throws IOException {
        assertThat(valori("# commento\n\n   \n\t# commento indentato\nA=uno\n#B=due\n"))
                .containsExactly(entry("A", "uno"));
    }

    @Test
    void prefissoExportESpaziAttornoANomeEUguale() throws IOException {
        assertThat(valori("export A=uno\nexport\tB=due\n  C  =  tre\nexport=quattro\n")).containsExactly(
                entry("A", "uno"), entry("B", "due"), entry("C", "tre"), entry("export", "quattro"));
    }

    @Test
    void valoriSenzaVirgolette() throws IOException {
        assertThat(valori("""
                A=valore con spazi interni   \t
                B=valore # commento in linea # altro
                C=valore#non-commento
                D=valore\t#non-commento dopo una tabulazione
                E= #non-commento all'inizio del valore
                F=\tdopo una tabulazione
                G=valore con "virgolette" e 'apici' interni
                H=a=b=c
                """)).containsExactly(
                entry("A", "valore con spazi interni"),
                entry("B", "valore"),
                entry("C", "valore#non-commento"),
                entry("D", "valore\t#non-commento dopo una tabulazione"),
                entry("E", "#non-commento all'inizio del valore"),
                entry("F", "dopo una tabulazione"),
                entry("G", "valore con \"virgolette\" e 'apici' interni"),
                entry("H", "a=b=c"));
    }

    @Test
    void senzaVirgoletteLaBarraRovesciataResta() throws IOException {
        assertThat(valori("""
                SAM_DB_PATH=C:\\Progetti\\Privati\\dati\\sam
                SAM_SAMPLES_DIR=C:\\cedolini\\
                ANTHROPIC_API_KEY=sk-ant-dopo-la-barra
                SAM_PDF_PASSWORD_CANDIDATE=pa\\ss\\nword,\\u00e8
                PERCORSO=C:\\utenti\\prova
                """)).containsExactly(
                entry("SAM_DB_PATH", "C:\\Progetti\\Privati\\dati\\sam"),
                entry("SAM_SAMPLES_DIR", "C:\\cedolini\\"),
                entry("ANTHROPIC_API_KEY", "sk-ant-dopo-la-barra"),
                entry("SAM_PDF_PASSWORD_CANDIDATE", "pa\\ss\\nword,\\u00e8"),
                entry("PERCORSO", "C:\\utenti\\prova"));
    }

    @Test
    void traApiciSingoliIlValoreELetterale() throws IOException {
        assertThat(valori("""
                A='  spazi, # cancelletto e "virgolette"  '
                B='C:\\nuovo\\tab ${ALTRA} $NON_DEFINITA $$' # commento
                C='Pa$sw0rd'
                D  =  'dopo gli spazi'#commento
                E='l\\'apice e la \\\\ doppia'
                """)).containsExactly(
                entry("A", "  spazi, # cancelletto e \"virgolette\"  "),
                entry("B", "C:\\nuovo\\tab ${ALTRA} $NON_DEFINITA $$"),
                entry("C", "Pa$sw0rd"),
                entry("D", "dopo gli spazi"),
                entry("E", "l'apice e la \\\\ doppia"));
    }

    @Test
    void traVirgoletteDoppieValgonoGliEscapeDiCompose() throws IOException {
        assertThat(valori("""
                A="riga1\\nriga2\\tfine"
                B="barra \\\\ e virgolette \\" interne"
                C="C:\\Progetti\\dati" # commento
                D="# non e' un commento "
                E="ritorno\\r a capo, \\a\\b\\f\\v e ottale \\0101"
                F="dollaro \\$NOME e \\$$"
                G="restano: \\c \\' \\x41 \\u00e8 \\0 \\012 \\0400"
                """)).containsExactly(
                entry("A", "riga1\nriga2\tfine"),
                entry("B", "barra \\ e virgolette \" interne"),
                entry("C", "C:\\Progetti\\dati"),
                entry("D", "# non e' un commento "),
                entry("E", "ritorno\r a capo, \u0007\b\f\u000B e ottale A"),
                entry("F", "dollaro $NOME e $$"),
                entry("G", "restano: \\c \\' \\x41 \\u00e8 \\ \\12 \\400"));
    }

    @Test
    void sostituzioneDelleVariabiliComeInCompose() throws IOException {
        assertThat(valori("""
                A=uno
                VUOTA=
                B=a${A}b-$A-$$A-$$$$
                C="${A}/$A" # commento
                D=${NON_DEFINITA:-default} ${VUOTA:-default} ${VUOTA-default} ${NON_DEFINITA-default}
                E=[${A:+alternativo}][${VUOTA:+alternativo}][${VUOTA+alternativo}][${NON_DEFINITA+x}]
                F=${NON_DEFINITA:-${A}-annidato}
                G=${A:-$NON_DEFINITA}
                H=$SAM_PROVA_AMBIENTE
                A=due
                I=$A
                J=resta: $ a$ $1 $-x ${A:?obbligatoria}
                K=C:\\dati\\$$cartella
                """)).containsExactly(
                entry("A", "due"),
                entry("B", "aunob-uno-$A-$$"),
                entry("C", "uno/uno"),
                entry("D", "default default  default"),
                entry("E", "[alternativo][][alternativo][]"),
                entry("F", "uno-annidato"),
                entry("G", "uno"),
                entry("H", "da-ambiente"),
                entry("I", "due"),
                entry("J", "resta: $ a$ $1 $-x due"),
                entry("K", "C:\\dati\\$cartella"));
    }

    @Test
    void leVariabiliDAmbienteVerePrecedonoLeRighePrecedenti() throws IOException {
        assertThat(valori("SAM_PROVA_AMBIENTE=dal-file\nA=${SAM_PROVA_AMBIENTE}\n"))
                .containsEntry("A", "da-ambiente");
    }

    @Test
    void ilCaricatoreDiSpringUsaLeVariabiliDAmbienteDelProcesso() throws IOException {
        var nome = System.getenv().keySet().stream()
                .filter(n -> n.matches("[A-Za-z_][A-Za-z0-9_]*") && !System.getenv(n).isEmpty())
                .findFirst()
                .orElseThrow();

        assertThat(valori(new CaricatoreFileEnv(), ("A=${" + nome + "}\n").getBytes(UTF_8)))
                .containsEntry("A", System.getenv(nome));
    }

    @Test
    void unValoreVuotoEComeUnaVariabileNonImpostata() throws IOException {
        assertThat(valori("VUOTA=\nB=   \nC=''\nD=\"\" # vuota\nE=uno\nE=\nF=$VUOTA\nG=x${VUOTA}y\n"))
                .containsExactly(entry("G", "xy"));
    }

    @Test
    void perSpringIlSegnapostoNelValoreResta() throws IOException {
        var sorgente = caricatore.load(NOME_IMPORT, risorsa("""
                A='utente${SAM_PROVA_X}'
                B='modello${NON_DEFINITA:-def}'
                C=$${A}
                D='C:\\dati\\${A}\\${'
                E='p${a${b}'
                SAM_PROVA_X=zz
                """)).getFirst();
        var ambiente = new StandardEnvironment();
        ambiente.getPropertySources().addFirst(sorgente);
        ambiente.getPropertySources().addFirst(new MapPropertySource("segnaposto", Map.of(
                "a", "${A:x}", "b", "${B:x}", "c", "${C:x}", "d", "${D:x}", "e", "${E:x}")));

        assertThat(ambiente.getProperty("a")).isEqualTo("utente${SAM_PROVA_X}");
        assertThat(ambiente.getProperty("b")).isEqualTo("modello${NON_DEFINITA:-def}");
        assertThat(ambiente.getProperty("c")).isEqualTo("${A}");
        assertThat(ambiente.getProperty("d")).isEqualTo("C:\\dati\\${A}\\${");
        assertThat(ambiente.getProperty("e")).isEqualTo("p${a${b}");
        assertThat(Binder.get(ambiente).bind("a", String.class).get()).isEqualTo("utente${SAM_PROVA_X}");
        assertThat(sorgente.getProperty("A")).isEqualTo("utente\\${SAM_PROVA_X}");
    }

    @Test
    void iCaratteriAccentatiSiLeggonoInUtf8() throws IOException {
        assertThat(valori("SAM_PDF_PASSWORD_CANDIDATE=Però€123\nSAM_DB_PATH=C:/Utenti/Nicolò/sam\nB='àèìòù'\n"))
                .containsExactly(
                        entry("SAM_PDF_PASSWORD_CANDIDATE", "Però€123"),
                        entry("SAM_DB_PATH", "C:/Utenti/Nicolò/sam"),
                        entry("B", "àèìòù"));
    }

    @Test
    void nomeRipetuto() throws IOException {
        assertThat(valori("C=uno\nC=due\nsalary-manager.ai.model=x\n_Z9=y")).containsExactly(
                entry("C", "due"), entry("salary-manager.ai.model", "x"), entry("_Z9", "y"));
    }

    @Test
    void ogniValoreConservaRigaEColonna() throws IOException {
        var sorgente = (CaricatoreFileEnv.SorgenteFileEnv) caricatore.load(NOME_IMPORT,
                risorsa("# commento\nA = uno\n")).getFirst();

        var origine = (TextResourceOrigin) sorgente.getOrigin("A");
        assertThat(origine.getLocation().getLine()).isEqualTo(1);
        assertThat(origine.getLocation().getColumn()).isEqualTo(4);
        assertThat(sorgente.getProperty("A")).isEqualTo("uno");
    }

    @Test
    void unFileSenzaVariabiliNonCreaSorgenti() throws IOException {
        assertThat(caricatore.load(NOME_IMPORT, risorsa(""))).isEmpty();
        assertThat(caricatore.load(NOME_IMPORT, risorsa("# solo commenti\n\n"))).isEmpty();
    }

    @Test
    void iFileApplicationEnvDellePosizioniStandardSonoIgnorati() throws IOException {
        assertThat(caricatore.load("Config resource 'file [application.env]' via location 'optional:file:./'",
                risorsa("ANTHROPIC_API_KEY=sk-ant-chiave-inventata\n"))).isEmpty();
    }

    static Stream<Arguments> righeNonValide() {
        return Stream.of(
                Arguments.of("A=1\nsk-ant-SEGRETO-INCOLLATO\n", 2, "manca il segno '='"),
                Arguments.of("A=1\n\nANTHROPIC_API_KEY sk-ant-SEGRETO\n", 3, "manca il segno '='"),
                Arguments.of("1SEGRETO=x\n", 1, "nome di variabile non valido"),
                Arguments.of("NOME SEGRETO=x\n", 1, "nome di variabile non valido"),
                Arguments.of("=SEGRETO\n", 1, "nome di variabile non valido"),
                Arguments.of("A=\"SEGRETO senza chiusura\n", 1, "virgolette non chiuse"),
                Arguments.of("A=\"SEGRETO con virgolette di escape\\\"\n", 1, "virgolette non chiuse"),
                Arguments.of("A=1\nB='SEGRETO senza chiusura\nC=2\n", 2, "apice singolo non chiuso"),
                Arguments.of("A=\"SEGRETO\"coda\n", 1, "testo dopo la chiusura"),
                Arguments.of("A='SEGRETO' coda\n", 1, "testo dopo la chiusura"),
                Arguments.of("SAM_SAMPLES_DIR='C:\\SEGRETO\\'\n", 1, "apice singolo non chiuso"),
                Arguments.of("A=1\nSAM_PDF_PASSWORD_CANDIDATE=Pa$SEGRETO\n", 2,
                        "riferimento a una variabile non definita: per un '$' letterale scrivi '$$' oppure metti il "
                                + "valore tra apici singoli"),
                Arguments.of("A=\"SEGRETO${NON_DEFINITA}\"\n", 1, "riferimento a una variabile non definita"),
                Arguments.of("A=SEGRETO${A:-$NON_DEFINITA}\n", 1, "riferimento a una variabile non definita"),
                Arguments.of("A=SEGRETO${\n", 1, "sostituzione ${...} non valida"),
                Arguments.of("A=SEGRETO${}\n", 1, "sostituzione ${...} non valida"),
                Arguments.of("A=SEGRETO${1}\n", 1, "sostituzione ${...} non valida"),
                Arguments.of("A=SEGRETO${A:x}\n", 1, "sostituzione ${...} non valida"),
                Arguments.of("A=SEGRETO${NON_DEFINITA:?manca}\n", 1, "variabile obbligatoria"),
                Arguments.of("V=\nA=SEGRETO${V:?manca}\n", 2, "variabile obbligatoria"));
    }

    @ParameterizedTest
    @MethodSource("righeNonValide")
    void unaRigaNonValidaBloccaLAvvioSenzaRiportarneIlContenuto(String testo, int riga, String problema) {
        assertThatThrownBy(() -> valori(testo))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("riga " + riga + ": " + problema)
                .hasMessageNotContaining("SEGRETO");
    }

    @Test
    void unTestoNonUtf8IndicaLaRiga() {
        var contenuto = concatena("A=uno\r\nB=Per".getBytes(UTF_8), new byte[] {(byte) 0xF2},
                "SEGRETO\r\n".getBytes(UTF_8));

        assertThatThrownBy(() -> valori(contenuto))
                .hasMessageContaining("riga 2: testo non UTF-8")
                .hasMessageNotContaining("SEGRETO");
    }

    @Test
    void unFileUtf16VieneRifiutato() {
        var contenuto = "\uFEFFANTHROPIC_API_KEY=sk-ant-SEGRETO\r\n".getBytes(StandardCharsets.UTF_16LE);

        assertThatThrownBy(() -> valori(contenuto))
                .hasMessageContaining("riga 1: il file e' in UTF-16")
                .hasMessageNotContaining("SEGRETO");
    }

    @Test
    void lErroreIndicaIlPercorsoDelFile(@TempDir Path cartella) throws IOException {
        var file = Files.writeString(cartella.resolve(".env"), "A=1\nB=2\nSEGRETO\n");

        assertThatThrownBy(() -> caricatore.load(NOME_IMPORT, new FileSystemResource(file)))
                .hasMessage("File .env non valido (" + file.toAbsolutePath() + "), riga 3: manca il segno '='"
                        + " (forma attesa NOME=valore)");
    }

    private Map<String, Object> valori(String testo) throws IOException {
        return valori(testo.getBytes(UTF_8));
    }

    private Map<String, Object> valori(byte[] contenuto) throws IOException {
        return valori(caricatore, contenuto);
    }

    /** Valori come risultano dal file (senza l'escape per Spring), nell'ordine del file. */
    private static Map<String, Object> valori(CaricatoreFileEnv caricatore, byte[] contenuto) throws IOException {
        var valori = new LinkedHashMap<String, Object>();
        for (var sorgente : caricatore.load(NOME_IMPORT, new ByteArrayResource(contenuto))) {
            var fileEnv = (CaricatoreFileEnv.SorgenteFileEnv) sorgente;
            for (var nome : fileEnv.getPropertyNames()) {
                valori.put(nome, fileEnv.letterale(nome));
            }
        }
        return valori;
    }

    private static Resource risorsa(String testo) {
        return new ByteArrayResource(testo.getBytes(UTF_8));
    }

    private static byte[] concatena(byte[]... parti) {
        var risultato = new ByteArrayOutputStream();
        for (var parte : parti) {
            risultato.writeBytes(parte);
        }
        return risultato.toByteArray();
    }
}
