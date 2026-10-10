package com.fcusumano.sam.cedolini.adapter.pdf;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DataSizeUnit;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;

/**
 * Lettura dei PDF caricati ({@code salary-manager.pdf.*}).
 *
 * @param passwordCandidate          password provate (dopo quella indicata dall'utente) per aprire i PDF protetti;
 *                                   da impostare con variabile d'ambiente ({@code SAM_PDF_PASSWORD_CANDIDATE}, separate
 *                                   da virgola), mai in file versionati
 * @param dimensioneMassimaRichiesta limite della richiesta all'API (32MB): il PDF codificato in base64 (+33%) deve
 *                                   starci, quindi il PDF stesso puo' pesare al massimo circa 24MB
 * @param pagineMassime              pagine massime di un PDF inviato all'API
 * @param limiteDecompressione       dimensione decompressa massima di un singolo flusso compresso del PDF: oltre, il
 *                                   file e' considerato costruito ad arte (bomba di decompressione) e rifiutato
 */
@ConfigurationProperties("salary-manager.pdf")
public record PdfProperties(
        @DefaultValue List<String> passwordCandidate,
        @DefaultValue("32MB") @DataSizeUnit(DataUnit.MEGABYTES) DataSize dimensioneMassimaRichiesta,
        @DefaultValue("100") int pagineMassime,
        @DefaultValue("100MB") @DataSizeUnit(DataUnit.MEGABYTES) DataSize limiteDecompressione) {

    public PdfProperties {
        passwordCandidate = passwordCandidate == null ? List.of()
                : passwordCandidate.stream().filter(p -> p != null && !p.isBlank()).toList();
    }
}
