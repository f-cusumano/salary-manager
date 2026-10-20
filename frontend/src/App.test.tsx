import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { App } from './App';
import { formatImporto } from './format/format';
import { renderConMock } from './test/render';

const POLLING_TEST_MS = 20;

async function selezionaPersona(user: ReturnType<typeof renderConMock>['user'], nome: string) {
  await user.click(await screen.findByRole('combobox', { name: 'Persona' }));
  await user.click(await screen.findByRole('option', { name: nome }));
}

function inputFile(modale: HTMLElement): HTMLInputElement {
  const input = modale.querySelector<HTMLInputElement>('input[type="file"]');
  if (!input) {
    throw new Error('input file della dropzone non trovato');
  }
  return input;
}

function pdf(nome: string): File {
  return new File(['%PDF-1.4 finto'], nome, { type: 'application/pdf' });
}

function escapeRegExp(testo: string): string {
  return testo.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/** Nome accessibile della barra di una mensilità: azione e titolo, seguiti da indicatori, ratei e netto. */
function barra(titolo: string, azione: 'Mostra' | 'Nascondi' = 'Mostra'): RegExp {
  return new RegExp(`^${azione} il dettaglio di ${escapeRegExp(titolo)}(?![\\d])`);
}

/** Valore di una riga etichetta/valore (lista di descrizioni) dentro un contenitore. */
function valoreDi(contenitore: HTMLElement, etichetta: string): string | null {
  return within(contenitore).getByText(etichetta, { selector: 'dt' }).nextElementSibling?.textContent ?? null;
}

function voceMensilita(titolo: string, azione: 'Mostra' | 'Nascondi' = 'Mostra'): HTMLElement {
  const voce = screen.getByRole('button', { name: barra(titolo, azione) }).closest('li');
  if (!voce) {
    throw new Error(`mensilità ${titolo} non trovata`);
  }
  return voce;
}

async function eliminaMensilita(user: ReturnType<typeof renderConMock>['user'], voce: HTMLElement) {
  await user.click(await within(voce).findByRole('button', { name: 'Elimina' }));
  const conferma = await screen.findByRole('dialog', { name: 'Eliminare il cedolino?' });
  await user.click(within(conferma).getByRole('button', { name: 'Elimina cedolino' }));
}

describe('barra superiore e selezione persona', () => {
  it('mostra logo, nome e tendina vuota con placeholder prima dei caricamenti', async () => {
    const { user } = renderConMock(<App />, { datiIniziali: 'vuoto' });
    expect(screen.getByRole('heading', { level: 1, name: 'Salary Manager' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Logo di Salary Manager' })).toHaveAttribute('src', '/logo.png');
    const tendina = screen.getByRole('combobox', { name: 'Persona' });
    expect(tendina).toHaveAttribute('placeholder', 'Seleziona persona');
    await user.click(tendina);
    expect(await screen.findByText('Nessuna persona: carica dei cedolini')).toBeInTheDocument();
    // area principale vuota senza persona selezionata
    expect(screen.queryByRole('region', { name: 'Aziende' })).not.toBeInTheDocument();
  });

  it('elenca le aziende in ordine cronologico inverso con il periodo e apre quella attuale', async () => {
    const { user } = renderConMock(<App />);
    await selezionaPersona(user, 'Mario Rossi');

    const aziende = await screen.findByRole('region', { name: 'Aziende' });
    const righe = within(aziende).getAllByRole('button', { name: /^(Apri|Chiudi) il dettaglio di/ });
    expect(righe[0]).toHaveTextContent('Alfa Software S.r.l. (da Marzo 2022)');
    expect(righe[1]).toHaveTextContent('Beta Consulting S.p.A. (da Settembre 2018 a Febbraio 2022)');

    // di default è aperta l'azienda attuale, con il triangolo delle segnalazioni
    expect(righe[0]).toHaveAttribute('aria-expanded', 'true');
    expect(righe[0]).toHaveAccessibleName(/Ci sono segnalazioni/);
    expect(righe[1]).toHaveAttribute('aria-expanded', 'false');
    expect(righe[1]).not.toHaveAccessibleName(/segnalazioni/);
    expect(
      await screen.findByRole('article', { name: 'Manca il cedolino di Agosto 2025' }),
    ).toBeInTheDocument();
    expect(screen.getByText('4 anni e 7 mesi')).toBeInTheDocument();
    expect(screen.getByText('Ferie e permessi residui')).toBeInTheDocument();
    expect(screen.getByText('Quota in azienda')).toBeInTheDocument();
  });

  it('tiene aperto un solo dettaglio alla volta e nasconde i dati solo attuali per le aziende passate', async () => {
    const { user } = renderConMock(<App />);
    await selezionaPersona(user, 'Mario Rossi');
    const beta = await screen.findByRole('button', { name: /Beta Consulting/ });
    await user.click(beta);

    expect(beta).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('button', { name: /Alfa Software/ })).toHaveAttribute('aria-expanded', 'false');
    expect(await screen.findByText('3 anni e 6 mesi')).toBeInTheDocument();
    // azienda passata: niente ferie residue né quota TFR in azienda, nessuna area segnalazioni
    expect(screen.queryByText('Ferie e permessi residui')).not.toBeInTheDocument();
    expect(screen.queryByText('Quota in azienda')).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Segnalazioni' })).not.toBeInTheDocument();

    // un secondo clic chiude il dettaglio
    await user.click(beta);
    expect(beta).toHaveAttribute('aria-expanded', 'false');
  });

  it('mostra il TFR liquidato e il netto liquidato di un rapporto concluso', async () => {
    const { user } = renderConMock(<App />);
    await selezionaPersona(user, 'Mario Rossi');
    await user.click(await screen.findByRole('button', { name: /Beta Consulting/ }));

    expect(await screen.findByText('TFR liquidato (lordo)')).toBeInTheDocument();
    expect(screen.getByText('9.870,00 €')).toBeInTheDocument();
    expect(screen.getByText('Netto liquidato')).toBeInTheDocument();
    expect(screen.getByText('7.699,60 €')).toBeInTheDocument();
    expect(screen.queryByText('Totale accumulato')).not.toBeInTheDocument();
    expect(screen.queryByText('Stima netto')).not.toBeInTheDocument();
  });

  it('stima la liquidazione dell’azienda attuale con voci, trattenute e totale netto', async () => {
    const { user } = renderConMock(<App />);
    await selezionaPersona(user, 'Mario Rossi');

    const sezione = await screen.findByRole('region', { name: 'Se andassi via oggi' });
    expect(within(sezione).getByText('Stima al 30 Settembre 2026')).toBeInTheDocument();
    expect(within(sezione).getByText('9.329,76 €')).toBeInTheDocument();
    expect(
      within(sezione).getByText('lordo 13.177,79 € · contributi 464,78 € · imposte 3.383,25 €'),
    ).toBeInTheDocument();
    const voci = within(sezione).getAllByRole('listitem');
    expect(voci.map((v) => v.firstElementChild?.firstElementChild?.textContent)).toEqual([
      'TFR in azienda',
      'Ferie · 12,5 giorni',
      'Permessi · 32 ore',
      'Ex festività · 16 ore',
      'Ratei di tredicesima · 9 mesi',
      'Ratei di quattordicesima · 3 mesi',
    ]);
    // il TFR non ha contributi: solo lordo e imposte; la base del calcolo è nel tooltip
    const tfr = voci[0];
    if (!tfr) {
      throw new Error('voce TFR non trovata');
    }
    expect(within(tfr).getByText('lordo 8.120,40 € · imposte 1.867,69 €')).toBeInTheDocument();
    expect(within(tfr).getByText('6.252,71 €')).toBeInTheDocument();
    expect(
      within(tfr).getByLabelText(/^TFR in azienda: Tassazione separata: aliquota media del 23%/),
    ).toBeInTheDocument();
    expect(within(sezione).getByText(/preavviso considerato lavorato/)).toBeInTheDocument();
    expect(
      within(sezione).queryByText('Totale parziale: alcune voci non sono stimabili.'),
    ).not.toBeInTheDocument();
  });

  it('segnala le voci non stimabili e il totale parziale; nessuna stima se il TFR è già liquidato', async () => {
    const { user } = renderConMock(<App />);
    await selezionaPersona(user, 'Laura Bianchi');

    const sezione = await screen.findByRole('region', { name: 'Se andassi via oggi' });
    expect(within(sezione).getByText('Totale parziale: alcune voci non sono stimabili.')).toBeInTheDocument();
    const rol = within(sezione).getByText('ROL · 22 ore').closest('li');
    if (!rol) {
      throw new Error('voce ROL non trovata');
    }
    expect(within(rol).getByText('non stimabile')).toBeInTheDocument();
    expect(within(rol).getByText(/^Valore non stimabile/)).toBeInTheDocument();

    await selezionaPersona(user, 'Mario Rossi');
    await user.click(await screen.findByRole('button', { name: /Beta Consulting/ }));
    expect(await screen.findByText('3 anni e 6 mesi')).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Se andassi via oggi' })).not.toBeInTheDocument();
  });

  it('marca come stime scatti e quota TFR non stampati, con la spiegazione nel tooltip', async () => {
    const { user } = renderConMock(<App />);
    await selezionaPersona(user, 'Laura Bianchi');

    expect(
      await screen.findByLabelText(/^stima: Numero non stampato sui cedolini: stimato dalle regole del CCNL/),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/^stima: Quota non stampata sul cedolino/)).toBeInTheDocument();
    // la base della stima del valore delle ferie arriva dal backend
    expect(
      screen.getByLabelText(
        'stima: Stima del valore se liquidato oggi. Ore x lordo mensile / divisore orario 173 (da configurazione).',
      ),
    ).toBeInTheDocument();
    expect(
      within(screen.getByRole('region', { name: 'Retribuzione' })).getByText(
        'Dal cedolino di Settembre 2026',
      ),
    ).toBeInTheDocument();
    // CCNL non indicato: RAL stimata sulle 13 mensilità di default
    expect(screen.getByText('1.950,00 € × 13 mensilità')).toBeInTheDocument();
    expect(
      screen.getByLabelText('stima: CCNL non indicato: RAL calcolata su 13 mensilità.'),
    ).toBeInTheDocument();
  });

  it("riferisce l'anzianità all'ultimo cedolino e non promette scatti quando non sono determinabili", async () => {
    const { user } = renderConMock(<App />);
    await selezionaPersona(user, 'Laura Bianchi');

    const anzianita = await screen.findByRole('region', { name: 'Anzianità in azienda' });
    expect(
      within(anzianita).getByText(/^dal Novembre 2025 al Settembre 2026 \(ultimo cedolino\)/),
    ).toBeInTheDocument();
    expect(within(anzianita).queryByText(/a oggi/)).not.toBeInTheDocument();
    // CCNL da indicare: il prossimo scatto non è noto, non "nessuno"
    expect(valoreDi(anzianita, 'Prossimo scatto')).toBe('non determinabile (CCNL da indicare)');
  });

  it('dice "nessuno previsto" solo a scatti esauriti o a rapporto concluso', async () => {
    const { user, backend } = renderConMock(<App />);
    const scatti = backend.db.rapporti.get(1)?.riepilogo?.scatti;
    if (!scatti) {
      throw new Error('riepilogo simulato mancante');
    }
    Object.assign(scatti, {
      attuali: 10,
      massimo: 10,
      prossimoScatto: null,
      fonteProssimo: 'NON_DISPONIBILE',
    });
    await selezionaPersona(user, 'Mario Rossi');
    const attuale = await screen.findByRole('region', { name: 'Anzianità in azienda' });
    expect(valoreDi(attuale, 'Prossimo scatto')).toBe('nessuno previsto');
    expect(
      within(attuale).getByText(/^dal Marzo 2022 al Settembre 2026 \(ultimo cedolino\)/),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Beta Consulting/ }));
    const passata = await screen.findByRole('region', { name: 'Anzianità in azienda' });
    expect(within(passata).getByText(/^dal Settembre 2018 al Febbraio 2022 ·/)).toBeInTheDocument();
    expect(valoreDi(passata, 'Prossimo scatto')).toBe('nessuno previsto');
  });

  it('mostra "non disponibile" se le regole non danno la data del prossimo scatto', async () => {
    const { user, backend } = renderConMock(<App />);
    const scatti = backend.db.rapporti.get(1)?.riepilogo?.scatti;
    if (!scatti) {
      throw new Error('riepilogo simulato mancante');
    }
    Object.assign(scatti, {
      attuali: 1,
      massimo: null,
      prossimoScatto: null,
      fonteProssimo: 'NON_DISPONIBILE',
    });
    await selezionaPersona(user, 'Mario Rossi');
    const anzianita = await screen.findByRole('region', { name: 'Anzianità in azienda' });
    expect(valoreDi(anzianita, 'Prossimo scatto')).toBe('—');
  });

  it("se l'elenco delle persone non si carica mostra il motivo e permette di riprovare", async () => {
    let primaChiamata = true;
    const { user } = renderConMock(<App />, {
      avvolgiFetch: (fetchApi) => (request) => {
        if (primaChiamata && new URL(request.url).pathname === '/api/v1/persone') {
          primaChiamata = false;
          return Promise.resolve(
            new Response(
              JSON.stringify({
                title: 'Servizio non disponibile',
                status: 503,
                detail: 'Database in manutenzione.',
              }),
              { status: 503, headers: { 'Content-Type': 'application/problem+json' } },
            ),
          );
        }
        return fetchApi(request);
      },
    });

    const errore = await screen.findByRole('alert');
    expect(errore).toHaveTextContent('Impossibile caricare l’elenco delle persone');
    expect(errore).toHaveTextContent('Database in manutenzione.');
    expect(screen.getByRole('combobox', { name: 'Persona' })).toHaveAttribute('aria-invalid', 'true');

    await user.click(within(errore).getByRole('button', { name: 'Riprova' }));
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
    await selezionaPersona(user, 'Mario Rossi');
    expect(await screen.findByRole('region', { name: 'Aziende' })).toBeInTheDocument();
  });
});

