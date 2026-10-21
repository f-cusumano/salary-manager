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
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * TREDICESIMA_MANCANTE (anomalia). Per ogni anno Y in cui il rapporto ha almeno una mensilita' ordinaria si segnala la
 * tredicesima mancante solo se:
 * <ul>
 *   <li>l'anno e' concluso nei dati: esiste un cedolino con periodo successivo a dicembre Y;</li>
 *   <li>la mensilita' ordinaria di dicembre Y e' caricata (per il caso contrario vedi sotto);</li>
 *   <li>non esiste il cedolino 13 di Y e nessun cedolino di Y (ne' quello di gennaio Y+1, per i pagamenti tardivi)
 *       riporta tredicesima o suoi ratei ({@code contieneTredicesima});</li>
 *   <li>l'utente non ha associato alla tredicesima di Y un file caricato con errore (lo segnala
 *       CEDOLINO_IN_ERRORE);</li>
 *   <li>il rapporto non e' cessato in Y;</li>
 *   <li>nell'anno sono maturati ratei: un'assunzione nell'ultima parte di dicembre (meno di 15 giorni) non ne
 *       matura; se la data di assunzione non e' nota e il primo cedolino ordinario e' di dicembre Y, il primo mese
 *       potrebbe essere parziale e l'anno non si valuta.</li>
 * </ul>
 * Se il rapporto e' cessato in Y i ratei vanno liquidati con le competenze finali: se manca il cedolino finale lo
 * segnala gia' FINE_RAPPORTO_MANCANTE; se c'e' (cedolino di fine rapporto o cedolino con TFR liquidato dal mese di
 * cessazione) ma nessun cedolino dal mese di cessazione riporta la tredicesima o i suoi ratei, si segnala con una
 * descrizione specifica (senza competenza: non e' attesa una mensilita' separata).
 * <p>
 * Se il cedolino ordinario di dicembre Y manca (segnalato come mensilita' mancante) o l'utente vi ha associato un file
 * in errore, la tredicesima potrebbe essere pagata proprio in quel cedolino. Valgono le altre condizioni e si guardano
 * gli altri anni del rapporto: se la tredicesima ha sempre un cedolino a parte si segnala come anomalia, se e' sempre
 * nel cedolino ordinario non si segnala, se non ci sono indizi si segnala come avviso. Anche gli anni senza alcun
 * cedolino ordinario caricato (tutti mancanti) si valutano cosi'. La riga della tredicesima compare quindi tra le
 * mensilita' mancanti e le si puo' associare un file in errore.
 * <p>
 * Per i CCNL in cui la tredicesima degli operai e' accantonata presso la Cassa Edile (configurabili) la regola non si
 * applica, salvo qualifica di impiegato o quadro.
 */
public final class RegolaTredicesimaMancante implements RegolaSegnalazione {

    public static final String CODICE = TipoSegnalazione.TREDICESIMA_MANCANTE.name();

    private final Set<String> ccnlTredicesimaCassaEdile;

    /**
     * @param ccnlTredicesimaCassaEdile codici CCNL in cui la tredicesima degli operai non e' pagata dal datore di lavoro
     */
    public RegolaTredicesimaMancante(Set<String> ccnlTredicesimaCassaEdile) {
        this.ccnlTredicesimaCassaEdile = Set.copyOf(ccnlTredicesimaCassaEdile);
    }

    @Override
    public String codice() {
        return CODICE;
    }

    @Override
    public List<SegnalazioneRilevata> valuta(ContestoRapporto contesto) {
        if (tredicesimaInCassaEdile(contesto)) {
            return List.of();
        }
        var mesiMancanti = RegolaMensilitaMancante.mesiMancanti(contesto);
        return Stream.concat(contesto.ordinari().stream().map(c -> c.competenza().anno()),
                        mesiMancanti.stream().map(YearMonth::getYear))
                .distinct()
                .sorted()
                .map(anno -> valutaAnno(contesto, anno, mesiMancanti))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<SegnalazioneRilevata> valutaAnno(ContestoRapporto contesto, int anno, List<YearMonth> mesiMancanti) {
        if (contesto.esisteCompetenza(anno, 13) || contesto.inErrore(anno, 13) || tredicesimaPagata(contesto, anno)) {
            return Optional.empty();
        }
        var cessazione = contesto.dataCessazione();
        if (cessazione != null && cessazione.getYear() == anno && !contesto.ordinariDopo(YearMonth.from(cessazione))) {
            return cessatoNellAnno(contesto, anno, cessazione);
        }
        var assunzione = contesto.dataAssunzione();
        if (assunzione != null && assunzione.getYear() == anno
                && Attivita.giorni(assunzione, LocalDate.of(anno, 12, 31)) < Attivita.GIORNI_MINIMI_RATEO) {
            return Optional.empty();
        }
        var dicembre = YearMonth.of(anno, 12);
        if (assunzione == null && ContestoRapporto.periodo(contesto.ordinari().getFirst()).equals(dicembre)) {
            return Optional.empty();
        }
        if (contesto.ordinario(dicembre).isEmpty()) {
            return MensilitaAggiuntiva.nonDisponibile(contesto, mesiMancanti, dicembre)
                    ? dicembreNonDisponibile(contesto, anno) : Optional.empty();
        }
        return contesto.primoDopo(dicembre).map(successivo -> {
            var evidenze = new ArrayList<Evidenza>();
            evidenze.add(Evidenze.cedolino("Cedolino di dicembre", contesto.ordinario(dicembre).orElseThrow()));
            evidenze.add(Evidenze.cedolino("Primo cedolino successivo", successivo));
            var descrizione = "Per il " + anno + " non risulta la tredicesima: non è stato caricato il cedolino della "
                    + "tredicesima e nessun cedolino del " + anno + " ne riporta le voci, anche se sono presenti cedolini "
                    + "successivi a " + Formato.mese(dicembre) + ".";
            return new SegnalazioneRilevata(CODICE, TipoSegnalazione.TREDICESIMA_MANCANTE, Gravita.ANOMALIA, null,
                    Competenza.Mensilita.tredicesima(anno), "Tredicesima " + anno + " mancante", descrizione, evidenze);
        });
    }

    /**
     * Manca il cedolino di dicembre (o e' in errore): la tredicesima potrebbe esserci dentro. Anomalia se negli altri
     * anni ha un cedolino a parte, avviso se non si sa, nulla se negli altri anni e' pagata nel cedolino ordinario.
     */
    private static Optional<SegnalazioneRilevata> dicembreNonDisponibile(ContestoRapporto contesto, int anno) {
        var dicembre = YearMonth.of(anno, 12);
        var successivo = contesto.primoDopo(dicembre);
        var modalita = MensilitaAggiuntiva.modalita(contesto, TipoMensilita.TREDICESIMA, anno);
        if (successivo.isEmpty() || modalita == MensilitaAggiuntiva.Modalita.NEL_CEDOLINO_ORDINARIO) {
            return Optional.empty();
        }
        var statoDicembre = contesto.inErrore(anno, 12) ? "è stato caricato con errore"
                : "non è stato caricato";
        var evidenze = new ArrayList<Evidenza>();
        contesto.ordinari().stream().filter(c -> ContestoRapporto.periodo(c).isBefore(dicembre)).reduce((a, b) -> b)
                .ifPresent(c -> evidenze.add(Evidenze.cedolino("Ultimo cedolino prima di dicembre", c)));
        evidenze.add(Evidenze.cedolino("Primo cedolino successivo", successivo.get()));
        if (modalita == MensilitaAggiuntiva.Modalita.A_PARTE) {
            MensilitaAggiuntiva.esempioAParte(contesto, TipoMensilita.TREDICESIMA, anno)
                    .ifPresent(c -> evidenze.add(Evidenze.cedolino("Tredicesima con cedolino a parte", c)));
            var descrizione = "Per il " + anno + " non risulta la tredicesima: negli altri anni del rapporto è pagata con "
                    + "un cedolino a parte, che per il " + anno + " non è stato caricato. Anche il cedolino di "
                    + Formato.mese(dicembre) + " " + statoDicembre + ".";
            return Optional.of(new SegnalazioneRilevata(CODICE, TipoSegnalazione.TREDICESIMA_MANCANTE,
                    Gravita.ANOMALIA, null, Competenza.Mensilita.tredicesima(anno), "Tredicesima " + anno + " mancante",
                    descrizione, evidenze));
        }
        var descrizione = "Per il " + anno + " non risulta la tredicesima, ma il cedolino di " + Formato.mese(dicembre)
                + " " + statoDicembre + ": la tredicesima potrebbe essere pagata in quel cedolino oppure con un "
                + "cedolino a parte, anch'esso non caricato.";
        return Optional.of(new SegnalazioneRilevata(CODICE, TipoSegnalazione.TREDICESIMA_MANCANTE, Gravita.AVVISO,
                null, Competenza.Mensilita.tredicesima(anno), "Tredicesima " + anno + " non trovata", descrizione,
                evidenze));
    }

    private Optional<SegnalazioneRilevata> cessatoNellAnno(ContestoRapporto contesto, int anno, LocalDate cessazione) {
        var meseCessazione = YearMonth.from(cessazione);
        var finali = contesto.cedolini().stream()
                .filter(c -> c.tipo() == TipoMensilita.FINE_RAPPORTO
                        || (!ContestoRapporto.periodo(c).isBefore(meseCessazione) && Attivita.tfrLiquidato(c)))
                .toList();
        if (finali.isEmpty()) {
            // Manca il cedolino finale: lo segnala FINE_RAPPORTO_MANCANTE
            return Optional.empty();
        }
        boolean ratei = contesto.cedolini().stream()
                .anyMatch(c -> c.contieneTredicesima() && !ContestoRapporto.periodo(c).isBefore(meseCessazione));
        var assunzione = contesto.dataAssunzione();
        var inizio = LocalDate.of(anno, 1, 1);
        if (assunzione != null) {
            inizio = Attivita.max(inizio, assunzione);
        }
        if (ratei || Attivita.giorni(inizio, cessazione) < Attivita.GIORNI_MINIMI_RATEO) {
            return Optional.empty();
        }
        var evidenze = new ArrayList<Evidenza>();
        evidenze.add(Evidenze.data("Data di cessazione", cessazione));
        finali.forEach(c -> evidenze.add(Evidenze.cedolino("Cedolino finale", c)));
        var descrizione = "Il rapporto è cessato il " + Formato.data(cessazione) + ": i ratei di tredicesima maturati nel "
                + anno + " vanno liquidati con le competenze finali, ma né il cedolino finale né gli altri cedolini dal "
                + "mese di cessazione riportano la tredicesima o i suoi ratei.";
        return Optional.of(new SegnalazioneRilevata(CODICE, TipoSegnalazione.TREDICESIMA_MANCANTE, Gravita.ANOMALIA,
                finali.getLast().id(), null, "Ratei di tredicesima " + anno + " non liquidati", descrizione, evidenze));
    }

    /** Un cedolino dell'anno, o quello di gennaio successivo, paga la tredicesima o i suoi ratei. */
    private static boolean tredicesimaPagata(ContestoRapporto contesto, int anno) {
        var gennaioSuccessivo = YearMonth.of(anno + 1, 1);
        return contesto.cedolini().stream().filter(CedolinoNormalizzato::contieneTredicesima)
                .anyMatch(c -> c.competenza().anno() == anno || ContestoRapporto.periodo(c).equals(gennaioSuccessivo));
    }

    private boolean tredicesimaInCassaEdile(ContestoRapporto contesto) {
        if (contesto.ccnl() == null || !ccnlTredicesimaCassaEdile.contains(contesto.ccnl().codice())) {
            return false;
        }
        var qualifica = contesto.rapporto().qualifica();
        if (qualifica == null) {
            qualifica = contesto.cedolini().stream().map(CedolinoNormalizzato::qualifica)
                    .filter(q -> q != null && !q.isBlank()).reduce((a, b) -> b).orElse("");
        }
        var q = qualifica.toLowerCase(Locale.ROOT);
        return !(q.contains("impieg") || q.contains("quadr"));
    }
}
