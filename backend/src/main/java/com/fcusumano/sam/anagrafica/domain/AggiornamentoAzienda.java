package com.fcusumano.sam.anagrafica.domain;

import java.util.Set;

/**
 * Aggiornamento di un'azienda registrata con i dati di un nuovo cedolino.
 * <ul>
 *   <li>ragione sociale, indirizzo e codice azienda sono attributi aggiornabili: vincono i dati del cedolino piu'
 *       recente; un valore mancante non cancella quello presente, un valore presente completa quello mancante;</li>
 *   <li>codice fiscale, partita IVA e matricola INPS sono chiavi: vengono solo completati se mancanti, e un
 *       identificativo fiscale gia' usato da un'altra azienda del workspace non viene assegnato.</li>
 * </ul>
 */
public final class AggiornamentoAzienda {

    private AggiornamentoAzienda() {
    }

    /**
     * @param registrata         azienda registrata
     * @param letta              dati normalizzati letti dal cedolino
     * @param fiscaliDiAltre     identificativi fiscali (codici fiscali e partite IVA) gia' usati da altre aziende
     * @return l'azienda aggiornata, con lo stesso id della registrata
     */
    public static ProfiloAzienda unisci(ProfiloAzienda registrata, ProfiloAzienda letta, Set<String> fiscaliDiAltre) {
        boolean recente = Recenza.sovrascrive(letta.riferimento(), registrata.riferimento());
        var ragioneSociale = recente ? letta.ragioneSociale() : registrata.ragioneSociale();
        var normalizzata = recente ? letta.ragioneSocialeNormalizzata() : registrata.ragioneSocialeNormalizzata();
        return new ProfiloAzienda(
                registrata.id(),
                completa(registrata.codiceFiscale(), letta.codiceFiscale(), fiscaliDiAltre),
                completa(registrata.partitaIva(), letta.partitaIva(), fiscaliDiAltre),
                registrata.posizioneInps() != null ? registrata.posizioneInps() : letta.posizioneInps(),
                ragioneSociale,
                normalizzata,
                attributo(registrata.indirizzo(), letta.indirizzo(), recente),
                attributo(registrata.codiceAzienda(), letta.codiceAzienda(), recente),
                Recenza.piuRecente(registrata.riferimento(), letta.riferimento()));
    }

    private static String completa(String presente, String nuovo, Set<String> usatiDaAltre) {
        if (presente != null || nuovo == null || usatiDaAltre.contains(nuovo)) {
            return presente;
        }
        return nuovo;
    }

    private static String attributo(String presente, String nuovo, boolean recente) {
        if (nuovo == null) {
            return presente;
        }
        return recente || presente == null ? nuovo : presente;
    }
}
