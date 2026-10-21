package com.fcusumano.sam.segnalazioni.domain;

import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.segnalazioni.Evidenza;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.TipoMensilita;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * QUATTORDICESIMA_MANCANTE (anomalia). Solo se il CCNL e' noto, prevede 14 mensilita' e ha il mese di pagamento M
 * (CCNL non noto: nessuna segnalazione). Per un anno Y si segnala se:
 * <ul>
 *   <li>esistono cedolini con periodo successivo a M/Y; se il CCNL ha valori da verificare ({@code noteVerifica}) il
 *       mese di pagamento non e' certo e si richiede che l'anno sia concluso (cedolini successivi a dicembre Y);</li>
 *   <li>il rapporto era attivo per almeno 15 giorni nel periodo di maturazione, da M dell'anno Y-1 a M-1 dell'anno Y
 *       (inizio = data di assunzione; se non nota, primo cedolino, accettato solo se precede l'ultimo mese del
 *       periodo, perche' il primo mese potrebbe essere parziale);</li>
 *   <li>i cedolini in cui la quattordicesima sarebbe stata pagata sono caricati: la mensilita' ordinaria di M/Y (o,
 *       con mese di pagamento da verificare, tutte le mensilita' ordinarie dell'anno); per il caso contrario vedi
 *       sotto;</li>
 *   <li>il rapporto non e' cessato prima di M/Y (in quel caso i ratei vanno nelle competenze finali);</li>
 *   <li>non esiste il cedolino 14 di Y e nessun cedolino di Y riporta quattordicesima o suoi ratei;</li>
 *   <li>l'utente non ha associato alla quattordicesima di Y un file caricato con errore (lo segnala
 *       CEDOLINO_IN_ERRORE);</li>
 *   <li>nessun cedolino del periodo da M/Y-1 a M/Y stampa un CCNL diverso da quello stampato sul cedolino piu' recente
 *       che lo riporta: con un cambio di contratto documentato (per esempio da un CCNL a 13 mensilita') il CCNL
 *       attuale non vale per quell'anno e la quattordicesima potrebbe non essere dovuta.</li>
 * </ul>
 * Se invece quei cedolini mancano (segnalati come mensilita' mancanti) o l'utente vi ha associato file in errore, la
 * quattordicesima potrebbe trovarsi proprio li'. Valgono le altre condizioni e si guardano gli altri anni del rapporto:
 * se la quattordicesima ha sempre un cedolino a parte si segnala come anomalia, se e' sempre nel cedolino ordinario
 * non si segnala, se non ci sono indizi si segnala come avviso. La riga della quattordicesima compare quindi tra le
 * mensilita' mancanti e le si puo' associare un file in errore.
 */
public final class RegolaQuattordicesimaMancante implements RegolaSegnalazione {

    public static final String CODICE = TipoSegnalazione.QUATTORDICESIMA_MANCANTE.name();

    @Override
    public String codice() {
        return CODICE;
    }

    @Override
    public List<SegnalazioneRilevata> valuta(ContestoRapporto contesto) {
        var ccnl = contesto.ccnl();
        if (ccnl == null || !ccnl.prevedeQuattordicesima() || ccnl.meseQuattordicesima() == null
                || contesto.cedolini().isEmpty()) {
            return List.of();
        }
        var inizio = inizio(contesto);
        int primoAnno = inizio.anno();
        int ultimoAnno = contesto.cedolini().stream().map(ContestoRapporto::periodo).mapToInt(YearMonth::getYear)
                .max().orElse(primoAnno);
        var mesiMancanti = RegolaMensilitaMancante.mesiMancanti(contesto);
        return IntStream.rangeClosed(primoAnno, ultimoAnno)
                .mapToObj(anno -> valutaAnno(contesto, anno, inizio, mesiMancanti))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<SegnalazioneRilevata> valutaAnno(ContestoRapporto contesto, int anno, Inizio inizio,
                                                      List<YearMonth> mesiMancanti) {
        var ccnl = contesto.ccnl();
        int mesePagamento = ccnl.meseQuattordicesima();
        var meseM = YearMonth.of(anno, mesePagamento);
        boolean meseCerto = ccnl.noteVerifica() == null;
        var soglia = meseCerto ? meseM : YearMonth.of(anno, 12);
        var successivo = contesto.primoDopo(soglia);
        if (successivo.isEmpty()) {
            return Optional.empty();
        }
        var ultimoMeseMaturazione = meseM.minusMonths(1);
        if (!attivoNelPeriodo(inizio, meseM.minusYears(1), ultimoMeseMaturazione)) {
            return Optional.empty();
        }
        var assenti = mesiDiPagamentoAssenti(contesto, anno, meseM, meseCerto, inizio);
        if (!assenti.stream().allMatch(mese -> MensilitaAggiuntiva.nonDisponibile(contesto, mesiMancanti, mese))) {
            return Optional.empty();
        }
        var modalita = assenti.isEmpty() ? null
                : MensilitaAggiuntiva.modalita(contesto, TipoMensilita.QUATTORDICESIMA, anno);
        if (modalita == MensilitaAggiuntiva.Modalita.NEL_CEDOLINO_ORDINARIO) {
            return Optional.empty();
        }
        var cessazione = contesto.dataCessazione();
        if (cessazione != null && cessazione.isBefore(meseM.atDay(1)) && !contesto.ordinariDopo(YearMonth.from(cessazione))) {
            return Optional.empty();
        }
        if (contesto.esisteCompetenza(anno, 14) || contesto.inErrore(anno, 14) || contesto.dellAnno(anno).stream()
                .anyMatch(CedolinoNormalizzato::contieneQuattordicesima)) {
            return Optional.empty();
        }
        if (ccnlCambiatoNelPeriodo(contesto, meseM.minusYears(1), meseM)) {
            return Optional.empty();
        }
        if (modalita != null) {
            return Optional.of(pagamentoNonDisponibile(contesto, anno, assenti, successivo.get(), modalita));
        }
        var evidenze = new ArrayList<Evidenza>();
        contesto.ordinario(meseM).ifPresent(c -> evidenze.add(Evidenze.cedolino("Cedolino del mese di pagamento", c)));
        evidenze.add(Evidenze.cedolino("Primo cedolino successivo", successivo.get()));
        evidenze.add(Evidenze.rapporto("Mese di pagamento della quattordicesima (CCNL)",
                Formato.nomeMese(mesePagamento)));
        var descrizione = "Il CCNL " + ccnl.descrizione() + " prevede la quattordicesima"
                + (meseCerto ? " a " + Formato.nomeMese(mesePagamento) : "")
                + ", ma per il " + anno + " non è stato caricato il cedolino della quattordicesima e nessun cedolino del "
                + anno + " ne riporta le voci, anche se sono presenti cedolini successivi a " + Formato.mese(soglia) + ".";
        return Optional.of(new SegnalazioneRilevata(CODICE, TipoSegnalazione.QUATTORDICESIMA_MANCANTE, Gravita.ANOMALIA,
                null, Competenza.Mensilita.quattordicesima(anno), "Quattordicesima " + anno + " mancante",
                descrizione, evidenze));
    }

    /**
     * Mancano cedolini in cui la quattordicesima potrebbe essere pagata (o sono in errore): anomalia se negli altri
     * anni ha un cedolino a parte, avviso se non si sa (la modalita' "nel cedolino ordinario" e' esclusa prima).
     */
    private static SegnalazioneRilevata pagamentoNonDisponibile(ContestoRapporto contesto, int anno,
                                                                List<YearMonth> assenti,
                                                                CedolinoNormalizzato successivo,
                                                                MensilitaAggiuntiva.Modalita modalita) {
        var ccnl = contesto.ccnl();
        var mesi = assenti.stream().map(Formato::mese).collect(Collectors.joining(", "));
        var stato = assenti.size() == 1
                ? "il cedolino di " + mesi + (contesto.inErrore(assenti.getFirst().getYear(),
                        assenti.getFirst().getMonthValue()) ? " è stato caricato con errore" : " non è stato caricato")
                : "i cedolini di " + mesi + " non sono stati caricati o sono in errore";
        var evidenze = new ArrayList<Evidenza>();
        evidenze.add(Evidenze.cedolino("Primo cedolino successivo", successivo));
        evidenze.add(Evidenze.rapporto("Mese di pagamento della quattordicesima (CCNL)",
                Formato.nomeMese(ccnl.meseQuattordicesima())));
        if (modalita == MensilitaAggiuntiva.Modalita.A_PARTE) {
            MensilitaAggiuntiva.esempioAParte(contesto, TipoMensilita.QUATTORDICESIMA, anno)
                    .ifPresent(c -> evidenze.add(Evidenze.cedolino("Quattordicesima con cedolino a parte", c)));
            var descrizione = "Il CCNL " + ccnl.descrizione() + " prevede la quattordicesima, ma per il " + anno
                    + " non risulta: negli altri anni del rapporto è pagata con un cedolino a parte, che per il " + anno
                    + " non è stato caricato. Inoltre " + stato + ".";
            return new SegnalazioneRilevata(CODICE, TipoSegnalazione.QUATTORDICESIMA_MANCANTE, Gravita.ANOMALIA, null,
                    Competenza.Mensilita.quattordicesima(anno), "Quattordicesima " + anno + " mancante", descrizione,
                    evidenze);
        }
        var descrizione = "Il CCNL " + ccnl.descrizione() + " prevede la quattordicesima, ma per il " + anno
                + " non risulta e " + stato + ": la quattordicesima potrebbe essere pagata lì oppure con un cedolino a "
                + "parte, anch'esso non caricato.";
        return new SegnalazioneRilevata(CODICE, TipoSegnalazione.QUATTORDICESIMA_MANCANTE, Gravita.AVVISO, null,
                Competenza.Mensilita.quattordicesima(anno), "Quattordicesima " + anno + " non trovata", descrizione,
                evidenze);
    }

    /**
     * Mesi senza cedolino ordinario tra quelli in cui la quattordicesima potrebbe essere stata pagata: il mese M se il
     * mese di pagamento e' certo, altrimenti tutti i mesi dell'anno (dall'inizio del rapporto). Vuoto se sono tutti
     * caricati.
     */
    private static List<YearMonth> mesiDiPagamentoAssenti(ContestoRapporto contesto, int anno, YearMonth meseM,
                                                          boolean meseCerto, Inizio inizio) {
        if (meseCerto) {
            return contesto.ordinario(meseM).isPresent() ? List.of() : List.of(meseM);
        }
        var assenti = new ArrayList<YearMonth>();
        var primoMese = inizio.primoMese().getYear() == anno ? inizio.primoMese() : YearMonth.of(anno, 1);
        for (var mese = primoMese; mese.getYear() == anno; mese = mese.plusMonths(1)) {
            if (contesto.ordinario(mese).isEmpty()) {
                assenti.add(mese);
            }
        }
        return assenti;
    }

    /**
     * True se un cedolino con periodo tra i due mesi (compresi) stampa un CCNL diverso da quello stampato sul cedolino
     * piu' recente del rapporto che lo riporta.
     */
    private static boolean ccnlCambiatoNelPeriodo(ContestoRapporto contesto, YearMonth primoMese, YearMonth ultimoMese) {
        var recente = contesto.cedolini().reversed().stream()
                .map(CedolinoNormalizzato::ccnlTesto)
                .filter(t -> t != null && !t.isBlank())
                .findFirst();
        return recente.isPresent() && contesto.cedolini().stream()
                .filter(c -> {
                    var periodo = ContestoRapporto.periodo(c);
                    return !periodo.isBefore(primoMese) && !periodo.isAfter(ultimoMese);
                })
                .anyMatch(c -> Attivita.diversiEntrambiPresenti(c.ccnlTesto(), recente.get()));
    }

    /** Almeno 15 giorni di attivita' tra l'inizio del rapporto e la fine del periodo di maturazione. */
    private static boolean attivoNelPeriodo(Inizio inizio, YearMonth primoMese, YearMonth ultimoMese) {
        var fine = ultimoMese.atEndOfMonth();
        if (inizio.data() != null) {
            var da = Attivita.max(inizio.data(), primoMese.atDay(1));
            return Attivita.giorni(da, fine) >= Attivita.GIORNI_MINIMI_RATEO;
        }
        // Data di assunzione non nota: il primo mese con cedolino potrebbe essere parziale
        return inizio.primoMese().isBefore(ultimoMese);
    }

    private static Inizio inizio(ContestoRapporto contesto) {
        var assunzione = contesto.dataAssunzione();
        if (assunzione != null) {
            return new Inizio(assunzione, YearMonth.from(assunzione));
        }
        return new Inizio(null, ContestoRapporto.periodo(contesto.cedolini().getFirst()));
    }

    /**
     * Inizio del rapporto.
     *
     * @param data      data di assunzione, null se non stampata
     * @param primoMese mese di assunzione o, in mancanza, del primo cedolino
     */
    private record Inizio(LocalDate data, YearMonth primoMese) {

        int anno() {
            return primoMese.getYear();
        }
    }
}
