package com.fcusumano.sam.web.api;

import static com.fcusumano.sam.web.api.Numeri.esatto;
import static com.fcusumano.sam.web.api.Numeri.euro;

import com.fcusumano.sam.calcoli.RiepilogoRapporto.IndicatoriMensilita;
import com.fcusumano.sam.cedolini.CedolinoId;
import com.fcusumano.sam.cedolini.CedolinoSintesi;
import com.fcusumano.sam.shared.Competenza;
import com.fcusumano.sam.shared.TipoMensilita;
import com.fcusumano.sam.web.api.dto.RigaMensilitaDto;
import com.fcusumano.sam.web.api.dto.RigaMensilitaDto.SaldoRateoCedolino;
import com.fcusumano.sam.web.api.dto.RigaMensilitaDto.Stato;
import java.time.Month;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Lista mensilita' del dettaglio rapporto: unisce i cedolini presenti (sintesi + indicatori del modulo calcoli) e le
 * mensilita' mancanti (modulo segnalazioni) in un'unica lista in ordine cronologico inverso con titoli pronti.
 * <p>
 * Posizione cronologica di ogni riga:
 * <ul>
 *   <li>mensilita' ordinaria: il suo mese;</li>
 *   <li>tredicesima: dopo dicembre del suo anno (nella lista inversa sta quindi sopra dicembre);</li>
 *   <li>quattordicesima: dopo il mese di pagamento previsto dal CCNL del rapporto, dopo giugno se non noto;</li>
 *   <li>fine rapporto: dopo il mese del periodo di retribuzione stampato, nell'ordine del progressivo.</li>
 * </ul>
 */
final class ListaMensilita {

    /** Mese dopo il quale si colloca la quattordicesima se il CCNL non indica il mese di pagamento. */
    static final int MESE_QUATTORDICESIMA_PREDEFINITO = Month.JUNE.getValue();

    private static final Comparator<Riga> ORDINE_INVERSO = Comparator.comparingLong(Riga::posizione)
            .thenComparingInt(Riga::mese)
            .thenComparing(riga -> riga.dto().chiave())
            .reversed();

    private ListaMensilita() {
    }

    private record Riga(long posizione, int mese, RigaMensilitaDto dto) {
    }

    /**
     * @param cedolini            cedolini presenti del rapporto (in qualunque ordine)
     * @param mancanti            mensilita' attese ma assenti; quelle per cui esiste gia' un cedolino sono ignorate
     * @param indicatori          indicatori per cedolino (vuota se il riepilogo non e' ancora calcolato)
     * @param meseQuattordicesima mese di pagamento della quattordicesima secondo il CCNL del rapporto; null se non noto
     */
    static List<RigaMensilitaDto> componi(List<CedolinoSintesi> cedolini, List<Competenza.Mensilita> mancanti,
                                          Map<CedolinoId, IndicatoriMensilita> indicatori,
                                          Integer meseQuattordicesima) {
        var righe = new ArrayList<Riga>(cedolini.size() + mancanti.size());
        var chiaviPresenti = new HashSet<String>();
        for (var cedolino : cedolini) {
            chiaviPresenti.add(Titoli.chiave(cedolino.competenza()));
            righe.add(riga(cedolino.competenza(), meseQuattordicesima,
                    presente(cedolino, indicatori.get(cedolino.id()))));
        }
        for (var mancante : mancanti) {
            if (chiaviPresenti.add(Titoli.chiave(mancante))) {
                righe.add(riga(mancante, meseQuattordicesima, mancante(mancante)));
            }
        }
        righe.sort(ORDINE_INVERSO);
        return righe.stream().map(Riga::dto).toList();
    }

    /**
     * Chiave di ordinamento cronologico crescente. Coincide con {@link Competenza#chiaveOrdinamento()} tranne che per
     * la quattordicesima, collocata dopo il mese di pagamento del CCNL: a meta' tra quel mese e il successivo, prima
     * di un eventuale cedolino di fine rapporto dello stesso mese.
     */
    static long posizione(Competenza competenza, Integer meseQuattordicesima) {
        if (competenza instanceof Competenza.Mensilita m && m.tipo() == TipoMensilita.QUATTORDICESIMA) {
            int mesePagamento = meseQuattordicesima != null && meseQuattordicesima >= 1 && meseQuattordicesima <= 12
                    ? meseQuattordicesima
                    : MESE_QUATTORDICESIMA_PREDEFINITO;
            return m.anno() * 10_000L + mesePagamento * 100L + 50;
        }
        return competenza.chiaveOrdinamento();
    }

    private static Riga riga(Competenza competenza, Integer meseQuattordicesima, RigaMensilitaDto dto) {
        int mese = competenza instanceof Competenza.Mensilita m ? m.mese() : 0;
        return new Riga(posizione(competenza, meseQuattordicesima), mese, dto);
    }

    private static RigaMensilitaDto presente(CedolinoSintesi cedolino, IndicatoriMensilita indicatori) {
        var competenza = cedolino.competenza();
        var ratei = cedolino.ratei().stream()
                .filter(r -> r.saldo() != null)
                .map(r -> new SaldoRateoCedolino(r.tipo(), r.unita(), esatto(r.saldo())))
                .toList();
        return new RigaMensilitaDto(
                Titoli.chiave(competenza),
                Stato.PRESENTE,
                Titoli.di(competenza),
                Mappatura.competenza(competenza),
                cedolino.periodoRetribuzione(),
                cedolino.id().valore(),
                euro(cedolino.netto()),
                euro(cedolino.lordo()),
                ratei,
                indicatori != null && indicatori.scattoInBusta(),
                indicatori != null && indicatori.aumentoFuoriScatti(),
                cedolino.nomeFile());
    }

    /** Riga "mancante": solo titolo; il periodo e' valorizzato solo per le mensilita' ordinarie. */
    private static RigaMensilitaDto mancante(Competenza.Mensilita competenza) {
        var periodo = competenza.tipo() == TipoMensilita.ORDINARIA ? competenza.comeYearMonth() : null;
        return new RigaMensilitaDto(
                Titoli.chiave(competenza),
                Stato.MANCANTE,
                Titoli.di(competenza),
                Mappatura.competenza(competenza),
                periodo,
                null,
                null,
                null,
                List.of(),
                false,
                false,
                null);
    }
}
