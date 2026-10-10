package com.fcusumano.sam.cedolini;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * File caricato con errore e non ancora risolto: il suo ultimo tentativo concluso e' in ERRORE e nessun cedolino
 * archiviato ha lo stesso contenuto (hash). La riga elencata e' quel tentativo; un file caricato piu' volte compare una
 * volta sola.
 *
 * @param motivo         motivo dell'errore, null per gli errori registrati prima dell'introduzione del motivo
 * @param messaggio      messaggio dell'errore; mai dati personali
 * @param pdfDisponibile PDF originale conservato: il file si puo' inserire a mano o rileggere
 * @param inElaborazione un nuovo tentativo dello stesso file e' in coda o in lettura: le azioni non sono disponibili
 * @param attribuzione   mensilita' associata dall'utente, null se non associato
 */
public record CaricamentoInErrore(
        CaricamentoId id,
        UUID lotto,
        String nomeFileOriginale,
        long dimensione,
        MotivoErrore motivo,
        String messaggio,
        boolean pdfDisponibile,
        boolean inElaborazione,
        AttribuzioneCaricamento attribuzione,
        OffsetDateTime creatoIl) {
}
