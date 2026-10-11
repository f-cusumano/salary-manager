package com.fcusumano.sam.web.api.dto;

import com.fcusumano.sam.cedolini.VerificaCedolinoManuale.Esito;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Esito della prova di inserimento a mano (nulla viene salvato). ACQUISIBILE: il cedolino puo' essere salvato con la
 * competenza indicata. DUPLICATO, AGGIUNTIVO_NON_GESTITO: il salvataggio verrebbe rifiutato.
 *
 * @param messaggio           motivo per DUPLICATO e AGGIUNTIVO_NON_GESTITO
 * @param competenza          competenza con cui verrebbe registrato (solo ACQUISIBILE)
 * @param titolo              titolo della competenza pronto per la UI (solo ACQUISIBILE)
 * @param nuovaPersona        verrebbe creata una nuova persona (non scelta dall'elenco, {@code personaId} null)
 * @param nuovaAzienda        verrebbe creata una nuova azienda (non scelta dall'elenco, {@code aziendaId} null)
 * @param nuovoRapporto       verrebbe creato un nuovo rapporto di lavoro
 * @param rapportoId          rapporto esistente a cui andrebbe il cedolino; null se nuovo
 * @param cedolinoEsistenteId cedolino gia' presente con cui confligge (DUPLICATO, AGGIUNTIVO_NON_GESTITO)
 * @param avvisi              controlli di coerenza non superati e avvisi sul contesto (non bloccanti)
 */
@Schema(name = "VerificaCedolinoManuale")
public record VerificaCedolinoManualeDto(
        Esito esito,
        String messaggio,
        CompetenzaDto competenza,
        String titolo,
        boolean nuovaPersona,
        boolean nuovaAzienda,
        boolean nuovoRapporto,
        Long rapportoId,
        Long cedolinoEsistenteId,
        List<String> avvisi) {
}
