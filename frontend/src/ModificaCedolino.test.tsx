import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { App } from './App';
import { selezionaPersona, type Utente } from './test/app';
import { renderConMock } from './test/render';

// Dati dimostrativi INVENTATI del backend simulato: Mario Rossi presso Alfa Software (rapporto 1), con il cedolino di
// Settembre 2026 tra quelli presenti.

function barra(titolo: string, azione: 'Mostra' | 'Nascondi' = 'Mostra'): RegExp {
  return new RegExp(`^${azione} il dettaglio di ${titolo}(?![\\d])`);
}

function campo(dialogo: HTMLElement, percorso: string): HTMLInputElement {
  const elemento = dialogo.querySelector<HTMLInputElement>(`[data-path="${percorso}"]`);
  if (!elemento) {
    throw new Error(`campo ${percorso} non trovato`);
  }
  return elemento;
}

async function sostituisci(
  user: Utente,
  dialogo: HTMLElement,
  percorso: string,
  testo: string,
): Promise<void> {
  await user.clear(campo(dialogo, percorso));
  await user.type(campo(dialogo, percorso), testo);
}

async function apriModifica(user: Utente, titolo: string): Promise<HTMLElement> {
  await selezionaPersona(user, 'Mario Rossi');
  await user.click(await screen.findByRole('button', { name: barra(titolo) }));
  await user.click(await screen.findByRole('button', { name: 'Modifica' }));
  const dialogo = await screen.findByRole('dialog', { name: `Modifica dati · ${titolo}` });
  await within(dialogo).findByRole('form', { name: 'Dati del cedolino' });
  return dialogo;
}

describe('modifica dei dati di un cedolino', { timeout: 40_000 }, () => {
  it('apre il modulo precompilato accanto al PDF e sostituisce il cedolino con i nuovi dati', async () => {
    const { user, backend } = renderConMock(<App />);
    const originale = [...backend.db.cedolini.values()].find(
      (c) => c.rapportoId === 1 && c.titolo === 'Settembre 2026',
    );
    if (!originale) {
      throw new Error('cedolino di Settembre 2026 non trovato');
    }
    const dialogo = await apriModifica(user, 'Settembre 2026');

    expect(await within(dialogo).findByTitle('PDF del cedolino')).toBeInTheDocument();
    expect(campo(dialogo, 'periodo.mese')).toHaveValue('Settembre');
    expect(campo(dialogo, 'periodo.anno')).toHaveValue('2026');
    expect(within(dialogo).getByRole('combobox', { name: 'Persona in archivio' })).toHaveValue('Mario Rossi');
    expect(within(dialogo).getByRole('combobox', { name: 'Azienda in archivio' })).toHaveValue(
      'Alfa Software S.r.l.',
    );
    expect(campo(dialogo, 'totali.netto')).not.toHaveValue('');
    expect(campo(dialogo, 'voci.0.descrizione')).not.toHaveValue('');

    await sostituisci(user, dialogo, 'totali.totaleCompetenze', '3000');
    await sostituisci(user, dialogo, 'totali.totaleTrattenute', '800');
    await sostituisci(user, dialogo, 'totali.netto', '2200');
    await user.click(within(dialogo).getByRole('button', { name: 'Verifica' }));
    const esito = await within(dialogo).findByRole('region', { name: 'Esito della verifica' });
    expect(within(esito).getByText('Settembre 2026')).toBeInTheDocument();
    await user.click(within(dialogo).getByRole('button', { name: 'Salva cedolino' }));

    expect(await screen.findByText('Cedolino modificato: Settembre 2026')).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: /Modifica dati/ })).not.toBeInTheDocument(),
    );
    expect(backend.db.cedolini.has(originale.id)).toBe(false);
    const nuovo = [...backend.db.cedolini.values()].find(
      (c) => c.rapportoId === 1 && c.titolo === 'Settembre 2026',
    );
    expect(nuovo).toMatchObject({ modificatoAMano: true, totali: { netto: 2200, totaleCompetenze: 3000 } });
    // la barra del cedolino che ha sostituito quello modificato resta aperta
    expect(await screen.findByText('Dati modificati a mano')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: barra('Settembre 2026', 'Nascondi') })).toBeInTheDocument();
  });

  it('un periodo già presente con importi diversi non si può salvare e non propone di rimuovere il file', async () => {
    const { user } = renderConMock(<App />);
    const dialogo = await apriModifica(user, 'Settembre 2026');

    await user.click(campo(dialogo, 'periodo.mese'));
    await user.click(await within(dialogo).findByRole('option', { name: 'Agosto' }));
    await sostituisci(user, dialogo, 'totali.netto', '1');
    await user.click(within(dialogo).getByRole('button', { name: 'Verifica' }));

    const esito = await within(dialogo).findByRole('region', { name: 'Esito della verifica' });
    expect(within(esito).getByText('Il cedolino non può essere salvato')).toBeInTheDocument();
    expect(within(esito).queryByRole('button', { name: 'Rimuovi questo file' })).not.toBeInTheDocument();
    expect(within(dialogo).getByRole('button', { name: 'Salva cedolino' })).toBeDisabled();
  });
});

describe('spiegazioni dei campi', { timeout: 40_000 }, () => {
  it('nel modulo: l’icona accanto all’etichetta apre la spiegazione senza cambiare il nome del campo', async () => {
    const { user } = renderConMock(<App />);
    const dialogo = await apriModifica(user, 'Settembre 2026');

    expect(within(dialogo).getByRole('textbox', { name: 'Totale competenze' })).toBe(
      campo(dialogo, 'totali.totaleCompetenze'),
    );
    await user.click(within(dialogo).getByRole('button', { name: 'Spiegazione: Totale competenze' }));
    expect(await screen.findByText(/somma la colonna delle competenze di tutte le voci/)).toBeVisible();
    expect(campo(dialogo, 'totali.totaleCompetenze')).not.toHaveFocus();

    await user.click(within(dialogo).getByRole('button', { name: 'Spiegazione: Lordo' }));
    expect(await screen.findByText(/Per lordo si intende la retribuzione lorda del mese/)).toBeVisible();
    // guida della sezione delle voci, con le categorie
    expect(within(dialogo).getByText('Come scegliere la categoria')).toBeInTheDocument();
  });

  it('nel dettaglio del cedolino: le stesse spiegazioni accanto ai dati e ai gruppi di voci', async () => {
    const { user } = renderConMock(<App />);
    await selezionaPersona(user, 'Mario Rossi');
    await user.click(await screen.findByRole('button', { name: barra('Settembre 2026') }));

    // il dettaglio si legge all'apertura della barra
    await user.click(
      await screen.findByRole('button', { name: 'Spiegazione: Netto in busta' }, { timeout: 5000 }),
    );
    expect(await screen.findByText(/Il netto in busta del mese/)).toBeVisible();
    await user.click(screen.getByRole('button', { name: /^Spiegazione: Competenze/ }));
    expect(await screen.findByText(/Retribuzione, straordinari, festività/)).toBeVisible();
  });
});
