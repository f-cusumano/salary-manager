package com.fcusumano.sam.anagrafica.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.DatiAzienda;
import java.time.YearMonth;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AggiornamentoAziendaTest {

    private static final YearMonth MARZO = YearMonth.of(2024, 3);
    private static final YearMonth GIUGNO = YearMonth.of(2024, 6);

    @Test
    void ilCedolinoPiuRecenteAggiornaGliAttributiECompletaGliIdentificativi() {
        var registrata = profilo(new DatiAzienda(null, null, null, "Alfa Software S.r.l.", "Via Roma 1, Milano", "A1",
                MARZO)).conId(new AziendaId(1));
        var letta = profilo(new DatiAzienda("01234567890", "01234567890", "1234567890", "ALFA SOFTWARE SRL",
                "Via Verdi 2, Milano", null, GIUGNO));

        var aggiornata = AggiornamentoAzienda.unisci(registrata, letta, Set.of());

        assertThat(aggiornata.id()).isEqualTo(new AziendaId(1));
        assertThat(aggiornata.ragioneSociale()).isEqualTo("ALFA SOFTWARE SRL");
        assertThat(aggiornata.indirizzo()).isEqualTo("Via Verdi 2, Milano");
        assertThat(aggiornata.codiceAzienda()).isEqualTo("A1");
        assertThat(aggiornata.codiceFiscale()).isEqualTo("01234567890");
        assertThat(aggiornata.partitaIva()).isEqualTo("01234567890");
        assertThat(aggiornata.posizioneInps()).isEqualTo("1234567890");
        assertThat(aggiornata.riferimento()).isEqualTo(GIUGNO);
    }

    @Test
    void unCedolinoPiuVecchioCompletaSoloIValoriMancanti() {
        var registrata = profilo(new DatiAzienda(null, "01234567890", "1234567890", "Alfa Software S.r.l.", null,
                null, GIUGNO)).conId(new AziendaId(1));
        var letta = profilo(new DatiAzienda(null, null, "9999999999", "Alfa Software (vecchia)", "Via Roma 1", "A1",
                MARZO));

        var aggiornata = AggiornamentoAzienda.unisci(registrata, letta, Set.of());

        assertThat(aggiornata.ragioneSociale()).isEqualTo("Alfa Software S.r.l.");
        assertThat(aggiornata.ragioneSocialeNormalizzata()).isEqualTo("ALFA SOFTWARE SRL");
        assertThat(aggiornata.indirizzo()).isEqualTo("Via Roma 1");
        assertThat(aggiornata.codiceAzienda()).isEqualTo("A1");
        assertThat(aggiornata.posizioneInps()).isEqualTo("1234567890");
        assertThat(aggiornata.riferimento()).isEqualTo(GIUGNO);
    }

    @Test
    void unIdentificativoGiaUsatoDaUnAltraAziendaNonVieneAssegnato() {
        var registrata = profilo(new DatiAzienda(null, null, "1234567890", "Alfa", null, null))
                .conId(new AziendaId(1));
        var letta = profilo(new DatiAzienda("01234567890", null, "1234567890", "Alfa", null, null));

        var aggiornata = AggiornamentoAzienda.unisci(registrata, letta, Set.of("01234567890"));

        assertThat(aggiornata.codiceFiscale()).isNull();
    }

    private static ProfiloAzienda profilo(DatiAzienda dati) {
        return ProfiloAzienda.daCedolino(dati);
    }
}
