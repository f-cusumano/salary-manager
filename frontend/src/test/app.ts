import { screen, within } from '@testing-library/react';
import type { renderConMock } from './render';

/** Interazioni ricorrenti nei test dell'applicazione completa (App) sul backend simulato. */

export type Utente = ReturnType<typeof renderConMock>['user'];

export const TITOLO_AREA_IN_ERRORE = 'Cedolini caricati con errore';

/** Sceglie la persona dalla tendina della barra superiore. */
export async function selezionaPersona(user: Utente, nome: string): Promise<void> {
  const barra = screen.getByRole('banner');
  await user.click(await within(barra).findByRole('combobox', { name: 'Persona' }));
  await user.click(await within(barra).findByRole('option', { name: nome }));
}

export function inputFile(modale: HTMLElement): HTMLInputElement {
  const input = modale.querySelector<HTMLInputElement>('input[type="file"]');
  if (!input) {
    throw new Error('input file della dropzone non trovato');
  }
  return input;
}

/** PDF finto con il nome indicato: l'esito del caricamento simulato dipende dal nome (vedi frontend/README.md). */
export function pdf(nome: string): File {
  return new File(['%PDF-1.4 finto'], nome, { type: 'application/pdf' });
}

/** Area "Cedolini caricati con errore" (attende che l'elenco sia caricato). */
export function areaInErrore(): Promise<HTMLElement> {
  return screen.findByRole('region', { name: TITOLO_AREA_IN_ERRORE });
}

/** Riga di un file nell'area dei cedolini caricati con errore. */
export function rigaInErrore(area: HTMLElement, nomeFile: string): HTMLElement {
  return within(area).getByRole('listitem', { name: `${nomeFile}: Caricato con errore` });
}

/** Apre il menu "Altre azioni per …" dentro un contenitore e sceglie una voce. */
export async function azioneDalMenu(
  user: Utente,
  contenitore: HTMLElement,
  oggetto: string,
  voce: string,
): Promise<void> {
  await user.click(within(contenitore).getByRole('button', { name: `Altre azioni per ${oggetto}` }));
  await user.click(await screen.findByRole('menuitem', { name: voce }));
}
