package com.fcusumano.sam.anagrafica;

/**
 * Voce dell'elenco aziende di una persona.
 *
 * @param etichettaAzienda ragione sociale, con " (P.IVA xxxxxxxxxxx)" aggiunto solo se tra i rapporti della persona
 *                         ci sono aziende con la stessa ragione sociale normalizzata (in mancanza di P.IVA si usa
 *                         " (CF ...)", poi " (Pos. INPS ...)")
 */
public record RapportoSintesi(
        RapportoLavoroId id,
        PersonaId personaId,
        AziendaId aziendaId,
        String ragioneSociale,
        String partitaIva,
        String etichettaAzienda,
        PeriodoRapporto periodo,
        String ccnlCodice) {
}
