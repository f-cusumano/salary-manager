package com.fcusumano.sam.shared.config;

import java.util.Optional;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * Lettura dei segreti che non sono property applicative (chiave API Anthropic, password dei campioni per i test live)
 * solo dalle fonti previste: la variabile d'ambiente vera, se non vuota, poi i file {@code .env} importati (sorgenti
 * di {@link CaricatoreFileEnv}, nell'ordine di precedenza dell'ambiente: la variante di un profilo attivo prima del
 * file base). Un valore vuoto non nasconde le fonti successive.
 * <p>
 * A differenza di {@code Environment.getProperty}, non consulta {@code application*.yml}, system property, argomenti
 * della riga di comando o proprieta' dei test: un segreto scritto per errore in un file versionato non viene usato.
 * Il valore e' restituito cosi' com'e', senza risolvere eventuali segnaposto {@code ${...}} (dal {@code .env}, quello
 * risultante dalla sintassi di Compose, senza l'escape che {@link CaricatoreFileEnv} aggiunge per Spring).
 */
public final class VariabiliAmbiente {

    private VariabiliAmbiente() {
    }

    /** Valore non vuoto della variabile {@code nome}, dall'ambiente o da un file {@code .env}. */
    public static Optional<String> leggi(ConfigurableEnvironment ambiente, String nome) {
        var sorgenti = ambiente.getPropertySources();
        return valore(sorgenti.get(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME), nome)
                .or(() -> sorgenti.stream()
                        .filter(CaricatoreFileEnv.SorgenteFileEnv.class::isInstance)
                        .map(CaricatoreFileEnv.SorgenteFileEnv.class::cast)
                        .flatMap(s -> nonVuoto(s.letterale(nome)).stream())
                        .findFirst());
    }

    private static Optional<String> valore(PropertySource<?> sorgente, String nome) {
        if (sorgente == null) {
            return Optional.empty();
        }
        return nonVuoto(sorgente.getProperty(nome));
    }

    private static Optional<String> nonVuoto(Object valore) {
        return Optional.ofNullable(valore).map(Object::toString).filter(v -> !v.isBlank());
    }
}