describe('mensilità', () => {
  it('espande più barre insieme caricando il dettaglio al bisogno; la mancante non è espandibile', async () => {
    const { user } = renderConMock(<App />);
    await selezionaPersona(user, 'Mario Rossi');

    const settembre = await screen.findByRole('button', { name: barra('Settembre 2026') });
    expect(settembre).toHaveAttribute('aria-expanded', 'false');
    await user.click(settembre);
    expect(settembre).toHaveAttribute('aria-expanded', 'true');
    expect(settembre).toHaveAccessibleName(barra('Settembre 2026', 'Nascondi'));
    expect(await screen.findByRole('heading', { name: 'Competenze' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: barra('Tredicesima 2025') }));
    await waitFor(() => expect(screen.getAllByRole('heading', { name: 'Competenze' })).toHaveLength(2));
    expect(screen.getByText('13ma Mensilità')).toBeInTheDocument();
    // dati letti senza avvisi: nessuna sezione dedicata
    expect(screen.queryByRole('heading', { name: 'Avvisi dell’estrazione' })).not.toBeInTheDocument();

    const mancante = screen.getByRole('listitem', { name: 'Agosto 2025: Mensilità mancante' });
    expect(within(mancante).queryByRole('button')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /dettaglio di Marzo 2025/ })).toHaveTextContent('Scatto');
    expect(screen.getByRole('button', { name: /dettaglio di Aprile 2024/ })).toHaveTextContent('Aumento');
  });

  it('annuncia nel nome della barra indicatori, ratei e netto della mensilità', async () => {
    const { user, backend } = renderConMock(<App />);
    await selezionaPersona(user, 'Mario Rossi');
    const netto = backend.db.rapporti.get(1)?.mensilita.find((m) => m.chiave === '2025.03')?.netto;
    if (netto == null) {
      throw new Error('mensilità simulata mancante');
    }

    const marzo = await screen.findByRole('button', { name: barra('Marzo 2025') });
    const nome = new RegExp(
      `Scatto.*Ferie.*Netto\\s*${escapeRegExp(formatImporto(netto))}$`.replace(/\u00a0/g, '\\s'),
    );
    expect(marzo).toHaveAccessibleName(nome);
    expect(screen.getByRole('button', { name: barra('Aprile 2024') })).toHaveAccessibleName(/Aumento/);
  });

  it("mostra gli avvisi dell'estrazione accanto ai dati non trovati", async () => {
    const { user } = renderConMock(<App />);
    await selezionaPersona(user, 'Laura Bianchi');
    await user.click(await screen.findByRole('button', { name: barra('Settembre 2026') }));

    const avvisi = (await screen.findByRole('heading', { name: 'Avvisi dell’estrazione' })).closest(
      'section',
    );
    if (!avvisi) {
      throw new Error('sezione degli avvisi non trovata');
    }
    expect(
      within(avvisi)
        .getAllByRole('listitem')
        .map((v) => v.textContent),
    ).toEqual(["Rimossi dati bancari presenti nell'estrazione: non vengono salvati"]);
  });

  it('mostra solo le colonne con valori e i dati non trovati con nomi leggibili', async () => {
    const { user } = renderConMock(<App />);
    await selezionaPersona(user, 'Laura Bianchi');
    await user.click(await screen.findByRole('button', { name: barra('Settembre 2026') }));

    const competenze = (await screen.findByRole('heading', { name: 'Competenze' })).closest('section');
    if (!competenze) {
      throw new Error('sezione Competenze non trovata');
    }
    expect(within(competenze).getByRole('columnheader', { name: 'Competenza' })).toBeInTheDocument();
    expect(within(competenze).queryByRole('columnheader', { name: 'Trattenuta' })).not.toBeInTheDocument();
    const previdenziali = screen
      .getByRole('heading', { name: 'Trattenute previdenziali' })
      .closest('section');
    expect(
      previdenziali && within(previdenziali).getByRole('columnheader', { name: 'Base' }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Dati non trovati sul cedolino: CCNL, codice CNEL del contratto$/),
    ).toBeInTheDocument();
  });

  it('sugli schermi stretti omette la colonna della base per lasciare visibili gli importi', async () => {
    vi.spyOn(window, 'matchMedia').mockImplementation((query: string) => ({
      matches: query.includes('max-width'),
      media: query,
      onchange: null,
      addListener: () => undefined,
      removeListener: () => undefined,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      dispatchEvent: () => false,
    }));
    const { user } = renderConMock(<App />);
    await selezionaPersona(user, 'Laura Bianchi');
    await user.click(await screen.findByRole('button', { name: barra('Settembre 2026') }));

    const previdenziali = (await screen.findByRole('heading', { name: 'Trattenute previdenziali' })).closest(
      'section',
    );
    if (!previdenziali) {
      throw new Error('sezione Trattenute previdenziali non trovata');
    }
    expect(within(previdenziali).getByRole('columnheader', { name: 'Trattenuta' })).toBeInTheDocument();
    expect(within(previdenziali).queryByRole('columnheader', { name: 'Base' })).not.toBeInTheDocument();
    const ratei = screen.getByRole('heading', { name: 'Ratei' }).closest('section');
    expect(ratei && within(ratei).getByRole('columnheader', { name: 'Saldo' })).toBeInTheDocument();
    expect(
      ratei && within(ratei).queryByRole('columnheader', { name: 'Residuo AP' }),
    ).not.toBeInTheDocument();
  });

  it('elimina un cedolino dopo la conferma e ricarica i dati', async () => {
    const { user, backend } = renderConMock(<App />);
    await selezionaPersona(user, 'Mario Rossi');
    await user.click(await screen.findByRole('button', { name: barra('Settembre 2026') }));
    await user.click(await screen.findByRole('button', { name: 'Elimina' }));

    const conferma = await screen.findByRole('dialog', { name: 'Eliminare il cedolino?' });
    await user.click(within(conferma).getByRole('button', { name: 'Elimina cedolino' }));

    await waitFor(() =>
      expect(screen.queryByRole('button', { name: /dettaglio di Settembre 2026/ })).not.toBeInTheDocument(),
    );
    expect(backend.db.rapporti.get(1)?.mensilita.some((m) => m.chiave === '2026.09')).toBe(false);
  });

  it("non elimina se l'utente annulla", async () => {
    const { user, backend } = renderConMock(<App />);
    await selezionaPersona(user, 'Mario Rossi');
    await user.click(await screen.findByRole('button', { name: barra('Settembre 2026') }));
    await user.click(await screen.findByRole('button', { name: 'Elimina' }));
    const conferma = await screen.findByRole('dialog', { name: 'Eliminare il cedolino?' });
    await user.click(within(conferma).getByRole('button', { name: 'Annulla' }));
    expect(backend.db.rapporti.get(1)?.mensilita.some((m) => m.chiave === '2026.09')).toBe(true);
  });

  it('dopo un’eliminazione aggiorna il nome del file dei cedolini di fine rapporto rinumerati', async () => {
    const { user } = renderConMock(<App />);
    await selezionaPersona(user, 'Mario Rossi');
    await user.click(await screen.findByRole('button', { name: /Beta Consulting/ }));
    await user.click(await screen.findByRole('button', { name: barra('Fine rapporto 2 – Maggio 2022') }));
    const conguaglio = voceMensilita('Fine rapporto 2 – Maggio 2022', 'Nascondi');
    await waitFor(() => expect(valoreDi(conguaglio, 'File')).toBe('2022.TFR02_2_1.pdf'));

    await user.click(screen.getByRole('button', { name: barra('Fine rapporto 1 – Marzo 2022') }));
    await eliminaMensilita(user, voceMensilita('Fine rapporto 1 – Marzo 2022', 'Nascondi'));

    // il conguaglio diventa TFR01: titolo e nome del file arrivano riletti dal backend
    const rinumerato = await screen.findByRole('button', {
      name: barra('Fine rapporto 1 – Maggio 2022', 'Nascondi'),
    });
    const voce = rinumerato.closest('li');
    if (!voce) {
      throw new Error('mensilità rinumerata non trovata');
    }
    await waitFor(() => expect(valoreDi(voce, 'File')).toBe('2022.TFR01_2_1.pdf'));
  });

  it('una barra aperta non passa a un altro cedolino che ne eredita la chiave', async () => {
    const { user } = renderConMock(<App />);
    await selezionaPersona(user, 'Mario Rossi');
    await user.click(await screen.findByRole('button', { name: /Beta Consulting/ }));
    await user.click(await screen.findByRole('button', { name: barra('Fine rapporto 1 – Marzo 2022') }));
    await eliminaMensilita(user, voceMensilita('Fine rapporto 1 – Marzo 2022', 'Nascondi'));

    const ereditato = await screen.findByRole('button', { name: barra('Fine rapporto 1 – Maggio 2022') });
    expect(ereditato).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('heading', { name: 'Competenze' })).not.toBeInTheDocument();
  });
});

