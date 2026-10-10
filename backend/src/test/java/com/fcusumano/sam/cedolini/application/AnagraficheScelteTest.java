package com.fcusumano.sam.cedolini.application;

import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.CF_GIOVANNA;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.CF_MARIO;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.PIVA_ALFA;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.PIVA_GAMMA;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conDatore;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conDipendente;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.conScelte;
import static com.fcusumano.sam.cedolini.application.CedoliniManualiDiProva.completo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fcusumano.sam.anagrafica.Azienda;
import com.fcusumano.sam.anagrafica.AziendaId;
import com.fcusumano.sam.anagrafica.AziendaRiconosciuta;
import com.fcusumano.sam.anagrafica.ConsultazioneAnagrafica;
import com.fcusumano.sam.anagrafica.DatiAzienda;
import com.fcusumano.sam.anagrafica.Persona;
import com.fcusumano.sam.anagrafica.PersonaId;
import com.fcusumano.sam.anagrafica.PersonaSintesi;
import com.fcusumano.sam.cedolini.CedolinoManuale;
import com.fcusumano.sam.cedolini.application.AnagraficheScelte.Risolte;
import com.fcusumano.sam.cedolini.application.AnagraficheScelte.Scelte;
import com.fcusumano.sam.shared.DatiNonValidiException;
import com.fcusumano.sam.shared.DatiNonValidiException.ErroreCampo;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regole delle anagrafiche scelte dall'elenco o nuove nell'inserimento a mano, con l'anagrafica simulata. Dati
 * inventati.
 */
class AnagraficheScelteTest {

    private static final Persona MARIO = new Persona(new PersonaId(1), CF_MARIO, "Mario", "Rossi",
            LocalDate.of(1980, 1, 1));
    private static final Persona MARIO_SENZA_NASCITA = new Persona(new PersonaId(2), CF_MARIO, "Mario", "Rossi", null);
    private static final Azienda ALFA = new Azienda(new AziendaId(3), null, PIVA_ALFA, "1122334455",
            "Alfa Software S.r.l.", "Via Roma 1, Roma", "ALFA01");
    private static final String MESSAGGIO_PERSONA =
            "La persona scelta non è più in archivio: scegline un'altra o creane una nuova";
    private static final String MESSAGGIO_AZIENDA =
            "L'azienda scelta non è più in archivio: scegline un'altra o creane una nuova";

    private ConsultazioneAnagrafica consultazione;

    @BeforeEach
    void anagrafica() {
        consultazione = mock(ConsultazioneAnagrafica.class);
        when(consultazione.trovaPersona(MARIO.id())).thenReturn(Optional.of(MARIO));
        when(consultazione.trovaPersona(MARIO_SENZA_NASCITA.id())).thenReturn(Optional.of(MARIO_SENZA_NASCITA));
        when(consultazione.trovaPersona(new PersonaId(99))).thenReturn(Optional.empty());
        when(consultazione.trovaAzienda(ALFA.id())).thenReturn(Optional.of(ALFA));
        when(consultazione.trovaAzienda(new AziendaId(99))).thenReturn(Optional.empty());
    }

    // ------------------------------------------------------------------------------------------ risolvi

    @Test
    void senzaSceltePersonaEAziendaSonoNuove() {
        var risolte = AnagraficheScelte.risolvi(consultazione, null, null);

        assertThat(risolte).isEqualTo(new Risolte(null, null));
        assertThat(risolte.scelte()).isEqualTo(Scelte.NESSUNA);
    }

    @Test
    void leAnagraficheSceltePresentiSonoLetteDallArchivio() {
        var risolte = AnagraficheScelte.risolvi(consultazione, 1L, 3L);

        assertThat(risolte).isEqualTo(new Risolte(MARIO, ALFA));
        assertThat(risolte.scelte()).isEqualTo(new Scelte(MARIO.id(), ALFA.id()));
        assertThat(AnagraficheScelte.risolvi(consultazione, risolte.scelte())).isEqualTo(risolte);
    }

