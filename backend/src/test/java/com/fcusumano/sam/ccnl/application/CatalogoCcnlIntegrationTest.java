package com.fcusumano.sam.ccnl.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.ccnl.CatalogoCcnl;
import com.fcusumano.sam.ccnl.Ccnl;
import com.fcusumano.sam.ccnl.DurataPreavviso;
import com.fcusumano.sam.ccnl.RegolaPreavviso;
import com.fcusumano.sam.ccnl.domain.RegolaRiconoscimento;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Catalogo CCNL sulla tabella reale (seed Liquibase su H2).
 */
@ApplicationModuleTest
@ActiveProfiles("test")
class CatalogoCcnlIntegrationTest {

    @Autowired
    CatalogoCcnl catalogo;

    @Autowired
    ArchivioCcnl archivio;

    @Test
    void elencoOrdinatoPerDescrizioneConValoriCertiEIncertiDistinti() {
        var elenco = catalogo.elenco();
        assertThat(elenco).extracting(Ccnl::descrizione).isSorted();
        assertThat(elenco).extracting(Ccnl::codice).containsExactlyInAnyOrder(
                "ALTRO_13", "ALTRO_14",
                "COMMERCIO", "COMMERCIO_CONFESERCENTI", "DISTRIBUZIONE_MODERNA_ORGANIZZATA",
                "DISTRIBUZIONE_COOPERATIVA", "FARMACIE_PRIVATE", "DIRIGENTI_TERZIARIO",
                "METALMECCANICO_INDUSTRIA", "METALMECCANICO_PMI", "METALMECCANICO_ARTIGIANATO",
                "ALIMENTARI_INDUSTRIA", "TESSILE_ABBIGLIAMENTO_MODA_INDUSTRIA", "GOMMA_PLASTICA_INDUSTRIA",
                "LEGNO_ARREDAMENTO_INDUSTRIA", "CHIMICO_FARMACEUTICO_INDUSTRIA", "DIRIGENTI_INDUSTRIA",
                "TURISMO", "MULTISERVIZI", "COOPERATIVE_SOCIALI", "VIGILANZA_PRIVATA", "LOGISTICA_TRASPORTO_MERCI",
                "IGIENE_AMBIENTALE", "AUTOFERROTRANVIERI", "LAVORO_DOMESTICO", "STUDI_PROFESSIONALI",
                "SANITA_PRIVATA_AIOP", "CREDITO", "ASSICURAZIONI", "ELETTRICO", "GAS_ACQUA", "TELECOMUNICAZIONI",
                "EDILIZIA_INDUSTRIA", "EDILIZIA_ARTIGIANATO");

        var commercio = catalogo.trova("COMMERCIO").orElseThrow();
        assertThat(commercio.descrizione()).isEqualTo("Commercio - Terziario, distribuzione e servizi (Confcommercio)");
        assertThat(commercio.regoleScattiNote()).isTrue();
        assertThat(commercio.noteVerifica()).isNull();

        // Valori non certi: NULL con nota di verifica che spiega perche'
        var turismo = catalogo.trova("TURISMO").orElseThrow();
        assertThat(turismo.meseQuattordicesima()).isEqualTo(7);
        assertThat(turismo.regoleScattiNote()).isFalse();
        assertThat(turismo.noteVerifica()).startsWith("La voce raggruppa due CCNL con scatti diversi");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = ';', value = {
            "COMMERCIO;14;36;10;7;H011",
            "COMMERCIO_CONFESERCENTI;14;36;10;7;H012",
            "DISTRIBUZIONE_MODERNA_ORGANIZZATA;14;36;10;7;H008",
            "DISTRIBUZIONE_COOPERATIVA;14;;10;6;H016",
            "FARMACIE_PRIVATE;14;24;15;7;H121",
            "DIRIGENTI_TERZIARIO;14;;;6;H021",
            "METALMECCANICO_INDUSTRIA;13;24;5;;C011",
            "METALMECCANICO_PMI;13;24;5;;C018",
            "METALMECCANICO_ARTIGIANATO;13;24;5;;C030",
            "ALIMENTARI_INDUSTRIA;14;24;5;;E012",
            "TESSILE_ABBIGLIAMENTO_MODA_INDUSTRIA;13;24;4;;D014",
            "GOMMA_PLASTICA_INDUSTRIA;13;24;5;;B371",
            "LEGNO_ARREDAMENTO_INDUSTRIA;13;24;5;;F051",
            "CHIMICO_FARMACEUTICO_INDUSTRIA;13;;;;B011",
            "DIRIGENTI_INDUSTRIA;13;;;;V012",
            "TURISMO;14;;;7;H052",
            "MULTISERVIZI;14;;;;",
            "COOPERATIVE_SOCIALI;13;24;5;;T151",
            "VIGILANZA_PRIVATA;14;;;;",
            "LOGISTICA_TRASPORTO_MERCI;14;;;;I100",
            "IGIENE_AMBIENTALE;14;;;;",
            "AUTOFERROTRANVIERI;14;24;6;7;",
            "LAVORO_DOMESTICO;13;24;7;;H501",
            "STUDI_PROFESSIONALI;14;36;8;;",
            "SANITA_PRIVATA_AIOP;13;;;;",
            "CREDITO;13;;;;",
            "ASSICURAZIONI;14;;;;",
            "ELETTRICO;14;;;6;",
            "GAS_ACQUA;14;;;6;",
            "TELECOMUNICAZIONI;13;24;7;;",
            "EDILIZIA_INDUSTRIA;13;;;;F012",
            "EDILIZIA_ARTIGIANATO;13;;;;F015",
            "ALTRO_13;13;;;;",
            "ALTRO_14;14;;;7;"})
    void valoriVerificatiDiOgniCcnl(String codice, int mensilita, Integer periodicita, Integer massimo, Integer mese,
                                   String codiceCnel) {
        var ccnl = catalogo.trova(codice).orElseThrow();
        assertThat(ccnl.mensilita()).isEqualTo(mensilita);
        assertThat(ccnl.periodicitaScattiMesi()).isEqualTo(periodicita);
        assertThat(ccnl.maxScatti()).isEqualTo(massimo);
        assertThat(ccnl.meseQuattordicesima()).isEqualTo(mese);
        assertThat(archivio.regoleRiconoscimento()).filteredOn(regola -> regola.codice().equals(codice))
                .extracting(RegolaRiconoscimento::codiceCnel).containsExactly(codiceCnel);
    }

