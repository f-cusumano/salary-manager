--liquibase formatted sql

--changeset sam:230-ccnl-codice-cnel
--comment: Codice dell'archivio CNEL (es. C011) usato per il riconoscimento quando e' stampato sul cedolino.
--comment: Valorizzato solo se verificato con certezza: in fase 1 tutti NULL.
ALTER TABLE ccnl ADD COLUMN codice_cnel VARCHAR(10);
ALTER TABLE ccnl ADD CONSTRAINT uk_ccnl_codice_cnel UNIQUE (codice_cnel);

--changeset sam:231-ccnl-parole-escluse
--comment: Frasi che, se presenti nel testo stampato, escludono il CCNL (es. "artigianato" per un CCNL industriale).
ALTER TABLE ccnl ADD COLUMN parole_escluse VARCHAR(500);

--changeset sam:232-ccnl-parole-chiave-intere
--comment: Riconoscimento per parole intere (non piu' per frammenti): ogni frase, separata da |, deve comparire nel testo
--comment: normalizzato (minuscolo, senza accenti e punteggiatura) come sequenza di parole intere. Le forme flesse sono
--comment: elencate esplicitamente. Le sigle confapi/abi/ance da sole non bastano dove sono ambigue.
UPDATE ccnl SET
    parole_chiave = 'commercio|terziario|confcommercio|distribuzione e servizi',
    parole_escluse = 'confesercenti|cooperative|cooperativa|cooperazione|artigianato|dirigenti'
WHERE codice = 'COMMERCIO';
UPDATE ccnl SET
    parole_chiave = 'metalmeccanico|metalmeccanici|metalmeccanica|metalmeccaniche|federmeccanica|assistal|industria metalmeccanica',
    parole_escluse = 'artigianato|artigiani|artigiane|artigiana|pmi|confapi|unionmeccanica|confimi|piccola e media industria|piccola industria|cooperative|dirigenti|orafi|argentieri'
WHERE codice = 'METALMECCANICO_INDUSTRIA';
UPDATE ccnl SET
    parole_chiave = 'unionmeccanica|metalmeccanica pmi|metalmeccanici pmi|confapi metalmeccanica|confapi metalmeccanici|metalmeccanica confapi|metalmeccanici confapi|piccola e media industria metalmeccanica',
    parole_escluse = 'artigianato|artigiani|dirigenti'
WHERE codice = 'METALMECCANICO_PMI';
UPDATE ccnl SET
    parole_chiave = 'telecomunicazioni|asstel',
    parole_escluse = 'dirigenti'
WHERE codice = 'TELECOMUNICAZIONI';
UPDATE ccnl SET
    parole_chiave = 'studi professionali|confprofessioni',
    parole_escluse = 'dirigenti'
WHERE codice = 'STUDI_PROFESSIONALI';
UPDATE ccnl SET
    parole_chiave = 'credito|aziende di credito|associazione bancaria italiana|bancari',
    parole_escluse = 'cooperativo|cooperative|federcasse|esattoriali|assicurazioni|assicurativi|dirigenti'
WHERE codice = 'CREDITO';
UPDATE ccnl SET
    parole_chiave = 'chimico|chimici|chimica|chimiche|chimico farmaceutico|farmaceutico|farmaceutici|farmaceutica|federchimica|farmindustria',
    parole_escluse = 'pmi|confapi|unionchimica|artigianato|artigiani|gomma|plastica|vetro|ceramica|commercio|dirigenti'
WHERE codice = 'CHIMICO_FARMACEUTICO_INDUSTRIA';
UPDATE ccnl SET
    parole_chiave = 'edile|edili|edilizia|ance',
    parole_escluse = 'artigianato|artigiani|artigiane|cooperative|cooperativa|pmi|confapi|aniem|legno|lapidei|laterizi|cemento|dirigenti'
WHERE codice = 'EDILIZIA_INDUSTRIA';
UPDATE ccnl SET
    parole_chiave = 'turismo|pubblici esercizi|federalberghi|fipe|alberghi|alberghiero|alberghiera',
    parole_escluse = 'confesercenti|cooperative|cooperativa|termali|dirigenti'
WHERE codice = 'TURISMO';

--changeset sam:233-ccnl-valori-non-certi
--comment: Revisione del seed: il mese di pagamento della quattordicesima non e' noto con certezza per questi CCNL,
--comment: quindi resta NULL (con nota di verifica) invece di un valore presunto.
UPDATE ccnl SET mese_quattordicesima = NULL,
    note_verifica = 'Verificare periodicita e numero massimo scatti di anzianita e mese di pagamento della quattordicesima (non impostato).'
WHERE codice IN ('TELECOMUNICAZIONI', 'STUDI_PROFESSIONALI', 'TURISMO');
