package com.fcusumano.sam.calcoli;

import com.fcusumano.sam.ccnl.DurataPreavviso;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Termini di preavviso del rapporto in corso per CCNL, livello e anzianita' all'ultimo cedolino, con l'indennita'
 * sostitutiva stimata (preavviso non lavorato). Con stato diverso da {@link StatoPreavviso#VERIFICATO} i termini e le
 * indennita' sono null.
 *
 * @param livello                livello del rapporto come stampato sui cedolini; null se non stampato
 * @param gruppoLivelli          gruppo di livelli del CCNL che comprende il livello (es. "2° e 3° livello")
 * @param fascia                 fascia di anzianita' applicata (es. "oltre 5 e fino a 10 anni")
 * @param prossimaFascia         primo mese in cui l'anzianita' rientra nella fascia successiva; null se la fascia e'
 *                               l'ultima o l'inizio dell'anzianita' non e' noto
 * @param dimissioni             preavviso dovuto dal lavoratore che si dimette
 * @param licenziamento          preavviso dovuto dall'azienda che licenzia
 * @param indennitaDimissioni    stima lorda di quanto l'azienda trattiene se ti dimetti senza lavorare il preavviso;
 *                               null se la retribuzione non e' nota
 * @param indennitaLicenziamento stima lorda di quanto l'azienda paga se licenzia senza far lavorare il preavviso; null
 *                               se la retribuzione non e' nota
 * @param riferimento            articolo del CCNL
 * @param nota                   base del calcolo o motivo per cui il preavviso non e' determinabile
 */
public record Preavviso(
        StatoPreavviso stato,
        String livello,
        String gruppoLivelli,
        String fascia,
        YearMonth prossimaFascia,
        DurataPreavviso dimissioni,
        DurataPreavviso licenziamento,
        BigDecimal indennitaDimissioni,
        BigDecimal indennitaLicenziamento,
        String riferimento,
        String nota) {

    public Preavviso {
        Objects.requireNonNull(stato, "stato");
    }

    /** Preavviso non determinabile, con il motivo. */
    public static Preavviso nonDeterminabile(StatoPreavviso stato, String livello, String nota) {
        return new Preavviso(stato, livello, null, null, null, null, null, null, null, null, nota);
    }
}
