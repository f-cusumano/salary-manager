package com.fcusumano.sam.cedolini;

import java.math.BigDecimal;

/**
 * Dati TFR stampati sul cedolino. Controllo di coerenza: fondo al 31/12 dell'anno = fondo al 31/12 precedente
 * + rivalutazione - imposta sostitutiva + quote dell'anno - quote a fondi/busta - anticipi.
 *
 * @param fondo31DicembreAnnoPrecedente fondo TFR in azienda al 31/12 dell'anno precedente
 * @param quotaAnno                     quota maturata nell'anno (progressivo)
 * @param quotaMese                     quota maturata nel mese
 * @param quotaFondi                    quota versata a fondi pensione (progressivo annuo se cosi' stampato)
 * @param quotaBusta                    quota liquidata in busta (QuIR)
 * @param anticipi                      anticipi erogati
 * @param retribuzioneUtile             retribuzione utile ai fini TFR
 * @param liquidatoLordo                TFR liquidato lordo (cedolini di fine rapporto)
 * @param ritenuta                      ritenuta sul TFR liquidato
 */
public record DatiTfr(
        BigDecimal fondo31DicembreAnnoPrecedente,
        BigDecimal rivalutazione,
        BigDecimal impostaSostitutiva,
        BigDecimal quotaAnno,
        BigDecimal quotaMese,
        BigDecimal quotaFondi,
        BigDecimal quotaBusta,
        BigDecimal anticipi,
        BigDecimal retribuzioneUtile,
        BigDecimal liquidatoLordo,
        BigDecimal ritenuta) {
}
