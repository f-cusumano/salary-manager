package com.fcusumano.sam.calcoli.domain;

import com.fcusumano.sam.cedolini.ElementiRetributivi;
import com.fcusumano.sam.shared.Importi;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Lordo mensile contrattuale di un cedolino, ricavato solo dagli elementi fissi della retribuzione (mai dalle voci del
 * mese: straordinari, variabili, rimborsi e buoni pasto restano esclusi per costruzione).
 *
 * @param importo lordo mensile (positivo)
 * @param origine da quale dato stampato e' ricavato
 */
public record LordoContrattuale(BigDecimal importo, Origine origine) {

    /** Origine del lordo mensile, in ordine di preferenza. */
    public enum Origine {
        /** Totale degli elementi fissi stampato. */
        TOTALE_STAMPATO,
        /** Somma di minimo (o retribuzione base) + contingenza + superminimo + scatti + altri elementi fissi. */
        SOMMA_ELEMENTI,
        /** Sola retribuzione base, non scomposta. */
        RETRIBUZIONE_BASE
    }

    /**
     * Lordo mensile dagli elementi fissi: totale stampato se positivo; altrimenti somma degli elementi, purche' sia
     * stampato almeno il minimo (o la retribuzione base); altrimenti vuoto.
     */
    public static Optional<LordoContrattuale> di(ElementiRetributivi elementi) {
        if (positivo(elementi.totale())) {
            return Optional.of(new LordoContrattuale(elementi.totale(), Origine.TOTALE_STAMPATO));
        }
        var base = elementi.minimo() != null ? elementi.minimo() : elementi.retribuzioneBase();
        if (base == null) {
            return Optional.empty();
        }
        var altri = Importi.sommaNonNulli(elementi.contingenza(), elementi.superminimo(), elementi.importoScatti(),
                elementi.altriElementiFissi());
        var somma = altri == null ? base : base.add(altri);
        if (!positivo(somma)) {
            return Optional.empty();
        }
        var origine = elementi.minimo() == null && altri == null ? Origine.RETRIBUZIONE_BASE : Origine.SOMMA_ELEMENTI;
        return Optional.of(new LordoContrattuale(somma, origine));
    }

    private static boolean positivo(BigDecimal importo) {
        return importo != null && importo.signum() > 0;
    }
}
