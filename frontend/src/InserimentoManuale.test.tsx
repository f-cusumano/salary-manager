import { act, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { App } from './App';
import { chiavi } from './api/chiavi';
import type { FetchApi } from './api/client';
import type { CedolinoManuale } from './api/types';
import { chiaveFile } from './mocks/dati';
import { areaInErrore, rigaInErrore, type Utente } from './test/app';
import { renderConMock } from './test/render';

// Dataset "errori" (dati INVENTATI): busta-scansione.pdf (scansione, non associata) e ristampa.pdf (associata ad
// Agosto 2025 di Mario Rossi presso Alfa Software). Il modulo e' grande: i campi si cercano per `data-path`, lo stesso
// attributo usato dal modulo per spostare il focus sugli errori.

/** Codice fiscale inventato ma formalmente valido (carattere di controllo compreso). */
const CF_NUOVO = 'VRDGPP90C15L219G';

async function apriInserimento(user: Utente, nomeFile: string): Promise<HTMLElement> {
  const area = await areaInErrore();
  await user.click(within(rigaInErrore(area, nomeFile)).getByRole('button', { name: 'Inserisci a mano' }));
  const dialogo = await screen.findByRole('dialog', { name: `Inserisci a mano · ${nomeFile}` });
  await within(dialogo).findByRole('form', { name: 'Dati del cedolino' });
  return dialogo;
}

function campo(dialogo: HTMLElement, percorso: string): HTMLInputElement {
  const elemento = dialogo.querySelector<HTMLInputElement>(`[data-path="${percorso}"]`);
  if (!elemento) {
    throw new Error(`campo ${percorso} non trovato`);
  }
  return elemento;
}

async function scegli(user: Utente, dialogo: HTMLElement, percorso: string, opzione: string): Promise<void> {
  await user.click(campo(dialogo, percorso));
  await user.click(await within(dialogo).findByRole('option', { name: opzione }));
}

async function scrivi(user: Utente, dialogo: HTMLElement, percorso: string, testo: string): Promise<void> {
  await user.type(campo(dialogo, percorso), testo);
}

/** Campi obbligatori di un cedolino di Maggio 2025 di una persona nuova (Giuseppe Verdi, Delta Servizi). */
async function compilaObbligatori(user: Utente, dialogo: HTMLElement): Promise<void> {
  await scegli(user, dialogo, 'periodo.tipo', 'Mensilità ordinaria');
  await scegli(user, dialogo, 'periodo.mese', 'Maggio');
  await scrivi(user, dialogo, 'periodo.anno', '2025');
  await scrivi(user, dialogo, 'dipendente.codiceFiscale', CF_NUOVO.toLowerCase());
  await scrivi(user, dialogo, 'dipendente.cognome', 'Verdi');
  await scrivi(user, dialogo, 'dipendente.nome', 'Giuseppe');
  await scrivi(user, dialogo, 'datoreLavoro.ragioneSociale', 'Delta Servizi S.r.l.');
  await compilaTotali(user, dialogo);
}

async function compilaTotali(user: Utente, dialogo: HTMLElement): Promise<void> {
  await scrivi(user, dialogo, 'totali.totaleCompetenze', '2000');
  await scrivi(user, dialogo, 'totali.totaleTrattenute', '500');
  await scrivi(user, dialogo, 'totali.netto', '1500');
}

function pulsante(dialogo: HTMLElement, nome: string | RegExp): HTMLElement {
  return within(dialogo).getByRole('button', { name: nome });
}

/** Risposta Problem Details del backend per una chiamata intercettata. */
function problema(status: number, corpo: Record<string, unknown>): Promise<Response> {
  return Promise.resolve(
    new Response(JSON.stringify({ status, ...corpo }), {
      status,
      headers: { 'Content-Type': 'application/problem+json' },
    }),
  );
}

/** Registra i corpi delle verifiche inviate al backend simulato, senza cambiarne le risposte. */
function registraVerifiche(richieste: CedolinoManuale[]) {
  return (fetchApi: FetchApi): FetchApi =>
    async (request) => {
      if (request.method === 'POST' && new URL(request.url).pathname.endsWith('/cedolino/verifica')) {
        richieste.push((await request.clone().json()) as CedolinoManuale);
      }
      return fetchApi(request);
    };
}

/** Annuncio dell'esito della verifica per i lettori di schermo. */
function annuncio(dialogo: HTMLElement): HTMLElement {
  const regione = dialogo.querySelector<HTMLElement>('[data-annuncio-verifica]');
  if (!regione) {
    throw new Error('regione dell’annuncio non trovata');
  }
  return regione;
}

function intercetta(percorso: RegExp, risposta: () => Promise<Response>) {
  return (fetchApi: FetchApi): FetchApi =>
    (request) =>
      request.method === 'POST' && percorso.test(new URL(request.url).pathname)
        ? risposta()
        : fetchApi(request);
}

describe('inserimento a mano', { timeout: 40_000 }, () => {
  it('precompila il modulo dal mese associato, dal rapporto e dal cedolino precedente', async () => {
    const { user } = renderConMock(<App />, { datiIniziali: 'errori' });
    const dialogo = await apriInserimento(user, 'ristampa.pdf');

    expect(within(dialogo).getByTitle('PDF del cedolino')).toBeInTheDocument();
    expect(within(dialogo).getByRole('link', { name: 'Apri in una nuova scheda' })).toHaveAttribute(
      'target',
      '_blank',
    );
    expect(campo(dialogo, 'periodo.tipo')).toHaveValue('Mensilità ordinaria');
    expect(campo(dialogo, 'periodo.mese')).toHaveValue('Agosto');
    expect(campo(dialogo, 'periodo.anno')).toHaveValue('2025');
    expect(within(dialogo).getByText('Sarà registrato come:').parentElement).toHaveTextContent('Agosto 2025');
    expect(campo(dialogo, 'dipendente.codiceFiscale')).toHaveValue('RSSMRA80A01H501U');
    expect(campo(dialogo, 'dipendente.cognome')).toHaveValue('Rossi');
    expect(campo(dialogo, 'datoreLavoro.ragioneSociale')).toHaveValue('Alfa Software S.r.l.');
    expect(campo(dialogo, 'datiContrattuali.dataAssunzione')).toHaveValue('2022-03-01');
    expect(campo(dialogo, 'datiContrattuali.dataCessazione')).toHaveValue('');
    // dal cedolino di Luglio 2025: le voci senza importi
    expect(campo(dialogo, 'voci.0.descrizione')).not.toHaveValue('');
    expect(campo(dialogo, 'voci.0.importoCompetenza')).toHaveValue('');
    expect(within(dialogo).getByText(/sono stati compilati in automatico/)).toBeInTheDocument();
    expect(within(dialogo).getByText('Copiato da Luglio 2025: verifica')).toBeInTheDocument();
    // i totali non si copiano mai
    expect(campo(dialogo, 'totali.netto')).toHaveValue('');
  });

  it('«Copia dati da…» compila solo i campi vuoti dal rapporto scelto e dal suo cedolino più vicino', async () => {
    const { user } = renderConMock(<App />, { datiIniziali: 'errori' });
    const dialogo = await apriInserimento(user, 'busta-scansione.pdf');
    await scegli(user, dialogo, 'periodo.mese', 'Aprile');
    await scrivi(user, dialogo, 'periodo.anno', '2026');
    await scrivi(user, dialogo, 'dipendente.nome', 'Mariano');

    await user.click(pulsante(dialogo, 'Copia dati da…'));
    await user.click(within(dialogo).getByRole('combobox', { name: 'Persona' }));
    await user.click(await within(dialogo).findByRole('option', { name: 'Mario Rossi' }));
    await user.click(await within(dialogo).findByRole('combobox', { name: 'Azienda' }));
    await user.click(await within(dialogo).findByRole('option', { name: /^Alfa Software S\.r\.l\./ }));
    await waitFor(() => expect(pulsante(dialogo, 'Copia i dati')).toBeEnabled());
    await user.click(pulsante(dialogo, 'Copia i dati'));

    expect(within(dialogo).getByRole('status')).toHaveTextContent(/^Campi compilati: \d+$/);
    expect(campo(dialogo, 'dipendente.codiceFiscale')).toHaveValue('RSSMRA80A01H501U');
    expect(campo(dialogo, 'dipendente.cognome')).toHaveValue('Rossi');
    expect(campo(dialogo, 'dipendente.nome')).toHaveValue('Mariano');
    expect(campo(dialogo, 'datoreLavoro.ragioneSociale')).toHaveValue('Alfa Software S.r.l.');
    expect(campo(dialogo, 'voci.0.descrizione')).not.toHaveValue('');
    expect(within(dialogo).getByText('Copiato da Aprile 2026: verifica')).toBeInTheDocument();
    expect(campo(dialogo, 'periodo.mese')).toHaveValue('Aprile');
  });

  it('segnala i campi obbligatori con il riepilogo e porta il focus sul primo errore', async () => {
    const { user } = renderConMock(<App />, { datiIniziali: 'errori' });
    const dialogo = await apriInserimento(user, 'busta-scansione.pdf');
    expect(pulsante(dialogo, 'Salva cedolino')).toBeDisabled();

    await user.click(pulsante(dialogo, 'Verifica'));

    const riepilogo = (await within(dialogo).findByText('10 campi da correggere')).closest<HTMLElement>(
      '[role="alert"]',
    );
    expect(riepilogo).not.toBeNull();
    await waitFor(() => expect(campo(dialogo, 'periodo.tipo')).toHaveFocus());
    expect(campo(dialogo, 'dipendente.codiceFiscale')).toHaveAttribute('aria-invalid', 'true');
    expect(within(dialogo).getAllByText('Indica il codice fiscale del dipendente')).toHaveLength(1);

    await user.click(
      within(riepilogo!).getByRole('button', { name: 'Ragione sociale: Indica la ragione sociale' }),
    );
    expect(campo(dialogo, 'datoreLavoro.ragioneSociale')).toHaveFocus();

    // correggendo un campo l'errore sparisce anche dal riepilogo
    await user.type(campo(dialogo, 'datoreLavoro.ragioneSociale'), 'Delta Servizi S.r.l.');
    expect(within(dialogo).getByText('9 campi da correggere')).toBeInTheDocument();
    expect(pulsante(dialogo, 'Salva cedolino')).toBeDisabled();
  });

  it('verifica e salva il cedolino di una persona nuova; salvare richiede una verifica dei dati attuali', async () => {
    const { user } = renderConMock(<App />, { datiIniziali: 'errori' });
    const dialogo = await apriInserimento(user, 'busta-scansione.pdf');
    await compilaObbligatori(user, dialogo);
    expect(campo(dialogo, 'dipendente.codiceFiscale')).toHaveValue(CF_NUOVO);

    await user.click(pulsante(dialogo, 'Verifica'));
    const esito = await within(dialogo).findByRole('region', { name: 'Esito della verifica' });
    expect(esito).toHaveTextContent('Sarà registrato come: Maggio 2025');
    expect(
      within(esito).getByText('Verrà creata una nuova persona: controlla il codice fiscale'),
    ).toBeVisible();
    expect(within(esito).getByText('Nuovo rapporto di lavoro')).toBeVisible();
    expect(pulsante(dialogo, 'Salva cedolino')).toBeEnabled();
    // l'esito compare in fondo al modulo: i lettori di schermo lo sentono annunciare
    expect(annuncio(dialogo)).toHaveAttribute('aria-live', 'polite');
    expect(annuncio(dialogo)).toHaveTextContent(
      /^Verifica completata: il cedolino si può salvare \(Maggio 2025\)\. Verrà creata una nuova persona/,
    );

    // dati cambiati dopo la verifica: niente salvataggio finché non si verifica di nuovo (anche con Ctrl+Invio)
    await scrivi(user, dialogo, 'dipendente.codiceDipendente', 'M-17');
    expect(pulsante(dialogo, 'Salva cedolino')).toBeDisabled();
    expect(
      within(dialogo).getAllByText(
        'I dati sono cambiati dopo la verifica: verifica di nuovo prima di salvare.',
      ).length,
    ).toBeGreaterThan(0);
    await user.keyboard('{Control>}{Enter}{/Control}');
    await waitFor(() => expect(pulsante(dialogo, 'Salva cedolino')).toBeEnabled());

    await user.click(pulsante(dialogo, 'Salva cedolino'));

    expect(await screen.findByText('Cedolino inserito: Maggio 2025')).toBeInTheDocument();
    await waitFor(() => expect(dialogo).not.toBeInTheDocument());
    const area = await areaInErrore();
    await waitFor(() =>
      expect(within(area).queryByRole('listitem', { name: /^busta-scansione\.pdf/ })).not.toBeInTheDocument(),
    );
    // senza una persona selezionata si mostra quella del cedolino inserito
    expect(within(screen.getByRole('banner')).getByRole('combobox', { name: 'Persona' })).toHaveValue(
      'Giuseppe Verdi',
    );
  });

  it('salvando il file associato la mensilità diventa presente; "Salva e apri il successivo" passa al prossimo file', async () => {
    const { user } = renderConMock(<App />, { datiIniziali: 'errori' });
    const dialogo = await apriInserimento(user, 'ristampa.pdf');
    await compilaTotali(user, dialogo);

    await user.click(pulsante(dialogo, 'Verifica'));
    const esito = await within(dialogo).findByRole('region', { name: 'Esito della verifica' });
    expect(esito).toHaveTextContent('Sarà registrato come: Agosto 2025');
    expect(within(esito).getByText('Dipendente: già presente')).toBeInTheDocument();
    expect(within(esito).getByText('Rapporto di lavoro: già presente')).toBeInTheDocument();

    // busta-scansione.pdf e cedolino-protetto.pdf si possono ancora completare a mano
    await user.click(pulsante(dialogo, 'Salva e apri il successivo (2)'));

    expect(await screen.findByText('Cedolino inserito: Agosto 2025')).toBeInTheDocument();
    expect(
      await screen.findByRole('dialog', { name: 'Inserisci a mano · busta-scansione.pdf' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('dialog', { name: 'Inserisci a mano · ristampa.pdf' })).not.toBeInTheDocument();
    const successivo = screen.getByRole('dialog', { name: 'Inserisci a mano · busta-scansione.pdf' });
    await within(successivo).findByRole('form', { name: 'Dati del cedolino' });
    await user.click(pulsante(successivo, 'Chiudi la finestra'));
    await waitFor(() => expect(successivo).not.toBeInTheDocument());

    expect(within(screen.getByRole('banner')).getByRole('combobox', { name: 'Persona' })).toHaveValue(
      'Mario Rossi',
    );
    const mensilita = (await screen.findByRole('heading', { name: 'Mensilità' })).closest('section');
    expect(
      await within(mensilita!).findByRole('button', { name: /^Mostra il dettaglio di Agosto 2025(?!\d)/ }),
    ).toBeInTheDocument();
    expect(
      within(mensilita!).queryByRole('listitem', { name: 'Agosto 2025: Caricato con errore' }),
    ).not.toBeInTheDocument();
  });

  it('"Salva e apri il successivo" non propone i file il cui mese associato è diventato presente', async () => {
    const { user, backend, queryClient } = renderConMock(<App />, { datiIniziali: 'errori' });
    await areaInErrore();
    // cedolino-protetto.pdf associato a Luglio 2025, nel frattempo presente: va solo rimosso
    const protetto = backend.db.caricamenti.find((c) => c.nomeFileOriginale === 'cedolino-protetto.pdf');
    backend.db.associazioni.set(chiaveFile(protetto!), { rapportoId: 1, anno: 2025, mese: 7 });
    await act(() => queryClient.invalidateQueries({ queryKey: chiavi.caricamentiInErrore }));
    const dialogo = await apriInserimento(user, 'ristampa.pdf');
    await compilaTotali(user, dialogo);

    await user.click(pulsante(dialogo, 'Verifica'));
    await within(dialogo).findByRole('region', { name: 'Esito della verifica' });

    expect(pulsante(dialogo, 'Salva e apri il successivo (1)')).toBeEnabled();
  });

  it('voci e ratei copiati restano modelli: si salvano solo se completati e si tolgono con un clic', async () => {
    const richieste: CedolinoManuale[] = [];
    const { user } = renderConMock(<App />, {
      datiIniziali: 'errori',
      avvolgiFetch: registraVerifiche(richieste),
    });
    const dialogo = await apriInserimento(user, 'ristampa.pdf');
    const righeVoci = () =>
      dialogo.querySelectorAll('[data-path^="voci."][data-path$=".descrizione"]').length;
    const copiate = righeVoci();
    expect(copiate).toBeGreaterThan(1);
    expect(
      within(dialogo).getByText(
        `Voci copiate senza quantità né importi: ${String(copiate)}. Non vengono salvate finché non le completi.`,
      ),
    ).toBeInTheDocument();
    await user.click(within(dialogo).getByRole('button', { name: /^Ratei/ }));
    expect(
      await within(dialogo).findByText(/^Ratei copiati senza maturato, goduto né saldo: \d+\./),
    ).toBeInTheDocument();

    await compilaTotali(user, dialogo);
    await scrivi(user, dialogo, 'voci.0.importoCompetenza', '1800');
    expect(
      within(dialogo).getByText(
        new RegExp(`^Voci copiate senza quantità né importi: ${String(copiate - 1)}\\.`),
      ),
    ).toBeInTheDocument();
    // ratei con i decimali dei cedolini e anche negativi (conguagli)
    await scrivi(user, dialogo, 'ratei.0.maturato', '2,16667');
    await scrivi(user, dialogo, 'ratei.0.goduto', '-1');
    expect(campo(dialogo, 'ratei.0.maturato')).toHaveValue('2,16667');
    expect(campo(dialogo, 'ratei.0.goduto')).toHaveValue('-1');
    await scrivi(user, dialogo, 'voci.1.quantita', '7,33333');
    expect(campo(dialogo, 'voci.1.quantita')).toHaveValue('7,33333');

    await user.click(pulsante(dialogo, 'Rimuovi le voci copiate vuote'));
    expect(righeVoci()).toBe(2);
    expect(within(dialogo).queryByText(/^Voci copiate senza/)).not.toBeInTheDocument();
    await user.click(pulsante(dialogo, 'Verifica'));
    await within(dialogo).findByRole('region', { name: 'Esito della verifica' });

    const [richiesta] = richieste;
    expect(richiesta?.voci.map((v) => [v.importoCompetenza, v.quantita])).toEqual([
      [1800, null],
      [null, 7.33333],
    ]);
    expect(richiesta?.ratei).toEqual([expect.objectContaining({ maturato: 2.16667, goduto: -1 })]);
  });

  it('una data che il browser non riesce a interpretare è un errore, non un campo vuoto', async () => {
    const { user } = renderConMock(<App />, { datiIniziali: 'errori' });
    const dialogo = await apriInserimento(user, 'busta-scansione.pdf');
    await compilaObbligatori(user, dialogo);
    const data = campo(dialogo, 'periodo.dataElaborazione');
    // giorno, mese o anno mancanti: il campo type="date" ha valore vuoto e validity.badInput
    Object.defineProperty(data, 'validity', { configurable: true, value: { badInput: true } });

    await user.click(data);
    await user.tab();

    expect(
      await within(dialogo).findByText('Data incompleta o inesistente: indica giorno, mese e anno'),
    ).toBeInTheDocument();
    await user.click(pulsante(dialogo, 'Verifica'));
    expect(await within(dialogo).findByText('1 campo da correggere')).toBeInTheDocument();
    expect(within(dialogo).queryByRole('region', { name: 'Esito della verifica' })).not.toBeInTheDocument();
  });

  it('un errore di riga sparisce quando la riga viene corretta', async () => {
    const { user } = renderConMock(<App />, { datiIniziali: 'errori' });
    const dialogo = await apriInserimento(user, 'busta-scansione.pdf');
    await compilaObbligatori(user, dialogo);
    await user.click(within(dialogo).getByRole('button', { name: /^Ratei/ }));
    await user.click(await within(dialogo).findByRole('button', { name: '+ Ferie (giorni)' }));

    await user.click(pulsante(dialogo, 'Verifica'));
    const riga = within(dialogo).getByRole('listitem', { name: 'Rateo 1' });
    expect(await within(riga).findByText('Indica almeno un valore del rateo')).toBeInTheDocument();

    await scrivi(user, dialogo, 'ratei.0.maturato', '2,5');

    expect(within(riga).queryByText('Indica almeno un valore del rateo')).not.toBeInTheDocument();
    expect(within(dialogo).queryByText('1 campo da correggere')).not.toBeInTheDocument();
  });

  it('un mese già presente non si può salvare: il file si può rimuovere dalla finestra', async () => {
    const { user } = renderConMock(<App />, { datiIniziali: 'errori' });
    const dialogo = await apriInserimento(user, 'ristampa.pdf');
    await scegli(user, dialogo, 'periodo.mese', 'Luglio');
    await compilaTotali(user, dialogo);

    await user.click(pulsante(dialogo, 'Verifica'));
    const esito = await within(dialogo).findByRole('region', { name: 'Esito della verifica' });
    expect(within(esito).getByText('Il cedolino non può essere salvato')).toBeInTheDocument();
    expect(esito).toHaveTextContent(/Luglio 2025/);
    expect(annuncio(dialogo)).toHaveTextContent(
      /^Verifica completata: il cedolino non può essere salvato: .*Luglio 2025/,
    );
    expect(pulsante(dialogo, 'Salva cedolino')).toBeDisabled();

    await user.click(within(esito).getByRole('button', { name: 'Rimuovi questo file' }));
    const conferma = await screen.findByRole('dialog', { name: 'Rimuovere il file in errore?' });
    await user.click(within(conferma).getByRole('button', { name: 'Rimuovi file' }));

    await waitFor(() => expect(dialogo).not.toBeInTheDocument());
    expect(await screen.findByText('File rimosso: ristampa.pdf')).toBeInTheDocument();
  });

  it('gli errori del server si mostrano sui campi, con gli indici delle righe del modulo', async () => {
    const { user } = renderConMock(<App />, {
      datiIniziali: 'errori',
      avvolgiFetch: intercetta(/\/cedolino\/verifica$/, () =>
        problema(400, {
          title: 'Dati non validi',
          errori: [
            { campo: 'voci[0].descrizione', messaggio: 'Testo troppo lungo (massimo 200 caratteri)' },
            { campo: null, messaggio: 'Corpo della richiesta non valido' },
          ],
        }),
      ),
    });
    const dialogo = await apriInserimento(user, 'busta-scansione.pdf');
    await compilaObbligatori(user, dialogo);
    // una riga vuota (non inviata) e una compilata: per il server è la voce 0, nel modulo la 1
    await user.click(pulsante(dialogo, 'Aggiungi voce'));
    await user.click(pulsante(dialogo, 'Aggiungi voce'));
    await scrivi(user, dialogo, 'voci.1.descrizione', 'Retribuzione ordinaria');

    await user.click(pulsante(dialogo, 'Verifica'));

    expect(await within(dialogo).findByText('2 campi da correggere')).toBeInTheDocument();
    await waitFor(() => expect(campo(dialogo, 'voci.1.descrizione')).toHaveFocus());
    expect(campo(dialogo, 'voci.1.descrizione')).toHaveAttribute('aria-invalid', 'true');
    expect(campo(dialogo, 'voci.0.descrizione')).not.toHaveAttribute('aria-invalid', 'true');
    expect(within(dialogo).getByText('Corpo della richiesta non valido')).toBeInTheDocument();
  });

  it('un rifiuto del salvataggio (409) resta nella finestra con tutti i dati', async () => {
    const { user } = renderConMock(<App />, {
      datiIniziali: 'errori',
      avvolgiFetch: intercetta(/\/caricamenti\/\d+\/cedolino$/, () =>
        problema(409, { title: 'Conflitto', detail: 'Il file è in elaborazione: riprova tra poco.' }),
      ),
    });
    const dialogo = await apriInserimento(user, 'busta-scansione.pdf');
    await compilaObbligatori(user, dialogo);
    await user.click(pulsante(dialogo, 'Verifica'));
    await within(dialogo).findByRole('region', { name: 'Esito della verifica' });

    await user.click(pulsante(dialogo, 'Salva cedolino'));

    const errore = await within(dialogo).findByText('Il file è in elaborazione: riprova tra poco.');
    expect(errore.closest('[role="alert"]')).toHaveTextContent('Impossibile salvare il cedolino');
    expect(dialogo).toBeInTheDocument();
    expect(campo(dialogo, 'dipendente.codiceFiscale')).toHaveValue(CF_NUOVO);
    expect(campo(dialogo, 'totali.netto')).toHaveValue('1.500');
  });

  it('sugli schermi stretti PDF e dati sono due schede, senza perdere i dati inseriti', async () => {
    vi.spyOn(window, 'matchMedia').mockImplementation((query) => ({
      matches: query.includes('max-width'),
      media: query,
      onchange: null,
      addListener: () => undefined,
      removeListener: () => undefined,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      dispatchEvent: () => false,
    }));
    const { user } = renderConMock(<App />, { datiIniziali: 'errori' });
    const dialogo = await apriInserimento(user, 'busta-scansione.pdf');
    const schedaDati = within(dialogo).getByRole('tab', { name: 'Dati' });
    expect(schedaDati).toHaveAttribute('aria-selected', 'true');
    const schedaPdf = within(dialogo).getByRole('tab', { name: 'PDF' });
    const pannelloPdf = document.getElementById(schedaPdf.getAttribute('aria-controls') ?? '');
    expect(pannelloPdf).not.toBeVisible();
    await scrivi(user, dialogo, 'dipendente.nome', 'Giuseppe');

    await user.click(schedaPdf);
    expect(pannelloPdf).toBeVisible();
    expect(within(pannelloPdf!).getByRole('link', { name: 'Apri in una nuova scheda' })).toBeVisible();
    expect(within(dialogo).queryByRole('form', { name: 'Dati del cedolino' })).not.toBeInTheDocument();

    await user.click(schedaDati);
    expect(campo(dialogo, 'dipendente.nome')).toHaveValue('Giuseppe');
  });

  it('chiudere un modulo con dati non salvati chiede conferma', async () => {
    const { user } = renderConMock(<App />, { datiIniziali: 'errori' });
    const dialogo = await apriInserimento(user, 'busta-scansione.pdf');
    await scrivi(user, dialogo, 'dipendente.nome', 'Giuseppe');

    await user.click(pulsante(dialogo, 'Chiudi la finestra'));
    let conferma = await screen.findByRole('dialog', { name: 'Chiudere senza salvare?' });
    await user.click(within(conferma).getByRole('button', { name: 'Continua l’inserimento' }));
    await waitFor(() => expect(conferma).not.toBeInTheDocument());
    expect(campo(dialogo, 'dipendente.nome')).toHaveValue('Giuseppe');

    await user.click(pulsante(dialogo, 'Chiudi la finestra'));
    conferma = await screen.findByRole('dialog', { name: 'Chiudere senza salvare?' });
    await user.click(within(conferma).getByRole('button', { name: 'Chiudi senza salvare' }));
    await waitFor(() => expect(dialogo).not.toBeInTheDocument());
    // il file resta da completare
    expect(rigaInErrore(await areaInErrore(), 'busta-scansione.pdf')).toBeInTheDocument();
  });
});
