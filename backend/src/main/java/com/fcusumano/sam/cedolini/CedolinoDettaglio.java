package com.fcusumano.sam.cedolini;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Dettaglio di un cedolino per la UI: dati normalizzati + metadati di acquisizione. Le aree tematiche (presenze,
 * competenze, trattenute previdenziali e fiscali, TFR, ratei, voci non retributive) si ottengono da
 * {@link CedolinoNormalizzato#vociDi(CategoriaVoce)} e dai sotto-record.
 *
 * @param confidenza       confidenza complessiva dell'estrazione (0..1), null se non disponibile (sempre null per i
 *                         cedolini inseriti a mano)
 * @param inseritoAMano    true se il cedolino e' stato inserito a mano da un file in errore (nessuna lettura)
 * @param campiNonTrovati  campi chiave non trovati nel documento
 * @param avvisiEstrazione avvisi dell'estrazione: controlli di coerenza non superati, valori non interpretabili, dati
 *                         bancari rimossi, note del modello
 */
public record CedolinoDettaglio(
        CedolinoNormalizzato dati,
        String nomeFile,
        OffsetDateTime acquisitoIl,
        BigDecimal confidenza,
        boolean inseritoAMano,
        List<String> campiNonTrovati,
        List<String> avvisiEstrazione) {

    public CedolinoDettaglio {
        campiNonTrovati = campiNonTrovati == null ? List.of() : List.copyOf(campiNonTrovati);
        avvisiEstrazione = avvisiEstrazione == null ? List.of() : List.copyOf(avvisiEstrazione);
    }
}
