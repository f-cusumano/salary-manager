import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { App } from './App';
import { chiavi } from './api/chiavi';
import {
  TITOLO_AREA_IN_ERRORE,
  areaInErrore,
  azioneDalMenu,
  inputFile,
  pdf,
  rigaInErrore,
  selezionaPersona,
  type Utente,
} from './test/app';
import { renderConMock } from './test/render';

// Dataset "errori": i dati dimostrativi piu' busta-scansione.pdf (scansione, non associata), cedolino-protetto.pdf
// (protetto da password) e ristampa.pdf (layout non riconosciuto, associata ad Agosto 2025 di Alfa Software).

const POLLING_TEST_MS = 20;

/** Carica i file dalla modale e attende la fine del lotto; restituisce la modale ancora aperta. */
async function caricaFile(user: Utente, ...nomi: string[]): Promise<HTMLElement> {
  await user.click(screen.getByRole('button', { name: 'Carica cedolini' }));
  const modale = await screen.findByRole('dialog', { name: 'Carica cedolini' });
  await user.upload(inputFile(modale), nomi.map(pdf));
  await user.click(within(modale).getByRole('button', { name: `Avvia caricamento (${nomi.length})` }));
  await within(modale).findByText('Caricamento completato', {}, { timeout: 3000 });
  return modale;
}

async function mensilitaDiAlfa(user: Utente): Promise<HTMLElement> {
  await selezionaPersona(user, 'Mario Rossi');
  return (await screen.findByRole('heading', { name: 'Mensilità' })).closest('section') ?? document.body;
}

