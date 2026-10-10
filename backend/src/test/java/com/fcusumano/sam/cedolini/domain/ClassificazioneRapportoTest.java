package com.fcusumano.sam.cedolini.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fcusumano.sam.cedolini.domain.ClassificazioneRapporto.Assegnazione;
import com.fcusumano.sam.cedolini.domain.ClassificazioneRapporto.Cedolino;
import com.fcusumano.sam.shared.Competenza;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClassificazioneRapportoTest {

    private static final LocalDate CESSAZIONE = LocalDate.of(2024, 6, 15);

    private static Cedolino archiviato(long id, TipoDichiarato dichiarato, YearMonth periodo, Competenza attuale) {
        return new Cedolino(id, dichiarato, periodo, null, attuale, false);
    }

    private static Competenza.Mensilita mese(int anno, int mese) {
        return new Competenza.Mensilita(anno, mese);
    }

    private static Competenza.FineRapporto tfr(int progressivo, int anno, int mese) {
        return new Competenza.FineRapporto(progressivo, YearMonth.of(anno, mese));
    }

    @Test
    void leMensilitaSuccessiveAllaCessazioneDiventanoFineRapportoInOrdineCronologico() {
        var cedolini = List.of(
                archiviato(1, TipoDichiarato.ORDINARIA, YearMonth.of(2024, 6), mese(2024, 6)),
                archiviato(2, TipoDichiarato.ORDINARIA, YearMonth.of(2024, 8), mese(2024, 8)),
                archiviato(3, TipoDichiarato.ORDINARIA, YearMonth.of(2024, 7), mese(2024, 7)));

        assertThat(ClassificazioneRapporto.classifica(cedolini, CESSAZIONE)).containsExactly(
                new Assegnazione(3, tfr(1, 2024, 7)),
                new Assegnazione(2, tfr(2, 2024, 8)));
    }

    @Test
    void senzaCessazioneUnaMensilitaRiclassificataTornaOrdinariaEIProgressiviSiRicompattano() {
        var cedolini = List.of(
                archiviato(1, TipoDichiarato.ORDINARIA, YearMonth.of(2024, 5), mese(2024, 5)),
                archiviato(2, TipoDichiarato.ORDINARIA, YearMonth.of(2024, 7), tfr(1, 2024, 7)),
                archiviato(3, TipoDichiarato.FINE_RAPPORTO, YearMonth.of(2024, 9), tfr(2, 2024, 9)));

        assertThat(ClassificazioneRapporto.classifica(cedolini, null)).containsExactly(
                new Assegnazione(2, mese(2024, 7)),
                new Assegnazione(3, tfr(1, 2024, 9)));
    }

    @Test
    void unaTredicesimaArchiviataDiventaFineRapportoETornaTredicesimaSeLaCessazioneSparisce() {
        var tredicesima = archiviato(1, TipoDichiarato.TREDICESIMA, YearMonth.of(2024, 12), mese(2024, 13));
        var cessazioneSettembre = LocalDate.of(2024, 9, 30);

        assertThat(ClassificazioneRapporto.classifica(List.of(tredicesima), cessazioneSettembre))
                .containsExactly(new Assegnazione(1, tfr(1, 2024, 12)));

        var riclassificata = archiviato(1, TipoDichiarato.TREDICESIMA, YearMonth.of(2024, 12), tfr(1, 2024, 12));
        assertThat(ClassificazioneRapporto.classifica(List.of(riclassificata), null))
                .containsExactly(new Assegnazione(1, mese(2024, 13)));
    }

    @Test
    void unaMensilitaNonTornaOrdinariaSeIlMeseEGiaOccupato() {
        // luglio dichiarato ordinario e classificato fine rapporto; un altro luglio documenta una riassunzione
        var riclassificato = archiviato(1, TipoDichiarato.ORDINARIA, YearMonth.of(2024, 7), tfr(1, 2024, 7));
        var riassunzione = new Cedolino(2, TipoDichiarato.ORDINARIA, YearMonth.of(2024, 7), LocalDate.of(2024, 7, 1),
                mese(2024, 7), false);

        assertThat(ClassificazioneRapporto.classifica(List.of(riclassificato, riassunzione), null)).isEmpty();
    }

    @Test
    void unaMensilitaNonDiventaFineRapportoSeIlPeriodoEGiaOccupatoDaUnFineRapporto() {
        var ordinario = archiviato(1, TipoDichiarato.ORDINARIA, YearMonth.of(2024, 7), mese(2024, 7));
        var fineRapporto = archiviato(2, TipoDichiarato.FINE_RAPPORTO, YearMonth.of(2024, 7), tfr(1, 2024, 7));

        assertThat(ClassificazioneRapporto.classifica(List.of(ordinario, fineRapporto), CESSAZIONE)).isEmpty();
    }

    @Test
    void unaMensilitaCheDocumentaUnaRiassunzioneRestaOrdinaria() {
        var riassunzione = new Cedolino(1, TipoDichiarato.ORDINARIA, YearMonth.of(2024, 10), LocalDate.of(2024, 9, 1),
                mese(2024, 10), false);

        assertThat(ClassificazioneRapporto.classifica(List.of(riassunzione), CESSAZIONE)).isEmpty();
    }

    @Test
    void ilCedolinoInArrivoNonCambiaClassificazioneMaRiceveIlProgressivo() {
        var settembre = archiviato(1, TipoDichiarato.FINE_RAPPORTO, YearMonth.of(2024, 9), tfr(1, 2024, 9));
        var ottobreInArrivo = new Cedolino(0, TipoDichiarato.FINE_RAPPORTO, YearMonth.of(2024, 10), null,
                tfr(1, 2024, 10), true);
        var luglioInArrivo = new Cedolino(0, TipoDichiarato.FINE_RAPPORTO, YearMonth.of(2024, 7), null,
                tfr(1, 2024, 7), true);

        assertThat(ClassificazioneRapporto.classifica(List.of(settembre, ottobreInArrivo), CESSAZIONE))
                .containsExactly(new Assegnazione(0, tfr(2, 2024, 10)));
        assertThat(ClassificazioneRapporto.classifica(List.of(settembre, luglioInArrivo), CESSAZIONE))
                .containsExactly(new Assegnazione(1, tfr(2, 2024, 9)));
    }

    @Test
    void unCedolinoFissoNonVieneRiclassificato() {
        var inArrivo = new Cedolino(0, TipoDichiarato.ORDINARIA, YearMonth.of(2024, 8), null, mese(2024, 8), true);

        assertThat(ClassificazioneRapporto.classifica(List.of(inArrivo), CESSAZIONE)).isEmpty();
    }

    @Test
    void dueCedoliniVersoLoStessoPeriodoDiFineRapportoSpostanoSoloIlPrimo() {
        var cessazioneGiugno = LocalDate.of(2024, 6, 30);
        var luglioOrdinario = archiviato(1, TipoDichiarato.ORDINARIA, YearMonth.of(2024, 7), mese(2024, 7));
        var quattordicesimaLuglio = archiviato(2, TipoDichiarato.QUATTORDICESIMA, YearMonth.of(2024, 7),
                mese(2024, 14));

        assertThat(ClassificazioneRapporto.classifica(List.of(luglioOrdinario, quattordicesimaLuglio),
                cessazioneGiugno)).containsExactly(new Assegnazione(1, tfr(1, 2024, 7)));
    }

    @Test
    void unoSpostamentoLiberaLaChiaveAUnAltroEDeterminaLOrdineDelleAssegnazioni() {
        // A (riassunzione stampata) vuole la tredicesima 2024 occupata da B, che diventa fine rapporto: A si sposta
        // solo dopo B, e le assegnazioni sono nell'ordine in cui applicarle senza collisioni
        var cessazione = LocalDate.of(2024, 10, 31);
        var a = new Cedolino(1, TipoDichiarato.TREDICESIMA, YearMonth.of(2024, 11), LocalDate.of(2024, 11, 1),
                tfr(1, 2024, 11), false);
        var b = archiviato(2, TipoDichiarato.TREDICESIMA, YearMonth.of(2024, 12), mese(2024, 13));

        assertThat(ClassificazioneRapporto.classifica(List.of(a, b), cessazione)).containsExactly(
                new Assegnazione(2, tfr(1, 2024, 12)),
                new Assegnazione(1, mese(2024, 13)));
    }

    @Test
    void loStessoStatoNonProduceAssegnazioni() {
        var cedolini = List.of(
                archiviato(1, TipoDichiarato.ORDINARIA, YearMonth.of(2024, 6), mese(2024, 6)),
                archiviato(2, TipoDichiarato.ORDINARIA, YearMonth.of(2024, 7), tfr(1, 2024, 7)),
                archiviato(3, TipoDichiarato.FINE_RAPPORTO, YearMonth.of(2024, 8), tfr(2, 2024, 8)));

        assertThat(ClassificazioneRapporto.classifica(cedolini, CESSAZIONE)).isEmpty();
    }

    @Test
    void chiaviOIdRipetutiSonoUnErrore() {
        var a = archiviato(1, TipoDichiarato.ORDINARIA, YearMonth.of(2024, 6), mese(2024, 6));
        var b = archiviato(2, TipoDichiarato.ORDINARIA, YearMonth.of(2024, 6), mese(2024, 6));

        assertThatThrownBy(() -> ClassificazioneRapporto.classifica(List.of(a, b), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cedolino(3, TipoDichiarato.AGGIUNTIVA, YearMonth.of(2024, 6), null,
                mese(2024, 6), false)).isInstanceOf(IllegalArgumentException.class);
    }
}
