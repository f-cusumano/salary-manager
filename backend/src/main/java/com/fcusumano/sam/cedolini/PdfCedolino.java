package com.fcusumano.sam.cedolini;

/**
 * PDF originale con il nome normalizzato (es. "2024.01_3_1.pdf").
 */
public record PdfCedolino(String nomeFile, byte[] contenuto) {
}
