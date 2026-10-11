package com.fcusumano.sam;

import static com.fcusumano.sam.shared.db.jooq.Tables.AZIENDA;
import static com.fcusumano.sam.shared.db.jooq.Tables.CARICAMENTO;
import static com.fcusumano.sam.shared.db.jooq.Tables.CARICAMENTO_ATTRIBUZIONE;
import static com.fcusumano.sam.shared.db.jooq.Tables.CCNL;
import static com.fcusumano.sam.shared.db.jooq.Tables.CEDOLINO;
import static com.fcusumano.sam.shared.db.jooq.Tables.CEDOLINO_PDF;
import static com.fcusumano.sam.shared.db.jooq.Tables.PERSONA;
import static com.fcusumano.sam.shared.db.jooq.Tables.RAPPORTO_LAVORO;
import static com.fcusumano.sam.shared.db.jooq.Tables.WORKSPACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Avvio del contesto completo e verifica dello schema creato da Liquibase su H2 (MODE=PostgreSQL).
 * Dati inventati.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SalaryManagerApplicationTests {

    @Autowired
    DSLContext dsl;

    @Test
    void ilContestoPartEIlWorkspaceDiDefaultEsiste() {
        assertThat(dsl.fetchExists(WORKSPACE, WORKSPACE.ID.eq(1L))).isTrue();
    }

    @Test
    void laTabellaCcnlEPopolata() {
        assertThat(dsl.fetchCount(CCNL)).isGreaterThanOrEqualTo(7);
        assertThat(dsl.fetchValue(CCNL.MENSILITA, CCNL.CODICE.eq("COMMERCIO"))).isEqualTo(14);
    }

    @Test
    void unPdfDa25MbVieneSalvatoERiletto() {
        long cedolinoId = inserisciCedolino(2024, 1, "ORDINARIA", "a".repeat(64));
        var pdf = new byte[25 * 1024 * 1024];
        pdf[pdf.length - 1] = 7;
        dsl.insertInto(CEDOLINO_PDF)
                .set(CEDOLINO_PDF.CEDOLINO_ID, cedolinoId)
                .set(CEDOLINO_PDF.WORKSPACE_ID, 1L)
                .set(CEDOLINO_PDF.DIMENSIONE, (long) pdf.length)
                .set(CEDOLINO_PDF.CONTENUTO, pdf)
                .execute();

        var letto = dsl.fetchValue(CEDOLINO_PDF.CONTENUTO, CEDOLINO_PDF.CEDOLINO_ID.eq(cedolinoId));
        assertThat(letto).hasSize(pdf.length);
        assertThat(letto[pdf.length - 1]).isEqualTo((byte) 7);

        dsl.deleteFrom(CEDOLINO).where(CEDOLINO.ID.eq(cedolinoId)).execute();
        assertThat(dsl.fetchExists(CEDOLINO_PDF, CEDOLINO_PDF.CEDOLINO_ID.eq(cedolinoId))).isFalse();
    }

    @Test
    void laCompetenzaDeveEssereCoerenteConIlTipo() {
        assertThatThrownBy(() -> inserisciCedolino(2024, 12, "TREDICESIMA", "b".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unicitaPerMeseEPerPeriodoDiFineRapporto() {
        long rapportoId = inserisciRapporto();
        inserisciCedolino(rapportoId, 2024, 1, "ORDINARIA", null, null, "c".repeat(64));
        assertThatThrownBy(() -> inserisciCedolino(rapportoId, 2024, 1, "ORDINARIA", null, null, "d".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Cedolini di fine rapporto: mese NULL (piu' righe ammesse), unicita' su periodo e su progressivo
        // (nomi file distinti per isolare i vincoli verificati)
        inserisciCedolino(rapportoId, 2024, null, "FINE_RAPPORTO", 1, LocalDate.of(2024, 3, 1), "e".repeat(64));
        inserisciCedolino(rapportoId, 2024, null, "FINE_RAPPORTO", 2, LocalDate.of(2024, 4, 1), "f".repeat(64));
        assertThatThrownBy(() -> inserisciCedolino(rapportoId, 2024, null, "FINE_RAPPORTO", 3, LocalDate.of(2024, 4, 1), "g".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> inserisciCedolino(rapportoId, 2024, null, "FINE_RAPPORTO", 2, LocalDate.of(2024, 5, 1), "h".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ilMotivoDelCaricamentoEAmmessoSoloInErroreETraIMotiviNoti() {
        inserisciCaricamento("ERRORE", "PDF_PROTETTO", "1".repeat(64));
        inserisciCaricamento("ERRORE", null, "2".repeat(64));

        assertThatThrownBy(() -> inserisciCaricamento("DUPLICATO", "PDF_PROTETTO", "3".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> inserisciCaricamento("ERRORE", "MOTIVO_SCONOSCIUTO", "4".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unaMensilitaSiAssociaAUnSoloFileEAssociazioneSparisceColRapporto() {
        long rapportoId = inserisciRapporto();
        inserisciAttribuzione(rapportoId, "a".repeat(64), 2023, 5);

        assertThatThrownBy(() -> inserisciAttribuzione(rapportoId, "b".repeat(64), 2023, 5))
                .as("stessa mensilita'").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> inserisciAttribuzione(rapportoId, "a".repeat(64), 2023, 6))
                .as("stesso file").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> inserisciAttribuzione(rapportoId, "c".repeat(64), 2023, 15))
                .as("mese oltre la quattordicesima").isInstanceOf(DataIntegrityViolationException.class);

        dsl.deleteFrom(RAPPORTO_LAVORO).where(RAPPORTO_LAVORO.ID.eq(rapportoId)).execute();
        assertThat(dsl.fetchCount(CARICAMENTO_ATTRIBUZIONE)).isZero();
    }

    private void inserisciCaricamento(String stato, String motivo, String hash) {
        dsl.insertInto(CARICAMENTO)
                .set(CARICAMENTO.WORKSPACE_ID, 1L)
                .set(CARICAMENTO.LOTTO, UUID.randomUUID())
                .set(CARICAMENTO.NOME_FILE_ORIGINALE, "prova.pdf")
                .set(CARICAMENTO.DIMENSIONE, 10L)
                .set(CARICAMENTO.HASH_CONTENUTO, hash)
                .set(CARICAMENTO.STATO, stato)
                .set(CARICAMENTO.MOTIVO, motivo)
                .execute();
    }

    private void inserisciAttribuzione(long rapportoId, String hash, int anno, int mese) {
        dsl.insertInto(CARICAMENTO_ATTRIBUZIONE)
                .set(CARICAMENTO_ATTRIBUZIONE.WORKSPACE_ID, 1L)
                .set(CARICAMENTO_ATTRIBUZIONE.HASH_CONTENUTO, hash)
                .set(CARICAMENTO_ATTRIBUZIONE.RAPPORTO_ID, rapportoId)
                .set(CARICAMENTO_ATTRIBUZIONE.COMPETENZA_ANNO, anno)
                .set(CARICAMENTO_ATTRIBUZIONE.COMPETENZA_MESE, mese)
                .execute();
    }

    private long inserisciCedolino(int anno, int mese, String tipo, String hash) {
        return inserisciCedolino(inserisciRapporto(), anno, mese, tipo, null, null, hash);
    }

    private long inserisciRapporto() {
        long personaId = dsl.insertInto(PERSONA)
                .set(PERSONA.WORKSPACE_ID, 1L)
                .set(PERSONA.CODICE_FISCALE, "RSSMRA80A01H501U")
                .set(PERSONA.NOME, "Mario")
                .set(PERSONA.COGNOME, "Rossi")
                .returningResult(PERSONA.ID).fetchOne().value1();
        long aziendaId = dsl.insertInto(AZIENDA)
                .set(AZIENDA.WORKSPACE_ID, 1L)
                .set(AZIENDA.RAGIONE_SOCIALE, "Alfa Software S.r.l.")
                .set(AZIENDA.RAGIONE_SOCIALE_NORMALIZZATA, "ALFA SOFTWARE SRL")
                .returningResult(AZIENDA.ID).fetchOne().value1();
        return dsl.insertInto(RAPPORTO_LAVORO)
                .set(RAPPORTO_LAVORO.WORKSPACE_ID, 1L)
                .set(RAPPORTO_LAVORO.PERSONA_ID, personaId)
                .set(RAPPORTO_LAVORO.AZIENDA_ID, aziendaId)
                .set(RAPPORTO_LAVORO.CCNL_CODICE, "COMMERCIO")
                .returningResult(RAPPORTO_LAVORO.ID).fetchOne().value1();
    }

    private long inserisciCedolino(long rapportoId, int anno, Integer mese, String tipo, Integer progressivoTfr,
                                   LocalDate periodoFineRapporto, String hash) {
        var periodo = periodoFineRapporto != null ? periodoFineRapporto : LocalDate.of(anno, Math.min(mese, 12), 1);
        var codice = progressivoTfr != null ? "TFR%02d".formatted(progressivoTfr) : "%02d".formatted(mese);
        return dsl.insertInto(CEDOLINO)
                .set(CEDOLINO.WORKSPACE_ID, 1L)
                .set(CEDOLINO.RAPPORTO_ID, rapportoId)
                .set(CEDOLINO.TIPO, tipo)
                .set(CEDOLINO.TIPO_DICHIARATO, tipo)
                .set(CEDOLINO.ANNO, anno)
                .set(CEDOLINO.MESE, mese)
                .set(CEDOLINO.PROGRESSIVO_TFR, progressivoTfr)
                .set(CEDOLINO.PERIODO_RETRIBUZIONE, periodo)
                .set(CEDOLINO.PERIODO_FINE_RAPPORTO, periodoFineRapporto)
                .set(CEDOLINO.NOME_FILE, "%d.%s_%d_%s.pdf".formatted(anno, codice, rapportoId, hash.charAt(0)))
                .set(CEDOLINO.HASH_CONTENUTO, hash)
                .set(CEDOLINO.ESTRATTO_JSON, "{}")
                .returningResult(CEDOLINO.ID).fetchOne().value1();
    }
}
