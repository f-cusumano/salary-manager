package com.fcusumano.sam.cedolini;

import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Stato di un file caricato.
 *
 * @param lotto               identifica i file caricati insieme (una POST)
 * @param messaggio           motivo per ERRORE / AGGIUNTIVO_NON_GESTITO / DUPLICATO, null altrimenti; mai dati personali
 * @param motivo              motivo dell'errore (solo ERRORE), null negli altri stati e negli errori registrati prima
 *                            dell'introduzione del motivo
 * @param pdfDisponibile      true solo per ERRORE con il PDF originale del file conservato (inseribile a mano e
 *                            rileggibile)
 * @param cedolinoId          cedolino creato (ACQUISITO), altrimenti null
 * @param cedolinoEsistenteId cedolino gia' presente con cui confligge (DUPLICATO, AGGIUNTIVO_NON_GESTITO), altrimenti null
 * @param personaId           persona a cui e' attribuito il cedolino (creato o esistente), null se non attribuito o se
 *                            il cedolino e' stato poi eliminato
 * @param rapportoId          rapporto di lavoro del cedolino (creato o esistente), null negli stessi casi di personaId
 */
public record Caricamento(
        CaricamentoId id,
        UUID lotto,
        String nomeFileOriginale,
        long dimensione,
        StatoCaricamento stato,
        String messaggio,
        MotivoErrore motivo,
        boolean pdfDisponibile,
        CedolinoId cedolinoId,
        CedolinoId cedolinoEsistenteId,
        PersonaId personaId,
        RapportoLavoroId rapportoId,
        OffsetDateTime creatoIl,
        OffsetDateTime aggiornatoIl) {
}
