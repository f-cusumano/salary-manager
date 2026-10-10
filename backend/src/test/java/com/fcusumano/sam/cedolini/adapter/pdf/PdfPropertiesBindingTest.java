package com.fcusumano.sam.cedolini.adapter.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Binding di {@code salary-manager.pdf.password-candidate} da variabile d'ambiente, come avviene in Docker Compose:
 * vale la variabile del segnaposto di {@code application.yml} ({@code SAM_PDF_PASSWORD_CANDIDATE}) e la forma
 * "relaxed" senza trattini ({@code SALARYMANAGER_PDF_PASSWORDCANDIDATE}); con i trattini resi come underscore la
 * variabile non corrisponde alla proprieta' e viene ignorata.
 */
class PdfPropertiesBindingTest {

    @Test
    void laVariabileDelSegnapostoPassaLePassword() throws IOException {
        assertThat(conVariabili(Map.of("SAM_PDF_PASSWORD_CANDIDATE", "prova-uno,prova-due")).passwordCandidate())
                .containsExactly("prova-uno", "prova-due");
    }

    @Test
    void laFormaRelaxedSenzaTrattiniPassaLePassword() throws IOException {
        assertThat(conVariabili(Map.of("SALARYMANAGER_PDF_PASSWORDCANDIDATE", "prova-uno,prova-due"))
                .passwordCandidate())
                .containsExactly("prova-uno", "prova-due");
    }

    @Test
    void conITrattiniComeUnderscoreLaVariabileEIgnorata() throws IOException {
        assertThat(conVariabili(Map.of("SALARY_MANAGER_PDF_PASSWORDCANDIDATE", "prova-uno")).passwordCandidate())
                .isEmpty();
    }

    @Test
    void senzaVariabiliNonCiSonoPasswordConfigurate() throws IOException {
        var properties = conVariabili(Map.of());

        assertThat(properties.passwordCandidate()).isEmpty();
        assertThat(properties.pagineMassime()).isEqualTo(100);
    }

    /** Ambiente con application.yml e le sole variabili indicate, poi binding come all'avvio dell'applicazione. */
    private static PdfProperties conVariabili(Map<String, Object> variabili) throws IOException {
        var ambiente = new StandardEnvironment();
        var sorgenti = ambiente.getPropertySources();
        sorgenti.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        sorgenti.replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variabili));
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                .forEach(sorgenti::addLast);
        return Binder.get(ambiente).bindOrCreate("salary-manager.pdf", PdfProperties.class);
    }
}
