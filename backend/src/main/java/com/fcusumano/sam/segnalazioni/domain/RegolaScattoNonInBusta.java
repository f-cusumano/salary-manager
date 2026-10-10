package com.fcusumano.sam.segnalazioni.domain;

import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.segnalazioni.Evidenza;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * SCATTO_NON_IN_BUSTA (avviso): un cedolino stampa il prossimo scatto di anzianita' alla data D, ma il primo cedolino
 * ordinario con periodo dal mese successivo a D non mostra un aumento del numero o dell'importo degli scatti rispetto
 * all'ultima mensilita' ordinaria precedente al mese di D.
 * <p>
 * Non si segnala se:
 * <ul>
 *   <li>mancano la mensilita' precedente a D o un cedolino ordinario dal mese successivo a D;</li>
 *   <li>numero e importo degli scatti non sono confrontabili (non stampati su entrambi i cedolini);</li>
 *   <li>il CCNL (se noto) indica il numero massimo di scatti e questo era gia' raggiunto;</li>
 *   <li>un cedolino successivo stampa una data del prossimo scatto posteriore a D (scatto rinviato dal software paghe,
 *       ad esempio per periodi non utili all'anzianita');</li>
 *   <li>numero o importo degli scatti calano: lo segnala gia' SCATTI_IN_CALO.</li>
 * </ul>
 */
public final class RegolaScattoNonInBusta implements RegolaSegnalazione {

    public static final String CODICE = TipoSegnalazione.SCATTO_NON_IN_BUSTA.name();

    @Override
    public String codice() {
        return CODICE;
    }

    @Override
    public List<SegnalazioneRilevata> valuta(ContestoRapporto contesto) {
        var ordinari = contesto.ordinari();
        var date = ordinari.stream().map(CedolinoNormalizzato::prossimoScatto).filter(Objects::nonNull)
                .distinct().sorted().toList();
        var risultato = new ArrayList<SegnalazioneRilevata>();
        for (var data : date) {
            valutaData(contesto, ordinari, data).ifPresent(risultato::add);
        }
        return risultato;
    }

    private Optional<SegnalazioneRilevata> valutaData(ContestoRapporto contesto, List<CedolinoNormalizzato> ordinari,
                                                      LocalDate dataScatto) {
        var meseScatto = YearMonth.from(dataScatto);
        var baseOpt = ordinari.stream().filter(c -> ContestoRapporto.periodo(c).isBefore(meseScatto)).reduce((a, b) -> b);
        var verificaOpt = ordinari.stream().filter(c -> ContestoRapporto.periodo(c).isAfter(meseScatto)).findFirst();
        if (baseOpt.isEmpty() || verificaOpt.isEmpty()) {
            return Optional.empty();
        }
        var base = baseOpt.get();
        var verifica = verificaOpt.get();
        var numeroPrima = base.numeroScatti();
        var numeroDopo = verifica.numeroScatti();
        var importoPrima = base.elementiRetributivi().importoScatti();
        var importoDopo = verifica.elementiRetributivi().importoScatti();
        boolean numeroConfrontabile = numeroPrima != null && numeroDopo != null;
        boolean importoConfrontabile = importoPrima != null && importoDopo != null;
        if (!numeroConfrontabile && !importoConfrontabile) {
            return Optional.empty();
        }
        boolean aumento = numeroConfrontabile && numeroDopo > numeroPrima
                || importoConfrontabile && importoDopo.compareTo(importoPrima) > 0;
        boolean calo = numeroConfrontabile && numeroDopo < numeroPrima
                || importoConfrontabile && importoDopo.compareTo(importoPrima) < 0;
        if (aumento || calo || massimoRaggiunto(contesto, numeroPrima) || rinviato(ordinari, base, verifica, dataScatto)) {
            return Optional.empty();
        }
        var fonte = ordinari.stream().filter(c -> dataScatto.equals(c.prossimoScatto())
                && ContestoRapporto.periodo(c).isBefore(meseScatto)).reduce((a, b) -> b)
                .orElseGet(() -> ordinari.stream().filter(c -> dataScatto.equals(c.prossimoScatto())).findFirst()
                        .orElseThrow());

        var valori = new ArrayList<String>();
        var evidenze = new ArrayList<Evidenza>();
        evidenze.add(Evidenze.valore("Prossimo scatto stampato", fonte, Formato.data(dataScatto)));
        if (numeroConfrontabile) {
            valori.add("numero di scatti " + numeroPrima + " e " + numeroDopo);
            evidenze.add(Evidenze.valore("Numero scatti", base, String.valueOf(numeroPrima)));
            evidenze.add(Evidenze.valore("Numero scatti", verifica, String.valueOf(numeroDopo)));
        }
        if (importoConfrontabile) {
            valori.add("importo mensile " + Formato.euro(importoPrima) + " e " + Formato.euro(importoDopo));
            evidenze.add(Evidenze.importo("Importo scatti", base, importoPrima));
            evidenze.add(Evidenze.importo("Importo scatti", verifica, importoDopo));
        }
        var descrizione = "Il cedolino di " + Formato.competenza(fonte.competenza()) + " indica il prossimo scatto di "
                + "anzianità al " + Formato.data(dataScatto) + ", ma nel cedolino di "
                + Formato.competenza(verifica.competenza()) + " gli scatti non aumentano rispetto al cedolino di "
                + Formato.competenza(base.competenza()) + " (" + String.join("; ", valori) + ").";
        return Optional.of(new SegnalazioneRilevata(CODICE, TipoSegnalazione.SCATTO_NON_IN_BUSTA, Gravita.AVVISO,
                verifica.id(), null, "Scatto di anzianità non in busta: " + Formato.competenza(verifica.competenza()),
                descrizione, evidenze));
    }

    private static boolean massimoRaggiunto(ContestoRapporto contesto, Integer numeroPrima) {
        var ccnl = contesto.ccnl();
        return ccnl != null && ccnl.maxScatti() != null && numeroPrima != null && numeroPrima >= ccnl.maxScatti();
    }

    /** Un cedolino dopo la base (fino a quello di verifica) stampa un prossimo scatto posteriore a D. */
    private static boolean rinviato(List<CedolinoNormalizzato> ordinari, CedolinoNormalizzato base,
                                    CedolinoNormalizzato verifica, LocalDate dataScatto) {
        var da = ContestoRapporto.periodo(base);
        var a = ContestoRapporto.periodo(verifica);
        return ordinari.stream()
                .filter(c -> ContestoRapporto.periodo(c).isAfter(da) && !ContestoRapporto.periodo(c).isAfter(a))
                .map(CedolinoNormalizzato::prossimoScatto)
                .anyMatch(d -> d != null && d.isAfter(dataScatto));
    }
}
