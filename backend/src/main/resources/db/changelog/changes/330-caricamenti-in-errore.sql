--liquibase formatted sql

--changeset sam:330-caricamento-motivo
--comment: Motivo dell'errore di un caricamento (enum MotivoErrore del modulo cedolini). Valorizzato solo in ERRORE;
--comment: null anche per gli errori registrati prima di questa versione. Indice per hash: file in errore (stesso
--comment: contenuto = stesso file) e tentativi successivi dello stesso file.
ALTER TABLE caricamento ADD COLUMN motivo VARCHAR(40);
ALTER TABLE caricamento ADD CONSTRAINT ck_caricamento_motivo CHECK (motivo IS NULL OR (stato = 'ERRORE' AND motivo IN (
    'PDF_PROTETTO', 'PDF_SENZA_TESTO', 'LAYOUT_NON_RICONOSCIUTO', 'LETTURA_NON_RIUSCITA', 'DATI_INCOMPLETI',
    'PDF_TROPPO_GRANDE', 'SERVIZIO_NON_DISPONIBILE', 'INTERROTTO', 'ERRORE_IMPREVISTO',
    'PDF_NON_VALIDO', 'PDF_TROPPE_PAGINE', 'PIU_CEDOLINI')));
CREATE INDEX ix_caricamento_hash ON caricamento (workspace_id, hash_contenuto, id);

--changeset sam:331-caricamento-pdf
--comment: PDF originale (come caricato, anche se protetto) di un file in errore completabile a mano. Uno per file
--comment: (workspace + hash). Eliminato nella stessa transazione in cui il file viene archiviato come cedolino,
--comment: scartato (DUPLICATO, AGGIUNTIVO_NON_GESTITO), chiuso con un motivo non completabile o rimosso dall'utente.
CREATE TABLE caricamento_pdf (
    workspace_id   BIGINT                   NOT NULL,
    hash_contenuto VARCHAR(64)              NOT NULL,
    dimensione     BIGINT                   NOT NULL,
    contenuto      BYTEA                    NOT NULL,
    creato_il      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_caricamento_pdf PRIMARY KEY (workspace_id, hash_contenuto),
    CONSTRAINT fk_caricamento_pdf_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (id)
);

--changeset sam:332-caricamento-attribuzione
--comment: Mensilita' (rapporto + anno + mese 1..14) a cui l'utente associa un file in errore. Una per file, una per
--comment: competenza. Eliminata con il rapporto (cascata), quando il file viene archiviato o risolto, o dall'utente.
CREATE TABLE caricamento_attribuzione (
    workspace_id    BIGINT                   NOT NULL,
    hash_contenuto  VARCHAR(64)              NOT NULL,
    rapporto_id     BIGINT                   NOT NULL,
    competenza_anno INTEGER                  NOT NULL,
    competenza_mese INTEGER                  NOT NULL,
    attribuito_il   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_caricamento_attribuzione PRIMARY KEY (workspace_id, hash_contenuto),
    CONSTRAINT fk_caricamento_attribuzione_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (id),
    CONSTRAINT fk_caricamento_attribuzione_rapporto FOREIGN KEY (rapporto_id)
        REFERENCES rapporto_lavoro (id) ON DELETE CASCADE,
    CONSTRAINT uk_caricamento_attribuzione_competenza UNIQUE (rapporto_id, competenza_anno, competenza_mese),
    CONSTRAINT ck_caricamento_attribuzione_competenza
        CHECK (competenza_anno BETWEEN 1900 AND 2999 AND competenza_mese BETWEEN 1 AND 14)
);
