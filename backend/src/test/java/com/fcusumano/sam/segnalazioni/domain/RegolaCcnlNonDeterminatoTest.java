package com.fcusumano.sam.segnalazioni.domain;

import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.ordinario;
import static com.fcusumano.sam.segnalazioni.domain.ContestoDiProva.COMMERCIO;
import static com.fcusumano.sam.segnalazioni.domain.ContestoDiProva.rapporto;
import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.segnalazioni.Evidenza;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import org.junit.jupiter.api.Test;

class RegolaCcnlNonDeterminatoTest {

    private final RegolaCcnlNonDeterminato regola = new RegolaCcnlNonDeterminato();

    @Test
    void segnalaIlCcnlNonStampato() {
        var segnalazioni = regola.valuta(rapporto().con(ordinario(2024, 1)).build());

        assertThat(segnalazioni).singleElement().satisfies(s -> {
            assertThat(s.tipo()).isEqualTo(TipoSegnalazione.CCNL_NON_DETERMINATO);
            assertThat(s.gravita()).isEqualTo(Gravita.INFORMATIVA);
            assertThat(s.cedolinoId()).isNull();
            assertThat(s.competenza()).isNull();
            assertThat(s.titolo()).isEqualTo("CCNL non determinato");
            assertThat(s.descrizione()).isEqualTo("Il CCNL non è stampato sui cedolini: indicalo nel riepilogo del "
                    + "rapporto. Finché non è indicato la RAL è solo stimata e non è possibile controllare "
                    + "quattordicesima, scatti di anzianità e livello secondo le regole del contratto.");
            assertThat(s.evidenze()).isEmpty();
        });
    }

    @Test
    void riportaIlTestoStampatoNonRiconosciuto() {
        var contesto = rapporto().con(ordinario(2024, 1).ccnl("CONTRATTO DI LAVORO Settore inventato"),
                ordinario(2024, 2)).build();

        assertThat(regola.valuta(contesto)).singleElement().satisfies(s -> {
            assertThat(s.descrizione()).contains("«CONTRATTO DI LAVORO Settore inventato»", "non è stato riconosciuto");
            assertThat(s.evidenze()).extracting(Evidenza::valore).containsExactly("CONTRATTO DI LAVORO Settore inventato");
        });
    }

    @Test
    void ccnlDeterminatoNessunaSegnalazione() {
        assertThat(regola.valuta(rapporto().ccnl(COMMERCIO).con(ordinario(2024, 1)).build())).isEmpty();
    }

    @Test
    void senzaCedoliniNessunaSegnalazione() {
        assertThat(regola.valuta(rapporto().build())).isEmpty();
    }
}
