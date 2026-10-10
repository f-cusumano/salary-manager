package com.fcusumano.sam.cedolini;

import com.fcusumano.sam.shared.UnitaMisura;
import java.math.BigDecimal;

/**
 * Situazione di un rateo (ferie, permessi, ...) con la sua unita' di misura. Uno stesso tipo puo' comparire due volte
 * con unita' diverse (es. ferie in giorni e in ore).
 *
 * @param residuoAnnoPrecedente residuo dell'anno precedente
 * @param saldo                 saldo a fine mese (residuo + maturato - goduto)
 */
public record Rateo(
        TipoRateo tipo,
        UnitaMisura unita,
        BigDecimal residuoAnnoPrecedente,
        BigDecimal maturato,
        BigDecimal goduto,
        BigDecimal saldo) {
}
