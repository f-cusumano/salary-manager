import type { ComboboxData, ComboboxItem, ComboboxParsedItem, OptionsFilter } from '@mantine/core';
import type {
  AttribuzioneCaricamento,
  AziendaSelezionabile,
  CedolinoDettaglio,
  PersonaSintesi,
  RapportoDettaglio,
  RapportoSintesi,
  RigaMensilita,
} from '../api/types';
import { copiaSeVuoti, notaCopia, valoreDi, type FonteCopia } from '../components/moduloCedolino';
import { testi } from '../i18n';
import {
  daAssociazione,
  daCedolinoRiferimento,
  daRapporto,
  periodoDi,
  rateoCopiatoVuoto,
  valoriVuoti,
  voceCopiataVuota,
  type ModoAnagrafica,
  type ValoriCedolinoManuale,
  type ValoriParziali,
} from './useCedolinoManuale';

/**
 * Funzioni pure della scelta di persona e azienda dall'elenco nell'inserimento a mano: dati anagrafici in archivio,
 * opzioni e ricerca delle tendine, precompilazione dei campi che seguono alla scelta.
 */

const t = testi.manuale;

/** Campi compilati in automatico (percorso del modulo => provenienza). */
export type Copiati = Readonly<Record<string, string>>;

export interface ValoriECopiati {
  valori: ValoriCedolinoManuale;
  copiati: Record<string, string>;
}

/** Dati anagrafici della persona in archivio (codice fiscale, nome, cognome, data di nascita); '' se assenti. */
export function identitaPersona(
  persona: Pick<PersonaSintesi, 'codiceFiscale' | 'nome' | 'cognome' | 'dataNascita'> | null,
): ValoriParziali {
  return {
    dipendente: {
      codiceFiscale: persona?.codiceFiscale ?? '',
      cognome: persona?.cognome ?? '',
      nome: persona?.nome ?? '',
      dataNascita: persona?.dataNascita ?? '',
    },
  };
}

/** Dati anagrafici dell'azienda in archivio (ragione sociale, partita IVA, codice fiscale, posizione INPS); '' se assenti. */
export function identitaAzienda(
  azienda: Pick<
    AziendaSelezionabile,
    'ragioneSociale' | 'partitaIva' | 'codiceFiscale' | 'posizioneInps'
  > | null,
): ValoriParziali {
  return {
    datoreLavoro: {
      ragioneSociale: azienda?.ragioneSociale ?? '',
      partitaIva: azienda?.partitaIva ?? '',
      codiceFiscale: azienda?.codiceFiscale ?? '',
      posizioneInps: azienda?.posizioneInps ?? '',
    },
  };
}

/** Attributi dell'azienda in archivio da copiare nei campi vuoti (si possono cambiare): indirizzo e codice azienda. */
export function attributiAzienda(
  azienda: Pick<AziendaSelezionabile, 'indirizzo' | 'codiceAzienda'>,
): ValoriParziali {
  return { datoreLavoro: { indirizzo: azienda.indirizzo ?? '', codiceAzienda: azienda.codiceAzienda ?? '' } };
}

/** Rapporto della coppia persona-azienda tra quelli della persona (il primo, cioe' il piu' recente); null se non c'e'. */
export function rapportoDellaCoppia(
  rapporti: readonly RapportoSintesi[],
  aziendaId: number,
): RapportoSintesi | null {
  return rapporti.find((r) => r.azienda.id === aziendaId) ?? null;
}

/**
 * Opzioni della tendina delle aziende: prima quelle della persona scelta (nell'ordine dei suoi rapporti, il rapporto
 * in corso per primo), poi le altre. Senza persona, o se la persona non ha rapporti tra le aziende elencate, un
 * elenco semplice.
 */
