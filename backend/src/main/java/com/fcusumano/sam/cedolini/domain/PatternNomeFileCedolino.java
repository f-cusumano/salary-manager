package com.fcusumano.sam.cedolini.domain;

import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.shared.Competenza;
import java.util.Objects;

/**
 * Implementazione di default basata su pattern con segnaposto: {@code {anno}}, {@code {mese}} (01..14 o TFRnn),
 * {@code {idAzienda}}, {@code {idPersona}}. Pattern di default: {@value #PATTERN_DEFAULT}.
 */
public final class PatternNomeFileCedolino implements NomeFileCedolinoStrategy {

    public static final String PATTERN_DEFAULT = "{anno}.{mese}_{idAzienda}_{idPersona}.pdf";

    private final String pattern;

    public PatternNomeFileCedolino(String pattern) {
        this.pattern = Objects.requireNonNullElse(pattern, PATTERN_DEFAULT);
        if (!this.pattern.contains("{anno}") || !this.pattern.contains("{mese}")
                || !this.pattern.contains("{idAzienda}") || !this.pattern.contains("{idPersona}")) {
            throw new IllegalArgumentException("Il pattern del nome file deve contenere {anno}, {mese}, {idAzienda} e {idPersona}");
        }
    }

    @Override
    public String nomeFile(Competenza competenza, AziendaId aziendaId, PersonaId personaId) {
        return pattern
                .replace("{anno}", "%04d".formatted(competenza.anno()))
                .replace("{mese}", competenza.codice())
                .replace("{idAzienda}", Long.toString(aziendaId.valore()))
                .replace("{idPersona}", Long.toString(personaId.valore()));
    }
}
