# Architettura di Salary Manager

## Panoramica

Monolite modulare **Spring Boot 4.0.8 + Spring Modulith 2.0.8** su **Java 25**, con architettura esagonale dentro
ogni modulo. Persistenza **H2 su file in MODE=PostgreSQL**, schema gestito da **Liquibase** (formatted SQL,
DDL PostgreSQL-compatibili), accesso ai dati con **jOOQ** (codice generato dai changelog Liquibase).
Frontend **SPA separata** (React + TypeScript + Vite + Mantine + TanStack Query) che usa solo le API REST
`/api/v1` descritte in [`openapi.yaml`](openapi.yaml).

```
salary-manager/
├── backend/            progetto Maven (mvnw / mvnw.cmd), Dockerfile
│   └── src/main/java/com/fcusumano/sam/
│       ├── SalaryManagerApplication.java
│       ├── shared/        kernel condiviso (modulo OPEN) + jOOQ generato (shared.db.jooq)
│       ├── ccnl/          tabella CCNL
│       ├── anagrafica/    persone, aziende, rapporti di lavoro
│       ├── cedolini/      caricamento, decifratura PDF, lettori (locale, Claude, fixture), archivio
│       ├── calcoli/       riepiloghi derivati
│       ├── segnalazioni/  regole di anomalia
│       └── web/           REST, DTO, errori, sicurezza, OpenAPI
├── frontend/           SPA (Vite), Dockerfile + nginx.conf
├── docs/               openapi.yaml, architecture.md, decisions.md, sviluppo.md
├── docker-compose.yml  backend + frontend, volume del database
└── samples/            cedolini reali (ignorata da git)
```

## Moduli e dipendenze

| Modulo         | Dipendenze consentite                                   | API pubblica (package radice) |
|----------------|---------------------------------------------------------|-------------------------------|
| `shared` (OPEN)| -                                                       | `WorkspaceId`, `CurrentWorkspaceProvider`, `ContestoWorkspace`, `Competenza` (sealed: `Mensilita`, `FineRapporto`), `TipoMensilita`, `UnitaMisura`, `Importi`, `Periodi`, `RisorsaNonTrovataException`, `OperazioneNonConsentitaException`, `config.VariabiliAmbiente`, jOOQ generato |
| `ccnl`         | shared                                                  | `CatalogoCcnl`, `Ccnl` |
| `anagrafica`   | ccnl, shared                                            | `CensimentoAnagrafica`, `ConsultazioneAnagrafica`, `GestioneRapporti`, id/record, eventi `CcnlRapportoModificato`, `RapportoLavoroEliminato` |
| `cedolini`     | anagrafica, ccnl, shared                                | `CaricamentoCedolini`, `ArchivioCedolini`, `CedolinoNormalizzato` e record collegati, eventi `CedolinoAcquisito`, `CedolinoEliminato` |
| `calcoli`      | cedolini, anagrafica, ccnl, shared                      | `CalcoliRapporto`, `RiepilogoRapporto` |
| `segnalazioni` | cedolini, anagrafica, ccnl, shared                      | `SegnalazioniRapporto`, `Segnalazione` |
| `web`          | tutti                                                   | nessuna (nessuno dipende da web) |

```
            web
   ┌────────┼─────────────┐
   ▼        ▼             ▼
calcoli  segnalazioni     │
   └──┬─────┘             │
      ▼                   │
   cedolini ◄─────────────┤
      ▼                   │
  anagrafica ◄────────────┤
      ▼                   │
     ccnl ◄───────────────┘
      ▼
    shared (OPEN, usabile da tutti)
```

Nessun ciclo. Le regole sono verificate da `ModularityTests` (`ApplicationModules.verify()`), i diagrammi
generati in `backend/target/spring-modulith-docs`.

### Struttura interna di un modulo (esagonale)

```
<modulo>/
├── package-info.java          @ApplicationModule(displayName, allowedDependencies)
├── *.java                     API pubblica: interfacce, record, enum, eventi
├── domain/                    logica pura (niente Spring, jOOQ, SDK Anthropic)
├── application/               servizi che implementano l'API, listener di eventi, porte
├── adapter/persistence/       repository jOOQ
├── adapter/...                altri adapter (es. cedolini.adapter.locale, .ai, .pdf, .fixture)
└── config/                    @ConfigurationProperties e @Bean del modulo
```

