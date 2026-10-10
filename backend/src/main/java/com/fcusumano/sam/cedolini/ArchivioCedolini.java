package com.fcusumano.sam.cedolini;

import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import java.util.List;
import java.util.Optional;

/**
 * Consultazione ed eliminazione dei cedolini acquisiti (workspace corrente).
 */
public interface ArchivioCedolini {

    /** Sintesi per la lista mensilita', in ordine cronologico inverso ({@code Competenza.ORDINE_CRONOLOGICO} invertito). */
    List<CedolinoSintesi> sintesiDiRapporto(RapportoLavoroId rapportoId);

    /** Tutti i cedolini normalizzati del rapporto in ordine cronologico crescente: input di calcoli e segnalazioni. */
    List<CedolinoNormalizzato> normalizzatiDiRapporto(RapportoLavoroId rapportoId);

    Optional<CedolinoDettaglio> dettaglio(CedolinoId cedolinoId);

    Optional<PdfCedolino> pdf(CedolinoId cedolinoId);

    /**
     * Elimina il cedolino (dati e PDF), rinumera i progressivi TFR rimasti, aggiorna gli estremi del rapporto in
     * anagrafica e pubblica {@link CedolinoEliminato} (ricalcolo da zero dei dati derivati). Se era l'ultimo cedolino
     * del rapporto, elimina anche il rapporto e le eventuali persona/azienda orfane.
     *
     * @throws com.fcusumano.sam.shared.RisorsaNonTrovataException se il cedolino non esiste
     */
    void elimina(CedolinoId cedolinoId);
}
