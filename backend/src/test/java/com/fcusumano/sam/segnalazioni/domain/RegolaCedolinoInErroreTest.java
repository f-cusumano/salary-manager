package com.fcusumano.sam.segnalazioni.domain;

import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.ordinario;
import static com.fcusumano.sam.segnalazioni.domain.CedolinoDiProva.tredicesima;
import static com.fcusumano.sam.segnalazioni.domain.ContestoDiProva.rapporto;
import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.cedolini.MotivoErrore;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import com.fcusumano.sam.segnalazioni.domain.ContestoRapporto.FileInErrore;
import com.fcusumano.sam.shared.Competenza;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RegolaCedolinoInErroreTest {

    private final RegolaCedolinoInErrore regola = new RegolaCedolinoInErrore();

    @Test
    void avvisoPerLaMensilitaConUnFileInErroreAssociato() {
        var contesto = rapporto().con(ordinario(2023, 4), ordinario(2023, 6))
                .inErrore(new Competenza.Mensilita(2023, 5), MotivoErrore.PDF_PROTETTO).build();

        var segnalazioni = regola.valuta(contesto);

        assertThat(segnalazioni).singleElement().satisfies(s -> {
            assertThat(s.tipo()).isEqualTo(TipoSegnalazione.CEDOLINO_IN_ERRORE);
            assertThat(s.codiceRegola()).isEqualTo("CEDOLINO_IN_ERRORE");
            assertThat(s.gravita()).isEqualTo(Gravita.AVVISO);
            assertThat(s.cedolinoId()).isNull();
            assertThat(s.competenza()).isEqualTo(new Competenza.Mensilita(2023, 5));
            assertThat(s.titolo()).isEqualTo("Cedolino caricato con errore: Maggio 2023");
            assertThat(s.descrizione()).isEqualTo("Il cedolino di Maggio 2023 è stato caricato ma non è stato letto "
                    + "(Protetto da password): inseriscilo a mano dall'elenco «Cedolini caricati con errore» o "
                    + "riprova la lettura.");
            assertThat(s.evidenze()).singleElement().satisfies(e -> {
                assertThat(e.descrizione()).isEqualTo("Motivo");
                assertThat(e.valore()).isEqualTo("Protetto da password");
                assertThat(e.cedolinoId()).isNull();
                assertThat(e.competenza()).isNull();
            });
        });
    }

    @Test
    void unaSegnalazionePerMensilitaInOrdineCronologicoAncheTredicesimaEQuattordicesima() {
        var contesto = rapporto().con(ordinario(2023, 1), ordinario(2024, 1))
                .inErrore(Competenza.Mensilita.quattordicesima(2023), MotivoErrore.PDF_SENZA_TESTO)
                .inErrore(Competenza.Mensilita.tredicesima(2023), MotivoErrore.LAYOUT_NON_RICONOSCIUTO)
                .inErrore(new Competenza.Mensilita(2023, 3), MotivoErrore.INTERROTTO)
                .build();

        assertThat(regola.valuta(contesto)).extracting(SegnalazioneRilevata::titolo).containsExactly(
                "Cedolino caricato con errore: Marzo 2023",
                "Cedolino caricato con errore: Quattordicesima 2023",
                "Cedolino caricato con errore: Tredicesima 2023");
    }

    @Test
    void senzaMotivoRegistratoNessunaEvidenzaENessunMotivoNelTesto() {
        var contesto = rapporto().con(ordinario(2023, 4))
                .inErrore(new Competenza.Mensilita(2023, 5), null).build();

        assertThat(regola.valuta(contesto)).singleElement().satisfies(s -> {
            assertThat(s.descrizione()).startsWith("Il cedolino di Maggio 2023 è stato caricato ma non è stato letto: ")
                    .doesNotContain("(");
            assertThat(s.evidenze()).isEmpty();
        });
    }

    @Test
    void senzaPdfConservatoNonProponeInserimentoAManoNeNuovaLetturaMaIlNuovoCaricamento() {
        var contesto = rapporto().con(ordinario(2023, 1))
                .inErrore(new Competenza.Mensilita(2023, 2), null)
                .inErrore(new Competenza.Mensilita(2023, 3), MotivoErrore.PDF_NON_VALIDO)
                .inErrore(new Competenza.Mensilita(2023, 4), MotivoErrore.PDF_TROPPE_PAGINE)
                .inErrore(new Competenza.Mensilita(2023, 5), MotivoErrore.PIU_CEDOLINI)
                // file in errore dopo un riavvio: il motivo conserverebbe il PDF, ma i byte non c'erano piu'
                .inErrore(new Competenza.Mensilita(2023, 6), MotivoErrore.INTERROTTO, false)
                .build();

        assertThat(regola.valuta(contesto)).extracting(SegnalazioneRilevata::descrizione).containsExactly(
                "Il cedolino di Febbraio 2023 è stato caricato ma non è stato letto: il PDF non è stato conservato, "
                        + "caricalo di nuovo.",
                "Il cedolino di Marzo 2023 è stato caricato ma non è stato letto (File danneggiato o non PDF): "
                        + "caricane una copia valida.",
                "Il cedolino di Aprile 2023 è stato caricato ma non è stato letto (Troppe pagine): dividi il PDF in "
                        + "un file per cedolino e caricali.",
                "Il cedolino di Maggio 2023 è stato caricato ma non è stato letto (Più cedolini nello stesso PDF): "
                        + "dividi il PDF in un file per cedolino e caricali.",
                "Il cedolino di Giugno 2023 è stato caricato ma non è stato letto (Elaborazione interrotta): il PDF "
                        + "non è stato conservato, caricalo di nuovo.");
        assertThat(regola.valuta(contesto)).extracting(SegnalazioneRilevata::descrizione)
                .noneMatch(d -> d.contains("a mano") || d.contains("riprova"));
    }

    @Test
    void nessunaSegnalazioneSeLaMensilitaHaGiaIlCedolino() {
        var contesto = rapporto().con(ordinario(2023, 5), tredicesima(2023))
                .inErrore(new Competenza.Mensilita(2023, 5), MotivoErrore.LETTURA_NON_RIUSCITA)
                .inErrore(Competenza.Mensilita.tredicesima(2023), MotivoErrore.LETTURA_NON_RIUSCITA)
                .build();

        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void senzaMensilitaInErroreNessunaSegnalazione() {
        var contesto = rapporto().con(ordinario(2023, 4), ordinario(2023, 6)).build();

        assertThat(contesto.mensilitaInErrore()).isEmpty();
        assertThat(regola.valuta(contesto)).isEmpty();
    }

    @Test
    void ilContestoOrdinaLeMensilitaEAccettaMotiviNulliEMappeNulle() {
        var base = rapporto().con(ordinario(2023, 4)).build();
        var conMappaNulla = new ContestoRapporto(base.rapporto(), null, base.cedolini(), base.oggi(), null);
        var conMotivoNullo = new ContestoRapporto(base.rapporto(), null, base.cedolini(), base.oggi(),
                Map.of(new Competenza.Mensilita(2023, 5), new FileInErrore(null, false)));
        var interrotto = new FileInErrore(MotivoErrore.INTERROTTO, true);
        var inDisordine = new ContestoRapporto(base.rapporto(), null, base.cedolini(), base.oggi(), Map.of(
                Competenza.Mensilita.tredicesima(2023), interrotto,
                new Competenza.Mensilita(2023, 12), interrotto,
                new Competenza.Mensilita(2023, 2), interrotto));

        assertThat(conMappaNulla.mensilitaInErrore()).isEmpty();
        assertThat(conMotivoNullo.inErrore(2023, 5)).isTrue();
        assertThat(conMotivoNullo.inErrore(2023, 6)).isFalse();
        assertThat(inDisordine.mensilitaInErrore().keySet()).containsExactly(new Competenza.Mensilita(2023, 2),
                new Competenza.Mensilita(2023, 12), Competenza.Mensilita.tredicesima(2023));
    }
}
