# Frontend Salary Manager

SPA in **React 19 + TypeScript (strict) + Vite**, con **Mantine 9** (core, hooks, dropzone, notifications,
modals, form) e **TanStack Query 5**. Parla col backend solo tramite le API REST descritte in
[`../docs/openapi.yaml`](../docs/openapi.yaml): i tipi sono generati con `openapi-typescript` e le chiamate passano
da `openapi-fetch`.

## Avvio

Requisiti: Node.js 22 (o superiore) e npm.

```bash
cd frontend
npm ci
npm run dev          # http://localhost:5173, /api inoltrato al backend su http://127.0.0.1:8080
```

Il backend di destinazione del proxy di sviluppo si cambia con `SAM_BACKEND_URL` (es.
`SAM_BACKEND_URL=http://127.0.0.1:9090 npm run dev`); il default usa `127.0.0.1` perché il backend avviato con mvnw
ascolta solo lì. In produzione il frontend chiama `/api/v1/...` sulla stessa origine (reverse proxy);
`VITE_API_BASE_URL` permette di indicare un'altra origine al momento della build (in quel caso il backend deve
accettarla in CORS, `salary-manager.web.cors-allowed-origins`).

### Sviluppo senza backend (dati simulati)

```bash
npm run dev:mock     # dati inventati in memoria (Mario Rossi, Laura Bianchi, aziende fittizie)
```

Con `?mock=vuoto` nell'URL si parte senza dati, come al primo avvio; con `?mock=errori` ai dati dimostrativi si
aggiungono tre cedolini caricati con errore (`busta-scansione.pdf`, `cedolino-protetto.pdf` e `ristampa.pdf`,
quest'ultimo associato alla mensilità mancante di Agosto 2025 di Mario Rossi). Nel caricamento simulato l'esito
dipende dal nome del file: `duplicato` → Duplicato, `aggiuntivo` → Aggiuntivo non gestito; in Errore, con il PDF
conservato (inseribile a mano e rileggibile), `scansione` (scansione senza testo), `ristampa` (layout non
riconosciuto) e `protetto` se non si indica una password; in Errore senza PDF conservato `danneggiato` (file
danneggiato) ed `errore` (più cedolini nello stesso PDF). Ogni altro file viene acquisito. Lo stesso file (stesso
nome e stessa dimensione) caricato più volte compare una volta sola tra i cedolini caricati con errore.
L'inserimento a mano simulato restituisce gli stessi errori del backend (percorsi, testi e ordine dei campi, uno per
campo) e gli stessi avvisi di coerenza, salvo l'avviso sui dati bancari rimossi dai testi. Come il backend:
`GET /api/v1/aziende` elenca le aziende dei rapporti per ragione sociale con l'etichetta anti-omonimia (P.IVA, poi
codice fiscale, poi posizione INPS; per le persone omonime il codice fiscale); con `personaId` / `aziendaId` il
cedolino usa i dati anagrafici in archivio e va al rapporto della coppia scelta (o a uno nuovo), un id sconosciuto è
un 400 sulla scelta; senza scelta ("Crea nuovo") un codice fiscale, una partita IVA, una posizione INPS o una ragione
sociale già in archivio sono un 400 "… già in archivio" sul campo (versione semplificata del riconoscimento del
censimento).

I mock esistono solo con `--mode mock` (`.env.mock` imposta `VITE_USE_MOCKS=true`): nella build di produzione il
modulo `src/mocks` non viene incluso nel bundle. `npm run build:mock` + `npm run preview:mock` servono la build
con i mock (cartella `dist-mock/`, non versionata).

## Script

| Script               | Descrizione                                                           |
| -------------------- | --------------------------------------------------------------------- |
| `npm run dev`        | server di sviluppo con proxy `/api` → backend                         |
| `npm run dev:mock`   | server di sviluppo con backend simulato                               |
| `npm run build`      | typecheck + build di produzione in `dist/`                            |
| `npm run typecheck`  | controllo dei tipi (app, test, configurazione)                        |
| `npm run lint`       | ESLint (typescript-eslint strict, react-hooks), zero warning ammessi  |
| `npm run format`     | Prettier                                                              |
| `npm test`           | Vitest + Testing Library (jsdom), con il backend simulato             |
| `npm run gen:api`    | rigenera `src/api/schema.d.ts` da `../docs/openapi.yaml` (versionato) |
| `npm run e2e`        | suite end-to-end Playwright (facoltativa, vedi sotto)                 |
| `npm run e2e:report` | apre il report HTML dell'ultima esecuzione end-to-end                 |