export function opzioniAziende(
  aziende: readonly AziendaSelezionabile[],
  rapportiPersona: readonly RapportoSintesi[] | null,
  etichettaPersona: string | null,
): ComboboxData {
  const opzione = (a: AziendaSelezionabile): ComboboxItem => ({ value: String(a.id), label: a.etichetta });
  const perId = new Map(aziende.map((a) => [a.id, a]));
  const ids = [...new Set((rapportiPersona ?? []).map((r) => r.azienda.id))].filter((id) => perId.has(id));
  if (etichettaPersona === null || ids.length === 0) {
    return aziende.map(opzione);
  }
  const dellaPersona = ids.flatMap((id) => {
    const azienda = perId.get(id);
    return azienda ? [opzione(azienda)] : [];
  });
  const altre = aziende.filter((a) => !ids.includes(a.id)).map(opzione);
  const gruppi = [{ group: t.aziendeDi.replace('{persona}', etichettaPersona), items: dellaPersona }];
  return altre.length > 0 ? [...gruppi, { group: t.altreAziende, items: altre }] : gruppi;
}

function senzaAccenti(testo: string): string {
  return testo.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}

/**
 * Ricerca nelle tendine: tutte le parole cercate (senza distinguere maiuscole e accenti) devono comparire
 * nell'etichetta o nei codici dell'opzione (codice fiscale, partita IVA).
 */
export function corrisponde(cerca: string, ...testi: (string | null | undefined)[]): boolean {
  const parole = senzaAccenti(cerca).split(/\s+/).filter(Boolean);
  const dove = senzaAccenti(testi.filter((x): x is string => typeof x === 'string').join(' '));
  return parole.every((parola) => dove.includes(parola));
}

/** Filtro di una tendina con `corrisponde`, sull'etichetta e sui codici di ogni opzione; i gruppi vuoti spariscono. */
export function filtroAnagrafiche(codici: ReadonlyMap<string, readonly (string | null)[]>): OptionsFilter {
  const filtra = (opzioni: ComboboxParsedItem[], cerca: string): ComboboxParsedItem[] =>
    opzioni.flatMap((opzione): ComboboxParsedItem[] => {
      if ('group' in opzione) {
        const items = filtra(opzione.items, cerca).filter((o): o is ComboboxItem => !('group' in o));
        return items.length > 0 ? [{ group: opzione.group, items }] : [];
      }
      return corrisponde(cerca, opzione.label, ...(codici.get(opzione.value) ?? [])) ? [opzione] : [];
    });
  return ({ options, search }) => filtra(options, search);
}

// ------------------------------------------------------------------ precompilazione alla scelta

/** Fonti dei dati alla scelta di persona e azienda. */
export interface FontiScelta {
  persona: PersonaSintesi | null;
  azienda: AziendaSelezionabile | null;
  /** Rapporto della coppia, solo con persona e azienda scelte e gia' legate da un rapporto. */
  rapporto: RapportoDettaglio | null;
  /** Cedolino del rapporto piu' vicino al periodo indicato, da cui si copiano i dati che si ripetono. */
  riferimento: { riga: RigaMensilita; cedolino: CedolinoDettaglio } | null;
}

/** Quale anagrafica e' cambiata: i suoi dati anagrafici vengono sostituiti. */
export interface CambioScelta {
  persona: boolean;
  azienda: boolean;
}

/** I dati presi dal mese associato al file restano anche cambiando le scelte. */
const MAI_LIBERATI = /^(periodo\.|contieneTredicesima$|contieneQuattordicesima$)/;

function conValore<T>(oggetto: T, percorso: string, valore: unknown): T {
  const [primo = '', ...resto] = percorso.split('.');
  const radice = oggetto as Record<string, unknown>;
  return {
    ...radice,
    [primo]: resto.length > 0 ? conValore(radice[primo], resto.join('.'), valore) : valore,
  } as T;
}

/** Sovrascrive (anche se compilati) i campi indicati nei dati parziali. */
function sostituisci(valori: ValoriCedolinoManuale, dati: ValoriParziali): ValoriCedolinoManuale {
  let risultato = valori;
  for (const [gruppo, campi] of Object.entries(dati)) {
    for (const [campo, valore] of Object.entries(campi as Record<string, unknown>)) {
      risultato = conValore(risultato, `${gruppo}.${campo}`, valore);
    }
  }
  return risultato;
}

