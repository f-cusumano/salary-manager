package com.fcusumano.sam.cedolini.domain;

import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.shared.Competenza;

/**
 * Generazione del nome normalizzato del PDF salvato a DB, indipendente dal nome del file caricato.
 * Default: {@code AAAA.MM_<idAzienda>_<idPersona>.pdf} (MM = 01..14) oppure {@code AAAA.TFRnn_<idAzienda>_<idPersona>.pdf}.
 * Componente sostituibile: il nome e' un dato a DB, rigenerarlo (es. dopo la rinumerazione TFR) non costa nulla.
 */
public interface NomeFileCedolinoStrategy {

    String nomeFile(Competenza competenza, AziendaId aziendaId, PersonaId personaId);
}
