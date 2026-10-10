package com.fcusumano.sam.cedolini;

import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.shared.Competenza;
import java.util.List;

/**
 * Esito della prova di inserimento a mano (acquisizione eseguita e annullata: nulla viene salvato).
 *
 * @param messaggio           motivo dello scarto (DUPLICATO, AGGIUNTIVO_NON_GESTITO), null se acquisibile
 * @param competenza          competenza con cui il cedolino verrebbe archiviato, null se scartato
 * @param nuovaPersona        la persona non e' stata scelta dall'elenco: ne verrebbe creata una
 * @param nuovaAzienda        l'azienda non e' stata scelta dall'elenco: ne verrebbe creata una
 * @param nuovoRapporto       la persona non ha ancora un rapporto con l'azienda: ne verrebbe creato uno
 * @param rapportoId          rapporto esistente a cui il cedolino verrebbe attribuito, null se verrebbe creato
 * @param cedolinoEsistenteId cedolino gia' presente con cui confligge, null se acquisibile
 * @param avvisi              controlli di coerenza non superati (salvati con il cedolino) e avvisi sul contesto
 *                            (mese o rapporto diversi da quelli associati al file, persona, azienda e rapporto
 *                            nuovi); mai valori inseriti
 */
public record VerificaCedolinoManuale(
        Esito esito,
        String messaggio,
        Competenza competenza,
        boolean nuovaPersona,
        boolean nuovaAzienda,
        boolean nuovoRapporto,
        RapportoLavoroId rapportoId,
        CedolinoId cedolinoEsistenteId,
        List<String> avvisi) {

    public VerificaCedolinoManuale {
        avvisi = avvisi == null ? List.of() : List.copyOf(avvisi);
    }

    /** Esito dell'acquisizione provata. */
    public enum Esito {
        /** Il cedolino verrebbe archiviato. */
        ACQUISIBILE,
        /** Esiste gia' un cedolino della stessa mensilita' con gli stessi importi. */
        DUPLICATO,
        /** Esiste gia' un cedolino della stessa mensilita' con importi diversi. */
        AGGIUNTIVO_NON_GESTITO
    }
}
