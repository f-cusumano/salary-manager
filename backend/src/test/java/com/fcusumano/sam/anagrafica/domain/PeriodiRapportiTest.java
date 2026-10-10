package com.fcusumano.sam.anagrafica.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.FontePeriodo;
import com.fcusumano.sam.anagrafica.PeriodoRapporto;
import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.anagrafica.RapportoLavoro;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class PeriodiRapportiTest {

    @Test
    void rapportoAttualeSenzaCessazioneEConUltimoCedolinoPiuRecente() {
        var passato = rapporto(1, LocalDate.of(2018, 4, 2), null, ym(2018, 4), ym(2023, 12));
        var attuale = rapporto(2, null, null, ym(2024, 1), ym(2026, 2));

        var periodi = PeriodiRapporti.calcola(List.of(passato, attuale));

        assertThat(periodi.get(passato.id())).isEqualTo(new PeriodoRapporto(ym(2018, 4), ym(2023, 12), false,
                FontePeriodo.DATA_CONTRATTUALE, FontePeriodo.CEDOLINI));
        assertThat(periodi.get(attuale.id())).isEqualTo(new PeriodoRapporto(ym(2024, 1), null, true,
                FontePeriodo.CEDOLINI, FontePeriodo.NON_DISPONIBILE));
    }

    @Test
    void conCessazioneLaFineEIlMeseDiCessazioneEIlRapportoNonEAttuale() {
        var cessato = rapporto(1, LocalDate.of(2020, 1, 7), LocalDate.of(2026, 2, 14), ym(2020, 1), ym(2026, 3));

        var periodo = PeriodiRapporti.calcola(List.of(cessato)).get(cessato.id());

        assertThat(periodo).isEqualTo(new PeriodoRapporto(ym(2020, 1), ym(2026, 2), false,
                FontePeriodo.DATA_CONTRATTUALE, FontePeriodo.DATA_CONTRATTUALE));
    }

    @Test
    void cambioAziendaNelloStessoMeseSonoAttualiEntrambiSeAPariMerito() {
        var a = rapporto(1, null, null, ym(2024, 1), ym(2025, 6));
        var b = rapporto(2, null, null, ym(2025, 6), ym(2025, 6));

        var periodi = PeriodiRapporti.calcola(List.of(a, b));

        assertThat(periodi.get(a.id()).attuale()).isTrue();
        assertThat(periodi.get(b.id()).attuale()).isTrue();
    }

    @Test
    void rapportoSenzaDatiNonDisponibile() {
        var vuoto = rapporto(1, null, null, null, null);

        var periodo = PeriodiRapporti.calcola(List.of(vuoto)).get(vuoto.id());

        assertThat(periodo.da()).isNull();
        assertThat(periodo.fonteInizio()).isEqualTo(FontePeriodo.NON_DISPONIBILE);
    }

    @Test
    void ordineCronologicoInversoConAttualeInAlto() {
        var primo = rapporto(1, null, LocalDate.of(2016, 12, 31), ym(2015, 1), ym(2016, 12));
        var secondo = rapporto(2, null, LocalDate.of(2019, 6, 30), ym(2017, 1), ym(2019, 6));
        var attuale = rapporto(3, null, null, ym(2016, 6), ym(2026, 1));
        var terzo = rapporto(4, null, null, ym(2019, 7), ym(2023, 3));

        var ordinati = PeriodiRapporti.ordina(List.of(primo, secondo, attuale, terzo));

        assertThat(ordinati).extracting(r -> r.rapporto().id().valore()).containsExactly(3L, 4L, 2L, 1L);
    }

    private static YearMonth ym(int anno, int mese) {
        return YearMonth.of(anno, mese);
    }

    private static RapportoLavoro rapporto(long id, LocalDate assunzione, LocalDate cessazione, YearMonth primo,
                                           YearMonth ultimo) {
        return new RapportoLavoro(new RapportoLavoroId(id), new PersonaId(1), new AziendaId(id), assunzione, cessazione,
                null, null, null, null, false, null, primo, ultimo);
    }
}
