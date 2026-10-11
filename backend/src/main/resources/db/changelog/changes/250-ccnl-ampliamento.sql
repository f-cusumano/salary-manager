--liquibase formatted sql

--changeset sam:250-ccnl-esistenti-verificati
--comment: Revisione dei CCNL gia' in tabella con i valori verificati sul testo contrattuale o su due fonti autorevoli
--comment: concordi (decisione 156): descrizione riconoscibile, scatti, quattordicesima, codice CNEL, parole chiave ed
--comment: escluse, note di verifica. I codici non cambiano perche' rapporto_lavoro li referenzia.
-- Fonte: https://www.cs.camcom.it//sites/default/files/uploaded/Promocosenza/Societa_trasparente/Personale/Commercio%20-%20Confcommercio%20-%20CNEL%20H011.pdf
-- Fonte: https://www.inps.it/content/dam/inps-site/pdf/allegati/2663KEY-ddl_310_658_inps_allegato.pdf
UPDATE ccnl SET
    descrizione = 'Commercio - Terziario, distribuzione e servizi (Confcommercio)',
    mensilita = 14,
    periodicita_scatti_mesi = 36,
    max_scatti = 10,
    mese_quattordicesima = 7,
    codice_cnel = 'H011',
    parole_chiave = 'commercio|terziario|confcommercio|distribuzione e servizi',
    parole_escluse = 'confesercenti|cooperative|cooperativa|cooperativo|cooperazione|artigianato|dirigenti|federdistribuzione|distribuzione moderna|dmo|turismo|pubblici esercizi|alberghi|farmacie|farmacia|pmi|p m i|cifa|confsal|conflavoro|cisal|anpit|fedarcom|federterziario|confimprese|confimprenditori',
    note_verifica = NULL
WHERE codice = 'COMMERCIO';
-- Fonte: https://www.lexplain.it/scatti-di-anzianita-contratto-metalmeccanici-industria/
-- Fonte: https://www.lavorosi.it/fileadmin/user_upload/PRASSI_2019/cnel-inps-Database-CCNL.pdf
UPDATE ccnl SET
    descrizione = 'Metalmeccanici - Industria metalmeccanica e installazione di impianti (Federmeccanica-Assistal)',
    mensilita = 13,
    periodicita_scatti_mesi = 24,
    max_scatti = 5,
    mese_quattordicesima = NULL,
    codice_cnel = 'C011',
    parole_chiave = 'metalmeccanico|metalmeccanici|metalmeccanica|metalmeccaniche|federmeccanica|assistal|industria metalmeccanica|metalmeccanici industria|metalmeccanica industria',
    parole_escluse = 'artigianato|artigiani|artigiane|artigiana|area meccanica|pmi|p m i|confapi|unionmeccanica|confimi|piccola e media industria|piccola industria|p ind|cooperative|dirigenti|orafi|argentieri|conflavoro|confsal|cifa|confimprese|federterziario',
    note_verifica = 'Valori verificati: 13 mensilità, 5 aumenti biennali. Norma transitoria: i lavoratori in forza al 16/07/1979 possono maturare fino a 12 aumenti periodici (fonte pmi.it); per questi casi rari il limite di 5 non vale. Esclusi i contratti fratelli (artigianato area meccanica, piccola industria Confapi/Confimi, contratti Confsal-Conflavoro/CIFA).'
WHERE codice = 'METALMECCANICO_INDUSTRIA';
-- Fonte: https://www.lexplain.it/scatti-di-anzianita-ccnl-metalmeccanici-pmi-confapi/
-- Fonte: https://edotto.com/articolo/ccnl-metalmeccanica-confapi-accordo-24-luglio-2025
UPDATE ccnl SET
    descrizione = 'Metalmeccanici - Piccola e media industria metalmeccanica, orafa e installazione impianti (Unionmeccanica-Confapi)',
    mensilita = 13,
    periodicita_scatti_mesi = 24,
    max_scatti = 5,
    mese_quattordicesima = NULL,
    codice_cnel = 'C018',
    parole_chiave = 'unionmeccanica|metalmeccanica pmi|metalmeccanici pmi|confapi metalmeccanica|confapi metalmeccanici|metalmeccanica confapi|metalmeccanici confapi|piccola e media industria metalmeccanica|unionmeccanica confapi',
    parole_escluse = 'artigianato|artigiani|area meccanica|dirigenti|confimi|federmeccanica|assistal|conflavoro|confsal|cifa',
    note_verifica = 'Valori verificati: 5 aumenti biennali (art. 41), 13 mensilità. Il CCNL contiene norme transitorie per i lavoratori con elevata anzianità aziendale maturata prima dell''armonizzazione (dal 1/3/2008): per loro gli importi possono differire. ''Piccola industria'' o ''p.ind'' da soli non bastano: esiste anche il CCNL Confimi.'
WHERE codice = 'METALMECCANICO_PMI';
-- Fonte: https://www.lexplain.it/tredicesima-nel-ccnl-settore-chimico-come-funziona/
-- Fonte: https://bollettinoadapt.it/scatti-di-anzianita-e-automatismi-promozionali-analisi-di-34-ccnl/
UPDATE ccnl SET
    descrizione = 'Chimici - Industria chimica, chimico-farmaceutica, fibre chimiche, abrasivi, lubrificanti e GPL (Federchimica-Farmindustria)',
    mensilita = 13,
    periodicita_scatti_mesi = NULL,
    max_scatti = NULL,
    mese_quattordicesima = NULL,
    codice_cnel = 'B011',
    parole_chiave = 'chimico|chimici|chimica|chimiche|chimico farmaceutico|chimico farmaceutica|chimica farmaceutica|farmaceutico|farmaceutici|farmaceutica|federchimica|farmindustria',
    parole_escluse = 'pmi|p m i|confapi|unionchimica|piccola e media industria|piccola industria|confimi|artigianato|artigiani|gomma|plastica|vetro|ceramica|concia|conciaria|commercio|farmacie|farmacia|federfarma|assofarm|cooperative|dirigenti|conflavoro|confsal|cifa|federterziario',
    note_verifica = 'Mensilità verificate: 13. Gli scatti di anzianità non maturano più: gli importi già maturati sono congelati in un superminimo non assorbibile, quindi nessuna regola sugli scatti. Eccezione: il settore Lubrificanti e GPL (stesso CCNL e stesso codice CNEL) prevede una quattordicesima a fine giugno, che il modello a 13 mensilità non rappresenta: per quei lavoratori una 14ª in busta non è un''anomalia.'
