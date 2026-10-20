package com.fcusumano.sam.cedolini.adapter.cifratura;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Chiave di cifratura delle password ricordate dei PDF ({@code salary-manager.password-pdf.*}).
 *
 * @param chiave     chiave AES-256 in base64 (32 byte); da impostare solo con variabile d'ambiente o nel file .env
 *                   ({@code SAM_PASSWORD_PDF_CHIAVE}). Se vuota si usa il file della chiave
 * @param fileChiave file con la chiave in base64, creato con una chiave casuale al primo uso se non esiste (di default
 *                   accanto al database)
 */
@ConfigurationProperties("salary-manager.password-pdf")
public record CifraturaProperties(String chiave, Path fileChiave) {
}
