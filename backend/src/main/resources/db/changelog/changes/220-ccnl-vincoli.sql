--liquibase formatted sql

--changeset sam:220-fk-rapporto-ccnl
--comment: Vincolo rapporto_lavoro.ccnl_codice -> ccnl.codice (definito qui perche' la tabella ccnl nasce dopo anagrafica)
ALTER TABLE rapporto_lavoro ADD CONSTRAINT fk_rapporto_ccnl FOREIGN KEY (ccnl_codice) REFERENCES ccnl (codice);
