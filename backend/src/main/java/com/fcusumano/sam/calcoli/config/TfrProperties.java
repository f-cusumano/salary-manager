package com.fcusumano.sam.calcoli.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Parametri per le stime TFR ({@code salary-manager.tfr.*}).
 *
 * @param aliquotaStimata                  aliquota della tassazione separata usata per stimare il netto TFR
 * @param impostaSostitutivaRivalutazione  imposta sostitutiva sulla rivalutazione (17%), se la rivalutazione e'
 *                                         stampata senza imposta
 * @param divisoreQuota                    divisore della quota annua (retribuzione utile / 13,5)
 * @param tassoRivalutazioneDefault        tasso annuo indicativo di rivalutazione (1,5% + 75% dell'inflazione) usato
 *                                         solo per la stima "ad oggi" dei rapporti in corso
 * @param mesiMassimiProiezione            mesi massimi proiettati dall'ultimo cedolino nella stima "ad oggi"; oltre,
 *                                         il TFR resta quello dell'ultimo cedolino (0 = nessuna proiezione)
 */
@ConfigurationProperties("salary-manager.tfr")
public record TfrProperties(
        @DefaultValue("0.23") BigDecimal aliquotaStimata,
        @DefaultValue("0.17") BigDecimal impostaSostitutivaRivalutazione,
        @DefaultValue("13.5") BigDecimal divisoreQuota,
        @DefaultValue("0.03") BigDecimal tassoRivalutazioneDefault,
        @DefaultValue("12") int mesiMassimiProiezione) {
}