Dopo ogni modifica al contratto OpenAPI eseguire `npm run gen:api` e committare `src/api/schema.d.ts`: il typecheck
segnala subito i punti del frontend da adeguare.

## Test end-to-end

La suite in `e2e/` (Playwright, solo Chromium) **non** fa parte di `npm test` né di `npm run build`. Avvia da sola:

- il backend in modalità demo (`../backend`, `mvnw spring-boot:run` con `SAM_LETTORE=fixture`, dati inventati e
  nessuna lettura reale dei PDF) con un database H2 nuovo in una cartella temporanea del sistema (`sam-e2e-*`,
  eliminata al termine);
- il server di sviluppo Vite con il proxy `/api` verso quel backend.

Poi percorre il flusso completo caricando i PDF di `../backend/src/main/resources/fixtures/pdf/`: primo avvio,
scarto dei file non PDF, lotto con tutti gli esiti, secondo lotto con la password del PDF protetto, omonimi con il
codice fiscale nella tendina, segnalazioni e riepilogo, mensilità (mancante, tredicesima, quattordicesima, fine
rapporto), buste espanse e download del PDF, eliminazione con ricalcolo, scelta e modifica del CCNL, note, un
cedolino caricato ma non letto (un PDF vuoto costruito nel test) associato alla sua mensilità mancante e inserito a
mano con verifica e salvataggio, rimozione del file danneggiato, vista mobile senza scorrimento orizzontale (anche
dell'elenco dei file in errore e dell'inserimento a mano).

```bash
npx playwright install chromium   # la prima volta: scarica il browser di Playwright
npm run e2e
npm run e2e:report                # report HTML (playwright-report/, non versionato)
```

Servono JDK 25 (`JAVA_HOME`) e le porte libere. Variabili facoltative:

| Variabile                | Default | Descrizione                                                          |
| ------------------------ | ------- | -------------------------------------------------------------------- |
| `SAM_E2E_BACKEND_PORT`   | `18080` | porta del backend avviato dalla suite                                |
| `SAM_E2E_FRONTEND_PORT`  | `15173` | porta del server Vite avviato dalla suite                            |
| `SAM_E2E_BASE_URL`       | -       | usa uno stack già avviato (demo, database vuoto) invece di avviarlo  |
| `SAM_E2E_CHROMIUM`       | -       | eseguibile di Chromium da usare al posto di quello installato        |
| `SAM_E2E_SCREENSHOT_DIR` | -       | cartella in cui salvare gli screenshot del flusso (desktop e mobile) |

In PowerShell: `$env:SAM_E2E_BACKEND_PORT = "28080"; npm run e2e`; dal prompt dei comandi:
`set SAM_E2E_BACKEND_PORT=28080` e poi `npm run e2e`.

## Struttura

```
src/
├── api/          client openapi-fetch, tipi generati (schema.d.ts) e alias, query/mutation TanStack, errori
├── components/   barra superiore, elenco aziende, dettaglio (segnalazioni, riepilogo, mensilità), busta, caricamento,
│                 cedolini caricati con errore (elenco, associazione al mese, rilettura, inserimento a mano)
├── format/       formattazione italiana (importi "1.234,56 €", mesi "Gennaio 2024", quantità, anzianità)
├── hooks/        caricamento dei PDF con polling del lotto, anteprima del PDF, funzioni pure dell'inserimento a mano
├── i18n/         testi dell'interfaccia (solo italiano in fase 1, pronti per altre lingue)
├── mocks/        backend simulato in memoria con dati inventati (solo sviluppo e test)
└── test/         setup di Vitest e render con i provider
e2e/              suite end-to-end Playwright (npm run e2e)
```

## Caricamento dei cedolini

