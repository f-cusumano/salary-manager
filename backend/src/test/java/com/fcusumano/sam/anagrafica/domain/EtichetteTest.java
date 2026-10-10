package com.fcusumano.sam.anagrafica.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fcusumano.sam.anagrafica.Azienda;
import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.Persona;
import com.fcusumano.sam.anagrafica.PersonaId;
import java.util.List;
import org.junit.jupiter.api.Test;

class EtichetteTest {

    @Test
    void personeOmonimeConCodiceFiscaleLeAltreSoloNomeECognome() {
        var mario = new Persona(new PersonaId(1), "RSSMRA80A01H501U", "Mario", "Rossi", null);
        var omonimo = new Persona(new PersonaId(2), "RSSMRA75C12F205K", "MARIO", "ROSSI", null);
        var giuseppina = new Persona(new PersonaId(3), "VRDGPP85M41F205X", "Giuseppina", "Verdi", null);

        var etichette = Etichette.persone(List.of(mario, omonimo, giuseppina));

        assertThat(etichette.get(mario.id())).isEqualTo("Mario Rossi (RSSMRA80A01H501U)");
        assertThat(etichette.get(omonimo.id())).isEqualTo("MARIO ROSSI (RSSMRA75C12F205K)");
        assertThat(etichette.get(giuseppina.id())).isEqualTo("Giuseppina Verdi");
    }

    @Test
    void aziendeOmonimeConPartitaIvaOInMancanzaCodiceFiscaleOMatricolaInps() {
        var conPiva = azienda(1, null, "01234567890", null, "Alfa Software S.r.l.");
        var conCf = azienda(2, "RSSMRA80A01H501U", null, null, "ALFA SOFTWARE SRL");
        var conInps = azienda(3, null, null, "1234567890", "Alfa Software - SRL");
        var unica = azienda(4, null, "09876543210", null, "Beta Consulting S.p.A.");

        var etichette = Etichette.aziende(List.of(conPiva, conCf, conInps, unica));

        assertThat(etichette.get(conPiva.id())).isEqualTo("Alfa Software S.r.l. (P.IVA 01234567890)");
        assertThat(etichette.get(conCf.id())).isEqualTo("ALFA SOFTWARE SRL (CF RSSMRA80A01H501U)");
        assertThat(etichette.get(conInps.id())).isEqualTo("Alfa Software - SRL (Pos. INPS 1234567890)");
        assertThat(etichette.get(unica.id())).isEqualTo("Beta Consulting S.p.A.");
    }

    private static Azienda azienda(long id, String cf, String piva, String inps, String ragioneSociale) {
        return new Azienda(new AziendaId(id), cf, piva, inps, ragioneSociale, null, null);
    }
}