/** Interruttore dei buoni pasto: rivela numero, valore unitario e importo. */
const BUONI_PASTO_PRESENTI = 'buoniPasto.presenti';

/**
 * Toglie i dati compilati in automatico da una scelta precedente: ogni campo copiato torna vuoto, tranne periodo e
 * flag presi dal mese associato al file. Di voci e ratei spariscono solo le righe copiate e mai toccate: quelle
 * dell'utente e le copie modificate restano. I campi modificati dall'utente non sono piu' tra i copiati e restano;
 * l'interruttore dei buoni pasto copiato resta acceso (e diventa dell'utente) se numero, valore unitario o importo
 * hanno ancora un valore, che altrimenti sparirebbe dal modulo e dalla richiesta.
 */
export function liberaCopiati(valori: ValoriCedolinoManuale, copiati: Copiati): ValoriECopiati {
  const vuoti = valoriVuoti();
  let risultato = valori;
  const restanti: Record<string, string> = {};
  for (const [percorso, nota] of Object.entries(copiati)) {
    if (MAI_LIBERATI.test(percorso)) {
      restanti[percorso] = nota;
    } else if (percorso === 'voci') {
      risultato = { ...risultato, voci: risultato.voci.filter((v) => !voceCopiataVuota(v)) };
    } else if (percorso === 'ratei') {
      risultato = { ...risultato, ratei: risultato.ratei.filter((r) => !rateoCopiatoVuoto(r)) };
    } else if (percorso !== BUONI_PASTO_PRESENTI) {
      risultato = conValore(risultato, percorso, valoreDi(vuoti, percorso));
    }
  }
  // dopo gli altri campi: un valore unitario copiato e' gia' stato tolto, contano solo i valori dell'utente
  const { numero, valoreUnitario, importo } = risultato.buoniPasto;
  if (BUONI_PASTO_PRESENTI in copiati && [numero, valoreUnitario, importo].every((v) => v === '')) {
    risultato = conValore(risultato, BUONI_PASTO_PRESENTI, vuoti.buoniPasto.presenti);
  }
  return { valori: risultato, copiati: restanti };
}

/**
 * Compila i soli campi vuoti, nell'ordine (la prima fonte vince): attributi dell'azienda scelta, dati del rapporto,
 * dati che si ripetono dal cedolino di riferimento.
 */
export function riempiVuoti(
  valori: ValoriCedolinoManuale,
  fonti: Pick<FontiScelta, 'azienda' | 'rapporto' | 'riferimento'>,
): ValoriECopiati {
  const elenco: FonteCopia[] = [];
  if (fonti.azienda) {
    elenco.push({ dati: attributiAzienda(fonti.azienda), nota: `${t.dallAziendaScelta}: ${t.daVerificare}` });
  }
  if (fonti.rapporto) {
    elenco.push({ dati: daRapporto(fonti.rapporto), nota: notaCopia(fonti.rapporto.etichetta) });
  }
  if (fonti.riferimento) {
    elenco.push({
      dati: daCedolinoRiferimento(fonti.riferimento.cedolino, periodoDi(valori.periodo)?.anno ?? null),
      nota: notaCopia(fonti.riferimento.riga.titolo),
    });
  }
  return copiaSeVuoti(valori, elenco);
}

/**
 * Alla scelta di un'altra persona o azienda, i suoi dati anagrafici cambiano solo da o verso un'anagrafica in archivio
 * (anche da una all'altra). Tra "Crea nuovo" e "Scegli dall'elenco" (ancora nessuna scelta) restano quelli scritti:
 * chi torna all'elenco per cercare un omonimo e non lo trova li ritrova tornando a "Crea nuovo" (nascosti, intanto non
 * vengono verificati ne' salvati: manca la scelta).
 */
export function cambiaIdentita(prima: ModoAnagrafica, dopo: ModoAnagrafica): boolean {
  return prima === 'scelta' || dopo === 'scelta';
}

