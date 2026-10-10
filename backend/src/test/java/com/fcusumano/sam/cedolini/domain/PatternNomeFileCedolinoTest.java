package com.fcusumano.sam.cedolini.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.shared.Competenza;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class PatternNomeFileCedolinoTest {

    private final NomeFileCedolinoStrategy strategia = new PatternNomeFileCedolino(PatternNomeFileCedolino.PATTERN_DEFAULT);

    @Test
    void mensilitaOrdinariaTredicesimaEFineRapporto() {
        var azienda = new AziendaId(3);
        var persona = new PersonaId(1);
        assertThat(strategia.nomeFile(new Competenza.Mensilita(2024, 1), azienda, persona)).isEqualTo("2024.01_3_1.pdf");
        assertThat(strategia.nomeFile(Competenza.Mensilita.tredicesima(2024), azienda, persona)).isEqualTo("2024.13_3_1.pdf");
        assertThat(strategia.nomeFile(new Competenza.FineRapporto(1, YearMonth.of(2020, 4)), azienda, persona))
                .isEqualTo("2020.TFR01_3_1.pdf");
    }
}
