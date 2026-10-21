import { testi } from '../i18n';

const spiegazioni: Readonly<Record<string, string>> = testi.aiuti.campi;

/** Spiegazione di un campo del cedolino per percorso (anche di una riga: "voci.2.base"); null se non prevista. */
export function aiutoDi(percorso: string): string | null {
  return spiegazioni[percorso.replace(/\.\d+(?=\.|$)/g, '')] ?? null;
}
