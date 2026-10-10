package com.fcusumano.sam.calcoli.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.anagrafica.PeriodoRapporto;
import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.anagrafica.RapportoLavoro;
import com.fcusumano.sam.anagrafica.RapportoLavoroDettaglio;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.calcoli.RiepilogoRapporto;
import com.fcusumano.sam.calcoli.domain.CalcolatoreRiepilogo;
import com.fcusumano.sam.calcoli.domain.ParametriCalcolo;
import com.fcusumano.sam.ccnl.CatalogoCcnl;
import com.fcusumano.sam.cedolini.ArchivioCedolini;
import com.fcusumano.sam.shared.WorkspaceId;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Lettura del riepilogo: il TFR salvato (fermo all'ultimo cedolino) e' proiettato al mese corrente solo per il
 * rapporto attuale della persona.
 */
class CalcoliRapportoServiceTest {

    private static final RapportoLavoroId ID = new RapportoLavoroId(3);
    private static final Clock APRILE_2024 = Clock.fixed(Instant.parse("2024-04-15T10:00:00Z"), ZoneOffset.UTC);
    private static final ParametriCalcolo PARAMETRI = new ParametriCalcolo(new BigDecimal("26"), new BigDecimal("173"),
            new BigDecimal("8"), new BigDecimal("13.5"), new BigDecimal("0.23"), new BigDecimal("0.17"),
            new BigDecimal("0.01"), 13, 3, new BigDecimal("0.03"), 12);

    private final ConsultazioneAnagrafica anagrafica = mock(ConsultazioneAnagrafica.class);
    private final RiepiloghiRepository repository = mock(RiepiloghiRepository.class);
    private final CalcoliRapportoService service = new CalcoliRapportoService(anagrafica, mock(ArchivioCedolini.class),
            mock(CatalogoCcnl.class), repository, () -> WorkspaceId.DEFAULT, new CalcolatoreRiepilogo(PARAMETRI),
            PARAMETRI, APRILE_2024);

    @Test
    void ilTfrDelRapportoAttualeEProiettatoAlMeseCorrente() {
        salvato();
        rapporto(true, null);

        var tfr = service.riepilogo(ID).orElseThrow().tfr();

        assertThat(tfr.quotaInAzienda()).isEqualByComparingTo("5036.30");
        assertThat(tfr.stimato()).isTrue();
        assertThat(tfr.nota()).startsWith("Stima ad oggi: 3 mesi proiettati");
    }

    @Test
    void ilTfrDiUnRapportoNonAttualeOCessatoRestaQuelloDellUltimoCedolino() {
        var salvato = salvato();
        rapporto(false, null);
        assertThat(service.riepilogo(ID)).containsSame(salvato);

        rapporto(true, LocalDate.of(2024, 1, 31));
        assertThat(service.riepilogo(ID)).containsSame(salvato);
    }

    private RiepilogoRapporto salvato() {
        var tfr = new RiepilogoRapporto.Tfr(new BigDecimal("4526.32"), new BigDecimal("160.60"),
                new BigDecimal("4526.32"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("3494.93"),
                new BigDecimal("0.23"), false, YearMonth.of(2023, 12), null, false, false, null, null);
        var riepilogo = new RiepilogoRapporto(ID, OffsetDateTime.now(APRILE_2024), null, null, null, false, null, tfr,
                List.of());
        when(repository.trova(WorkspaceId.DEFAULT, ID)).thenReturn(Optional.of(riepilogo));
        return riepilogo;
    }

    private void rapporto(boolean attuale, LocalDate cessazione) {
        var rapporto = new RapportoLavoro(ID, new PersonaId(1), new AziendaId(1), LocalDate.of(2021, 9, 1), cessazione,
                null, null, null, null, false, null, YearMonth.of(2021, 9), YearMonth.of(2023, 12));
        var periodo = new PeriodoRapporto(YearMonth.of(2021, 9), attuale ? null : YearMonth.of(2023, 12), attuale,
                null, null);
        when(anagrafica.dettaglioRapporto(ID))
                .thenReturn(Optional.of(new RapportoLavoroDettaglio(rapporto, null, null, null, periodo)));
    }
}
