package com.fcusumano.sam.web.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Nuova lettura del PDF conservato di un file in errore.
 *
 * @param password password di apertura del PDF protetto, null o vuota se non serve; vale solo per questa lettura, mai
 *                 salvata, restituita o registrata nei log (anche {@link #toString()} la nasconde)
 */
@Schema(name = "RichiestaRilettura")
public record RichiestaRiletturaDto(
        @Schema(type = "string", format = "password", maxLength = LUNGHEZZA_MASSIMA_PASSWORD,
                accessMode = Schema.AccessMode.WRITE_ONLY,
                description = "Password di apertura del PDF protetto (null se non serve). Il backend prova questa, "
                        + "poi le password configurate e i codici fiscali delle persone censite. Mai salvata ne' "
                        + "registrata nei log.")
        String password) {

    /** Come la password del caricamento (validata con le stesse regole). */
    public static final int LUNGHEZZA_MASSIMA_PASSWORD = 200;

    @Override
    public String toString() {
        return "RichiestaRiletturaDto[password=" + (password == null ? "assente" : "***") + "]";
    }
}
