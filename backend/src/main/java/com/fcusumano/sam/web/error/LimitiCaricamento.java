package com.fcusumano.sam.web.error;

import org.springframework.util.unit.DataSize;

/**
 * Limiti dell'upload multipart, letti dalla configurazione standard di Spring
 * ({@code spring.servlet.multipart.max-file-size} e {@code max-request-size}) per avere un'unica fonte.
 */
public final class LimitiCaricamento {

    /** Espressione per {@code @Value}: dimensione massima di un singolo file (default 25MB). */
    public static final String MAX_FILE = "${spring.servlet.multipart.max-file-size:25MB}";
    /** Espressione per {@code @Value}: dimensione massima dell'intera richiesta (default 500MB). */
    public static final String MAX_RICHIESTA = "${spring.servlet.multipart.max-request-size:500MB}";

    private LimitiCaricamento() {
    }

    /** "25 MB", "512 KB", "100 byte". */
    public static String descrivi(DataSize dimensione) {
        long byteTotali = dimensione.toBytes();
        if (byteTotali >= DataSize.ofMegabytes(1).toBytes() && byteTotali % DataSize.ofMegabytes(1).toBytes() == 0) {
            return dimensione.toMegabytes() + " MB";
        }
        if (byteTotali >= DataSize.ofKilobytes(1).toBytes() && byteTotali % DataSize.ofKilobytes(1).toBytes() == 0) {
            return dimensione.toKilobytes() + " KB";
        }
        return byteTotali + " byte";
    }
}
