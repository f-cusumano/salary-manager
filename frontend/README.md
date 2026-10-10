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
rapporto), buste espanse e download del PDF, eliminazione con ricalcolo, scelta e modifica del CCNL, note, vista
mobile senza scorrimento orizzontale.

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

- è precompilato dal mese associato (periodo), dal rapporto (anagrafiche e assunzione) e dal cedolino più vicino dello
  stesso rapporto (inquadramento, elementi retributivi, voci senza quantità e importi, ratei); "Copia dati da…" fa lo
  stesso a richiesta. Si compilano solo i campi vuoti, segnati con un punto blu; la data di cessazione, i totali e
  quantità e importi delle voci non vengono mai copiati;
- i numeri si scrivono all'italiana ("1.234,56"; il punto non diventa mai la virgola decimale) e si possono incollare
  da un PDF ("1.234,56", "1234.56", "123,45-");
- **Verifica** (anche con Ctrl+Invio / ⌘+Invio) controlla i campi obbligatori e i formati, poi chiede al backend
  come verrebbe registrato il cedolino (titolo, persona o rapporto nuovi, avvisi, duplicato) senza salvare nulla;
  gli errori del backend si posano sui campi. I controlli di coerenza (netto, somme delle voci, saldi dei ratei)
  sono avvisi arancioni che non bloccano;
- **Salva cedolino** è abilitato solo se l'ultima verifica riguarda esattamente i dati attuali ed è acquisibile;
  **Salva e apri il successivo** passa al prossimo file da completare. Il cedolino salvato è archiviato con il PDF
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