WHERE codice = 'CHIMICO_FARMACEUTICO_INDUSTRIA';
-- Fonte: https://ilccnl.it/contratto/ccnl/turismo-confcommercio---aziende-alberghiere
-- Fonte: https://www.lexplain.it/scatti-di-anzianita-ccnl-turismo-ristorazione-e-pubblici-esercizi/
UPDATE ccnl SET
    descrizione = 'Turismo - Alberghi e pubblici esercizi (Federalberghi-Faita, Fipe - Confcommercio)',
    mensilita = 14,
    periodicita_scatti_mesi = NULL,
    max_scatti = NULL,
    mese_quattordicesima = 7,
    codice_cnel = 'H052',
    parole_chiave = 'turismo|pubblici esercizi|federalberghi|fipe|faita|federcamping|alberghi|alberghiero|alberghiera|ristorazione collettiva e commerciale',
    parole_escluse = 'confesercenti|assoturismo|cooperative|cooperativa|termali|dirigenti|confindustria|federturismo|industria turistica|confsal|cisal|federterziario|conflavoro|commercio|terziario',
    note_verifica = 'La voce raggruppa due CCNL con scatti diversi: Turismo Federalberghi-Faita (scatti triennali) e Pubblici esercizi Fipe dal 2018 (sei scatti quadriennali). Periodicità e massimo non impostati. Quattordicesima con la retribuzione di luglio in entrambi. Il codice CNEL H052 riconosce solo il CCNL Federalberghi; il CCNL Fipe (H05Y) si riconosce dalle parole chiave. Un testo con "commercio" o "terziario" (es. "Commercio e turismo") non viene riconosciuto; "Turismo Confcommercio" sì.'
WHERE codice = 'TURISMO';
-- Fonte: https://www.lexplain.it/scatti-di-anzianita-studi-professionali-da-20-a-30-euro-mensili/
-- Fonte: https://www.lexplain.it/quattordicesima-ccnl-studi-professionali-guida
UPDATE ccnl SET
    descrizione = 'Studi professionali (Confprofessioni)',
    mensilita = 14,
    periodicita_scatti_mesi = 36,
    max_scatti = 8,
    mese_quattordicesima = NULL,
    codice_cnel = NULL,
    parole_chiave = 'studi professionali|confprofessioni',
    parole_escluse = 'dirigenti|confsal|cifa|fisapi|consilp|cipa|cisal|conflavoro|anpit|federterziario',
    note_verifica = 'Mese della quattordicesima non impostato: il CCNL la prevede in coincidenza con le ferie e non oltre il 30 giugno (di norma giugno, ma il mese non è fissato).'
WHERE codice = 'STUDI_PROFESSIONALI';
-- Fonte: https://www.lexplain.it/scatti-di-anzianita-bancari-e-nel-ccnl-credito-come-funzionano/
-- Fonte: https://www.fisac-cgil.it/wp-content/uploads/2017/06/Birindelli.pdf
UPDATE ccnl SET
    descrizione = 'Credito - Bancari: aree professionali e quadri direttivi (ABI)',
    mensilita = 13,
    periodicita_scatti_mesi = NULL,
    max_scatti = NULL,
    mese_quattordicesima = NULL,
    codice_cnel = NULL,
    parole_chiave = 'credito|aziende di credito|imprese creditizie|associazione bancaria italiana|bancari|bancario|credito abi|abi credito|quadri direttivi e aree professionali',
    parole_escluse = 'cooperativo|cooperative|federcasse|rurali|bcc|esattoriali|riscossione|banca d italia|assicurazioni|assicurativi|assicurative|assicurazione|dirigenti',
    note_verifica = 'Scatti di anzianità non impostati: il primo matura dopo 4 anni e i successivi ogni 3. Il massimo è 8 per aree professionali e QD 1°-2° livello e 7 per QD 3°-4° livello (12 per chi era in servizio al 19/12/1994). Codice CNEL (probabilmente J241) da verificare sull''archivio CNEL.'
WHERE codice = 'CREDITO';
-- Fonte: https://www.mysolution.it/globalassets/_nuovomysolution/pdf-schede-sintesi/telecomunicazioni_definitivo.pdf
-- Fonte: https://www.lexplain.it/scatti-di-anzianita-nel-contratto-telecomunicazioni/
UPDATE ccnl SET
    descrizione = 'Telecomunicazioni - Imprese esercenti servizi di telecomunicazione (Asstel)',
    mensilita = 13,
    periodicita_scatti_mesi = 24,
    max_scatti = 7,
    mese_quattordicesima = NULL,
    codice_cnel = NULL,
    parole_chiave = 'telecomunicazioni|telecomunicazione|asstel|servizi di telecomunicazione|imprese esercenti servizi di telecomunicazione',
    parole_escluse = 'dirigenti|artigianato|artigiani|installazione|installatori|impianti|metalmeccanici|metalmeccanica|cooperative|cisal|confimprese|cnai|cifa',
    note_verifica = 'Codice CNEL (probabilmente K411) da verificare sull''archivio CNEL.'
