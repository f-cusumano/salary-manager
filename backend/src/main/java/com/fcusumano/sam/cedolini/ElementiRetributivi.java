package com.fcusumano.sam.cedolini;

import java.math.BigDecimal;

/**
 * Elementi fissi mensili della retribuzione. Possono essere scomposti (minimo + contingenza + superminimo + scatti +
 * altri elementi) oppure stampati solo come retribuzione base: in quel caso solo {@code retribuzioneBase} e
 * {@code totale} sono valorizzati. Ai rinnovi CCNL il minimo puo' crescere e il superminimo assorbibile calare a
 * parita' di totale: non e' un'anomalia.
 *
 * @param superminimoAssorbibile null se non indicato
 * @param altriElementiFissi     somma di altri elementi fissi (EDR, indennita' di funzione, terzo elemento, ...)
 * @param totale                 totale lordo mensile contrattuale (base per la RAL)
 * @param divisoreOrario         divisore orario stampato (es. 168, 173)
 * @param divisoreGiornaliero    divisore giornaliero stampato (es. 26)
 */
public record ElementiRetributivi(
        BigDecimal minimo,
        BigDecimal contingenza,
        BigDecimal superminimo,
        Boolean superminimoAssorbibile,
        BigDecimal importoScatti,
        BigDecimal altriElementiFissi,
        BigDecimal retribuzioneBase,
        BigDecimal totale,
        BigDecimal pagaOraria,
        BigDecimal pagaGiornaliera,
        BigDecimal divisoreOrario,
        BigDecimal divisoreGiornaliero) {

    public static final ElementiRetributivi VUOTI =
            new ElementiRetributivi(null, null, null, null, null, null, null, null, null, null, null, null);

    /** True se la retribuzione e' scomposta almeno in minimo o superminimo. */
    public boolean scomposti() {
        return minimo != null || superminimo != null;
    }
}
