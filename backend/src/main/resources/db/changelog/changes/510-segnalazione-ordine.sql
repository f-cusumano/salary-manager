--liquibase formatted sql

--changeset sam:510-segnalazione-ordine
--comment: Posizione della segnalazione nell'ordinamento calcolato dal motore (gravita' decrescente, poi data):
--comment: le letture usano ORDER BY ordine.
ALTER TABLE segnalazione ADD COLUMN ordine INTEGER NOT NULL DEFAULT 0;
CREATE INDEX ix_segnalazione_rapporto_ordine ON segnalazione (rapporto_id, ordine);
