# Salary Manager

Portale web per ricostruire e monitorare la situazione retributiva di più persone a partire dai loro **cedolini
PDF**. I cedolini vengono letti, normalizzati, salvati a database e usati per calcolare riepiloghi (anzianità, scatti,
RAL, TFR, ferie e permessi con stima del valore) e per segnalare anomalie (mensilità mancanti, valori in calo senza
causa nota, fondo TFR incoerente, ...). La lettura è fatta di default dal **lettore locale**, gratuito e offline, che
interpreta il testo dei PDF dei layout supportati; per scansioni e layout sconosciuti si può usare **Claude** (API
Anthropic, a pagamento). Vedi [Lettori dei cedolini](#lettori-dei-cedolini).

Fase 1: un solo utente, **nessun login**. L'applicazione è pensata per l'uso in locale: per questo Docker Compose
pubblica le porte solo su `127.0.0.1`.

## Indice

- [Architettura in breve](#architettura-in-breve)
- [Prerequisiti](#prerequisiti)
- [Avvio in sviluppo](#avvio-in-sviluppo)
- [Avvio con Docker Compose](#avvio-con-docker-compose)
- [Lettori dei cedolini](#lettori-dei-cedolini)
- [Modalità demo (dati inventati)](#modalità-demo-dati-inventati)
- [Configurazione](#configurazione)
  - [File `.env`](#file-env)
- [Dove finiscono dati e PDF](#dove-finiscono-dati-e-pdf)
- [PDF protetti da password](#pdf-protetti-da-password)
- [Cedolini caricati con errore](#cedolini-caricati-con-errore)
- [Cedolini reali: misure e test live](#cedolini-reali-misure-e-test-live)
- [Test](#test)
- [Struttura del repository](#struttura-del-repository)
- [Sostituire il logo](#sostituire-il-logo)
- [Documentazione](#documentazione)
- [Privacy](#privacy)

## Architettura in breve

- **Backend** (`backend/`): monolite modulare **Spring Boot 4 + Spring Modulith** su **Java 25**, architettura
  esagonale dentro ogni modulo (il dominio non dipende da Spring, jOOQ o SDK). Database **H2 su file** in modalità
  PostgreSQL, schema gestito da **Liquibase**, accesso ai dati con **jOOQ** (codice generato dai changelog durante la
  build). Apertura, decifratura e lettura del testo dei PDF con **Apache PDFBox** (lettore locale); lettore
  alternativo con l'**SDK Java ufficiale Anthropic** (structured outputs).
- **Frontend** (`frontend/`): SPA **React + TypeScript + Vite** con **Mantine** e **TanStack Query**; parla col
  backend solo tramite le API REST `/api/v1` descritte in [`docs/openapi.yaml`](docs/openapi.yaml), da cui è
  generato il client TypeScript.

| Modulo         | Responsabilità                                                          | Dipende da                         |
|----------------|-------------------------------------------------------------------------|------------------------------------|
| `shared`       | kernel condiviso (workspace, competenza, importi), codice jOOQ generato  | -                                  |
| `ccnl`         | tabella dei contratti collettivi e riconoscimento dal testo stampato    | shared                             |
| `anagrafica`   | persone, aziende, rapporti di lavoro, CCNL scelto e note                | ccnl, shared                       |
| `cedolini`     | caricamento asincrono, PDF (anche protetti), lettura, archivio          | anagrafica, ccnl, shared           |
| `calcoli`      | riepiloghi derivati, ricalcolati da zero                                | cedolini, anagrafica, ccnl, shared |
| `segnalazioni` | regole di anomalia (una classe per regola)                              | cedolini, anagrafica, ccnl, shared |
| `web`          | controller REST, DTO, errori (Problem Details), sicurezza, OpenAPI      | tutti                              |

Ogni modulo usa solo le proprie tabelle e le API pubbliche degli altri. L'acquisizione o l'eliminazione di un
cedolino, il cambio di CCNL e l'associazione di un file in errore a una mensilità pubblicano eventi
(`CedolinoAcquisito`, `CedolinoEliminato`, `CcnlRapportoModificato`, `CaricamentiInErroreModificati`) consegnati **in
modo sincrono** nella stessa transazione: calcoli e segnalazioni del rapporto (per l'associazione solo le
segnalazioni) vengono ricalcolati da zero, tutto o niente. I confini sono verificati in build da `ModularityTests` (Spring Modulith) e
`ArchitectureTests` (ArchUnit). Dettagli in [`docs/architecture.md`](docs/architecture.md).

## Prerequisiti

- **JDK 25** (es. Eclipse Temurin) con `JAVA_HOME` impostato. Maven non serve: c'è il Maven Wrapper
  (`backend/mvnw`, `backend/mvnw.cmd`).
- **Node.js 22** (o superiore) con npm.
- Facoltativo: **chiave API Anthropic**, solo per leggere i cedolini con Claude (`SAM_LETTORE=claude`). Il lettore
  locale predefinito, la modalità demo e i test non la usano.
- Facoltativo: **Docker** con Compose v2 (BuildKit, attivo di default nelle versioni recenti).

## Avvio in sviluppo

### Backend

Con il lettore locale predefinito non serve alcuna configurazione: avvia il backend dalla cartella `backend/`.

```bash
cd backend
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run   (PowerShell: .\mvnw.cmd spring-boot:run)
```

Le impostazioni facoltative (lettore Claude e relativa chiave API, password dei PDF, porte) vanno nel file `.env` della
radice del progetto, lo stesso usato da Docker Compose (ignorato da git: **non committarlo**), che il backend avviato
dalla cartella `backend/` legge da solo (`../.env`). Per crearlo si copia il modello:

```bash
cp .env.example .env        # PowerShell: Copy-Item .env.example .env   -   cmd: copy .env.example .env
```

Le variabili d'ambiente hanno la precedenza sul `.env` (in PowerShell `$env:SAM_LETTORE = "claude"`, nel prompt dei
comandi `set SAM_LETTORE=claude`, senza virgolette). Formato del file e variabili che contano sono descritti in
[File `.env`](#file-env); per usare Claude vedi [Lettore Claude](#lettore-claude-a-pagamento).

- API REST: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html` (OpenAPI generata su `/v3/api-docs`)
- Il backend ascolta solo su `127.0.0.1`: in fase 1 non c'è login, quindi non è raggiungibile da altri computer
  (`SAM_SERVER_ADDRESS` cambia l'interfaccia).

Al primo avvio Liquibase crea il database in `backend/data/salary-manager.mv.db`.

### Frontend

```bash
cd frontend
npm ci
npm run dev          # http://localhost:5173
```

Il server di sviluppo Vite inoltra `/api` al backend su `http://127.0.0.1:8080` (per cambiarlo:
`SAM_BACKEND_URL=http://127.0.0.1:9090 npm run dev`). Con `npm run dev:mock` il frontend gira senza backend, con
dati inventati in memoria. Altri script in [`frontend/README.md`](frontend/README.md).

## Avvio con Docker Compose

1. Facoltativo: copia il modello [`.env.example`](.env.example) in `.env` accanto a `docker-compose.yml` (il file
   `.env` è ignorato da git: **non committarlo**). Con il lettore locale predefinito non serve; serve per scegliere
   il lettore Claude (`SAM_LETTORE=claude` e `ANTHROPIC_API_KEY`) o per cambiare password dei PDF, porte e JVM:

   ```bash
   cp .env.example .env        # PowerShell: Copy-Item .env.example .env
   ```

2. Costruisci e avvia:

   ```bash
   docker compose up --build -d
   ```

3. Apri **`http://localhost:8081`**.

| Servizio   | URL sull'host                                  | Note                                                       |
|------------|------------------------------------------------|------------------------------------------------------------|
| `frontend` | `http://localhost:8081` (`SAM_FRONTEND_PORT`)  | nginx: SPA + proxy di `/api`, `/v3/api-docs`, Swagger UI   |
| `backend`  | `http://localhost:8080` (`SAM_BACKEND_PORT`)   | API e `swagger-ui.html`; il frontend non ne ha bisogno     |

Il frontend parte quando il backend risulta in salute (healthcheck su `GET /api/v1/ccnl`, dopo l'avvio e le
migrazioni del database).

Comandi utili:

```bash
docker compose logs -f backend      # log
docker compose up --build -d        # aggiornamento dopo un git pull
docker compose down                 # arresto: i dati restano nel volume
docker compose down -v              # arresto ED ELIMINAZIONE di tutti i dati
```

- Le porte sono pubblicate solo su `127.0.0.1` (dentro il container il backend ascolta su tutte le interfacce:
  `SAM_SERVER_ADDRESS=0.0.0.0` è fisso in `docker-compose.yml`). Per renderle raggiungibili da altri computer impostare
  `SAM_BIND_ADDRESS=0.0.0.0`, ma solo su una rete fidata: in fase 1 non c'è login. In quel caso aggiungere a
  `SAM_ALLOWED_HOSTS` il nome o l'indirizzo con cui si apre l'applicazione (es.
  `SAM_ALLOWED_HOSTS=localhost,127.0.0.1,[::1],::1,192.168.1.20`): il backend risponde solo ai nomi host ammessi,
  per impedire a una pagina web di raggiungerlo con il DNS rebinding.
- `docker compose config` stampa anche la chiave API, se impostata: non condividerne l'output.
- Opzioni della JVM: `SAM_JAVA_OPTS` (default `-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError`).
- Le immagini si possono costruire anche singolarmente: `docker build -t salary-manager-backend backend` e
  `docker build -t salary-manager-frontend frontend` (il frontend inoltra le API a `SAM_BACKEND_URL`, default
  `http://backend:8080`). La build dell'immagine backend salta i test; per eseguirli anche lì:
  `docker build --build-arg MAVEN_BUILD_ARGS="" backend`.

## Lettori dei cedolini

Chi interpreta i PDF si sceglie con `SAM_LETTORE` (proprietà `salary-manager.lettore`), nell'ambiente o nel `.env`:

| `SAM_LETTORE`        | Lettore | Costo e rete | Cosa legge |
|----------------------|---------|--------------|------------|
| `locale` (default)   | lettore locale: testo del PDF con le posizioni (Apache PDFBox) e regole per layout | gratuito, offline: il PDF non lascia il computer | i layout supportati (tabella sotto), solo PDF con testo |
| `claude`             | Claude tramite l'API Anthropic (structured outputs) | **a pagamento**: crediti API separati dall'abbonamento a Claude; il PDF viene inviato ad Anthropic | qualsiasi cedolino, anche scansioni |
| `fixture`            | modalità demo: a ogni PDF associa un JSON di dati inventati | gratuito, offline | solo i PDF di prova (vedi [Modalità demo](#modalità-demo-dati-inventati)) |

Tutti e tre producono lo stesso JSON, che passa dalla **stessa normalizzazione e validazione** (somme, netto, ratei,
date, codici fiscali): il resto dell'applicazione non sa quale lettore è stato usato. Il lettore usato resta registrato
con il cedolino (`cedolino.modello_estrazione`: `lettore-locale-1/<layout>`, il modello Claude o `fixture`). Un valore
diverso dai tre blocca l'avvio. Cambiare lettore non rilegge i cedolini già caricati: per rileggerne uno si elimina e
si ricarica.

### Lettore locale (predefinito)

Riconosce il layout dalle etichette stampate e legge ogni campo in posizione relativa alle etichette (o, per i moduli
prestampati, alle coordinate del foglio). Layout supportati:

| Layout | Modello registrato | Riconosciuto da | Note |
|--------|--------------------|-----------------|------|
| JOB Sistemi (libro unico "Paghe Web") | `lettore-locale-1/job-sistemi` | "MESE DI RETRIBUZIONE", "NETTO IN BUSTA", "TFR SPETTANTE AZIENDA" | anche ristampe su pagine di formato diverso; TFR completo, presenze, ratei, progressivi |
| Zucchetti, grafica classica e recente | `lettore-locale-1/zucchetti` | "ZUCCHETTI", "PERIODO DI RETRIBUZIONE", "NETTO DEL MESE" | la grafica recente stampa le etichette con una "s" al posto degli spazi |
| Modulo continuo M39320 (studi paghe) | `lettore-locale-1/modulo-m39320` | "(M39320)" e "RETRIBUZIONE BASE" o "Paga oraria" | le etichette sono sul modulo e non nel PDF: campi letti per posizione; più fogli per cedolino |

Riconosce mensilità ordinarie, tredicesime (anche stampate come cedolino "aggiuntivo"), quattordicesime e cedolini di
fine rapporto successivi alla cessazione; classifica le voci (competenze, trattenute previdenziali e fiscali, altre
trattenute, voci non retributive, informative, presenze) dalla descrizione e dalla colonna in cui è stampato
l'importo. **Non legge mai i dati bancari** (IBAN, ABI/CAB, conto), anche se stampati.

Non indovina: lascia vuoto ciò che non trova e non deduce valori. La confidenza (0,95 al massimo) scende per ogni
sezione attesa e non trovata, per ogni controllo interno non superato (somma delle voci diversa dal totale stampato,
TFR spettante diverso da fondo più quota) e per ogni campo chiave assente; i controlli non superati diventano avvisi
del cedolino, senza valori.

Limiti:

- **PDF senza testo** (scansioni, foto, PDF di sole immagini): errore *"Il PDF non contiene testo selezionabile..."*;
- **layout sconosciuti**: errore *"Il lettore locale non riconosce il layout di questo cedolino..."*, con l'elenco dei
  layout supportati;
- **layout riconosciuto ma senza periodo, codice fiscale del dipendente o netto nel testo** (per esempio parti
  stampate come immagine): errore che elenca ciò che manca;
- i campi che il cedolino non stampa restano vuoti: nei layout visti finora il CCNL e la partita IVA dell'azienda
  mancano spesso in Zucchetti e nel modulo M39320, il codice fiscale dell'azienda nel modulo M39320, il fondo TFR al
  31/12 nell'anno di assunzione. Un CCNL non stampato si indica poi dall'interfaccia (rapporto con "CCNL da
  indicare").

Nei tre casi d'errore il messaggio suggerisce il lettore Claude. Per aggiungere un layout:
[`docs/sviluppo.md`](docs/sviluppo.md#lettore-locale-aggiungere-un-layout).

### Lettore Claude (a pagamento)

Legge qualsiasi cedolino, anche scansionato, ma ogni PDF è una chiamata all'API Anthropic, pagata con i **crediti
API** (separati dall'abbonamento a Claude), e il PDF (la copia decifrata, se era protetto) viene inviato ad
Anthropic. Si attiva nel `.env` (o nell'ambiente):

```dotenv
SAM_LETTORE=claude
ANTHROPIC_API_KEY=sk-ant-...
# facoltativi: SAM_AI_MODEL=claude-opus-5-5, SAM_AI_EFFORT=medium
```

Senza chiave il caricamento va in errore con un messaggio. Le impostazioni di Claude sono le proprietà
`salary-manager.ai.*` ([tabella](#variabili-e-proprietà)).

## Modalità demo (dati inventati)

Con `SAM_LETTORE=fixture` il backend non legge davvero i PDF: a ogni PDF caricato associa un JSON di fixture con
**dati inventati** (`backend/src/main/resources/fixtures/`), che passa dalla stessa validazione degli altri lettori.
Per provarla si caricano i PDF di prova in **`backend/src/main/resources/fixtures/pdf/`**; un PDF senza fixture
corrispondente termina in errore.

Con Docker, in un progetto separato (il volume dei dati è distinto da quello reale):

```bash
SAM_LETTORE=fixture docker compose -p salary-manager-demo up --build -d
# http://localhost:8081 ... al termine, eliminando anche i dati demo:
docker compose -p salary-manager-demo down -v
```

In PowerShell si imposta prima `$env:SAM_LETTORE = "fixture"`. Per farla girare insieme all'istanza reale
cambiare `SAM_FRONTEND_PORT` e `SAM_BACKEND_PORT`.

In sviluppo, con un database separato:

```bash
cd backend
SAM_LETTORE=fixture SAM_DB_PATH=./data/demo ./mvnw spring-boot:run
```

Windows (PowerShell):

```powershell
cd backend
$env:SAM_LETTORE = "fixture"
$env:SAM_DB_PATH = "./data/demo"
.\mvnw.cmd spring-boot:run
```

Windows (prompt dei comandi):

```bat
cd backend
set SAM_LETTORE=fixture
set SAM_DB_PATH=./data/demo
mvnw.cmd spring-boot:run
```

Poi, in un secondo terminale, `cd frontend`, `npm ci` e `npm run dev` (`http://localhost:5173`). Per usare altre
porte: `SAM_PORT=18080` (e `SAM_CORS_ORIGINS=http://localhost:15173`) per il backend,
`SAM_BACKEND_URL=http://127.0.0.1:18080 npx vite --port 15173` per il frontend (in PowerShell
`$env:SAM_BACKEND_URL = "http://127.0.0.1:18080"; npx vite --port 15173`).

Lo scenario, descritto in [`fixtures/README.md`](backend/src/main/resources/fixtures/README.md), comprende Mario Rossi
con due rapporti (Alfa Software S.r.l. e Beta Consulting S.p.A.) e un omonimo: mensilità mancante, rinnovo CCNL con
assorbimento del superminimo, scatto di anzianità, tredicesima separata, cedolino aggiuntivo, cessazione e cedolino
e due cedolini di fine rapporto (`TFR01`, `TFR02`), quattordicesima separata, buoni pasto, CCNL da scegliere.
Due PDF sono **protetti da password**: `rossi-beta-2024-09.pdf` si apre con il codice fiscale di Mario Rossi
(`rssmra80a01h501u`), quindi da solo quando lo si carica insieme agli altri suoi cedolini; `rossi-beta-2025-03.pdf`
richiede la password `demo-beta-2025` indicata nella modale di caricamento.

## Configurazione

La configurazione è in [`backend/src/main/resources/application.yml`](backend/src/main/resources/application.yml)
(commentato) e si cambia con variabili d'ambiente o con il file `.env`.

### File `.env`

Il file `.env` della radice del progetto (modello: [`.env.example`](.env.example)) è letto da Docker Compose e dal
backend avviato con `mvnw` dalla cartella `backend/`, che cerca `../.env` (`SAM_ENV_FILE` indica un altro file). È
ignorato da git, come le varianti `.env.*` e `.env-*` (tranne `.env.example`): non va mai committato.

Formato, lo stesso di Docker Compose salvo due differenze volute (variabile non definita e riga vuota, sotto):

- una variabile per riga, `NOME=valore`; le righe vuote e quelle che iniziano con `#` sono ignorate, `export `
  davanti al nome è facoltativo e gli spazi attorno al nome e al segno `=` non contano;
- codifica **UTF-8**, con o senza BOM (in Notepad: "UTF-8" o "UTF-8 con BOM", non "ANSI" né "UTF-16"), fine riga
  Windows o Unix;
- valore senza virgolette: preso così com'è, senza gli spazi finali e senza un commento in linea, cioè uno spazio
  seguito da `#` (`SAM_AI_EFFORT=high # nota`; in `pa#ss` il `#` fa parte del valore). La `\` **non** è un carattere
  di escape: percorsi Windows si scrivono come sono (`SAM_DB_PATH=C:\dati\sam`);
- il `$` invece **sì**, come in Compose: `$NOME` e `${NOME}` diventano il valore della variabile (d'ambiente o di una
  riga precedente), `${NOME:-default}` il default se è vuota o non c'è, `$$` è un `$`. Una password con il `$` va
  quindi **tra apici singoli** (`SAM_PDF_PASSWORD_CANDIDATE='Pa$sw0rd'`) oppure con `$$` (`Pa$$sw0rd`). Una variabile
  non definita blocca l'avvio del backend (Compose la renderebbe vuota, con un solo avviso, troncando la password);
- tra apici singoli il valore è letterale, `$`, `\`, `#` e spazi compresi (`SAM_PDF_PASSWORD_CANDIDATE='pa ss #1'`):
  serve se contiene `$`, ` #` o spazi all'inizio o alla fine. Unica eccezione, `\'` è un apice: il valore non può
  quindi finire con una `\` (`'C:\dati\'` non è chiuso, né per Compose né per il backend: scrivi `C:\dati\` senza
  apici);
- tra virgolette doppie valgono gli escape di Compose (`\n`, `\r`, `\t`, `\\`, `\"`, `\$` e pochi altri) e la
  sostituzione del `$` (per percorsi Windows e password meglio non usarle); apici e virgolette si chiudono sulla
  stessa riga;
- una riga vuota (`NOME=`, `NOME=''`) vale come variabile non impostata: con `mvnw` si usa il default di
  `application.yml`, mentre Docker Compose passa al container un valore vuoto per le variabili senza default in
  `docker-compose.yml` (`SAM_AI_MODEL`, `SAM_AI_EFFORT`). Per tornare al default **commenta la riga**. Se
  `server.address` risulta comunque vuoto (per esempio `SAM_SERVER_ADDRESS` vuota nell'ambiente) il backend non parte;
- `${` resta letterale anche quando la variabile entra in un segnaposto di `application.yml` (`'utente${X}'` arriva
  così com'è), come con Compose;
- una riga non valida (senza `=`, con un nome non ammesso, con virgolette non chiuse, con un `${...}` non valido o una
  variabile non definita) o un file non UTF-8 bloccano l'avvio del backend con un errore che indica file e numero di
  riga, mai il contenuto.

Con `mvnw` contano `ANTHROPIC_API_KEY` e le variabili `SAM_*` che `application.yml` usa come segnaposto (tabella
sotto, comprese `SAM_PORT` e `SAM_SERVER_ADDRESS`). La forma "relaxed" (`SALARYMANAGER_AI_CONCURRENCY=4`) e le altre
variabili di Spring (`SPRING_PROFILES_ACTIVE`, `SERVER_PORT`) nel `.env` sono ignorate; il nome esatto di una
proprietà (`salary-manager.ai.concurrency=4`) invece vale, ma solo con `mvnw`. Docker Compose usa il `.env` solo per
le variabili citate in `docker-compose.yml`: in `.env.example` quelle che valgono per uno solo dei due avvii sono
marcate "(solo Docker Compose)" o "(solo mvnw)".

Precedenza, dalla più forte: argomenti (`--proprieta=valore`) e system property (`-Dproprieta=valore`), variabili
d'ambiente vere, `.env-<profilo>` (solo con un profilo Spring attivo, oppure `.env-default` senza profili), `.env`,
`application.yml`. La chiave API fa eccezione: si legge solo dalla variabile d'ambiente, se non è vuota, e poi dal
`.env`, mai da `application*.yml`, system property o argomenti. I test non leggono il `.env` (surefire imposta
`SAM_ENV_FILE` su un file inesistente, la suite end-to-end fa lo stesso), tranne il
[test live](#cedolini-reali-misure-e-test-live) e la [misura sui campioni](#misura-del-lettore-locale).

### Variabili e proprietà

Variabili principali del backend (d'ambiente o nel `.env`):

| Variabile                         | Proprietà                                  | Default                  | Descrizione |
|-----------------------------------|--------------------------------------------|--------------------------|-------------|
| `SAM_LETTORE`                     | `salary-manager.lettore`                   | `locale`                 | lettore dei cedolini: `locale` (gratuito, offline), `claude` (API Anthropic, a pagamento) o `fixture` (demo e test); vedi [Lettori dei cedolini](#lettori-dei-cedolini) |
| `ANTHROPIC_API_KEY`               | (non è una property)                       | -                        | chiave API Anthropic, solo con `SAM_LETTORE=claude`: variabile d'ambiente non vuota o `.env`, mai `application.yml` |
| `SAM_AI_MODEL`                    | `salary-manager.ai.model`                  | `claude-opus-5-5`        | modello Claude (solo con `SAM_LETTORE=claude`) |
| `SAM_AI_EFFORT`                   | `salary-manager.ai.effort`                 | `medium`                 | `low`, `medium`, `high`, `xhigh`, `max` (solo con `SAM_LETTORE=claude`) |
| `SAM_AI_FALLBACK_RIFIUTI`         | `salary-manager.ai.fallback-rifiuti`       | `true`                   | se il modello rifiuta, l'API ripete la richiesta sul modello di fallback (solo con `SAM_LETTORE=claude`) |
| `SAM_PDF_PASSWORD_CANDIDATE`      | `salary-manager.pdf.password-candidate`    | (nessuna)                | password da provare sui PDF protetti, separate da virgola |
| `SAM_DB_PATH`                     | `salary-manager.database.path`             | `./data/salary-manager`  | file H2 senza estensione, relativo alla cartella di avvio |
| `SAM_DB_USER`, `SAM_DB_PASSWORD`  | `spring.datasource.username` / `password`  | `sa` / vuota             | credenziali del database |
| `SAM_JOOQ_DIALECT`                | `spring.jooq.sql-dialect`                  | `H2`                     | `POSTGRES` con un vero PostgreSQL |
| `SAM_PORT`                        | `server.port`                              | `8080`                   | porta HTTP del backend |
| `SAM_SERVER_ADDRESS`              | `server.address`                           | `127.0.0.1`              | interfaccia di ascolto: solo locale (in fase 1 non c'è login); nel container Docker `0.0.0.0` |
| `SAM_ENV_FILE`                    | `spring.config.import`                     | `../.env`                | file `.env` letto all'avvio, relativo alla cartella di avvio (non si imposta nel `.env` stesso) |
| `SAM_CORS_ORIGINS`                | `salary-manager.web.cors-allowed-origins`  | `http://localhost:5173`  | origini ammesse (server di sviluppo Vite) |
| `SAM_ALLOWED_HOSTS`               | `salary-manager.web.allowed-hosts`         | `localhost,127.0.0.1,[::1],::1` | nomi host ammessi nell'header `Host`, separati da virgola (altri nomi: 400) |
| `SAM_SAMPLES_DIR`                 | `salary-manager.samples-dir`               | `../samples`             | cedolini reali per la misura del lettore locale e i test live (più cartelle separate da `:`, su Windows da `;`) |

Altre proprietà utili, senza variabile dedicata:

| Proprietà                                                  | Default                                     | Descrizione |
|------------------------------------------------------------|---------------------------------------------|-------------|
| `salary-manager.letture-contemporanee`                     | `2`                                         | cedolini letti contemporaneamente (qualunque lettore) |
| `salary-manager.ai.concurrency`                            | `2`                                         | richieste contemporanee all'API Anthropic (lettore Claude) |
| `salary-manager.ai.max-tokens` / `timeout` / `max-retries` | `32000` / `5m` / `2`                        | parametri delle richieste a Claude (oltre 21333 token si usa lo streaming) |
| `salary-manager.pdf.pagine-massime`                        | `100`                                       | pagine massime di un PDF |
| `salary-manager.pdf.dimensione-massima-richiesta`          | `32MB`                                      | limite della richiesta all'API Anthropic (PDF fino a circa 23MB) |
| `salary-manager.pdf.limite-decompressione`                 | `100MB`                                     | dimensione massima di un flusso compresso del PDF una volta decompresso (oltre: PDF danneggiato) |
| `spring.servlet.multipart.max-file-size` / `max-request-size` | `25MB` / `500MB`                         | limiti del caricamento (per file / per richiesta) |
| `server.tomcat.max-part-count`                             | `1001`                                      | parti di una richiesta multipart: fino a 1000 PDF per lotto più la password |
| `salary-manager.storage.nome-file-pattern`                 | `{anno}.{mese}_{idAzienda}_{idPersona}.pdf` | nome normalizzato dei PDF |
| `salary-manager.tfr.*`                                     | `0.23` / `0.17` / `13.5` / `0.03` / `12`    | aliquota stimata per il netto, imposta sostitutiva sulla rivalutazione, divisore della quota, tasso indicativo di rivalutazione e mesi massimi della stima "ad oggi" |
| `salary-manager.ferie.*`                                   | `26` / `173` / `8`                          | divisori giornaliero e orario, ore al giorno per la stima del valore |
| `salary-manager.calcoli.*`                                 | `0.01` / `13` / `3`                         | tolleranza degli aumenti, mensilità senza CCNL (14 se i cedolini documentano una quattordicesima), cedolini esaminati per i buoni pasto |
| `salary-manager.segnalazioni.*`                            | vedi `application.yml`                      | tolleranze (fondo TFR, importi), mesi di attesa del fine rapporto, CCNL con Cassa Edile |

Qualunque proprietà si può sovrascrivere con una variabile d'ambiente vera in forma "relaxed" (es.
`SALARYMANAGER_AI_CONCURRENCY=4`) o con un argomento (`--salary-manager.ai.concurrency=4`; con il plugin Maven:
`./mvnw spring-boot:run -Dspring-boot.run.arguments=--salary-manager.ai.concurrency=4`). Nella forma relaxed i punti
diventano `_` e i trattini si eliminano: `salary-manager` diventa `SALARYMANAGER`, `password-candidate` diventa
`PASSWORDCANDIDATE` (una variabile come `SALARY_MANAGER_AI_CONCURRENCY` viene ignorata). Per le proprietà che in
`application.yml` hanno già un segnaposto `SAM_*` (tabella sopra) conviene usare quella variabile, l'unica che vale
anche nel `.env`.

Variabili del frontend e di Docker Compose:

| Variabile            | Dove                       | Default                                          | Descrizione |
|----------------------|----------------------------|--------------------------------------------------|-------------|
| `SAM_BACKEND_URL`    | Vite (sviluppo), nginx     | `http://127.0.0.1:8080` / `http://backend:8080`  | backend a cui inoltrare `/api` |
| `VITE_API_BASE_URL`  | build del frontend         | (stessa origine)                                 | origine delle API se diversa (il backend deve ammetterla in CORS) |
| `SAM_FRONTEND_PORT`  | Docker Compose             | `8081`                                           | porta dell'applicazione sull'host |
| `SAM_BACKEND_PORT`   | Docker Compose             | `8080`                                           | porta delle API sull'host |
| `SAM_BIND_ADDRESS`   | Docker Compose             | `127.0.0.1`                                      | interfaccia dell'host su cui pubblicare le porte |
| `SAM_JAVA_OPTS`      | Docker Compose             | `-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError` | opzioni della JVM del backend (variabile `JAVA_OPTS` dell'immagine) |
| `SAM_TZ`             | Docker Compose             | `Europe/Rome`                                    | fuso orario del backend |

## Dove finiscono dati e PDF

- **Tutto è nel database H2**, un solo file: in sviluppo `backend/data/salary-manager.mv.db` (cartella `data/`
  ignorata da git), con Docker il volume `sam-data` montato su `/data` (`/data/salary-manager.mv.db`).
- I **PDF originali**, così come caricati (anche se protetti da password), sono salvati nella tabella `cedolino_pdf`
  con un nome normalizzato indipendente dal file caricato: `AAAA.MM_<idAzienda>_<idPersona>.pdf` (mese `01`-`12`,
  `13` tredicesima, `14` quattordicesima) oppure `AAAA.TFRnn_<idAzienda>_<idPersona>.pdf` per i cedolini successivi
  alla cessazione. Si scaricano dalla lista mensilità.
- Il PDF di un **file caricato con errore** che si può ancora completare (inserimento a mano o nuova lettura) resta
  nella tabella `caricamento_pdf`, così come caricato, uno per file anche se caricato più volte. Viene eliminato
  appena il file è risolto: inserito a mano o letto (diventa il PDF del cedolino), scartato come duplicato o
  aggiuntivo, rimosso con "Rimuovi" dall'elenco dei cedolini caricati con errore. I file danneggiati, con troppe
  pagine o con più cedolini non vengono conservati.
- Il JSON prodotto dal lettore resta a DB per verifica (`cedolino.estratto_json`), con il lettore usato
  (`cedolino.modello_estrazione`). **Nessun dato bancario** (IBAN, ABI/CAB, conto) viene letto o salvato; le password
  dei PDF non vengono mai salvate né scritte nei log.
- Con il **lettore locale** (predefinito) e con quello a fixture il PDF **non lascia il computer**. Con il lettore
  Claude (`SAM_LETTORE=claude`) **il PDF viene inviato all'API Anthropic** (la copia decifrata, se era protetto).
- Eliminando un cedolino i dati derivati del rapporto vengono ricalcolati da zero; eliminando l'ultimo cedolino di
  un rapporto spariscono anche il rapporto e la persona o l'azienda rimaste senza rapporti.
- Per ripartire da zero: fermare il backend e cancellare `backend/data/` (con Docker: `docker compose down -v`).
- I database H2 creati dalle versioni precedenti alla correzione dell'URL JDBC (opzione `DATABASE_TO_LOWER=TRUE`,
  tolta: impediva a Liquibase di riconoscere le proprie tabelle e ogni avvio dopo il primo falliva) vanno ricreati:
  cancellare `backend/data/` o la cartella indicata in `SAM_DB_PATH` (demo comprese) e, con Docker, il volume
  (`docker compose down -v`). Quei file erano comunque già illeggibili dal secondo avvio.
- Copia di sicurezza del volume Docker (contiene dati personali: salvarla fuori dal repository):

  ```bash
  docker compose stop backend
  docker run --rm -v salary-manager_sam-data:/data -v "$HOME":/backup alpine \
      tar czf /backup/salary-manager-data.tgz -C /data .
  docker compose start backend
  ```

## PDF protetti da password

Alcuni cedolini sono protetti da una password di apertura. Nella modale di caricamento si può indicare una
**password facoltativa valida per tutto il lotto**. Per ogni PDF protetto il backend prova, in ordine:

1. la password indicata nel caricamento;
2. le password configurate (`SAM_PDF_PASSWORD_CANDIDATE`, separate da virgola);
3. i **codici fiscali delle persone già censite**, in maiuscolo e in minuscolo (molti software paghe usano il codice
   fiscale del dipendente come password).

Se in un lotto di più file un PDF protetto non si apre, viene **riprovato una volta a fine lotto**: così si apre anche
quando il codice fiscale che fa da password appartiene a una persona censita da un altro file dello stesso lotto
(i file sono elaborati in parallelo e l'ordine non conta). Se nessuna password funziona, il file va in errore con il
messaggio *"PDF protetto da password: indica la password e ricarica"* e compare tra i
[cedolini caricati con errore](#cedolini-caricati-con-errore): con **"Riprova con password"** si indica la password e
il file viene riletto dal PDF conservato, senza ricaricarlo (la password vale solo per quella lettura e non viene
salvata). In alternativa si può inserire a mano: l'anteprima del PDF nel modulo chiede la password al visualizzatore
del browser, che non la salva. Il lettore (locale o Claude) riceve una copia decifrata creata solo in memoria; a DB si
conserva il PDF originale, su cui si calcola anche l'impronta usata per riconoscere i file già caricati.

I cedolini scansionati (solo immagine, senza testo) si leggono solo con il lettore Claude, che legge il PDF anche
visivamente; il lettore locale li rifiuta con un messaggio che lo suggerisce.

## Cedolini caricati con errore

Un PDF che il lettore non riesce a leggere (protetto, scansione senza testo, layout non riconosciuto, lettore non
disponibile, dati essenziali mancanti, ...) diventa un **cedolino caricato con errore**: è stato caricato ma non è
nell'archivio. È una cosa diversa da una **mensilità mancante**, per cui nessun file è stato caricato.

- **Elenco**: i file in errore compaiono nell'area rossa **"Cedolini caricati con errore"** in cima alla pagina (anche
  senza una persona selezionata), con il motivo dell'errore; il contatore nella barra superiore porta all'elenco. Lo
  stesso file caricato più volte compare una volta sola; sparisce quando viene letto, inserito a mano o rimosso.
- **Azioni** (secondo il motivo): **Inserisci a mano**, **Riprova con password** (PDF protetti), **Riprova** (lettore
  non disponibile o elaborazione interrotta), **Associa a un mese**, **Scarica PDF**, **Rimuovi** (elimina il file,
  il PDF conservato e l'associazione). Per un file danneggiato, troppo lungo o con più cedolini l'unica via è caricare
  un file valido (un PDF per cedolino).
- **Associa a un mese**: si sceglie persona, rapporto e mensilità (mai dedotti in automatico). La mensilità diventa
  rossa, *"Caricato con errore"*, e la segnalazione *"Cedolino caricato con errore"* prende il posto di *"Mensilità
  mancante"*. Una mensilità che ha già un cedolino o un altro file associato non si può scegliere.
- **Inserisci a mano**: un modulo con tutti i campi che il lettore avrebbe estratto, accanto all'anteprima del PDF.
  "Copia dati da..." riempie i campi vuoti da un rapporto esistente (persona, azienda, voci senza importi, ...).
  **Verifica** prova l'archiviazione senza salvare (mensilità, persona o rapporto nuovi, conflitti, controlli di
  coerenza); **Salva cedolino** lo archivia con il PDF originale come se fosse stato letto: riepilogo e segnalazioni
  si ricalcolano, e nel dettaglio compare "Inserito a mano" al posto dell'affidabilità della lettura. Sono
  obbligatori periodo, codice fiscale, nome e cognome del dipendente, ragione sociale del datore di lavoro, totale
  competenze, totale trattenute e netto.
- **Errori delle versioni precedenti**: i file andati in errore prima di questa funzione non hanno il PDF conservato
  né il motivo; compaiono nell'elenco con il loro messaggio e vanno **ricaricati una volta** (oppure rimossi).
- Un file la cui prima lettura è stata interrotta da un riavvio dell'applicazione non ha il PDF conservato: va
  ricaricato.

## Cedolini reali: misure e test live

I cedolini reali contengono dati personali: vanno nella cartella **`samples/`** alla radice del repository, che è
**ignorata da git** (come ogni cartella `samples/` e `data/`). Non vanno mai copiati altrove nel repository: fixture
e test usano solo dati inventati. La cartella si cambia con `SAM_SAMPLES_DIR` (relativa a `backend/`; più cartelle
separate da `:`, su Windows da `;`). La password dei PDF protetti (facoltativa) va in `SAM_SAMPLES_PASSWORD`,
nell'ambiente o nel `.env`; valgono anche le password configurate (`SAM_PDF_PASSWORD_CANDIDATE`). Entrambi i test
usano il profilo `test`, quindi leggono anche l'eventuale `.env-test` (con precedenza sul `.env`), e sono esclusi dalla
build normale.

### Misura del lettore locale

`MisuraCampioniLocaleTest` (`@Tag("campioni")`) passa ogni PDF dei campioni al lettore locale, **senza rete** e senza
costi:

```bash
cd backend
./mvnw test -Dgroups=campioni -Dsam.test.excludedGroups=none
```

Windows (PowerShell): `.\mvnw.cmd test "-Dgroups=campioni" "-Dsam.test.excludedGroups=none"`.

- Per ogni file stampa solo numero progressivo, layout, esito (o motivo dell'errore), tipo, conteggi (campi
  valorizzati, campi chiave non trovati, voci, ratei, avvisi) ed esito dei controlli di coerenza: netto = competenze -
  trattenute + arrotondamento, somme delle voci = totali stampati, ratei (residuo + maturato - goduto = saldo), TFR
  (fondo + quota dell'anno = TFR spettante, dove stampato). Mai nomi di file, nominativi, codici fiscali, periodi o
  importi.
- Poi una tabella aggregata per layout ed esito, i campi chiave non trovati (nome del campo e conteggio) e il confronto
  dei mesi consecutivi dello stesso rapporto (progressivi annui non decrescenti, fondo TFR costante nell'anno e non
  decrescente al cambio d'anno), sempre solo come conteggi.
- I PDF protetti che nessuna password apre si riprovano a fine giro con i codici fiscali letti dagli altri campioni,
  tenuti solo in memoria, come fa l'applicazione con le persone già censite.
- Il test fallisce se un cedolino letto non supera i controlli di coerenza; se non ci sono PDF viene saltato.

### Test live con Claude

Il test `EstrazioneCampioniLiveTest` (`@Tag("live")`) invia davvero a Claude ogni PDF dei campioni e verifica la
lettura end-to-end con il lettore Claude. Per eseguirlo:

```bash
cd backend                               # con ANTHROPIC_API_KEY nel .env della radice (o nell'ambiente)
./mvnw test -Dgroups=live -Dsam.test.excludedGroups=none
```

Windows (PowerShell): `.\mvnw.cmd test "-Dgroups=live" "-Dsam.test.excludedGroups=none"`.

- Se la cartella manca o è vuota, o se `ANTHROPIC_API_KEY` non è impostata (né nel `.env` né nell'ambiente), il test
  viene saltato. Non serve impostare `SAM_LETTORE`: il test usa sempre Claude.
- L'output riporta per ogni file solo un numero progressivo, esito, tipo, competenza e confidenza: mai nomi di file,
  nominativi, codici fiscali o importi. Di un errore riporta il motivo o la classe dell'eccezione, mai il messaggio.
- Ogni PDF è una chiamata all'API, con il relativo costo.

## Test

```bash
cd backend
./mvnw -B verify                         # PowerShell: .\mvnw.cmd -B verify   prompt: mvnw.cmd -B verify
./mvnw test -Dtest=CalcoloTfrTest        # una sola classe
```

`verify` esegue i test unitari (dominio, calcoli, regole di segnalazione), i test di integrazione su H2 in memoria
(repository, servizi, API, lettura con il lettore a fixture), i test del lettore locale su PDF sintetici generati in
memoria con dati inventati, `ArchitectureTests` e `ModularityTests`, e genera i diagrammi dei moduli in
`backend/target/spring-modulith-docs`. Nessuna chiamata di rete né all'API Anthropic; la misura sui campioni e il test
live sono esclusi (`@Tag("campioni")`, `@Tag("live")`).

```bash
cd frontend
npm test                 # Vitest + Testing Library
npm run lint             # ESLint, zero warning
npm run typecheck
npm run format:check
npm run build
```

Una suite **end-to-end facoltativa** (Playwright, `frontend/e2e/`) avvia il backend in modalità demo su un database
temporaneo e il frontend, poi percorre il flusso completo in Chromium: caricamento dei PDF di fixture (anche quello
protetto), selezione della persona e degli omonimi, segnalazioni, riepilogo, mensilità, download, eliminazione, CCNL,
note e vista mobile. Non fa parte della build di default:

```bash
cd frontend
npx playwright install chromium          # solo la prima volta
npm run e2e                              # porte 18080 (backend) e 15173 (frontend)
```

Variabili e dettagli in [`frontend/README.md`](frontend/README.md#test-end-to-end).

## Struttura del repository

```
salary-manager/
├── backend/                        Spring Boot (Maven Wrapper: mvnw, mvnw.cmd)
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/fcusumano/sam/
│       │   ├── shared/             kernel condiviso + jOOQ generato
│       │   ├── ccnl/               tabella e riconoscimento CCNL
│       │   ├── anagrafica/         persone, aziende, rapporti
│       │   ├── cedolini/           caricamento, PDF, lettori (locale, Claude, fixture), archivio
│       │   ├── calcoli/            riepiloghi derivati
│       │   ├── segnalazioni/       regole di anomalia
│       │   └── web/                REST, DTO, errori, sicurezza, OpenAPI
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── db/changelog/       changelog Liquibase (un intervallo di numeri per modulo)
│       │   ├── prompts/            prompt di Claude e schema JSON comune a tutti i lettori
│       │   └── fixtures/           cedolini inventati (JSON + PDF) per demo e test
│       └── test/                   test unitari, di integrazione e architetturali
├── frontend/                       SPA React (Vite), Dockerfile, nginx.conf
│   ├── public/logo.png
│   ├── src/
│   └── e2e/                        test end-to-end Playwright (facoltativi, npm run e2e)
├── docs/                           openapi.yaml, architecture.md, decisions.md, sviluppo.md
├── docker-compose.yml
├── .env.example                    modello delle variabili per backend e Docker Compose (copiarlo in .env)
└── samples/                        cedolini reali (ignorata da git, da creare)
```

## Sostituire il logo

Il logo è [`frontend/public/logo.png`](frontend/public/logo.png): **PNG quadrato 128×128 px con trasparenza**, usato
nella barra superiore e come favicon. Basta sovrascriverlo mantenendo **stesso nome, formato e dimensione**. In
sviluppo è sufficiente ricaricare la pagina; con Docker va ricostruita l'immagine del frontend:
`docker compose up --build -d frontend`.

## Documentazione

- [`docs/architecture.md`](docs/architecture.md): moduli, dipendenze, tabelle, flusso di caricamento, eventi, Docker
- [`docs/decisions.md`](docs/decisions.md): registro delle decisioni di progetto
- [`docs/sviluppo.md`](docs/sviluppo.md): convenzioni e procedure (nuove tabelle, eventi, regole di segnalazione,
  CCNL, layout del lettore locale, client TypeScript)
- [`docs/openapi.yaml`](docs/openapi.yaml): contratto REST tra frontend e backend
- [`frontend/README.md`](frontend/README.md): frontend, script e modalità mock
- [`backend/src/main/resources/fixtures/README.md`](backend/src/main/resources/fixtures/README.md): scenario demo

## Privacy

- Nel repository **nessun dato personale reale**: fixture, test e mock usano nominativi, codici fiscali, aziende e
  importi inventati (es. "Mario Rossi", `RSSMRA80A01H501U`, "Alfa Software S.r.l.").
- `samples/`, `data/`, i file `.env` (con le varianti `.env.*` e `.env-*`, tranne `.env.example`) e i database H2 sono
  ignorati da git.
- Con il lettore locale (predefinito) i PDF non lasciano il computer; solo con il lettore Claude vengono inviati
  all'API Anthropic.
- La chiave API non va mai in file versionati: solo nel `.env` (ignorato da git) o in una variabile d'ambiente.
- I PDF dei cedolini caricati con errore restano nel database solo finché servono per completarli e si eliminano
  quando il file è risolto o rimosso; i moduli inseriti a mano non vengono mai salvati come bozza nel browser.
- I log a livello INFO non contengono codici fiscali, nominativi, importi né password.