    @Test
    void ogniValoreNonImpostatoHaUnaNotaDiVerifica() {
        assertThat(catalogo.elenco())
                .filteredOn(ccnl -> !ccnl.regoleScattiNote()
                        || (ccnl.prevedeQuattordicesima() && ccnl.meseQuattordicesima() == null))
                .allSatisfy(ccnl -> assertThat(ccnl.noteVerifica()).as(ccnl.codice()).isNotBlank());
    }

    @Test
    void descrizioniENoteConLeLettereAccentate() {
        assertThat(catalogo.trova("ALTRO_13").orElseThrow().descrizione()).isEqualTo("Altro CCNL (13 mensilità)");
        assertThat(catalogo.trova("ALTRO_14").orElseThrow().descrizione()).isEqualTo("Altro CCNL (14 mensilità)");
        assertThat(catalogo.elenco()).extracting(Ccnl::noteVerifica).filteredOn(nota -> nota != null)
                .noneMatch(nota -> nota.matches(".*\\b(mensilita|periodicita|anzianita)\\b.*"));
        assertThat(catalogo.trova("SANITA_PRIVATA_AIOP").orElseThrow().descrizione()).startsWith("Sanità privata");
    }

    @Test
    void laDescrizioneDiOgniCcnlNeRiconosceIlCodice() {
        // le descrizioni sono anche i suggerimenti dell'inserimento a mano: scelte come testo, riconoscono il CCNL
        assertThat(catalogo.elenco()).filteredOn(ccnl -> !ccnl.codice().startsWith("ALTRO_"))
                .allSatisfy(ccnl -> assertThat(catalogo.riconosci(ccnl.descrizione())).as(ccnl.descrizione())
                        .map(Ccnl::codice).contains(ccnl.codice()));
        assertThat(catalogo.riconosci("Altro CCNL (13 mensilità)")).isEmpty();
    }

    @Test
    void preavvisoSoloPerICcnlVerificatiConTreFasceDiAnzianitaPerGruppo() {
        var verificati = List.of("COMMERCIO", "METALMECCANICO_INDUSTRIA", "STUDI_PROFESSIONALI", "TURISMO");
        assertThat(catalogo.elenco()).extracting(Ccnl::codice)
                .allSatisfy(codice -> assertThat(catalogo.preavviso(codice).isEmpty())
                        .as(codice).isEqualTo(!verificati.contains(codice)));
        verificati.forEach(codice -> catalogo.preavviso(codice).stream()
                .collect(Collectors.groupingBy(RegolaPreavviso::livelli,
                        Collectors.mapping(RegolaPreavviso::oltreAnni, Collectors.toList())))
                .forEach((livelli, fasce) -> assertThat(fasce).as(codice + " " + livelli).containsExactly(0, 5, 10)));
        assertThat(catalogo.preavviso(null)).isEmpty();
        assertThat(catalogo.preavviso("INESISTENTE")).isEmpty();
    }

