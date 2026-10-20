# Decisioni

Registro delle decisioni di progetto. Le decisioni marcate **[utente]** sono state prese con l'utente e sono
vincolanti; le altre sono default ragionevoli scelti durante lo sviluppo e possono essere riviste.

## Generali

1. **[utente] Riscrittura completa.** Del progetto precedente si conserva solo il package base `com.fcusumano.sam`
   (groupId `com.fcusumano.sam`, artifactId `salary-manager`).
2. **[utente] Stack backend.** Spring Boot 4.0.8 (ultima 4.0.x stabile) + Spring Modulith 2.0.8, Java 25, architettura
   esagonale per modulo; il dominio non dipende da Spring, jOOQ o SDK Anthropic.
3. **[utente] Persistenza.** H2 su file in `MODE=PostgreSQL`, DDL PostgreSQL-compatibili, Liquibase (formatted SQL),
   jOOQ con codegen dai changelog Liquibase.
4. **[utente] Frontend.** SPA separata React + TypeScript + Vite + Mantine + TanStack Query, solo via REST `/api/v1`
   documentate OpenAPI.
5. **Modulo REST chiamato `web`** (la specifica lo chiama `api`): contiene controller, DTO, gestione errori,
   sicurezza e configurazione springdoc. Il contratto di riferimento e' `docs/openapi.yaml`.
6. **Lingua.** Termini di dominio, commenti e messaggi in italiano; codice idiomatico Java 25 (record, sealed,
   pattern matching).

## Versioni e dipendenze

7. **Starter modulari di Boot 4** verificati su Maven Central: `spring-boot-starter-webmvc`, `-validation`,
   `-security`, `-jooq`, `-liquibase`; test: `spring-boot-starter-test`, `-webmvc-test`, `-jooq-test`,
   `-security-test`, `spring-modulith-starter-test`, `archunit-junit5` 1.5.1.
8. **H2 2.5.252** (ultima stabile) sovrascrive la 2.4.240 gestita da Boot 4.0.8 tramite la property `h2.version`:
   compatibile (build, codegen, Liquibase e test passano). Se dovesse dare problemi, rimuovere la property per tornare
   alla versione gestita da Boot.
9. **jOOQ 3.19.37 e Liquibase 5.0.3** gestiti da Boot 4.0.8. Il codegen usa `jooq-meta-extensions-liquibase` 3.19.37
   con Liquibase 5.0.3 (stessa versione del runtime) e H2 2.5.252 come dipendenze del plugin: `LiquibaseDatabase`
   funziona con `includeAll` e formatted SQL, quindi non serve l'alternativa (liquibase-maven-plugin + codegen JDBC).
