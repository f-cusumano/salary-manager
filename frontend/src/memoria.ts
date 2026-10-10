/**
 * Preferenze locali del browser (solo comodità: la persona selezionata). L'accesso a localStorage puo' fallire
 * (navigazione privata, dati bloccati): in quel caso l'app funziona senza ricordare nulla.
 */
const CHIAVE_PERSONA = 'salary-manager.persona';

export function leggiPersonaRicordata(): number | null {
  try {
    const valore = window.localStorage.getItem(CHIAVE_PERSONA);
    const id = valore ? Number(valore) : NaN;
    return Number.isInteger(id) && id > 0 ? id : null;
  } catch {
    return null;
  }
}

export function ricordaPersona(id: number | null): void {
  try {
    if (id === null) {
      window.localStorage.removeItem(CHIAVE_PERSONA);
    } else {
      window.localStorage.setItem(CHIAVE_PERSONA, String(id));
    }
  } catch {
    // preferenza non salvabile: nessun effetto sul funzionamento
  }
}
