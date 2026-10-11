import { mkdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { expect, test, type Locator, type Page } from '@playwright/test';

/**
 * Flusso completo contro il backend reale in modalità demo (lettore a fixture, database vuoto): caricamento
 * multiplo con tutti gli esiti, selezione automatica della persona, aziende e dettaglio, buste, download, CCNL,
 * note, eliminazione con ricalcolo, cedolini caricati con errore (associazione al mese, inserimento a mano con
 * persona e azienda scelte dagli elenchi o nuove, rimozione). Solo dati inventati (fixture di
 * backend/src/main/resources/fixtures e un PDF vuoto costruito qui).
 */

const CARTELLA_PDF = join(import.meta.dirname, '../../backend/src/main/resources/fixtures/pdf');
const CARTELLA_SCREENSHOT = process.env.SAM_E2E_SCREENSHOT_DIR;
const PASSWORD_BETA_2025_03 = 'demo-beta-2025';

interface FileDaCaricare {
  name: string;
  mimeType: string;
  buffer: Buffer;
}

function fixture(nome: string, nomeFile = `${nome}.pdf`): FileDaCaricare {
  return {
    name: nomeFile,
    mimeType: 'application/pdf',
    buffer: readFileSync(join(CARTELLA_PDF, `${nome}.pdf`)),
  };
}

/**
 * PDF valido di una pagina vuota (catalogo, pagine, pagina e tabella xref con gli offset corretti). Il commento lo
 * rende diverso da ogni fixture: il lettore a fixture non lo riconosce (ne' per nome, ne' per hash, ne' per testo) e
 * il file resta in errore con il PDF conservato, completabile a mano.
 */
function pdfMinimo(commento: string): Buffer {
  const oggetti = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << >> >>',
  ];
  let pdf = `%PDF-1.4\n% ${commento}\n`;
  const posizioni = oggetti.map((oggetto, i) => {
    const posizione = Buffer.byteLength(pdf, 'latin1');
    pdf += `${i + 1} 0 obj\n${oggetto}\nendobj\n`;
    return posizione;
  });
  const inizioXref = Buffer.byteLength(pdf, 'latin1');
  pdf += `xref\n0 ${oggetti.length + 1}\n0000000000 65535 f \n`;
  pdf += posizioni.map((p) => `${String(p).padStart(10, '0')} 00000 n \n`).join('');
  pdf += `trailer\n<< /Size ${oggetti.length + 1} /Root 1 0 R >>\nstartxref\n${inizioXref}\n%%EOF\n`;
  return Buffer.from(pdf, 'latin1');
}

function mesi(prefisso: string, anno: number, da: number, a: number, esclusi: number[] = []): string[] {
  const nomi: string[] = [];
  for (let mese = da; mese <= a; mese++) {
    if (!esclusi.includes(mese)) {
      nomi.push(`${prefisso}-${anno}-${String(mese).padStart(2, '0')}`);
    }
  }
  return nomi;
}

async function screenshot(page: Page, nome: string, intera = false) {
  if (!CARTELLA_SCREENSHOT) {
    return;
  }
  mkdirSync(CARTELLA_SCREENSHOT, { recursive: true });
  // attende la fine delle animazioni (espansioni, notifiche)
  await page.waitForTimeout(400);
  await page.screenshot({ path: join(CARTELLA_SCREENSHOT, `${nome}.png`), fullPage: intera });
}

function rigaFile(modale: Locator, nomeFile: string): Locator {
  return modale.locator('li', { has: modale.page().getByTitle(nomeFile, { exact: true }) });
}

async function attendiFineLotto(modale: Locator) {
  await expect(modale.getByText('Caricamento completato', { exact: true })).toBeVisible({ timeout: 90_000 });
}