    @Test
    void preavvisoDelCommercioPerLivelloEAnzianita() {
        var regole = catalogo.preavviso("COMMERCIO");
        assertThat(regole).hasSize(12);
        var terzoOltreCinque = regole.stream().filter(r -> r.comprende("3° livello") && r.oltreAnni() == 5)
                .findFirst().orElseThrow();
        assertThat(terzoOltreCinque.descrizioneLivelli()).isEqualTo("2° e 3° livello");
        assertThat(terzoOltreCinque.dimissioni()).isEqualTo(new DurataPreavviso(0, 30));
        assertThat(terzoOltreCinque.licenziamento()).isEqualTo(new DurataPreavviso(0, 45));
        assertThat(terzoOltreCinque.riferimento()).contains("Art. 247");

        var metalmeccanico = catalogo.preavviso("METALMECCANICO_INDUSTRIA").stream()
                .filter(r -> r.comprende("C3") && r.oltreAnni() == 10).findFirst().orElseThrow();
        assertThat(metalmeccanico.dimissioni()).isEqualTo(new DurataPreavviso(2, 15));
        assertThat(metalmeccanico.licenziamento()).isEqualTo(new DurataPreavviso(2, 15));
    }

    @Test
    void trovaSoloPerCodiceEsatto() {
        assertThat(catalogo.trova("COMMERCIO")).isPresent();
        assertThat(catalogo.trova("commercio")).isEmpty();
        assertThat(catalogo.trova("INESISTENTE")).isEmpty();
        assertThat(catalogo.trova(null)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "Metalmeccanici imp.;METALMECCANICO_INDUSTRIA",
            "CONTRATTO DI LAVORO Metalmeccanici imp. CNEL C011;METALMECCANICO_INDUSTRIA",
            "CONTRATTO DI LAVORO Metalmeccanici imp. (p.ind) CNEL C018;METALMECCANICO_PMI",
            "Industria metalmeccanica e installazione di impianti;METALMECCANICO_INDUSTRIA",
            "Metalmeccanica PMI Confapi;METALMECCANICO_PMI",
            "Metalmeccanica artigianato;METALMECCANICO_ARTIGIANATO",
            "Metalmeccanici artigiani - area meccanica;METALMECCANICO_ARTIGIANATO",
            "CCNL Commercio Terziario;COMMERCIO",
            "CCNL Terziario Confcommercio;COMMERCIO",
            "Terziario, distribuzione e servizi;COMMERCIO",
            "Commercio Confesercenti;COMMERCIO_CONFESERCENTI",
            "Terziario Confesercenti;COMMERCIO_CONFESERCENTI",
            "Distribuzione moderna organizzata;DISTRIBUZIONE_MODERNA_ORGANIZZATA",
            "Terziario DMO Federdistribuzione;DISTRIBUZIONE_MODERNA_ORGANIZZATA",
            "Distribuzione cooperativa;DISTRIBUZIONE_COOPERATIVA",
            "Commercio cooperative di consumo;DISTRIBUZIONE_COOPERATIVA",
            "Dipendenti da farmacia privata;FARMACIE_PRIVATE",
            "Farmacie private Federfarma;FARMACIE_PRIVATE",
            "Dirigenti commercio;DIRIGENTI_TERZIARIO",
            "Dirigenti aziende del terziario;DIRIGENTI_TERZIARIO",
            "Alimentari industria;ALIMENTARI_INDUSTRIA",
            "Industrie alimentari;ALIMENTARI_INDUSTRIA",
            "Tessile abbigliamento moda;TESSILE_ABBIGLIAMENTO_MODA_INDUSTRIA",
            "Tessili industria;TESSILE_ABBIGLIAMENTO_MODA_INDUSTRIA",
            "Gomma plastica;GOMMA_PLASTICA_INDUSTRIA",
            "Gomma e cavi elettrici;GOMMA_PLASTICA_INDUSTRIA",
            "Industria della gomma, cavi elettrici ed affini;GOMMA_PLASTICA_INDUSTRIA",
            "Legno e arredamento industria;LEGNO_ARREDAMENTO_INDUSTRIA",
            "Chimico farmaceutico;CHIMICO_FARMACEUTICO_INDUSTRIA",
            "Chimici industria;CHIMICO_FARMACEUTICO_INDUSTRIA",
            "Dirigenti industria;DIRIGENTI_INDUSTRIA",
            "Dirigenti aziende industriali;DIRIGENTI_INDUSTRIA",
            "Turismo - Pubblici esercizi;TURISMO",
            "Turismo Confcommercio;TURISMO",
            "Pubblici esercizi Fipe;TURISMO",
            "Multiservizi;MULTISERVIZI",
            "Pulizia e servizi integrati;MULTISERVIZI",
            "Cooperative sociali;COOPERATIVE_SOCIALI",
            "Vigilanza privata;VIGILANZA_PRIVATA",
            "Servizi fiduciari;VIGILANZA_PRIVATA",
            "Logistica, trasporto merci e spedizione;LOGISTICA_TRASPORTO_MERCI",
            "Autotrasporto merci e logistica;LOGISTICA_TRASPORTO_MERCI",
            "Igiene ambientale;IGIENE_AMBIENTALE",
            "Autoferrotranvieri;AUTOFERROTRANVIERI",
            "Lavoro domestico;LAVORO_DOMESTICO",
            "Colf e badanti;LAVORO_DOMESTICO",
            "Studi professionali;STUDI_PROFESSIONALI",
            "Sanità privata AIOP;SANITA_PRIVATA_AIOP",
            "Case di cura private personale non medico;SANITA_PRIVATA_AIOP",
            "Credito;CREDITO",
            "Bancari ABI;CREDITO",
            "Assicurazioni ANIA;ASSICURAZIONI",
            "Imprese assicuratrici;ASSICURAZIONI",
            "Settore elettrico;ELETTRICO",
            "Gas-acqua;GAS_ACQUA",
            "Telecomunicazioni;TELECOMUNICAZIONI",
            "Edilizia industria;EDILIZIA_INDUSTRIA",
            "Edili industria CNEL F012;EDILIZIA_INDUSTRIA",
            "Edilizia artigianato;EDILIZIA_ARTIGIANATO",
            "Edili artigianato e PMI;EDILIZIA_ARTIGIANATO",
            "Codice CNEL H012;COMMERCIO_CONFESERCENTI",
            "CNEL T151;COOPERATIVE_SOCIALI"})
    void riconosceITestiStampatiTipici(String testo, String atteso) {
        assertThat(catalogo.riconosci(testo)).map(Ccnl::codice).contains(atteso);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // contratti fratelli o sigle ambigue
            "Metalmeccanici imp. (p.ind)",
            "Metalmeccanici imp. (p.ind) CNEL C011",
            "Metalmeccanica Confimi",
            "Edilizia PMI",
            "Edilizia cooperative",
            "Turismo Confesercenti",
            "Commercio e turismo",
            "Terziario e turismo",
            "Dirigenti",
            "Federmanager",
            "Dirigenti cooperative di consumo",
            "Credito cooperativo",
            "Credito assicurazioni",
            "Agenzie di assicurazione",
            "Chimica gomma plastica vetro PMI",
            "Gomma e plastica Confapi",
            "Alimentari artigianato",
            "Tessili artigianato",
            "Legno edilizia",
            "Elettrici gas acqua",
            "AIOP RSA",
            "Cooperativa sociale",
            "Multiservizi cooperative sociali",
            "Logistica artigianato trasporti e logistica",
            // il fratello escluso non lascia come unico candidato un CCNL di un altro settore
            "Gomma cavi elettrici PMI",
            "Gomma, cavi elettrici ed affini - Confapi Unionchimica",
            "Chimica, gomma, cavi elettrici",
            // sigle puntate: "P.M.I." diventa "p m i" ed esclude come "PMI"
            "Metalmeccanica P.M.I.",
            "Commercio P.M.I.",
            "Alimentari P.M.I.",
            "Coop sociali multiservizi",
            // codice CNEL in contrasto con le parole chiave, o due codici
            "Commercio CNEL C011",
            "C011 H011",
            // testi generici
            "Agenzia commerciale",
            "Contratto collettivo nazionale di lavoro",
            "Abi"})
    void inCasoDiDubbioNonRiconosceNulla(String testo) {
        assertThat(catalogo.riconosci(testo)).isEmpty();
    }
}
