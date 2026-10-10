package com.fcusumano.sam.anagrafica;

/**
 * Evento: il rapporto sta per essere eliminato (pubblicato prima della cancellazione, nella stessa transazione).
 * I dati derivati sono comunque rimossi in cascata dal database: l'evento serve a eventuali pulizie applicative.
 */
public record RapportoLavoroEliminato(RapportoLavoroId rapportoId, PersonaId personaId, AziendaId aziendaId) {
}
