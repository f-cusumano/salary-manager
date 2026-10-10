package com.fcusumano.sam.segnalazioni.domain;

import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.ordinario;
import static com.fcusumano.sam.segnalazioni.domain.ContestoDiProva.rapporto;
import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import com.fcusumano.sam.shared.Competenza;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class MotoreSegnalazioniTest {

    private static final ContestoRapporto CONTESTO = rapporto()
            .con(ordinario(2024, 1), ordinario(2024, 2), ordinario(2024, 3)).build();
    private static final CedolinoId GENNAIO = ordinario(2024, 1).build().id();
    private static final CedolinoId MARZO = ordinario(2024, 3).build().id();

    private static RegolaSegnalazione regola(String codice, Function<ContestoRapporto, List<SegnalazioneRilevata>> esito) {
        return new RegolaSegnalazione() {
            @Override
            public String codice() {
                return codice;
            }

            @Override
            public List<SegnalazioneRilevata> valuta(ContestoRapporto contesto) {
                return esito.apply(contesto);
            }
        };
    }

    private static SegnalazioneRilevata segnalazione(TipoSegnalazione tipo, Gravita gravita, CedolinoId cedolino,
                                                     Competenza.Mensilita competenza, String titolo) {
        return new SegnalazioneRilevata(tipo.name(), tipo, gravita, cedolino, competenza, titolo, "Descrizione", List.of());
    }

    @Test
    void ordinaPerGravitaDecrescentePoiPerData() {
        var informativa = segnalazione(TipoSegnalazione.CCNL_NON_DETERMINATO, Gravita.INFORMATIVA, null, null, "CCNL");
        var avvisoMarzo = segnalazione(TipoSegnalazione.PROGRESSIVI_IN_CALO, Gravita.AVVISO, MARZO, null, "Marzo");
        var avvisoGennaio = segnalazione(TipoSegnalazione.SCATTO_NON_IN_BUSTA, Gravita.AVVISO, GENNAIO, null, "Gennaio");
        var anomaliaRapporto = segnalazione(TipoSegnalazione.FONDO_TFR_IN_CALO, Gravita.ANOMALIA, null, null, "Fondo");
        var anomaliaMaggio = segnalazione(TipoSegnalazione.MENSILITA_MANCANTE, Gravita.ANOMALIA, null,
                new Competenza.Mensilita(2024, 5), "Maggio");
        var anomaliaFebbraio = segnalazione(TipoSegnalazione.MENSILITA_MANCANTE, Gravita.ANOMALIA, null,
                new Competenza.Mensilita(2024, 2), "Febbraio");
        var motore = new MotoreSegnalazioni(List.of(
                regola("A", c -> List.of(informativa, avvisoMarzo, anomaliaRapporto)),
                regola("B", c -> List.of(anomaliaMaggio, avvisoGennaio, anomaliaFebbraio))), (r, e) -> { });

        assertThat(motore.valuta(CONTESTO)).containsExactly(
                anomaliaFebbraio, anomaliaMaggio, anomaliaRapporto, avvisoGennaio, avvisoMarzo, informativa);
    }

    @Test
    void eliminaIDuplicatiDellaStessaAnomalia() {
        var prima = segnalazione(TipoSegnalazione.SCATTO_NON_IN_BUSTA, Gravita.AVVISO, MARZO, null, "A");
        var duplicata = segnalazione(TipoSegnalazione.SCATTO_NON_IN_BUSTA, Gravita.AVVISO, MARZO, null, "B");
        var altroTipo = segnalazione(TipoSegnalazione.SCATTI_IN_CALO, Gravita.ANOMALIA, MARZO, null, "C");
        var motore = new MotoreSegnalazioni(List.of(regola("X", c -> List.of(duplicata, prima, altroTipo))),
                (r, e) -> { });

        assertThat(motore.valuta(CONTESTO)).containsExactly(altroTipo, prima);
    }

    @Test
    void unaRegolaInErroreNonBloccaLeAltre() {
        var valida = segnalazione(TipoSegnalazione.CCNL_NON_DETERMINATO, Gravita.INFORMATIVA, null, null, "CCNL");
        var errori = new ArrayList<String>();
        var motore = new MotoreSegnalazioni(List.of(
                regola("GUASTA", c -> {
                    throw new IllegalStateException("errore di prova");
                }),
                regola("VALIDA", c -> List.of(valida))), (r, e) -> errori.add(r.codice() + ": " + e.getMessage()));

        assertThat(motore.valuta(CONTESTO)).containsExactly(valida);
        assertThat(errori).containsExactly("GUASTA: errore di prova");
    }

    @Test
    void rapportoSenzaCedoliniNessunaSegnalazione() {
        var motore = new MotoreSegnalazioni(List.of(new RegolaCcnlNonDeterminato()), (r, e) -> { });

        assertThat(motore.valuta(rapporto().build())).isEmpty();
    }

    @Test
    void risultatoDeterministicoEIdempotenteConLeRegoleReali() {
        var regole = new ArrayList<RegolaSegnalazione>(List.of(
                new RegolaMensilitaMancante(), new RegolaTredicesimaMancante(Set.of()),
                new RegolaQuattordicesimaMancante(), new RegolaFineRapportoMancante(2),
                new RegolaFondoTfrInCalo(BigDecimal.ONE), new RegolaTfrIncoerente(new BigDecimal("5"), BigDecimal.ONE),
                new RegolaScattiInCalo(BigDecimal.ONE), new RegolaProgressiviInCalo(BigDecimal.ONE),
                new RegolaLivelloInCalo(), new RegolaScattoNonInBusta(), new RegolaCcnlNonDeterminato()));
        var contesto = rapporto()
                .con(ordinario(2023, 11), ordinario(2023, 12), ordinario(2024, 2).scatti(2, null),
                        ordinario(2024, 3).scatti(1, null))
                .build();

        var risultato = new MotoreSegnalazioni(regole, (r, e) -> { }).valuta(contesto);
        var inversa = new ArrayList<>(regole.reversed());

        assertThat(risultato).extracting(SegnalazioneRilevata::tipo).containsExactly(
                TipoSegnalazione.TREDICESIMA_MANCANTE, TipoSegnalazione.MENSILITA_MANCANTE,
                TipoSegnalazione.SCATTI_IN_CALO, TipoSegnalazione.CCNL_NON_DETERMINATO);
        assertThat(new MotoreSegnalazioni(inversa, (r, e) -> { }).valuta(contesto)).isEqualTo(risultato);
        assertThat(new MotoreSegnalazioni(regole, (r, e) -> { }).valuta(contesto)).isEqualTo(risultato);
    }
}
