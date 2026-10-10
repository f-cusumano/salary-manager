--liquibase formatted sql

--changeset sam:200-ccnl
--comment: Tabella di lookup dei CCNL. Dato di riferimento globale (non per workspace).
--comment: I campi nullable indicano un valore non noto con certezza: vedi note_verifica.
CREATE TABLE ccnl (
    codice                 VARCHAR(50)  PRIMARY KEY,
    descrizione            VARCHAR(200) NOT NULL,
    mensilita              INTEGER     NOT NULL,
    periodicita_scatti_mesi INTEGER,
    max_scatti             INTEGER,
    mese_quattordicesima   INTEGER,
    parole_chiave          VARCHAR(500),
    note_verifica          VARCHAR(1000),
    attivo                 BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_ccnl_mensilita CHECK (mensilita IN (13, 14)),
    CONSTRAINT ck_ccnl_mese_14 CHECK (mese_quattordicesima IS NULL OR (mese_quattordicesima BETWEEN 1 AND 12)),
    CONSTRAINT ck_ccnl_quattordicesima CHECK (mensilita = 14 OR mese_quattordicesima IS NULL),
    CONSTRAINT ck_ccnl_scatti CHECK (periodicita_scatti_mesi IS NULL OR periodicita_scatti_mesi > 0)
);
