package com.fcusumano.sam.shared;

import java.time.YearMonth;
import java.util.Comparator;

/**
 * Competenza di un cedolino: e' la chiave logica (insieme al rapporto di lavoro) che lo rende univoco.
 * <ul>
 *   <li>{@link Mensilita}: anno + mese 1..14 (13 = tredicesima, 14 = quattordicesima);</li>
 *   <li>{@link FineRapporto}: cedolino successivo alla cessazione, senza mese ma con progressivo 1..n
 *       (TFR01, TFR02, ...) assegnato in ordine cronologico del periodo di retribuzione stampato.</li>
 * </ul>
 */
public sealed interface Competenza permits Competenza.Mensilita, Competenza.FineRapporto {

    /** Ordine cronologico crescente (per l'ordine inverso usare {@code ORDINE_CRONOLOGICO.reversed()}). */
    Comparator<Competenza> ORDINE_CRONOLOGICO = Comparator.comparingLong(Competenza::chiaveOrdinamento);

    /** Anno di competenza (per i cedolini di fine rapporto: anno del periodo di retribuzione). */
    int anno();

    TipoMensilita tipo();

    /** Codice usato nel nome file: "01".."14" oppure "TFR01", "TFR02", ... */
    String codice();

    /**
     * Chiave numerica di ordinamento cronologico: la tredicesima segue dicembre, la quattordicesima segue luglio,
     * i cedolini di fine rapporto seguono il mese del proprio periodo di retribuzione.
     */
    long chiaveOrdinamento();

    /** Mensilita' ordinaria, tredicesima o quattordicesima. */
    record Mensilita(int anno, int mese) implements Competenza {

        public Mensilita {
            if (anno < 1900 || anno > 2999) {
                throw new IllegalArgumentException("Anno di competenza non valido: " + anno);
            }
            if (mese < 1 || mese > 14) {
                throw new IllegalArgumentException("Mese di competenza non valido (1..14): " + mese);
            }
        }

        public static Mensilita ordinaria(YearMonth periodo) {
            return new Mensilita(periodo.getYear(), periodo.getMonthValue());
        }

        public static Mensilita tredicesima(int anno) {
            return new Mensilita(anno, 13);
        }

        public static Mensilita quattordicesima(int anno) {
            return new Mensilita(anno, 14);
        }

        @Override
        public TipoMensilita tipo() {
            return switch (mese) {
                case 13 -> TipoMensilita.TREDICESIMA;
                case 14 -> TipoMensilita.QUATTORDICESIMA;
                default -> TipoMensilita.ORDINARIA;
            };
        }

        @Override
        public String codice() {
            return "%02d".formatted(mese);
        }

        @Override
        public long chiaveOrdinamento() {
            int posizione = switch (mese) {
                case 13 -> 1250;
                case 14 -> 750;
                default -> mese * 100;
            };
            return anno * 10_000L + posizione;
        }

        /** Mese solare della mensilita' ordinaria; per tredicesima/quattordicesima solleva eccezione. */
        public YearMonth comeYearMonth() {
            if (mese > 12) {
                throw new IllegalStateException("La competenza " + codice() + " non corrisponde a un mese solare");
            }
            return YearMonth.of(anno, mese);
        }
    }

    /** Cedolino di fine rapporto (post cessazione). */
    record FineRapporto(int progressivo, YearMonth periodoRetribuzione) implements Competenza {

        public FineRapporto {
            if (progressivo < 1 || progressivo > 49) {
                throw new IllegalArgumentException("Progressivo TFR non valido: " + progressivo);
            }
            if (periodoRetribuzione == null) {
                throw new IllegalArgumentException("Periodo di retribuzione obbligatorio per il cedolino di fine rapporto");
            }
        }

        @Override
        public int anno() {
            return periodoRetribuzione.getYear();
        }

        @Override
        public TipoMensilita tipo() {
            return TipoMensilita.FINE_RAPPORTO;
        }

        @Override
        public String codice() {
            return "TFR%02d".formatted(progressivo);
        }

        @Override
        public long chiaveOrdinamento() {
            return periodoRetribuzione.getYear() * 10_000L + periodoRetribuzione.getMonthValue() * 100L + 50 + progressivo;
        }
    }
}
