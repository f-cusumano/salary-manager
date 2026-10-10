package com.fcusumano.sam.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.SQLException;
import liquibase.integration.spring.SpringLiquibase;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Regressione: con l'URL JDBC di produzione ({@code application.yml}) il database H2 su file deve sopravvivere ai
 * riavvii. Con l'opzione {@code DATABASE_TO_LOWER=TRUE} Liquibase non riconosceva la propria tabella
 * {@code databasechangelog} e il secondo avvio falliva ("Table databasechangelog already exists").
 * Ogni "avvio" applica il changelog master con lo stesso componente usato da Spring Boot.
 */
class RiavvioDatabaseIntegrationTest {

    private static final String SEGNAPOSTO_PERCORSO = "${salary-manager.database.path}";

    @TempDir
    Path cartella;

    @Test
    void ilChangelogSiApplicaPiuVolteSulloStessoFileEIDatiRestano() throws Exception {
        var dataSource = dataSource(urlDiProduzione());

        aggiorna(dataSource);
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO workspace (codice, descrizione) VALUES ('riavvio', 'Workspace di prova')");

        aggiorna(dataSource);
        aggiorna(dataSource);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workspace WHERE codice = 'riavvio'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM databasechangelog WHERE id = '300-cedolino'",
                Integer.class)).isEqualTo(1);
    }

    private String urlDiProduzione() throws Exception {
        var sorgenti = new YamlPropertySourceLoader().load("application",
                new ClassPathResource("application.yml"));
        var url = sorgenti.stream()
                .map(s -> s.getProperty("spring.datasource.url"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(url).contains(SEGNAPOSTO_PERCORSO);
        return url.replace(SEGNAPOSTO_PERCORSO, cartella.resolve("salary-manager").toString());
    }

    private static JdbcDataSource dataSource(String url) {
        var dataSource = new JdbcDataSource();
        dataSource.setURL(url);
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    /** Un avvio dell'applicazione dal punto di vista dello schema: Liquibase update del changelog master. */
    private static void aggiorna(JdbcDataSource dataSource) throws Exception {
        var liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();
        chiudi(dataSource);
    }

    /** Chiude il database su file come allo spegnimento dell'applicazione. */
    private static void chiudi(JdbcDataSource dataSource) throws SQLException {
        try (var connessione = dataSource.getConnection(); var istruzione = connessione.createStatement()) {
            istruzione.execute("SHUTDOWN");
        }
    }
}