WHERE codice = 'TELECOMUNICAZIONI';
-- Fonte: https://ediltecnico.it/113460/ccnl-edilizia-industria-la-guida-completa-su-livelli-mensilita-e-ferie/
-- Fonte: https://ance.it/2026/02/appalti-pubblici-dallanac-le-indicazioni-per-la-corretta-identificazione-del-ccnl/
UPDATE ccnl SET
    descrizione = 'Edilizia - Industria (ANCE, Legacoop, Confcooperative, AGCI)',
    mensilita = 13,
    periodicita_scatti_mesi = NULL,
    max_scatti = NULL,
    mese_quattordicesima = NULL,
    codice_cnel = 'F012',
    parole_chiave = 'edile|edili|edilizia|ance|edilizia industria|edili industria|industria edile',
    parole_escluse = 'artigianato|artigiani|artigiane|artigiana|artigiano|imprese artigiane|anaepa|cna costruzioni|casartigiani|claai|pmi|p m i|piccole e medie imprese|piccola e media impresa|confapi|aniem|confimi|cooperative|cooperativa|legno|lapidei|laterizi|cemento|manufatti|dirigenti|cnai|unapi|esaarco|cisal|confsal|confinnova|confimpresa|confimprese|unimpresa|cifa|consil',
    note_verifica = 'Le regole dipendono dalla categoria. Impiegati: 14 mensilità (premio annuo entro il 30 giugno) e 5 scatti biennali. Operai: tredicesima e ferie accantonate presso la Cassa Edile e anzianità tramite APE invece degli scatti in busta. Mensilità impostata a 13 e scatti non impostati: le regole su mensilità mancanti vanno applicate con cautela. Il codice F012 copre l''unico CCNL ANCE-Cooperative, ma un cedolino con «cooperative» non viene riconosciuto qui per prudenza.'
WHERE codice = 'EDILIZIA_INDUSTRIA';

--changeset sam:251-ccnl-nuovi
--comment: Nuovi CCNL tra i piu' diffusi (decisione 156). Un valore e' impostato solo se certo; altrimenti NULL con
--comment: la spiegazione in note_verifica. Parole chiave ed escluse per parole intere, separate da |.
-- Fonte: https://www.caar.it/wp-content/uploads/2025/07/ccnl-terziario-confesercenti.pdf
-- Fonte: https://ilccnl.it/ccnl/terziario---confesercenti/commercio---confesercenti
INSERT INTO ccnl (codice, descrizione, mensilita, periodicita_scatti_mesi, max_scatti, mese_quattordicesima,
                  codice_cnel, parole_chiave, parole_escluse, note_verifica) VALUES
('COMMERCIO_CONFESERCENTI', 'Commercio - Terziario, distribuzione e servizi (Confesercenti)',
 14, 36, 10, 7, 'H012',
 'confesercenti|terziario confesercenti|commercio confesercenti',
 'turismo|pubblici esercizi|alberghi|agenzie di viaggio|agenzie viaggi|dirigenti|cooperative|cooperativa|artigianato|panificazione',
 NULL);
-- Fonte: https://www.lexplain.it/scatti-di-anzianita-ccnl-distribuzione-moderna-organizzata-triennali-e-in-cifra-fissa/
-- Fonte: https://ilccnl.it/ccnl/distribuzione-moderna-organizzata---federdistribuzione/distribuzione-moderna-organizzata---federdistribuzione
INSERT INTO ccnl (codice, descrizione, mensilita, periodicita_scatti_mesi, max_scatti, mese_quattordicesima,
                  codice_cnel, parole_chiave, parole_escluse, note_verifica) VALUES
('DISTRIBUZIONE_MODERNA_ORGANIZZATA', 'Distribuzione moderna organizzata - DMO (Federdistribuzione)',
 14, 36, 10, 7, 'H008',
 'distribuzione moderna organizzata|distribuzione moderna|federdistribuzione|commercio dmo|terziario dmo|ccnl dmo|dmo federdistribuzione',
 'dirigenti|cooperative|cooperativa|cooperazione',
 NULL);
-- Fonte: https://www.filcams.cgil.it/page/distribuzione_coop_
-- Fonte: https://ilccnl.it/ccnl/cooperative-di-consumo/cooperative-di-consumo
INSERT INTO ccnl (codice, descrizione, mensilita, periodicita_scatti_mesi, max_scatti, mese_quattordicesima,
                  codice_cnel, parole_chiave, parole_escluse, note_verifica) VALUES
('DISTRIBUZIONE_COOPERATIVA', 'Distribuzione cooperativa - Cooperative di consumo (ANCC-Coop, Confcooperative, AGCI)',
 14, NULL, 10, 6, 'H016',
 'distribuzione cooperativa|cooperative di consumo|cooperativa di consumo|cooperative consumo|coop consumo|ancc coop|commercio cooperative|commercio cooperativo|terziario cooperative',
 'dirigenti|sociali|sociale|turismo|confsal|cisal|unci|agricole|agricola|edilizia|edili',
 'Scatti: 10 triennali, ma secondo la sintesi Filcams maturano "dal 21° anno di età". Il calcolo parte dalla data di assunzione e sbaglierebbe per chi è stato assunto prima dei 21 anni, quindi la periodicità non è impostata. Va verificato sul testo integrale se la regola vale ancora per tutti o, come nel CCNL DMO (art. 188), solo per gli assunti prima del 1987.');
-- Fonte: https://www.lexplain.it/scatti-di-anzianita-farmacisti-nel-ccnl-farmacie-private/
-- Fonte: https://ilccnl.it/ccnl/farmacie-private/farmacie-urbane
INSERT INTO ccnl (codice, descrizione, mensilita, periodicita_scatti_mesi, max_scatti, mese_quattordicesima,
                  codice_cnel, parole_chiave, parole_escluse, note_verifica) VALUES
