package com.fcusumano.sam.anagrafica.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.DatiAzienda;
import com.fcusumano.sam.anagrafica.domain.AbbinamentoAzienda.Criterio;
import java.util.List;
import org.junit.jupiter.api.Test;

class AbbinamentoAziendaTest {

    private static final String PIVA_ALFA = "01234567890";
    private static final String PIVA_BETA = "09876543210";

    @Test
    void codiceFiscaleNumericoEPartitaIvaSiConfrontanoInModoIncrociato() {
        var registrata = registrata(1, null, PIVA_ALFA, null, "Alfa Software S.r.l.");
        var letta = letta(PIVA_ALFA, null, null, "Alfa Software (nuova denominazione)");

        var esito = AbbinamentoAzienda.trova(letta, List.of(registrata)).orElseThrow();

        assertThat(esito.azienda().id()).isEqualTo(new AziendaId(1));
        assertThat(esito.criterio()).isEqualTo(Criterio.IDENTIFICATIVO_FISCALE);
    }

    @Test
    void conPiuCorrispondenzeFiscaliVinceQuellaConPiuIdentificativiInComune() {
        // ditta individuale: codice fiscale del titolare e partita IVA
        var soloPiva = registrata(1, PIVA_ALFA, null, null, "Alfa");
        var cfEPiva = registrata(2, "RSSMRA80A01H501U", PIVA_ALFA, null, "Alfa");
        var letta = letta("RSSMRA80A01H501U", PIVA_ALFA, null, "Alfa");

        assertThat(AbbinamentoAzienda.trova(letta, List.of(soloPiva, cfEPiva))).get()
                .extracting(e -> e.azienda().id().valore()).isEqualTo(2L);
    }

    @Test
    void matricolaInpsQuandoMancanoGliIdentificativiFiscali() {
        var registrata = registrata(1, PIVA_ALFA, PIVA_ALFA, "1234567890", "Alfa Software S.r.l.");
        // layout che non stampa CF/P.IVA e stampa INPS e INAIL concatenati
        var letta = letta(null, null, "123456789012345678", "ALFA SOFTWARE");

        var esito = AbbinamentoAzienda.trova(letta, List.of(registrata)).orElseThrow();

        assertThat(esito.criterio()).isEqualTo(Criterio.POSIZIONE_INPS);
    }

    @Test
    void matricolaInpsUgualeMaPartitaIvaDiversaNonAbbina() {
        var registrata = registrata(1, null, PIVA_ALFA, "1234567890", "Alfa Software S.r.l.");
        var letta = letta(null, PIVA_BETA, "1234567890", "Beta Consulting S.p.A.");

        assertThat(AbbinamentoAzienda.trova(letta, List.of(registrata))).isEmpty();
    }

    @Test
    void ragioneSocialeNormalizzataComeUltimoCriterio() {
        var registrata = registrata(1, null, PIVA_ALFA, null, "Alfa Software S.r.l.");
        var letta = letta(null, null, null, "ALFA SOFTWARE SRL");

        var esito = AbbinamentoAzienda.trova(letta, List.of(registrata)).orElseThrow();

        assertThat(esito.criterio()).isEqualTo(Criterio.RAGIONE_SOCIALE);
    }

    @Test
    void omonimiConIdentificativiDiversiRestanoDistinti() {
        var registrata = registrata(1, null, PIVA_ALFA, "1234567890", "Alfa Software S.r.l.");

        assertThat(AbbinamentoAzienda.trova(letta(null, PIVA_BETA, null, "Alfa Software SRL"), List.of(registrata)))
                .isEmpty();
        assertThat(AbbinamentoAzienda.trova(letta(null, null, "5555555555", "Alfa Software SRL"), List.of(registrata)))
                .isEmpty();
    }

    @Test
    void codiciFiscaliAlfanumericiDiversiSonoInContrasto() {
        var ditta = registrata(1, "RSSMRA80A01H501U", null, null, "Rossi Mario");
        assertThat(AbbinamentoAzienda.trova(letta("VRDGPP85M41F205X", null, null, "ROSSI MARIO"), List.of(ditta)))
                .isEmpty();
        // un codice fiscale alfanumerico e una partita IVA non sono confrontabili: nessun contrasto
        assertThat(AbbinamentoAzienda.trova(letta(null, PIVA_ALFA, null, "ROSSI MARIO"), List.of(ditta)))
                .get().extracting(AbbinamentoAzienda.Esito::criterio).isEqualTo(Criterio.RAGIONE_SOCIALE);
    }

    @Test
    void traPiuOmonimiCompatibiliVinceIlPrimoRegistrato() {
        var prima = registrata(3, null, PIVA_ALFA, null, "Alfa Software S.r.l.");
        var seconda = registrata(7, null, PIVA_BETA, null, "Alfa Software SRL");

        assertThat(AbbinamentoAzienda.trova(letta(null, null, null, "ALFA SOFTWARE S.R.L."), List.of(seconda, prima)))
                .get().extracting(e -> e.azienda().id().valore()).isEqualTo(3L);
    }

    @Test
    void nessunaCandidataNessunAbbinamento() {
        assertThat(AbbinamentoAzienda.trova(letta(PIVA_ALFA, null, null, "Alfa"), List.of())).isEmpty();
    }

    static ProfiloAzienda letta(String cf, String piva, String inps, String ragioneSociale) {
        return ProfiloAzienda.daCedolino(new DatiAzienda(cf, piva, inps, ragioneSociale, null, null));
    }

    static ProfiloAzienda registrata(long id, String cf, String piva, String inps, String ragioneSociale) {
        return letta(cf, piva, inps, ragioneSociale).conId(new AziendaId(id));
    }
}
