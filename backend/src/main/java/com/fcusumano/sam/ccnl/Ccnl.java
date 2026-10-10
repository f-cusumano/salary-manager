package com.fcusumano.sam.ccnl;

/**
 * Contratto collettivo nazionale di lavoro (dato di riferimento globale, non legato al workspace).
 *
 * @param codice                codice univoco (es. "COMMERCIO")
 * @param descrizione           descrizione estesa
 * @param mensilita             numero di mensilita' annue attese (13 o 14)
 * @param periodicitaScattiMesi periodicita' degli scatti di anzianita' in mesi; null se non nota con certezza
 * @param maxScatti             numero massimo di scatti; null se non noto con certezza
 * @param meseQuattordicesima   mese (1..12) di pagamento della quattordicesima; null se 13 mensilita' o non noto
 * @param noteVerifica          note sui valori da verificare; null se tutti i valori sono certi
 */
public record Ccnl(
        String codice,
        String descrizione,
        int mensilita,
        Integer periodicitaScattiMesi,
        Integer maxScatti,
        Integer meseQuattordicesima,
        String noteVerifica) {

    public boolean prevedeQuattordicesima() {
        return mensilita == 14;
    }

    /** True se periodicita' e numero massimo degli scatti sono noti: solo allora si possono stimare gli scatti. */
    public boolean regoleScattiNote() {
        return periodicitaScattiMesi != null && maxScatti != null;
    }
}
