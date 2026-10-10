import { screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { renderConMock } from '../test/render';
import { CampoNumero } from './CampoNumero';

function renderCampo(props: Partial<Parameters<typeof CampoNumero>[0]> = {}) {
  const onChange = vi.fn<(valore: number | string) => void>();
  const risultato = renderConMock(<CampoNumero label="Importo" onChange={onChange} {...props} />);
  const input = screen.getByLabelText<HTMLInputElement>('Importo');
  return { ...risultato, input, onChange, ultimo: () => onChange.mock.calls.at(-1)?.[0] };
}

describe('campo numerico all’italiana', () => {
  it('con la virgola decimale mostra il punto delle migliaia e restituisce il numero', async () => {
    const { user, input, ultimo } = renderCampo();
    await user.type(input, '1234,56');
    expect(input).toHaveValue('1.234,56');
    expect(ultimo()).toBe(1234.56);
  });

  it('il punto digitato non diventa una virgola decimale', async () => {
    const { user, input, ultimo } = renderCampo();
    await user.type(input, '1.234,56');
    expect(input).toHaveValue('1.234,56');
    expect(ultimo()).toBe(1234.56);
  });

  it('limita i decimali: 2 per gli importi, 5 per basi e paghe', async () => {
    const importo = renderCampo();
    await importo.user.type(importo.input, '10,1299');
    expect(importo.input).toHaveValue('10,12');
    importo.unmount();
    const base = renderCampo({ decimali: 5 });
    await base.user.type(base.input, '15,123456');
    expect(base.input).toHaveValue('15,12345');
    expect(base.ultimo()).toBe(15.12345);
  });

  it('la variante intera non ha separatore delle migliaia né decimali', async () => {
    const { user, input, ultimo } = renderCampo({ intero: true });
    await user.type(input, '2024,5');
    expect(input).toHaveValue('20245');
    await user.clear(input);
    await user.type(input, '2024');
    expect(input).toHaveValue('2024');
    expect(ultimo()).toBe(2024);
  });

  it('rifiuta il segno meno se non ammesso', async () => {
    const senza = renderCampo();
    await senza.user.type(senza.input, '-5');
    expect(senza.input).toHaveValue('5');
    expect(senza.ultimo()).toBe(5);
    senza.unmount();
    const con = renderCampo({ negativi: true });
    await con.user.type(con.input, '-5,5');
    expect(con.input).toHaveValue('-5,5');
    expect(con.ultimo()).toBe(-5.5);
  });

  it.each<[string, number]>([
    ['1.234,56', 1234.56],
    ['1234.56', 1234.56],
    ['1,234.56', 1234.56],
    ['€ 2.100,00', 2100],
    ['1.234', 1234],
    ['0.5', 0.5],
    ['12,345678', 12.35],
  ])('incollando "%s" ottiene %d', async (testo, atteso) => {
    const { user, input, ultimo } = renderCampo();
    await user.click(input);
    await user.paste(testo);
    expect(ultimo()).toBe(atteso);
  });

  it('il segno in coda dei cedolini ("123,45-") vale dove i negativi sono ammessi', async () => {
    const con = renderCampo({ negativi: true });
    await con.user.click(con.input);
    await con.user.paste('123,45-');
    expect(con.ultimo()).toBe(-123.45);
    expect(con.input).toHaveValue('-123,45');
  });

  it('negli importi senza segno (voci) il segno incollato si ignora', async () => {
    const { user, input, ultimo } = renderCampo({ senzaSegno: true });
    await user.click(input);
    await user.paste('123,45-');
    expect(ultimo()).toBe(123.45);
    expect(input).toHaveValue('123,45');
    expect(screen.queryByText('Il valore non può essere negativo')).not.toBeInTheDocument();
  });

  it('negli altri campi un numero negativo incollato è rifiutato e segnalato, mai cambiato di segno', async () => {
    const { user, input, onChange } = renderCampo({ defaultValue: 12 });
    await user.click(input);
    await user.paste('-8,50');
    expect(onChange).not.toHaveBeenCalled();
    expect(input).toHaveValue('12');
    expect(screen.getByText('Il valore non può essere negativo')).toBeInTheDocument();
    expect(input).toHaveAttribute('aria-invalid', 'true');
    // il messaggio sparisce con il valore successivo
    await user.paste('8,50');
    expect(onChange).toHaveBeenLastCalledWith(8.5);
    expect(screen.queryByText('Il valore non può essere negativo')).not.toBeInTheDocument();
  });

  it('un testo che non è un numero incollato resta alle regole del campo', async () => {
    const { user, input, onChange } = renderCampo();
    await user.click(input);
    await user.paste('abc');
    expect(input).toHaveValue('');
    expect(onChange).not.toHaveBeenCalledWith(expect.any(Number));
  });
});
