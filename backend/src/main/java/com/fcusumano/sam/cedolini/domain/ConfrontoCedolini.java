package com.fcusumano.sam.cedolini.domain;

import com.fcusumano.sam.shared.Importi;
import java.math.BigDecimal;

/**
 * Confronto tra due cedolini con la stessa competenza: se netto, lordo, totale competenze e totale trattenute
 * coincidono (ignorando la scala) il nuovo e' un duplicato; altrimenti e' un cedolino aggiuntivo non gestito
 * (es. premi o arretrati pagati a parte nello stesso mese).
 */
public final class ConfrontoCedolini {

    private ConfrontoCedolini() {
    }

    /** Importi che caratterizzano il contenuto di un cedolino. */
    public record ImportiChiave(BigDecimal netto, BigDecimal lordo, BigDecimal totaleCompetenze,
                                BigDecimal totaleTrattenute) {
    }

    public static boolean stessoContenuto(ImportiChiave a, ImportiChiave b) {
        return Importi.uguali(a.netto(), b.netto())
                && Importi.uguali(a.lordo(), b.lordo())
                && Importi.uguali(a.totaleCompetenze(), b.totaleCompetenze())
                && Importi.uguali(a.totaleTrattenute(), b.totaleTrattenute());
    }
}
