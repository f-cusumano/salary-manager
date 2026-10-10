package com.fcusumano.sam.cedolini.config;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cartella (o cartelle) locale dei cedolini reali di esempio ({@code salary-manager.samples-dir}), esclusa da git.
 * Usata solo dai test esclusi dalla build di default: {@code @Tag("live")} (lettore Claude) e {@code @Tag("campioni")}
 * (misure del lettore locale). Piu' cartelle si separano con il separatore dei percorsi del sistema ({@code :} su
 * Linux e macOS, {@code ;} su Windows).
 */
@ConfigurationProperties("salary-manager")
public record CampioniProperties(@DefaultValue("../samples") String samplesDir) {

    /** Prima cartella dei campioni come {@link Path} (relativa alla cartella di avvio del backend). */
    public Path cartella() {
        return cartelle().getFirst();
    }

    /** Tutte le cartelle dei campioni, nell'ordine indicato. */
    public List<Path> cartelle() {
        var cartelle = Arrays.stream(samplesDir.split(File.pathSeparator))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .map(Path::of)
                .toList();
        return cartelle.isEmpty() ? List.of(Path.of("../samples")) : cartelle;
    }
}