('FARMACIE_PRIVATE', 'Farmacie private - Dipendenti da farmacia privata (Federfarma)',
 14, 24, 15, 7, 'H121',
 'farmacie private|farmacia privata|farmacie privata|dipendenti da farmacia privata|federfarma',
 'municipalizzate|municipalizzata|speciali|aziende speciali|assofarm|comunali|comunale|dirigenti|industria',
 NULL);
-- Fonte: https://www.manageritalia.it/wp-content/uploads/2024/07/TU-ccnl-Dirigenti-terziario-12-aprile-2023.pdf
-- Fonte: https://ilccnl.it/ccnl/dirigenti---terziario/dirigenti---commercio
INSERT INTO ccnl (codice, descrizione, mensilita, periodicita_scatti_mesi, max_scatti, mese_quattordicesima,
                  codice_cnel, parole_chiave, parole_escluse, note_verifica) VALUES
('DIRIGENTI_TERZIARIO', 'Dirigenti commercio - Dirigenti di aziende del terziario, distribuzione e servizi (Confcommercio-Manageritalia)',
 14, NULL, NULL, 6, 'H021',
 'dirigenti commercio|dirigenti terziario|dirigenti del terziario|dirigenti aziende del terziario|dirigenti di aziende del terziario|dirigenti aziende commerciali|dirigenti commercio e servizi|manageritalia',
 'industria|industriali|federmanager|confapi|pmi|p m i|cooperative|cooperativa|cooperazione|confesercenti|turismo|alberghi|alberghiere|trasporti|spedizione|spedizioni|logistica|magazzini generali|agenzie marittime|credito|assicurazioni',
 'Scatti di anzianità aboliti dal 1° luglio 2004 (art. 9). Restano solo, in via transitoria, per i dirigenti in servizio al 30 giugno 2004, quindi periodicità e massimo non sono impostati. Dal 1° gennaio 2026 vale l''ipotesi di rinnovo del 5 novembre 2025: verificare che gli artt. 8-9 non siano cambiati.');
-- Fonte: https://www.eblart.it/wp-content/uploads/2018/06/Scheda-sintetica-CCNL-Area-meccanica-artigianato.pdf
-- Fonte: https://www.inps.it/content/dam/inps-site/pdf/allegati/2663KEY-ddl_310_658_inps_allegato.pdf
INSERT INTO ccnl (codice, descrizione, mensilita, periodicita_scatti_mesi, max_scatti, mese_quattordicesima,
                  codice_cnel, parole_chiave, parole_escluse, note_verifica) VALUES
('METALMECCANICO_ARTIGIANATO', 'Metalmeccanici - Artigianato area meccanica, metalmeccanica e installazione impianti (Confartigianato, CNA, Casartigiani, CLAAI)',
 13, 24, 5, NULL, 'C030',
 'metalmeccanici artigiani|metalmeccanica artigianato|metalmeccanici artigianato|metalmeccanico artigianato|artigiani metalmeccanici|metalmeccanica artigiana|imprese artigiane metalmeccaniche|area meccanica|area meccanica artigianato|artigiani area meccanica',
 'federmeccanica|assistal|confapi|unionmeccanica|confimi|piccola e media industria|dirigenti|cooperative|confsal|cesac|conflavoro|cifa|confimprese',
 'Due regimi di calcolo degli scatti in base alla data di maturazione del primo scatto (27/11/1997): cambia l''importo, non la periodicità biennale né il massimo di 5. Per gli operai la tredicesima è una gratifica natalizia di 173 ore. Gli apprendisti maturano gli scatti solo dal 1/1/2025 (rinnovo 2024). Lo stesso CCNL (C030) copre anche orafi e odontotecnici artigiani (anche per loro massimo 5 bienni), che però qui non vengono riconosciuti dalle parole chiave.');
-- Fonte: https://www.uila.eu/2016/02/05/ccnl-per-i-lavoratori-dellindustria-alimentare-2016-2019/65/
-- Fonte: https://www.lavorosi.it/fileadmin/user_upload/PRASSI_2019/cnel-inps-Database-CCNL.pdf
INSERT INTO ccnl (codice, descrizione, mensilita, periodicita_scatti_mesi, max_scatti, mese_quattordicesima,
                  codice_cnel, parole_chiave, parole_escluse, note_verifica) VALUES
('ALIMENTARI_INDUSTRIA', 'Alimentari - Industria alimentare (Federalimentare e associazioni di settore)',
 14, 24, 5, NULL, 'E012',
 'alimentari|alimentare|alimentaristi|industria alimentare|industrie alimentari|alimentari industria|federalimentare',
 'artigianato|artigiani|artigiane|artigiana|panificazione|panificatori|panifici|confapi|unionalimentari|pmi|p m i|piccola e media industria|piccola industria|confimi|cooperative|cooperativa|commercio|terziario|dettaglianti|ortofrutticoli|agrumari|dirigenti|conflavoro|confsal|cifa|confimprese',
 'Valori verificati sul testo contrattuale: 14 mensilità (art. 54), 5 aumenti biennali (art. 53, gli scatti maturati prima del 1/6/1995 contano nel massimo). Mese della quattordicesima non impostato: il CCNL non lo fissa; nella prassi è luglio. Per alcune piccole aziende (Assobibe, Mineracqua) è ammesso il pagamento a rate di 13ª e 14ª entro 2 mesi dalla scadenza. Escluso anche ''unionalimentari'' (CCNL piccola industria Confapi).');
-- Fonte: https://www.lexplain.it/scatti-di-anzianita-ccnl-tessile-abbigliamento-moda-industria-da-671-a-1291-euro-mensili/
-- Fonte: https://www.direzionelavoro.it/wp-content/uploads/2023/10/Tessili-industria-CNEL-D014.pdf
INSERT INTO ccnl (codice, descrizione, mensilita, periodicita_scatti_mesi, max_scatti, mese_quattordicesima,
                  codice_cnel, parole_chiave, parole_escluse, note_verifica) VALUES
