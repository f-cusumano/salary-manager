import { LOCALE, testi } from '../i18n';
import type { Periodo, UnitaMisura } from '../api/types';

/** Formattazione italiana di importi, quantità, mesi e date (il backend invia solo valori grezzi). */

const VUOTO = testi.comune.nonDisponibile;

const formatoEuro = new Intl.NumberFormat(LOCALE, {
  style: 'currency',
  currency: 'EUR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
  // "always": in it-IT il separatore delle migliaia altrimenti manca per i numeri a 4 cifre (1234,56)
  useGrouping: 'always',
});

const formatiNumero = new Map<number, Intl.NumberFormat>();

function formatoNumero(decimali: number): Intl.NumberFormat {
  let formato = formatiNumero.get(decimali);
  if (!formato) {
    formato = new Intl.NumberFormat(LOCALE, { maximumFractionDigits: decimali, useGrouping: 'always' });
    formatiNumero.set(decimali, formato);
  }
  return formato;
}

const formatoPercentuale = new Intl.NumberFormat(LOCALE, { style: 'percent', maximumFractionDigits: 2 });

const PATTERN_MESE = /^(\d{4})-(\d{2})$/;
const PATTERN_DATA = /^(\d{4})-(\d{2})-(\d{2})/;

function nonValorizzato(valore: number | null | undefined): valore is null | undefined {
  return valore === null || valore === undefined || Number.isNaN(valore);
}

/** "1.234,56 €" (con spazio non separabile prima del simbolo). */
export function formatImporto(valore: number | null | undefined): string {
  return nonValorizzato(valore) ? VUOTO : formatoEuro.format(valore);
}

/** Numero decimale all'italiana: "1.234,5". */
export function formatNumero(valore: number | null | undefined, decimali = 2): string {
  return nonValorizzato(valore) ? VUOTO : formatoNumero(decimali).format(valore);
}

/** Frazione 0..1 come percentuale: 0.23 => "23%". */
export function formatPercentuale(valore: number | null | undefined): string {
  return nonValorizzato(valore) ? VUOTO : formatoPercentuale.format(valore);
}

/** Quantità con la sua unità: "12,5 giorni", "1 ora". */
export function formatQuantita(valore: number | null | undefined, unita: UnitaMisura): string {
  if (nonValorizzato(valore)) {
    return VUOTO;
  }
  const singolare = Math.abs(valore) === 1;
  const etichetta =
    unita === 'GIORNI'
      ? singolare
        ? testi.unita.giorno
        : testi.unita.giorni
      : singolare
        ? testi.unita.ora
        : testi.unita.ore;
  return `${formatNumero(valore)} ${etichetta}`;
}

/** Quantità compatta per i badge: "12,5 g", "8 h". */
export function formatQuantitaBreve(valore: number, unita: UnitaMisura): string {
  return `${formatNumero(valore)} ${unita === 'GIORNI' ? testi.unita.giornoBreve : testi.unita.oraBreve}`;
}

function nomeMese(mese: number): string | undefined {
  return testi.mesi[mese - 1];
}

/** "2024-01" => "Gennaio 2024". */
export function formatMese(valore: string | null | undefined): string {
  if (!valore) {
    return VUOTO;
  }
  const match = PATTERN_MESE.exec(valore);
  const nome = match ? nomeMese(Number(match[2])) : undefined;
  return match && nome ? `${nome} ${match[1]}` : valore;
}

/** "2024-01-15" => "15 Gennaio 2024". */
export function formatData(valore: string | null | undefined): string {
  if (!valore) {
    return VUOTO;
  }
  const match = PATTERN_DATA.exec(valore);
  const nome = match ? nomeMese(Number(match[2])) : undefined;
  return match && nome ? `${Number(match[3])} ${nome} ${match[1]}` : valore;
}

/** "2027-03-01" => "Marzo 2027". */
export function formatMeseDiData(valore: string | null | undefined): string {
  if (!valore) {
    return VUOTO;
  }
  const match = PATTERN_DATA.exec(valore);
  return match ? formatMese(`${match[1]}-${match[2]}`) : valore;
}

/** Istante ISO in ora locale: "7 Ottobre 2026, 14:05". */
export function formatIstante(valore: string | null | undefined): string {
  if (!valore) {
    return VUOTO;
  }
  const data = new Date(valore);
  if (Number.isNaN(data.getTime())) {
    return valore;
  }
  const ore = String(data.getHours()).padStart(2, '0');
  const minuti = String(data.getMinutes()).padStart(2, '0');
  return `${data.getDate()} ${nomeMese(data.getMonth() + 1) ?? ''} ${data.getFullYear()}, ${ore}:${minuti}`;
}

/**
 * Periodo del rapporto senza parentesi: "da Gennaio 2024 a Febbraio 2026" oppure "da Gennaio 2024" se in corso.
 * Restituisce null se il periodo non è noto.
 */
export function formatPeriodo(periodo: Pick<Periodo, 'da' | 'a'>): string | null {
  const { da, a } = periodo;
  if (da && a) {
    return `${testi.periodo.da} ${formatMese(da)} ${testi.periodo.a} ${formatMese(a)}`;
  }
  if (da) {
    return `${testi.periodo.da} ${formatMese(da)}`;
  }
  if (a) {
    return `${testi.periodo.fino} ${formatMese(a)}`;
  }
  return null;
}

/** Etichetta della riga azienda: "Alfa Software S.r.l. (da Gennaio 2024 a Febbraio 2026)". */
export function formatEtichettaRapporto(etichetta: string, periodo: Pick<Periodo, 'da' | 'a'>): string {
  const testo = formatPeriodo(periodo);
  return testo ? `${etichetta} (${testo})` : etichetta;
}

/** "2 anni e 2 mesi", "2 anni", "1 anno e 1 mese", "5 mesi". */
export function formatAnzianita(anni: number, mesi: number): string {
  const parti: string[] = [];
  if (anni > 0) {
    parti.push(`${anni} ${anni === 1 ? testi.durata.anno : testi.durata.anni}`);
  }
  if (mesi > 0) {
    parti.push(`${mesi} ${mesi === 1 ? testi.durata.mese : testi.durata.mesi}`);
  }
  return parti.length > 0 ? parti.join(` ${testi.durata.congiunzione} `) : testi.durata.menoDiUnMese;
}

/** Dimensione di un file: "850 KB", "1,2 MB". */
export function formatDimensione(byte: number): string {
  if (byte < 1024) {
    return `${byte} B`;
  }
  if (byte < 1024 * 1024) {
    return `${formatNumero(byte / 1024, 0)} KB`;
  }
  return `${formatNumero(byte / (1024 * 1024), 1)} MB`;
}

const campiCedolino: Readonly<Record<string, string>> = testi.campoCedolino;

/**
 * Nome leggibile di un campo del cedolino indicato dal suo percorso (es. "datiContrattuali.codiceCnel").
 * Per un percorso sconosciuto usa l'ultimo segmento, separando le parole in camelCase.
 */
export function etichettaCampoCedolino(percorso: string): string {
  const noto = campiCedolino[percorso];
  if (noto) {
    return noto;
  }
  const ultimo = percorso.split('.').pop() ?? percorso;
  return ultimo
    .replace(/\[\d*\]/g, '')
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .toLowerCase()
    .trim();
}
