--liquibase formatted sql

--changeset sam:240-ccnl-descrizioni-accentate
--comment: Descrizioni e note di verifica mostrate in UI con le lettere accentate (il seed 210 usava "mensilita",
--comment: "periodicita", "anzianita"). I changeset gia' applicati non si modificano.
UPDATE ccnl SET descrizione = 'Altro CCNL (13 mensilità)' WHERE codice = 'ALTRO_13';
UPDATE ccnl SET descrizione = 'Altro CCNL (14 mensilità)' WHERE codice = 'ALTRO_14';
UPDATE ccnl SET note_verifica = 'Verificare periodicità e numero massimo scatti di anzianità.'
WHERE codice = 'METALMECCANICO_PMI';
UPDATE ccnl SET note_verifica = 'Verificare periodicità e numero massimo scatti di anzianità e mese di pagamento della quattordicesima (non impostato).'
WHERE codice IN ('TELECOMUNICAZIONI', 'STUDI_PROFESSIONALI', 'TURISMO');
UPDATE ccnl SET note_verifica = 'Verificare numero mensilità, periodicità e numero massimo scatti di anzianità.'
WHERE codice IN ('CREDITO', 'CHIMICO_FARMACEUTICO_INDUSTRIA');
UPDATE ccnl SET note_verifica = 'Per gli operai tredicesima e ferie sono accantonate presso la Cassa Edile: le regole su mensilità mancanti vanno applicate con cautela. Verificare scatti (anzianità professionale edile).'
WHERE codice = 'EDILIZIA_INDUSTRIA';