('TESSILE_ABBIGLIAMENTO_MODA_INDUSTRIA', 'Tessile, abbigliamento, moda - Industria (Confindustria Moda - Federazione Tessile e Moda)',
 13, 24, 4, NULL, 'D014',
 'tessile|tessili|tessile abbigliamento|tessili abbigliamento|tessile abbigliamento moda|tessili e abbigliamento|abbigliamento moda|confindustria moda|sistema moda italia|federazione tessile e moda',
 'artigianato|artigiani|artigiane|artigiana|confartigianato|confapi|unionmoda|pmi|p m i|piccola e media industria|piccola industria|confimi|commercio|terziario|calzature|calzaturiera|calzaturieri|pelli|cuoio|occhiali|lavanderie|pulitintolavanderie|cooperative|cooperativa|dirigenti|conflavoro|confsal|cifa',
 'Valori verificati: 13 mensilità, 4 aumenti biennali (art. 46; confermato da sintesi MySolution e documento aziendale Thaler; una sola fonte, leggeinchiaro, riporta 5). In caso di passaggio di livello si mantiene l''importo maturato e si possono maturare ulteriori bienni fino al massimo del nuovo livello. Per gli operai gli scatti non incidono su cottimi e incentivi. Codice D014 confermato dal database CNEL-INPS.');
-- Fonte: https://ntpluslavoro.ilsole24ore.com/art/scatti-anzianita-ccnl-gomma-plastica-industria-AD4dKLJB
-- Fonte: https://edotto.com/articolo/gomma-plastica-rinnovo-2026-2028
INSERT INTO ccnl (codice, descrizione, mensilita, periodicita_scatti_mesi, max_scatti, mese_quattordicesima,
                  codice_cnel, parole_chiave, parole_escluse, note_verifica) VALUES
('GOMMA_PLASTICA_INDUSTRIA', 'Gomma plastica - Industria della gomma, cavi elettrici ed affini e delle materie plastiche (Federazione Gomma Plastica)',
 13, 24, 5, NULL, 'B371',
 'gomma|gomma plastica|gomma e plastica|gomma cavi elettrici|gomma e cavi elettrici|materie plastiche|gommaplastica|federazione gomma plastica',
 'artigianato|artigiani|artigiane|artigiana|confartigianato|confapi|unionchimica|unionplast|pmi|p m i|piccola e media industria|piccola industria|confimi|chimica|chimico|chimici|vetro|ceramica|commercio|terziario|cooperative|dirigenti|conflavoro|confsal|cifa|federterziario',
 'Valori verificati: 13 mensilità, massimo 5 aumenti biennali. In caso di passaggio di livello l''importo degli scatti maturati viene riproporzionato sul valore dello scatto del nuovo livello (frazioni di scatto): il numero di scatti in busta può non essere intero. Escluso il testo con ''chimica'' per non confondersi con il CCNL Confapi chimica-gomma-plastica e con i contratti artigiani.');
-- Fonte: https://www.lexplain.it/tabelle-retributive-ccnl-legno-arredamento-industria-2024-ecco-gli-aumenti-di-stipendio/
-- Fonte: https://farecontrattazione.adapt.it/wp-content/uploads/2026/09/Il-rinnovo-del-CCNL-Legno-Arredo-Industria.pdf
INSERT INTO ccnl (codice, descrizione, mensilita, periodicita_scatti_mesi, max_scatti, mese_quattordicesima,
                  codice_cnel, parole_chiave, parole_escluse, note_verifica) VALUES
('LEGNO_ARREDAMENTO_INDUSTRIA', 'Legno e arredamento - Industria del legno, sughero, mobile e arredamento e boschiva e forestale (FederlegnoArredo)',
 13, 24, 5, NULL, 'F051',
 'legno|legno arredamento|legno e arredamento|legno arredo|legno industria|legno sughero mobile|industria del legno|federlegnoarredo|federlegno|mobile e arredamento',
 'artigianato|artigiani|artigiane|artigiana|lapidei|confapi|pmi|p m i|piccola e media industria|piccola industria|confimi|edile|edili|edilizia|cemento|laterizi|idraulico forestale|commercio|terziario|cooperative|cooperativa|dirigenti|conflavoro|confsal|cifa',
 'Valori verificati: 13 mensilità, massimo 5 aumenti biennali (art. 40). Il CCNL F051 copre anche i settori boschivi e forestali, quindi "boschivi" e "forestali" non escludono il riconoscimento; resta esclusa la sistemazione "idraulico forestale" (contratto agricolo). Alcuni accordi territoriali (es. distretti del mobile di Brianza e Pesaro) prevedono una somma annua aggiuntiva simile a una quattordicesima, non prevista dal CCNL nazionale. Dal 1/1/2027 gli importi degli scatti aumentano di circa il 20%.');
-- Fonte: https://www.crs4.it/repository/documents/trasparenza/site/personale/ccnl/ccnl_dirigenti_industria_01-01-2022.pdf
-- Fonte: https://www.mysolution.it/lavoro/strumenti/sintesi-ccnl/catalogo/dirigenti-industria/maggio-2025/
INSERT INTO ccnl (codice, descrizione, mensilita, periodicita_scatti_mesi, max_scatti, mese_quattordicesima,
                  codice_cnel, parole_chiave, parole_escluse, note_verifica) VALUES
