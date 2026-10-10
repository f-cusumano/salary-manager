package com.fcusumano.sam.shared.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;
import static org.springframework.core.env.StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ByteArrayResource;

/**
 * {@link VariabiliAmbiente}: la chiave si legge dalla variabile d'ambiente non vuota, poi dai file {@code .env}; mai da
 * {@code application*.yml}, system property o argomenti. Ambienti costruiti a mano, indipendenti dalla macchina.
 */
class VariabiliAmbienteTest {

    private static final String CHIAVE = "ANTHROPIC_API_KEY";

    @Test
    void laVariabileDAmbienteHaLaPrecedenzaSulFileEnv() throws IOException {
        var ambiente = ambiente(Map.of(CHIAVE, "sk-ant-da-ambiente"), fileEnv(".env", CHIAVE + "=sk-ant-da-file"));

        assertThat(VariabiliAmbiente.leggi(ambiente, CHIAVE)).contains("sk-ant-da-ambiente");
    }

    @Test
    void unaVariabileDAmbienteVuotaNonNascondeIlFileEnv() throws IOException {
        var fileEnv = fileEnv(".env", CHIAVE + "=sk-ant-da-file");

        assertThat(VariabiliAmbiente.leggi(ambiente(Map.of(CHIAVE, ""), fileEnv), CHIAVE)).contains("sk-ant-da-file");
        assertThat(VariabiliAmbiente.leggi(ambiente(Map.of(CHIAVE, "  "), fileEnv), CHIAVE)).contains("sk-ant-da-file");
        assertThat(VariabiliAmbiente.leggi(ambiente(Map.of(), fileEnv), CHIAVE)).contains("sk-ant-da-file");
    }

    @Test
    void applicationYmlSystemPropertyEArgomentiSonoIgnorati() throws IOException {
        var yml = new YamlPropertySourceLoader().load("Config resource 'class path resource [application.yml]'",
                new ByteArrayResource((CHIAVE + ": sk-ant-da-yml\n").getBytes(UTF_8))).getFirst();
        var ambiente = ambiente(Map.of(),
                new MapPropertySource("commandLineArgs", Map.of(CHIAVE, "sk-ant-da-argomento")),
                new MapPropertySource("Inlined Test Properties", Map.of(CHIAVE, "sk-ant-da-test")),
                yml);
        ambiente.getPropertySources().replace(SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
                new MapPropertySource(SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, Map.of(CHIAVE, "sk-ant-da-system-property")));

        assertThat(ambiente.getProperty(CHIAVE)).isEqualTo("sk-ant-da-system-property");
        assertThat(VariabiliAmbiente.leggi(ambiente, CHIAVE)).isEmpty();
    }

    @Test
    void laVarianteDelProfiloPrecedeIlFileBaseSeNonEVuota() throws IOException {
        var base = fileEnv(".env", CHIAVE + "=sk-ant-da-file\nSAM_SAMPLES_PASSWORD=password-inventata");

        assertThat(VariabiliAmbiente.leggi(ambiente(Map.of(), fileEnv(".env-test", CHIAVE + "=sk-ant-da-profilo"), base),
                CHIAVE)).contains("sk-ant-da-profilo");
        assertThat(VariabiliAmbiente.leggi(ambiente(Map.of(), fileEnv(".env-test", CHIAVE + "=\nALTRA=x"), base),
                CHIAVE)).contains("sk-ant-da-file");
        assertThat(VariabiliAmbiente.leggi(ambiente(Map.of(), fileEnv(".env-test", CHIAVE + "=x"), base),
                "SAM_SAMPLES_PASSWORD")).contains("password-inventata");
    }

    @Test
    void ilValoreArrivaComeScrittoSenzaRisolvereSegnaposto() throws IOException {
        var ambiente = ambiente(Map.of(), fileEnv(".env", "SAM_SAMPLES_PASSWORD='pa${ss}word'"));

        assertThat(VariabiliAmbiente.leggi(ambiente, "SAM_SAMPLES_PASSWORD")).contains("pa${ss}word");
        assertThat(ambiente.getProperty("SAM_SAMPLES_PASSWORD")).isEqualTo("pa${ss}word");
    }

    @Test
    void ilValoreEQuelloDellaSintassiDiCompose() throws IOException {
        var contenuto = "SAM_SAMPLES_PASSWORD=Pa$$w0rd${SAM_PROVA_NON_DEFINITA:-}\n"
                + CHIAVE + "=\"sk-ant-\\${CHIAVE}\"";
        var ambiente = ambiente(Map.of(), fileEnv(".env", contenuto));

        assertThat(VariabiliAmbiente.leggi(ambiente, "SAM_SAMPLES_PASSWORD")).contains("Pa$w0rd");
        assertThat(VariabiliAmbiente.leggi(ambiente, CHIAVE)).contains("sk-ant-${CHIAVE}");
    }

    @Test
    void senzaValoriLaVariabileEAssente() throws IOException {
        assertThat(VariabiliAmbiente.leggi(ambiente(Map.of()), CHIAVE)).isEmpty();
        assertThat(VariabiliAmbiente.leggi(ambiente(Map.of(), fileEnv(".env", "SAM_AI_MODEL=x")), CHIAVE)).isEmpty();
    }

    /**
     * Ambiente con le sole variabili d'ambiente indicate, senza system property, e le altre sorgenti in coda
     * nell'ordine dato (come le sorgenti della configurazione dopo quelle di sistema).
     */
    private static ConfigurableEnvironment ambiente(Map<String, Object> variabili, PropertySource<?>... altre) {
        var ambiente = new StandardEnvironment();
        var sorgenti = ambiente.getPropertySources();
        sorgenti.replace(SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
                new MapPropertySource(SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, Map.of()));
        sorgenti.replace(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variabili));
        for (var sorgente : altre) {
            sorgenti.addLast(sorgente);
        }
        return ambiente;
    }

    private static PropertySource<?> fileEnv(String file, String contenuto) throws IOException {
        return new CaricatoreFileEnv().load(
                        "Config resource 'file [../" + file + "]' via location 'optional:file:../.env[.env]'",
                        new ByteArrayResource(contenuto.getBytes(UTF_8)))
                .getFirst();
    }
}