describe('elenco dei cedolini caricati con errore', () => {
  it('è visibile anche senza persona selezionata e distinto dalle mensilità mancanti', async () => {
    renderConMock(<App />, { datiIniziali: 'errori' });
    const area = await areaInErrore();

    // dal caricamento più recente, uno per file
    expect(
      within(area)
        .getAllByRole('listitem')
        .map((riga) => riga.getAttribute('aria-label')),
    ).toEqual([
      'busta-scansione.pdf: Caricato con errore',
      'cedolino-protetto.pdf: Caricato con errore',
      'ristampa.pdf: Caricato con errore',
    ]);
    expect(
      within(area).getByText(/finché non lo fai il mese resta «Mensilità mancante»/),
    ).toBeInTheDocument();
    expect(within(area).queryByRole('listitem', { name: /Mensilità mancante/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Aziende' })).not.toBeInTheDocument();

    const ristampa = rigaInErrore(area, 'ristampa.pdf');
    expect(within(ristampa).getByText('Layout non riconosciuto')).toBeInTheDocument();
    expect(within(ristampa).getByText('Agosto 2025 · Alfa Software S.r.l.')).toBeInTheDocument();
    expect(within(ristampa).getByText(/non riconosce il layout/)).toBeInTheDocument();
    const scansione = rigaInErrore(area, 'busta-scansione.pdf');
    expect(within(scansione).getByText('Scansione senza testo')).toBeInTheDocument();
    expect(within(scansione).getByText('Non associato a un mese')).toBeInTheDocument();
    expect(within(scansione).getByText('1 Ottobre 2026, 11:20')).toBeInTheDocument();
  });

  it('il contatore della barra superiore porta all’elenco', async () => {
    const { user } = renderConMock(<App />, { datiIniziali: 'errori' });
    const scorrimento = vi.spyOn(HTMLElement.prototype, 'scrollIntoView');
    const contatore = await screen.findByRole('button', { name: `${TITOLO_AREA_IN_ERRORE} (3)` });
    expect(within(screen.getByRole('banner')).getByText('3')).toBeInTheDocument();

    await user.click(contatore);
    const area = await areaInErrore();
    expect(scorrimento).toHaveBeenCalledOnce();
    expect(scorrimento.mock.contexts[0]).toBe(area);
    expect(area).toHaveFocus();
  });

  it('senza file in errore non mostra né l’elenco né il contatore', async () => {
    const { queryClient } = renderConMock(<App />);
    await waitFor(() =>
      expect(queryClient.getQueryState(chiavi.caricamentiInErrore)?.status).toBe('success'),
    );
    expect(screen.queryByRole('region', { name: TITOLO_AREA_IN_ERRORE })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Cedolini caricati con errore/ })).not.toBeInTheDocument();
  });

  it('propone come azione principale quella adatta al motivo, le altre nel menu', async () => {
    const { user } = renderConMock(<App />, { datiIniziali: 'errori' });
    const area = await areaInErrore();
    const pulsanti = (nome: string) =>
      within(rigaInErrore(area, nome))
        .getAllByRole('button')
        .map((b) => b.getAttribute('aria-label') ?? b.textContent);

    expect(pulsanti('busta-scansione.pdf')).toEqual([
      'Inserisci a mano',
      'Altre azioni per busta-scansione.pdf',
    ]);
    expect(pulsanti('cedolino-protetto.pdf')).toEqual([
      'Riprova con password',
      'Altre azioni per cedolino-protetto.pdf',
    ]);
    expect(pulsanti('ristampa.pdf')).toEqual(['Inserisci a mano', 'Altre azioni per ristampa.pdf']);

    await user.click(within(area).getByRole('button', { name: 'Altre azioni per ristampa.pdf' }));
    const menu = await screen.findByRole('menu');
    expect(
      within(menu)
        .getAllByRole('menuitem')
        .map((voce) => voce.textContent),
    ).toEqual(['Riprova', 'Cambia mese', 'Rimuovi associazione', 'Scarica PDF', 'Rimuovi']);
  });

  it('un file senza PDF conservato si può solo caricare di nuovo', async () => {
    const { user } = renderConMock(<App intervalloPollingMs={POLLING_TEST_MS} />, { datiIniziali: 'errori' });
    const modale = await caricaFile(user, 'danneggiato.pdf', 'scansione-maggio.pdf');

    // nella modale l'inserimento a mano c'è solo per il file con il PDF conservato
    expect(
      within(within(modale).getByText('danneggiato.pdf').closest('li') ?? modale).queryByRole('button'),
    ).not.toBeInTheDocument();
    expect(
      within(within(modale).getByText('scansione-maggio.pdf').closest('li') ?? modale).getByRole('button', {
        name: 'Inserisci a mano',
      }),
    ).toBeInTheDocument();
    expect(await screen.findByText('Errore: 2 · 2 da completare')).toBeInTheDocument();
    await user.click(within(modale).getByRole('button', { name: 'Chiudi' }));

    const area = await areaInErrore();
    const danneggiato = await within(area).findByRole('listitem', {
      name: 'danneggiato.pdf: Caricato con errore',
    });
    expect(within(danneggiato).getByText('File danneggiato o non PDF')).toBeInTheDocument();
    expect(
      within(danneggiato).getByText('Il file non è un PDF leggibile: caricane una copia valida.'),
    ).toBeInTheDocument();
    expect(within(danneggiato).queryByRole('button', { name: 'Inserisci a mano' })).not.toBeInTheDocument();
    await user.click(within(danneggiato).getByRole('button', { name: 'Altre azioni per danneggiato.pdf' }));
    expect((await screen.findAllByRole('menuitem')).map((voce) => voce.textContent)).toEqual([
      'Associa a un mese',
      'Rimuovi',
    ]);
    await user.keyboard('{Escape}');

    await user.click(within(danneggiato).getByRole('button', { name: 'Carica di nuovo' }));
    const nuova = await screen.findByRole('dialog', { name: 'Carica cedolini' });
    expect(
      within(nuova).getByText('Trascina qui i cedolini PDF o fai clic per sceglierli'),
    ).toBeInTheDocument();
  });

  it('rimuove un file in errore solo dopo la conferma', async () => {
    const { user, backend } = renderConMock(<App />, { datiIniziali: 'errori' });
    const area = await areaInErrore();

    await azioneDalMenu(user, area, 'busta-scansione.pdf', 'Rimuovi');
    let conferma = await screen.findByRole('dialog', { name: 'Rimuovere il file in errore?' });
    expect(conferma).toHaveTextContent(/busta-scansione\.pdf.*Per recuperarlo dovrai caricarlo di nuovo/);
    await user.click(within(conferma).getByRole('button', { name: 'Annulla' }));
    expect(rigaInErrore(area, 'busta-scansione.pdf')).toBeInTheDocument();

    await azioneDalMenu(user, area, 'busta-scansione.pdf', 'Rimuovi');
    conferma = await screen.findByRole('dialog', { name: 'Rimuovere il file in errore?' });
    await user.click(within(conferma).getByRole('button', { name: 'Rimuovi file' }));

    expect(await screen.findByText('File rimosso: busta-scansione.pdf')).toBeInTheDocument();
    await waitFor(() =>
      expect(
        within(area).queryByRole('listitem', { name: 'busta-scansione.pdf: Caricato con errore' }),
      ).not.toBeInTheDocument(),
    );
    expect(screen.getByRole('button', { name: `${TITOLO_AREA_IN_ERRORE} (2)` })).toBeInTheDocument();
    expect(backend.db.caricamenti.some((c) => c.nomeFileOriginale === 'busta-scansione.pdf')).toBe(false);
    expect(backend.db.pdfFile.size).toBe(2);
  });

  it('riprova con la password e segue il nuovo lotto fino all’acquisizione', async () => {
    const { user } = renderConMock(<App intervalloPollingMs={POLLING_TEST_MS} />, { datiIniziali: 'errori' });
    const area = await areaInErrore();
    await user.click(
      within(rigaInErrore(area, 'cedolino-protetto.pdf')).getByRole('button', {
        name: 'Riprova con password',
      }),
    );

    const rilettura = await screen.findByRole('dialog', {
      name: 'Riprova con password · cedolino-protetto.pdf',
    });
    const password = within(rilettura).getByLabelText('Password del PDF');
    expect(password).toHaveAttribute('type', 'password');
    await user.type(password, 'segreta');
    await user.click(within(rilettura).getByRole('button', { name: 'Rileggi' }));

    // la rilettura è un nuovo lotto: si apre la modale di caricamento che ne segue lo stato
    const modale = await screen.findByRole('dialog', { name: 'Carica cedolini' });
    expect(screen.queryByRole('dialog', { name: /Riprova con password/ })).not.toBeInTheDocument();
    expect(
      await within(modale).findByText('Caricamento completato', {}, { timeout: 3000 }),
    ).toBeInTheDocument();
    expect(within(modale).getByRole('listitem')).toHaveTextContent(/cedolino-protetto\.pdfAcquisito/);
    await waitFor(() =>
      expect(
        within(area).queryByRole('listitem', { name: 'cedolino-protetto.pdf: Caricato con errore' }),
      ).not.toBeInTheDocument(),
    );
    expect(screen.getByRole('button', { name: `${TITOLO_AREA_IN_ERRORE} (2)` })).toBeInTheDocument();
  });

  it('durante la rilettura il file resta in elenco con le azioni disabilitate', async () => {
    // polling lento: il nuovo tentativo resta in lettura per tutto il test
    const { user } = renderConMock(<App intervalloPollingMs={60_000} />, { datiIniziali: 'errori' });
    const area = await areaInErrore();
    await azioneDalMenu(user, area, 'busta-scansione.pdf', 'Riprova');

    const rilettura = await screen.findByRole('dialog', { name: 'Riprova la lettura · busta-scansione.pdf' });
    expect(within(rilettura).getByLabelText('Password del PDF (facoltativa)')).toHaveValue('');
    await user.click(within(rilettura).getByRole('button', { name: 'Rileggi' }));
    const modale = await screen.findByRole('dialog', { name: 'Carica cedolini' });
    await user.click(within(modale).getByRole('button', { name: 'Chiudi' }));

    const riga = rigaInErrore(area, 'busta-scansione.pdf');
    expect(await within(riga).findByText('In elaborazione')).toBeInTheDocument();
    expect(riga).toHaveAttribute('aria-busy', 'true');
    expect(within(riga).getByRole('button', { name: 'Inserisci a mano' })).toHaveAttribute(
      'aria-disabled',
      'true',
    );
    expect(within(riga).getByRole('button', { name: 'Altre azioni per busta-scansione.pdf' })).toBeDisabled();
    // un secondo lotto non parte finché il primo è in corso
    await user.click(
      within(rigaInErrore(area, 'cedolino-protetto.pdf')).getByRole('button', {
        name: 'Riprova con password',
      }),
    );
    const altra = await screen.findByRole('dialog', { name: 'Riprova con password · cedolino-protetto.pdf' });
    expect(within(altra).getByText('Attendi la fine del caricamento in corso.')).toBeInTheDocument();
    expect(within(altra).getByRole('button', { name: 'Rileggi' })).toBeDisabled();
  });

  // la finestra dell'inserimento a mano monta ogni volta il modulo completo e l'anteprima del PDF: tre aperture
  it(
    'apre l’inserimento a mano dall’elenco, dalla mensilità in errore e dalla modale di caricamento',
    { timeout: 20_000 },
    async () => {
      const { user } = renderConMock(<App intervalloPollingMs={POLLING_TEST_MS} />, {
        datiIniziali: 'errori',
      });
      const chiudi = async (nomeFile: string) => {
        const dialogo = await screen.findByRole('dialog', { name: `Inserisci a mano · ${nomeFile}` });
        await user.click(within(dialogo).getByRole('button', { name: 'Chiudi la finestra' }));
        await waitFor(() => expect(dialogo).not.toBeInTheDocument());
      };

      const area = await areaInErrore();
      await user.click(
        within(rigaInErrore(area, 'busta-scansione.pdf')).getByRole('button', { name: 'Inserisci a mano' }),
      );
      const dialogo = await screen.findByRole('dialog', { name: 'Inserisci a mano · busta-scansione.pdf' });
      expect(within(dialogo).getByText('Scansione senza testo')).toBeInTheDocument();
      await chiudi('busta-scansione.pdf');

      const mensilita = await mensilitaDiAlfa(user);
      const agosto = await within(mensilita).findByRole('listitem', {
        name: 'Agosto 2025: Caricato con errore',
      });
      await user.click(within(agosto).getByRole('button', { name: 'Inserisci a mano' }));
      await chiudi('ristampa.pdf');

      const modale = await caricaFile(user, 'scansione-giugno.pdf');
      await user.click(within(modale).getByRole('button', { name: 'Inserisci a mano' }));
      expect(
        await screen.findByRole('dialog', { name: 'Inserisci a mano · scansione-giugno.pdf' }),
      ).toBeVisible();
      expect(screen.queryByRole('dialog', { name: 'Carica cedolini' })).not.toBeInTheDocument();
    },
  );
});

describe('associazione di un file in errore a una mensilità', () => {
  it('la mensilità associata è «Caricato con errore» e non più mancante', async () => {
    const { user } = renderConMock(<App />, { datiIniziali: 'errori' });
    const mensilita = await mensilitaDiAlfa(user);

    const agosto = await within(mensilita).findByRole('listitem', {
      name: 'Agosto 2025: Caricato con errore',
    });
    expect(within(agosto).getByText('Caricato con errore · Layout non riconosciuto')).toBeInTheDocument();
    expect(
      within(mensilita).queryByRole('listitem', { name: 'Agosto 2025: Mensilità mancante' }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('article', { name: 'Cedolino caricato con errore: Agosto 2025' }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('article', { name: 'Manca il cedolino di Agosto 2025' }),
    ).not.toBeInTheDocument();
  });

  it('associa un file dalla mensilità mancante', async () => {
    const { user } = renderConMock(<App />, { datiIniziali: 'errori' });
    const mensilita = await mensilitaDiAlfa(user);

    // la ristampa viene staccata: Agosto 2025 torna mancante e si può associare un altro file
    const inErrore = await within(mensilita).findByRole('listitem', {
      name: 'Agosto 2025: Caricato con errore',
    });
    await azioneDalMenu(user, inErrore, 'Agosto 2025', 'Rimuovi associazione');
    expect(await screen.findByText('Associazione rimossa')).toBeInTheDocument();
    const mancante = await within(mensilita).findByRole('listitem', {
      name: 'Agosto 2025: Mensilità mancante',
    });
    expect(within(mancante).getByText('Nessun cedolino acquisito')).toBeInTheDocument();
    expect(
      await screen.findByRole('article', { name: 'Manca il cedolino di Agosto 2025' }),
    ).toBeInTheDocument();

    await user.click(within(mancante).getByRole('button', { name: 'Associa un file in errore' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Associa un file in errore ad Agosto 2025' });
    const salva = within(dialogo).getByRole('button', { name: 'Salva' });
    expect(salva).toBeDisabled();
    await user.click(within(dialogo).getByRole('combobox', { name: 'File in errore' }));
    await user.click(await within(dialogo).findByRole('option', { name: /^busta-scansione\.pdf/ }));
    await user.click(salva);

    expect(await screen.findByText('File associato ad Agosto 2025')).toBeInTheDocument();
    const associata = await within(mensilita).findByRole('listitem', {
      name: 'Agosto 2025: Caricato con errore',
    });
    expect(within(associata).getByText('Caricato con errore · Scansione senza testo')).toBeInTheDocument();
    expect(
      within(rigaInErrore(await areaInErrore(), 'busta-scansione.pdf')).getByText(
        'Agosto 2025 · Alfa Software S.r.l.',
      ),
    ).toBeInTheDocument();
  });

  it('associa un file dall’elenco scegliendo persona, azienda e mese', async () => {
    const { user, backend } = renderConMock(<App />, { datiIniziali: 'errori' });
    const area = await areaInErrore();
    await azioneDalMenu(user, area, 'busta-scansione.pdf', 'Associa a un mese');

    const dialogo = await screen.findByRole('dialog', { name: 'Associa a un mese – busta-scansione.pdf' });
    await user.click(within(dialogo).getByRole('combobox', { name: 'Persona' }));
    await user.click(await within(dialogo).findByRole('option', { name: 'Mario Rossi' }));
    await user.click(await within(dialogo).findByRole('combobox', { name: 'Azienda' }));
    await user.click(
      await within(dialogo).findByRole('option', { name: 'Alfa Software S.r.l. (da Marzo 2022)' }),
    );

    // Agosto 2025 è già associato alla ristampa: non si può scegliere
    expect(await within(dialogo).findByRole('radio', { name: 'Agosto 2025' })).toBeDisabled();
    await user.click(within(dialogo).getByRole('button', { name: 'Altro mese…' }));
    await user.click(within(dialogo).getByRole('combobox', { name: 'Mese' }));
    await user.click(await within(dialogo).findByRole('option', { name: 'Settembre' }));
    await user.type(within(dialogo).getByRole('textbox', { name: 'Anno' }), '2026');
    expect(within(dialogo).getByText('Cedolino già presente per Settembre 2026')).toBeInTheDocument();
    const salva = within(dialogo).getByRole('button', { name: 'Salva' });
    expect(salva).toBeDisabled();

    await user.click(within(dialogo).getByRole('radio', { name: 'Tredicesima' }));
    expect(salva).toBeEnabled();
    await user.click(salva);

    expect(await screen.findByText('File associato a Tredicesima 2026')).toBeInTheDocument();
    expect(
      await within(rigaInErrore(area, 'busta-scansione.pdf')).findByText(
        'Tredicesima 2026 · Alfa Software S.r.l.',
      ),
    ).toBeInTheDocument();
    expect([...backend.db.associazioni.values()]).toContainEqual({ rapportoId: 1, anno: 2026, mese: 13 });

    const mensilita = await mensilitaDiAlfa(user);
    expect(
      await within(mensilita).findByRole('listitem', { name: 'Tredicesima 2026: Caricato con errore' }),
    ).toBeInTheDocument();
  });

  it('mostra nella finestra il rifiuto del backend', async () => {
    const { user } = renderConMock(<App />, {
      datiIniziali: 'errori',
      avvolgiFetch: (fetchApi) => (request) =>
        request.method === 'PUT' && new URL(request.url).pathname.endsWith('/attribuzione')
          ? Promise.resolve(
              new Response(
                JSON.stringify({
                  title: 'Operazione non consentita',
                  status: 409,
                  detail: 'Il file è in elaborazione: attendi la fine',
                }),
                { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
              ),
            )
          : fetchApi(request),
    });
    const area = await areaInErrore();
    await azioneDalMenu(user, area, 'ristampa.pdf', 'Cambia mese');

    // il file è già associato: persona, azienda e mese attuali sono già scelti
    const dialogo = await screen.findByRole('dialog', { name: 'Associa a un mese – ristampa.pdf' });
    expect(await within(dialogo).findByRole('radio', { name: 'Agosto 2025' })).toBeChecked();
    await user.click(within(dialogo).getByRole('button', { name: 'Salva' }));

    const errore = await within(dialogo).findByRole('alert');
    expect(errore).toHaveTextContent('Impossibile associare il file');
    expect(errore).toHaveTextContent('Il file è in elaborazione: attendi la fine');
    expect(dialogo).toBeInTheDocument();
  });
});

describe('dettaglio di un cedolino inserito a mano', () => {
  it('mostra «Inserito a mano» al posto dell’affidabilità della lettura', async () => {
    const { user, backend } = renderConMock(<App />);
    const cedolino = [...backend.db.cedolini.values()].find(
      (c) => c.rapportoId === 1 && c.titolo === 'Settembre 2026',
    );
    if (!cedolino) {
      throw new Error('cedolino simulato mancante');
    }
    cedolino.inseritoAMano = true;
    cedolino.confidenza = null;
    await selezionaPersona(user, 'Mario Rossi');
    await user.click(await screen.findByRole('button', { name: /^Mostra il dettaglio di Settembre 2026/ }));

    expect(await screen.findByText('Inserito a mano')).toBeInTheDocument();
    expect(screen.queryByText(/Affidabilità della lettura/)).not.toBeInTheDocument();
  });
});