('DIRIGENTI_INDUSTRIA', 'Dirigenti industria - Dirigenti di aziende produttrici di beni e servizi (Confindustria-Federmanager)',
 13, NULL, NULL, NULL, 'V012',
 'dirigenti industria|dirigenti aziende industriali|dirigenti di aziende industriali|dirigenti industriali|dirigenti aziende produttrici di beni e servizi|dirigenti di aziende produttrici di beni e servizi|confindustria federmanager',
 'confapi|pmi|p m i|piccola e media industria|piccole e medie industrie|piccola industria|commercio|terziario|confcommercio|manageritalia|cooperative|cooperativa|credito|assicurazioni|assicurative|agricoltura|agricole|confservizi|utilitalia|cispel|autotrasporto|alberghi|turismo',
 'Mensilità: 13, ma il CCNL ammette un numero maggiore se previsto in azienda, quindi una 14ª in busta non è per forza un''anomalia. Scatti aboliti dall''accordo 24/11/2004: solo i dirigenti in servizio a quella data maturano ancora 129,11 euro per biennio, fino a 10 aumenti complessivi; il valore dipende dalla data di assunzione e resta non impostato. ''Federmanager'' da solo non basta, perché firma anche il CCNL dirigenti PMI Confapi e altri.');
-- Fonte: https://ilccnl.it/contratto/ccnl/multiservizi---servizi-integrati
-- Fonte: https://www.lexplain.it/scatti-di-anzianita-ccnl-pulizie-multiservizi-calcoli-diversi-tra-operai-e-impiegati/
INSERT INTO ccnl (codice, descrizione, mensilita, periodicita_scatti_mesi, max_scatti, mese_quattordicesima,
                  codice_cnel, parole_chiave, parole_escluse, note_verifica) VALUES
('MULTISERVIZI', 'Multiservizi - Servizi di pulizia e servizi integrati',
 14, NULL, NULL, NULL, NULL,
 'multiservizi|servizi integrati|servizi di pulizia|imprese di pulizia|pulizia e servizi integrati|pulizie multiservizi|pulizia multiservizi',
 'artigianato|artigiani|artigiane|artigiana|dirigenti|cooperative sociali|coop sociali|confsal|cisal|conflavoro|unci|unicoop|fismic|cnai|confial|federdat|usae|fenapi|ugl',
 'Scatti diversi per categoria: impiegati 8 scatti biennali, operai una sola quota fissa di anzianità dal quinto anno. Periodicità e massimo non impostati. Mese della quattordicesima non impostato: il CCNL prevede il pagamento "entro il 15 luglio", quindi con il cedolino di giugno o di luglio. Codice CNEL non impostato (K511 o K512 secondo la fonte).');
-- Fonte: https://www.legacoop.coop/wp-content/uploads/2024/03/20240307-circolare-rinnovo-CCNL-sociali_rev.1.1.pdf
-- Fonte: https://www.lexplain.it/scatti-di-anzianita-ccnl-cooperative-sociali-aumento-stipendio-ogni-due-anni/
INSERT INTO ccnl (codice, descrizione, mensilita, periodicita_scatti_mesi, max_scatti, mese_quattordicesima,
                  codice_cnel, parole_chiave, parole_escluse, note_verifica) VALUES
('COOPERATIVE_SOCIALI', 'Cooperative sociali - Settore socio-sanitario, assistenziale, educativo e di inserimento lavorativo',
 13, 24, 5, NULL, 'T151',
 'cooperative sociali|coop sociali|cooperative socio sanitarie|socio sanitario assistenziale educativo|legacoopsociali|federsolidarieta',
 'multiservizi|vigilanza|unci|unicoop|confsal|cisal|ugl|dirigenti|conflavoro|federterziario',
 'Dal 1° gennaio 2025 il CCNL prevede mezza quattordicesima, pagata con le spettanze di giugno. Il modello prevede solo mensilità intere e non gestisce un istituto che decorre da una data, quindi la voce è impostata a 13 mensilità: la mezza quattordicesima non è controllata e non entra nella RAL. Parola chiave al plurale "cooperative sociali" per non riconoscere il CCNL dal nome di un''azienda "cooperativa sociale" che applica un altro contratto.');
-- Fonte: https://www.dipendenti.it/guida/ccnl-vigilanza-privata
-- Fonte: https://ilccnl.it/ccnl/vigilanza-privata/vigilanza-privata
INSERT INTO ccnl (codice, descrizione, mensilita, periodicita_scatti_mesi, max_scatti, mese_quattordicesima,
                  codice_cnel, parole_chiave, parole_escluse, note_verifica) VALUES
('VIGILANZA_PRIVATA', 'Vigilanza privata e servizi fiduciari',
 14, NULL, NULL, NULL, NULL,
 'vigilanza privata|servizi fiduciari|istituti di vigilanza|istituti e imprese di vigilanza|guardie giurate|guardia giurata',
 'anpit|cisal|confsal|ugl|unci|federterziario|conflavoro|cnai|dirigenti',
 'Un unico CCNL con due sezioni. Vigilanza: 6 scatti triennali, quattordicesima entro il 15 luglio (fonte unica). Servizi fiduciari: regole proprie sugli scatti e quattordicesima introdotta in modo progressivo solo dal 2024, quindi per gli anni precedenti le 14 mensilità non valgono. Periodicità, massimo e mese di pagamento della quattordicesima non impostati. Codice CNEL HV17 non accettato dal riconoscitore.');
-- Fonte: https://wageindicator.org/it-it/ai/lavoro-in-italia/contratti-collettivi-nazionali/ccnl-logistica-2024-2027/
-- Fonte: https://www.inps.it/content/dam/inps-site/pdf/allegati/2663KEY-ddl_310_658_inps_allegato.pdf
INSERT INTO ccnl (codice, descrizione, mensilita, periodicita_scatti_mesi, max_scatti, mese_quattordicesima,
                  codice_cnel, parole_chiave, parole_escluse, note_verifica) VALUES