Vincoli verificati da `ArchitectureTests` (ArchUnit):

- `..domain..` non dipende da `org.springframework`, `org.jooq`, `com.anthropic`;
- jOOQ solo in `..adapter.persistence..`, `..config..` e `shared.db..`;
- SDK Anthropic solo in `cedolini.adapter.ai..`;
- PDFBox solo in `cedolini.adapter.pdf..`, `cedolini.adapter.locale..` e `cedolini.adapter.fixture..` (il resto usa
  le porte `LettorePdf` e `LettoreCedolini`);
- ogni modulo usa solo le classi jOOQ delle proprie tabelle; ogni tabella ha un modulo proprietario.

## Ownership delle tabelle

| Modulo         | Changelog (range)            | Tabelle |
|----------------|------------------------------|---------|
| `shared`       | `changes/0xx-*.sql`          | `workspace` |
| `anagrafica`   | `changes/1xx-*.sql`          | `persona`, `azienda`, `rapporto_lavoro` |
| `ccnl`         | `changes/2xx-*.sql`          | `ccnl` (+ FK `rapporto_lavoro.ccnl_codice` definita in `220-ccnl-vincoli.sql`) |
| `cedolini`     | `changes/3xx-*.sql`          | `cedolino`, `cedolino_pdf`, `cedolino_voce`, `cedolino_elemento_retributivo`, `cedolino_rateo`, `cedolino_tfr`, `cedolino_dati`, `caricamento` |
| `calcoli`      | `changes/4xx-*.sql`          | `riepilogo_rapporto`, `riepilogo_rateo`, `indicatore_mensilita` |
| `segnalazioni` | `changes/5xx-*.sql`          | `segnalazione` |

Colonne aggiunte dai changelog successivi ai primi di ogni modulo (sempre additivi, mai modifiche ai changeset gia'
applicati):