describe('riepilogo modificabile', () => {
  it('salva le note', async () => {
    const { user, backend } = renderConMock(<App />);
    await selezionaPersona(user, 'Mario Rossi');
    const note = await screen.findByRole('textbox', { name: 'Note' });
    expect(screen.getByRole('button', { name: 'Salva note' })).toBeDisabled();
    await user.clear(note);
    await user.type(note, 'Promozione prevista a Marzo');
    await user.click(screen.getByRole('button', { name: 'Salva note' }));
    expect(await screen.findByText('Note salvate')).toBeInTheDocument();
    expect(backend.db.rapporti.get(1)?.note).toBe('Promozione prevista a Marzo');
  });

  it('chiede il CCNL non riconosciuto e ricalcola alla scelta', async () => {
    const { user, backend } = renderConMock(<App />);
    await selezionaPersona(user, 'Laura Bianchi');
    expect(await screen.findByText(/Il contratto non è stampato sui cedolini/)).toBeInTheDocument();

    await user.click(screen.getByRole('combobox', { name: 'Seleziona il CCNL' }));
    await user.click(await screen.findByRole('option', { name: /Studi professionali/ }));

    expect(
      await screen.findByText('CCNL aggiornato: riepilogo e segnalazioni ricalcolati.'),
    ).toBeInTheDocument();
    expect(await screen.findByText('scelto da te')).toBeInTheDocument();
    expect(backend.db.rapporti.get(3)?.ccnl?.codice).toBe('STUDI_PROFESSIONALI');
    // la segnalazione "CCNL da indicare" sparisce dopo il ricalcolo
    await waitFor(() =>
      expect(screen.queryByRole('heading', { name: 'Segnalazioni' })).not.toBeInTheDocument(),
    );
  });

  it('chiede conferma prima di cambiare un CCNL già impostato', async () => {
    const { user, backend } = renderConMock(<App />);
    await selezionaPersona(user, 'Mario Rossi');
    await user.click(await screen.findByRole('button', { name: 'Modifica il CCNL' }));
    await user.click(screen.getByRole('combobox', { name: 'Seleziona il CCNL' }));
    await user.click(await screen.findByRole('option', { name: /Telecomunicazioni/ }));
    await user.click(screen.getByRole('button', { name: 'Conferma' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Cambiare il CCNL?' });
    await user.click(within(dialogo).getByRole('button', { name: 'Conferma' }));
    await waitFor(() => expect(backend.db.rapporti.get(1)?.ccnl?.codice).toBe('TELECOMUNICAZIONI'));
  });
});

describe('caricamento cedolini', () => {
  it('mostra lo stato di ogni file e alla fine seleziona la persona appena caricata', async () => {
    const { user } = renderConMock(<App intervalloPollingMs={POLLING_TEST_MS} />, { datiIniziali: 'vuoto' });
    await user.click(screen.getByRole('button', { name: 'Carica cedolini' }));
    const modale = await screen.findByRole('dialog', { name: 'Carica cedolini' });

    await user.upload(inputFile(modale), [
      pdf('gennaio.pdf'),
      pdf('gennaio-duplicato.pdf'),
      pdf('marzo-aggiuntivo.pdf'),
      pdf('protetto.pdf'),
    ]);
    expect(within(modale).getByText('File selezionati (4)')).toBeInTheDocument();
    await user.click(within(modale).getByRole('button', { name: 'Avvia caricamento (4)' }));

    expect(
      await within(modale).findByText('Caricamento completato', {}, { timeout: 3000 }),
    ).toBeInTheDocument();
    // a lotto concluso i file da controllare vengono per primi
    const righe = within(modale).getAllByRole('listitem');
    expect(righe[0]).toHaveTextContent('protetto.pdfErrore');
    expect(righe[0]).toHaveTextContent('PDF protetto da password: riprova indicando la password');
    expect(righe[1]).toHaveTextContent('Aggiuntivo non gestito');
    expect(righe[2]).toHaveTextContent('Duplicato');
    expect(righe[3]).toHaveTextContent('gennaio.pdfAcquisito');

    // nessuna persona era selezionata: viene scelta quella del cedolino acquisito
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Persona' })).toHaveValue('Mario Rossi'));
    await user.click(within(modale).getByRole('button', { name: 'Chiudi' }));
    expect(await screen.findByRole('button', { name: /Alfa Software/ })).toBeInTheDocument();
  });

  it('invia la password indicata per i PDF protetti', async () => {
    const { user } = renderConMock(<App intervalloPollingMs={POLLING_TEST_MS} />);
    await user.click(screen.getByRole('button', { name: 'Carica cedolini' }));
    const modale = await screen.findByRole('dialog', { name: 'Carica cedolini' });
    await user.upload(inputFile(modale), [pdf('protetto.pdf')]);
    await user.type(within(modale).getByLabelText('Password dei PDF (facoltativa)'), 'segreta');
    await user.click(within(modale).getByRole('button', { name: 'Avvia caricamento (1)' }));
    expect(
      await within(modale).findByText('Caricamento completato', {}, { timeout: 3000 }),
    ).toBeInTheDocument();
    expect(within(modale).getByRole('listitem')).toHaveTextContent('Acquisito');
  });

  it('descrive in italiano il pulsante che mostra la password', async () => {
    const { user } = renderConMock(<App />);
    await user.click(screen.getByRole('button', { name: 'Carica cedolini' }));
    const modale = await screen.findByRole('dialog', { name: 'Carica cedolini' });
    const password = within(modale).getByLabelText('Password dei PDF (facoltativa)');
    expect(password).toHaveAttribute('type', 'password');

    await user.click(
      within(modale).getByRole('button', { name: 'Mostra o nascondi la password', hidden: true }),
    );
    expect(password).toHaveAttribute('type', 'text');
  });

  it("permette di togliere un file prima dell'invio", async () => {
    const { user } = renderConMock(<App />);
    await user.click(screen.getByRole('button', { name: 'Carica cedolini' }));
    const modale = await screen.findByRole('dialog', { name: 'Carica cedolini' });
    const avvia = within(modale).getByRole('button', { name: 'Avvia caricamento' });
    expect(avvia).toBeDisabled();
    await user.upload(inputFile(modale), [pdf('a.pdf'), pdf('b.pdf')]);
    await user.click(within(modale).getByRole('button', { name: 'Rimuovi a.pdf' }));
    expect(within(modale).getByText('File selezionati (1)')).toBeInTheDocument();
    expect(within(modale).queryByText('a.pdf')).not.toBeInTheDocument();
  });
});
