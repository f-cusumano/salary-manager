package com.fcusumano.sam.segnalazioni.domain;

import com.fcusumano.sam.segnalazioni.Gravita;
import com.fcusumano.sam.segnalazioni.TipoSegnalazione;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * FONDO_TFR_IN_CALO (anomalia): il fondo TFR al 31/12 stampato cala da un anno al successivo. Si confronta il fondo
 * al 31/12/Y-1 (stampato sui cedolini di Y) con il fondo al 31/12/Y (stampato sui cedolini di Y+1), oltre la
 * tolleranza configurata, escludendo gli anni Y con anticipi, quote a fondi pensione, liquidazioni, cedolini di fine
 * rapporto o cessazione, e gli anni con mensilita' ordinarie mancanti (potrebbero nascondere un anticipo).
 */
public final class RegolaFondoTfrInCalo implements RegolaSegnalazione {

    public static final String CODICE = TipoSegnalazione.FONDO_TFR_IN_CALO.name();

    private final BigDecimal tolleranza;

    /**
     * @param tolleranza calo minimo (euro) considerato: assorbe gli arrotondamenti di stampa
     */
    public RegolaFondoTfrInCalo(BigDecimal tolleranza) {
        this.tolleranza = Objects.requireNonNull(tolleranza, "tolleranza");
    }

    @Override
    public String codice() {
        return CODICE;
    }

    @Override
    public List<SegnalazioneRilevata> valuta(ContestoRapporto contesto) {
        var storico = new StoricoTfr(contesto, tolleranza);
        return storico.anniConFondo().stream()
                .map(storico::caloSenzaCauseNote)
                .flatMap(Optional::stream)
                .map(this::segnalazione)
                .toList();
    }

    private SegnalazioneRilevata segnalazione(StoricoTfr.CaloFondo calo) {
        int anno = calo.anno();
        var precedente = calo.precedente();
        var successivo = calo.successivo();
        var descrizione = "Il fondo TFR al " + Formato.fineAnno(anno) + " (" + Formato.euro(successivo.valore())
                + ", cedolino di " + Formato.competenza(successivo.cedolino().competenza()) + ") è inferiore a quello al "
                + Formato.fineAnno(anno - 1) + " (" + Formato.euro(precedente.valore()) + ", cedolino di "
                + Formato.competenza(precedente.cedolino().competenza()) + ") di " + Formato.euro(calo.differenza())
                + ". Nei cedolini del " + anno + " non risultano anticipi, liquidazioni o versamenti a fondi pensione "
                + "che lo giustifichino.";
        var evidenze = List.of(
                Evidenze.importo("Fondo TFR al " + Formato.fineAnno(anno - 1), precedente.cedolino(), precedente.valore()),
                Evidenze.importo("Fondo TFR al " + Formato.fineAnno(anno), successivo.cedolino(), successivo.valore()));
        return new SegnalazioneRilevata(CODICE, TipoSegnalazione.FONDO_TFR_IN_CALO, Gravita.ANOMALIA,
                successivo.cedolino().id(), null, "Fondo TFR in calo al " + Formato.fineAnno(anno), descrizione, evidenze);
    }
}
