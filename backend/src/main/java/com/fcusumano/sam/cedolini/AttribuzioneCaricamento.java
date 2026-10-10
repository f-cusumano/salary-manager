package com.fcusumano.sam.cedolini;

import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.shared.Competenza;

/**
 * Mensilita' a cui l'utente ha associato un file in errore (mai dedotta automaticamente).
 *
 * @param competenza         anno + mese 1..14 (13 tredicesima, 14 quattordicesima)
 * @param competenzaPresente true se nel frattempo e' stato archiviato un cedolino per questa mensilita' (da un altro
 *                           file): l'associazione non ha piu' effetto sulla lista mensilita'
 */
public record AttribuzioneCaricamento(RapportoLavoroId rapportoId, Competenza.Mensilita competenza,
                                      boolean competenzaPresente) {
}
