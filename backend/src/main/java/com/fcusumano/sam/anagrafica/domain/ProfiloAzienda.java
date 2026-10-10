package com.fcusumano.sam.anagrafica.domain;

import com.fcusumano.sam.anagrafica.Azienda;
import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.DatiAzienda;
import java.time.YearMonth;
import java.util.Objects;
import java.util.Set;

/**
 * Azienda con identificativi gia' normalizzati, ragione sociale normalizzata e periodo di riferimento degli attributi.
 *
 * @param id          null per un'azienda non ancora registrata (dati letti da un cedolino)
 * @param riferimento periodo del cedolino da cui provengono gli attributi aggiornabili; null se non noto
 */
public record ProfiloAzienda(
        AziendaId id,
        String codiceFiscale,
        String partitaIva,
        String posizioneInps,
        String ragioneSociale,
        String ragioneSocialeNormalizzata,
        String indirizzo,
        String codiceAzienda,
        YearMonth riferimento) {

    /** Lunghezza massima dell'indirizzo (colonna {@code azienda.indirizzo}). */
    public static final int LUNGHEZZA_INDIRIZZO = 300;
    /** Lunghezza massima del codice azienda (colonna {@code azienda.codice_azienda}). */
    public static final int LUNGHEZZA_CODICE_AZIENDA = 50;

    public ProfiloAzienda {
        Objects.requireNonNull(ragioneSociale, "ragioneSociale");
        Objects.requireNonNull(ragioneSocialeNormalizzata, "ragioneSocialeNormalizzata");
    }

    /**
     * Normalizza i dati letti da un cedolino.
     *
     * @throws IllegalArgumentException se manca la ragione sociale
     */
    public static ProfiloAzienda daCedolino(DatiAzienda dati) {
        var ragioneSociale = Testi.pulisciETronca(dati.ragioneSociale(), RagioneSociale.LUNGHEZZA);
        var normalizzata = RagioneSociale.normalizza(ragioneSociale);
        if (normalizzata == null) {
            throw new IllegalArgumentException("Ragione sociale dell'azienda mancante");
        }
        return new ProfiloAzienda(
                null,
                IdentificativiAzienda.normalizzaCodiceFiscale(dati.codiceFiscale()),
                IdentificativiAzienda.normalizzaPartitaIva(dati.partitaIva()),
                IdentificativiAzienda.normalizzaPosizioneInps(dati.posizioneInps()),
                ragioneSociale,
                normalizzata,
                Testi.pulisciETronca(dati.indirizzo(), LUNGHEZZA_INDIRIZZO),
                Testi.pulisciETronca(dati.codiceAzienda(), LUNGHEZZA_CODICE_AZIENDA),
                dati.riferimento());
    }

    /** Codice fiscale e partita IVA (confrontabili in modo incrociato). */
    public Set<String> identificativiFiscali() {
        return IdentificativiAzienda.fiscali(codiceFiscale, partitaIva);
    }

    public ProfiloAzienda conId(AziendaId nuovoId) {
        return new ProfiloAzienda(nuovoId, codiceFiscale, partitaIva, posizioneInps, ragioneSociale,
                ragioneSocialeNormalizzata, indirizzo, codiceAzienda, riferimento);
    }

    /** Vista pubblica (richiede un'azienda registrata). */
    public Azienda comeAzienda() {
        Objects.requireNonNull(id, "Azienda non ancora registrata");
        return new Azienda(id, codiceFiscale, partitaIva, posizioneInps, ragioneSociale, indirizzo, codiceAzienda);
    }
}