    @Test
    void unaSceltaNonInArchivioONonPositivaEUnErroreSulSuoCampo() {
        assertThatThrownBy(() -> AnagraficheScelte.risolvi(consultazione, 99L, 3L))
                .isInstanceOfSatisfying(DatiNonValidiException.class, e -> assertThat(e.errori())
                        .containsExactly(new ErroreCampo("personaId", MESSAGGIO_PERSONA)));
        assertThatThrownBy(() -> AnagraficheScelte.risolvi(consultazione, 1L, 0L))
                .isInstanceOfSatisfying(DatiNonValidiException.class, e -> assertThat(e.errori())
                        .containsExactly(new ErroreCampo("aziendaId", MESSAGGIO_AZIENDA)));
        assertThatThrownBy(() -> AnagraficheScelte.risolvi(consultazione, -1L, 99L))
                .isInstanceOfSatisfying(DatiNonValidiException.class, e -> assertThat(e.errori()).containsExactly(
                        new ErroreCampo("personaId", MESSAGGIO_PERSONA),
                        new ErroreCampo("aziendaId", MESSAGGIO_AZIENDA)));
    }

    // ------------------------------------------------------------------------------------------ dati in archivio

    @Test
    void iDatiAnagraficiDelleAnagraficheScelteSonoQuelliInArchivio() {
        var inserito = conDatore(conDipendente(conScelte(completo(), 1L, 3L),
                        new CedolinoManuale.Dipendente("RSSMRA80A01H501X", "Maria", "Rosi", LocalDate.of(1981, 2, 2),
                                "000123")),
                new CedolinoManuale.DatoreLavoro("98765432109", "98765432109", "999", "98765432/10", "Alfa Sofware",
                        "Via Verdi 2, Milano", "ALFA02"));

        var conDati = AnagraficheScelte.conDatiInArchivio(inserito, new Risolte(MARIO, ALFA));

        assertThat(conDati.dipendente()).isEqualTo(new CedolinoManuale.Dipendente(CF_MARIO, "Mario", "Rossi",
                LocalDate.of(1980, 1, 1), "000123"));
        assertThat(conDati.datoreLavoro()).as("attributi e posizione INAIL restano quelli inseriti").isEqualTo(
                new CedolinoManuale.DatoreLavoro(null, PIVA_ALFA, "1122334455", "98765432/10",
                        "Alfa Software S.r.l.", "Via Verdi 2, Milano", "ALFA02"));
        assertThat(conDati.personaId()).isEqualTo(1L);
        assertThat(conDati.aziendaId()).isEqualTo(3L);
        assertThat(conDati.datiContrattuali()).isEqualTo(inserito.datiContrattuali());
        assertThat(conDati.periodo()).isEqualTo(inserito.periodo());
        assertThat(conDati.totali()).isEqualTo(inserito.totali());
        assertThat(conDati.voci()).isEqualTo(inserito.voci());
        assertThat(conDati.ratei()).isEqualTo(inserito.ratei());
        assertThat(conDati.tfr()).isEqualTo(inserito.tfr());
    }

    @Test
    void laDataDiNascitaInseritaCompletaSoloUnaPersonaCheNonLaHa() {
        var inserito = conDipendente(conScelte(completo(), 2L, null),
                new CedolinoManuale.Dipendente(null, null, null, LocalDate.of(1980, 1, 1), null));

        var conDati = AnagraficheScelte.conDatiInArchivio(inserito, new Risolte(MARIO_SENZA_NASCITA, null));

        assertThat(conDati.dipendente()).isEqualTo(new CedolinoManuale.Dipendente(CF_MARIO, "Mario", "Rossi",
                LocalDate.of(1980, 1, 1), null));
        assertThat(conDati.datoreLavoro()).as("azienda nuova: dati inseriti").isEqualTo(inserito.datoreLavoro());
    }

