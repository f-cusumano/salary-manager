import { NumberInput, type NumberInputProps } from '@mantine/core';
import { useUncontrolled } from '@mantine/hooks';
import { useState, type ClipboardEvent } from 'react';
import { testi } from '../i18n';

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
  /** Cifre decimali ammesse: 2 per importi e divisori, 5 per basi, paghe, quantita' delle voci e ratei. */
  decimali?: number;
  /** Ammette valori negativi (arrotondamento, netto, ratei). */
  negativi?: boolean;
  /**
   * Importo senza segno per definizione (importi delle voci): il segno di un numero incollato si ignora, come il meno
   * in coda con cui i cedolini stampano le trattenute. Negli altri campi senza negativi un numero negativo incollato
   * viene rifiutato con un messaggio, mai cambiato di segno.
   */
  senzaSegno?: boolean;
  /** Numero intero senza separatore delle migliaia (anni, numero di scatti). */
  intero?: boolean;
}

const STILE_CIFRE = { input: { textAlign: 'right', fontVariantNumeric: 'tabular-nums' } } as const;

/**
 * Interpreta un numero incollato da un PDF o da un foglio di calcolo: "1.234,56", "1234.56", "1,234.56", "123,45-"
 * (segno in coda dei cedolini), con eventuali spazi e simbolo dell'euro. Un solo punto seguito da tre cifre e' il
 * separatore delle migliaia, come nei cedolini italiani. Restituisce il numero con il suo segno, null se il testo non
 * e' un numero.
 */
function numeroIncollato(testo: string, decimali: number): number | null {
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
  return negativo && arrotondato !== 0 ? -arrotondato : arrotondato;
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
  senzaSegno = false,
  intero = false,
  onPaste,
  error,
  ...altre
}: Props) {
  const [valore, setValore] = useUncontrolled<number | string>({
    value,
    defaultValue,
    finalValue: '',
    ...(onChange ? { onChange } : {}),
  });
  // numero negativo incollato in un campo che non lo ammette: il valore non cambia e il campo lo segnala
  const [negativoRifiutato, setNegativoRifiutato] = useState(false);

  const aggiorna = (nuovo: number | string) => {
    setNegativoRifiutato(false);
    setValore(nuovo);
  };

  const incolla = (evento: ClipboardEvent<HTMLInputElement>) => {
    onPaste?.(evento);
    const numero = numeroIncollato(evento.clipboardData.getData('text/plain'), intero ? 0 : decimali);
    if (numero === null) {
      return;
    }
    evento.preventDefault();
    if (numero < 0 && !negativi) {
      if (senzaSegno) {
        aggiorna(-numero);
      } else {
        setNegativoRifiutato(true);
      }
      return;
    }
    aggiorna(numero);
  };

  return (
    <NumberInput
      {...altre}
      error={negativoRifiutato ? testi.comune.negativoNonAmmesso : error}
      value={valore}
      onChange={aggiorna}
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
