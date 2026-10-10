--liquibase formatted sql

--changeset sam:410-riepilogo-scatti-stima
--comment: Fonte e flag di stima degli scatti attuali (stampati sui cedolini o stimati dalle regole del CCNL)
ALTER TABLE riepilogo_rapporto ADD COLUMN scatti_fonte_attuali VARCHAR(30);
ALTER TABLE riepilogo_rapporto ADD COLUMN scatti_attuali_stimati BOOLEAN NOT NULL DEFAULT FALSE;

--changeset sam:411-riepilogo-tfr-liquidazione
--comment: Quota mensile TFR stimata e TFR liquidato da un cedolino di fine rapporto (importo lordo e netto)
ALTER TABLE riepilogo_rapporto ADD COLUMN tfr_quota_mensile_stimata BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE riepilogo_rapporto ADD COLUMN tfr_liquidato BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE riepilogo_rapporto ADD COLUMN tfr_importo_liquidato NUMERIC(15, 5);
ALTER TABLE riepilogo_rapporto ADD COLUMN tfr_netto_liquidato NUMERIC(15, 5);

--changeset sam:412-riepilogo-rateo-nota
--comment: Base usata per la stima del valore di un rateo (paga stampata o lordo mensile / divisore)
ALTER TABLE riepilogo_rateo ADD COLUMN nota VARCHAR(500);
CREATE INDEX ix_riepilogo_rateo_workspace ON riepilogo_rateo (workspace_id);

--changeset sam:413-indicatore-ordine
--comment: Posizione cronologica del cedolino nel rapporto, per restituire gli indicatori in ordine
ALTER TABLE indicatore_mensilita ADD COLUMN ordine INTEGER NOT NULL DEFAULT 0;
