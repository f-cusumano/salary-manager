import { NumberInput, type NumberInputProps } from '@mantine/core';
import { useUncontrolled } from '@mantine/hooks';
import type { ClipboardEvent } from 'react';

type PropsNumberInput = Omit<
  NumberInputProps,
  | 'value'
  | 'defaultValue'
  | 'onChange'
  | 'decimalSeparator'
  | 'thousandSeparator'
  | 'allowedDecimalSeparators'
  | 'decimalScale'
  | 'allowDecimal'
  | 'allowNegative'
>;

interface Props extends PropsNumberInput {
  value?: number | string;
  defaultValue?: number | string;
  /** Numero, '' se vuoto, oppure il testo intermedio della digitazione (es. "12.") */
  onChange?: (valore: number | string) => void;
  /** Cifre decimali ammesse: 2 per importi, quantita' e divisori, 5 per basi e paghe orarie/giornaliere. */
  decimali?: number;
  /** Ammette valori negativi (arrotondamento, netto, saldi dei ratei). */
  negativi?: boolean;
  /** Numero intero senza separatore delle migliaia (anni, numero di scatti). */
  intero?: boolean;
}

const STILE_CIFRE = { input: { textAlign: 'right', fontVariantNumeric: 'tabular-nums' } } as const;

/**
 * Interpreta un numero incollato da un PDF o da un foglio di calcolo: "1.234,56", "1234.56", "1,234.56", "123,45-"
 * (segno in coda dei cedolini), con eventuali spazi e simbolo dell'euro. Un solo punto seguito da tre cifre e' il
 * separatore delle migliaia, come nei cedolini italiani. Restituisce null se il testo non e' un numero.
 */
function numeroIncollato(testo: string, decimali: number, negativi: boolean): number | null {
  let pulito = testo.replace(/[\s€]/g, '');
  let negativo = false;
  if (pulito.endsWith('-')) {
    negativo = true;
    pulito = pulito.slice(0, -1);
  }
  if (pulito.startsWith('-')) {
    negativo = true;
    pulito = pulito.slice(1);
  } else if (pulito.startsWith('+')) {
    pulito = pulito.slice(1);
  }
  if (!/^\d[\d.,]*$/.test(pulito) || !/\d$/.test(pulito)) {
    return null;
  }
  const ultimaVirgola = pulito.lastIndexOf(',');
  const ultimoPunto = pulito.lastIndexOf('.');
  const virgole = pulito.split(',').length - 1;
  const punti = pulito.split('.').length - 1;
  let separatoreDecimale: ',' | '.' | null = null;
  if (virgole > 0 && punti > 0) {
    separatoreDecimale = ultimaVirgola > ultimoPunto ? ',' : '.';
  } else if (virgole === 1) {
    separatoreDecimale = ',';
  } else if (punti === 1) {
    const cifreDopo = pulito.length - ultimoPunto - 1;
    separatoreDecimale = cifreDopo === 3 && !pulito.startsWith('0.') ? null : '.';
  }
  let parteIntera = pulito;
  let parteDecimale = '';
  if (separatoreDecimale !== null) {
    const posizione = pulito.lastIndexOf(separatoreDecimale);
    parteIntera = pulito.slice(0, posizione);
    parteDecimale = pulito.slice(posizione + 1);
    if (parteDecimale.includes(',') || parteDecimale.includes('.')) {
      return null;
    }
  }
  const numero = Number(`${parteIntera.replace(/[.,]/g, '')}.${parteDecimale || '0'}`);
  if (!Number.isFinite(numero)) {
    return null;
  }
  const fattore = 10 ** decimali;
  const arrotondato = Math.round(numero * fattore) / fattore;
  return negativo && negativi && arrotondato !== 0 ? -arrotondato : arrotondato;
}

/**
 * Campo numerico all'italiana: virgola decimale e punto delle migliaia ("1.234,56"). Il punto digitato non diventa
 * mai la virgola (con i separatori predefiniti di Mantine "1.234,56" varrebbe 1,23); i numeri incollati vengono
 * interpretati nei formati piu' comuni. Cifre allineate a destra, senza frecce di incremento.
 */
export function CampoNumero({
  value,
  defaultValue,
  onChange,
  decimali = 2,
  negativi = false,
  intero = false,
  onPaste,
  ...altre
}: Props) {
  const [valore, setValore] = useUncontrolled<number | string>({
    value,
    defaultValue,
    finalValue: '',
    ...(onChange ? { onChange } : {}),
  });

  const incolla = (evento: ClipboardEvent<HTMLInputElement>) => {
    onPaste?.(evento);
    const numero = numeroIncollato(
      evento.clipboardData.getData('text/plain'),
      intero ? 0 : decimali,
      negativi,
    );
    if (numero !== null) {
      evento.preventDefault();
      setValore(numero);
    }
  };

  return (
    <NumberInput
      {...altre}
      value={valore}
      onChange={setValore}
      onPaste={incolla}
      decimalSeparator=","
      thousandSeparator={intero ? undefined : '.'}
      allowedDecimalSeparators={[',']}
      decimalScale={intero ? 0 : decimali}
      allowDecimal={!intero}
      allowNegative={negativi}
      hideControls
      inputMode={intero ? 'numeric' : 'decimal'}
      styles={STILE_CIFRE}
    />
  );
}
