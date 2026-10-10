package com.fcusumano.sam.cedolini.adapter.locale;

import com.fcusumano.sam.cedolini.application.estrazione.CedolinoEstratto.TipoCedolinoEstratto;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException;
import com.fcusumano.sam.cedolini.application.estrazione.EstrazioneCedolinoException.Motivo;
import com.fcusumano.sam.cedolini.application.estrazione.InterpreteRispostaEstrazione;
import com.fcusumano.sam.cedolini.application.estrazione.LettoreCedolini;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lettore locale dei cedolini (predefinito, {@code salary-manager.lettore=locale}): legge il testo del PDF con le
 * posizioni dei caratteri (Apache PDFBox), riconosce il layout tra quelli noti ({@link LayoutCedolino}) e ne estrae i
 * dati nel JSON dello schema di estrazione, che passa poi dalla stessa normalizzazione e validazione semantica degli
 * altri lettori ({@link InterpreteRispostaEstrazione}). Gratuito, offline, deterministico.
 * <p>
 * Non indovina: un PDF senza testo (scansione) o con un layout sconosciuto, o in cui mancano i dati essenziali
 * (periodo, codice fiscale del dipendente, netto), produce un errore che suggerisce il lettore Claude. Il modello
 * registrato con il cedolino e' {@value #VERSIONE}/&lt;layout&gt; (es. "lettore-locale-1/job-sistemi"). Non logga
 * valori letti.
 */
@Component
@ConditionalOnProperty(name = "salary-manager.lettore", havingValue = "locale", matchIfMissing = true)
class LettoreLocaleCedolini implements LettoreCedolini {

    private static final Logger log = LoggerFactory.getLogger(LettoreLocaleCedolini.class);

    /** Versione del lettore: cambia quando cambiano le regole di lettura in modo da rendere utile una rilettura. */
    static final String VERSIONE = "lettore-locale-1";
    /** Caratteri alfanumerici minimi perche' il PDF sia considerato testuale (sotto e' una scansione). */
    static final int CARATTERI_MINIMI = 200;
    /** Layout supportati, in ordine di prova. */
    static final List<LayoutCedolino> LAYOUT = List.of(new LayoutJobSistemi(), new LayoutZucchetti(),
            new LayoutModuloM39320());
    static final String SUGGERIMENTO_CLAUDE =
            "Per questo cedolino puoi usare il lettore Claude (SAM_LETTORE=claude, a pagamento, richiede ANTHROPIC_API_KEY).";

    private final List<LayoutCedolino> layout;
    private final InterpreteRispostaEstrazione interprete;
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    LettoreLocaleCedolini(Clock clock) {
        this(LAYOUT, InterpreteRispostaEstrazione.standard(clock));
    }

    LettoreLocaleCedolini(List<LayoutCedolino> layout, InterpreteRispostaEstrazione interprete) {
        this.layout = List.copyOf(layout);
        this.interprete = interprete;
    }

    @Override
    public RisultatoEstrazione estrai(byte[] pdf) {
        var lettura = leggi(pdf);
        var json = mapper.writeValueAsString(lettura.cedolino().json(mapper));
        log.debug("Cedolino letto dal lettore locale con il layout {}", lettura.layout().id());
        return interprete.interpreta(json, VERSIONE + "/" + lettura.layout().id());
    }

    /** Esito della lettura prima della validazione: layout riconosciuto e dati letti. */
    record Lettura(LayoutCedolino layout, CedolinoLetto cedolino) {
    }

    /**
     * Legge il PDF senza validarlo (serve anche alle misure sui campioni).
     *
     * @throws EstrazioneCedolinoException DOCUMENTO_NON_VALIDO se il PDF e' vuoto o illeggibile, PDF_SENZA_TESTO se
     *                                     non contiene testo, LAYOUT_NON_RICONOSCIUTO se il layout e' sconosciuto o
     *                                     mancano i dati essenziali
     */
    Lettura leggi(byte[] pdf) {
        if (pdf == null || pdf.length == 0) {
            throw new EstrazioneCedolinoException(Motivo.DOCUMENTO_NON_VALIDO, "Documento vuoto");
        }
        DocumentoTesto documento;
        try {
            documento = TestoPdf.leggi(pdf);
        } catch (IOException e) {
            throw new EstrazioneCedolinoException(Motivo.DOCUMENTO_NON_VALIDO, "Il file non è un PDF leggibile", e);
        }
        if (documento.pagine().isEmpty() || documento.caratteriSignificativi() < CARATTERI_MINIMI) {
            throw new EstrazioneCedolinoException(Motivo.PDF_SENZA_TESTO,
                    "Il PDF non contiene testo selezionabile (probabilmente è una scansione o un'immagine) e il "
                            + "lettore locale non può interpretarlo. " + SUGGERIMENTO_CLAUDE);
        }
        var riconosciuto = layout.stream().filter(l -> l.riconosce(documento)).findFirst()
                .orElseThrow(() -> new EstrazioneCedolinoException(Motivo.LAYOUT_NON_RICONOSCIUTO,
                        "Il lettore locale non riconosce il layout di questo cedolino (layout supportati: "
                                + String.join(", ", layout.stream().map(LayoutCedolino::nome).toList()) + "). "
                                + SUGGERIMENTO_CLAUDE));
        CedolinoLetto letto;
        try {
            letto = riconosciuto.leggi(documento);
        } catch (RuntimeException e) {
            log.warn("Lettura non riuscita con il layout {} ({})", riconosciuto.id(), e.getClass().getSimpleName());
            throw new EstrazioneCedolinoException(Motivo.LAYOUT_NON_RICONOSCIUTO,
                    "Il lettore locale non è riuscito a leggere questo cedolino " + riconosciuto.nome() + ". "
                            + SUGGERIMENTO_CLAUDE, e);
        }
        var mancanti = new ArrayList<String>();
        if (letto.periodo == null) {
            mancanti.add("periodo di retribuzione");
        }
        if (letto.codiceFiscale == null) {
            mancanti.add("codice fiscale del dipendente");
        }
        if (letto.netto == null) {
            mancanti.add("netto");
        }
        if (!mancanti.isEmpty()) {
            throw new EstrazioneCedolinoException(Motivo.LAYOUT_NON_RICONOSCIUTO,
                    "Il lettore locale riconosce il layout " + riconosciuto.nome()
                            + " ma non trova nel testo del PDF: " + String.join(", ", mancanti)
                            + " (alcune parti potrebbero essere stampate come immagine). " + SUGGERIMENTO_CLAUDE);
        }
        letto.tipo = Classificazione.tipo(letto);
        if (letto.tipo == TipoCedolinoEstratto.TREDICESIMA && letto.intestazioneAggiuntiva) {
            letto.nota("Periodo stampato come aggiuntivo (AGG.) con voce di tredicesima: tipo TREDICESIMA");
        } else if (letto.tipo == TipoCedolinoEstratto.FINE_RAPPORTO) {
            letto.nota("Periodo successivo al mese di cessazione stampato: cedolino di fine rapporto");
        }
        return new Lettura(riconosciuto, letto);
    }
}
