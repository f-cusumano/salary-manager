package com.fcusumano.sam.anagrafica.domain;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Regole di matching tra l'azienda letta da un cedolino e le aziende gia' registrate.
 * <ol>
 *   <li><b>identificativi fiscali</b>: codice fiscale e partita IVA confrontati in modo incrociato (un codice fiscale
 *       numerico di 11 cifre e' una partita IVA). Se piu' aziende corrispondono vince quella con piu' identificativi in
 *       comune, a parita' la prima registrata;</li>
 *   <li><b>matricola INPS</b> (normalizzata a 10 cifre), purche' gli identificativi fiscali non siano in contrasto;</li>
 *   <li><b>ragione sociale normalizzata</b>, purche' ne' gli identificativi fiscali ne' la matricola INPS siano in
 *       contrasto (omonimi distinti restano distinti). Se restano piu' aziende omonime compatibili vince la prima
 *       registrata.</li>
 * </ol>
 * Due valori sono "in contrasto" quando sono presenti da entrambe le parti e diversi.
 */
public final class AbbinamentoAzienda {

    /** Criterio con cui l'azienda e' stata riconosciuta. */
    public enum Criterio {
        IDENTIFICATIVO_FISCALE,
        POSIZIONE_INPS,
        RAGIONE_SOCIALE
    }

    /** Azienda registrata riconosciuta e criterio usato. */
    public record Esito(ProfiloAzienda azienda, Criterio criterio) {
    }

    private static final Comparator<ProfiloAzienda> PER_ID =
            Comparator.comparingLong(azienda -> azienda.id().valore());

    private AbbinamentoAzienda() {
    }

    /**
     * @param letta     azienda letta dal cedolino (normalizzata)
     * @param candidate aziende registrate del workspace che condividono almeno un identificativo, la matricola INPS o
     *                  la ragione sociale normalizzata (puo' contenerne anche altre: vengono ignorate)
     */
    public static Optional<Esito> trova(ProfiloAzienda letta, List<ProfiloAzienda> candidate) {
        var fiscali = letta.identificativiFiscali();
        if (!fiscali.isEmpty()) {
            var perFiscale = candidate.stream()
                    .filter(c -> !Collections.disjoint(fiscali, c.identificativiFiscali()))
                    .max(Comparator.<ProfiloAzienda>comparingInt(c -> inComune(fiscali, c))
                            .thenComparing(PER_ID.reversed()));
            if (perFiscale.isPresent()) {
                return perFiscale.map(c -> new Esito(c, Criterio.IDENTIFICATIVO_FISCALE));
            }
        }
        if (letta.posizioneInps() != null) {
            var perInps = candidate.stream()
                    .filter(c -> letta.posizioneInps().equals(c.posizioneInps()))
                    .filter(c -> !conflittoFiscale(letta, c))
                    .min(PER_ID);
            if (perInps.isPresent()) {
                return perInps.map(c -> new Esito(c, Criterio.POSIZIONE_INPS));
            }
        }
        return candidate.stream()
                .filter(c -> letta.ragioneSocialeNormalizzata().equals(c.ragioneSocialeNormalizzata()))
                .filter(c -> !conflittoFiscale(letta, c))
                .filter(c -> !inContrasto(letta.posizioneInps(), c.posizioneInps()))
                .min(PER_ID)
                .map(c -> new Esito(c, Criterio.RAGIONE_SOCIALE));
    }

    /**
     * True se gli identificativi fiscali dimostrano che si tratta di aziende diverse: codici fiscali alfanumerici
     * diversi, oppure numeri di partita IVA (P.IVA o codice fiscale numerico) presenti da entrambe le parti e senza
     * valori in comune.
     */
    static boolean conflittoFiscale(ProfiloAzienda a, ProfiloAzienda b) {
        if (!Collections.disjoint(a.identificativiFiscali(), b.identificativiFiscali())) {
            return false;
        }
        boolean cfAlfanumericiDiversi = a.codiceFiscale() != null && b.codiceFiscale() != null
                && !IdentificativiAzienda.codiceFiscaleNumerico(a.codiceFiscale())
                && !IdentificativiAzienda.codiceFiscaleNumerico(b.codiceFiscale());
        var numeriA = IdentificativiAzienda.numeriPartitaIva(a.codiceFiscale(), a.partitaIva());
        var numeriB = IdentificativiAzienda.numeriPartitaIva(b.codiceFiscale(), b.partitaIva());
        boolean partiteIvaDiverse = !numeriA.isEmpty() && !numeriB.isEmpty();
        return cfAlfanumericiDiversi || partiteIvaDiverse;
    }

    private static boolean inContrasto(String a, String b) {
        return a != null && b != null && !Objects.equals(a, b);
    }

    private static int inComune(Set<String> fiscali, ProfiloAzienda candidata) {
        var comuni = new HashSet<>(fiscali);
        comuni.retainAll(candidata.identificativiFiscali());
        return comuni.size();
    }
}