| Changelog                         | Colonne                                                                                       |
|-----------------------------------|-----------------------------------------------------------------------------------------------|
| `110-azienda-riferimento.sql`     | `azienda.riferimento_dati` (periodo del cedolino da cui vengono gli attributi aggiornabili)    |
| `230-ccnl-riconoscimento.sql`     | `ccnl.codice_cnel` (univoco, nullable), `ccnl.parole_escluse`; parole chiave per parole intere; quattordicesima non certa a NULL |
| `240-ccnl-descrizioni.sql`        | (solo dati) descrizioni e note di verifica dei CCNL con le lettere accentate                  |
| `310-cedolino-tipo-dichiarato.sql`| `cedolino.tipo_dichiarato` (tipo dichiarato dall'estrazione, base della riclassificazione)    |
| `320-cedolino-avvisi.sql`         | `cedolino.avvisi_estrazione` (avvisi dei controlli e note del modello, separati da a capo)     |
| `410-calcoli-stime.sql`           | `riepilogo_rapporto`: fonte e stima degli scatti attuali, quota TFR stimata, TFR liquidato (importo e netto); `riepilogo_rateo.nota`; `indicatore_mensilita.ordine` |
| `510-segnalazione-ordine.sql`     | `segnalazione.ordine` (ordinamento deterministico) e indice `(rapporto_id, ordine)`            |

Tutte le tabelle di dominio hanno `workspace_id` (FK verso `workspace`; workspace di default `id = 1`). La tabella
`ccnl` e' un dato di riferimento globale. Le tabelle derivate (`riepilogo_*`, `indicatore_mensilita`, `segnalazione`)
e le tabelle figlie di `cedolino` hanno FK `ON DELETE CASCADE`: eliminare un cedolino o un rapporto rimuove i dati
che ne dipendono. Nessun dato bancario in nessuna tabella.

Un modulo non legge ne' scrive le tabelle di un altro: usa le sue API.

## Flusso di caricamento

```
POST /api/v1/caricamenti (multipart: N file + password facoltativa del lotto)
  └─ web ─► CaricamentoCedolini.avvia(files, password)
        ├─ per ogni file: hash SHA-256 del PDF ORIGINALE + riga `caricamento` IN_CODA (lotto comune),
        │  in una transazione breve committata subito ─► risposta 202 immediata
        └─ elaborazione asincrona (un thread virtuale per file, nel ContestoWorkspace del lotto; la password
           resta solo in memoria):
             1. hash gia' presente nel workspace                         ─► DUPLICATO (nessuna lettura)
                (file identici nello stesso lotto: elaborati uno dopo l'altro, il secondo risulta DUPLICATO)
             2. IN_LETTURA (al massimo salary-manager.letture-contemporanee letture contemporanee):
                a. LettorePdf.apri: intestazione %PDF-, flussi compressi entro salary-manager.pdf.limite-
                   decompressione, pagine, limiti dell'API; se cifrato prova in ordine
                   nessuna password, password del lotto, salary-manager.pdf.password-candidate, codici fiscali
                   delle persone censite (maiuscoli e minuscoli). Nessuna valida: in un lotto di piu' file il
                   caricamento torna IN_CODA e viene riprovato una volta a fine lotto (quando le persone degli
                   altri file sono censite); altrimenti, o al secondo tentativo, ERRORE
                   "PDF protetto da password: indica la password e ricarica".
                   Per un PDF cifrato produce una COPIA DECIFRATA (PDFBox, setAllSecurityToBeRemoved).
                b. LettoreCedolini.estrai(copia per la lettura) ─► CedolinoEstratto validato
                   (lettore scelto con salary-manager.lettore: locale, claude o fixture; PDF senza testo o
                   layout non riconosciuto dal lettore locale, errore o rifiuto di Claude, piu' cedolini nel
                   documento ─► ERRORE)
             3. transazione serializzata per workspace (ScrittureSerializzate: lock per workspace preso prima
                della transazione e rilasciato dopo il commit, cosi' piu' file della stessa persona nuova non
                creano doppioni anche con salary-manager.letture-contemporanee > 1):
                0. ricontrollo dell'hash dentro il lock                    ─► DUPLICATO
                a. CensimentoAnagrafica.trovaOCreaPersona (CF)
                b. CensimentoAnagrafica.trovaOCreaAzienda (CF/P.IVA ─► posizione INPS ─► ragione sociale normalizzata)
                c. CatalogoCcnl.riconosci(testo CCNL / codice CNEL, se stampati) + CensimentoAnagrafica.trovaOCreaRapporto
                d. competenza: ORDINARIA mese 1..12, TREDICESIMA 13, QUATTORDICESIMA 14, FINE_RAPPORTO (anche le
                   mensilita' con periodo successivo alla cessazione) con progressivo TFRnn per periodo; poi
                   riclassificazione completa dei cedolini del rapporto rispetto alla cessazione attuale
                   (ClassificazioneRapporto: mensilita' <-> fine rapporto, progressivi e nomi file)
                e. unicita' (rapporto + anno + mese, o rapporto + periodo per FINE_RAPPORTO):
                   gia' presente con contenuto equivalente ─► DUPLICATO
                   gia' presente con contenuto diverso / tipo AGGIUNTIVA ─► AGGIUNTIVO_NON_GESTITO
                   (uno scarto annulla la transazione: nessuna anagrafica resta creata per lui)
                f. salvataggio cedolino + figlie + PDF ORIGINALE (nome da NomeFileCedolinoStrategy)
                g. CensimentoAnagrafica.aggiornaEstremiCedolini
                h. publish CedolinoAcquisito ─► (sincrono, stessa transazione)
                     calcoli.RicalcoloRiepilogoListener      ─► CalcoliRapporto.ricalcola
                     segnalazioni.RicalcoloSegnalazioniListener ─► SegnalazioniRapporto.ricalcola
                i. caricamento ACQUISITO (cedolinoId)
                (un'eccezione in un passo qualsiasi, ricalcoli compresi, annulla tutto ─► ERRORE)
GET /api/v1/caricamenti/lotti/{lotto}  (polling della UI finche' completato)
```

Il lotto e' completato quando nessun caricamento e' IN_CODA o IN_LETTURA. Per ACQUISITO, DUPLICATO e
AGGIUNTIVO_NON_GESTITO ogni caricamento espone anche `rapportoId` e `personaId` del cedolino salvato o di quello gia'
presente: il repository di `cedolini` ricava il rapporto con una join sulla propria tabella `cedolino`, il servizio
risolve la persona tramite `anagrafica.ConsultazioneAnagrafica` (una lettura per rapporto distinto), cosi' la UI
seleziona la persona giusta senza altre chiamate. Se quel cedolino viene poi eliminato i due campi tornano `null`.

Al riavvio, i caricamenti rimasti IN_CODA/IN_LETTURA (elaborazione in memoria interrotta) vengono marcati ERRORE
prima che il server accetti richieste (`RipristinoCaricamentiInterrotti`).

### Lettori dei cedolini

La porta `cedolini.application.estrazione.LettoreCedolini` (`estrai(byte[] pdf)` ─► `RisultatoEstrazione`: cedolino
validato, JSON originale, modello, avvisi) ha tre implementazioni; `salary-manager.lettore` (`LettoreProperties`,
variabile `SAM_LETTORE`) ne attiva esattamente una con `@ConditionalOnProperty` (un valore diverso blocca l'avvio):

| Valore              | Adapter                                   | Modello registrato (`cedolino.modello_estrazione`) |
|---------------------|-------------------------------------------|----------------------------------------------------|
| `locale` (default)  | `adapter.locale.LettoreLocaleCedolini`    | `lettore-locale-1/<id del layout>`                 |
| `claude`            | `adapter.ai.ClaudeLettoreCedolini`        | il modello Claude (`salary-manager.ai.model`)      |
| `fixture`           | `adapter.fixture.FixtureLettoreCedolini`  | `fixture`                                          |

Tutti producono il JSON dello schema `prompts/cedolino-schema-v1.json` e lo passano alla stessa
`InterpreteRispostaEstrazione` (normalizzazione, mascheramento degli IBAN sfuggiti, `ValidatoreCedolinoEstratto`):
validazione, avvisi e confidenza seguono le stesse regole qualunque sia il lettore, e il resto dell'applicazione non
sa quale lettore e' stato usato. Le letture contemporanee sono limitate da `salary-manager.letture-contemporanee`
(qualunque lettore); il lettore Claude limita a parte le richieste all'API (`salary-manager.ai.concurrency`).

**Lettore locale** (`cedolini.adapter.locale`), gratuito, offline e deterministico:

```
PDF (copia decifrata)
  └─ TestoPdf (PDFBox, PDFTextStripper) ─► DocumentoTesto: pagine ─► righe ─► parole con coordinate
       (le parole si separano sugli spazi stampati e sui salti tra caratteri; righe per linea di base)
       testo senza caratteri alfanumerici sufficienti ─► PDF_SENZA_TESTO
  └─ LayoutCedolino.riconosce (primo dei LAYOUT in ordine)  nessuno ─► LAYOUT_NON_RICONOSCIUTO
       LayoutJobSistemi ("job-sistemi"), LayoutZucchetti ("zucchetti"), LayoutModuloM39320 ("modulo-m39320")
  └─ LayoutCedolino.leggi ─► CedolinoLetto (campi null se non trovati, mai dedotti)
       Zone: valore a destra o sotto un'etichetta, aree, colonne; Colonne: assegnazione degli importi alle colonne
       per posizione; Valori: importi, date, mesi, codici fiscali; Periodo: periodo stampato ed elementi fissi;
       Classificazione: categorie delle voci, ratei, unita', tipo (tredicesima, quattordicesima, fine rapporto),
       cognome/nome con il codice fiscale
       periodo, codice fiscale del dipendente o netto assenti ─► LAYOUT_NON_RICONOSCIUTO
  └─ CedolinoLetto.json ─► InterpreteRispostaEstrazione.interpreta(json, "lettore-locale-1/<id>")
```

- Le posizioni sono relative alle etichette stampate (JOB Sistemi, Zucchetti), o alle coordinate del foglio scalate
  sul formato della pagina per i moduli prestampati le cui etichette non sono nel testo (M39320).
- Controlli interni (somma delle voci contro i totali stampati, TFR spettante contro fondo piu' quota) e sezioni
  attese ma assenti diventano avvisi senza valori e riducono la confidenza (base 0,95; fattori in `CedolinoLetto`);
  i controlli comuni (netto, ratei, date, codici fiscali) restano quelli del validatore.
- I dati bancari non vengono mai letti (nessun campo li raccoglie); nei log solo l'id del layout e la classe di
  un'eccezione, mai valori.
- I messaggi di errore (`EstrazioneCedolinoException` con motivo `PDF_SENZA_TESTO` o `LAYOUT_NON_RICONOSCIUTO`)
  suggeriscono il lettore Claude. Come aggiungere un layout:
  [`sviluppo.md`](sviluppo.md#lettore-locale-aggiungere-un-layout).

**Lettore Claude** (`cedolini.adapter.ai`): invia il PDF all'API Anthropic come blocco `document` con structured
outputs (schema JSON, prompt in `prompts/`); legge anche le scansioni. A pagamento, chiave `ANTHROPIC_API_KEY`.

**Lettore a fixture** (`cedolini.adapter.fixture`): associa al PDF un JSON di `fixtures/cedolini/` (nome del file,
hash o marcatore `Fixture: <nome>` nel testo); per test, modalita' demo e suite end-to-end.

### PDF protetti, scansioni e dati sensibili

- **Originale e copia**: a DB (`cedolino_pdf`) si conserva sempre il PDF originale caricato, anche se cifrato; l'hash
  per il riconoscimento dei duplicati e' calcolato sull'originale. La copia decifrata esiste solo in memoria per il
  lettore (con il lettore Claude, per la richiesta all'API). Le password non sono salvate ne' registrate nei log.
- **Scansioni**: alcuni cedolini sono solo immagini. Il lettore locale li rifiuta (`PDF_SENZA_TESTO`, con il
  suggerimento del lettore Claude); il lettore Claude riceve il PDF come blocco `document` e legge anche visivamente
  le pagine, senza dipendere dal testo estratto localmente.
- **CCNL**: alcuni layout stampano il contratto (es. "CONTRATTO DI LAVORO Metalmeccanici ...", talvolta con il codice
  CNEL "C018"), altri no. Il riconoscimento e' conservativo (parole intere, frasi escluse, codice CNEL coerente) e
  restituisce un CCNL solo se univoco; altrimenti il rapporto resta "CCNL da indicare" e lo sceglie l'utente.
- **Dati bancari**: IBAN, ABI/CAB e conto non sono nello schema di estrazione, il lettore locale non li legge, il
  prompt di Claude vieta di riportarli in qualunque campo e non esistono colonne per salvarli; un IBAN di qualunque
  paese (lunghezza del paese e verifica modulo 97) sfuggito in un testo libero viene mascherato prima del
  salvataggio.
- **PDF malevoli**: i flussi compressi vengono decompressi in streaming e scartati prima dell'apertura con PDFBox
  (limite per flusso configurabile); un `OutOfMemoryError` o `StackOverflowError` porta il singolo caricamento in
  ERRORE senza bloccare il lotto.
- **Nomi host**: senza login l'applicazione e' pubblicata solo su localhost (il backend avviato con `mvnw` ascolta su
  `127.0.0.1`, Docker Compose pubblica le porte su `127.0.0.1`); il firewall di Spring Security ammette solo i nomi
  host configurati (`SAM_ALLOWED_HOSTS`), contro il DNS rebinding.

## Eventi

| Evento                     | Pubblicato da | Quando                                                   | Ascoltato da |
|----------------------------|---------------|----------------------------------------------------------|--------------|
| `CedolinoAcquisito`        | cedolini      | dopo il salvataggio, nella stessa transazione            | calcoli, segnalazioni |
| `CedolinoEliminato`        | cedolini      | dopo la cancellazione, nella stessa transazione          | calcoli, segnalazioni |
| `CcnlRapportoModificato`   | anagrafica    | cambio CCNL scelto dall'utente                           | calcoli, segnalazioni |
| `RapportoLavoroEliminato`  | anagrafica    | prima di cancellare un rapporto rimasto senza cedolini   | (pulizie applicative eventuali) |

Gli eventi sono eventi applicativi Spring consegnati **in modo sincrono** (`@EventListener`) nella transazione che li
pubblica: il ricalcolo dei dati derivati e' parte della stessa unita' di lavoro (tutto o niente). Non si usa
l'event publication registry asincrono di Spring Modulith. I ricalcoli sono **da zero**, deterministici e
idempotenti (cancella e reinserisce i dati derivati del rapporto).

## Eliminazione di un cedolino

`ArchivioCedolini.elimina` (transazione unica, sotto lo stesso lock per workspace dell'acquisizione): cancella cedolino
+ figlie + PDF, ricostruisce i dati contrattuali del rapporto dai cedolini rimasti
(`CensimentoAnagrafica.sovrascriviDatiRapporto`), riclassifica i cedolini rimasti rispetto alla cessazione ricostruita
(mensilita' che tornano tali se la cessazione sparisce, progressivi TFR e nomi file), aggiorna gli estremi, pubblica `CedolinoEliminato` (ricalcolo sincrono di riepilogo e
segnalazioni). Se il rapporto non ha piu' cedolini: `CensimentoAnagrafica.eliminaRapporto` elimina rapporto e
persona/azienda orfane; i dati derivati spariscono in cascata.

## Configurazione

`backend/src/main/resources/application.yml` (commentato). Classi `@ConfigurationProperties` nel modulo che le usa:

| Prefisso                      | Classe                                         |
|-------------------------------|------------------------------------------------|
| `salary-manager.lettore`, `.letture-contemporanee`, `.fixture-dir` | `cedolini.config.LettoreProperties` |
| `salary-manager.ai`           | `cedolini.config.AiProperties` (solo lettore Claude) |
| `salary-manager.pdf`          | `cedolini.adapter.pdf.PdfProperties`           |
| `salary-manager.storage`      | `cedolini.config.StorageProperties`            |
| `salary-manager.samples-dir`  | `cedolini.config.CampioniProperties`           |
| `salary-manager.tfr`          | `calcoli.config.TfrProperties`                 |
| `salary-manager.ferie`        | `calcoli.config.FerieProperties`               |
| `salary-manager.calcoli`      | `calcoli.config.CalcoliProperties`             |
| `salary-manager.segnalazioni` | `segnalazioni.config.SegnalazioniProperties`   |
| `salary-manager.web`          | `web.config.WebProperties`                     |
| `salary-manager.database.path`| segnaposto usato in `spring.datasource.url`    |

Profilo `test` (`src/test/resources/application-test.yml`): H2 in memoria per contesto, lettore `fixture`.
Surefire lo attiva automaticamente (`SPRING_PROFILES_ACTIVE=test`); i test Spring usano anche `@ActiveProfiles("test")`.
I test sui cedolini reali di `samples/` sono esclusi di default tramite la property Maven `sam.test.excludedGroups`
(`live,campioni`): `@Tag("campioni")` misura il lettore locale senza rete
(`./mvnw test -Dgroups=campioni -Dsam.test.excludedGroups=none`), `@Tag("live")` legge con Claude
(`./mvnw test -Dgroups=live -Dsam.test.excludedGroups=none`, con `ANTHROPIC_API_KEY` nell'ambiente o nel `.env` della
radice; senza chiave viene saltato). Gli altri test non
leggono il `.env` dello sviluppatore: surefire punta `SAM_ENV_FILE` a un file inesistente (la suite end-to-end del
frontend fa lo stesso).

File `.env`: `application.yml` importa `optional:file:${SAM_ENV_FILE:../.env}[.env]`, cioe' il `.env` della radice del
progetto quando il backend parte da `backend/` (lo stesso file di Docker Compose). L'estensione `env` e' gestita da
`shared.config.CaricatoreFileEnv` (`PropertySourceLoader` registrato in `META-INF/spring.factories`): legge il file in
UTF-8 con la sintassi di Compose (virgolette, apici, commenti in linea, `export`, nessun escape con la `\` fuori
dalle virgolette doppie, gli escape e la sostituzione `$NOME`/`${NOME:-default}` di Compose in
`shared.config.InterpolazioneCompose`) e crea sorgenti con nome `dotenv: ...` e origine (riga e colonna) per ogni
valore. Un valore vuoto equivale a una variabile non impostata; un `${` nel valore e' salvato con l'escape dei
segnaposto di Spring (`\${`), cosi' resta letterale anche quando la variabile risolve un segnaposto di
`application.yml`. Una riga non valida, o con una variabile non definita da sostituire (Compose la renderebbe vuota),
blocca l'avvio con file e numero di riga, mai il contenuto. I file `application.env` che Spring Boot
cercherebbe nelle posizioni standard sono ignorati. Le variabili del `.env` risolvono i segnaposto `SAM_*` con
precedenza minore delle variabili d'ambiente vere; con un profilo attivo si legge anche `.env-<profilo>` (senza
profili `.env-default`), con precedenza sul `.env`. La chiave API non passa da `Environment.getProperty`:
`shared.config.VariabiliAmbiente` la legge dalla variabile d'ambiente, se non vuota, e poi dalle sorgenti `dotenv`,
mai da `application*.yml`, system property o argomenti; `FornitoreClientAnthropic` la verifica (solo caratteri ASCII
stampabili, senza spazi) prima di creare il client, con messaggi che non ne ripetono il valore.

Interfaccia di ascolto: `server.address: ${SAM_SERVER_ADDRESS:127.0.0.1}` (in fase 1 non c'e' login; se e' vuota il
backend non parte, invece di ascoltare su tutte le interfacce). Nel container Docker `SAM_SERVER_ADDRESS` vale
`0.0.0.0` (Dockerfile e `docker-compose.yml`), perche' nginx deve raggiungere il backend; da fuori si arriva solo alle
porte pubblicate su `SAM_BIND_ADDRESS`.

L'elenco delle variabili d'ambiente e delle proprieta' principali e' nel [README](../README.md#configurazione).

## Build

```
cd backend
./mvnw -B verify          # Linux/macOS
mvnw.cmd -B verify        # Windows
```

`generate-sources` esegue `jooq-codegen-maven` con `org.jooq.meta.extensions.liquibase.LiquibaseDatabase`: applica i
changelog a un H2 in memoria e genera il codice in `target/generated-sources/jooq`
(package `com.fcusumano.sam.shared.db.jooq`). Nessun database esterno richiesto.

## Esecuzione con Docker

```
            host (127.0.0.1)
   :8081 ─────────┐            ┌───────── :8080 (API e Swagger UI, facoltativo)
                  ▼            ▼
   ┌──────────────────┐   ┌──────────────────────────┐    volume "sam-data"
   │ frontend (nginx) │──►│ backend (Spring Boot)    │───► /data/salary-manager.mv.db
   │ dist/ della SPA  │   │ JRE 25, utente non root  │      (dati e PDF originali)
   └──────────────────┘   └──────────────────────────┘
     /api, /v3/api-docs,           │
     /swagger-ui ─► backend        └──► API Anthropic (estrazione)
```

- `backend/Dockerfile`: build multi-stage (JDK 25 + Maven Wrapper con cache delle dipendenze, jar estratto a strati),
  runtime JRE 25 con utente non root, database H2 nel volume `/data`, ascolto su tutte le interfacce del container
  (`SAM_SERVER_ADDRESS=0.0.0.0`), healthcheck su `GET /api/v1/ccnl` (verso `127.0.0.1`), opzioni JVM in `JAVA_OPTS`.
- `frontend/Dockerfile`: build con Node 22, runtime nginx stable alpine; `nginx.conf` (template) serve la SPA con
  fallback su `index.html`, cache lunga per `assets/` (nomi con hash), gzip, `client_max_body_size` allineato al
  limite multipart del backend e proxy verso `SAM_BACKEND_URL` (stessa origine: niente CORS).
- `docker-compose.yml`: i due servizi, il volume nominato `sam-data`, il lettore `SAM_LETTORE` (default `locale`) e
  la chiave `ANTHROPIC_API_KEY` (solo per il lettore Claude) dall'ambiente o da `.env` (mai versionato), porte
  pubblicate solo su `127.0.0.1` (nessun login in fase 1), modalita' demo con `SAM_LETTORE=fixture`.
