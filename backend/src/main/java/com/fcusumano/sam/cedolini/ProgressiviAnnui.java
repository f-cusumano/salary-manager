package com.fcusumano.sam.cedolini;

import java.math.BigDecimal;

/**
 * Progressivi annui stampati (crescono nel corso dello stesso anno, ripartono a gennaio).
 */
public record ProgressiviAnnui(BigDecimal imponibilePrevidenziale, BigDecimal imponibileIrpef, BigDecimal irpefTrattenuta) {

    public static final ProgressiviAnnui VUOTI = new ProgressiviAnnui(null, null, null);
}
