package com.fcusumano.sam.web.api.dto;

import com.fcusumano.sam.cedolini.MotivoErrore;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * File in errore (stesso contenuto = stesso file): l'ultimo tentativo concluso del file, in ERRORE, e il file non e'
 * archiviato come cedolino. {@code id} e' il caricamento da usare nelle azioni su
 * {@code /api/v1/caricamenti/{caricamentoId}}.
 *
 * @param dimensione     byte
 * @param motivo         null per gli errori delle versioni precedenti (il PDF non e' conservato)
 * @param messaggio      messaggio dell'errore per l'utente (mai dati personali)
 * @param pdfDisponibile PDF conservato: il file si puo' inserire a mano e rileggere
 * @param inElaborazione un nuovo tentativo dello stesso file e' in corso (azioni non disponibili)
 * @param attribuzione   mensilita' a cui l'utente ha associato il file; null se non associato
 */
@Schema(name = "CaricamentoInErrore")
public record CaricamentoInErroreDto(
        long id,
        UUID lotto,
        String nomeFileOriginale,
        long dimensione,
        MotivoErrore motivo,
        String messaggio,
        boolean pdfDisponibile,
        boolean inElaborazione,
        AttribuzioneCaricamentoDto attribuzione,
        OffsetDateTime creatoIl) {
}