('LOGISTICA_TRASPORTO_MERCI', 'Logistica, trasporto merci e spedizione',
 14, NULL, NULL, NULL, 'I100',
 'logistica trasporto merci e spedizione|logistica trasporto merci|trasporto merci e spedizione|autotrasporto merci e logistica|autotrasporto merci|trasporto merci e logistica|trasporti e logistica|logistica e trasporti|spedizione e logistica|confetra',
 'artigianato|artigiani|artigiana|artigiane|dirigenti|conflavoro|confsal|cisal|ugl|federterziario|cnai|pmi|p m i|autoferrotranvieri|attivita ferroviarie|noleggio autobus',
 'Scatti: 5 aumenti biennali (art. 17), ma per i nuovi assunti la maturazione parte dal 4° anno (art. 4 bis) e Magazzini generali e Industria del freddo hanno regole proprie. Periodicità e massimo non impostati. Mese della quattordicesima non impostato: il CCNL fissa il pagamento entro la prima decade di luglio, quindi con il cedolino di giugno o di luglio. La sezione artigiana è esclusa dal riconoscimento.');
-- Fonte: https://ilccnl.it/contratto/ccnl/igiene-ambientale
-- Fonte: https://ecolanspa.portaletrasparenza.net/download/attachment/2469/o_1h43gfql2ookdsg1gaphnhai0h.pdf
INSERT INTO ccnl (codice, descrizione, mensilita, periodicita_scatti_mesi, max_scatti, mese_quattordicesima,
                  codice_cnel, parole_chiave, parole_escluse, note_verifica) VALUES
('IGIENE_AMBIENTALE', 'Igiene ambientale - Servizi ambientali (Utilitalia, Cisambiente, Assoambiente)',
 14, NULL, NULL, NULL, NULL,
 'igiene ambientale|servizi ambientali|assoambiente|cisambiente',
 'conflavoro|confsal|cisal|ugl|federterziario|artigianato|dirigenti|gas acqua|settore elettrico|elettrici|funerari',
 'Scatti triennali (art. 30) convertibili, su opzione del lavoratore, in previdenza complementare; numero massimo non verificato. Periodicità e massimo non impostati. Mese della quattordicesima non impostato: il CCNL la prevede tra il 15 giugno e il 15 luglio. Codice CNEL non trovato. La sigla Utilitalia non è tra le parole chiave perché firma anche i CCNL gas-acqua ed elettrico.');
-- Fonte: https://ilccnl.it/contratto/ccnl/autoferrotranvieri
-- Fonte: https://www.lexplain.it/scatti-di-anzianita-autoferrotranvieri-importi-in-base-ai-parametri/
INSERT INTO ccnl (codice, descrizione, mensilita, periodicita_scatti_mesi, max_scatti, mese_quattordicesima,
                  codice_cnel, parole_chiave, parole_escluse, note_verifica) VALUES
('AUTOFERROTRANVIERI', 'Autoferrotranvieri - Internavigatori (Mobilità/TPL)',
 14, 24, 6, 7, NULL,
 'autoferrotranvieri|autoferrotranviari|autoferrotranviario|internavigatori|mobilita tpl|trasporto pubblico locale|asstra',
 'dirigenti|attivita ferroviarie|noleggio autobus|logistica|trasporto merci|confsal|cisal|ugl',
 NULL);
-- Fonte: https://brocardi.it/contratto-collettivo-colf-badanti/art37.html
-- Fonte: https://www.fiscoetasse.com/BusinessCenter/download/Lavoro-domestico-CNEL-H501-2025.pdf
INSERT INTO ccnl (codice, descrizione, mensilita, periodicita_scatti_mesi, max_scatti, mese_quattordicesima,
                  codice_cnel, parole_chiave, parole_escluse, note_verifica) VALUES
('LAVORO_DOMESTICO', 'Lavoro domestico - Colf e badanti (Fidaldo-Domina)',
 13, 24, 7, NULL, 'H501',
 'lavoro domestico|lavoratori domestici|lavoratore domestico|collaboratori familiari|collaboratore familiare|colf|badante|badanti|assistente familiare|fidaldo|assindatcolf|federcolf',
 'federproprieta|confsal|cisal|cooperative|cooperativa|multiservizi|agenzia per il lavoro|somministrazione|dirigenti',
 NULL);
-- Fonte: https://ilccnl.it/contratto/ccnl/case-di-cura-private---personale-non-medico---aiop
-- Fonte: https://www.icsmaugeri.it/sites/default/files/documenti/CCNL-AIOP-PERS-NON-MEDICO%20DIP-2016-2018.pdf
INSERT INTO ccnl (codice, descrizione, mensilita, periodicita_scatti_mesi, max_scatti, mese_quattordicesima,
                  codice_cnel, parole_chiave, parole_escluse, note_verifica) VALUES
('SANITA_PRIVATA_AIOP', 'Sanità privata - Case di cura private, personale non medico (AIOP-ARIS)',
 13, NULL, NULL, NULL, NULL,
 'aiop|case di cura private|ospedalita privata|sanita privata personale non medico|sanita privata',
 'personale medico|medici|rsa|residenze sanitarie assistenziali|riabilitazione|uneba|don gnocchi|cooperative|cooperativa|confsal|ugl|federterziario|confial|federdat|dirigenti',
 'Il CCNL AIOP-ARIS non medici non prevede scatti periodici: l''anzianità pregressa è in una RIA ad personam (art. 56) e la progressione avviene per posizioni economiche (art. 51). Periodicità e massimo non impostati. Il CCNL AIOP RSA e centri di riabilitazione è distinto ed è escluso. "cisal" non è tra le parole escluse perché Cisal Sanità ha aderito a questo CCNL.');
-- Fonte: https://ilccnl.it/contratto/ccnl/assicurazioni---personale-amministrativo
-- Fonte: https://www.fisac-cgil.it/wp-content/uploads/2017/06/Birindelli.pdf
INSERT INTO ccnl (codice, descrizione, mensilita, periodicita_scatti_mesi, max_scatti, mese_quattordicesima,
                  codice_cnel, parole_chiave, parole_escluse, note_verifica) VALUES