La modale accetta più PDF (massimo 25 MB ciascuno) e una **password facoltativa** valida per tutto il lotto, per i
PDF protetti: viene inviata come campo multipart `password` insieme ai file. Se non la si indica il backend prova le
password configurate e i codici fiscali delle persone già censite. Lo stato di ogni file (in coda, in lettura,
acquisito, duplicato, aggiuntivo non gestito, errore con motivo) è aggiornato in polling e il caricamento prosegue
anche chiudendo la modale; a lotto concluso i file da controllare (errori, aggiuntivi, duplicati) sono elencati per
primi. Al termine si aggiornano persone e dettagli; se non c'era una persona selezionata viene selezionata quella del
primo cedolino acquisito (`personaId` del caricamento), altrimenti quella di un duplicato o aggiuntivo. Gli omonimi
si distinguono nella tendina dal codice fiscale tra parentesi. Se il lotto ha file in errore la notifica finale
aggiunge "· N da completare"; nella modale i file in errore con il PDF conservato hanno il pulsante
**Inserisci a mano**.

### Cedolini caricati con errore

Un file caricato ma non letto (scansione senza testo, layout non riconosciuto, PDF protetto, lettore non
disponibile…) non è una mensilità mancante: finché non viene risolto compare nell'area rossa **Cedolini caricati con
errore** in cima alla pagina, anche senza una persona selezionata, con un contatore rosso nella barra superiore che
porta all'area. Ogni riga mostra nome del file, data, motivo, messaggio e mese associato; l'azione principale dipende
dal motivo e dal PDF conservato (tabella in `azioniPerCaricamento`, `src/hooks/useCedolinoManuale.ts`):

- **Riprova con password** per i PDF protetti, **Riprova** per i guasti transitori (lettore non disponibile,
  elaborazione interrotta, errore imprevisto): il PDF conservato viene riletto in un nuovo lotto, seguito nella modale
  di caricamento;
- **Inserisci a mano** per gli altri motivi;
- **Carica di nuovo** quando il PDF non è stato conservato (file danneggiato, troppe pagine, più cedolini nello stesso
  PDF, errori registrati prima dei motivi);
- **Rimuovi** (con conferma) quando il mese associato è nel frattempo diventato presente.

Le altre azioni sono nel menu "Altre azioni per …": associa a un mese (o cambia mese e rimuovi l'associazione),
scarica il PDF, rimuovi il file. Mentre un nuovo tentativo dello stesso file è in corso le azioni sono disabilitate.

**Associa a un mese**: l'utente indica persona, azienda e mensilità del file (chip delle mensilità mancanti oppure
"Altro mese…"), oppure parte da una mensilità mancante e sceglie il file. La mensilità associata diventa una riga rossa
"Caricato con errore" (con **Inserisci a mano** se il PDF è conservato) e la segnalazione "Cedolino caricato con
errore" prende il posto di "Mensilità mancante". L'associazione non viene mai indovinata.

**Inserisci a mano** apre una finestra a schermo intero con il PDF accanto al modulo (schede "Dati" / "PDF" sotto i
992 px; "Apri in una nuova scheda" per i telefoni che non mostrano i PDF negli iframe). Il modulo (`@mantine/form`) ha
tutti i campi del lettore, divisi in sezioni, senza dati bancari:

- dipendente e datore di lavoro si scelgono dall'archivio, per evitare doppioni da refusi: **"Persona in archivio"**
  ("Seleziona persona/dipendente": nome e cognome, con il codice fiscale per gli omonimi) e **"Azienda in archivio"**
  (ragione sociale, con P.IVA, codice fiscale o posizione INPS per le omonime; prima le aziende della persona scelta).
  La ricerca trova anche per codice fiscale o partita IVA, senza distinguere maiuscole e accenti. I dati anagrafici
  della scelta (codice fiscale, nome, cognome, data di nascita se in archivio; ragione sociale, P.IVA, codice
  fiscale, posizione INPS) sono in sola lettura e il server usa comunque quelli in archivio; matricola, posizione
  INAIL, codice azienda e indirizzo restano modificabili. Accanto alla tendina **"Crea nuovo"** / **"Crea nuova"**
  mostra i campi da scrivere come stampati (il focus va al primo) e "Scegli dall'elenco" riporta alla tendina; con
  l'archivio vuoto si parte direttamente da lì. Il modulo blocca un codice fiscale o una P.IVA/codice fiscale
  dell'azienda già in archivio (confronto incrociato); posizione INPS e ragione sociale le controlla il backend alla
  verifica. Senza scelta né "Crea nuovo" la verifica chiede di scegliere (un solo errore sulla tendina);
