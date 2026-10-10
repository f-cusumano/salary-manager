--liquibase formatted sql

--changeset sam:210-ccnl-seed
--comment: CCNL principali. Valori inseriti solo se noti con certezza; il resto e' NULL con nota di verifica.
--comment: parole_chiave: frammenti (minuscoli, separati da |) usati per riconoscere il CCNL dal testo stampato.
--comment: Evitare frammenti brevi e generici (es. sigle di 3 lettere) che darebbero falsi riconoscimenti.
INSERT INTO ccnl (codice, descrizione, mensilita, periodicita_scatti_mesi, max_scatti, mese_quattordicesima, parole_chiave, note_verifica) VALUES
('COMMERCIO', 'Terziario, distribuzione e servizi (Confcommercio)', 14, 36, 10, 7,
 'commercio|terziario|confcommercio|distribuzione e servizi', NULL),
('METALMECCANICO_INDUSTRIA', 'Metalmeccanico industria (Federmeccanica-Assistal)', 13, 24, 5, NULL,
 'metalmeccanic|federmeccanica|assistal|industria metalmeccanica', NULL),
('METALMECCANICO_PMI', 'Metalmeccanico piccola e media industria (Confapi-Unionmeccanica)', 13, NULL, NULL, NULL,
 'unionmeccanica|confapi|metalmeccanica pmi|piccola e media industria metalmeccanica',
 'Verificare periodicita e numero massimo scatti di anzianita.'),
('TELECOMUNICAZIONI', 'Telecomunicazioni (Asstel)', 14, NULL, NULL, 7,
 'telecomunicazion|asstel',
 'Verificare periodicita e numero massimo scatti di anzianita e mese di pagamento della quattordicesima.'),
('STUDI_PROFESSIONALI', 'Studi professionali (Confprofessioni)', 14, NULL, NULL, 7,
 'studi professionali|confprofessioni',
 'Verificare periodicita e numero massimo scatti di anzianita e mese di pagamento della quattordicesima.'),
('CREDITO', 'Credito - aree professionali e quadri direttivi (ABI)', 13, NULL, NULL, NULL,
 'credito|aziende di credito|associazione bancaria|bancari',
 'Verificare numero mensilita, periodicita e numero massimo scatti di anzianita.'),
('CHIMICO_FARMACEUTICO_INDUSTRIA', 'Chimico-farmaceutico industria (Federchimica-Farmindustria)', 13, NULL, NULL, NULL,
 'chimic|farmaceutic|federchimica|farmindustria',
 'Verificare numero mensilita, periodicita e numero massimo scatti di anzianita.'),
('EDILIZIA_INDUSTRIA', 'Edilizia industria (ANCE)', 13, NULL, NULL, NULL,
 'edili|edilizia',
 'Per gli operai tredicesima e ferie sono accantonate presso la Cassa Edile: le regole su mensilita mancanti vanno applicate con cautela. Verificare scatti (anzianita professionale edile).'),
('TURISMO', 'Turismo - pubblici esercizi e alberghi (Federalberghi-Fipe)', 14, NULL, NULL, 7,
 'turismo|pubblici esercizi|federalberghi|fipe|alberghi',
 'Verificare periodicita e numero massimo scatti di anzianita e mese di pagamento della quattordicesima.'),
('ALTRO_13', 'Altro CCNL (13 mensilita)', 13, NULL, NULL, NULL, NULL,
 'Voce generica per CCNL non presenti in tabella: nessuna regola sugli scatti.'),
('ALTRO_14', 'Altro CCNL (14 mensilita)', 14, NULL, NULL, 7, NULL,
 'Voce generica per CCNL non presenti in tabella: quattordicesima assunta a luglio, nessuna regola sugli scatti.');
