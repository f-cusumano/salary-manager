package com.fcusumano.sam.segnalazioni.domain;

import com.fcusumano.sam.anagrafica.RapportoLavoro;
import com.fcusumano.sam.ccnl.Ccnl;
import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.cedolini.MotivoErrore;
import com.fcusumano.sam.shared.Competenza;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Costruttore di contesti per i test delle regole (dati inventati).
 */
final class ContestoDiProva {

    static final Ccnl COMMERCIO = new Ccnl("COMMERCIO", "Terziario, distribuzione e servizi", 14, 36, 10, 7, null);
    static final Ccnl METALMECCANICO = new Ccnl("METALMECCANICO_INDUSTRIA", "Metalmeccanico industria", 13, 24, 5,
            null, null);
    static final Ccnl TELECOMUNICAZIONI = new Ccnl("TELECOMUNICAZIONI", "Telecomunicazioni", 14, null, null, 7,
            "Verificare il mese di pagamento della quattordicesima.");
    static final Ccnl EDILIZIA = new Ccnl("EDILIZIA_INDUSTRIA", "Edilizia industria", 13, null, null, null,
            "Tredicesima degli operai presso la Cassa Edile.");
    static final LocalDate OGGI = LocalDate.of(2026, 10, 7);

    private LocalDate assunzione;
    private LocalDate cessazione;
    private String qualifica;
    private Ccnl ccnl;
    private LocalDate oggi = OGGI;
    private final List<CedolinoNormalizzato> cedolini = new ArrayList<>();
    private final Map<Competenza.Mensilita, ContestoRapporto.FileInErrore> inErrore = new HashMap<>();

    static ContestoDiProva rapporto() {
        return new ContestoDiProva();
    }

    ContestoDiProva assunzione(LocalDate data) {
        this.assunzione = data;
        return this;
    }

    ContestoDiProva cessazione(LocalDate data) {
        this.cessazione = data;
        return this;
    }

    ContestoDiProva qualifica(String qualifica) {
        this.qualifica = qualifica;
        return this;
    }

    ContestoDiProva ccnl(Ccnl ccnl) {
        this.ccnl = ccnl;
        return this;
    }

    ContestoDiProva oggi(LocalDate oggi) {
        this.oggi = oggi;
        return this;
    }

    ContestoDiProva con(CedolinoDiProva... cedolini) {
        for (var cedolino : cedolini) {
            this.cedolini.add(cedolino.build());
        }
        return this;
    }

    ContestoDiProva con(Collection<CedolinoNormalizzato> cedolini) {
        this.cedolini.addAll(cedolini);
        return this;
    }

    /**
     * Mensilita' a cui l'utente ha associato un file caricato con errore (motivo null = non registrato), con il PDF
     * conservato se il motivo lo prevede.
     */
    ContestoDiProva inErrore(Competenza.Mensilita competenza, MotivoErrore motivo) {
        return inErrore(competenza, motivo, motivo != null && motivo.conservaPdf());
    }

    /** Come {@link #inErrore(Competenza.Mensilita, MotivoErrore)}, con la presenza del PDF esplicita. */
    ContestoDiProva inErrore(Competenza.Mensilita competenza, MotivoErrore motivo, boolean pdfDisponibile) {
        inErrore.put(competenza, new ContestoRapporto.FileInErrore(motivo, pdfDisponibile));
        return this;
    }

    /** Sostituisce il cedolino con la stessa competenza (per variare un mese di una serie). */
    ContestoDiProva sostituisci(CedolinoDiProva cedolino) {
        var nuovo = cedolino.build();
        cedolini.removeIf(c -> c.competenza().equals(nuovo.competenza()));
        cedolini.add(nuovo);
        return this;
    }

    ContestoRapporto build() {
        var rapporto = new RapportoLavoro(CedolinoDiProva.RAPPORTO, CedolinoDiProva.PERSONA, CedolinoDiProva.AZIENDA,
                assunzione, cessazione, qualifica, null, null, ccnl == null ? null : ccnl.codice(), false, null, null, null);
        return inErrore.isEmpty() ? new ContestoRapporto(rapporto, ccnl, cedolini, oggi)
                : new ContestoRapporto(rapporto, ccnl, cedolini, oggi, inErrore);
    }
}
