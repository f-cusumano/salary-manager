package com.fcusumano.sam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulithic;

/**
 * Salary Manager: monolite modulare (Spring Modulith). Ogni sotto-package diretto e' un modulo applicativo.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@Modulithic(systemName = "Salary Manager", sharedModules = "shared")
public class SalaryManagerApplication {

    public static void main(String[] args) {
        // Nessuna telemetria verso servizi esterni (Liquibase analytics)
        if (System.getProperty("liquibase.analytics.enabled") == null) {
            System.setProperty("liquibase.analytics.enabled", "false");
        }
        SpringApplication.run(SalaryManagerApplication.class, args);
    }
}
