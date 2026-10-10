package com.fcusumano.sam.anagrafica.domain;

import com.fcusumano.sam.anagrafica.Azienda;
import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.Persona;
import com.fcusumano.sam.anagrafica.PersonaId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Etichette anti-omonimia per la UI: l'identificativo si aggiunge solo quando serve a distinguere.
 * <ul>
 *   <li>persona: "Nome Cognome", con " (CODICEFISCALE)" se nel workspace c'e' un'altra persona con lo stesso nome e
 *       cognome (senza distinzione tra maiuscole e minuscole, accenti e spazi);</li>
 *   <li>azienda: ragione sociale, con " (P.IVA ...)" se tra le aziende considerate ce n'e' un'altra con la stessa
 *       ragione sociale normalizzata; in mancanza di partita IVA si usa il codice fiscale ("CF ..."), poi la
 *       matricola INPS ("Pos. INPS ...").</li>
 * </ul>
 */
public final class Etichette {

    private static final Pattern NON_ALFANUMERICI = Pattern.compile("[^A-Z0-9]+");

    private Etichette() {
    }

    public static Map<PersonaId, String> persone(Collection<Persona> persone) {
        var omonimi = conteggi(persone, p -> chiaveNome(p.nome() + " " + p.cognome()));
        var etichette = new LinkedHashMap<PersonaId, String>();
        for (var persona : persone) {
            var base = persona.nomeCompleto();
            boolean omonimia = omonimi.get(chiaveNome(persona.nome() + " " + persona.cognome())) > 1;
            etichette.put(persona.id(), omonimia ? base + " (" + persona.codiceFiscale() + ")" : base);
        }
        return etichette;
    }

    public static Map<AziendaId, String> aziende(Collection<Azienda> aziende) {
        var omonime = conteggi(aziende, Etichette::chiaveAzienda);
        var etichette = new LinkedHashMap<AziendaId, String>();
        for (var azienda : aziende) {
            boolean omonimia = omonime.get(chiaveAzienda(azienda)) > 1;
            etichette.put(azienda.id(), omonimia ? azienda.ragioneSociale() + distintivo(azienda) : azienda.ragioneSociale());
        }
        return etichette;
    }

    private static String distintivo(Azienda azienda) {
        if (azienda.partitaIva() != null) {
            return " (P.IVA " + azienda.partitaIva() + ")";
        }
        if (azienda.codiceFiscale() != null) {
            return " (CF " + azienda.codiceFiscale() + ")";
        }
        if (azienda.posizioneInps() != null) {
            return " (Pos. INPS " + azienda.posizioneInps() + ")";
        }
        return "";
    }

    private static String chiaveAzienda(Azienda azienda) {
        var normalizzata = RagioneSociale.normalizza(azienda.ragioneSociale());
        return normalizzata != null ? normalizzata : "";
    }

    private static String chiaveNome(String nomeCompleto) {
        return NON_ALFANUMERICI.matcher(Testi.maiuscoloSenzaAccenti(nomeCompleto)).replaceAll(" ").strip();
    }

    private static <T> Map<String, Long> conteggi(Collection<T> elementi, Function<T, String> chiave) {
        return elementi.stream().collect(Collectors.groupingBy(chiave, Collectors.counting()));
    }
}
