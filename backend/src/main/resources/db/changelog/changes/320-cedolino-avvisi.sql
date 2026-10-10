--liquibase formatted sql

--changeset sam:320-cedolino-avvisi-estrazione
--comment: Avvisi dell'estrazione (controlli semantici non superati, valori non interpretabili, dati bancari rimossi,
--comment: note del modello) separati da a capo, come campi_non_trovati. Mai scritti nei log.
ALTER TABLE cedolino ADD COLUMN avvisi_estrazione VARCHAR(4000);
