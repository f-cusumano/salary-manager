package com.fcusumano.sam;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifica dei confini tra moduli (Spring Modulith): nessun ciclo, solo dipendenze dichiarate in
 * {@code allowedDependencies}, nessun accesso ai package interni di altri moduli.
 */
class ModularityTests {

    private static final ApplicationModules MODULI = ApplicationModules.of(SalaryManagerApplication.class);

    @Test
    void confiniTraModuliRispettati() {
        MODULI.verify();
    }

    @Test
    void generaDocumentazioneModuli() {
        // Diagrammi e canvas dei moduli in target/spring-modulith-docs
        new Documenter(MODULI).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml().writeModuleCanvases();
    }
}
