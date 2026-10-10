package com.fcusumano.sam.anagrafica.domain;

import com.fcusumano.sam.anagrafica.DatiRapporto;
import java.time.LocalDate;

/**
 * Regole di aggiornamento del rapporto con i dati letti dai cedolini.
 * <ul>
 *   <li>date, qualifica, livello e codice dipendente vengono sovrascritti solo da un cedolino con riferimento uguale o
 *       piu' recente di quello dei dati presenti; un cedolino piu' vecchio completa soltanto i valori mancanti;
 *       un valore null non cancella mai un valore presente;</li>
 *   <li>il CCNL riconosciuto viene applicato solo se l'utente non ne ha scelto uno, con lo stesso criterio di recenza;</li>
 *   <li>se le date risultano incoerenti (cessazione precedente all'assunzione, es. riassunzione presso la stessa
 *       azienda) si scarta la data proveniente dalla fonte meno recente; se entrambe vengono dalla stessa fonte si
 *       scarta la cessazione.</li>
 * </ul>
 */
public final class AggiornamentoRapporto {

    /** Lunghezza massima della qualifica (colonna {@code rapporto_lavoro.qualifica}). */
    public static final int LUNGHEZZA_QUALIFICA = 100;
    /** Lunghezza massima del livello (colonna {@code rapporto_lavoro.livello}). */
    public static final int LUNGHEZZA_LIVELLO = 50;
    /** Lunghezza massima del codice dipendente (colonna {@code rapporto_lavoro.codice_dipendente}). */
    public static final int LUNGHEZZA_CODICE_DIPENDENTE = 50;

    private AggiornamentoRapporto() {
    }

    /**
     * Applica i dati di un cedolino.
     *
     * @param ccnlRiconosciuto codice CCNL riconosciuto e verificato in tabella; null se non riconosciuto
     */
    public static StatoRapporto unisci(StatoRapporto presente, DatiRapporto dati, String ccnlRiconosciuto) {
        boolean recente = Recenza.sovrascrive(dati.riferimento(), presente.riferimentoDati());
        var assunzione = scegli(presente.dataAssunzione(), dati.dataAssunzione(), recente);
        var cessazione = scegli(presente.dataCessazione(), dati.dataCessazione(), recente);
        if (assunzione != null && cessazione != null && cessazione.isBefore(assunzione)) {
            boolean assunzioneNuova = provieneDaiDati(presente.dataAssunzione(), assunzione);
            boolean cessazioneNuova = provieneDaiDati(presente.dataCessazione(), cessazione);
            if (assunzioneNuova && !cessazioneNuova) {
                cessazione = null;
            } else if (cessazioneNuova && !assunzioneNuova) {
                assunzione = null;
            } else {
                cessazione = null;
            }
        }
        var ccnl = presente.ccnlCodice();
        if (!presente.ccnlSceltoDaUtente() && ccnlRiconosciuto != null && (recente || ccnl == null)) {
            ccnl = ccnlRiconosciuto;
        }
        return new StatoRapporto(
                assunzione,
                cessazione,
                scegli(presente.qualifica(), Testi.pulisciETronca(dati.qualifica(), LUNGHEZZA_QUALIFICA), recente),
                scegli(presente.livello(), Testi.pulisciETronca(dati.livello(), LUNGHEZZA_LIVELLO), recente),
                scegli(presente.codiceDipendente(),
                        Testi.pulisciETronca(dati.codiceDipendente(), LUNGHEZZA_CODICE_DIPENDENTE), recente),
                ccnl,
                presente.ccnlSceltoDaUtente(),
                Recenza.piuRecente(presente.riferimentoDati(), dati.riferimento()));
    }

    /**
     * Sostituisce i dati con quelli indicati, ignorando la recenza (anche i null). Un CCNL scelto dall'utente resta;
     * altrimenti il CCNL diventa quello riconosciuto (null = da indicare). Date incoerenti: si scarta la cessazione.
     */
    public static StatoRapporto sovrascrivi(StatoRapporto presente, DatiRapporto dati, String ccnlRiconosciuto) {
        var cessazione = dati.dataCessazione();
        if (dati.dataAssunzione() != null && cessazione != null && cessazione.isBefore(dati.dataAssunzione())) {
            cessazione = null;
        }
        return new StatoRapporto(
                dati.dataAssunzione(),
                cessazione,
                Testi.pulisciETronca(dati.qualifica(), LUNGHEZZA_QUALIFICA),
                Testi.pulisciETronca(dati.livello(), LUNGHEZZA_LIVELLO),
                Testi.pulisciETronca(dati.codiceDipendente(), LUNGHEZZA_CODICE_DIPENDENTE),
                presente.ccnlSceltoDaUtente() ? presente.ccnlCodice() : ccnlRiconosciuto,
                presente.ccnlSceltoDaUtente(),
                dati.riferimento());
    }

    private static <T> T scegli(T presente, T nuovo, boolean recente) {
        if (nuovo == null) {
            return presente;
        }
        return recente || presente == null ? nuovo : presente;
    }

    private static boolean provieneDaiDati(LocalDate presente, LocalDate risultato) {
        return !risultato.equals(presente);
    }
}