function escapeRegExp(testo: string): string {
  return testo.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Nome accessibile della barra di una mensilità: azione e titolo, seguiti da indicatori, ratei e netto (il nome nasce
 * dal contenuto della barra).
 */
function nomeBarra(titolo: string, azione: 'Mostra' | 'Nascondi' = 'Mostra'): RegExp {
  return new RegExp(`^${azione} il dettaglio di ${escapeRegExp(titolo)}(?!\\d)`);
}

function barra(page: Page, titolo: string, azione: 'Mostra' | 'Nascondi' = 'Mostra'): Locator {
  return page.getByRole('button', { name: nomeBarra(titolo, azione) });
}

function busta(page: Page, titolo: string): Locator {
  return page.getByRole('listitem').filter({ has: barra(page, titolo, 'Nascondi') });
}

/** Valore di una riga etichetta/valore (lista di descrizioni). */
function valoreDi(contenitore: Locator, etichetta: string): Locator {
  return contenitore
    .locator('dt', { hasText: new RegExp(`^${escapeRegExp(etichetta)}$`) })
    .locator('xpath=following-sibling::dd[1]');
}

function azienda(page: Page, nome: string): Locator {
  return page.getByRole('button', { name: new RegExp(`dettaglio di ${nome}`) });
}

async function apriAzienda(page: Page, nome: string) {
  const voce = azienda(page, nome);
  if ((await voce.getAttribute('aria-expanded')) !== 'true') {
    await voce.click();
  }
  await expect(voce).toHaveAttribute('aria-expanded', 'true');
}

function areaInErrore(page: Page): Locator {
  return page.getByRole('region', { name: 'Cedolini caricati con errore' });
}

function fileInErrore(page: Page, nomeFile: string): Locator {
  return areaInErrore(page).getByRole('listitem', { name: `${nomeFile}: Caricato con errore` });
}

/** Campo del modulo di inserimento a mano (`data-path` = percorso dello schema CedolinoManuale). */
function campo(dialogo: Locator, percorso: string): Locator {
  return dialogo.locator(`[data-path="${percorso}"]`);
}

/**
 * Corpo di `POST /api/v1/caricamenti/{id}/cedolino(/verifica)` con persona e azienda nuove ("Crea nuovo") e importi
 * INVENTATI: per controllare le regole del backend senza passare dai controlli del browser.
 */
function cedolinoManualeNuoveAnagrafiche(
  dipendente: { codiceFiscale: string; nome: string; cognome: string },
  datoreLavoro: { ragioneSociale: string; partitaIva: string | null },
) {
  const vuoti = (...campi: string[]) => Object.fromEntries(campi.map((c) => [c, null]));
  return {
    personaId: null,
    aziendaId: null,
    dipendente: { ...dipendente, dataNascita: null, codiceDipendente: null },
    datoreLavoro: {
      ...vuoti('codiceFiscale', 'posizioneInps', 'posizioneInail', 'indirizzo', 'codiceAzienda'),
      ...datoreLavoro,
    },
    datiContrattuali: vuoti('dataAssunzione', 'dataCessazione', 'qualifica', 'livello', 'ccnl', 'codiceCnel'),
    periodo: { anno: 2023, mese: 4, tipo: 'ORDINARIA', dataElaborazione: null, meseVariabili: null },
    totali: { lordo: null, totaleCompetenze: 2100, totaleTrattenute: 450, arrotondamento: null, netto: 1650 },
    retribuzione: vuoti(
      'minimo',
      'contingenza',
      'superminimo',
      'superminimoAssorbibile',
      'importoScatti',
      'altriElementiFissi',
      'retribuzioneBase',
      'totale',
      'pagaOraria',
      'pagaGiornaliera',
      'divisoreOrario',
      'divisoreGiornaliero',
    ),
    voci: [],
    ratei: [],
    tfr: null,
    presenze: vuoti('giorniLavorati', 'oreLavorate', 'giorniRetribuiti'),
    scatti: vuoti('numeroScatti', 'dataProssimoScatto'),
    buoniPasto: { presenti: false, ...vuoti('numero', 'valoreUnitario', 'importo') },
    progressivi: vuoti('imponibilePrevidenziale', 'imponibileIrpef', 'irpefTrattenuta'),
    contieneTredicesima: false,
    contieneQuattordicesima: false,
  };
}

/** Sceglie una voce di una tendina del modulo (le opzioni sono fuori dalla finestra, in un portale). */
async function scegli(dialogo: Locator, percorso: string, opzione: string) {
  await campo(dialogo, percorso).click();
  await dialogo.page().getByRole('option', { name: opzione, exact: true }).click();
}

async function senzaScorrimentoOrizzontale(page: Page) {
  const larghezza = await page.evaluate(() => document.documentElement.scrollWidth);
  expect(larghezza).toBeLessThanOrEqual(page.viewportSize()?.width ?? 0);
}

test.describe.configure({ mode: 'serial' });

test.describe('Salary Manager in modalità demo', () => {
  let page: Page;

  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage();
  });

  test.afterAll(async () => {
    await page.close();
  });

  test('al primo avvio la tendina è vuota e l’area principale è vuota', async () => {
    await page.goto('/');
    const tendina = page.getByRole('combobox', { name: 'Persona' });
    await expect(tendina).toHaveAttribute('placeholder', 'Seleziona persona');
    await expect(tendina).toHaveValue('');
    await expect(page.getByRole('region', { name: 'Aziende' })).toHaveCount(0);
    await screenshot(page, '01-primo-avvio');
  });

  test('la modale scarta i file che non sono PDF prima dell’invio', async () => {
    await page.getByRole('button', { name: 'Carica cedolini' }).click();
    const modale = page.getByRole('dialog', { name: 'Carica cedolini' });
    await modale.locator('input[type="file"]').setInputFiles({
      name: 'appunti.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from('non sono un cedolino'),
    });
    await expect(modale.getByText('File non accettati')).toBeVisible();
    await expect(modale.getByText('appunti.txt: non è un PDF')).toBeVisible();
    await expect(modale.getByRole('button', { name: /Avvia caricamento/ })).toBeDisabled();
  });

  test('caricamento multiplo: stato per file e selezione automatica della persona', async () => {
    const modale = page.getByRole('dialog', { name: 'Carica cedolini' });
    const files = [
      ...[...mesi('rossi-alfa', 2023, 1, 13, [5]), ...mesi('rossi-alfa', 2024, 1, 6)].map((n) => fixture(n)),
      fixture('rossi-alfa-2024-03-aggiuntivo'),
      fixture('rossi-alfa-2024-tfr02'),
      fixture('rossi-alfa-2024-tfr01'),
      ...[...mesi('rossi-beta', 2024, 6, 12, [9]), ...mesi('rossi-beta', 2025, 1, 2)].map((n) => fixture(n)),
      fixture('rossi-beta-2024-07-14'),
      // protetto con il codice fiscale di Mario Rossi: si apre dopo il censimento, anche nello stesso lotto
      fixture('rossi-beta-2024-09'),
      // protetto con una password che il sistema non conosce
      fixture('rossi-beta-2025-03'),
      // stesso file con un altro nome
      fixture('rossi-alfa-2023-01', 'gennaio-2023-copia.pdf'),
      {
        name: 'danneggiato.pdf',
        mimeType: 'application/pdf',
        buffer: Buffer.from('%PDF-1.7\nnon valido\n%%EOF\n'),
      },
    ];
    await modale.locator('input[type="file"]').setInputFiles(files);
    await expect(modale.getByText(`File selezionati (${files.length})`)).toBeVisible();
    await screenshot(page, '02-modale-selezione');

    await modale.getByRole('button', { name: `Avvia caricamento (${files.length})` }).click();
    await expect(modale.getByRole('list', { name: 'Carica cedolini' })).toBeVisible();
    await screenshot(page, '03-modale-in-corso');
    await attendiFineLotto(modale);

    const attesi: [string, string, string?][] = [
      ['rossi-alfa-2023-01.pdf', 'Acquisito'],
      ['rossi-alfa-2023-13.pdf', 'Acquisito'],
      ['rossi-alfa-2024-tfr01.pdf', 'Acquisito'],
      ['rossi-alfa-2024-tfr02.pdf', 'Acquisito'],
      ['rossi-beta-2024-07-14.pdf', 'Acquisito'],
      ['rossi-beta-2024-09.pdf', 'Acquisito'],
      ['rossi-alfa-2024-03-aggiuntivo.pdf', 'Aggiuntivo non gestito', 'Cedolino aggiuntivo di Marzo 2024'],
      ['gennaio-2023-copia.pdf', 'Duplicato', 'Lo stesso file è già stato caricato: scartato'],
      ['rossi-beta-2025-03.pdf', 'Errore', 'PDF protetto da password: riprova indicando la password'],
      ['danneggiato.pdf', 'Errore', 'Il PDF è danneggiato o non leggibile'],
    ];
    for (const [nome, stato, messaggio] of attesi) {
      const riga = rigaFile(modale, nome);
      await expect(riga).toContainText(stato);
      if (messaggio) {
        await expect(riga).toContainText(messaggio);
      }
    }
    await expect(modale.getByText(`${files.length - 4} di ${files.length} acquisiti`)).toBeVisible();
    await screenshot(page, '04-modale-completata');

    await modale.getByRole('button', { name: 'Chiudi', exact: true }).click();
    await expect(page.getByRole('combobox', { name: 'Persona' })).toHaveValue('Mario Rossi');
    const aziende = page.getByRole('region', { name: 'Aziende' });
    const voci = aziende.getByRole('button', { name: /^(Apri|Chiudi) il dettaglio di/ });
    await expect(voci).toHaveCount(2);
    await expect(voci.nth(0)).toContainText('Beta Consulting S.p.A. (da Giugno 2024)');
    await expect(voci.nth(1)).toContainText('Alfa Software S.r.l. (da Settembre 2021 a Giugno 2024)');
    // di default è aperta l'azienda attuale; entrambe hanno segnalazioni
    await expect(voci.nth(0)).toHaveAttribute('aria-expanded', 'true');
    await expect(voci.nth(1)).toHaveAttribute('aria-expanded', 'false');
    await expect(voci.nth(0)).toHaveAccessibleName(/Ci sono segnalazioni/);
    await expect(voci.nth(1)).toHaveAccessibleName(/Ci sono segnalazioni/);
  });

  test('un secondo lotto con la password apre il PDF protetto; gli omonimi mostrano il codice fiscale', async () => {
    await page.getByRole('button', { name: 'Carica cedolini' }).click();
    // il lotto precedente è concluso: la modale riparte dalla selezione dei file
    const modale = page.getByRole('dialog', { name: 'Carica cedolini' });
    await modale
      .locator('input[type="file"]')
      .setInputFiles([
        fixture('rossi-beta-2025-03'),
        fixture('rossi2-beta-2025-01'),
        fixture('rossi2-beta-2025-02'),
      ]);
    await modale.getByLabel('Password dei PDF (facoltativa)').fill(PASSWORD_BETA_2025_03);
    await modale.getByRole('button', { name: 'Avvia caricamento (3)' }).click();
    await attendiFineLotto(modale);
    for (const nome of ['rossi-beta-2025-03.pdf', 'rossi2-beta-2025-01.pdf', 'rossi2-beta-2025-02.pdf']) {
      await expect(rigaFile(modale, nome)).toContainText('Acquisito');
    }
    await modale.getByRole('button', { name: 'Chiudi', exact: true }).click();

    // la persona selezionata resta la stessa, ora con il codice fiscale per distinguerla dall'omonimo
    const tendina = page.getByRole('combobox', { name: 'Persona' });
    await expect(tendina).toHaveValue('Mario Rossi (RSSMRA80A01H501U)');
    await tendina.click();
    await expect(page.getByRole('option', { name: 'Mario Rossi (RSSMRA85C12F205M)' })).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(barra(page, 'Marzo 2025')).toBeVisible();

    // il PDF protetto ora e' archiviato: tra i file in errore resta solo quello danneggiato, che non si completa a
    // mano (PDF non conservato)
    await expect(areaInErrore(page).getByRole('listitem')).toHaveCount(1);
    const danneggiato = fileInErrore(page, 'danneggiato.pdf');
    await expect(danneggiato).toContainText('File danneggiato o non PDF');
    await expect(danneggiato.getByRole('button', { name: 'Inserisci a mano' })).toHaveCount(0);
  });

  test('dettaglio dell’azienda attuale: segnalazioni, riepilogo e mensilità', async () => {
    await expect(page.getByRole('article', { name: 'CCNL non determinato' })).toBeVisible();
    await expect(page.getByText('Ferie e permessi residui')).toBeVisible();
    await expect(page.getByText('Quota in azienda')).toBeVisible();
    // RAL stimata (CCNL non indicato): 14 mensilità perché i cedolini documentano una quattordicesima
    await expect(page.getByText('2.600,00 € × 14 mensilità')).toBeVisible();
    await expect(page.getByText('36.400,00 €')).toBeVisible();
    await expect(
      page.getByLabel(
        'stima: CCNL non indicato: RAL calcolata su 14 mensilità (quattordicesima presente nei cedolini).',
      ),
    ).toBeVisible();
    // anzianità fino all'ultimo cedolino (non "a oggi"); prossimo scatto non determinabile senza CCNL
    const anzianita = page.getByRole('region', { name: 'Anzianità in azienda' });
    await expect(anzianita).toContainText('dal Giugno 2024 al Marzo 2025 (ultimo cedolino)');
    await expect(anzianita).not.toContainText('a oggi');
    await expect(valoreDi(anzianita, 'Prossimo scatto')).toHaveText('non determinabile (CCNL da indicare)');
    const mensilita = page.getByRole('button', { name: /^Mostra il dettaglio di / });
    await expect(mensilita.first()).toHaveAccessibleName(nomeBarra('Marzo 2025'));
    // il nome della barra comprende anche il netto
    await expect(mensilita.first()).toHaveAccessibleName(/Netto\s*[\d.]+,\d{2}\s*€$/);
    // la quattordicesima segue giugno (mese di pagamento non noto senza CCNL)
    const titoli = await mensilita.evaluateAll((barre) => barre.map((b) => b.textContent));
    expect(titoli.slice(-3).map((t) => t.replace(/^Mostra il dettaglio di /, ''))).toEqual([
      expect.stringMatching(/^Luglio 2024(?!\d)/),
      expect.stringMatching(/^Quattordicesima 2024(?!\d)/),
      expect.stringMatching(/^Giugno 2024(?!\d)/),
    ]);
    await screenshot(page, '05-azienda-attuale', true);
  });

  test('azienda passata: mensilità mancante, tredicesima, fine rapporto e TFR liquidato', async () => {
    await apriAzienda(page, 'Alfa Software');
    await expect(azienda(page, 'Beta Consulting')).toHaveAttribute('aria-expanded', 'false');
    await expect(page.getByRole('article', { name: 'Mensilità mancante: Maggio 2023' })).toBeVisible();
    await expect(page.getByRole('listitem', { name: 'Maggio 2023: Mensilità mancante' })).toBeVisible();
    await expect(barra(page, 'Tredicesima 2023')).toBeVisible();
    await expect(barra(page, 'Fine rapporto 2 – Settembre 2024')).toBeVisible();
    await expect(barra(page, 'Fine rapporto 1 – Luglio 2024')).toBeVisible();
    // rapporto concluso: niente ferie residue né quota in azienda, TFR liquidato e netto liquidato
    await expect(page.getByText('Ferie e permessi residui')).toHaveCount(0);
    await expect(page.getByText('TFR liquidato (lordo)')).toBeVisible();
    await expect(page.getByText('Netto liquidato')).toBeVisible();
    await screenshot(page, '06-azienda-passata', true);
  });

  test('più buste espanse insieme, dettaglio per aree e download del PDF con il nome normalizzato', async () => {
    await barra(page, 'Fine rapporto 1 – Luglio 2024').click();
    await barra(page, 'Tredicesima 2023').click();
    await expect(barra(page, 'Fine rapporto 1 – Luglio 2024', 'Nascondi')).toBeVisible();
    await expect(barra(page, 'Tredicesima 2023', 'Nascondi')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Competenze' })).toHaveCount(2);
    await expect(page.getByText('T.F.R. liquidato')).toBeVisible();
    // voce della tredicesima (la cella riporta codice e descrizione), distinta dai ratei del fine rapporto
    await expect(page.getByRole('cell', { name: /^8800\s*13ma Mensilita'$/ })).toBeVisible();
    await screenshot(page, '07-buste-espanse', true);

    const [download] = await Promise.all([
      page.waitForEvent('download'),
      busta(page, 'Fine rapporto 1 – Luglio 2024').getByRole('button', { name: 'Scarica PDF' }).click(),
    ]);
    expect(download.suggestedFilename()).toMatch(/^2024\.TFR01_\d+_\d+\.pdf$/);
    const scaricato = readFileSync(await download.path());
    expect(scaricato.equals(fixture('rossi-alfa-2024-tfr01').buffer)).toBe(true);
  });

  test('eliminazione di un cedolino con conferma e ricalcolo delle segnalazioni', async () => {
    await barra(page, 'Aprile 2023').click();
    await busta(page, 'Aprile 2023').getByRole('button', { name: 'Elimina' }).click();
    const conferma = page.getByRole('dialog', { name: 'Eliminare il cedolino?' });
    await conferma.getByRole('button', { name: 'Elimina cedolino' }).click();

    await expect(page.getByRole('listitem', { name: 'Aprile 2023: Mensilità mancante' })).toBeVisible();
    await expect(page.getByRole('article', { name: 'Mensilità mancante: Aprile 2023' })).toBeVisible();
    await expect(page.getByRole('article', { name: 'Mensilità mancante: Maggio 2023' })).toBeVisible();
  });

  test('eliminando il primo fine rapporto il secondo viene rinumerato e il dettaglio aperto si aggiorna', async () => {
    await barra(page, 'Fine rapporto 2 – Settembre 2024').click();
    const secondo = busta(page, 'Fine rapporto 2 – Settembre 2024');
    await expect(valoreDi(secondo, 'File')).toHaveText(/^2024\.TFR02_\d+_\d+\.pdf$/);

    await busta(page, 'Fine rapporto 1 – Luglio 2024').getByRole('button', { name: 'Elimina' }).click();
    await page
      .getByRole('dialog', { name: 'Eliminare il cedolino?' })
      .getByRole('button', { name: 'Elimina cedolino' })
      .click();

    // il cedolino di Settembre diventa TFR01, resta aperto e mostra il nuovo nome del file
    await expect(barra(page, 'Fine rapporto 1 – Luglio 2024', 'Nascondi')).toHaveCount(0);
    const rinumerato = busta(page, 'Fine rapporto 1 – Settembre 2024');
    await expect(rinumerato).toBeVisible();
    await expect(valoreDi(rinumerato, 'File')).toHaveText(/^2024\.TFR01_\d+_\d+\.pdf$/);
    await expect(barra(page, 'Fine rapporto 2 – Settembre 2024')).toHaveCount(0);
    await screenshot(page, '08-fine-rapporto-rinumerato', true);
  });

  test('CCNL scelto una volta e poi modificato con conferma: la RAL viene ricalcolata', async () => {
    await apriAzienda(page, 'Beta Consulting');
    await page.getByRole('combobox', { name: 'Seleziona il CCNL' }).click();
    await page.getByRole('option', { name: /Terziario, distribuzione e servizi \(Confcommercio\)/ }).click();
    await expect(page.getByText('CCNL aggiornato: riepilogo e segnalazioni ricalcolati.')).toBeVisible();
    await expect(page.getByText('scelto da te')).toBeVisible();
    await expect(page.getByRole('article', { name: 'CCNL non determinato' })).toHaveCount(0);
    await expect(page.getByText('2.600,00 € × 14 mensilità')).toBeVisible();
    await expect(page.getByText('36.400,00 €')).toBeVisible();
    await screenshot(page, '09-ccnl-scelto', true);

    await page.getByRole('button', { name: 'Modifica il CCNL' }).click();
    await page.getByRole('combobox', { name: 'Seleziona il CCNL' }).click();
    await page.getByRole('option', { name: /Industria metalmeccanica e installazione di impianti/ }).click();
    await page.getByRole('button', { name: 'Conferma', exact: true }).click();
    const dialogo = page.getByRole('dialog', { name: 'Cambiare il CCNL?' });
    await dialogo.getByRole('button', { name: 'Conferma' }).click();
    await expect(page.getByText('2.600,00 € × 13 mensilità')).toBeVisible();
    await expect(page.getByText('33.800,00 €')).toBeVisible();
  });

  test('le note vengono salvate e restano dopo il ricaricamento della pagina', async () => {
    const note = page.getByRole('textbox', { name: 'Note' });
    await note.fill('Colloquio di revisione retributiva previsto a Marzo');
    await page.getByRole('button', { name: 'Salva note' }).click();
    await expect(page.getByText('Note salvate')).toBeVisible();

    await page.reload();
    await expect(page.getByRole('textbox', { name: 'Note' })).toHaveValue(
      'Colloquio di revisione retributiva previsto a Marzo',
    );
  });

  test('un cedolino caricato ma non letto si associa al suo mese e si inserisce a mano', async ({
    browser,
  }) => {
    // 1. un PDF che il lettore non riconosce: resta in errore con il PDF conservato
    await page.getByRole('button', { name: 'Carica cedolini' }).click();
    const modale = page.getByRole('dialog', { name: 'Carica cedolini' });
    await modale.locator('input[type="file"]').setInputFiles({
      name: 'busta-maggio-2023.pdf',
      mimeType: 'application/pdf',
      buffer: pdfMinimo('busta di prova senza fixture'),
    });
    await modale.getByRole('button', { name: 'Avvia caricamento (1)' }).click();
    await attendiFineLotto(modale);
    const riga = rigaFile(modale, 'busta-maggio-2023.pdf');
    await expect(riga).toContainText('Errore');
    await expect(riga).toContainText('Nessuna fixture di estrazione associata al PDF');
    await expect(riga.getByRole('button', { name: 'Inserisci a mano' })).toBeVisible();
    await modale.getByRole('button', { name: 'Chiudi', exact: true }).click();

    // 2. elenco dei file in errore
    await expect(fileInErrore(page, 'busta-maggio-2023.pdf')).toBeVisible();
    await expect(fileInErrore(page, 'danneggiato.pdf')).toBeVisible();
    await expect(areaInErrore(page).getByRole('listitem')).toHaveCount(2);
    await screenshot(page, '12-file-in-errore');

    // 3. associazione alla mensilità mancante di Maggio 2023
    await areaInErrore(page).getByRole('button', { name: 'Altre azioni per busta-maggio-2023.pdf' }).click();
    await page.getByRole('menuitem', { name: 'Associa a un mese' }).click();
    const associa = page.getByRole('dialog', { name: /^Associa a un mese/ });
    await associa.getByRole('combobox', { name: 'Persona' }).click();
    await page.getByRole('option', { name: 'Mario Rossi (RSSMRA80A01H501U)' }).click();
    await associa.getByRole('combobox', { name: 'Azienda' }).click();
    await page.getByRole('option', { name: /^Alfa Software S\.r\.l\./ }).click();
    await associa.getByRole('group', { name: 'Mensilità' }).getByText('Maggio 2023', { exact: true }).click();
    await associa.getByRole('button', { name: 'Salva' }).click();
    await expect(associa).toHaveCount(0);
    await expect(fileInErrore(page, 'busta-maggio-2023.pdf')).toContainText(
      'Maggio 2023 · Alfa Software S.r.l.',
    );

    // 4. la mensilità non è più mancante ma caricata con errore, con la sua segnalazione
    await apriAzienda(page, 'Alfa Software');
    await expect(page.getByRole('listitem', { name: 'Maggio 2023: Caricato con errore' })).toBeVisible();
    await expect(
      page.getByRole('article', { name: 'Cedolino caricato con errore: Maggio 2023' }),
    ).toBeVisible();
    await expect(page.getByRole('article', { name: 'Mensilità mancante: Maggio 2023' })).toHaveCount(0);
    await expect(page.getByRole('article', { name: 'Mensilità mancante: Aprile 2023' })).toBeVisible();

    // 5. su mobile l'elenco e le due schede dell'inserimento a mano non scorrono in orizzontale
    const mobile = await browser.newContext({
      viewport: { width: 390, height: 844 },
      isMobile: true,
      hasTouch: true,
      locale: 'it-IT',
      timezoneId: 'Europe/Rome',
    });
    const pagina = await mobile.newPage();
    try {
      await pagina.goto('/');
      await expect(fileInErrore(pagina, 'busta-maggio-2023.pdf')).toBeVisible();
      await senzaScorrimentoOrizzontale(pagina);
      await fileInErrore(pagina, 'busta-maggio-2023.pdf')
        .getByRole('button', { name: 'Inserisci a mano' })
        .click();
      const dialogo = pagina.getByRole('dialog', { name: 'Inserisci a mano · busta-maggio-2023.pdf' });
      await expect(dialogo.getByRole('form', { name: 'Dati del cedolino' })).toBeVisible();
      await senzaScorrimentoOrizzontale(pagina);
      // sezione Dipendente: la tendina della persona e «Crea nuovo» restano nello schermo
      const creaNuovo = dialogo.getByRole('button', { name: 'Crea nuovo dipendente' });
      await creaNuovo.scrollIntoViewIfNeeded();
      await expect(dialogo.getByRole('combobox', { name: 'Persona in archivio' })).toBeVisible();
      const riquadro = await creaNuovo.boundingBox();
      expect((riquadro?.x ?? 0) + (riquadro?.width ?? 0)).toBeLessThanOrEqual(390);
      await senzaScorrimentoOrizzontale(pagina);
      await screenshot(pagina, '13-mobile-inserimento-dati');
      await dialogo.getByRole('tab', { name: 'PDF' }).click();
      await expect(dialogo.getByTitle('PDF del cedolino')).toBeVisible();
      await senzaScorrimentoOrizzontale(pagina);
      await screenshot(pagina, '14-mobile-inserimento-pdf');
      await dialogo.getByRole('tab', { name: 'Dati' }).click();
      await campo(dialogo, 'totali.netto').fill('1.650,00');
      await dialogo.getByRole('button', { name: 'Chiudi la finestra' }).click();
      const conferma = pagina.getByRole('dialog', { name: 'Chiudere senza salvare?' });
      await conferma.getByRole('button', { name: 'Chiudi senza salvare' }).click();
      await expect(dialogo).toHaveCount(0);
      // niente e' stato salvato: il file resta da completare
      await expect(fileInErrore(pagina, 'busta-maggio-2023.pdf')).toBeVisible();
    } finally {
      await mobile.close();
    }

    // 6. inserimento a mano dalla riga della mensilità: periodo dal mese associato, persona e azienda del suo
    // rapporto già scelte (dati anagrafici in archivio, in sola lettura)
    await page
      .getByRole('listitem', { name: 'Maggio 2023: Caricato con errore' })
      .getByRole('button', { name: 'Inserisci a mano' })
      .click();
    const dialogo = page.getByRole('dialog', { name: 'Inserisci a mano · busta-maggio-2023.pdf' });
    await expect(dialogo.getByRole('form', { name: 'Dati del cedolino' })).toBeVisible();
    await expect(dialogo.locator('iframe[title="PDF del cedolino"]')).toBeVisible();
    await expect(campo(dialogo, 'periodo.tipo')).toHaveValue('Mensilità ordinaria');
    await expect(campo(dialogo, 'periodo.mese')).toHaveValue('Maggio');
    await expect(campo(dialogo, 'periodo.anno')).toHaveValue('2023');
    const persona = dialogo.getByRole('combobox', { name: 'Persona in archivio' });
    const aziendaScelta = dialogo.getByRole('combobox', { name: 'Azienda in archivio' });
    // nell'archivio c'e' un omonimo: l'etichetta ha il codice fiscale
    await expect(persona).toHaveValue('Mario Rossi (RSSMRA80A01H501U)');
    await expect(aziendaScelta).toHaveValue('Alfa Software S.r.l.');
    await expect(campo(dialogo, 'dipendente.codiceFiscale')).toHaveValue('RSSMRA80A01H501U');
    await expect(campo(dialogo, 'dipendente.cognome')).toHaveValue('Rossi');
    await expect(campo(dialogo, 'dipendente.nome')).toHaveValue('Mario');
    await expect(campo(dialogo, 'datoreLavoro.ragioneSociale')).toHaveValue('Alfa Software S.r.l.');
    for (const percorso of [
      'dipendente.codiceFiscale',
      'dipendente.cognome',
      'dipendente.nome',
      'datoreLavoro.ragioneSociale',
    ]) {
      await expect(campo(dialogo, percorso)).toHaveAttribute('readonly', '');
    }

    // 6b. «Crea nuovo» con il codice fiscale di una persona in archivio: va scelta dall'elenco
    await dialogo.getByRole('button', { name: 'Crea nuovo dipendente' }).click();
    await expect(campo(dialogo, 'dipendente.codiceFiscale')).toHaveValue('');
    await expect(campo(dialogo, 'dipendente.codiceFiscale')).not.toHaveAttribute('readonly', '');
    await campo(dialogo, 'dipendente.codiceFiscale').fill('RSSMRA80A01H501U');
    await campo(dialogo, 'dipendente.cognome').fill('Rossi');
    await campo(dialogo, 'dipendente.nome').fill('Mario');
    await dialogo.getByRole('button', { name: 'Verifica', exact: true }).click();
    await expect(campo(dialogo, 'dipendente.codiceFiscale')).toHaveAttribute('aria-invalid', 'true');
    await expect(
      dialogo
        .getByText("Questo codice fiscale è già in archivio: seleziona la persona dall'elenco", {
          exact: true,
        })
        .first(),
    ).toBeVisible();
    await dialogo.getByRole('button', { name: 'Scegli dall’elenco delle persone' }).click();
    await persona.click();
    await page.getByRole('option', { name: 'Mario Rossi (RSSMRA80A01H501U)' }).click();
    await expect(campo(dialogo, 'dipendente.codiceFiscale')).toHaveValue('RSSMRA80A01H501U');
    await expect(campo(dialogo, 'dipendente.codiceFiscale')).toHaveAttribute('readonly', '');

    // 7. importi inventati
    await campo(dialogo, 'totali.totaleCompetenze').fill('2.100,00');
    await campo(dialogo, 'totali.totaleTrattenute').fill('450,00');
    await campo(dialogo, 'totali.netto').fill('1.650,00');

    // 7b. «Crea nuova» con una variante della ragione sociale di un'azienda in archivio: errore del server sul campo
    await dialogo.getByRole('button', { name: 'Crea nuova azienda' }).click();
    await campo(dialogo, 'datoreLavoro.ragioneSociale').fill('ALFA SOFTWARE SRL');
    await dialogo.getByRole('button', { name: 'Verifica', exact: true }).click();
    await expect(campo(dialogo, 'datoreLavoro.ragioneSociale')).toHaveAttribute('aria-invalid', 'true');
    await expect(
      dialogo
        .getByText("Un'azienda con questa ragione sociale è già in archivio: selezionala dall'elenco", {
          exact: true,
        })
        .first(),
    ).toBeVisible();
    await expect(campo(dialogo, 'datiContrattuali.dataAssunzione')).toHaveValue('');
    await dialogo.getByRole('button', { name: 'Scegli dall’elenco delle aziende' }).click();
    await aziendaScelta.click();
    const elencoAziende = page.getByRole('listbox');
    await expect(elencoAziende.getByText('Aziende di Mario Rossi (RSSMRA80A01H501U)')).toBeVisible();
    await expect(elencoAziende.getByRole('option').nth(0)).toHaveText('Beta Consulting S.p.A.');
    await expect(elencoAziende.getByRole('option').nth(1)).toHaveText('Alfa Software S.r.l.');
    await elencoAziende.getByRole('option', { name: 'Alfa Software S.r.l.' }).click();
    // di nuovo i dati del rapporto e del cedolino più vicino
    await expect(campo(dialogo, 'datiContrattuali.dataAssunzione')).not.toHaveValue('');
    await expect(campo(dialogo, 'datoreLavoro.ragioneSociale')).toHaveAttribute('readonly', '');
    await expect(campo(dialogo, 'voci.0.descrizione')).toHaveValue('Retribuzione ordinaria');
    await expect(campo(dialogo, 'voci.0.categoria')).toHaveValue('COMPETENZA');
    await campo(dialogo, 'voci.0.importoCompetenza').fill('2.100,00');
    await dialogo.getByRole('button', { name: 'Verifica', exact: true }).click();
    const esito = dialogo.getByRole('region', { name: 'Esito della verifica' });
    await expect(esito).toContainText('Sarà registrato come: Maggio 2023');
    await expect(esito.getByText('Dipendente: già presente')).toBeVisible();
    await expect(esito.getByText('Azienda: già presente')).toBeVisible();
    await expect(esito.getByText('Rapporto di lavoro: già presente')).toBeVisible();
    await screenshot(page, '15-inserimento-verificato');

    // 8. salvataggio: la mensilità diventa presente, inserita a mano
    await dialogo.getByRole('button', { name: 'Salva cedolino' }).click();
    await expect(page.getByText('Cedolino inserito: Maggio 2023')).toBeVisible();
    await expect(dialogo).toHaveCount(0);
    await expect(barra(page, 'Maggio 2023')).toBeVisible();
    await expect(page.getByRole('listitem', { name: 'Maggio 2023: Caricato con errore' })).toHaveCount(0);
    await expect(
      page.getByRole('article', { name: 'Cedolino caricato con errore: Maggio 2023' }),
    ).toHaveCount(0);
    await barra(page, 'Maggio 2023').click();
    await expect(busta(page, 'Maggio 2023')).toContainText('Inserito a mano');
    await expect(fileInErrore(page, 'busta-maggio-2023.pdf')).toHaveCount(0);

    // 9. il file danneggiato si rimuove: senza file in errore l'elenco sparisce
    await areaInErrore(page).getByRole('button', { name: 'Altre azioni per danneggiato.pdf' }).click();
    await page.getByRole('menuitem', { name: 'Rimuovi', exact: true }).click();
    await page
      .getByRole('dialog', { name: 'Rimuovere il file in errore?' })
      .getByRole('button', { name: 'Rimuovi file' })
      .click();
    await expect(areaInErrore(page)).toHaveCount(0);
    await expect(page.getByRole('listitem', { name: 'Aprile 2023: Mensilità mancante' })).toBeVisible();
  });

  test('inserimento a mano senza mese associato: persona e azienda scelte dagli elenchi, omonimi distinti', async () => {
    // 1. due PDF che il lettore non riconosce
    await page.getByRole('button', { name: 'Carica cedolini' }).click();
    const modale = page.getByRole('dialog', { name: 'Carica cedolini' });
    await modale.locator('input[type="file"]').setInputFiles([
      {
        name: 'busta-verdi.pdf',
        mimeType: 'application/pdf',
        buffer: pdfMinimo('busta di un nuovo dipendente'),
      },
      {
        name: 'busta-aprile-2023.pdf',
        mimeType: 'application/pdf',
        buffer: pdfMinimo('busta di aprile 2023'),
      },
    ]);
    await modale.getByRole('button', { name: 'Avvia caricamento (2)' }).click();
    await attendiFineLotto(modale);
    await modale.getByRole('button', { name: 'Chiudi', exact: true }).click();
    await expect(areaInErrore(page).getByRole('listitem')).toHaveCount(2);

    // 2. persona e azienda nuove: «Crea nuova» con la partita IVA di un'azienda in archivio e' rifiutata, con un'altra
    // partita IVA nasce un'omonima di Alfa Software
    await fileInErrore(page, 'busta-verdi.pdf').getByRole('button', { name: 'Inserisci a mano' }).click();
    const nuove = page.getByRole('dialog', { name: 'Inserisci a mano · busta-verdi.pdf' });
    await expect(nuove.getByRole('form', { name: 'Dati del cedolino' })).toBeVisible();
    await scegli(nuove, 'periodo.tipo', 'Mensilità ordinaria');
    await scegli(nuove, 'periodo.mese', 'Gennaio');
    await campo(nuove, 'periodo.anno').fill('2025');
    await nuove.getByRole('button', { name: 'Crea nuovo dipendente' }).click();
    await expect(campo(nuove, 'dipendente.codiceFiscale')).toBeFocused();
    await campo(nuove, 'dipendente.codiceFiscale').fill('VRDLGU90A01H501S');
    await campo(nuove, 'dipendente.cognome').fill('Verdi');
    await campo(nuove, 'dipendente.nome').fill('Luigi');
    await nuove.getByRole('button', { name: 'Crea nuova azienda' }).click();
    await campo(nuove, 'datoreLavoro.ragioneSociale').fill('Alfa Software S.r.l.');
    await campo(nuove, 'datoreLavoro.partitaIva').fill('01234567897');
    await campo(nuove, 'totali.totaleCompetenze').fill('1.000,00');
    await campo(nuove, 'totali.totaleTrattenute').fill('200,00');
    await campo(nuove, 'totali.netto').fill('800,00');
    await nuove.getByRole('button', { name: 'Verifica', exact: true }).click();
    await expect(campo(nuove, 'datoreLavoro.partitaIva')).toHaveAttribute('aria-invalid', 'true');
    await expect(
      nuove
        .getByText("Questa partita IVA è già in archivio: seleziona l'azienda dall'elenco", { exact: true })
        .first(),
    ).toBeVisible();
    await campo(nuove, 'datoreLavoro.partitaIva').fill('98765432103');
    await nuove.getByRole('button', { name: 'Verifica', exact: true }).click();
    const esitoNuove = nuove.getByRole('region', { name: 'Esito della verifica' });
    await expect(esitoNuove).toContainText('Sarà registrato come: Gennaio 2025');
    await expect(
      esitoNuove.getByText('Verrà creata una nuova persona: controlla il codice fiscale'),
    ).toBeVisible();
    await expect(
      esitoNuove.getByText(
        'Verrà creata una nuova azienda: controlla ragione sociale, partita IVA e codice fiscale',
      ),
    ).toBeVisible();
    await expect(esitoNuove.getByText('Nuovo rapporto di lavoro', { exact: true })).toBeVisible();
    await nuove.getByRole('button', { name: 'Salva cedolino' }).click();
    await expect(page.getByText('Cedolino inserito: Gennaio 2025')).toBeVisible();
    await expect(nuove).toHaveCount(0);
    // la persona selezionata resta Mario Rossi
    await expect(page.getByRole('combobox', { name: 'Persona' })).toHaveValue(
      'Mario Rossi (RSSMRA80A01H501U)',
    );

    // 3. il server rifiuta comunque una persona e un'azienda "nuove" che sono gia' in archivio
    const inErrore = (await (await page.request.get('/api/v1/caricamenti/in-errore')).json()) as {
      id: number;
      nomeFileOriginale: string;
    }[];
    const aprile = inErrore.find((f) => f.nomeFileOriginale === 'busta-aprile-2023.pdf');
    expect(aprile).toBeDefined();
    const rifiuto = await page.request.post(`/api/v1/caricamenti/${aprile?.id}/cedolino/verifica`, {
      data: cedolinoManualeNuoveAnagrafiche(
        { codiceFiscale: 'rssmra80a01h501u', nome: 'Mario', cognome: 'Rossi' },
        { ragioneSociale: 'Delta Servizi S.r.l.', partitaIva: 'IT01234567897' },
      ),
    });
    expect(rifiuto.status()).toBe(400);
    expect(((await rifiuto.json()) as { errori: unknown[] }).errori).toEqual([
      {
        campo: 'dipendente.codiceFiscale',
        messaggio: "Questo codice fiscale è già in archivio: seleziona la persona dall'elenco",
      },
      {
        campo: 'datoreLavoro.partitaIva',
        messaggio: "Questa partita IVA è già in archivio: seleziona l'azienda dall'elenco",
      },
    ]);

    // 4. senza mese associato nulla e' scelto: le tendine vanno scelte prima di verificare
    await fileInErrore(page, 'busta-aprile-2023.pdf')
      .getByRole('button', { name: 'Inserisci a mano' })
      .click();
    const dialogo = page.getByRole('dialog', { name: 'Inserisci a mano · busta-aprile-2023.pdf' });
    await expect(dialogo.getByRole('form', { name: 'Dati del cedolino' })).toBeVisible();
    const persona = dialogo.getByRole('combobox', { name: 'Persona in archivio' });
    const aziendaScelta = dialogo.getByRole('combobox', { name: 'Azienda in archivio' });
    await expect(persona).toHaveAttribute('placeholder', 'Seleziona persona/dipendente');
    await expect(persona).toHaveValue('');
    await expect(aziendaScelta).toHaveAttribute('placeholder', 'Seleziona azienda');
    await expect(aziendaScelta).toHaveValue('');
    await expect(campo(dialogo, 'dipendente.codiceFiscale')).toHaveCount(0);
    await expect(campo(dialogo, 'datoreLavoro.ragioneSociale')).toHaveCount(0);
    await dialogo.getByRole('button', { name: 'Verifica', exact: true }).click();
    await expect(persona).toHaveAttribute('aria-invalid', 'true');
    await expect(
      dialogo.getByText("Scegli la persona dall'elenco o «Crea nuovo»", { exact: true }).first(),
    ).toBeVisible();
    await expect(
      dialogo.getByText("Scegli l'azienda dall'elenco o «Crea nuova»", { exact: true }).first(),
    ).toBeVisible();
    await scegli(dialogo, 'periodo.tipo', 'Mensilità ordinaria');
    await scegli(dialogo, 'periodo.mese', 'Aprile');
    await campo(dialogo, 'periodo.anno').fill('2023');

    // 5. persona dall'elenco: gli omonimi hanno il codice fiscale, i dati anagrafici sono quelli in archivio
    await persona.click();
    await expect(page.getByRole('option')).toHaveText([
      'Mario Rossi (RSSMRA80A01H501U)',
      'Mario Rossi (RSSMRA85C12F205M)',
      'Luigi Verdi',
    ]);
    await page.getByRole('option', { name: 'Mario Rossi (RSSMRA80A01H501U)' }).click();
    await expect(persona).toHaveValue('Mario Rossi (RSSMRA80A01H501U)');
    for (const [percorso, valore] of [
      ['dipendente.codiceFiscale', 'RSSMRA80A01H501U'],
      ['dipendente.cognome', 'Rossi'],
      ['dipendente.nome', 'Mario'],
      ['dipendente.dataNascita', '1980-01-01'],
    ] as const) {
      await expect(campo(dialogo, percorso)).toHaveValue(valore);
      await expect(campo(dialogo, percorso)).toHaveAttribute('readonly', '');
    }

    // 6. azienda dall'elenco: prima quelle della persona, le omonime con la partita IVA; la ricerca trova la P.IVA
    await aziendaScelta.click();
    const elenco = page.getByRole('listbox');
    await expect(elenco.getByText('Aziende di Mario Rossi (RSSMRA80A01H501U)')).toBeVisible();
    await expect(elenco.getByText('Altre aziende')).toBeVisible();
    await expect(elenco.getByRole('option')).toHaveText([
      'Beta Consulting S.p.A.',
      'Alfa Software S.r.l. (P.IVA 01234567897)',
      'Alfa Software S.r.l. (P.IVA 98765432103)',
    ]);
    await aziendaScelta.fill('98765');
    await expect(elenco.getByRole('option')).toHaveText(['Alfa Software S.r.l. (P.IVA 98765432103)']);
    await aziendaScelta.fill('01234567897');
    await elenco.getByRole('option', { name: 'Alfa Software S.r.l. (P.IVA 01234567897)' }).click();
    await expect(aziendaScelta).toHaveValue('Alfa Software S.r.l. (P.IVA 01234567897)');

    // 7. precompilati dall'azienda, dal rapporto e dal cedolino piu' vicino
    for (const [percorso, valore] of [
      ['datoreLavoro.ragioneSociale', 'Alfa Software S.r.l.'],
      ['datoreLavoro.partitaIva', '01234567897'],
      ['datoreLavoro.posizioneInps', '1122334455'],
    ] as const) {
      await expect(campo(dialogo, percorso)).toHaveValue(valore);
      await expect(campo(dialogo, percorso)).toHaveAttribute('readonly', '');
    }
    await expect(campo(dialogo, 'datoreLavoro.codiceAzienda')).toHaveValue('ALFA01');
    await expect(campo(dialogo, 'datoreLavoro.indirizzo')).toHaveValue('Via Roma 1, 00100 Roma (RM)');
    await expect(campo(dialogo, 'datiContrattuali.dataAssunzione')).toHaveValue('2021-09-01');
    await expect(campo(dialogo, 'dipendente.codiceDipendente')).toHaveValue('000123');
    await expect(campo(dialogo, 'voci.0.descrizione')).toHaveValue('Retribuzione ordinaria');
    await screenshot(page, '16-inserimento-scelte-dagli-elenchi');

    // 8. importi inventati, verifica e salvataggio nel rapporto scelto: la mensilita' mancante torna presente
    await campo(dialogo, 'totali.totaleCompetenze').fill('2.100,00');
    await campo(dialogo, 'totali.totaleTrattenute').fill('450,00');
    await campo(dialogo, 'totali.netto').fill('1.650,00');
    await campo(dialogo, 'voci.0.importoCompetenza').fill('2.100,00');
    await dialogo.getByRole('button', { name: 'Verifica', exact: true }).click();
    const esito = dialogo.getByRole('region', { name: 'Esito della verifica' });
    await expect(esito).toContainText('Sarà registrato come: Aprile 2023');
    await expect(esito.getByText('Dipendente: già presente')).toBeVisible();
    await expect(esito.getByText('Azienda: già presente')).toBeVisible();
    await expect(esito.getByText('Rapporto di lavoro: già presente')).toBeVisible();
    await dialogo.getByRole('button', { name: 'Salva cedolino' }).click();
    await expect(page.getByText('Cedolino inserito: Aprile 2023')).toBeVisible();
    await expect(dialogo).toHaveCount(0);
    await expect(areaInErrore(page)).toHaveCount(0);
    await apriAzienda(page, 'Alfa Software');
    await expect(page.getByRole('listitem', { name: 'Aprile 2023: Mensilità mancante' })).toHaveCount(0);
    await expect(page.getByRole('article', { name: 'Mensilità mancante: Aprile 2023' })).toHaveCount(0);
    await barra(page, 'Aprile 2023').click();
    await expect(busta(page, 'Aprile 2023')).toContainText('Inserito a mano');
  });

  test('su mobile la pagina non scorre in orizzontale', async ({ browser }) => {
    const mobile = await browser.newContext({
      viewport: { width: 390, height: 844 },
      isMobile: true,
      hasTouch: true,
      locale: 'it-IT',
      timezoneId: 'Europe/Rome',
    });
    const pagina = await mobile.newPage();
    try {
      await pagina.goto('/');
      await pagina.getByRole('combobox', { name: 'Persona' }).click();
      await pagina.getByRole('option', { name: 'Mario Rossi (RSSMRA80A01H501U)' }).click();
      await expect(pagina.getByRole('region', { name: 'Aziende' })).toBeVisible();
      await expect(barra(pagina, 'Marzo 2025')).toBeVisible();
      await barra(pagina, 'Marzo 2025').click();
      await expect(pagina.getByRole('heading', { name: 'Competenze' })).toBeVisible();
      const larghezza = await pagina.evaluate(() => document.documentElement.scrollWidth);
      expect(larghezza).toBeLessThanOrEqual(390);
      await screenshot(pagina, '10-mobile-dettaglio', true);

      await pagina.getByRole('button', { name: 'Carica cedolini' }).click();
      await expect(pagina.getByRole('dialog', { name: 'Carica cedolini' })).toBeVisible();
      await screenshot(pagina, '11-mobile-modale');
    } finally {
      await mobile.close();
    }
  });
});
