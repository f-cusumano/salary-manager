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
// Agosto 2025 di Mario Rossi presso Alfa Software); in archivio Mario Rossi (Alfa Software, poi Beta Consulting) e
// Laura Bianchi (Gamma Servizi). Il modulo e' grande: i campi si cercano per `data-path`, lo stesso attributo usato dal
// modulo per spostare il focus sugli errori.

/** Codice fiscale inventato ma formalmente valido (carattere di controllo compreso). */
const CF_NUOVO = 'VRDGPP90C15L219G';
const PERSONA = 'Persona in archivio';
const AZIENDA = 'Azienda in archivio';

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

function tendina(dialogo: HTMLElement, nome: string): HTMLInputElement {
  return within(dialogo).getByRole<HTMLInputElement>('combobox', { name: nome });
}

/** Sceglie una persona o un'azienda in archivio, a elenco caricato. */
async function scegliAnagrafica(
  user: Utente,
  dialogo: HTMLElement,
  nome: string,
  opzione: string | RegExp,
): Promise<void> {
  await waitFor(() => expect(tendina(dialogo, nome)).toBeEnabled());
  await user.click(tendina(dialogo, nome));
  await user.click(await within(dialogo).findByRole('option', { name: opzione }));
}

/** Nomi delle opzioni della tendina aperta, nell'ordine. */
async function opzioni(user: Utente, dialogo: HTMLElement, nome: string): Promise<string[]> {
  await waitFor(() => expect(tendina(dialogo, nome)).toBeEnabled());
  await user.click(tendina(dialogo, nome));
  const elenco = await within(dialogo).findByRole('listbox');
  return within(elenco)
    .getAllByRole('option')
    .map((o) => o.textContent);
}

function etichettaDi(dialogo: HTMLElement, percorso: string): HTMLElement {
  const etichetta = dialogo.querySelector<HTMLElement>(`label[for="${campo(dialogo, percorso).id}"]`);
  if (!etichetta) {
    throw new Error(`etichetta di ${percorso} non trovata`);
  }
  return etichetta;
}

/** Provenienza di un campo compilato in automatico: il suggerimento del punto blu accanto all'etichetta. */
async function notaCopiato(user: Utente, dialogo: HTMLElement, percorso: string): Promise<string | null> {
  const punto = etichettaDi(dialogo, percorso).querySelector<HTMLElement>('[data-copiato]');
  if (!punto) {
    return null;
  }
  await user.hover(punto);
  const nota = (await screen.findByRole('tooltip')).textContent;
  await user.unhover(punto);
  await waitFor(() => expect(screen.queryByRole('tooltip')).not.toBeInTheDocument());
  return nota;
}

function inSolaLettura(dialogo: HTMLElement, ...percorsi: string[]): void {
  for (const percorso of percorsi) {
    expect(campo(dialogo, percorso)).toHaveAttribute('readonly');
  }
}

function assente(dialogo: HTMLElement, percorso: string): void {
  expect(dialogo.querySelector(`[data-path="${percorso}"]`)).toBeNull();
}

/** «Crea nuovo» per la persona e «Crea nuova» per l'azienda. */
async function creaNuove(user: Utente, dialogo: HTMLElement): Promise<void> {
  await user.click(pulsante(dialogo, 'Crea nuovo dipendente'));
  await user.click(pulsante(dialogo, 'Crea nuova azienda'));
}

