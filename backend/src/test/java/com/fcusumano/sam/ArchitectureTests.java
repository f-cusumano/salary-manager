package com.fcusumano.sam;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.shared.db.jooq.Tables;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Regole architetturali verificate con ArchUnit sul codice di produzione.
 */
class ArchitectureTests {

    private static final String BASE = "com.fcusumano.sam";
    private static final String JOOQ_TABLES = BASE + ".shared.db.jooq.tables.";

    /**
     * Tabelle (nome della classe jOOQ generata) di proprieta' esclusiva di ciascun modulo.
     * Una nuova tabella va aggiunta qui, nel modulo che la possiede.
     */
    static final Map<String, Set<String>> TABELLE_PER_MODULO = Map.of(
            "shared", Set.of("Workspace"),
            "anagrafica", Set.of("Persona", "Azienda", "RapportoLavoro"),
            "ccnl", Set.of("Ccnl"),
            "cedolini", Set.of("Cedolino", "CedolinoPdf", "CedolinoVoce", "CedolinoElementoRetributivo",
                    "CedolinoRateo", "CedolinoTfr", "CedolinoDati", "Caricamento", "CaricamentoPdf",
                    "CaricamentoAttribuzione"),
            "calcoli", Set.of("RiepilogoRapporto", "RiepilogoRateo", "IndicatoreMensilita",
                    "RiepilogoLiquidazioneVoce"),
            "segnalazioni", Set.of("Segnalazione"));

    private static final JavaClasses CLASSI = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE);

    @Test
    void ilDominioNonDipendeDaFrameworkEdSdk() {
        noClasses().that().resideInAPackage(BASE + "..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "org.jooq..", "com.anthropic..")
                .because("il dominio e' Java puro (architettura esagonale)")
                .check(CLASSI);
    }

    @Test
    void jooqSoloNegliAdapterDiPersistenzaENellaConfigurazione() {
        noClasses().that().resideOutsideOfPackages(BASE + "..adapter.persistence..", BASE + ".shared.db..", BASE + "..config..")
                .should().dependOnClassesThat().resideInAPackage("org.jooq..")
                .because("l'accesso ai dati passa dagli adapter di persistenza")
                .check(CLASSI);
    }

    @Test
    void sdkAnthropicSoloNellAdapterIa() {
        noClasses().that().resideOutsideOfPackage(BASE + ".cedolini.adapter.ai..")
                .should().dependOnClassesThat().resideInAPackage("com.anthropic..")
                .because("l'IA e' dietro la porta LettoreCedolini")
                .check(CLASSI);
    }

    @Test
    void pdfBoxSoloNegliAdapterDeiPdf() {
        noClasses().that().resideOutsideOfPackages(BASE + ".cedolini.adapter.pdf..", BASE + ".cedolini.adapter.fixture..",
                        BASE + ".cedolini.adapter.locale..")
                .should().dependOnClassesThat().resideInAPackage("org.apache.pdfbox..")
                .because("la decifratura dei PDF e' dietro la porta LettorePdf, la lettura del testo dietro LettoreCedolini")
                .check(CLASSI);
    }

    @Test
    void ogniModuloUsaSoloLeProprieTabelle() {
        var tutte = TABELLE_PER_MODULO.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
        TABELLE_PER_MODULO.forEach((modulo, proprie) -> {
            var vietate = new HashSet<String>();
            tutte.stream().filter(t -> !proprie.contains(t)).forEach(t -> {
                vietate.add(JOOQ_TABLES + t);
                vietate.add(JOOQ_TABLES + "records." + t + "Record");
            });
            noClasses().that().resideInAPackage(BASE + "." + modulo + "..")
                    .and().resideOutsideOfPackage(BASE + ".shared.db.jooq..")
                    .should().dependOnClassesThat(DescribedPredicate.describe(
                            "sono tabelle jOOQ di altri moduli", (JavaClass c) -> vietate.contains(c.getName())))
                    .because("un modulo non accede alle tabelle di altri moduli, ma alle loro API")
                    .allowEmptyShould(true)
                    .check(CLASSI);
        });
    }

    @Test
    void ogniTabellaHaUnModuloProprietario() {
        var generate = Arrays.stream(Tables.class.getFields())
                .filter(f -> Modifier.isStatic(f.getModifiers()))
                .map(f -> f.getType().getSimpleName())
                .collect(Collectors.toSet());
        var assegnate = TABELLE_PER_MODULO.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
        assertThat(assegnate).containsExactlyInAnyOrderElementsOf(generate);
    }
}
