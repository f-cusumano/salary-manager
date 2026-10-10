# Fixture di estrazione (dati inventati)

Cedolini fittizi usati dal lettore a fixture (`salary-manager.lettore=fixture`, variabile `SAM_LETTORE=fixture`):
test end-to-end, sviluppo offline e modalita' demo. **Nessun dato reale**: persone, codici fiscali, aziende, indirizzi e importi
sono inventati (i codici fiscali e la partita IVA hanno solo il carattere di controllo formalmente corretto).

- `cedolini/<nome>.json`: output di estrazione nel formato dello structured output
  (schema `prompts/cedolino-schema-v1.json`: stringa vuota = dato non stampato, importi come stringhe decimali,
  date ISO). Passano dalla stessa validazione degli altri lettori (locale e Claude).
- `pdf/<nome>.pdf`: PDF di una pagina con il testo "Cedolino di prova" e la riga `Fixture: <nome>`.

Il lettore associa un PDF caricato alla fixture tramite (in ordine) il nome del file caricato, l'hash SHA-256
del PDF, la riga `Fixture: <nome>` nel testo del PDF.

## PDF protetto da password

`pdf/rossi-beta-2024-09.pdf` e' cifrato (AES-256) con password di apertura **`rssmra80a01h501u`**
(il codice fiscale fittizio di Mario Rossi in minuscolo). Si apre indicando la password nel caricamento oppure,
se Mario Rossi (`RSSMRA80A01H501U`) e' gia' censito, automaticamente tramite i codici fiscali delle persone.

`pdf/rossi-beta-2025-03.pdf` e' cifrato (AES-256) con password **`demo-beta-2025`**, che non e' un codice fiscale:
si apre solo indicandola nel caricamento (o in `salary-manager.pdf.password-candidate`). Senza password il file
resta in errore "PDF protetto da password".

Un PDF protetto che si apre con il codice fiscale di una persona censita **nello stesso lotto** (per esempio
`rossi-beta-2024-09` caricato insieme agli altri cedolini di Mario Rossi) non fallisce se viene letto prima che la
persona sia salvata: a fine lotto viene riprovato una volta, quando i codici fiscali sono disponibili.

## Scenario

| Persona | Azienda | Fixture | Casi coperti |
|---|---|---|---|
| Mario Rossi `RSSMRA80A01H501U` | Alfa Software S.r.l. (P.IVA `01234567897`, INPS `1122334455`, CCNL stampato) | `rossi-alfa-2023-01` ... `rossi-alfa-2024-06` | mensilita' ordinarie 2023-2024 con **maggio 2023 mancante**; retribuzione scomposta (minimo + superminimo assorbibile + scatti); **rinnovo CCNL a giugno 2023** (minimo da 2000,00 a 2123,50, superminimo assorbito da 300,00 a 176,50, totale invariato); **scatto di anzianita' a settembre 2023** (prossimo scatto stampato); **malattia** a marzo 2023; rimborso chilometrico ad aprile 2023; **anticipo TFR** a febbraio 2024; ferie in giorni e ROL in ore; variabili del mese precedente; arrotondamento del netto |
| | | `rossi-alfa-2023-13` | **tredicesima separata** (periodo "Dicembre AGG." con voce "13ma Mensilita'": tipo TREDICESIMA, mese 13) |
| | | `rossi-alfa-2024-03-aggiuntivo` | cedolino aggiuntivo (premio) nello stesso mese di un ordinario: tipo AGGIUNTIVA |
| | | `rossi-alfa-2024-06` | **cessazione il 15/06/2024** (mese parziale, ordinario) |
| | | `rossi-alfa-2024-tfr01` | **fine rapporto** (luglio 2024): TFR liquidato e ritenuta, ferie e ROL non goduti, ratei di tredicesima |
| | | `rossi-alfa-2024-tfr02` | **secondo cedolino di fine rapporto** (settembre 2024): arretrati liquidati dopo la cessazione (tipo FINE_RAPPORTO, codice `TFR02`) |
| | Beta Consulting S.p.A. (CF/P.IVA non stampati, INPS `7012345678/01`, CCNL non stampato) | `rossi-beta-2024-06` ... `rossi-beta-2025-02` | **assunzione il 16/06/2024** (cambio azienda a meta' mese); retribuzione base non scomposta con paga oraria/giornaliera e divisori; **buoni pasto**; ferie in giorni, ROL ed ex festivita' in ore; **tredicesima inclusa nel cedolino di dicembre 2024**; fondo TFR al 31/12 dal 2025 |
| | | `rossi-beta-2024-07-14` | **quattordicesima separata** (luglio 2024, tipo QUATTORDICESIMA, mese 14) |
| | | `rossi-beta-2025-03` | ordinaria di marzo 2025 in un **PDF protetto da una password che non e' un codice fiscale** |
| Mario Rossi `RSSMRA85C12F205M` (omonimo) | Beta Consulting S.p.A. | `rossi2-beta-2025-01`, `rossi2-beta-2025-02` | persona omonima con codice fiscale diverso |

Solo per i test (in `backend/src/test/resources/fixtures/`): `test-due-cedolini` (PDF con due cedolini),
`test-non-cedolino` (documento che non e' un cedolino), `test-incoerente` (netto incoerente e codice fiscale con
carattere di controllo errato: estratto con avvisi e confidenza ridotta).

I totali sono coerenti (competenze - trattenute + arrotondamento = netto), i saldi dei ratei
(residuo AP + maturato - goduto) e il fondo TFR al 31/12/2023 (fondo 2022 + rivalutazione - imposta sostitutiva +
quote 2023) quadrano.