/** Campi obbligatori di un cedolino di Maggio 2025 di una persona nuova (Giuseppe Verdi, Delta Servizi). */
async function compilaObbligatori(user: Utente, dialogo: HTMLElement): Promise<void> {
  await scegli(user, dialogo, 'periodo.tipo', 'Mensilità ordinaria');
  await scegli(user, dialogo, 'periodo.mese', 'Maggio');
  await scrivi(user, dialogo, 'periodo.anno', '2025');
  await creaNuove(user, dialogo);
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
  it('dal mese associato sceglie persona e azienda (dati in sola lettura) e precompila dal rapporto', async () => {
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
    expect(tendina(dialogo, PERSONA)).toHaveValue('Mario Rossi');
    expect(tendina(dialogo, AZIENDA)).toHaveValue('Alfa Software S.r.l.');
    expect(campo(dialogo, 'dipendente.codiceFiscale')).toHaveValue('RSSMRA80A01H501U');
    expect(campo(dialogo, 'dipendente.cognome')).toHaveValue('Rossi');
    expect(campo(dialogo, 'dipendente.dataNascita')).toHaveValue('1980-01-01');
    expect(campo(dialogo, 'datoreLavoro.ragioneSociale')).toHaveValue('Alfa Software S.r.l.');
    expect(campo(dialogo, 'datoreLavoro.partitaIva')).toHaveValue('01234567897');
    inSolaLettura(
      dialogo,
      'dipendente.codiceFiscale',
      'dipendente.cognome',
      'dipendente.nome',
      'dipendente.dataNascita',
      'datoreLavoro.ragioneSociale',
      'datoreLavoro.partitaIva',
      'datoreLavoro.codiceFiscale',
      'datoreLavoro.posizioneInps',
    );
    expect(campo(dialogo, 'dipendente.codiceDipendente')).not.toHaveAttribute('readonly');
    expect(
      within(dialogo).getByText('Dati in archivio: per cambiarli scegli un’altra persona'),
    ).toBeVisible();
    // indirizzo dell'azienda in archivio: si puo' cambiare
    expect(campo(dialogo, 'datoreLavoro.indirizzo')).toHaveValue('Via Roma 1, 00100 Roma');
    expect(campo(dialogo, 'datoreLavoro.indirizzo')).not.toHaveAttribute('readonly');
    expect(await notaCopiato(user, dialogo, 'datoreLavoro.indirizzo')).toBe('Dall’azienda scelta: verifica');
    expect(campo(dialogo, 'datiContrattuali.dataAssunzione')).toHaveValue('2022-03-01');
    expect(await notaCopiato(user, dialogo, 'datiContrattuali.dataAssunzione')).toBe(
      'Copiato da Alfa Software S.r.l.: verifica',
    );
    // i dati in archivio non sono "copiati": nessun punto blu
    expect(etichettaDi(dialogo, 'dipendente.codiceFiscale').querySelector('[data-copiato]')).toBeNull();
    expect(campo(dialogo, 'datiContrattuali.dataCessazione')).toHaveValue('');
    // dal cedolino di Luglio 2025: le voci senza importi
    expect(campo(dialogo, 'voci.0.descrizione')).not.toHaveValue('');
    expect(campo(dialogo, 'voci.0.importoCompetenza')).toHaveValue('');
    expect(within(dialogo).getByText(/sono stati compilati in automatico/)).toBeInTheDocument();
    expect(within(dialogo).getByText('Copiato da Luglio 2025: verifica')).toBeInTheDocument();
    // i totali non si copiano mai
    expect(campo(dialogo, 'totali.netto')).toHaveValue('');
  });

  it('senza mese associato: scelta la persona le sue aziende vengono prima, scelta l’azienda il modulo si precompila', async () => {
    const { user } = renderConMock(<App />, { datiIniziali: 'errori' });
    const dialogo = await apriInserimento(user, 'busta-scansione.pdf');
    expect(tendina(dialogo, PERSONA)).toHaveValue('');
    expect(tendina(dialogo, PERSONA)).toHaveAttribute('placeholder', 'Seleziona persona/dipendente');
    assente(dialogo, 'dipendente.codiceFiscale');
    assente(dialogo, 'datoreLavoro.ragioneSociale');
    await scegli(user, dialogo, 'periodo.mese', 'Aprile');
    await scrivi(user, dialogo, 'periodo.anno', '2026');

    await scegliAnagrafica(user, dialogo, PERSONA, 'Laura Bianchi');
    expect(campo(dialogo, 'dipendente.codiceFiscale')).toHaveValue('BNCLRA85M41F205X');
    inSolaLettura(dialogo, 'dipendente.codiceFiscale', 'dipendente.cognome', 'dipendente.nome');

    expect(await opzioni(user, dialogo, AZIENDA)).toEqual([
      'Gamma Servizi S.r.l.',
      'Alfa Software S.r.l.',
      'Beta Consulting S.p.A.',
    ]);
    const elenco = within(dialogo).getByRole('listbox');
    expect(within(elenco).getByText('Aziende di Laura Bianchi')).toBeInTheDocument();
    expect(within(elenco).getByText('Altre aziende')).toBeInTheDocument();
    await user.click(within(elenco).getByRole('option', { name: 'Gamma Servizi S.r.l.' }));

    await waitFor(() => expect(campo(dialogo, 'datiContrattuali.dataAssunzione')).toHaveValue('2025-11-03'));
    expect(campo(dialogo, 'datoreLavoro.ragioneSociale')).toHaveValue('Gamma Servizi S.r.l.');
    // un identificativo che l'archivio non ha resta vuoto, non si scrive a mano
    expect(campo(dialogo, 'datoreLavoro.codiceFiscale')).toHaveValue('');
    expect(campo(dialogo, 'datoreLavoro.codiceFiscale')).toHaveAttribute(
      'placeholder',
      'Non presente in archivio',
    );
    expect(campo(dialogo, 'datiContrattuali.qualifica')).toHaveValue('Impiegata');
    expect(campo(dialogo, 'voci.0.descrizione')).not.toHaveValue('');
    expect(within(dialogo).getByText('Copiato da Aprile 2026: verifica')).toBeInTheDocument();
    expect(campo(dialogo, 'periodo.mese')).toHaveValue('Aprile');
  });

  it('cambiando azienda o persona i dati compilati in automatico si sostituiscono, quelli modificati restano', async () => {
    const { user } = renderConMock(<App />, { datiIniziali: 'errori' });
    const dialogo = await apriInserimento(user, 'ristampa.pdf');
    expect(campo(dialogo, 'datiContrattuali.livello')).toHaveValue('2');
    await user.clear(campo(dialogo, 'datiContrattuali.qualifica'));
    await scrivi(user, dialogo, 'datiContrattuali.qualifica', 'Quadro');

    await scegliAnagrafica(user, dialogo, AZIENDA, 'Beta Consulting S.p.A.');

    await waitFor(() => expect(campo(dialogo, 'datiContrattuali.dataAssunzione')).toHaveValue('2018-09-03'));
    expect(campo(dialogo, 'datoreLavoro.ragioneSociale')).toHaveValue('Beta Consulting S.p.A.');
    expect(campo(dialogo, 'datoreLavoro.indirizzo')).toHaveValue('Corso Italia 10, 20100 Milano');
    expect(campo(dialogo, 'datiContrattuali.livello')).toHaveValue('C3');
    expect(campo(dialogo, 'datiContrattuali.qualifica')).toHaveValue('Quadro');
    expect(await notaCopiato(user, dialogo, 'datiContrattuali.dataAssunzione')).toBe(
      'Copiato da Beta Consulting S.p.A.: verifica',
    );
    expect(within(dialogo).getByText(/^Copiato da .+ 2022: verifica$/)).toBeInTheDocument();
    // il periodo del mese associato resta
    expect(campo(dialogo, 'periodo.mese')).toHaveValue('Agosto');
    expect(tendina(dialogo, PERSONA)).toHaveValue('Mario Rossi');

    // Laura Bianchi non ha rapporti con Beta Consulting: i dati del rapporto precedente si tolgono
    await scegliAnagrafica(user, dialogo, PERSONA, 'Laura Bianchi');
    expect(campo(dialogo, 'dipendente.codiceFiscale')).toHaveValue('BNCLRA85M41F205X');
    expect(campo(dialogo, 'datiContrattuali.dataAssunzione')).toHaveValue('');
    expect(campo(dialogo, 'datiContrattuali.livello')).toHaveValue('');
    expect(campo(dialogo, 'datiContrattuali.qualifica')).toHaveValue('Quadro');
    expect(campo(dialogo, 'datoreLavoro.ragioneSociale')).toHaveValue('Beta Consulting S.p.A.');
    expect(campo(dialogo, 'datoreLavoro.indirizzo')).toHaveValue('Corso Italia 10, 20100 Milano');

    await compilaTotali(user, dialogo);
    await user.click(pulsante(dialogo, 'Verifica'));
    const esito = await within(dialogo).findByRole('region', { name: 'Esito della verifica' });
    expect(within(esito).getByText('Nuovo rapporto di lavoro')).toBeInTheDocument();
    expect(
      within(esito).getByText('Persona o azienda diverse da quelle del mese associato al file (Agosto 2025)'),
    ).toBeInTheDocument();
  });

  it('«Crea nuovo» con un codice fiscale già in archivio è un errore; «Scegli dall’elenco» torna alla tendina', async () => {
    const { user } = renderConMock(<App />, { datiIniziali: 'errori' });
    const dialogo = await apriInserimento(user, 'busta-scansione.pdf');

    await user.click(pulsante(dialogo, 'Crea nuovo dipendente'));

    expect(
      within(dialogo).getByText('Nuova persona: inserisci i dati come stampati sul cedolino.'),
    ).toBeVisible();
    expect(within(dialogo).queryByRole('combobox', { name: PERSONA })).not.toBeInTheDocument();
    await waitFor(() => expect(campo(dialogo, 'dipendente.codiceFiscale')).toHaveFocus());
    expect(campo(dialogo, 'dipendente.codiceFiscale')).not.toHaveAttribute('readonly');
    await scrivi(user, dialogo, 'dipendente.codiceFiscale', 'rssmra80a01h501u');
    await scrivi(user, dialogo, 'dipendente.cognome', 'Rossi');
    await scrivi(user, dialogo, 'dipendente.nome', 'Mario');

    await user.click(pulsante(dialogo, 'Verifica'));

    const messaggio = "Questo codice fiscale è già in archivio: seleziona la persona dall'elenco";
    expect(await within(dialogo).findAllByText(messaggio)).not.toHaveLength(0);
    expect(campo(dialogo, 'dipendente.codiceFiscale')).toHaveAttribute('aria-invalid', 'true');

    await user.click(pulsante(dialogo, 'Scegli dall’elenco delle persone'));

    await waitFor(() => expect(tendina(dialogo, PERSONA)).toHaveFocus());
    assente(dialogo, 'dipendente.codiceFiscale');
    expect(within(dialogo).queryByText(messaggio)).not.toBeInTheDocument();
    await scegliAnagrafica(user, dialogo, PERSONA, 'Mario Rossi');
    expect(campo(dialogo, 'dipendente.codiceFiscale')).toHaveValue('RSSMRA80A01H501U');
    inSolaLettura(dialogo, 'dipendente.codiceFiscale');
  });

  it('le tendine sono obbligatorie; «Crea nuovo» toglie l’errore della scelta e i dati scritti restano tornando dall’elenco', async () => {
    const { user } = renderConMock(<App />, { datiIniziali: 'errori' });
    const dialogo = await apriInserimento(user, 'busta-scansione.pdf');
    await waitFor(() => expect(tendina(dialogo, PERSONA)).toBeEnabled());
    expect(tendina(dialogo, PERSONA)).toBeRequired();
    expect(tendina(dialogo, AZIENDA)).toBeRequired();
    expect(etichettaDi(dialogo, 'personaId')).toHaveTextContent(`${PERSONA} *`);

    await user.click(pulsante(dialogo, 'Verifica'));
    expect(await within(dialogo).findByText('8 campi da correggere')).toBeInTheDocument();
    const intestazione = within(dialogo).getByRole('button', { name: /^Dipendente/ });
    expect(intestazione).toHaveTextContent('1 errore');

    // fatto cio' che chiedeva il messaggio, l'errore sparisce subito da riepilogo e intestazione
    await user.click(pulsante(dialogo, 'Crea nuovo dipendente'));

    expect(within(dialogo).getByText('7 campi da correggere')).toBeInTheDocument();
    expect(
      within(dialogo).queryByText("Scegli la persona dall'elenco o «Crea nuovo»"),
    ).not.toBeInTheDocument();
    expect(intestazione).not.toHaveTextContent('errore');
    await scrivi(user, dialogo, 'dipendente.codiceFiscale', CF_NUOVO);
    await scrivi(user, dialogo, 'dipendente.cognome', 'Verdi');
    await user.clear(campo(dialogo, 'dipendente.cognome'));
    await user.tab();
    expect(within(dialogo).getByText('8 campi da correggere')).toBeInTheDocument();
    await scrivi(user, dialogo, 'dipendente.cognome', 'Verdi');
    await scrivi(user, dialogo, 'dipendente.nome', 'Giuseppe');

    // si torna all'elenco a cercare un omonimo e non lo si trova: i dati scritti non si perdono
    await user.click(pulsante(dialogo, 'Scegli dall’elenco delle persone'));
    await waitFor(() => expect(tendina(dialogo, PERSONA)).toHaveFocus());
    await user.click(pulsante(dialogo, 'Crea nuovo dipendente'));

    expect(campo(dialogo, 'dipendente.codiceFiscale')).toHaveValue(CF_NUOVO);
    expect(campo(dialogo, 'dipendente.cognome')).toHaveValue('Verdi');
    expect(campo(dialogo, 'dipendente.nome')).toHaveValue('Giuseppe');
    // scegliendo una persona in archivio i dati sono i suoi; con "Crea nuovo" si riparte da vuoto
    await user.click(pulsante(dialogo, 'Scegli dall’elenco delle persone'));
    await scegliAnagrafica(user, dialogo, PERSONA, 'Laura Bianchi');
    expect(campo(dialogo, 'dipendente.cognome')).toHaveValue('Bianchi');
    await user.click(pulsante(dialogo, 'Crea nuovo dipendente'));
    expect(campo(dialogo, 'dipendente.cognome')).toHaveValue('');
  });

  it('date e buoni pasto scritti dopo un cambio di scelta arrivano alla richiesta', async () => {
    const richieste: CedolinoManuale[] = [];
    const { user } = renderConMock(<App />, {
      datiIniziali: 'errori',
      avvolgiFetch: registraVerifiche(richieste),
    });
    const dialogo = await apriInserimento(user, 'ristampa.pdf');
    expect(campo(dialogo, 'datiContrattuali.dataAssunzione')).toHaveValue('2022-03-01');
    await user.click(within(dialogo).getByRole('button', { name: /^Scatti e buoni pasto/ }));
    const buoni = await within(dialogo).findByRole('switch', { name: /^Buoni pasto presenti/ });
    expect(buoni).toBeChecked();
    await scrivi(user, dialogo, 'buoniPasto.numero', '20');
    await scrivi(user, dialogo, 'buoniPasto.importo', '160');

    // un'azienda nuova non ha rapporti: i dati copiati dal rapporto si tolgono, quelli scritti restano
    await user.click(pulsante(dialogo, 'Crea nuova azienda'));

    expect(campo(dialogo, 'datiContrattuali.dataAssunzione')).toHaveValue('');
    expect(buoni).toBeChecked();
    expect(campo(dialogo, 'buoniPasto.numero')).toHaveValue('20');
    expect(campo(dialogo, 'buoniPasto.valoreUnitario')).toHaveValue('');
    // la stessa data che era stata copiata, riscritta a mano
    await user.type(campo(dialogo, 'datiContrattuali.dataAssunzione'), '2022-03-01');
    await scrivi(user, dialogo, 'datoreLavoro.ragioneSociale', 'Delta Servizi S.r.l.');
    await compilaTotali(user, dialogo);
    await user.click(pulsante(dialogo, 'Verifica'));

    await within(dialogo).findByRole('region', { name: 'Esito della verifica' });
    expect(richieste.at(-1)).toMatchObject({
      aziendaId: null,
      datiContrattuali: { dataAssunzione: '2022-03-01' },
      buoniPasto: { presenti: true, numero: 20, valoreUnitario: null, importo: 160 },
    });
  });

  it('cambiando il periodo dopo la scelta la precompilazione segue il cedolino più vicino al periodo', async () => {
    const richieste: CedolinoManuale[] = [];
    const { user } = renderConMock(<App />, {
      datiIniziali: 'errori',
      avvolgiFetch: registraVerifiche(richieste),
    });
    const dialogo = await apriInserimento(user, 'busta-scansione.pdf');
    await scegli(user, dialogo, 'periodo.tipo', 'Mensilità ordinaria');
    await scegliAnagrafica(user, dialogo, PERSONA, 'Mario Rossi');
    await scegliAnagrafica(user, dialogo, AZIENDA, 'Alfa Software S.r.l.');
    // senza periodo il riferimento e' il cedolino piu' recente
    expect(await within(dialogo).findByText('Copiato da Settembre 2026: verifica')).toBeInTheDocument();
    await user.clear(campo(dialogo, 'datiContrattuali.qualifica'));
    await scrivi(user, dialogo, 'datiContrattuali.qualifica', 'Quadro');

    await scegli(user, dialogo, 'periodo.mese', 'Febbraio');
    await scrivi(user, dialogo, 'periodo.anno', '2024');

    expect(await within(dialogo).findByText('Copiato da Febbraio 2024: verifica')).toBeInTheDocument();
    expect(within(dialogo).queryByText('Copiato da Settembre 2026: verifica')).not.toBeInTheDocument();
    expect(campo(dialogo, 'datiContrattuali.qualifica')).toHaveValue('Quadro');
    expect(campo(dialogo, 'datiContrattuali.dataAssunzione')).toHaveValue('2022-03-01');
    await compilaTotali(user, dialogo);
    await user.click(pulsante(dialogo, 'Verifica'));
    await within(dialogo).findByRole('region', { name: 'Esito della verifica' });
    // stessi dati di una scelta fatta dopo il periodo: elementi prima del rinnovo, fondo TFR dello stesso anno
    expect(richieste.at(-1)).toMatchObject({
      periodo: { anno: 2024, mese: 2 },
      datiContrattuali: { qualifica: 'Quadro' },
      retribuzione: { minimo: 1780.1 },
      tfr: { fondo31DicembreAnnoPrecedente: 15900 },
    });
  });

  it('«Crea nuova» con la ragione sociale di un’azienda in archivio: l’errore del server è sul campo', async () => {
    const { user } = renderConMock(<App />, { datiIniziali: 'errori' });
    const dialogo = await apriInserimento(user, 'busta-scansione.pdf');
    await scegli(user, dialogo, 'periodo.tipo', 'Mensilità ordinaria');
    await scegli(user, dialogo, 'periodo.mese', 'Maggio');
    await scrivi(user, dialogo, 'periodo.anno', '2025');
    await scegliAnagrafica(user, dialogo, PERSONA, 'Mario Rossi');
    await user.click(pulsante(dialogo, 'Crea nuova azienda'));
    await waitFor(() => expect(campo(dialogo, 'datoreLavoro.ragioneSociale')).toHaveFocus());
    await scrivi(user, dialogo, 'datoreLavoro.ragioneSociale', 'ALFA SOFTWARE SRL');
    await compilaTotali(user, dialogo);

    await user.click(pulsante(dialogo, 'Verifica'));

    expect(await within(dialogo).findByText('1 campo da correggere')).toBeInTheDocument();
    await waitFor(() => expect(campo(dialogo, 'datoreLavoro.ragioneSociale')).toHaveFocus());
    expect(campo(dialogo, 'datoreLavoro.ragioneSociale')).toHaveAttribute('aria-invalid', 'true');
    expect(
      within(dialogo).getAllByText(
        "Un'azienda con questa ragione sociale è già in archivio: selezionala dall'elenco",
      ).length,
    ).toBeGreaterThan(0);
  });

  it('gli omonimi si distinguono: la persona con il codice fiscale, l’azienda con la partita IVA', async () => {
    const { user, backend, queryClient } = renderConMock(<App />, { datiIniziali: 'errori' });
    await areaInErrore();
    // un altro Mario Rossi e un'altra Alfa Software S.r.l. (di Laura Bianchi), inventati
    backend.db.persone.push({
      id: 50,
      nome: 'Mario',
      cognome: 'Rossi',
      codiceFiscale: 'RSSMRA85C12F205M',
      dataNascita: null,
      etichetta: 'Mario Rossi',
    });
    const laura = backend.db.rapporti.get(3)!;
    backend.db.rapporti.set(90, {
      ...laura,
      id: 90,
      azienda: {
        ...laura.azienda,
        id: 90,
        ragioneSociale: 'Alfa Software S.r.l.',
        partitaIva: '22334455668',
      },
      mensilita: [],
    });
    await act(() => queryClient.invalidateQueries({ queryKey: chiavi.persone }));
    const dialogo = await apriInserimento(user, 'ristampa.pdf');

    expect(tendina(dialogo, PERSONA)).toHaveValue('Mario Rossi (RSSMRA80A01H501U)');
    expect(tendina(dialogo, AZIENDA)).toHaveValue('Alfa Software S.r.l. (P.IVA 01234567897)');
    expect(await opzioni(user, dialogo, AZIENDA)).toEqual([
      'Alfa Software S.r.l. (P.IVA 01234567897)',
      'Beta Consulting S.p.A.',
      'Alfa Software S.r.l. (P.IVA 22334455668)',
      'Gamma Servizi S.r.l.',
    ]);
    // la ricerca trova anche per codice fiscale
    await user.clear(tendina(dialogo, PERSONA));
    await user.type(tendina(dialogo, PERSONA), 'rssmra85');
    const elenco = await within(dialogo).findByRole('listbox');
    await waitFor(() =>
      expect(
        within(elenco)
          .getAllByRole('option')
          .map((o) => o.textContent),
      ).toEqual(['Mario Rossi (RSSMRA85C12F205M)']),
    );
    await user.click(within(elenco).getByRole('option', { name: 'Mario Rossi (RSSMRA85C12F205M)' }));
    expect(campo(dialogo, 'dipendente.codiceFiscale')).toHaveValue('RSSMRA85C12F205M');
    // senza data di nascita in archivio si puo' indicare quella stampata
    expect(campo(dialogo, 'dipendente.dataNascita')).not.toHaveAttribute('readonly');
  });

  it('se la lettura dell’elenco delle aziende o del rapporto non riesce si può riprovare', async () => {
    let guasto = true;
    const { user } = renderConMock(<App />, {
      datiIniziali: 'errori',
      avvolgiFetch: (fetchApi) => (request) =>
        guasto &&
        request.method === 'GET' &&
        /^\/api\/v1\/(aziende|persone\/\d+\/rapporti)$/.test(new URL(request.url).pathname)
          ? problema(500, { title: 'Errore interno', detail: 'Servizio non disponibile' })
          : fetchApi(request),
    });
    const dialogo = await apriInserimento(user, 'busta-scansione.pdf');
    const erroreElenco = (
      await within(dialogo).findByText('Impossibile leggere l’elenco delle aziende')
    ).closest('[role="alert"]');
    expect(erroreElenco).toHaveTextContent('Servizio non disponibile');
    // «Crea nuova» resta disponibile anche senza elenco
    expect(pulsante(dialogo, 'Crea nuova azienda')).toBeEnabled();

    guasto = false;
    await user.click(within(erroreElenco as HTMLElement).getByRole('button', { name: 'Riprova' }));
    await waitFor(() =>
      expect(
        within(dialogo).queryByText('Impossibile leggere l’elenco delle aziende'),
      ).not.toBeInTheDocument(),
    );

    guasto = true;
    await scegliAnagrafica(user, dialogo, PERSONA, 'Mario Rossi');
    await scegliAnagrafica(user, dialogo, AZIENDA, 'Alfa Software S.r.l.');
    const erroreRapporto = (
      await within(dialogo).findByText(/^Impossibile leggere i dati del rapporto/)
    ).closest('[role="alert"]');
    expect(erroreRapporto).toHaveTextContent('Servizio non disponibile');
    // i dati in archivio ci sono comunque
    expect(campo(dialogo, 'datoreLavoro.ragioneSociale')).toHaveValue('Alfa Software S.r.l.');
    expect(campo(dialogo, 'datiContrattuali.dataAssunzione')).toHaveValue('');

    guasto = false;
    await user.click(within(erroreRapporto as HTMLElement).getByRole('button', { name: 'Riprova' }));

    await waitFor(() => expect(campo(dialogo, 'datiContrattuali.dataAssunzione')).toHaveValue('2022-03-01'));
    expect(within(dialogo).queryByText(/^Impossibile leggere i dati del rapporto/)).not.toBeInTheDocument();
  });

  it('un errore del server sulla persona scelta si mostra sulla tendina', async () => {
    const { user } = renderConMock(<App />, {
      datiIniziali: 'errori',
      avvolgiFetch: intercetta(/\/cedolino\/verifica$/, () =>
        problema(400, {
          title: 'Dati non validi',
          errori: [
            {
              campo: 'personaId',
              messaggio: "La persona scelta non è più in archivio: scegline un'altra o creane una nuova",
            },
          ],
        }),
      ),
    });
    const dialogo = await apriInserimento(user, 'ristampa.pdf');
    await compilaTotali(user, dialogo);

    await user.click(pulsante(dialogo, 'Verifica'));

    expect(await within(dialogo).findByText('1 campo da correggere')).toBeInTheDocument();
    await waitFor(() => expect(tendina(dialogo, PERSONA)).toHaveFocus());
    expect(tendina(dialogo, PERSONA)).toHaveAttribute('aria-invalid', 'true');
    expect(
      within(dialogo).getAllByText(
        "La persona scelta non è più in archivio: scegline un'altra o creane una nuova",
      ).length,
    ).toBeGreaterThan(0);
  });

  it('segnala i campi obbligatori con il riepilogo e porta il focus sul primo errore', async () => {
    const { user } = renderConMock(<App />, { datiIniziali: 'errori' });
    const dialogo = await apriInserimento(user, 'busta-scansione.pdf');
    expect(pulsante(dialogo, 'Salva cedolino')).toBeDisabled();

    await user.click(pulsante(dialogo, 'Verifica'));

    const riepilogo = (await within(dialogo).findByText('8 campi da correggere')).closest<HTMLElement>(
      '[role="alert"]',
    );
    expect(riepilogo).not.toBeNull();
    await waitFor(() => expect(campo(dialogo, 'periodo.tipo')).toHaveFocus());
    // persona e azienda: un solo errore sulla tendina, non sui dati anagrafici (non ancora mostrati)
    expect(tendina(dialogo, PERSONA)).toHaveAttribute('aria-invalid', 'true');
    expect(within(dialogo).getAllByText("Scegli la persona dall'elenco o «Crea nuovo»")).toHaveLength(1);

    await user.click(
      within(riepilogo!).getByRole('button', {
        name: "Azienda in archivio: Scegli l'azienda dall'elenco o «Crea nuova»",
      }),
    );
    expect(tendina(dialogo, AZIENDA)).toHaveFocus();

    // scegliendo l'errore sparisce anche dal riepilogo
    await scegliAnagrafica(user, dialogo, AZIENDA, 'Alfa Software S.r.l.');
    expect(within(dialogo).getByText('7 campi da correggere')).toBeInTheDocument();
    expect(tendina(dialogo, AZIENDA)).not.toHaveAttribute('aria-invalid', 'true');
    expect(pulsante(dialogo, 'Salva cedolino')).toBeDisabled();
  });

  it('verifica e salva il cedolino di persona e azienda nuove; salvare richiede una verifica dei dati attuali', async () => {
    const { user } = renderConMock(<App />, { datiIniziali: 'errori' });
    const dialogo = await apriInserimento(user, 'busta-scansione.pdf');
    await compilaObbligatori(user, dialogo);
    expect(campo(dialogo, 'dipendente.codiceFiscale')).toHaveValue(CF_NUOVO);

    await user.click(pulsante(dialogo, 'Verifica'));
    const esito = await within(dialogo).findByRole('region', { name: 'Esito della verifica' });
    expect(esito).toHaveTextContent('Sarà registrato come: Maggio 2025');
    // tre righe arancioni: persona, azienda e rapporto nuovi (gli avvisi non le ripetono)
    for (const testo of [
      'Verrà creata una nuova persona: controlla il codice fiscale',
      'Verrà creata una nuova azienda: controlla ragione sociale, partita IVA e codice fiscale',
      'Nuovo rapporto di lavoro',
    ]) {
      expect(within(esito).getByText(testo)).toBeVisible();
      expect(within(esito).getByText(testo)).toHaveStyle({ color: 'var(--mantine-color-orange-9)' });
    }
    expect(within(esito).queryByText(/già presente/)).not.toBeInTheDocument();
    expect(pulsante(dialogo, 'Salva cedolino')).toBeEnabled();
    // l'esito compare in fondo al modulo: i lettori di schermo lo sentono annunciare
    expect(annuncio(dialogo)).toHaveAttribute('aria-live', 'polite');
    expect(annuncio(dialogo)).toHaveTextContent(
      /^Verifica completata: il cedolino si può salvare \(Maggio 2025\)\. Verrà creata una nuova persona: controlla il codice fiscale\. Verrà creata una nuova azienda/,
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
    expect(within(esito).getByText('Azienda: già presente')).toBeInTheDocument();
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
    // tendina e «Crea nuovo» su una riga che va a capo invece di uscire dallo schermo
    const riga = pulsante(dialogo, 'Crea nuovo dipendente').parentElement;
    expect(riga).toContainElement(tendina(dialogo, PERSONA));
    expect(riga).toHaveStyle({ '--group-wrap': 'wrap' });
    await user.click(pulsante(dialogo, 'Crea nuovo dipendente'));
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
    await scrivi(user, dialogo, 'dipendente.codiceDipendente', 'M-17');

    await user.click(pulsante(dialogo, 'Chiudi la finestra'));
    let conferma = await screen.findByRole('dialog', { name: 'Chiudere senza salvare?' });
    await user.click(within(conferma).getByRole('button', { name: 'Continua l’inserimento' }));
    await waitFor(() => expect(conferma).not.toBeInTheDocument());
    expect(campo(dialogo, 'dipendente.codiceDipendente')).toHaveValue('M-17');

    await user.click(pulsante(dialogo, 'Chiudi la finestra'));
    conferma = await screen.findByRole('dialog', { name: 'Chiudere senza salvare?' });
    await user.click(within(conferma).getByRole('button', { name: 'Chiudi senza salvare' }));
    await waitFor(() => expect(dialogo).not.toBeInTheDocument());
    // il file resta da completare
    expect(rigaInErrore(await areaInErrore(), 'busta-scansione.pdf')).toBeInTheDocument();
  });
});
