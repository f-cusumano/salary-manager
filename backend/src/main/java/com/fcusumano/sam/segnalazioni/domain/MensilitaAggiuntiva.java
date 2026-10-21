package com.fcusumano.sam.segnalazioni.domain;

import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.shared.TipoMensilita;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Utilita' comuni alle regole di tredicesima e quattordicesima per gli anni in cui manca il cedolino del mese in cui
 * sarebbero pagate: dagli altri anni del rapporto si ricava se l'azienda le paga con un cedolino a parte o dentro il
 * cedolino ordinario.
 */
final class MensilitaAggiuntiva {

    /** Come l'azienda paga la mensilita' aggiuntiva negli altri anni del rapporto. */
    enum Modalita {
        /** Solo con un cedolino a parte (caricato, o associato a un file in errore). */
        A_PARTE,
        /** Solo dentro un cedolino ordinario. */
        NEL_CEDOLINO_ORDINARIO,
        /** Nessun indizio, o indizi in entrambi i sensi. */
        NON_NOTA
    }

    private MensilitaAggiuntiva() {
    }

    /**
     * Modalita' di pagamento della tredicesima (o quattordicesima) negli anni del rapporto diversi da {@code anno}.
     * Per la tredicesima contano solo i cedolini ordinari di dicembre e gennaio (non quelli con le competenze finali,
     * che ne liquidano i ratei); per la quattordicesima i cedolini ordinari senza TFR liquidato.
     */
    static Modalita modalita(ContestoRapporto contesto, TipoMensilita tipo, int anno) {
        boolean aParte = cedoliniAParte(contesto, tipo, anno).findAny().isPresent()
                || contesto.mensilitaInErrore().keySet().stream()
                        .anyMatch(m -> m.tipo() == tipo && m.anno() != anno);
        boolean nelOrdinario = contesto.ordinari().stream()
                .filter(c -> c.competenza().anno() != anno)
                .anyMatch(c -> tipo == TipoMensilita.TREDICESIMA
                        ? c.contieneTredicesima() && dicembreOGennaio(ContestoRapporto.periodo(c))
                        : c.contieneQuattordicesima() && !Attivita.tfrLiquidato(c));
        if (aParte && !nelOrdinario) {
            return Modalita.A_PARTE;
        }
        if (nelOrdinario && !aParte) {
            return Modalita.NEL_CEDOLINO_ORDINARIO;
        }
        return Modalita.NON_NOTA;
    }

    /** Ultimo cedolino a parte della mensilita' aggiuntiva di un altro anno (evidenza della modalita'). */
    static Optional<CedolinoNormalizzato> esempioAParte(ContestoRapporto contesto, TipoMensilita tipo, int anno) {
        return cedoliniAParte(contesto, tipo, anno).reduce((a, b) -> b);
    }

    /**
     * True se il cedolino ordinario del mese manca ma e' atteso (segnalato come mensilita' mancante) o se l'utente vi
     * ha associato un file in errore: in entrambi i casi non se ne conosce il contenuto.
     */
    static boolean nonDisponibile(ContestoRapporto contesto, List<YearMonth> mesiMancanti, YearMonth mese) {
        return contesto.ordinario(mese).isEmpty()
                && (mesiMancanti.contains(mese) || contesto.inErrore(mese.getYear(), mese.getMonthValue()));
    }

    private static Stream<CedolinoNormalizzato> cedoliniAParte(ContestoRapporto contesto, TipoMensilita tipo,
                                                               int anno) {
        return contesto.diTipo(tipo).stream().filter(c -> c.competenza().anno() != anno);
    }

    private static boolean dicembreOGennaio(YearMonth periodo) {
        return periodo.getMonthValue() == 12 || periodo.getMonthValue() == 1;
    }
}