/**
 * Riferimento di una precompilazione dal rapporto: il cedolino copiato e il criterio "stesso anno" del periodo (residui
 * e fondo TFR). Se cambiando il periodo il riferimento resta lo stesso, la precompilazione non va rifatta.
 */
export function chiaveRiferimento(
  riferimento: FontiScelta['riferimento'],
  periodo: { anno: number } | null,
): string {
  if (!riferimento) {
    return 'nessuno';
  }
  const stessoAnno = periodo !== null && riferimento.cedolino.competenza.anno === periodo.anno;
  return `${String(riferimento.riga.cedolinoId)}:${String(stessoAnno)}`;
}

/**
 * Applica una scelta di persona e/o azienda (i valori hanno gia' id e flag della nuova scelta): toglie i dati copiati
 * dalla scelta precedente, sostituisce i dati anagrafici dell'anagrafica cambiata con quelli in archivio (vuoti per
 * una nuova) e compila i campi vuoti dalle fonti. I dati anagrafici dell'anagrafica non cambiata restano com'erano.
 */
export function applicaScelta(
  valori: ValoriCedolinoManuale,
  copiati: Copiati,
  cambio: CambioScelta,
  fonti: FontiScelta,
): ValoriECopiati {
  const liberati = liberaCopiati(valori, copiati);
  let risultato = liberati.valori;
  if (cambio.persona) {
    risultato = sostituisci(risultato, identitaPersona(fonti.persona));
  }
  if (cambio.azienda) {
    risultato = sostituisci(risultato, identitaAzienda(fonti.azienda));
  }
  const riempiti = riempiVuoti(risultato, fonti);
  return { valori: riempiti.valori, copiati: { ...liberati.copiati, ...riempiti.copiati } };
}

/** Anagrafiche in archivio lette per preparare il modulo (undefined se la lettura non e' riuscita). */
export interface ElenchiAnagrafiche {
  persone: readonly PersonaSintesi[] | undefined;
  aziende: readonly AziendaSelezionabile[] | undefined;
}

/**
 * Valori iniziali del modulo: periodo dal mese associato al file; persona e azienda del suo rapporto gia' scelte (se
 * negli elenchi) con i dati del rapporto e del cedolino di riferimento; "Crea nuovo" gia' attivo se l'archivio e'
 * vuoto. Senza associazione nulla e' scelto.
 */
export function valoriIniziali(
  attribuzione: Pick<AttribuzioneCaricamento, 'competenza' | 'titolo' | 'personaId'> | null,
  elenchi: ElenchiAnagrafiche,
  rapporto: RapportoDettaglio | undefined,
  riferimento: FontiScelta['riferimento'],
): ValoriECopiati {
  const periodo = attribuzione
    ? copiaSeVuoti(valoriVuoti(), [
        {
          dati: daAssociazione(attribuzione, rapporto?.ccnl?.meseQuattordicesima ?? null),
          nota: `${t.dalMeseAssociato} (${attribuzione.titolo}): ${t.daVerificare}`,
        },
      ])
    : { valori: valoriVuoti(), copiati: {} };
  const persona = (attribuzione && elenchi.persone?.find((p) => p.id === attribuzione.personaId)) ?? null;
  const azienda = (rapporto && elenchi.aziende?.find((a) => a.id === rapporto.azienda.id)) ?? null;
  const coppia = persona !== null && azienda !== null;
  return applicaScelta(
    {
      ...periodo.valori,
      personaId: persona ? String(persona.id) : null,
      nuovaPersona: elenchi.persone?.length === 0,
      aziendaId: azienda ? String(azienda.id) : null,
      nuovaAzienda: elenchi.aziende?.length === 0,
    },
    periodo.copiati,
    { persona: true, azienda: true },
    {
      persona,
      azienda,
      rapporto: coppia ? (rapporto ?? null) : null,
      riferimento: coppia ? riferimento : null,
    },
  );
}
