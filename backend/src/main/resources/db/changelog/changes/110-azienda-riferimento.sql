--liquibase formatted sql

--changeset sam:110-azienda-riferimento-dati
--comment: Periodo del cedolino da cui provengono gli attributi aggiornabili dell'azienda (ragione sociale, indirizzo,
--comment: codice azienda): un cedolino piu' vecchio caricato dopo non li sovrascrive.
ALTER TABLE azienda ADD COLUMN riferimento_dati DATE;
