package com.fcusumano.sam.segnalazioni.domain;

import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.segnalazioni.Evidenza;
import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import com.fcusumano.sam.shared.TipoMensilita;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * FINE_RAPPORTO_MANCANTE (avviso): data di cessazione nota, nessun cedolino di fine rapporto e nessun cedolino dal mese
 * di cessazione in poi con TFR liquidato.
 * <p>
 * Esclusioni:
 * <ul>
 *   <li>cessazione troppo recente rispetto a oggi (meno dei mesi di attesa configurati): la liquidazione puo' non
 *       essere ancora stata elaborata;</li>
 *   <li>mensilita' ordinarie successive al mese di cessazione: il rapporto e' proseguito (riassunzione) e la data
 *       stampata non chiude il rapporto;</li>
 *   <li>TFR interamente versato a un fondo pensione (nessun fondo in azienda stampato e quote a fondi presenti): il
 *       datore di lavoro non deve liquidare TFR.</li>
 * </ul>
 */
public final class RegolaFineRapportoMancante implements RegolaSegnalazione {

    public static final String CODICE = TipoSegnalazione.FINE_RAPPORTO_MANCANTE.name();

    private final int mesiAttesa;

    /**
     * @param mesiAttesa mesi dopo la cessazione entro i quali l'assenza del cedolino finale non e' segnalata
     */
    public RegolaFineRapportoMancante(int mesiAttesa) {
        if (mesiAttesa < 0) {
            throw new IllegalArgumentException("Mesi di attesa non validi: " + mesiAttesa);
        }
        this.mesiAttesa = mesiAttesa;
    }

    @Override
    public String codice() {
        return CODICE;
    }

    @Override
    public List<SegnalazioneRilevata> valuta(ContestoRapporto contesto) {
        var cessazione = contesto.dataCessazione();
        if (cessazione == null || contesto.cedolini().isEmpty()
                || contesto.oggi().isBefore(cessazione.plusMonths(mesiAttesa))) {
            return List.of();
        }
        var meseCessazione = YearMonth.from(cessazione);
        boolean liquidazione = contesto.cedolini().stream()
                .anyMatch(c -> c.tipo() == TipoMensilita.FINE_RAPPORTO
                        || (!ContestoRapporto.periodo(c).isBefore(meseCessazione) && Attivita.tfrLiquidato(c)));
        if (liquidazione || contesto.ordinariDopo(meseCessazione) || tfrInteramenteAFondi(contesto)) {
            return List.of();
        }
        var riferimento = contesto.ordinario(meseCessazione).orElse(contesto.cedolini().getLast());
        var evidenze = new ArrayList<Evidenza>();
        evidenze.add(Evidenze.data("Data di cessazione", cessazione));
        evidenze.add(Evidenze.cedolino("Ultimo cedolino caricato", contesto.cedolini().getLast()));
        var descrizione = "Il rapporto risulta cessato il " + Formato.data(cessazione) + ", ma non è stato caricato un "
                + "cedolino di fine rapporto né un cedolino con la liquidazione del TFR. Se la liquidazione è già "
                + "avvenuta, carica il relativo cedolino.";
        return List.of(new SegnalazioneRilevata(CODICE, TipoSegnalazione.FINE_RAPPORTO_MANCANTE, Gravita.AVVISO,
                riferimento.id(), null, "Cedolino di fine rapporto mancante", descrizione, evidenze));
    }

    /** Nessun fondo TFR in azienda stampato (assente o zero) e quote versate a fondi presenti. */
    private static boolean tfrInteramenteAFondi(ContestoRapporto contesto) {
        var tfr = contesto.cedolini().stream().map(CedolinoNormalizzato::tfr).filter(Objects::nonNull).toList();
        boolean fondoInAzienda = tfr.stream().anyMatch(t -> Attivita.positivo(t.fondo31DicembreAnnoPrecedente())
                || Attivita.positivo(t.quotaAnno()) && !Attivita.positivo(t.quotaFondi()));
        boolean quoteAFondi = tfr.stream().anyMatch(t -> Attivita.positivo(t.quotaFondi()));
        return quoteAFondi && !fondoInAzienda;
    }
}
