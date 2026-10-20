package com.fcusumano.sam.calcoli.domain;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Parametri della stima della liquidazione ({@code salary-manager.liquidazione.*}): nessun valore e' cablato nel
 * dominio.
 *
 * @param aliquotaContributiDefault aliquota dei contributi INPS a carico del dipendente usata quando il cedolino non la
 *                                  stampa (es. 9,19%)
 * @param irpef                     scaglioni IRPEF per anno di entrata in vigore, almeno uno
 */
public record ParametriLiquidazione(BigDecimal aliquotaContributiDefault, List<ScaglioniIrpef> irpef) {

    public ParametriLiquidazione {
        Objects.requireNonNull(aliquotaContributiDefault, "aliquotaContributiDefault");
        if (aliquotaContributiDefault.signum() < 0 || aliquotaContributiDefault.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException(
                    "aliquotaContributiDefault deve essere tra 0 e 1: " + aliquotaContributiDefault);
        }
        if (irpef == null || irpef.isEmpty()) {
            throw new IllegalArgumentException("Servono gli scaglioni IRPEF di almeno un anno");
        }
        irpef = irpef.stream().sorted(Comparator.comparingInt(ScaglioniIrpef::dalAnno)).toList();
    }

    /**
     * Scaglioni in vigore nell'anno indicato: quelli con l'anno di entrata in vigore piu' recente non successivo; per
     * gli anni precedenti al primo configurato, il primo.
     */
    public ScaglioniIrpef scaglioni(int anno) {
        return irpef.stream().filter(s -> s.dalAnno() <= anno).reduce((a, b) -> b).orElse(irpef.getFirst());
    }

    /**
     * Scaglioni IRPEF in vigore da un anno.
     *
     * @param dalAnno   primo anno di applicazione
     * @param scaglioni in ordine crescente; l'ultimo e' senza limite ({@code fino} null)
     */
    public record ScaglioniIrpef(int dalAnno, List<Scaglione> scaglioni) {

        public ScaglioniIrpef {
            if (scaglioni == null || scaglioni.isEmpty()) {
                throw new IllegalArgumentException("Scaglioni IRPEF " + dalAnno + " vuoti");
            }
            scaglioni = List.copyOf(scaglioni);
            BigDecimal precedente = BigDecimal.ZERO;
            for (int i = 0; i < scaglioni.size(); i++) {
                var s = scaglioni.get(i);
                var ultimo = i == scaglioni.size() - 1;
                if (ultimo != (s.fino() == null)) {
                    throw new IllegalArgumentException(
                            "Scaglioni IRPEF " + dalAnno + ": solo l'ultimo scaglione e' senza limite");
                }
                if (!ultimo && s.fino().compareTo(precedente) <= 0) {
                    throw new IllegalArgumentException("Scaglioni IRPEF " + dalAnno + " non crescenti");
                }
                if (!ultimo) {
                    precedente = s.fino();
                }
            }
        }

        /** Imposta lorda (senza detrazioni) sul reddito indicato. */
        public BigDecimal imposta(BigDecimal reddito) {
            var imposta = BigDecimal.ZERO;
            var inizio = BigDecimal.ZERO;
            for (var s : scaglioni) {
                if (reddito.compareTo(inizio) <= 0) {
                    break;
                }
                var fine = s.fino() == null ? reddito : s.fino().min(reddito);
                imposta = imposta.add(fine.subtract(inizio).multiply(s.aliquota()));
                inizio = s.fino() == null ? reddito : s.fino();
            }
            return imposta;
        }

        /** Aliquota dello scaglione in cui cade il reddito indicato (aliquota marginale). */
        public BigDecimal aliquotaMarginale(BigDecimal reddito) {
            return scaglioni.stream()
                    .filter(s -> s.fino() == null || reddito.compareTo(s.fino()) <= 0)
                    .findFirst()
                    .orElse(scaglioni.getLast())
                    .aliquota();
        }
    }

    /**
     * Scaglione IRPEF.
     *
     * @param fino     limite superiore incluso; null per l'ultimo scaglione
     * @param aliquota aliquota tra 0 e 1
     */
    public record Scaglione(BigDecimal fino, BigDecimal aliquota) {

        public Scaglione {
            Objects.requireNonNull(aliquota, "aliquota");
            if (aliquota.signum() < 0 || aliquota.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("Aliquota IRPEF fuori intervallo: " + aliquota);
            }
        }
    }
}
