package com.fcusumano.sam.cedolini.application;

import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.anagrafica.RapportoLavoroId;
import com.fcusumano.sam.cedolini.CedolinoDettaglio;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.CedolinoNormalizzato;
import com.fcusumano.sam.cedolini.CedolinoSintesi;
import com.fcusumano.sam.cedolini.PdfCedolino;
import com.fcusumano.sam.cedolini.domain.ClassificazioneRapporto;
import com.fcusumano.sam.cedolini.domain.ConfrontoCedolini.ImportiChiave;
import com.fcusumano.sam.cedolini.domain.EstremiCedolini.PeriodoCedolino;
import com.fcusumano.sam.cedolini.domain.TipoDichiarato;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.WorkspaceId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Porta di persistenza dei cedolini (tabelle {@code cedolino*}). Tutte le operazioni sono filtrate per workspace.
 */
public interface CedoliniRepository {

    /** Cedolino con lo stesso hash SHA-256 del PDF originale. */
    Optional<CedolinoId> perHash(WorkspaceId workspace, String hash);

    /**
     * Cedolino con la stessa chiave di unicita': rapporto + anno + mese per le mensilita', rapporto + periodo di
     * retribuzione per i cedolini di fine rapporto (il progressivo di {@code competenza} e' ignorato).
     */
    Optional<CedolinoConImporti> perChiave(WorkspaceId workspace, RapportoLavoroId rapportoId, Competenza competenza);

    /**
     * Cedolini del rapporto con i dati che ne determinano la classificazione (tipo dichiarato, periodo stampato,
     * assunzione stampata, competenza attuale), in ordine di id.
     */
    List<ClassificazioneRapporto.Cedolino> classificabili(WorkspaceId workspace, RapportoLavoroId rapportoId);

    /** Periodi di retribuzione di tutti i cedolini del rapporto. */
    List<PeriodoCedolino> periodi(WorkspaceId workspace, RapportoLavoroId rapportoId);

    /** Salva cedolino, tabelle figlie e PDF originale. */
    CedolinoId inserisci(WorkspaceId workspace, NuovoCedolino cedolino);

    /**
     * Assegna competenza (mensilita' o fine rapporto con progressivo) e nome file ai cedolini indicati, nell'ordine
     * dato: ogni cambio di classificazione va verso una chiave di unicita' gia' libera al suo turno; progressivi TFR e
     * nomi file passano da valori temporanei, cosi' nessun vincolo unico viene violato durante gli spostamenti.
     */
    void assegnaCompetenze(WorkspaceId workspace, List<AssegnazioneCompetenza> assegnazioni);

    Optional<CedolinoRegistrato> trova(WorkspaceId workspace, CedolinoId id);

    /** Sintesi dei cedolini del rapporto (ordine non garantito). */
    List<CedolinoSintesi> sintesi(WorkspaceId workspace, RapportoLavoroId rapportoId);

    /** Cedolini normalizzati del rapporto (ordine non garantito). */
    List<CedolinoNormalizzato> normalizzati(WorkspaceId workspace, RapportoLavoroId rapportoId, PersonaId personaId,
                                            AziendaId aziendaId);

    Optional<CedolinoDettaglio> dettaglio(WorkspaceId workspace, CedolinoId id, PersonaId personaId, AziendaId aziendaId);

    Optional<PdfCedolino> pdf(WorkspaceId workspace, CedolinoId id);

    /**
     * Cedolini (di tutti i workspace) il cui PDF archiviato e' ancora protetto o da verificare, in ordine di
     * workspace e id.
     */
    List<PdfDaSbloccare> pdfDaSbloccare();

    /** Cedolini del workspace il cui PDF archiviato e' ancora protetto o da verificare, in ordine di id. */
    List<CedolinoId> pdfDaSbloccare(WorkspaceId workspace);

    /**
     * Aggiorna il PDF archiviato del cedolino (se esiste ancora).
     *
     * @param contenuto copia senza protezione da archiviare al posto del PDF attuale, null per lasciarlo com'e'
     * @param protetto  true se il PDF archiviato resta protetto da una password sconosciuta
     */
    void aggiornaPdf(WorkspaceId workspace, CedolinoId id, byte[] contenuto, boolean protetto);

    /** Elimina il cedolino; le tabelle figlie e il PDF sono rimossi in cascata. */
    void elimina(WorkspaceId workspace, CedolinoId id);

    /** Riferimenti essenziali di un cedolino archiviato. */
    record CedolinoRegistrato(CedolinoId id, RapportoLavoroId rapportoId, Competenza competenza) {
    }

    /** Cedolino archiviato con gli importi usati per distinguere duplicato e aggiuntivo. */
    record CedolinoConImporti(CedolinoId id, Competenza competenza, ImportiChiave importi) {
    }

    record AssegnazioneCompetenza(CedolinoId id, Competenza competenza, String nomeFile) {
    }

    record PdfDaSbloccare(WorkspaceId workspace, CedolinoId cedolinoId) {
    }

    /**
     * Cedolino da salvare.
     *
     * @param dati               dati normalizzati ({@code id} null)
     * @param tipoDichiarato     tipo dichiarato dall'estrazione (mai AGGIUNTIVA: un aggiuntivo non viene salvato)
     * @param pdf                PDF da archiviare: quello caricato o, se era cifrato, la sua copia senza protezione
     * @param pdfProtetto        false se il PDF e' senza password di apertura, null se non verificato (inserimento a
     *                           mano dal PDF conservato di un file in errore)
     * @param jsonEstrazione     JSON dell'estrazione per audit (dati bancari gia' mascherati)
     * @param confidenza         confidenza complessiva 0..1, null se non disponibile
     * @param avvisiEstrazione   avvisi dell'estrazione (dati bancari gia' mascherati), mai da loggare
     */
    record NuovoCedolino(
            CedolinoNormalizzato dati,
            TipoDichiarato tipoDichiarato,
            String nomeFile,
            String hash,
            byte[] pdf,
            Boolean pdfProtetto,
            String jsonEstrazione,
            BigDecimal confidenza,
            List<String> campiNonTrovati,
            List<String> avvisiEstrazione,
            String modello) {

        public NuovoCedolino {
            if (tipoDichiarato == null || tipoDichiarato == TipoDichiarato.AGGIUNTIVA) {
                throw new IllegalArgumentException("Tipo dichiarato non archiviabile: " + tipoDichiarato);
            }
            campiNonTrovati = campiNonTrovati == null ? List.of() : List.copyOf(campiNonTrovati);
            avvisiEstrazione = avvisiEstrazione == null ? List.of() : List.copyOf(avvisiEstrazione);
        }
    }
}