10. **springdoc-openapi 3.0.3** (linea 3.0.x, compatibile con Boot 4.0; la 3.1.x e' allineata a Boot 4.1).
11. **SDK Anthropic `com.anthropic:anthropic-java` 2.69.0** (porta con se' Jackson 2, separato dal Jackson 3 di Boot 4).
12. **Spring Modulith**: solo `spring-modulith-starter-core` (niente event publication registry, niente JDBC
    per gli eventi).
13. **Maven Wrapper** 3.3.4 (tipo `only-script`) con Maven 3.9.11; presenti `mvnw` e `mvnw.cmd`.
14. **Telemetria Liquibase disattivata** (`liquibase.analytics.enabled=false` in `.mvn/jvm.config`, surefire e
    `main`): nessuna chiamata verso servizi esterni.

## Modello dati

15. **[utente] Unicita' del cedolino**: persona + azienda + anno + mese, con mese 01..14 (13 = tredicesima,
    14 = quattordicesima). Poiche' esiste un solo rapporto per coppia persona-azienda, a DB e'
    `UNIQUE (rapporto_id, anno, mese)`.
16. **[utente] Cedolini di fine rapporto**: senza mese, con progressivo `TFR01`, `TFR02`, ... in ordine cronologico
    del periodo di retribuzione stampato, rinumerati se ne arriva uno precedente. A DB: `mese` NULL,
    `progressivo_tfr`, e `periodo_fine_rapporto` (= periodo di retribuzione, valorizzato solo per FINE_RAPPORTO) con
    `UNIQUE (rapporto_id, periodo_fine_rapporto)`: e' l'unicita' "sul periodo stampato". Un `CHECK` impone la coerenza
    tra tipo, mese e progressivo. Nella rinumerazione, aggiornare i progressivi evitando collisioni temporanee con il
    vincolo unico (es. prima valori negativi/alti, poi definitivi, oppure in ordine decrescente).
17. **[utente] Duplicati e aggiuntivi**: stesso cedolino gia' presente => scartato (DUPLICATO); stessa competenza ma
    contenuto diverso (es. aggiuntivo premi/arretrati) => scartato con stato AGGIUNTIVO_NON_GESTITO e motivo.
    In aggiunta il file identico (hash SHA-256) e' riconosciuto prima dell'estrazione, senza chiamare l'IA
    (`UNIQUE (workspace_id, hash_contenuto)` su `cedolino`).
18. **[utente] Nome file PDF** `AAAA.MM_<idAzienda>_<idPersona>.pdf` o `AAAA.TFRnn_<idAzienda>_<idPersona>.pdf`
    con gli id generati dal sistema; generato da `NomeFileCedolinoStrategy` (default `PatternNomeFileCedolino`,
    pattern in `salary-manager.storage.nome-file-pattern`).
19. **[utente] Riconoscimento**: persona per codice fiscale; azienda per CF/P.IVA, poi posizione/matricola INPS,
    poi ragione sociale normalizzata. Indirizzo, codice azienda e codice dipendente sono attributi aggiornabili.
    CF e P.IVA azienda sono univoci per workspace (quando presenti).
20. **[utente] Etichette UI**: persona "Nome Cognome" (+ CF solo in caso di omonimia); azienda ragione sociale
    (+ P.IVA solo in caso di omonimia tra le aziende della persona; se manca la P.IVA si usa il codice fiscale).
21. **Un rapporto per coppia persona-azienda** (`UNIQUE (workspace_id, persona_id, azienda_id)`): una riassunzione
    presso la stessa azienda confluisce nello stesso rapporto (limite noto della fase 1).
22. **[utente] CCNL**: tabella di lookup; spesso non stampato => l'utente lo sceglie una volta per rapporto, resta
    modificabile, il cambio fa ricalcolare (`CcnlRapportoModificato`). `ccnl_scelto_da_utente` impedisce che
    l'estrazione sovrascriva la scelta. Il riconoscimento dal testo stampato restituisce un CCNL solo se univoco.
23. **Tabella `ccnl` globale** (dato di riferimento, senza `workspace_id`). Seed con i principali CCNL: valori
    inseriti solo se noti con certezza (Commercio: 14 mensilita', scatti triennali, max 10, quattordicesima a luglio;
    Metalmeccanico industria: 13 mensilita', scatti biennali, max 5); gli altri hanno periodicita'/numero scatti NULL e
    una `note_verifica`. Revisione del seed (`233-ccnl-valori-non-certi`): il mese della quattordicesima di
    Telecomunicazioni, Studi professionali e Turismo non e' noto con certezza ed e' NULL con nota. Le voci
    `ALTRO_13` / `ALTRO_14` coprono i CCNL non in tabella. Con regole scatti ignote non si stimano scatti e non si
    segnalano scatti mancanti. Riconoscimento dal testo stampato: decisioni 47-48. Elenco ampliato: decisione 156.
24. **FK `rapporto_lavoro.ccnl_codice -> ccnl.codice`** definita in `220-ccnl-vincoli.sql` perche' la tabella
    `ccnl` (2xx) e' creata dopo `rapporto_lavoro` (1xx).
25. **Date contrattuali**: data di assunzione/cessazione stampate prevalgono sulle date dedotte dai cedolini
    (`primo_cedolino` / `ultimo_cedolino` su `rapporto_lavoro`). Se due cedolini riportano dati diversi vince il
    piu' recente (`riferimento_dati`).
26. **Importi** `NUMERIC(15,5)` / `BigDecimal`, mai float; quantita' (ore/giorni) anch'esse `NUMERIC(15,5)`.
    Confidenza `NUMERIC(6,5)` (0..1). Interi come `INTEGER` (jOOQ genera `Integer`).
27. **PDF in tabella separata** `cedolino_pdf` con colonna `BYTEA` (supportata da H2 e PostgreSQL; verificato un PDF
    da 25MB). JSON di audit (`cedolino.estratto_json`, `segnalazione.evidenze`) in colonne `TEXT`.
28. **Nessun dato bancario** (IBAN, ABI/CAB, conto) in nessuna tabella ne' nello schema di estrazione.
29. **Cascade**: figlie di `cedolino` e dati derivati (`riepilogo_*`, `indicatore_mensilita`, `segnalazione`) hanno
    `ON DELETE CASCADE`; `caricamento.cedolino_id` e i riferimenti del riepilogo ai cedolini `ON DELETE SET NULL`.
    Ogni colonna FK usata da cascade/set null ha un indice (PostgreSQL non li crea in automatico).
30. **Identity** `BIGINT GENERATED BY DEFAULT AS IDENTITY` (PostgreSQL e H2); `workspace` parte da 2 perche' l'id 1
    e' inserito esplicitamente per il workspace di default.
31. **Nomi senza virgolette in jOOQ** (`RenderQuotedNames.EXPLICIT_DEFAULT_UNQUOTED`): i nomi generati sono resi
    senza quoting e funzionano identici su H2 (che porta in maiuscolo i nomi non quotati) e su PostgreSQL (che li porta
    in minuscolo). L'URL H2 **non** usa `DATABASE_TO_LOWER=TRUE`: con quell'opzione Liquibase non riconosceva la propria
    tabella `databasechangelog` gia' presente, provava a ricrearla e ogni avvio dopo il primo sullo stesso file falliva
    (regressione coperta da `RiavvioDatabaseIntegrationTest`, che applica il changelog piu' volte sullo stesso file con
    l'URL di `application.yml`). I file H2 creati con l'opzione vanno ricreati. Dialetto jOOQ configurabile
    (`SAM_JOOQ_DIALECT`, default `H2`).

## Comportamento

32. **[utente] Eliminazione di un cedolino** => ricalcolo da zero dei dati derivati del rapporto. Se era l'ultimo
    cedolino del rapporto, il rapporto viene eliminato e con lui persona e azienda rimaste senza rapporti (spec §7.9).
33. **[utente] Ricalcolo sincrono**: eventi applicativi Spring con `@EventListener` nella stessa transazione che li
    pubblica; niente event publication registry asincrono. Calcoli deterministici e idempotenti.
34. **[utente] Segnalazioni veritiere**: meglio non segnalare che segnalare il falso; ogni regola e' una classe
    (`RegolaSegnalazione`) testata singolarmente; con dati insufficienti non segnala.
35. **Mensilita' mancanti**: derivate dalle segnalazioni MENSILITA_MANCANTE / TREDICESIMA_MANCANTE /
    QUATTORDICESIMA_MANCANTE (`competenza_anno` / `competenza_mese` su `segnalazione`); la lista mensilita' dell'API
    le unisce ai cedolini presenti gia' ordinata in ordine cronologico inverso, con titolo pronto.
36. **Indicatori per mensilita'** (scatto in busta, aumento fuori scatti) calcolati dal modulo calcoli
    (`indicatore_mensilita`) perche' richiedono il confronto tra cedolini.
37. **Periodo di un rapporto e rapporto "attuale"**: attuale = senza data di cessazione e con l'ultimo cedolino piu'
    recente tra i rapporti della persona. Per i rapporti non attuali senza cessazione stampata la fine e' l'ultimo
    cedolino.
38. **Ordinamento cronologico** delle competenze: la tredicesima segue dicembre, la quattordicesima segue luglio, i
    cedolini di fine rapporto seguono il mese del loro periodo (`Competenza.ORDINE_CRONOLOGICO`).
39. **Caricamento asincrono in memoria**: i file in coda sono elaborati con thread virtuali e concorrenza limitata
    (`salary-manager.ai.concurrency`); lo stato e' su DB (`caricamento`) e la UI fa polling per lotto. I caricamenti
    rimasti IN_CODA/IN_LETTURA dopo un riavvio vanno marcati ERRORE ("elaborazione interrotta").
40. **"Conguaglio" e aggiuntivi**: i conguagli dentro un cedolino ordinario non sono un tipo a se'; un cedolino
    aggiuntivo separato nello stesso mese (non 13a/14a) e' AGGIUNTIVO_NON_GESTITO in fase 1. E' l'applicazione della
    decisione utente 17 (stessa chiave ma contenuto diverso => AGGIUNTIVO_NON_GESTITO), che prevale sul titolo
    "Conguaglio ..." citato come esempio nel par. 10 della specifica: quel titolo quindi non viene mai prodotto. Dopo la
    cessazione un cedolino separato e' un fine rapporto (TFRnn).

## Estrazione IA

41. **[utente] Modello di default `claude-opus-5-5`** (configurabile, `salary-manager.ai.model`), effort `medium`
    (default del modello, configurabile), SDK Java ufficiale 2.69.0, chiave da `ANTHROPIC_API_KEY` (mai in properties).
42. **Porta `EstrattoreCedolino`** (`cedolini.application.estrazione`): input byte del PDF, output
    `RisultatoEstrazione` = `CedolinoEstratto` (record che rispecchia lo schema JSON degli structured outputs) + JSON
    originale per audit + modello usato; errori come `EstrazioneCedolinoException` con `Motivo` (rifiuto,
    max_tokens, output non valido, documento non valido/piu' cedolini, servizio non disponibile, configurazione).
    Implementazioni selezionate da `salary-manager.ai.provider`: `anthropic` (default) e `fixture` (test/sviluppo).
    *Aggiornata dalla decisione 139*: la porta si chiama `LettoreCedolini`, la scelta `salary-manager.lettore`
    (`locale` di default, `claude`, `fixture`).
43. **Date nello schema di estrazione come stringhe ISO** (`yyyy-MM-dd`) normalizzate dal modello e validate/convertite
    in `LocalDate`/`YearMonth` in fase di normalizzazione; importi come stringhe decimali ("1234.56") convertite in
    `BigDecimal`; dati assenti come stringa vuota (dettagli nella decisione 60).

## Sicurezza, privacy, log

44. **Spring Security permit-all** in fase 1 (stateless, CSRF disattivato, CORS per `http://localhost:5173`),
    `CurrentWorkspaceProvider` che restituisce il workspace 1 (o quello di `ContestoWorkspace`, decisione 72);
    `UserDetailsServiceAutoConfiguration` esclusa (nessuna password generata). Senza login la protezione e' la
    pubblicazione su localhost, che da sola non ferma il DNS rebinding (Host e Origin coincidono, CORS non interviene):
    lo `StrictHttpFirewall` ammette solo i nomi host di `salary-manager.web.allowed-hosts` (`SAM_ALLOWED_HOSTS`,
    default `localhost,127.0.0.1,[::1],::1`) e le altre richieste ricevono 400 in Problem Details. nginx inoltra
    l'Host del browser, quindi il controllo vale anche passando dal frontend; chi pubblica su un altro nome o
    indirizzo (`SAM_BIND_ADDRESS=0.0.0.0`) lo aggiunge alla variabile.
45. **Privacy**: nessun dato personale reale nel repository; `samples/` (cedolini reali) e `data/` (DB) ignorati da
    git; fixture e test con dati inventati (es. "Mario Rossi", `RSSMRA80A01H501U`, "Alfa Software S.r.l.").
    Log senza codici fiscali, nomi o importi a livello INFO; log SQL con parametri disattivati.
46. **Errori REST** in Problem Details (RFC 9457) tramite `web.error.GestoreErrori`; nessun dettaglio interno o dato
    personale nelle risposte.

## Anagrafica e CCNL

47. **Riconoscimento del CCNL conservativo** (`ccnl.domain.RiconoscitoreCcnl`): testo normalizzato (minuscolo, senza
    accenti e punteggiatura) confrontato per **parole intere** con le frasi di `parole_chiave` (forme flesse elencate
    esplicitamente, es. "metalmeccanico|metalmeccanici|metalmeccanica"); le frasi di `parole_escluse` eliminano il
    candidato (es. "artigianato", "confapi", "dirigenti", "confesercenti" per i CCNL industriali/terziario); una
    corrispondenza contenuta in una piu' lunga di un altro CCNL non conta. Risultato solo se il candidato e' unico:
    "Metalmeccanici imp." => `METALMECCANICO_INDUSTRIA`; "Commercio e turismo" => nessuno (esclusioni tra contratti
    fratelli ed esempi aggiornati: decisione 156).
48. **Codice CNEL** (`ccnl.codice_cnel`, univoco, nullable): un codice nel testo (lettera + 3 cifre, es. "C018") che
    corrisponde a un CCNL in tabella lo riconosce da solo o se concorda con le parole chiave; in contrasto => nessun
    riconoscimento; un codice non in tabella e' ignorato. In fase 1 **nessun codice era valorizzato** (non verificati
    con certezza); dalla decisione 156 sono valorizzati quelli verificati.
49. **Changelog solo additivi**: le modifiche a tabelle e seed gia' rilasciati stanno in nuovi changeset (`110`,
    `230`-`233`, `410`-`413`, `510`) invece di modificare quelli esistenti, per non cambiarne i checksum.
50. **Codice fiscale della persona**: normalizzato maiuscolo senza spazi; il controllo formale (struttura, omocodie,
    carattere di controllo) non blocca il censimento: un codice non valido e' accettato e annotato nel log a DEBUG
    senza il valore. Rifiutati solo il codice mancante o piu' lungo di 16 caratteri.
51. **Nome e cognome** tutti maiuscoli o tutti minuscoli (come stampati sui cedolini) diventano "Iniziali Maiuscole"
    ("D'ANGELO" => "D'Angelo"); i nomi gia' in forma mista restano invariati. Una persona esistente non viene mai
    rinominata.
52. **Identificativi azienda**: CF e P.IVA senza spazi/separatori e senza prefisso "IT"; P.IVA valida solo se di 11
    caratteri, CF se al massimo 16 (altrimenti ignorati come chiave). La **matricola INPS** e' memorizzata
    normalizzata a 10 cifre: primo gruppo di esattamente 10 cifre, altrimenti le prime 10 cifre di un gruppo piu'
    lungo (INPS e INAIL concatenati), altrimenti le cifre presenti. Il chiamante passa la posizione come stampata.
53. **Matching azienda** (`anagrafica.domain.AbbinamentoAzienda`): (1) CF/P.IVA incrociati (vince l'azienda con piu'
    identificativi in comune, poi la prima registrata); (2) matricola INPS, se gli identificativi fiscali non sono in
    contrasto; (3) ragione sociale normalizzata, se ne' identificativi fiscali ne' matricola INPS sono in contrasto
    (omonimi distinti restano distinti; tra piu' omonimi compatibili vince il primo registrato). "In contrasto" =
    presenti da entrambe le parti e diversi; un CF alfanumerico (ditta individuale) e una P.IVA non sono confrontabili.
    Gli identificativi sono solo completati se mancanti (mai sostituiti), e un CF/P.IVA gia' usato da un'altra azienda
    non viene assegnato. Un'azienda con piu' matricole INPS stampate senza CF/P.IVA diventa piu' aziende: si preferisce
    non fondere aziende diverse.
54. **Ragione sociale normalizzata**: maiuscolo senza accenti, punti eliminati, altra punteggiatura come spazio, "&" =
    "E", sigle puntate spaziate ricompattate solo se formano una sigla societaria nota ("S. R. L." => "SRL"), forme
    uniformate (SRL comprende SRLS, SRLSU/"S.U.", "a socio unico", "unipersonale", "societa' a responsabilita'
    limitata"; SPA, SAS, SNC, SAPA, SS, SC, SCARL), "in liquidazione" rimosso.
55. **Attributi aggiornabili dell'azienda** con criterio di recenza: colonna `azienda.riferimento_dati`
    (changelog `110`) e componente opzionale `DatiAzienda.riferimento` (periodo di retribuzione del cedolino; il
    costruttore a 6 argomenti resta: null = dati considerati i piu' recenti). Un cedolino vecchio caricato dopo non
    sovrascrive indirizzo, ragione sociale e codice azienda.
56. **Aggiornamento del rapporto**: date, qualifica, livello e codice dipendente sovrascritti solo da un cedolino con
    riferimento uguale o piu' recente; un cedolino piu' vecchio completa solo i valori mancanti; null non cancella.
    Date incoerenti (es. riassunzione presso la stessa azienda, decisione 21): si scarta la data della fonte meno
    recente (stessa fonte: la cessazione). `sovrascriviDatiRapporto` sostituisce tutti i valori, null compresi, e
    reimposta il riferimento. Un codice CCNL riconosciuto ma non in tabella e' ignorato.
57. **CCNL scelto dall'utente**: `impostaCcnl` valida il codice con `CatalogoCcnl.trova` (404 se inesistente), marca la
    scelta e pubblica `CcnlRapportoModificato` solo se il codice cambia (confermare il CCNL gia' riconosciuto lo rende
    "scelto dall'utente" senza ricalcolo).
58. **Etichette anti-omonimia**: persone omonime (nome+cognome senza distinzione di maiuscole, accenti e spazi) in
    tutto il workspace => "Nome Cognome (CF)". Aziende con la stessa ragione sociale normalizzata **tra i rapporti
    della persona** => " (P.IVA ...)", in mancanza " (CF ...)", poi " (Pos. INPS ...)".
59. **Periodo e ordinamento dei rapporti**: oltre alla decisione 37, `fonteFine` = NON_DISPONIBILE per i rapporti
    attuali (nessuna fine). Ordine: attuali in alto, poi inizio decrescente, fine decrescente, id decrescente.
    **Eliminazione del rapporto** idempotente (rapporto gia' assente => nessun effetto), da chiamare dopo aver
    cancellato i cedolini; dopo la cancellazione si eliminano persona e azienda rimaste senza rapporti.

## Estrazione IA: dettagli implementativi

60. **Schema di output senza campi opzionali ne' unioni.** Gli structured outputs limitano a 24 i parametri opzionali
    e a 16 quelli con tipi unione (`anyOf`, `["string","null"]`) per richiesta: lo schema
    (`prompts/cedolino-schema-v1.json`) rende tutto obbligatorio e rappresenta i dati assenti come stringa vuota
    (testi, importi come stringhe decimali "1234.56", date ISO), con 0 per anno/mese non determinabili e oggetti
    sempre presenti. Il sorgente usa `$defs`/`$ref` locali; l'adapter invia la versione espansa (nessun `$ref`).
    `SchemaEstrazione.VERSIONE` ("v1") versiona insieme schema e prompt (`prompts/estrazione-cedolino-v1.md`).
61. **Pipeline di interpretazione unica** (`application.estrazione.InterpreteRispostaEstrazione`) per Claude e fixture:
    parsing, mascheramento IBAN, validazione contro lo schema (output non conforme => `OUTPUT_NON_VALIDO`),
    normalizzazione (stringa vuota => null, importi `BigDecimal` anche in formato italiano, date ISO da qualunque
    formato residuo, valori non interpretabili => null + avviso), `numeroCedoliniNelDocumento` 0 o > 1 =>
    `DOCUMENTO_NON_VALIDO` gia' nell'estrattore. Nel record: `tfr` null se il cedolino non riporta dati TFR, gli
    altri sotto-record sempre presenti; il codice CNEL stampato viene aggiunto al testo del CCNL ("... (CNEL C018)")
    per aiutarne il riconoscimento; la posizione INAIL e' estratta solo per non confonderla con quella INPS e non
    passa nel record.
62. **Controlli semantici non bloccanti** (`ValidatoreCedolinoEstratto`): anno 1990..anno prossimo, mese 1..12,
    codice fiscale (carattere di controllo, omocodie), P.IVA/CF numerico dell'azienda, competenze - trattenute
    (+ arrotondamento) = netto entro 1 euro, date contrattuali, fine rapporto successivo alla cessazione, saldo dei
    ratei entro 0,05, tredicesima/quattordicesima con la relativa voce. Ogni controllo fallito moltiplica la
    confidenza complessiva per 0,8, porta a 0,5 quella del campo coinvolto e aggiunge un avviso (mai valori).
    Avvisi e note del modello ("Nota estrazione: ...") sono in `RisultatoEstrazione.avvisi` (componente aggiunto con
    costruttore compatibile a 3 argomenti) e sono salvati con il cedolino (decisione 83), gia' mascherati dai dati
    bancari; non vanno mai nei log.
63. **Dati bancari**: il prompt vieta di estrarli e lo schema non ha campi bancari; in piu' ogni testo che contiene un
    IBAN di qualunque paese viene mascherato ("[dato bancario rimosso]") sia nel record sia nel JSON di audit
    (`estrazione.DatiBancari`). Una sequenza e' un IBAN se ha la lunghezza prevista per il paese (registro ISO 13616;
    per i paesi non elencati da 15 a 34 caratteri), anche scritta a gruppi separati da spazi, e supera la verifica
    modulo 97: codici fiscali, matricole e codici voce simili restano intatti.
64. **Richiesta a Claude**: endpoint Messages, `model` come stringa, nessun `thinking` (sempre attivo su
    claude-opus-5-5) ne' `tool_choice`, `output_config.effort` da configurazione (validato all'avvio),
    `output_config.format` json_schema, prompt di sistema con `cache_control` (lotti di cedolini riusano la cache),
    PDF come blocco `document` base64 prima del testo. Con `max-tokens` > 21333 si usa lo streaming
    (`MessageAccumulator`); default 32000 (in `application.yml` e in `AiProperties`) perche' il thinking consuma token
    in output.
65. **Fallback lato server sui rifiuti** attivo per default (`salary-manager.ai.fallback-rifiuti=true`, componente
    `AiProperties.fallbackRifiuti`): header `anthropic-beta: server-side-fallback-2026-07-01` e campo
    `"fallbacks": "default"` aggiunti come header/proprieta' aggiuntive del builder. La risposta puo' arrivare da un
    altro modello (`RisultatoEstrazione.modello` riporta quello effettivo); si usa l'ultimo blocco di testo perche' in
    streaming l'output parziale del modello che ha rifiutato precede quello del fallback. `stop_reason=refusal` resta
    un errore `RIFIUTO` (con la categoria, se presente).
66. **Errori dell'SDK**: 429/5xx/529, rete e timeout (dopo i `max-retries` dell'SDK) => `SERVIZIO_NON_DISPONIBILE`;
    401/403/404 => `CONFIGURAZIONE`; 400/413/422 => `DOCUMENTO_NON_VALIDO`; `max_tokens` => `LIMITE_TOKEN`;
    `model_context_window_exceeded` => `DOCUMENTO_NON_VALIDO`; altri stop reason => `OUTPUT_NON_VALIDO`. Il client e'
    creato al primo uso: senza `ANTHROPIC_API_KEY` l'applicazione parte e l'estrazione fallisce con `CONFIGURAZIONE`.
    Concorrenza limitata da un semaforo (`salary-manager.ai.concurrency`) anche dentro l'adapter.
67. **PDF protetti da password** (porta `LettorePdf`, adapter `adapter.pdf.PdfBoxLettorePdf`, PDFBox 3.0.8): si prova
    senza password, poi la password del lotto, le password di `salary-manager.pdf.password-candidate` (solo da
    variabile d'ambiente `SAM_PDF_PASSWORD_CANDIDATE`) e infine i codici fiscali delle persone censite in maiuscolo e
    minuscolo (letti solo se servono). Nessuna valida => `PdfNonLeggibileException` (`DOCUMENTO_NON_VALIDO`,
    causa `PROTETTO_DA_PASSWORD`) con messaggio "PDF protetto da password: riprova indicando la password" (era
    "indica la password e ricarica": con il PDF conservato si riprova senza ricaricare, decisione 148). A Claude va
    la copia decifrata (`setAllSecurityToBeRemoved`); a DB e per l'hash si usa l'originale. Le password non sono mai
    salvate ne' loggate. PDFBox e' ammesso solo negli adapter `pdf` e `fixture` (regola ArchUnit). Archivio della copia
    decifrata, password ricordate e ordine aggiornato dei tentativi: decisioni 157 e 158.
68. **Limiti dei PDF**: intestazione `%PDF-` nei primi 1024 byte, almeno una pagina, al massimo
    `salary-manager.pdf.pagine-massime` (100) pagine, PDF in base64 + 256KB di margine entro
    `salary-manager.pdf.dimensione-massima-richiesta` (32MB, cioe' PDF fino a circa 23MB). Le scansioni senza testo
    sono accettate: l'estrazione non usa mai il testo estratto localmente (Claude legge il PDF anche visivamente).
    Prima di aprire il PDF con PDFBox ogni flusso compresso zlib viene decompresso in streaming e scartato
    (`adapter.pdf.ControlloDecompressione`): oltre `salary-manager.pdf.limite-decompressione` (100MB per flusso) il
    PDF e' DANNEGGIATO, senza allocare la memoria che una "bomba" di decompressione richiederebbe (i flussi dei PDF
    cifrati non sono verificabili prima della decifratura). `OutOfMemoryError` e `StackOverflowError` durante
    l'apertura diventano anch'essi DANNEGGIATO.
69. **Estrattore a fixture**: porta estesa con il metodo default `estrai(byte[] pdf, String nomeFileOriginale)` (il
    nome non va mai al modello; per default viene ignorato). La fixture si sceglie per nome del file caricato, per
    hash SHA-256 dei PDF di prova (`fixtures/pdf/`, cartella sorella di `salary-manager.fixture-dir`, gia'
    `salary-manager.ai.fixture-dir` prima della decisione 139) o per la riga
    `Fixture: <nome>` nel testo del PDF (funziona anche sulla copia decifrata). Le fixture sono nel formato dello
    structured output e passano dalla stessa validazione. Set inventato documentato in
    `backend/src/main/resources/fixtures/README.md` (PDF protetto: password `rssmra80a01h501u`).
70. **Test live** (`@Tag("live")`, `EstrazioneCampioniLiveTest`): esclusi di default tramite la property Maven
    `sam.test.excludedGroups` (default `live`), perche' un valore scritto direttamente in `<excludedGroups>` non e'
    sovrascrivibile con `-D`. Esecuzione da `backend/`: `./mvnw test -Dgroups=live -Dsam.test.excludedGroups=none`
    con `ANTHROPIC_API_KEY` (password dei campioni protetti in `SAM_SAMPLES_PASSWORD`); senza chiave o senza campioni
    il test viene saltato. Stampa solo progressivo, esito, tipo, competenza e confidenza. Dalla decisione 139 il
    default della property e' `live,campioni` (si aggiunge la misura del lettore locale).

## Cedolini: caricamento e archivio

71. **[utente] Password dei PDF protetti per lotto**: estensione additiva dell'API,
    `CaricamentoCedolini.avvia(List<FileCaricato>, String password)` (l'`avvia(files)` esistente delega con password
    null). La password resta solo in memoria per l'elaborazione (mai nei log, nascosta nel `toString`) ed e' la prima
    provata dalla decisione 67; se apre un PDF viene ricordata cifrata (decisione 158). L'hash per i duplicati e'
    calcolato sul PDF originale caricato; a DB si salva la sua copia senza protezione se era cifrato (decisione 157).
72. **Elaborazione asincrona**: `avvia` registra i file IN_CODA in una transazione propria (`REQUIRES_NEW`, committata
    prima di avviare i thread) e ritorna. Ogni file e' elaborato su un thread virtuale; le estrazioni contemporanee sono
    limitate da un semaforo (`salary-manager.ai.concurrency`). File identici (stesso hash) dello stesso workspace sono
    elaborati uno dopo l'altro: il secondo risulta DUPLICATO senza una seconda chiamata all'IA (controllo dell'hash
    prima di IN_LETTURA). Ogni cambio di stato del caricamento e' committato subito (polling). Il workspace e'
    catturato in `avvia` e l'elaborazione di ogni file gira dentro `shared.ContestoWorkspace` (uno `ScopedValue` di
    Java 25): `CurrentWorkspaceProvider` restituisce per primo il workspace del contesto, cosi' censimento, controlli,
    ricalcoli sincroni e codici fiscali provati come password lavorano tutti nel workspace del lotto anche sui thread
    dell'elaborazione (che non hanno una richiesta HTTP). Anche la transazione serializzata apre lo stesso contesto.
73. **Persistenza serializzata per workspace**: censimento, controlli di unicita', salvataggio, rinumerazione TFR,
    estremi del rapporto, evento `CedolinoAcquisito` (ricalcoli sincroni) e stato ACQUISITO avvengono in un'unica
    transazione `REQUIRES_NEW` eseguita sotto un lock per workspace preso prima dell'inizio e rilasciato dopo il commit:
    niente doppioni di persona/azienda quando piu' file della stessa persona nuova arrivano nello stesso lotto. Anche
    `ArchivioCedolini.elimina` usa lo stesso lock (transazione propria). L'hash e' ricontrollato dentro il lock.
74. **Scarti senza effetti collaterali**: un cedolino DUPLICATO o AGGIUNTIVO_NON_GESTITO annulla la transazione di
    persistenza (nessuna persona/azienda/rapporto creati per lui); lo stato e' poi registrato a parte, con il riferimento
    al cedolino esistente (`cedolinoEsistenteId`) quando individuato. Il tipo AGGIUNTIVA dichiarato dall'estrazione e'
    sempre AGGIUNTIVO_NON_GESTITO (con riferimento all'ordinaria dello stesso mese se presente).
75. **Duplicato o aggiuntivo per chiave**: stessa competenza e stessi netto, lordo, totale competenze e totale
    trattenute (confronto numerico, null = null) => DUPLICATO; altrimenti AGGIUNTIVO_NON_GESTITO. Il lordo non stampato
    vale il totale competenze (anche a DB).
76. **Competenza** (`cedolini.domain.DeterminazioneCompetenza`): FINE_RAPPORTO dichiarato => cedolino di fine
    rapporto sul periodo stampato; ORDINARIA => mensilita' del periodo, TREDICESIMA => mese 13 e QUATTORDICESIMA =>
    14 dell'anno del periodo; ciascuna delle tre diventa fine rapporto se il periodo e' successivo al mese di
    cessazione (stampata sul cedolino o gia' nota per il rapporto), salvo che il cedolino stampi un'assunzione
    successiva alla cessazione (riassunzione). Il mese di cessazione resta una mensilita'. Il tipo dichiarato
    dall'estrazione e' salvato in `cedolino.tipo_dichiarato` (changelog `310`; per le righe esistenti ricavato dal
    tipo e dal JSON di estrazione), cosi' la classificazione si puo' sempre ricalcolare.
77. **Classificazione deterministica** (`cedolini.domain.ClassificazioneRapporto`, attuazione delle decisioni 16 e
    33): dopo ogni acquisizione, eliminazione o aggiornamento del rapporto tutti i cedolini del rapporto vengono
    riclassificati rispetto alla cessazione attuale. Le mensilita' successive alla cessazione diventano fine rapporto e
    tornano mensilita' quando la cessazione sparisce o si sposta (per esempio eliminando il cedolino che la stampava);
    i progressivi TFR01, TFR02, ... seguono l'ordine cronologico del periodo stampato; i nomi file sono ricalcolati.
    Lo stato dipende quindi solo dai cedolini presenti, non dall'ordine di caricamento o di eliminazione. Un cedolino
    cambia classificazione solo se la nuova chiave (mese oppure periodo di fine rapporto) e' libera: altrimenti resta
    com'e' (conflitto da risolvere a mano, mai una sovrascrittura). Il cedolino in acquisizione non cambia
    classificazione (la sua chiave e' gia' stata verificata) ma riceve il progressivo definitivo.
78. **Riassegnazione in due passi** (attuazione della decisione 16): prima tipo, competenza, progressivi temporanei
    (1000 + n, oltre il massimo ammesso 49) e nomi file temporanei, poi progressivi e nomi definitivi; le assegnazioni
    sono applicate nell'ordine calcolato dal dominio (ciascuna verso una chiave gia' libera): nessuna collisione
    transitoria con `uk_cedolino_mese`, `uk_cedolino_tfr`, `uk_cedolino_periodo_tfr` e `uk_cedolino_nome_file`.
79. **Estremi del rapporto** (`primo_cedolino` / `ultimo_cedolino`): periodi minimo e massimo delle mensilita'
    ordinarie; se il rapporto ha solo tredicesime/quattordicesime/fine rapporto si usano tutti i cedolini.
80. **Eliminazione**: dopo la cancellazione i dati contrattuali del rapporto sono ricostruiti dai cedolini rimasti
    (per ogni dato il valore del cedolino piu' recente che lo stampa, CCNL ri-riconosciuto dal testo stampato se non
    scelto dall'utente) con `CensimentoAnagrafica.sovrascriviDatiRapporto`; poi riclassificazione dei cedolini rimasti
    rispetto alla cessazione ricostruita (decisione 77), aggiornamento degli estremi ed evento `CedolinoEliminato`. Se non restano cedolini: evento e poi `eliminaRapporto` (rapporto e orfani, decisione 32).
81. **Messaggi e log dei caricamenti**: i messaggi di `EstrazioneCedolinoException` (gia' pensati per l'utente) sono
    riportati tali e quali; dati indispensabili mancanti (periodo, codice fiscale, ragione sociale) o rifiutati
    dall'anagrafica => "Cedolino non acquisibile: ..."; ogni altro errore (compreso un errore nei ricalcoli sincroni,
    che annulla l'acquisizione) => messaggio generico. Nei log solo id, motivo e tipo di eccezione (mai messaggi
    tecnici, che potrebbero contenere valori estratti). Anche un `Error` (per esempio `OutOfMemoryError` o
    `StackOverflowError` su un PDF malevolo) porta il caricamento in ERRORE con il messaggio generico e non blocca il
    lotto; si rilanciano solo gli altri `VirtualMachineError`.
82. **Riavvio**: i caricamenti IN_CODA/IN_LETTURA vanno in ERRORE ("Elaborazione interrotta dal riavvio
    dell'applicazione: ricarica il file") in `SmartInitializingSingleton`, cioe' prima che il server web accetti
    richieste, con il motivo `INTERROTTO` (decisione 143; i file gia' archiviati diventano DUPLICATO e quelli con il
    PDF conservato hanno un messaggio che propone nuova lettura e inserimento a mano). Alla chiusura l'executor viene
    fermato (attesa massima 30 secondi).
83. **Campi non trovati e avvisi dell'estrazione** salvati in `cedolino.campi_non_trovati` e
    `cedolino.avvisi_estrazione` (changelog `320`), separati da a capo e troncati a riga intera entro 4000 caratteri;
    esposti nel dettaglio del cedolino (`campiNonTrovati`, `avvisiEstrazione`).

## Calcoli (riepilogo del rapporto)

84. **Logica nel dominio puro** (`calcoli.domain`, una classe per regola: `CalcoloAnzianita`, `CalcoloScatti`,
    `CalcoloRetribuzione`, `CalcoloBuoniPasto`, `CalcoloRatei`, `CalcoloTfr`, `CalcoloIndicatori`, orchestrate da
    `CalcolatoreRiepilogo`): input = cedolini normalizzati del rapporto + date contrattuali + CCNL, ordinati per
    competenza (indipendente dall'ordine di arrivo); parametri da `salary-manager.ferie|tfr|calcoli`. Ricalcolo sempre
    da zero, sostituendo riepilogo, saldi e indicatori del rapporto (stesso input => stesso riepilogo, a parte
    `calcolatoIl`). Senza rapporto o senza cedolini i dati derivati vengono rimossi. Date contrattuali: quelle del
    rapporto (anagrafica); se assenti, le ultime stampate sui cedolini.
85. **Anzianita'**: mesi inclusi, estremi compresi; inizio = mese di assunzione (fonte DATA_CONTRATTUALE) o primo
    cedolino ordinario (CEDOLINI); fine = mese di cessazione o ultimo cedolino ordinario. Tredicesime,
    quattordicesime e fine rapporto non spostano gli estremi. Fine prima dell'inizio (dati incoerenti) => 0.
86. **Scatti**: attuali = numero stampato, se viene dall'ultima mensilita' ordinaria o dal mese finale
    dell'anzianita'. Se e' stampato solo su un cedolino precedente (layout cambiato): con regole CCNL note, numero
    stampato + scatti maturati da allora secondo il CCNL (fonte CCNL, `attualiStimati = true`, mai oltre il
    massimo); senza regole, il numero stampato marcato come stima. Nessun numero stampato: stima dal CCNL (solo con
    periodicita' e massimo noti, `attualiStimati = true`, fonte CCNL, mai oltre il massimo); altrimenti null.
    Decorrenza dello scatto n = primo giorno del mese successivo al compimento di n periodi dall'assunzione (senza
    assunzione: dal primo cedolino ordinario). Prossimo scatto: data stampata sull'ultimo cedolino che la riporta, solo
    se successiva al mese finale (una data gia' trascorsa e' ignorata); altrimenti decorrenza dello scatto successivo
    agli attuali, solo se gli attuali sono pari a quelli maturabili dall'inizio del rapporto secondo le regole (non lo
    sono, per esempio, con anzianita' riconosciuta da un precedente datore: nessuna previsione); mai oltre il massimo;
    nessun prossimo scatto oltre la data di cessazione.
87. **RAL**: lordo mensile contrattuale dagli elementi fissi (totale stampato > somma minimo/retribuzione base +
    contingenza + superminimo + scatti + altri fissi > retribuzione base), mai dalle voci del mese (straordinari,
    variabili, rimborsi, buoni pasto esclusi per costruzione) x mensilita' del CCNL. Riferimento: ultima mensilita'
    ordinaria; se non stampa gli elementi fissi, la piu' recente che li stampa con nota. CCNL non indicato: RAL
    stimata con nota "CCNL non indicato", su 14 mensilita' se i cedolini documentano una quattordicesima (cedolino di
    quattordicesima o cedolino che la paga, anche come ratei), altrimenti su 13
    (`salary-manager.calcoli.mensilita-senza-ccnl`). Senza elementi fissi la RAL e' null con nota (nessuna
    ricostruzione dalle voci).
88. **Buoni pasto: SI** se almeno uno degli ultimi 3 cedolini ordinari (`cedolini-buoni-pasto`) ha
    `buoniPasto.presenti` o una voce "buoni/buono pasto", "ticket restaurant/pasto".
89. **Ratei**: saldi (per tipo e unita', primo per coppia) dell'ultimo cedolino che riporta almeno un saldo; i saldi
    di cedolini precedenti non vengono mescolati. Valore stimato (sempre stima, al centesimo) = giorni x paga
    giornaliera / ore x paga oraria stampate (cedolino dei ratei, poi ultima ordinaria con elementi fissi), altrimenti
    lordo mensile / divisore stampato o configurato (26 / 173); giorni senza lordo ma con paga oraria: ore giornaliere
    configurate x paga oraria. Ogni saldo ha una nota con la base usata (colonna `riepilogo_rateo.nota`).
90. **TFR**: dai dati dell'anno dell'ultimo cedolino con dati TFR, composti da tutti i cedolini di quell'anno (una
    tredicesima separata o un fine rapporto senza liquidazione che stampano solo una parte dei dati non nascondono
    gli altri): fondo 31/12 AP = ultimo valore stampato nell'anno; quota anno = massimo valore stampato (progressiva,
    la tredicesima la incrementa); rivalutazione e imposta = quelle dell'ordinaria di dicembre se ne stampa la
    rivalutazione, altrimenti l'ultimo valore stampato nell'anno. In azienda = fondo 31/12 AP + rivalutazione -
    imposta sostitutiva + quota anno - quote a fondi - quote in busta - anticipi (dell'anno). Fondo non stampato da
    nessun cedolino dell'anno: 0 solo se l'assunzione e' nell'anno, altrimenti totale non determinabile (null con
    nota). Quota anno assente: somma delle quote mensili
    dell'anno (stima). Imposta assente con rivalutazione: 17% configurato (stima). Rivalutazione non stampata: 0
    (le paghe la calcolano a fine anno o alla liquidazione). Fondi/busta/anticipi trattati come progressivi annui
    (ultimo valore); se la serie dell'anno cala, o per fondi/busta se il valore coincide con la quota del mese
    mentre la quota dell'anno e' maggiore, sono importi del mese e si sommano (stima). Totale accumulato = in
    azienda + quote ai fondi degli anni coperti dai cedolini (senza rendimenti del fondo; anni senza dati dichiarati
    in nota). Stima netto (solo sulla quota in azienda) = (in azienda - rivalutazione netta dell'anno) x (1 -
    `aliquota-stimata`) + rivalutazione netta dell'anno: la rivalutazione gia' assoggettata a imposta sostitutiva non
    e' tassata di nuovo (art. 19 TUIR); quelle degli anni precedenti, comprese nel fondo, sono trattate come
    imponibili (detto in nota). Quota mensile:
    stampata sull'ultima ordinaria con dati TFR, altrimenti retribuzione utile / 13,5 (`quotaMensileStimata`).
    Accumulato negativo => dati incoerenti, totale null con nota.
91. **TFR liquidato**: importo liquidato positivo su un cedolino di fine rapporto (o sull'ordinario del mese di
    cessazione o successivo) => `liquidato`, accumulato 0, importo lordo (somma) e netto (lordo - ritenuta, solo se
    tutte le ritenute sono stampate), nessuna stima del netto, nota con il mese.
92. **Indicatori per mensilita'** (attuazione della decisione 36; solo ordinarie, rispetto alla precedente ordinaria;
    tolleranza 0,01 EUR): scatto in busta se aumenta il numero stampato o l'importo scatti (con retribuzione scomposta
    su entrambi la riga scatti assente vale 0); aumento fuori scatti se (lordo - scatti) cresce oltre la tolleranza.
    Il riassorbimento del superminimo a parita' di totale non e' un aumento. Se l'importo scatti non e' confrontabile
    (stampato su uno solo dei due, o su nessuno mentre il numero aumenta) l'aumento non viene segnalato (meglio non
    segnalare). Tredicesime, quattordicesime e fine rapporto: indicatori falsi, lordo contrattuale valorizzato se
    stampato. Gli indicatori sono salvati con la posizione cronologica (`indicatore_mensilita.ordine`).
93. **Campi aggiunti al riepilogo** (changelog `410-calcoli-stime.sql`): `Scatti.fonteAttuali/attualiStimati`,
    `Tfr.quotaMensileStimata/liquidato/importoLiquidato/nettoLiquidato`, `SaldoRateo.nota`; parametri
    `salary-manager.calcoli.*` (`CalcoliProperties`). Le note sono troncate alla lunghezza delle colonne.
94. **TFR "ad oggi" e parametri TFR**: `salary-manager.tfr` contiene aliquota stimata, imposta sostitutiva sulla
    rivalutazione, divisore della quota, `tasso-rivalutazione-default` (3% annuo indicativo: 1,5% + 75% di
    un'inflazione del 2%) e `mesi-massimi-proiezione` (12). Il riepilogo salvato resta fermo all'ultimo cedolino con
    dati TFR (deterministico e idempotente, decisione 33); in lettura, solo per il rapporto attuale e in corso,
    `calcoli.domain.ProiezioneTfr` aggiunge i mesi interamente trascorsi da allora (fino al mese precedente quello
    corrente): quota mensile x mesi + rivalutazione pro rata della quota in azienda al tasso indicativo, al netto
    dell'imposta sostitutiva; la stima del netto cresce delle quote tassate all'aliquota stimata e della rivalutazione
    netta. Il risultato e' marcato stimato con la nota "Stima ad oggi: N mesi proiettati dall'ultimo cedolino ...".
    Nessuna proiezione con TFR liquidato o non determinabile, senza quota mensile, con quote a fondi o in busta (la
    ripartizione futura non e' nota) o con cedolini piu' vecchi del massimo configurato: il valore resta quello del
    mese di riferimento indicato. La rivalutazione non stampata nei dati dei cedolini vale 0 (decisione 90); il
    contributo aggiuntivo non entra in nessun calcolo; la tolleranza del controllo di coerenza del fondo e'
    `salary-manager.segnalazioni.tolleranza-tfr` (decisione 98).

## Segnalazioni

95. **Motore e regole**: ogni regola e' una classe del dominio puro (`segnalazioni.domain.Regola*`) registrata come
    bean in `segnalazioni.config.SegnalazioniConfiguration`; `MotoreSegnalazioni` le valuta tutte da zero, elimina i
    duplicati (stesso tipo + cedolino + competenza: una segnalazione per anomalia) e ordina per gravita' decrescente,
    poi in ordine cronologico (competenza o cedolino di riferimento; quelle sull'intero rapporto in coda), tipo e
    titolo. L'ordine e' salvato nella colonna `segnalazione.ordine` (changelog `510`). Una regola che solleva
    un'eccezione viene saltata, senza far fallire l'acquisizione del cedolino: nel log a livello ERROR solo il codice
    della regola e il tipo di eccezione (il messaggio puo' contenere date o importi), il dettaglio solo a DEBUG. Nessuna segnalazione per un rapporto senza cedolini.
96. **Competenza sulle segnalazioni** valorizzata solo per MENSILITA_MANCANTE / TREDICESIMA_MANCANTE /
    QUATTORDICESIMA_MANCANTE (alimentano `mensilitaMancanti()`, decisione 35); le altre puntano al cedolino con
    `cedolinoId`. I ratei di tredicesima non liquidati alla cessazione non hanno competenza (non e' attesa una
    mensilita' separata).
97. **Testi**: titolo breve, descrizione in italiano con importi "1.234,56 €", mesi "Gennaio 2024", date
    "15 Giugno 2024", competenze "Tredicesima 2023" / "Fine rapporto 1 – Luglio 2024"; evidenze con cedolino,
    competenza e valore formattato, salvate come JSON (`{"tipo":"MENSILITA","anno":..,"mese":..}` oppure
    `{"tipo":"FINE_RAPPORTO","progressivo":..,"periodo":"AAAA-MM"}` per la competenza).
98. **Parametri** in `salary-manager.segnalazioni` (`SegnalazioniProperties`): `tolleranza-tfr` (5,00 €: scostamento
    massimo nel controllo di coerenza del fondo TFR), `tolleranza-importi` (1,00 €: calo minimo per fondo TFR, importo
    scatti e progressivi), `mesi-attesa-fine-rapporto` (2), `ccnl-tredicesima-cassa-edile` (EDILIZIA_INDUSTRIA, EDILIZIA_ARTIGIANATO: decisione 156).
99. **Mensilita' mancanti**: tra il primo e l'ultimo cedolino ordinario (mai prima: i cedolini precedenti possono non
    essere stati caricati), estese fino al mese di cessazione se successivo (mai oltre il mese precedente a oggi). Un
    mese coperto da un cedolino di fine rapporto o compreso tra una cessazione e una riassunzione stampate non e'
    mancante.
100. **Tredicesima/quattordicesima mancanti**: oltre alle condizioni della specifica si richiede che il cedolino in cui
     sarebbero state pagate sia caricato (dicembre per la tredicesima; per la quattordicesima il mese M del CCNL o,
     se il CCNL ha `note_verifica` e quindi M non e' certo, tutte le mensilita' dell'anno e un cedolino successivo a
     dicembre): altrimenti potrebbero trovarsi proprio nel cedolino mancante, gia' segnalato. Servono almeno 15 giorni
     di lavoro nel periodo di maturazione (assunzioni/cessazioni a ridosso). La tredicesima pagata nel cedolino di
     gennaio successivo e' accettata. Per i CCNL con tredicesima degli operai in Cassa Edile la regola non si applica
     salvo qualifica di impiegato o quadro. Senza data di assunzione, se il primo cedolino ordinario e' di dicembre
     l'anno non si valuta per la tredicesima (il primo mese potrebbe essere parziale, come gia' per la
     quattordicesima). La quattordicesima di un anno non si segnala se un cedolino del periodo di maturazione stampa un
     CCNL diverso da quello del cedolino piu' recente (cambio di contratto documentato: il CCNL attuale potrebbe non
     valere per quell'anno), come gia' per livello e scatti.
101. **Fine rapporto mancante**: non segnalato se dopo il mese di cessazione ci sono mensilita' ordinarie (rapporto
     proseguito/riassunzione) o se il TFR risulta interamente versato a un fondo pensione.
102. **Fondo TFR**: il fondo al 31/12/Y-1 e' quello stampato sull'ultimo cedolino dell'anno Y che lo riporta (escluso il
     fine rapporto). FONDO_TFR_IN_CALO richiede tutte le mensilita' ordinarie dell'anno (un mese mancante potrebbe
     nascondere un anticipo). TFR_INCOERENTE richiede il cedolino di dicembre; quota dell'anno = progressivo massimo
     stampato nell'anno (la tredicesima separata puo' essere elaborata prima o dopo dicembre); rivalutazione e imposta
     dal cedolino di dicembre; anticipi e quote a fondi letti come progressivi solo se l'interpretazione e' univoca (un
     solo valore, oppure valori strettamente crescenti su tutti i mesi dal primo; per gli anticipi anche valori
     costanti = un solo anticipo), altrimenti nessuna segnalazione. Se il fondo stampato nell'anno successivo coincide
     con il precedente (fondo non ancora aggiornato) o il calo e' gia' segnalato da FONDO_TFR_IN_CALO, TFR_INCOERENTE
     non segnala. Esclusi gli anni con liquidazioni, fine rapporto, cessazione o TFR in busta (QuIR).
103. **Progressivi annui**: confrontati solo tra mensilita' ordinarie e cedolini di fine rapporto dello stesso anno
     (la tredicesima separata e' spesso elaborata tra novembre e dicembre e il confronto cronologico darebbe falsi
     cali). Il calo di imponibile IRPEF e IRPEF trattenuta non si segnala su dicembre, mese di cessazione, fine
     rapporto o cedolini con voci di conguaglio/730/rimborso IRPEF; l'imponibile previdenziale si controlla sempre.
104. **Livello in calo**: verso della scala certo solo per COMMERCIO (7 -> 1 -> Quadro, numero piu' basso = livello piu'
     alto) e METALMECCANICO_INDUSTRIA (numerica storica 1..8 con "S" = super, numero piu' alto = livello piu' alto;
     per lettere dal 2021 D1 < D2 < C1 < C2 < C3 < B1 < B2 < B3 < A1; le due classificazioni non si confrontano). Per
     gli altri CCNL o testi non riconosciuti nessuna segnalazione.
105. **Scatti**: SCATTI_IN_CALO richiede lo stesso livello stampato su entrambi i cedolini e nessun cambio del CCNL
     stampato; il calo dell'importo non si segnala se cala anche il minimo o la retribuzione base (riduzione generale,
     es. part-time). SCATTO_NON_IN_BUSTA confronta la mensilita' precedente al mese di D con la prima dal mese
     successivo; non segnala se un cedolino intermedio stampa una data di prossimo scatto posteriore (scatto rinviato),
     se il massimo del CCNL era raggiunto o se gli scatti calano (lo dice SCATTI_IN_CALO).

## Frontend

106. **Versioni**: TypeScript 5.9.3 e non la 7.x (typescript-eslint 8.71 supporta < 6.1 e openapi-typescript 7.13
     dichiara ^5.x); jsdom 29.1.1 perche' la 30 richiede Node >= 22.22.2.
107. **Client tipizzato**: openapi-typescript genera `src/api/schema.d.ts` da `docs/openapi.yaml` (versionato,
     `npm run gen:api`), openapi-fetch lo usa; il client e' iniettabile via context, cosi' mock e test usano lo stesso
     codice.
108. **Mock senza MSW**: backend simulato in memoria (`src/mocks`) collegato come `fetch` di openapi-fetch, attivo solo
     con `--mode mock` (`VITE_USE_MOCKS` in `.env.mock`, versionato con una negazione nel `.gitignore` del frontend).
     L'import e' dinamico e condizionato: la build di produzione non contiene mock e non serve un service worker.
109. **Caricamento**: password facoltativa per lotto inviata come campo multipart `password` (decisione 71). Stato del
     lotto in un hook a livello di App, cosi' il polling (1,5 s, fino al completamento) prosegue a modale chiusa. Al
     termine si invalidano persone, rapporti e dettagli e, se nessuna persona era selezionata, si seleziona quella del
     primo ACQUISITO (cedolino -> rapporto -> persona).
110. **Selezione**: persona selezionata ricordata in localStorage (solo comodita', protetta da try/catch) e considerata
     valida solo se e' ancora tra le persone restituite dal backend. Rapporto aperto di default: quello con
     `attuale = true`, altrimenti il primo dell'elenco; un click su quello aperto lo chiude.
111. **PDF**: download via fetch + blob con il nome da `Content-Disposition`.
112. **CCNL**: il cambio di un CCNL gia' impostato chiede conferma (ricalcola tutto); la prima scelta di un CCNL da
     indicare no.
113. **Stime**: valore ferie, stima netto TFR, RAL o TFR stimati e prossimo scatto da CCNL hanno un badge "stima" con
     tooltip, aperto anche al focus da tastiera.
114. **Testi e formati**: testi UI in `src/i18n/it.ts`, predisposti per altre lingue (spec §9). Importi con `Intl`
     it-IT e `useGrouping: 'always'`, altrimenti it-IT stamperebbe "1234,56 €".
115. **Logo** generato (busta paga blu con moneta in euro, PNG 128x128 trasparente) in `frontend/public/logo.png`,
     sostituibile a parita' di nome, formato e dimensione.

## Esecuzione con Docker

116. **Docker Compose** con due servizi: `backend` (immagine Spring Boot) e `frontend` (nginx che serve la SPA e
     inoltra `/api`, `/v3/api-docs` e Swagger UI al backend: stessa origine, niente CORS). Database H2 nel volume
     nominato `sam-data` montato su `/data`. Porte pubblicate solo su `127.0.0.1` (`SAM_BIND_ADDRESS` per cambiarlo),
     perche' in fase 1 non c'e' login. `ANTHROPIC_API_KEY` solo dall'ambiente o da `.env` (ignorato da git; modello in
     `.env.example`).
117. **Healthcheck** del backend su `GET /api/v1/ccnl` (endpoint leggero del contratto che richiede applicazione
     avviata e database raggiungibile), senza aggiungere Spring Boot Actuator; il frontend parte quando il backend e'
     in salute (`depends_on: condition: service_healthy`).
118. **Immagine backend**: build con `eclipse-temurin:25-jdk` e Maven Wrapper (repository Maven in cache BuildKit),
     runtime `eclipse-temurin:25-jre` con il jar estratto a strati (`-Djarmode=tools extract --layers`), utente non
     root (uid 10001), `TZ=Europe/Rome`, opzioni JVM in `JAVA_OPTS`. I test non girano nella build dell'immagine
     (`-Dmaven.test.skip=true`, sovrascrivibile con l'argomento `MAVEN_BUILD_ARGS`): si eseguono con `./mvnw -B verify`.
119. **Immagine frontend**: build con `node:22-alpine`, runtime `nginx:stable-alpine`. `nginx.conf` e' un template
     (variabile `SAM_BACKEND_URL`, default `http://backend:8080`); `client_max_body_size 500m` come
     `spring.servlet.multipart.max-request-size`; cache immutabile di un anno per `assets/` (nomi con hash), `no-cache`
     per `index.html` e i file di `public/` (un logo sostituito si vede subito); gzip anche sulle risposte JSON.
120. **Modalita' demo**: `SAM_AI_PROVIDER=fixture` con un nome di progetto Compose diverso
     (`docker compose -p salary-manager-demo ...`), cosi' il volume dei dati demo e' separato da quello reale. Dalla
     decisione 139 la variabile e' `SAM_LETTORE=fixture`.
121. **Password dei PDF in Compose** passate al container come `SAM_PDF_PASSWORD_CANDIDATE`, letta dal segnaposto
     `${SAM_PDF_PASSWORD_CANDIDATE:}` di `salary-manager.pdf.password-candidate` in `application.yml` (come fuori da
     Compose). Nel binding "relaxed" di Spring Boot i trattini si eliminano: la forma equivalente e'
     `SALARYMANAGER_PDF_PASSWORDCANDIDATE`, mentre `SALARY_MANAGER_PDF_PASSWORDCANDIDATE` (usata in precedenza) non
     corrisponde ad alcuna proprieta' ed era ignorata (`PdfPropertiesBindingTest`).

## Integrazione dei moduli

122. **Dipendenze condivise**: PDFBox 3.0.8 (ultima 3.x stabile su Maven Central) dichiarato una sola volta nel pom;
     `excludedGroups` di surefire parametrizzato con `sam.test.excludedGroups` (decisione 70).
123. **Verifica tra moduli** (`FlussoCedoliniIntegrationTest`): un lotto con tutte le fixture elaborato con
     `salary-manager.ai.concurrency=8` (dalla decisione 139 `salary-manager.letture-contemporanee=8`) produce una sola persona per codice fiscale e una sola azienda per identificativi
     (senza il lock della decisione 73 il test fallisce); riepilogo e segnalazioni risultano calcolati per ogni rapporto
     al termine del lotto e ricalcolati dopo un'eliminazione; un errore in un listener sincrono annulla l'acquisizione
     (nessuna anagrafica ne' cedolino) o l'eliminazione (il cedolino resta), a riprova che i ricalcoli girano nella
     stessa transazione (decisione 33).

## Integrazione finale

124. **`personaId` e `rapportoId` del caricamento derivati, non salvati**: la tabella `caricamento` resta invariata;
     il repository di `cedolini` ricava il rapporto con una left join sulla propria tabella `cedolino` (cedolino
     acquisito o, per duplicati e aggiuntivi, quello gia' presente) e il servizio risolve la persona con
     `ConsultazioneAnagrafica`, una lettura per rapporto distinto. Nessun modulo legge tabelle altrui; se il cedolino
     viene eliminato i campi tornano `null` invece di puntare a righe inesistenti. Il frontend seleziona la persona
     del primo file acquisito del lotto (altrimenti di un duplicato o aggiuntivo) senza chiamate aggiuntive.
125. **Riprova a fine lotto dei PDF protetti**: i file di un lotto sono elaborati in parallelo, quindi un PDF che si
     apre col codice fiscale di una persona censita da un altro file dello stesso lotto poteva fallire solo perche'
     letto prima. In un lotto di piu' file un PDF che nessuna password apre torna `IN_CODA` e viene rielaborato una
     sola volta quando tutti gli altri file del lotto sono conclusi; al secondo tentativo, o in un lotto di un solo
     file, resta l'errore "PDF protetto da password". Il lotto non risulta completato finche' la riprova non termina.
126. **Messaggi per l'utente in italiano corretto**: i messaggi restituiti dal backend (esiti dei caricamenti,
     problem details, errori dei calcoli) usano le lettere accentate; log e commenti restano in ASCII con l'apostrofo,
     come le descrizioni di `openapi.yaml` e delle annotazioni springdoc (che devono coincidere col file).
127. **Limiti del multipart**: oltre a 25 MB per file e 500 MB per richiesta, `server.tomcat.max-part-count: 1001`
     (fino a 1000 PDF in un lotto piu' il campo password: il default di Tomcat, 50 parti, rifiutava i lotti grandi) e
     `server.tomcat.max-swallow-size` pari al limite della richiesta, cosi' a un file troppo grande il client riceve
     la risposta di errore invece di una connessione chiusa.
128. **Fixture demo complete**: aggiunte `rossi-beta-2024-07-14` (quattordicesima separata), `rossi-alfa-2024-tfr02`
     (secondo cedolino di fine rapporto, `TFR02`) e `rossi-beta-2025-03` (PDF protetto da una password che non e' un
     codice fiscale, `demo-beta-2025`), cosi' la modalita' demo copre tutti i casi della specifica. I PDF di fixture
     stanno solo in `src/main/resources/fixtures/pdf/` (i duplicati in `src/test/resources` sono stati rimossi; li'
     restano solo le fixture usate esclusivamente dai test).
129. **Suite end-to-end facoltativa con Playwright** (`@playwright/test` 1.63.0, versione esatta, solo Chromium) in
     `frontend/e2e/`, fuori da `npm test` e dal build: avvia backend demo su database temporaneo e Vite tramite i
     `webServer` di Playwright (porte 18080 e 15173 configurabili, `mvnw.cmd` su Windows), test in sequenza su un
     unico stack. Il browser si installa con `npx playwright install chromium` oppure si indica con
     `SAM_E2E_CHROMIUM`. Ha una propria configurazione TypeScript (`tsconfig.e2e.json`, strict) inclusa nel typecheck.
130. **Tabelle delle voci compatte**: il dettaglio della busta mostra solo le colonne con almeno un valore (quantita',
     base, competenza, trattenuta) e su schermi stretti (< 576 px) omette la base e il residuo dell'anno precedente
     dei ratei, con carattere e spaziatura ridotti, cosi' importi e saldi restano visibili senza scorrere.
131. **Campi non trovati con nomi leggibili**: i percorsi tecnici dello schema di estrazione
     (`datiContrattuali.codiceCnel`) sono tradotti con la mappa `campoCedolino` di `it.ts`; un percorso sconosciuto
     mostra l'ultimo segmento in parole minuscole.
132. **Interfaccia**: le notifiche compaiono in basso a destra per non coprire la barra superiore e la tendina delle
     persone; la tendina e' larga 20rem su desktop per mostrare per intero "Nome Cognome (CODICE FISCALE)" degli
     omonimi; a lotto concluso la modale elenca prima errori, aggiuntivi e duplicati, poi gli acquisiti.
133. **Descrizioni CCNL con le lettere accentate** (changelog `240-ccnl-descrizioni.sql`): "Altro CCNL (13
     mensilità)" / "(14 mensilità)" e note di verifica con "periodicità", "anzianità", "mensilità"; il seed `210`
     gia' applicato non si modifica (decisione 49).
134. **Riepilogo senza affermazioni non verificate**: per un rapporto in corso l'anzianita' e' mostrata "al <mese>
     (ultimo cedolino)", perche' il backend la calcola fino all'ultimo cedolino ordinario e non fino a oggi; il TFR
     indica "Dal cedolino di <mese>" (l'eventuale proiezione ad oggi e' spiegata nella nota della stima). Il prossimo
     scatto e' "nessuno previsto" solo a rapporto concluso o con il numero massimo di scatti raggiunto; se la data non
     e' determinabile si legge "non determinabile (CCNL da indicare)" oppure "—".
135. **Dettaglio della busta sempre riletto dopo le modifiche**: caricamenti, eliminazioni e cambi di CCNL invalidano
     tutti i dettagli dei cedolini (chiave `['cedolino']`), perche' la riclassificazione puo' cambiarne competenza,
     progressivo TFR e nome del file. Le barre aperte delle mensilita' sono ricordate per id del cedolino, non per
     chiave di competenza (che un cedolino di fine rapporto rinumerato puo' ereditare).
136. **Accessibilita' delle mensilita'**: la barra di una mensilita' non ha `aria-label`; il nome accessibile nasce
     dal contenuto (azione visivamente nascosta, titolo, indicatori Scatto/Aumento, ratei e netto), cosi' i lettori di
     schermo annunciano gli stessi dati visibili. Gli avvisi dell'estrazione del cedolino sono elencati nel dettaglio
     in una sezione dedicata, accanto ai dati non trovati. Se l'elenco delle persone non si carica, l'area principale
     mostra il motivo (Problem Details) con il pulsante "Riprova" e la tendina e' marcata come non valida; anche il
     download del PDF riporta il motivo restituito dal backend.
137. **Chiave API e variabili `SAM_*` anche dal file `.env`** (richiesta dell'utente): `application.yml` importa il
     `.env` della radice del progetto (`optional:file:${SAM_ENV_FILE:../.env}[.env]`), lo stesso file di Docker
     Compose, cosi' `ANTHROPIC_API_KEY` e le variabili `SAM_*` dei segnaposto valgono anche avviando il backend con
     `mvnw` da `backend/`. Il file non e' letto come `.properties` (ISO-8859-1, `\` come escape, virgolette e commenti
     dentro il valore) ma da un `PropertySourceLoader` dedicato, `shared.config.CaricatoreFileEnv`, con la sintassi di
     Compose: UTF-8 con o senza BOM, LF o CRLF, `export` facoltativo, commenti in linea, apici singoli letterali (con
     `\'` come apice), escape di Compose solo tra virgolette doppie, la `\` letterale negli altri casi, sostituzione
     di `$NOME`, `${NOME}`, `${NOME:-default}` e delle altre forme di Compose (`$$` per un `$`) fuori dagli apici
     singoli (`shared.config.InterpolazioneCompose`). Cosi' lo stesso file da' gli stessi valori ai due avvii, con
     due differenze volute: una variabile non definita nella sostituzione blocca l'avvio invece di diventare vuota
     (una password con `$` non viene troncata in silenzio: va tra apici singoli o con `$$`), e un valore vuoto vale
     come variabile non impostata, cosi' valgono i default di `application.yml` come con i `${VAR:-default}` di
     `docker-compose.yml` (altrimenti `SAM_SERVER_ADDRESS=` farebbe ascoltare il backend su tutte le interfacce,
     contro la decisione 138). Il loader salva ogni `${` del valore con l'escape dei segnaposto di Spring (`\${`):
     senza, Spring risolverebbe i `${...}` di un valore che entra in un segnaposto di `application.yml`, anche tra
     apici singoli. Una riga non valida blocca l'avvio con file e numero di riga, mai il contenuto. La forma relaxed
     (`SALARYMANAGER_*`) nel `.env` non vale; con un profilo attivo si legge anche `.env-<profilo>`
     (`.env-*` e' ignorato da git e dai contesti Docker). La chiave non passa da `Environment.getProperty`:
     `shared.config.VariabiliAmbiente` la legge dalla variabile d'ambiente, se non vuota, poi dal `.env`, mai da
     `application*.yml`, system property o argomenti; `FornitoreClientAnthropic` la verifica (solo ASCII stampabile,
     senza spazi) prima di creare il client, con un errore `CONFIGURAZIONE` che non ne ripete il valore. I test non
     leggono il `.env` dello sviluppatore (surefire e la suite end-to-end impostano `SAM_ENV_FILE` su un file
     inesistente), tranne `EstrazioneCampioniLiveTest`.
138. **Backend in ascolto solo su `127.0.0.1`** (`server.address: ${SAM_SERVER_ADDRESS:127.0.0.1}`): in fase 1 non
     c'e' login, quindi il backend avviato con `mvnw` non accetta connessioni da altri computer, come gia' Docker
     Compose con le porte pubblicate su `127.0.0.1`. Nel container `SAM_SERVER_ADDRESS` vale `0.0.0.0` (Dockerfile e
     `docker-compose.yml`, valore fisso), perche' nginx deve raggiungere il backend; healthcheck e proxy restano
     invariati. Il proxy di Vite e la suite end-to-end puntano a `127.0.0.1` invece che a `localhost`, che puo' essere
     risolto prima in `::1`. Se `server.address` risulta vuoto (per esempio `SAM_SERVER_ADDRESS` vuota nell'ambiente)
     il backend non parte (`web.config.SecurityConfiguration`), invece di ascoltare su tutte le interfacce.

## Lettore locale dei cedolini

139. **[utente] Lettore locale dei cedolini, predefinito al posto di Claude**: l'API Anthropic richiede crediti a
     pagamento separati dall'abbonamento a Claude, quindi la lettura di default non deve dipenderne.
     - **Porta**: `EstrattoreCedolino` diventa `LettoreCedolini` (stessa firma: `estrai(byte[])` ─►
       `RisultatoEstrazione`), con tre implementazioni scelte da `salary-manager.lettore` (`LettoreProperties`,
       variabile `SAM_LETTORE`, enum senza distinzione tra maiuscole e minuscole: un valore diverso blocca l'avvio):
       `locale` (default, `adapter.locale.LettoreLocaleCedolini`), `claude` (`adapter.ai.ClaudeLettoreCedolini`, gia'
       `AnthropicEstrattoreCedolino`) e `fixture` (`adapter.fixture.FixtureLettoreCedolini`). Le impostazioni di Claude
       restano in `salary-manager.ai.*`; `salary-manager.ai.provider` e `SAM_AI_PROVIDER` sono eliminati (nessun
       alias: un valore rimasto nel `.env` non ha piu' effetto e si legge il lettore locale). Proprieta' comuni a
       tutti i lettori spostate fuori da `ai`: `salary-manager.letture-contemporanee` (letture parallele dei
       caricamenti; `ai.concurrency` limita ora solo le richieste all'API) e `salary-manager.fixture-dir`.
     - **Stessa validazione**: il lettore locale produce lo stesso JSON dello schema degli structured outputs e lo
       passa a `InterpreteRispostaEstrazione` (normalizzazione, mascheramento IBAN, `ValidatoreCedolinoEstratto`);
       il modello registrato e' `lettore-locale-1/<id del layout>` (la versione cambia quando le regole cambiano in
       modo da rendere utile una rilettura). Il resto dell'applicazione non sa quale lettore e' stato usato e il
       contratto OpenAPI non cambia.
     - **Lettura**: testo del PDF con le coordinate dei caratteri (PDFBox), parole e righe ricostruite per posizione,
       una strategia per layout (`LayoutCedolino`: `riconosce` stretto su poche etichette caratteristiche, `leggi` con
       posizioni relative alle etichette o, per i moduli prestampati, alle coordinate del foglio). Layout iniziali:
       JOB Sistemi, Zucchetti (grafica classica e recente), modulo continuo M39320 degli studi paghe, progettati e
       misurati sui cedolini reali disponibili localmente, senza copiarne alcun valore nel repository.
     - **Non indovinare**: un PDF senza testo selezionabile (`PDF_SENZA_TESTO`), un layout sconosciuto o un layout
       riconosciuto senza periodo, codice fiscale del dipendente o netto nel testo (`LAYOUT_NON_RICONOSCIUTO`) vanno
       in errore con un messaggio in italiano che suggerisce `SAM_LETTORE=claude`; i campi non trovati restano vuoti
       (mai dedotti). I due motivi sono nuovi valori interni di `EstrazioneCedolinoException.Motivo`, non esposti
       dalle API (il caricamento riporta solo il messaggio; superato dalla decisione 141, che espone `MotivoErrore`).
     - **Confidenza onesta**: base 0,95, ridotta per ogni sezione attesa e non trovata, ogni controllo interno non
       superato (somma delle voci contro i totali stampati, TFR spettante contro fondo piu' quota) e ogni campo
       chiave assente; i controlli non superati diventano avvisi senza valori.
     - **Dati bancari mai letti** (nessun campo li raccoglie, anche se stampati); nei log solo l'id del layout.
     - **PDFBox** ammesso anche in `cedolini.adapter.locale` (`ArchitectureTests`).
     - **Test**: nel repository solo PDF sintetici generati in memoria con dati inventati (`CedoliniSintetici`:
       ordinaria, tredicesima e fine rapporto JOB Sistemi, Zucchetti nelle due grafiche, modulo M39320 ordinario e di
       fine rapporto, cambio di azienda a meta' mese, scansione e layout sconosciuto), regole comuni
       (`RegoleLetturaTest`) e scelta del lettore (`SceltaLettoreTest`). La misura sui cedolini reali e' un test a
       parte, `MisuraCampioniLocaleTest` con `@Tag("campioni")`, escluso di default insieme a `live`
       (`sam.test.excludedGroups=live,campioni`) ed eseguibile senza rete con
       `./mvnw test -Dgroups=campioni -Dsam.test.excludedGroups=none`: stampa solo conteggi ed esiti dei controlli di
       coerenza (netto, somme, ratei, TFR, confronto dei mesi consecutivi dello stesso rapporto) e fallisce se un
       cedolino letto non e' coerente. `SAM_SAMPLES_DIR` accetta piu' cartelle (separatore di percorso del sistema).
     - **Frontend**: i testi che citavano Claude per la lettura parlano del lettore in generale ("Lettura del cedolino
       in corso").

## Cedolini caricati con errore e inserimento a mano

140. **[utente] Mensilita' mancanti e cedolini caricati con errore sono cose diverse**: una mensilita' *mancante* non
     ha ne' cedolino ne' file associato; un *cedolino caricato con errore* e' un file (workspace + hash SHA-256: stesso
     contenuto = stesso file) il cui ultimo tentativo concluso e' in ERRORE e che nessun cedolino archiviato ha.
     - **Area globale** "Cedolini caricati con errore" (`GET /api/v1/caricamenti/in-errore`): rossa, in cima alla
       pagina anche senza una persona selezionata, ben distinta dall'arancione di "Mensilità mancante", con il
       contatore nella barra superiore.
     - **Associazione a un mese** ("Associa a un mese", solo per scelta dell'utente): la mensilita' diventa una riga
       `IN_ERRORE` (rossa, con "Inserisci a mano") e la segnalazione `CEDOLINO_IN_ERRORE` (AVVISO) prende il posto di
       "Mensilità mancante" (ANOMALIA). Il file non viene mai associato automaticamente: i file che falliscono non
       hanno dati leggibili e indovinare il mese violerebbe la decisione 34.
     - **Lista mensilita'**: per la stessa competenza PRESENTE prevale su IN_ERRORE e IN_ERRORE su MANCANTE; una riga
       IN_ERRORE puo' cadere fuori dal periodo atteso (per esempio prima del primo cedolino).
141. **Motivo dell'errore salvato ed esposto**: colonna `caricamento.motivo` (changelog `330`; un CHECK la ammette
     solo in ERRORE) con l'enum pubblico `cedolini.MotivoErrore`, esposto in `Caricamento.motivo`,
     `CaricamentoInErrore.motivo` e `RigaMensilita.motivoErrore`, perche' le azioni offerte dipendono dal motivo
     (password, nuova lettura, inserimento a mano, "dividi il PDF"). Le eccezioni interne si traducono in
     `MotiviErrore` con `switch` esaustivi (un nuovo motivo interno non compila senza la sua traduzione); un PDF con
     piu' cedolini ha ora il motivo interno `PIU_CEDOLINI` invece di `DOCUMENTO_NON_VALIDO`. Supera l'ultimo punto
     della decisione 139 ("non esposti dalle API"). Gli errori registrati prima non vengono ricostruiti dai testi dei
     messaggi (confronto fragile): restano con motivo `null`, senza PDF e con il loro messaggio, e vanno ricaricati.
142. **[utente] PDF originale dei file in errore completabili conservato** in `caricamento_pdf` (changelog `331`),
     uno per file (chiave workspace + hash), come caricato (anche se protetto): serve per l'anteprima, l'inserimento
     a mano e la nuova lettura.
     - **Quando**: solo quando un tentativo finisce in ERRORE con un motivo che lo prevede
       (`MotivoErrore.conservaPdf()`: tutti tranne `PDF_NON_VALIDO`, `PDF_TROPPE_PAGINE` e `PIU_CEDOLINI`) e il file
       non e' archiviato. Non alla registrazione: dati personali a riposo solo quando servono e nessuna doppia
       scrittura per i file che si leggono.
     - **Fino a quando**: si elimina nella stessa transazione che risolve il file: archiviato come cedolino (lettura
       o inserimento a mano), scartato (DUPLICATO, AGGIUNTIVO_NON_GESTITO), chiuso con un motivo che non lo prevede,
       rimosso dall'utente. Al riavvio si eliminano quelli dei file non piu' in errore (rete di sicurezza).
     - **Limite accettato**: un riavvio durante la *prima* elaborazione lascia il file in errore (`INTERROTTO`)
       senza PDF ("...: ricarica il file"); un riavvio durante una nuova lettura non perde nulla, perche' il PDF e'
       del file e non del tentativo, e il messaggio lo dice ("...: riprova la lettura o inserisci i dati a mano").
       Un'elaborazione interrotta senza riavvio ha i byte in memoria e conserva il PDF, con lo stesso messaggio.
       Quello che conta per le azioni e per i testi e' la presenza del PDF (`pdfDisponibile`), non il solo motivo.
143. **"File in errore" calcolato, non salvato**: nessun nuovo stato del caricamento.
     - **Predicato unico** (`JooqCaricamentiRepository`, condizione `fileInErrore`): esiste un tentativo ERRORE del
       file senza tentativi successivi conclusi e nessun cedolino ha quell'hash; la riga elencata e' l'ultimo
       tentativo concluso. `inElaborazione` (un tentativo IN_CODA o IN_LETTURA dello stesso file) tiene visibile la
       riga durante una nuova lettura e blocca le azioni (409).
     - **Scritture serializzate**: tutto cio' che decide la sorte di un file passa dalla transazione serializzata per
       workspace (decisione 73) e ricontrolla il predicato: gli esiti conclusivi diversi da ACQUISITO
       (`ChiusuraCaricamento`), l'acquisizione e le azioni dell'utente. Se la transazione di chiusura fallisce, un
       aggiornamento semplice registra comunque lo stato: un caricamento non resta mai IN_LETTURA. La chiusura di un
       errore di lettura avviene dopo aver rilasciato il permesso di lettura, che non resta occupato in attesa del
       lock.
     - **Chiusura di un ERRORE**: dopo averlo registrato ricontrolla il predicato sul file, non solo l'assenza del
       cedolino: se un tentativo successivo dello stesso file si e' gia' concluso (per esempio scartato come
       DUPLICATO) il file non e' piu' in errore e PDF e associazione si eliminano invece di conservarli.
     - **Tentativi gemelli**: archiviando un file (lettura o inserimento a mano) i suoi tentativi in ERRORE
       successivi (per id: lo stesso PDF due volte nello stesso lotto) diventano DUPLICATO del cedolino archiviato;
       altrimenti, eliminato il cedolino, il file tornerebbe "in errore" senza PDF.
     - **Ordine nell'acquisizione**: caricamento ACQUISITO, eliminazione di PDF conservato e associazione, poi
       `CedolinoAcquisito`: i listener sincroni vedono gia' uno stato coerente.
     - **Modifica la decisione 82**: al riavvio i caricamenti non conclusi di un file gia' archiviato diventano
       DUPLICATO (la loro elaborazione li avrebbe scartati); gli altri ricevono il motivo `INTERROTTO` e un messaggio
       che dipende dalla presenza del PDF conservato; poi si eliminano PDF conservati e associazioni dei file non piu'
       in errore e si ricalcolano le segnalazioni dei rapporti associati ai file coinvolti.
144. **Associazione a una mensilita'** in `caricamento_attribuzione` (changelog `332`): una per file (chiave
     workspace + hash, non per tentativo: nessuna eredita' tra tentativi) e una per competenza (UNIQUE rapporto + anno
     + mese 1..14), FK verso `rapporto_lavoro` con `ON DELETE CASCADE`, come `riepilogo_rapporto` e `segnalazione`:
     eliminato il rapporto, l'associazione sparisce senza codice applicativo.
     - Rifiutata (409) se la mensilita' ha gia' un cedolino o un altro file associato; il file si sposta liberamente.
     - Ogni modifica (associazione creata, spostata o rimossa; file archiviato in un altro rapporto, scartato o
       rimosso; nuovo tentativo del file associato concluso in ERRORE, che puo' cambiare motivo e PDF disponibile;
       riavvio che interrompe un tentativo del file associato) pubblica `cedolini.CaricamentiInErroreModificati`,
       ascoltato solo da segnalazioni: i dati dei cedolini, e quindi i calcoli, non cambiano. La segnalazione
       riporta motivo e azioni possibili e non deve mai restare indietro.
     - **Testo della segnalazione** `CEDOLINO_IN_ERRORE`: con il PDF conservato propone l'inserimento a mano
       dall'elenco o una nuova lettura; senza (motivi che non lo conservano, errori delle versioni precedenti, prima
       elaborazione interrotta dal riavvio) propone solo un nuovo caricamento: "caricane una copia valida" per un
       file danneggiato, "dividi il PDF in un file per cedolino" per troppe pagine o piu' cedolini, altrimenti "il PDF
       non e' stato conservato, caricalo di nuovo". Il contesto delle regole riceve per ogni mensilita' motivo e
       presenza del PDF (`ContestoRapporto.FileInErrore`). Precisa il testo fisso della specifica, che non
       considerava i file senza PDF.
     - **Modifica le decisioni 35, 96, 99 e 100**: le mensilita' con un file associato non sono mancanti (le tre regole
       "mancante" le ignorano tramite `ContestoRapporto.mensilitaInErrore`) e hanno la loro segnalazione
       `CEDOLINO_IN_ERRORE` con la competenza; la riga IN_ERRORE della lista mensilita' viene dal modulo cedolini, non
       dalle segnalazioni.
     - **Modifica la decisione 124**: la tabella `caricamento` resta senza riferimenti all'anagrafica; il solo legame
       salvato tra un file in errore e un rapporto e' l'associazione scelta dall'utente, mentre `personaId` e
       `rapportoId` dei caricamenti restano derivati dal cedolino.
145. **[utente] L'inserimento a mano e' un lettore in piu'**: il modulo, con gli stessi campi e percorsi dello schema
     `prompts/cedolino-schema-v1.json` (`CedolinoManuale`), diventa il JSON dello schema (`JsonCedolinoManuale`) e
     passa da `InterpreteRispostaEstrazione` e `AcquisizioneCedolino` come una lettura: normalizzazione, mascheramento
     degli IBAN, controlli di coerenza, censimento, competenza, duplicati, riclassificazione, ricalcoli e stato
     ACQUISITO senza logica duplicata.
     - Si archivia il PDF originale conservato con il suo hash: un nuovo caricamento dello stesso file e' DUPLICATO.
     - Modello registrato `inserimento-manuale-1` (`ModelliEstrazione`), confidenza `null` (non c'e' una lettura),
       nessun campo non trovato (un campo vuoto e' un dato "non stampato"); il dettaglio del cedolino espone
       `inseritoAMano`, mostrato al posto dell'affidabilita' della lettura. Il JSON salvato resta come traccia di
       quanto inserito.
     - Una persona gia' censita non viene modificata: nome e cognome in archivio restano (superato dalla decisione
       150: la persona in archivio si sceglie dall'elenco e i suoi dati non si riscrivono).
146. **Verifica = acquisizione annullata**: `POST /api/v1/caricamenti/{id}/cedolino/verifica` esegue la stessa
     acquisizione dell'inserimento nella transazione serializzata e la annulla sempre (`setRollbackOnly`): competenza
     definitiva, scarto per duplicato o aggiuntivo, persona e rapporto nuovi sono quelli veri, senza una seconda
     implementazione. E' sicuro perche' tutti i listener sono `@EventListener` sincroni nella stessa transazione e non
     c'e' event publication registry (decisione 33): l'annullamento non lascia tracce, nemmeno in riepiloghi e
     segnalazioni. In prova non si scrive la riga di log dell'acquisizione; l'anagrafica puo' registrare a INFO l'id
     di una persona poi annullata (accettato: e' solo un id). Un inserimento scartato annulla la transazione allo
     stesso modo: 409 e il file resta in errore con il suo PDF.
147. **Errori bloccanti e avvisi dell'inserimento a mano** (`ValidazioneCedolinoManuale`):
     - **Bloccanti** (400 con `Problema.errori[{campo, messaggio}]`, `campo` = percorso nello schema come
       `totali.netto` o `voci[3].descrizione`): i dati che servono all'archiviazione e al confronto dei duplicati o
       che l'interprete scarterebbe in silenzio. Tipo, anno (dal 1900 all'anno prossimo) e mese 1..12 del periodo;
       codice fiscale, nome e cognome del dipendente; ragione sociale, partita IVA e codice fiscale dell'azienda;
       totale competenze, totale trattenute e netto; limiti di importi e quantita' (meno di 10^8 in valore assoluto,
       cioe' al massimo 8 cifre intere, e 5 decimali) e lunghezze dei testi; descrizione e categoria delle voci;
       ratei univoci per tipo e unita'. Un errore per campo.
     - **Limite degli importi piu' stretto delle colonne**: `NUMERIC(15,5)` ammette 10 cifre intere, ma
       l'acquisizione calcola e salva nella stessa transazione dati derivati (RAL = lordo per mensilita', TFR,
       valore dei ratei = saldo per paga oraria) che con valori di 9-10 cifre uscirebbero dalle colonne; un valore
       cosi' grande e' quasi sempre un codice incollato nel campo sbagliato. Se un dato calcolato non sta comunque
       nella colonna (errore SQL di classe 22) verifica e inserimento rispondono 400 con un errore generale ("Alcuni
       importi o quantità sono troppo grandi: controlla i valori inseriti"), mai 500, e il file resta in errore.
     - **Testi liberi**: spazi Unicode (anche lo spazio non separabile delle pagine web) resi come spazi semplici e
       caratteri invisibili tolti prima del mascheramento degli IBAN, che riconosce anche gruppi separati da
       trattini o punti.
     - **Codice fiscale del dipendente**: valido, di 11 cifre (provvisorio, con un avviso) oppure uguale a quello di
       una persona censita; altrimenti errore, perche' il censimento accetta qualunque codice e un refuso creerebbe
       una persona fantasma. Superato dalle decisioni 151 e 152: con la persona scelta il codice fiscale e' quello in
       archivio e non si valida, con "Crea nuovo" un codice fiscale censito e' un errore.
     - **Avvisi** non bloccanti salvati con il cedolino: quelli del validatore comune, le somme delle voci contro i
       totali stampati (stessa regola e tolleranza del lettore locale) e il codice fiscale provvisorio; la sola
       verifica aggiunge gli avvisi di contesto (persona o rapporto nuovi, nome diverso da quello in archivio, mese
       diverso da quello associato; rivisti dalla decisione 155).
     - I messaggi non ripetono mai i valori inseriti. Il frontend rispecchia le regole, ma l'autorita' e' il server;
       gli errori campo per campo viaggiano con `shared.DatiNonValidiException`.
148. **Nuova lettura e rimozione**:
     - `POST /api/v1/caricamenti/{id}/rilettura` registra un nuovo caricamento dello stesso file (stesso nome e hash)
       in un nuovo lotto di un solo file e lo elabora dal PDF conservato, con la password facoltativa solo in memoria
       (mai salvata, mai nei log, nascosta nel `toString` del DTO): e' la via per i PDF protetti senza ricaricarli. Un
       lotto di un solo file non ha la riprova a fine lotto (decisione 125).
     - `DELETE /api/v1/caricamenti/{id}` elimina tutti i tentativi in ERRORE del file, il PDF conservato e
       l'associazione (privacy prima di tutto, nessuno stato in piu'); i tentativi con altri esiti restano.
149. **Frontend dei file in errore e dell'inserimento a mano**:
     - **Stato del modulo** con `@mantine/form` 9.7.1 (versione esatta, la stessa di Mantine): percorsi uguali a
       quelli dello schema, cosi' un errore del server su `voci[3].descrizione` si posa sul campo `voci.3.descrizione`.
     - **Importi all'italiana** (`CampoNumero`, su `NumberInput`): separatore decimale "," e delle migliaia ".", con
       `allowedDecimalSeparators={[',']}` perche' con il default di Mantine "1.234,56" diventerebbe 1,23; anno e
       numero di scatti interi e senza separatore delle migliaia.
     - **Anteprima del PDF conservato** da un URL `blob:` (scaricato con fetch e revocato alla chiusura: non soggetto
       a `X-Frame-Options`), con "Apri in una nuova scheda" per i telefoni, che non mostrano i PDF negli iframe.
     - **Nessuna bozza salvata nel browser**: chiudere un modulo modificato chiede conferma.
     - **Copia dei dati da un rapporto o da un cedolino esistente** fatta dal client con `GET /api/v1/rapporti/{id}`
       e `GET /api/v1/cedolini/{id}` (nessuna API nuova), solo nei campi vuoti e mai per la data di cessazione, i
       totali e le quantita' e gli importi delle voci (valori del singolo mese). Superato dalla decisione 154: la
       copia avviene alla scelta di persona e azienda, con le stesse fonti e regole.
     - **Voci e ratei copiati** da un cedolino sono modelli: finche' restano come copiati e senza quantita' ne'
       importi (per i ratei senza valori del mese) non vengono inviati, validati ne' contati, e una nota offre di
       toglierli; modificati diventano righe dell'utente.
     - **Numeri**: le quantita' delle voci e i valori dei ratei accettano 5 decimali come lo schema; maturato e
       goduto dei ratei possono essere negativi (rettifiche); gli importi delle voci si incollano senza segno (il
       segno lo da' la categoria), mentre negli altri campi non negativi un valore negativo incollato e' rifiutato con
       un messaggio. Stesso limite del server (8 cifre intere, 5 decimali); una data che il browser considera
       incompleta (`validity.badInput`) o con l'anno di piu' di 4 cifre e' un errore, non un campo vuoto.
     - **Errori sempre attuali**: gli errori di riga e quelli tra campi (cessazione prima dell'assunzione) spariscono
       quando una modifica a un altro campo li risolve; l'esito della verifica e' annunciato ai lettori di schermo.
     - **Elenco dei file sempre attuale**: un 404 o 409 sulle azioni di un file in errore (verifica, inserimento,
       associazione, nuova lettura, rimozione) invalida l'elenco, che mostra subito lo stato vero; "Inserisci a mano"
       aperto durante un caricamento in corso rilegge l'elenco prima di dire che il file non c'e' piu'.
     - **Salvataggio** abilitato solo dopo una verifica dei valori correnti con esito `ACQUISIBILE`.
     - **Nessun router**: le azioni sui file (inserisci, associa, rileggi, rimuovi) sono offerte a tutta la pagina da
       un contesto React dell'`App`.
150. **[utente] Persona e azienda dell'inserimento a mano si scelgono dall'elenco**: per evitare refusi che
     creerebbero anagrafiche fantasma, la sezione Dipendente ha la tendina "Persona in archivio" ("Seleziona
     persona/dipendente") con accanto "Crea nuovo", la sezione Datore di lavoro la tendina "Azienda in archivio"
     ("Seleziona azienda") con accanto "Crea nuova".
     - **Etichette anti-omonimia**: "Nome Cognome", con il codice fiscale solo per gli omonimi; per le aziende la
       ragione sociale, con la partita IVA solo per le omonime (in mancanza il codice fiscale, poi la posizione INPS).
     - **Dati anagrafici in sola lettura** con i valori in archivio: codice fiscale, cognome e nome della persona (la
       data di nascita resta modificabile solo se in archivio manca: completa la persona, come gia' fa il
       censimento); ragione sociale, partita IVA, codice fiscale e posizione INPS dell'azienda, anche se vuoti
       (sono chiavi del censimento: un refuso che completa una P.IVA mancante dividerebbe poi l'azienda in due).
       Matricola, posizione INAIL, indirizzo e codice azienda restano modificabili (attributi del singolo cedolino o
       aggiornabili).
     - **Ordine delle aziende**: con una persona scelta prima le sue aziende (stesso ordine dei suoi rapporti), poi
       le altre per ragione sociale; nessuna scelta automatica dell'azienda quando la persona ha un solo rapporto (il
       file puo' essere di un nuovo datore di lavoro).
     - **Ricerca e ritorno all'elenco**: le tendine cercano anche il codice fiscale della persona e la partita IVA o
       il codice fiscale dell'azienda (senza distinzione di maiuscole e accenti); dopo "Crea nuovo" i dati anagrafici
       sono modificabili e "Scegli dall'elenco" riporta alla tendina senza cancellarli (nascosti, tornano tornando a
       "Crea nuovo"): i dati anagrafici cambiano solo da o verso un'anagrafica in archivio. Le tendine sono
       obbligatorie (asterisco e `aria-required`); cambiando scelta gli errori della scelta precedente e dei suoi dati
       anagrafici (anche quelli del server) spariscono subito da campo, riepilogo e intestazione della sezione.
     - **Modifica le decisioni** 145 (ultimo punto: con la persona scelta nome e cognome sono quelli in archivio, con
       "Crea nuovo" un codice fiscale censito e' un errore), 147 (il codice fiscale non e' piu' accettato perche'
       censito: vedi la decisione 152) e 149 (la "Copia dei dati" da un rapporto o da un cedolino diventa la
       precompilazione alla scelta, decisione 154, e c'e' un'API nuova, decisione 153).
151. **Anagrafiche scelte usate esattamente**: `CedolinoManuale` ha `personaId` e `aziendaId` (`null` = "Crea
     nuovo"). Con un id il server usa proprio quell'anagrafica, qualunque cosa dicano le regole di riconoscimento:
     - **Esistenza** controllata per prima (`AnagraficheScelte.risolvi`): un id non positivo o di un'anagrafica non
       piu' nel workspace e' un 400 sul campo `personaId`/`aziendaId` ("La persona scelta non è più in archivio:
       scegline un'altra o creane una nuova"), restituito da solo perche' senza l'anagrafica i dati non si possono
       completare. Un id non positivo non diventa mai "Crea nuovo" in silenzio.
     - **Dati anagrafici sovrascritti** con quelli in archivio (`AnagraficheScelte.conDatiInArchivio`) prima della
       validazione: cio' che il client invia per quei campi non conta, e il JSON salvato con il cedolino riporta i
       dati in archivio. Codice fiscale, nome e cognome della persona scelta, la sua data di nascita se in archivio
       e i quattro identificativi dell'azienda scelta non vengono validati (sono gia' in archivio): il censimento dei
       PDF salva la data di nascita come letta, e una data non plausibile (nel futuro, anno fuori intervallo) in sola
       lettura bloccherebbe per sempre l'inserimento a mano per quella persona. La data inserita per una persona
       scelta che in archivio non la ha (la completa) si valida come sempre.
     - **Collegamento**: la persona si ritrova con il normale `trovaOCreaPersona` sul codice fiscale in archivio
       (univoco nel workspace, `uk_persona_cf`); l'azienda con il nuovo `CensimentoAnagrafica.aggiornaAziendaScelta`,
       che aggiorna proprio l'azienda scelta senza abbinamento (stessi aggiornamenti per recenza e identificativi
       completati dell'azienda riconosciuta). Il rapporto risultante deve essere della coppia scelta, altrimenti
       l'acquisizione si annulla (`IllegalStateException`, mai atteso).
     - **Ricontrollo nella transazione serializzata** (`AcquisizioneCedolino.acquisisciManuale`, sotto lo stesso
       lock per workspace dei caricamenti): esistenza delle scelte e regole della decisione 152 sui dati interpretati,
       contro un caricamento in background che nel frattempo crea la stessa persona o azienda o un'eliminazione
       dell'anagrafica scelta. Il riconoscimento dell'azienda (`ConsultazioneAnagrafica.aziendaRiconosciuta`) non
       solleva eccezioni sui dati, cosi' non segna la transazione da annullare.
     - **Perche' non la sola verifica a posteriori** (scelta confrontata con l'esito del riconoscimento): su archivi
       con omonime risolte per "prima registrata", spareggi tra CF e P.IVA o righe scritte prima delle regole attuali
       il riconoscimento puo' scegliere un'altra azienda e l'utente non potrebbe mai salvare. Il collegamento per id
       non sbaglia azienda e lascia invariata la pipeline (registrazione, censimento, competenza, duplicati).
     - Ordine dei controlli di verifica e inserimento: forma del corpo (400), esistenza delle scelte (400), campi
       compresi i "già in archivio" (400), controlli sul file nella transazione (404/409), ricontrollo nella
       transazione (400), acquisizione.
152. **"Crea nuovo" non crea doppioni e non unisce in silenzio**: senza `personaId` un codice fiscale (normalizzato)
     uguale a quello di una persona in archivio e' un 400 su `dipendente.codiceFiscale` ("Questo codice fiscale è già
     in archivio: seleziona la persona dall'elenco"), prima del controllo di validita'. Senza `aziendaId`, se i dati
     del datore non hanno altri errori e il censimento riconoscerebbe un'azienda in archivio, 400 sul campo che
     determina l'abbinamento: `datoreLavoro.partitaIva` se la P.IVA inserita e' tra gli identificativi dell'azienda
     trovata, altrimenti `datoreLavoro.codiceFiscale` (anche un CF numerico uguale a una P.IVA in archivio),
     `datoreLavoro.posizioneInps` per la matricola INPS, `datoreLavoro.ragioneSociale` per la ragione sociale
     normalizzata ("… è già in archivio: seleziona l'azienda dall'elenco").
     - Le regole sono quelle del censimento: `ConsultazioneAnagrafica.aziendaRiconosciuta` e' una prova a vuoto di
       `trovaOCreaAzienda` con `AbbinamentoAzienda`, senza una seconda implementazione del riconoscimento.
     - Il client rispecchia solo codice fiscale della persona e P.IVA/codice fiscale dell'azienda (confronto
       incrociato): matricola INPS e ragione sociale hanno normalizzazioni troppo ricche da replicare senza falsi
       allarmi e compaiono dopo la verifica.
     - Limite accettato: un'azienda davvero diversa che il censimento non distingue da una in archivio (stessa
       ragione sociale normalizzata, nessun identificativo in contrasto) non si puo' creare a mano, come non si
       creerebbe leggendo il PDF.
153. **Elenco aziende ed etichette sull'intero workspace**: `GET /api/v1/aziende` (`AziendaSelezionabile`: dati
     anagrafici, indirizzo, codice azienda ed etichetta) ordinato per ragione sociale senza distinzione tra maiuscole
     e minuscole, poi per id; vuoto prima dei caricamenti. L'etichetta usa la stessa regola delle aziende di una
     persona (`Etichette.aziende`) ma calcolata su tutte le aziende del workspace. Per le regole del censimento due
     omonime differiscono sempre su almeno uno tra P.IVA, codice fiscale e posizione INPS, quindi le etichette sono
     distinte; senza alcun identificativo (impossibile passando dal censimento) resta la sola ragione sociale.
     `PersonaSintesi` ha in piu' `dataNascita` (additivo): l'elenco delle persone gia' caricato basta a precompilare,
     senza un endpoint di dettaglio.
154. **Precompilazione alla scelta**: i dati anagrafici vengono dall'archivio; gli attributi dell'azienda scelta
     (indirizzo, codice azienda) e, con persona e azienda scelte e un rapporto tra loro, i dati del rapporto e del
     cedolino di riferimento piu' vicino al periodo riempiono i campi successivi, solo se vuoti (prima fonte vince:
     azienda, rapporto, cedolino). Cambiando una scelta i campi riempiti in automatico dalla scelta precedente si
     svuotano e si riempiono di nuovo, quelli modificati dall'utente restano; il periodo (anche quello del mese
     associato) non viene mai ricopiato. L'interruttore dei buoni pasto copiato resta acceso se numero, valore unitario
     o importo hanno un valore dell'utente (altrimenti quei valori sparirebbero dal modulo e dalla richiesta). Aprendo
     il modulo da un file associato a un mese persona e azienda sono gia' scelte. Il pannello "Copia dati da…" e'
     eliminato: il suo compito lo fa la scelta.
     - **Il riferimento segue il periodo**: indicando o cambiando il periodo dopo la scelta, il cedolino di
       riferimento si rilegge; se cambia (o cambia il criterio "stesso anno" per residui dei ratei e fondo TFR) i dati
       copiati si sostituiscono come al cambio di scelta. Il risultato non dipende dall'ordine di compilazione; senza
       cambio del riferimento nulla si muove (anche digitando l'anno cifra per cifra).
155. **Esito della verifica**: `VerificaCedolinoManuale` ha in piu' `nuovaAzienda` (`aziendaId` assente: il ricontrollo
     garantisce che nessuna azienda in archivio venga riconosciuta) e il pannello mostra dipendente, azienda e rapporto
     presenti o nuovi. Avvisi di contesto, in quest'ordine: periodo diverso dal mese associato; "Persona o azienda
     diverse da quelle del mese associato al file (…)" quando il cedolino andrebbe a un rapporto diverso da quello del
     mese associato; nuova persona; "Verrà creata una nuova azienda: controlla ragione sociale, partita IVA e codice
     fiscale"; "Verrà creato un nuovo rapporto di lavoro: la persona scelta non ha ancora cedolini con l'azienda
     scelta" solo quando persona e azienda sono gia' in archivio. Eliminato l'avviso sul nome diverso da quello in
     archivio, che non puo' piu' presentarsi.

## Elenco dei CCNL ampliato

156. **Elenco dei CCNL ampliato ai contratti piu' diffusi** (changelog `250-ccnl-ampliamento.sql`: `250` aggiorna i CCNL
     gia' presenti, `251` inserisce i nuovi). Oltre ai nove CCNL del seed e a `ALTRO_13`/`ALTRO_14` (invariati): Terziario
     Confesercenti, Distribuzione moderna organizzata, Distribuzione cooperativa, Farmacie private, Dirigenti del
     terziario, Metalmeccanici artigianato, Alimentari, Tessile-abbigliamento-moda, Gomma-plastica e Legno-arredamento
     industria, Dirigenti industria, Multiservizi, Cooperative sociali, Vigilanza privata, Logistica e trasporto merci,
     Igiene ambientale, Autoferrotranvieri, Lavoro domestico, Sanita' privata AIOP, Assicurazioni ANIA, Elettrici,
     Gas-acqua, Edilizia artigianato e PMI (32 CCNL in tutto). I codici esistenti non cambiano (li referenzia
     `rapporto_lavoro`); il mock del frontend (`CCNL_MOCK`) rispecchia l'intera tabella.
     - **Regola della certezza**: un valore (mensilita', periodicita' e massimo degli scatti, mese della quattordicesima,
       codice CNEL) e' impostato solo se confermato dal testo contrattuale o da una fonte autorevole (archivio CNEL o
       database CNEL-INPS, parti firmatarie, PDF ufficiale del contratto) oppure da due fonti giuslavoristiche
       indipendenti e concordi; le fonti sono citate nei commenti del changelog. Quando la regola dipende da categoria,
       sezione, data di assunzione o ha eccezioni che il modello a un solo numero non rappresenta, il valore e' NULL e
       `note_verifica` spiega perche' (un valore sbagliato e' peggio di un NULL: genera falsi allarmi). Un termine di
       pagamento ("entro il 15 luglio", "tra il 15 giugno e il 15 luglio") non fissa il mese del cedolino: NULL. Ogni
       CCNL con regole sugli scatti o mese della quattordicesima NULL ha una nota di verifica (controllato da
       `CatalogoCcnlIntegrationTest`); un codice CNEL NULL vuol dire solo "non verificato" (decisione 48) e non
       sempre ha una nota (es. Autoferrotranvieri, con tutti gli altri valori certi). Una nota rende "da verificare"
       anche il mese della quattordicesima impostato (`RegolaQuattordicesimaMancante`: la quattordicesima mancante si
       segnala solo ad anno concluso): vale per Distribuzione cooperativa, Dirigenti del terziario, Turismo, Elettrici
       e Gas-acqua.
     - **Correzioni del seed**: Telecomunicazioni ha 13 mensilita' (non 14) e 7 scatti biennali; Studi professionali
       8 scatti triennali; Metalmeccanici PMI 5 scatti biennali; Turismo quattordicesima con la retribuzione di luglio
       (scatti NULL: la voce raggruppa Federalberghi, triennali, e Fipe, quadriennali). Cooperative sociali a 13
       mensilita': la mezza quattordicesima dal 2025 non e' rappresentabile e non viene controllata.
     - **Codici CNEL** valorizzati solo dove verificati (supera la decisione 48 "nessun codice valorizzato"): un codice
       che il riconoscitore non accetta (non lettera + 3 cifre, es. H05Y, HV17) o in conflitto tra fonti resta NULL con
       nota.
     - **Descrizioni riconoscibili**: "<Settore> - <dettaglio> (<parti firmatarie>)", con le lettere accentate. Ogni
       descrizione, scritta come testo del CCNL (anche come suggerimento dell'inserimento a mano), riconosce il proprio
       CCNL: per questo l'edilizia industriale e' "Edilizia - Industria (ANCE, Legacoop, Confcooperative, AGCI)" senza la
       parola "cooperative", che esclude il CCNL per prudenza.
     - **Riconoscimento sempre conservativo tra contratti fratelli**: ogni CCNL esclude le sigle e le parole dei fratelli
       (artigianato, PMI/Confapi/Confimi, cooperative, dirigenti, contratti di sigle minori). Dove si esclude "pmi" si
       esclude anche "p m i" (il testo "P.M.I." normalizzato); gli Elettrici escludono "gomma" e "cavi elettrici" (CCNL
       gomma, cavi elettrici ed affini, anche nelle versioni PMI); il Multiservizi esclude anche "coop sociali". Ora sono riconosciuti testi
       che prima non lo erano perche' il fratello non era in tabella: "Commercio Confesercenti", "Metalmeccanica
       artigianato", "Dirigenti commercio", "Turismo Confcommercio" (il Commercio esclude "turismo", il Turismo esclude
       "commercio" e "terziario", quindi "Commercio e turismo" resta non riconosciuto). "p.ind" (piccola industria)
       esclude il metalmeccanico industria senza bastare per la PMI Confapi (esiste anche Confimi): "Metalmeccanici imp.
       (p.ind)" non e' piu' riconosciuto (supera l'esempio della decisione 47), con "CNEL C018" diventa
       `METALMECCANICO_PMI`.
     - **Cassa Edile**: `ccnl-tredicesima-cassa-edile` comprende anche `EDILIZIA_ARTIGIANATO` (tredicesima degli operai
       pagata dalla Cassa Edile come nell'industria).

## Cedolini protetti da password

157. **[utente] Il PDF protetto si archivia senza protezione** (supera le decisioni 67, 71 e 142 su "a DB si salva
     l'originale"): quando una password apre un PDF cifrato, in `cedolino_pdf` va la copia senza protezione prodotta da
     PDFBox (`setAllSecurityToBeRemoved`: stesse pagine e stesso contenuto, file riscritto), la stessa che riceve il
     lettore. Vale anche per i PDF cifrati con la sola password del proprietario (`LettorePdf.DocumentoPdf.decifrato`).
     L'hash del cedolino resta quello del PDF caricato: ricaricare lo stesso file protetto da' ancora "gia' caricato".
     Un file in errore aperto da una password (es. layout non riconosciuto dopo la password giusta) conserva in
     `caricamento_pdf` la copia senza protezione, anche al posto del PDF protetto conservato da un tentativo precedente
     (`CaricamentiRepository.salvaPdfSbloccato`). L'eventuale firma digitale non e' piu' valida nella copia (accettato
     dall'utente: nei cedolini e' rara). La colonna `cedolino_pdf.protetto` (changelog `340`) vale false per i PDF senza
     password di apertura, true per quelli che nessuna password nota apre, null se da verificare (archiviati prima di
     questa versione e inseriti a mano dal PDF conservato, che puo' essere ancora protetto).
158. **[utente] Password ricordata cifrata**: la password indicata dall'utente (caricamento o nuova lettura) che apre un
     PDF (`DocumentoPdf.passwordUtenteValida`, anche nella versione senza spazi ai lati) viene salvata in `password_pdf`
     (changelog `341`) cifrata con AES-256-GCM (`adapter.cifratura.AesGcmCifraturaPassword`: versione, nonce casuale di
     12 byte, testo cifrato e tag; l'id del workspace come dati autenticati) e non piu' due volte (confronto in chiaro
     nella transazione serializzata del workspace). Le password configurate e i codici fiscali non si salvano: si
     provano comunque. Ordine dei tentativi (aggiorna la decisione 67): nessuna password, password dell'utente,
     configurate, ricordate, codici fiscali (ricordate e codici fiscali letti solo se servono; i valori di 16 lettere e
     cifre si provano anche in maiuscolo e minuscolo, gli altri come sono). Le password ricordate valgono per tutto il
     workspace, non per azienda: un PDF protetto non dice di quale azienda e' finche' non si apre.
     - **Chiave**: `salary-manager.password-pdf.chiave` (`SAM_PASSWORD_PDF_CHIAVE`, base64 di 32 byte) o, se vuota, il
       file `salary-manager.password-pdf.file-chiave` (default `${salary-manager.database.path}-chiave-password-pdf.key`,
       cioe' accanto al database e nel volume con Docker), creato al primo uso con una chiave casuale e permessi 600 dove
       possibile. Chiave mancante o non valida: errore chiaro al primo uso, non all'avvio. Una password non piu'
       decifrabile (chiave cambiata) viene ignorata con un avviso nei log (solo il numero).
     - **Nei test** la chiave e' fissa in `application-test.yml`: nessun file creato.
159. **[utente] Nuova lettura di tutti i file protetti in errore**: `POST /api/v1/caricamenti/in-errore/rilettura-protetti`
     (`CaricamentiInErrore.rileggiProtetti`) registra in un nuovo lotto un tentativo per ogni file in errore con motivo
     `PDF_PROTETTO`, PDF conservato e nessun tentativo in corso (204 se nessuno), senza password: valgono quelle
     ricordate. Il frontend la propone alla fine della nuova lettura di un file protetto fatta con una password, se il
     file non e' tornato in errore per la password e restano altri file protetti in errore ("Prova anche sugli altri" /
     "Solo questo"); in entrambi i casi la password resta ricordata. Si provano tutti i file protetti e non solo quelli
     "dell'azienda" perche' l'azienda di un PDF chiuso non e' nota; quelli che non si aprono restano in errore.
160. **[utente] Sblocco dei PDF gia' archiviati** (`SbloccoPdfArchiviati`): all'avvio (`ApplicationReadyEvent`, tutti i
     workspace), quando si ricorda una nuova password e dopo un inserimento a mano (un workspace) i PDF con `protetto`
     true o null si aprono con le password configurate, ricordate e i codici fiscali delle persone del workspace; quelli
     aperti sono sostituiti dalla copia senza protezione (hash invariato). Un solo sblocco alla volta su un thread
     virtuale, senza bloccare richieste o avvio; un PDF illeggibile resta da verificare. Nei log solo id e conteggi.

## Stima della liquidazione ("Se andassi via oggi")

161. **[utente] Sezione "Se andassi via oggi"** nel riepilogo del rapporto: stima di quanto si riceverebbe lasciando
     l'azienda, con le voci singole e il totale (lordo, contributi, imposte, netto). Esposta per il rapporto attuale e
     per l'ultimo rapporto della persona (primo nell'ordine dell'anagrafica) finche' un cedolino non liquida il TFR;
     per gli altri rapporti `liquidazione` e' null. Calcolata nel ricalcolo (`CalcoloLiquidazione`, deterministica) e
     salvata in `riepilogo_rapporto` (colonne `liquidazione_*`) e `riepilogo_liquidazione_voce` (changelog
     `420-liquidazione.sql`).
162. **[utente] Data della stima = ultimo cedolino, senza proiezioni**: fine del mese dell'ultima mensilita' ordinaria,
     o la data di cessazione se nota. Il TFR e' quello calcolato dai cedolini, non la proiezione "ad oggi" del blocco
     TFR (che resta com'e').
163. **[utente] TFR con la tassazione separata dell'azienda** (art. 19 TUIR): reddito di riferimento = (TFR in azienda al
     netto delle rivalutazioni gia' assoggettate a imposta sostitutiva + quote versate ai fondi + anticipi) x 12 / mesi
     di anzianita'; aliquota media = IRPEF lorda del reddito di riferimento / reddito di riferimento, con gli scaglioni
     dell'anno della data di riferimento (`salary-manager.liquidazione.irpef`, per anno di entrata in vigore: 2007,
     2022, 2024, 2026 = 23% / 33% / 43% dalla L. 199/2025); nessuna detrazione, nessun contributo. Le rivalutazioni
     nette escluse sono quelle degli anni con dati TFR sui cedolini (dicembre se stampata, altrimenti l'ultima
     dell'anno; imposta stampata o 17%); quelle degli anni senza dati, o comprese in un fondo iniziale, restano
     imponibili (stima prudente, detto in nota). Gli anticipi concorrono solo all'aliquota; la riliquidazione
     dell'Agenzia delle Entrate sull'aliquota media dei cinque anni precedenti non e' stimata. Le quote versate ai
     fondi pensione non sono nel totale (le liquida il fondo).
164. **[utente] Trattenute su ferie, permessi e ratei**: contributi INPS a carico del dipendente con l'aliquota stampata
     sull'ultima mensilita' ordinaria (somma delle percentuali delle trattenute previdenziali INPS/IVS/FPLD/CIGS/FIS/FAP,
     accettata tra 5% e 15%; i fondi pensione e sanitari sono esclusi), altrimenti
     `salary-manager.liquidazione.aliquota-contributi-default` (9,19%); IRPEF all'aliquota marginale della RAL al netto
     dei contributi. Addizionali, detrazioni, trattamento integrativo e conguaglio di fine anno non sono stimati (detto
     in nota). Senza RAL imposte e netto di queste voci sono null e il totale e' parziale (`completa = false`).
165. **Voci**: ferie, permessi, ROL, ex festivita' e banca ore dai saldi del riepilogo ratei (un'unita' per tipo, quella
     con il valore stimato; un saldo negativo e' un debito e riduce il totale; saldo 0 omesso). Ratei di tredicesima:
     mesi dall'inizio dell'anno (o dall'assunzione, o dal mese dopo l'ultimo pagamento nell'anno: tredicesima,
     dicembre o altro cedolino che la contiene) al mese di riferimento x lordo mensile / 12; alla cessazione il mese
     conta se lavorato per almeno 15 giorni. Ratei di quattordicesima solo per i CCNL a 14 mensilita': periodo di 12
     mesi che inizia nel mese di pagamento del CCNL (luglio: da luglio a giugno, come nel Commercio), pagamenti
     considerati solo se successivi all'inizio del periodo; mese ignoto = voce non stimabile. Preavviso considerato
     lavorato: nessuna indennita' ne' trattenuta (il preavviso e' esposto nel riepilogo dell'azienda, a parte: 166-169).

## Preavviso nel riepilogo dell'azienda

166. **[utente] Preavviso nel riepilogo, non nella liquidazione**: blocco "Preavviso" del riepilogo, solo per il
     rapporto attuale in corso (nessuna cessazione, TFR non liquidato), con i termini per dimissioni e licenziamento e
     l'indennita' sostitutiva se il preavviso non e' lavorato (entrambe le versioni richieste dall'utente). La stima
     "Se andassi via oggi" resta con il preavviso lavorato (165).
167. **Termini solo se verificati su due fonti**: tabella `ccnl_preavviso` (changelog `260-ccnl-preavviso.sql`, fonti in
     commento) per CCNL, gruppo di livelli e fascia di anzianita' (`oltre_anni` 0, 5, 10), in mesi e giorni di
     calendario. Verificati: Commercio (artt. 247 e 254), Metalmeccanico industria (classificazione 2021 e vecchie
     categorie), Studi professionali (art. 146), Turismo. Gli altri CCNL non hanno righe e il blocco dice "non ancora
     verificati" (es. Telecomunicazioni: una sola fonte dopo il rinnovo 2025; Logistica: termini diversi per impiegati,
     operai e viaggianti, non distinguibili dal livello; Commercio Confesercenti: nessuna fonte). Come per i parametri
     CCNL (decisioni precedenti), un valore non verificato non e' inserito.
168. **Livello e fascia**: il livello del rapporto (stampato sui cedolini) e' normalizzato (maiuscole, "quadro" = Q,
     "super" = S, numeri romani in cifre, senza "livello"/"categoria") e cercato nei gruppi del CCNL; non trovato =
     "livello non riconosciuto". Fascia = la soglia piu' alta superata dall'anzianita' all'ultimo cedolino (stessa
     anzianita' del riepilogo, nessuna proiezione come per 162): "oltre 5 anni" vale da 5 anni e 1 mese. Il blocco
     indica da quale mese si passa alla fascia successiva.
169. **Indennita' sostitutiva stimata e lorda**: lordo mensile x mensilita' / 12 (ratei delle mensilita' aggiuntive) per
     ogni mese di preavviso, / 30 per ogni giorno di calendario. Contributi e imposte (tassazione separata) non sono
     stimati; voci variabili escluse. Calcolata in lettura (`CalcoloPreavviso`, da CCNL, livello e riepilogo salvato),
     senza colonne nuove nel riepilogo: segue subito la tabella dei termini.
