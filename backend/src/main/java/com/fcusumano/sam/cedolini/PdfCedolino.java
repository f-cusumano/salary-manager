package com.fcusumano.sam.cedolini;

/**
 * PDF originale con il nome del file: normalizzato per un cedolino archiviato (es. "2024.01_3_1.pdf"), quello
 * caricato dall'utente per un file in errore.
 */
public record PdfCedolino(String nomeFile, byte[] contenuto) {
}
