import { mkdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { expect, test, type Locator, type Page } from '@playwright/test';

/**
 * Flusso completo contro il backend reale in modalità demo (lettore a fixture, database vuoto): caricamento
 * multiplo con tutti gli esiti, selezione automatica della persona, aziende e dettaglio, buste, download, CCNL,
 * note, eliminazione con ricalcolo. Solo dati inventati (fixture di backend/src/main/resources/fixtures).
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
      ['rossi-beta-2025-03.pdf', 'Errore', 'PDF protetto da password: indica la password e ricarica'],
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
    await page.getByRole('option', { name: /Terziario, distribuzione e servizi/ }).click();
    await expect(page.getByText('CCNL aggiornato: riepilogo e segnalazioni ricalcolati.')).toBeVisible();
    await expect(page.getByText('scelto da te')).toBeVisible();
    await expect(page.getByRole('article', { name: 'CCNL non determinato' })).toHaveCount(0);
    await expect(page.getByText('2.600,00 € × 14 mensilità')).toBeVisible();
    await expect(page.getByText('36.400,00 €')).toBeVisible();
    await screenshot(page, '09-ccnl-scelto', true);

    await page.getByRole('button', { name: 'Modifica il CCNL' }).click();
    await page.getByRole('combobox', { name: 'Seleziona il CCNL' }).click();
    await page.getByRole('option', { name: /Metalmeccanico industria/ }).click();
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