- è precompilato dal mese associato (periodo, con persona e azienda del suo rapporto già scelte) e, scelte persona e
  azienda, dall'indirizzo e dal codice azienda in archivio, dal loro rapporto (assunzione) e dal cedolino più vicino
  dello stesso rapporto (inquadramento, elementi retributivi, voci senza quantità e importi, ratei); il rapporto si
  legge alla scelta (con "Riprova" se la lettura fallisce). Si compilano solo i campi vuoti, segnati con un punto
  blu; cambiando una scelta i campi compilati in automatico si svuotano e si compilano di nuovo, quelli modificati
  restano, il periodo non cambia. La data di cessazione, i totali e quantità e importi delle voci non vengono mai
  copiati. Voci e ratei copiati sono modelli: finché restano senza quantità né importi (i ratei senza valori del
  mese) non vengono inviati, validati né contati, e una nota permette di toglierli; appena modificati diventano righe
  come le altre;
- i numeri si scrivono all'italiana ("1.234,56"; il punto non diventa mai la virgola decimale) e si possono incollare
  da un PDF ("1.234,56", "1234.56", "123,45-"): negli importi delle voci il segno incollato si ignora (lo dà la
  categoria), maturato e goduto dei ratei possono essere negativi, negli altri campi un valore negativo incollato è
  rifiutato con un messaggio. Al massimo 8 cifre intere e 5 decimali (quantità delle voci e valori dei ratei hanno 5
  decimali); una data incompleta o con l'anno di più di 4 cifre è un errore, non un campo vuoto;
- **Verifica** (anche con Ctrl+Invio / ⌘+Invio) controlla i campi obbligatori e i formati, poi chiede al backend
  come verrebbe registrato il cedolino (titolo, persona, azienda o rapporto nuovi, avvisi, duplicato) senza salvare
  nulla; gli errori del backend si posano sui campi (anche sulle tendine; un errore sulla scelta o sui dati
  anagrafici, come "… già in archivio", rilegge gli elenchi) e l'esito è annunciato ai lettori di schermo. Gli errori di riga e tra
  campi spariscono appena un'altra modifica li risolve. I controlli di coerenza (netto, somme delle voci, saldi dei
  ratei) sono avvisi arancioni che non bloccano;
- **Salva cedolino** è abilitato solo se l'ultima verifica riguarda esattamente i dati attuali ed è acquisibile;
  **Salva e apri il successivo** passa al prossimo file da completare (esclusi quelli associati a un mese che ha già
  il cedolino). Un 404 o 409 su un'azione di un file in errore aggiorna subito l'elenco. Il cedolino salvato è archiviato con il PDF
  originale e, nel dettaglio, mostra "Inserito a mano" al posto dell'affidabilità della lettura.

## Logo

Il logo è `public/logo.png`: **PNG quadrato 128×128 px con trasparenza**, usato nella barra superiore e come
favicon. Per sostituirlo basta sovrascrivere il file mantenendo **stesso nome, formato e dimensione**.

## Privacy

Nessun dato personale reale nel frontend, nei mock o nei test: solo nominativi, codici fiscali, aziende e importi
inventati.

Per i cedolini caricati con errore:

- l'anteprima del PDF conservato usa un URL `blob:` revocato alla chiusura della finestra, e il PDF non resta nella
  cache delle query;
- la password di una rilettura vive solo nella modale aperta e viene inviata una volta, senza essere salvata;
- il modulo dell'inserimento a mano non viene mai salvato nel browser come bozza (niente `localStorage`): chiudendolo
  con dati non salvati si chiede conferma;
- nei campi di testo libero (indirizzo, descrizione delle voci) l'interfaccia ricorda di non inserire IBAN o
  coordinate bancarie; i messaggi di errore dei campi non riportano mai i valori inseriti.