('ASSICURAZIONI', 'Assicurazioni - Imprese assicuratrici, personale non dirigente (ANIA)',
 14, NULL, NULL, NULL, NULL,
 'imprese assicuratrici|imprese di assicurazione|imprese assicurative|assicurazioni|assicurazione|assicurativi|assicurative|assicurazioni ania|ania assicurazioni',
 'agenzie|agenzia|agenti|gestione libera|sna|unapass|anapa|alleanza|societa di assistenza|assistenza|broker|brokers|brokeraggio|aiba|mediatori|mediazione|credito|bancari|cooperative|dirigenti',
 'Scatti non impostati: il CCNL ANIA usa classi di anzianità nelle tabelle retributive, non scatti a cadenza fissa. Mese della quattordicesima non impostato: ilccnl.it riporta il 15 giugno dal testo contrattuale, ma manca una seconda fonte indipendente e Money.it indica luglio. Codice CNEL (probabilmente J121) da verificare sull''archivio CNEL. Esclusi i CCNL delle agenzie in gestione libera (SNA, UNAPASS, ANAPA) e dei broker (AIBA).');
-- Fonte: https://www.flaeicisl.org/contratti/ccnl-settore-elettrico-2025
-- Fonte: https://ilccnl.it/contratto/ccnl/elettrici-dal-010701
INSERT INTO ccnl (codice, descrizione, mensilita, periodicita_scatti_mesi, max_scatti, mese_quattordicesima,
                  codice_cnel, parole_chiave, parole_escluse, note_verifica) VALUES
('ELETTRICO', 'Elettrici - Settore elettrico (Elettricità Futura, Utilitalia, Enel, Terna, GSE, Sogin, Energia Libera)',
 14, NULL, NULL, 6, NULL,
 'settore elettrico|elettrici|elettrico|aziende elettriche|imprese elettriche|lavoratori addetti al settore elettrico',
 'gas|acqua|cavi elettrici|gomma|petrolio|energia e petrolio|concorrente|artigianato|artigiani|artigiane|installazione|installatori|impianti|metalmeccanici|metalmeccanica|metalmeccanico|commercio|terziario|cooperative|dirigenti',
 'Scatti non impostati. Chi è stato assunto prima del 2020 matura 5 aumenti biennali, il primo al compimento del primo biennio. Per gli assunti dal 1° gennaio 2020 gli scatti sono sostituiti da versamenti periodici al fondo pensione (art. 46), che non compaiono come scatto in busta. Codice CNEL (probabilmente K051) da verificare sull''archivio CNEL.');
-- Fonte: https://www.mysolution.it/globalassets/_nuovomysolution/pdf-schede-sintesi/gas-acqua_definitivo.pdf
-- Fonte: https://www.fiscoetasse.com/domande-e-risposte/12564-quattordicesima-in-quali-ccnl-e-prevista.html
INSERT INTO ccnl (codice, descrizione, mensilita, periodicita_scatti_mesi, max_scatti, mese_quattordicesima,
                  codice_cnel, parole_chiave, parole_escluse, note_verifica) VALUES
('GAS_ACQUA', 'Gas-acqua (Utilitalia, Anigas, Assogas, Igas, Anfida, Proxigas)',
 14, NULL, NULL, 6, NULL,
 'gas acqua|gas e acqua|acqua gas|acqua e gas|settore gas acqua',
 'elettrico|elettrici|petrolio|energia e petrolio|igiene ambientale|servizi ambientali|letturisti|artigianato|cooperative|dirigenti',
 'Scatti di anzianità non impostati: sono congelati all''importo maturato al 31/12/2015, pagato come elemento ad personam, e non ne maturano di nuovi. Codice CNEL (probabilmente K321) da verificare sull''archivio CNEL.');
-- Fonte: https://www.filcacisl.it/sindacato/wp-content/uploads/2026/01/20251222_CCNL_web.pdf
-- Fonte: https://www.cnel.it/media/docs/default-source/news-documents/14-report-ccnl-vigenti-31-12-2021.pdf
INSERT INTO ccnl (codice, descrizione, mensilita, periodicita_scatti_mesi, max_scatti, mese_quattordicesima,
                  codice_cnel, parole_chiave, parole_escluse, note_verifica) VALUES
('EDILIZIA_ARTIGIANATO', 'Edilizia - Artigianato e PMI: imprese artigiane e piccole imprese industriali dell''edilizia (ANAEPA Confartigianato, CNA Costruzioni, Casartigiani, CLAAI)',
 13, NULL, NULL, NULL, 'F015',
 'edilizia artigianato|edili artigianato|edilizia artigiana|edilizia artigiani|edili artigiani|edili artigiane|imprese artigiane edili|imprese artigiane dell edilizia|edilizia artigianato e pmi|edili artigianato e pmi|anaepa|cna costruzioni',
 'industria|ance|confapi|aniem|confimi|cooperative|cooperativa|legno|lapidei|laterizi|cemento|manufatti|dirigenti|cnai|unapi|esaarco|cisal|confsal|confinnova|confimpresa|confimprese|unimpresa|cifa|consil|ugl',
 'Le regole dipendono dalla categoria. Impiegati: 14 mensilità (premio annuo entro il 30 giugno) e 5 scatti biennali. Operai: tredicesima pagata dalla Cassa Edile e anzianità tramite APE. Mensilità impostata a 13 e scatti non impostati: le regole su mensilità mancanti vanno applicate con cautela. «Edilizia PMI» da solo non è una parola chiave perché è ambiguo con il CCNL Confapi-ANIEM (F018). Esclusi i CCNL edili artigiani e PMI firmati da altre sigle (es. Confinnova-Cisal).');
