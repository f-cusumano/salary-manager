package com.fcusumano.sam.ccnl;

/**
 * Durata di un preavviso in mesi e giorni di calendario (es. "1 mese e 15 giorni" = 1, 15).
 */
public record DurataPreavviso(int mesi, int giorni) {

    public DurataPreavviso {
        if (mesi < 0 || giorni < 0 || mesi + giorni == 0) {
            throw new IllegalArgumentException("Durata del preavviso non valida: " + mesi + " mesi, " + giorni + " giorni");
        }
    }
}