    @Test
    void lAreeAnagraficheAssentiPrendonoIDatiInArchivio() {
        var senzaAree = new CedolinoManuale(1L, 3L, null, null, null, null, null, null, null, null, null, null, null,
                null, null, false, false);

        var conDati = AnagraficheScelte.conDatiInArchivio(senzaAree, new Risolte(MARIO, ALFA));

        assertThat(conDati.dipendente()).isEqualTo(new CedolinoManuale.Dipendente(CF_MARIO, "Mario", "Rossi",
                LocalDate.of(1980, 1, 1), null));
        assertThat(conDati.datoreLavoro()).isEqualTo(new CedolinoManuale.DatoreLavoro(null, PIVA_ALFA, "1122334455",
                null, "Alfa Software S.r.l.", null, null));
        assertThat(AnagraficheScelte.conDatiInArchivio(conScelte(senzaAree, null, null), new Risolte(null, null)))
                .isEqualTo(conScelte(senzaAree, null, null));
    }

    // ------------------------------------------------------------------------------------------ gia' in archivio

    @Test
    void unaPersonaNuovaConUnCodiceFiscaleInArchivioVaSceltaDallElenco() {
        when(consultazione.elencoPersone()).thenReturn(List.of(
                new PersonaSintesi(MARIO.id(), "Mario", "Rossi", CF_MARIO, null, "Mario Rossi")));

        assertThat(AnagraficheScelte.giaInArchivio(consultazione, CF_MARIO, null, Scelte.NESSUNA)).containsExactly(
                new ErroreCampo("dipendente.codiceFiscale",
                        "Questo codice fiscale è già in archivio: seleziona la persona dall'elenco"));
        assertThat(AnagraficheScelte.giaInArchivio(consultazione, CF_GIOVANNA, null, Scelte.NESSUNA)).isEmpty();
        assertThat(AnagraficheScelte.giaInArchivio(consultazione, null, null, Scelte.NESSUNA)).isEmpty();
        assertThat(AnagraficheScelte.giaInArchivio(consultazione, CF_MARIO, null, new Scelte(MARIO.id(), null)))
                .as("persona scelta: il codice e' il suo").isEmpty();
    }

    @Test
    void unAziendaNuovaRiconosciutaDalCensimentoVaSceltaDallElenco() {
        var gamma = new DatiAzienda(null, PIVA_GAMMA, null, "Gamma Servizi S.r.l.", null, null);
        var alfa = new DatiAzienda(null, PIVA_ALFA, null, "Alfa Software S.r.l.", null, null);
        when(consultazione.aziendaRiconosciuta(gamma)).thenReturn(Optional.empty());
        when(consultazione.aziendaRiconosciuta(alfa)).thenReturn(Optional.of(
                new AziendaRiconosciuta(ALFA.id(), AziendaRiconosciuta.Dato.PARTITA_IVA)));

        assertThat(AnagraficheScelte.giaInArchivio(consultazione, null, alfa, Scelte.NESSUNA)).containsExactly(
                new ErroreCampo("datoreLavoro.partitaIva",
                        "Questa partita IVA è già in archivio: seleziona l'azienda dall'elenco"));
        assertThat(AnagraficheScelte.giaInArchivio(consultazione, null, gamma, Scelte.NESSUNA)).isEmpty();
        assertThat(AnagraficheScelte.giaInArchivio(consultazione, null, alfa, new Scelte(null, ALFA.id())))
                .as("azienda scelta: nessun riconoscimento").isEmpty();
        verify(consultazione, never()).elencoPersone();
    }

    @Test
    void ogniDatoRiconosciutoHaIlSuoCampo() {
        assertThat(List.of(AziendaRiconosciuta.Dato.values())).extracting(AnagraficheScelte::erroreAzienda)
                .extracting(ErroreCampo::campo).containsExactly("datoreLavoro.partitaIva",
                        "datoreLavoro.codiceFiscale", "datoreLavoro.posizioneInps", "datoreLavoro.ragioneSociale");
        assertThat(AnagraficheScelte.datiAzienda(null)).isEmpty();
        assertThat(AnagraficheScelte.datiAzienda(new CedolinoManuale.DatoreLavoro("CF", "PI", "INPS", "INAIL", "RS",
                "IND", "COD"))).contains(new DatiAzienda("CF", "PI", "INPS", "RS", "IND", "COD"));
        verify(consultazione, never()).aziendaRiconosciuta(any());
    }
}
