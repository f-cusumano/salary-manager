package com.fcusumano.sam.web.api.dto;

import com.fcusumano.sam.cedolini.StatoCaricamento;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Stato di un file caricato.
 *
 * @param dimensione          byte
 * @param messaggio           motivo per ERRORE, DUPLICATO, AGGIUNTIVO_NON_GESTITO
 * @param cedolinoId          cedolino creato (ACQUISITO)
 * @param cedolinoEsistenteId cedolino gia' presente con cui confligge (DUPLICATO, AGGIUNTIVO_NON_GESTITO)
 * @param personaId           persona del cedolino acquisito o di quello con cui confligge; null se non disponibile
 * @param rapportoId          rapporto del cedolino acquisito o di quello con cui confligge; null se non disponibile
 */
@Schema(name = "Caricamento")
public record CaricamentoDto(
        long id,
        UUID lotto,
        String nomeFileOriginale,
        long dimensione,
        StatoCaricamento stato,
        String messaggio,
        Long cedolinoId,
        Long cedolinoEsistenteId,
        Long personaId,
        Long rapportoId,
        OffsetDateTime creatoIl,
        OffsetDateTime aggiornatoIl) {
}
