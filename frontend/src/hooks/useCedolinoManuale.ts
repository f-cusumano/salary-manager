import { randomId } from '@mantine/hooks';
import type {
  AttribuzioneCaricamento,
  AziendaSelezionabile,
  CaricamentoInErrore,
  CategoriaVoce,
  CedolinoDettaglio,
  CedolinoManuale,
  ErroreCampo,
  MotivoErrore,
  PersonaSintesi,
  RapportoDettaglio,
  RigaMensilita,
  TipoMensilita,
  TipoRateo,
  UnitaMisura,
  Voce,
} from '../api/types';
import { formatMensilita } from '../format/format';
import { testi } from '../i18n';

/**
 * Funzioni pure dei cedolini caricati con errore e del loro inserimento a mano (testate in useCedolinoManuale.test.ts,
 * come le funzioni accanto a useCaricamento).
 */

/** Azioni possibili su un file in errore (le etichette sono in testi.inErrore.azione). */
export type AzioneFileInErrore =
  | 'INSERISCI_A_MANO'
  | 'RIPROVA_CON_PASSWORD'
  | 'RIPROVA'
  | 'ASSOCIA'
  | 'CAMBIA_MESE'
  | 'RIMUOVI_ASSOCIAZIONE'
  | 'SCARICA_PDF'
  | 'RIMUOVI'
  | 'CARICA_DI_NUOVO';

/** Indicazione mostrata accanto alle azioni (testi in testi.inErrore.suggerimento). */
export type SuggerimentoFileInErrore =
  'MESE_PRESENTE' | 'PDF_NON_VALIDO' | 'DIVIDI_PDF' | 'PDF_NON_CONSERVATO';

export interface AzioniFileInErrore {
  /** Pulsante visibile della riga. */
  principale: AzioneFileInErrore;
  /** Voci del menu "Altre azioni", nell'ordine in cui mostrarle. */
  menu: AzioneFileInErrore[];
  suggerimento: SuggerimentoFileInErrore | null;
  /** Un nuovo tentativo dello stesso file e' in corso: azioni disabilitate (il backend le rifiuterebbe). */
  bloccate: boolean;
}

/** Motivi transitori: la prima cosa da fare e' rileggere il PDF conservato. */
const MOTIVI_DA_RIPROVARE: readonly MotivoErrore[] = [
  'SERVIZIO_NON_DISPONIBILE',
  'INTERROTTO',
  'ERRORE_IMPREVISTO',
];

/** Chiave di una mensilita' (come Competenza.chiave) da anno + mese 1..14: (2023, 5) => "2023.05", 13 => "2023.13". */
export function chiaveMensilita(anno: number, mese: number): string {
  return `${anno}.${String(mese).padStart(2, '0')}`;
}

/** Il file si puo' inserire a mano adesso: PDF conservato e nessun nuovo tentativo in corso. */
export function completabileAMano(
  elemento: Pick<CaricamentoInErrore, 'pdfDisponibile' | 'inElaborazione'>,
): boolean {
  return elemento.pdfDisponibile && !elemento.inElaborazione;
}

function suggerimentoSenzaPdf(motivo: MotivoErrore | null): SuggerimentoFileInErrore {
  switch (motivo) {
    case 'PDF_NON_VALIDO':
      return 'PDF_NON_VALIDO';
    case 'PDF_TROPPE_PAGINE':
    case 'PIU_CEDOLINI':
      return 'DIVIDI_PDF';
    default:
      // anche gli errori registrati prima dei motivi (motivo null): il PDF non e' stato conservato
      return 'PDF_NON_CONSERVATO';
  }
}

/**
 * Azione principale, menu e indicazione di un file in errore, in base a motivo, PDF conservato, associazione e
 * tentativo in corso: un mese diventato presente suggerisce di rimuovere il file; con il PDF si propone la rilettura
 * (password per i protetti, semplice per i guasti transitori) o l'inserimento a mano; senza PDF si puo' solo caricare
 * di nuovo il file.
 */
export function azioniPerCaricamento(
  elemento: Pick<CaricamentoInErrore, 'motivo' | 'pdfDisponibile' | 'inElaborazione' | 'attribuzione'>,
): AzioniFileInErrore {
  const bloccate = elemento.inElaborazione;
  if (elemento.attribuzione?.competenzaPresente) {
    return { principale: 'RIMUOVI', menu: ['RIMUOVI_ASSOCIAZIONE'], suggerimento: 'MESE_PRESENTE', bloccate };
  }
  const associazione: AzioneFileInErrore[] = elemento.attribuzione
    ? ['CAMBIA_MESE', 'RIMUOVI_ASSOCIAZIONE']
    : ['ASSOCIA'];
  if (!elemento.pdfDisponibile) {
    return {
      principale: 'CARICA_DI_NUOVO',
      menu: [...associazione, 'RIMUOVI'],
      suggerimento: suggerimentoSenzaPdf(elemento.motivo),
      bloccate,
    };
  }
  if (elemento.motivo === 'PDF_PROTETTO') {
    return {
      principale: 'RIPROVA_CON_PASSWORD',
      menu: ['INSERISCI_A_MANO', ...associazione, 'SCARICA_PDF', 'RIMUOVI'],
      suggerimento: null,
      bloccate,
    };
  }
  if (elemento.motivo !== null && MOTIVI_DA_RIPROVARE.includes(elemento.motivo)) {
    return {
      principale: 'RIPROVA',
      menu: ['INSERISCI_A_MANO', ...associazione, 'SCARICA_PDF', 'RIMUOVI'],
      suggerimento: null,
      bloccate,
    };
  }
  return {
    principale: 'INSERISCI_A_MANO',
    menu: ['RIPROVA', ...associazione, 'SCARICA_PDF', 'RIMUOVI'],
    suggerimento: null,
    bloccate,
  };
}

// ------------------------------------------------------------------ valori del modulo di inserimento a mano

/**
 * Numero nel modulo: '' se vuoto. Durante la digitazione il campo numerico puo' lasciare un testo intermedio con il
 * punto come separatore decimale (es. "12."), che vale come il numero che rappresenta.
 */
export type NumeroModulo = number | string;

/** Superminimo assorbibile come scelto nel modulo (null nella richiesta per NON_INDICATO). */
export type SceltaSuperminimo = 'SI' | 'NO' | 'NON_INDICATO';

export interface VoceModulo {
  /** Chiave stabile della riga (spostamenti e rimozioni nel modulo non controllato); non viene inviata. */
  chiave: string;
  /**
   * Riga copiata da un cedolino di riferimento: i suoi dati al momento della copia (non inviati). Finche' l'utente
   * non la modifica e' solo un modello senza valori del mese: non viene inviata, controllata ne' contata.
   */
  copia?: string;
  codice: string;
  descrizione: string;
  categoria: string | null;
  quantita: NumeroModulo;
  unita: string;
  base: NumeroModulo;
  importoCompetenza: NumeroModulo;
  importoTrattenuta: NumeroModulo;
}

export interface RateoModulo {
  chiave: string;
  /** Come VoceModulo.copia. */
  copia?: string;
  tipo: string | null;
  unita: string | null;
  residuoAnnoPrecedente: NumeroModulo;
  maturato: NumeroModulo;
  goduto: NumeroModulo;
  saldo: NumeroModulo;
}

/**
 * Valori del modulo: stessi percorsi della richiesta CedolinoManuale (gli errori del backend si mostrano sul campo
 * omonimo), con testi vuoti '' al posto di null, scelte come stringhe e mese delle variabili come "AAAA-MM".
 */
export interface ValoriCedolinoManuale {
  /** Persona scelta dall'elenco (id come stringa, valore della tendina); null se non scelta o nuova. */
  personaId: string | null;
  /** "Crea nuovo": la persona non e' in archivio e i suoi dati si inseriscono a mano (mai con personaId). */
  nuovaPersona: boolean;
  /** Azienda scelta dall'elenco; null se non scelta o nuova. */
  aziendaId: string | null;
  /** "Crea nuova": l'azienda non e' in archivio (mai con aziendaId). */
  nuovaAzienda: boolean;
  dipendente: {
    codiceFiscale: string;
    nome: string;
    cognome: string;
    dataNascita: string;
    codiceDipendente: string;
  };
  datoreLavoro: {
    codiceFiscale: string;
    partitaIva: string;
    posizioneInps: string;
    posizioneInail: string;
    ragioneSociale: string;
    indirizzo: string;
    codiceAzienda: string;
  };
  datiContrattuali: {
    dataAssunzione: string;
    dataCessazione: string;
    qualifica: string;
    livello: string;
    ccnl: string;
    codiceCnel: string;
  };
  periodo: {
    anno: NumeroModulo;
    /** "1".."12" (valore della tendina dei mesi). */
    mese: string | null;
    tipo: string | null;
    dataElaborazione: string;
    /** "AAAA-MM"; vuoto se non stampato; incompleto se manca il mese o l'anno. */
    meseVariabili: string;
  };
  totali: {
    lordo: NumeroModulo;
    totaleCompetenze: NumeroModulo;
    totaleTrattenute: NumeroModulo;
    arrotondamento: NumeroModulo;
    netto: NumeroModulo;
  };
  retribuzione: {
    minimo: NumeroModulo;
    contingenza: NumeroModulo;
    superminimo: NumeroModulo;
    superminimoAssorbibile: SceltaSuperminimo;
    importoScatti: NumeroModulo;
    altriElementiFissi: NumeroModulo;
    retribuzioneBase: NumeroModulo;
    totale: NumeroModulo;
    pagaOraria: NumeroModulo;
    pagaGiornaliera: NumeroModulo;
    divisoreOrario: NumeroModulo;
    divisoreGiornaliero: NumeroModulo;
  };
  voci: VoceModulo[];
  ratei: RateoModulo[];
  tfr: {
    fondo31DicembreAnnoPrecedente: NumeroModulo;
    rivalutazione: NumeroModulo;
    impostaSostitutiva: NumeroModulo;
    quotaAnno: NumeroModulo;
    quotaMese: NumeroModulo;
    quotaFondi: NumeroModulo;
    quotaBusta: NumeroModulo;
    anticipi: NumeroModulo;
    retribuzioneUtile: NumeroModulo;
    liquidatoLordo: NumeroModulo;
    ritenuta: NumeroModulo;
  };
  presenze: {
    giorniLavorati: NumeroModulo;
    oreLavorate: NumeroModulo;
    giorniRetribuiti: NumeroModulo;
  };
  scatti: {
    numeroScatti: NumeroModulo;
    dataProssimoScatto: string;
  };
  buoniPasto: {
    presenti: boolean;
    numero: NumeroModulo;
    valoreUnitario: NumeroModulo;
    importo: NumeroModulo;
  };
  progressivi: {
    imponibilePrevidenziale: NumeroModulo;
    imponibileIrpef: NumeroModulo;
    irpefTrattenuta: NumeroModulo;
  };
  contieneTredicesima: boolean;
  contieneQuattordicesima: boolean;
}

/** Parte dei valori del modulo (le liste, se presenti, sono complete): origine di un precompilamento. */
export type ValoriParziali = Parziale<ValoriCedolinoManuale>;

type Parziale<T> = T extends readonly unknown[]
  ? T
  : T extends object
    ? { [K in keyof T]?: Parziale<T[K]> }
    : T;

/**
 * Come si indica una persona o un'azienda: non ancora indicata, scelta dall'elenco delle anagrafiche in archivio
 * (dati anagrafici in sola lettura) o nuova ("Crea nuovo", dati inseriti a mano).
 */
export type ModoAnagrafica = 'nessuna' | 'scelta' | 'nuova';

export function modoPersona(
  valori: Pick<ValoriCedolinoManuale, 'personaId' | 'nuovaPersona'>,
): ModoAnagrafica {
  return valori.personaId !== null ? 'scelta' : valori.nuovaPersona ? 'nuova' : 'nessuna';
}

export function modoAzienda(
  valori: Pick<ValoriCedolinoManuale, 'aziendaId' | 'nuovaAzienda'>,
): ModoAnagrafica {
  return valori.aziendaId !== null ? 'scelta' : valori.nuovaAzienda ? 'nuova' : 'nessuna';
}

export const TIPI_MENSILITA: readonly TipoMensilita[] = [
  'ORDINARIA',
  'TREDICESIMA',
  'QUATTORDICESIMA',
  'FINE_RAPPORTO',
];

export const CATEGORIE_VOCE: readonly CategoriaVoce[] = [
  'COMPETENZA',
  'TRATTENUTA_PREVIDENZIALE',
  'TRATTENUTA_FISCALE',
  'ALTRA_TRATTENUTA',
  'NON_RETRIBUTIVA',
  'PRESENZA',
  'INFORMATIVA',
];

export const TIPI_RATEO: readonly TipoRateo[] = ['FERIE', 'PERMESSI', 'ROL', 'EX_FESTIVITA', 'BANCA_ORE'];
export const UNITA_RATEO: readonly UnitaMisura[] = ['GIORNI', 'ORE'];

/** Modulo vuoto: nessuna voce, nessun rateo, superminimo non indicato. */
export function valoriVuoti(): ValoriCedolinoManuale {
  return {
    personaId: null,
    nuovaPersona: false,
    aziendaId: null,
    nuovaAzienda: false,
    dipendente: { codiceFiscale: '', nome: '', cognome: '', dataNascita: '', codiceDipendente: '' },
    datoreLavoro: {
      codiceFiscale: '',
      partitaIva: '',
      posizioneInps: '',
      posizioneInail: '',
      ragioneSociale: '',
      indirizzo: '',
      codiceAzienda: '',
    },
    datiContrattuali: {
      dataAssunzione: '',
      dataCessazione: '',
      qualifica: '',
      livello: '',
      ccnl: '',
      codiceCnel: '',
    },
    periodo: { anno: '', mese: null, tipo: null, dataElaborazione: '', meseVariabili: '' },
    totali: { lordo: '', totaleCompetenze: '', totaleTrattenute: '', arrotondamento: '', netto: '' },
    retribuzione: {
      minimo: '',
      contingenza: '',
      superminimo: '',
      superminimoAssorbibile: 'NON_INDICATO',
      importoScatti: '',
      altriElementiFissi: '',
      retribuzioneBase: '',
      totale: '',
      pagaOraria: '',
      pagaGiornaliera: '',
      divisoreOrario: '',
      divisoreGiornaliero: '',
    },
    voci: [],
    ratei: [],
    tfr: {
      fondo31DicembreAnnoPrecedente: '',
      rivalutazione: '',
      impostaSostitutiva: '',
      quotaAnno: '',
      quotaMese: '',
      quotaFondi: '',
      quotaBusta: '',
      anticipi: '',
      retribuzioneUtile: '',
      liquidatoLordo: '',
      ritenuta: '',
    },
    presenze: { giorniLavorati: '', oreLavorate: '', giorniRetribuiti: '' },
    scatti: { numeroScatti: '', dataProssimoScatto: '' },
    buoniPasto: { presenti: false, numero: '', valoreUnitario: '', importo: '' },
    progressivi: { imponibilePrevidenziale: '', imponibileIrpef: '', irpefTrattenuta: '' },
    contieneTredicesima: false,
    contieneQuattordicesima: false,
  };
}

/** Nuova riga di voce; la categoria proposta e' quella della riga precedente. */
export function voceVuota(categoria: string | null = 'COMPETENZA'): VoceModulo {
  return {
    chiave: randomId(),
    codice: '',
    descrizione: '',
    categoria,
    quantita: '',
    unita: '',
    base: '',
    importoCompetenza: '',
    importoTrattenuta: '',
  };
}

export function rateoVuoto(tipo: TipoRateo | null = null, unita: UnitaMisura | null = null): RateoModulo {
  return {
    chiave: randomId(),
    tipo,
    unita,
    residuoAnnoPrecedente: '',
    maturato: '',
    goduto: '',
    saldo: '',
  };
}

// ------------------------------------------------------------------ conversione nella richiesta

/** Il numero rappresentato da un valore del modulo; null se vuoto o non interpretabile. */
export function numeroDa(valore: NumeroModulo): number | null {
  if (typeof valore === 'number') {
    return Number.isFinite(valore) ? valore : null;
  }
  const testo = valore.trim();
  if (testo === '') {
    return null;
  }
  const numero = Number(testo);
  return Number.isFinite(numero) ? numero : null;
}

function vuoto(valore: NumeroModulo | string | null): boolean {
  return valore === null || (typeof valore === 'string' && valore.trim() === '');
}

function testoDa(valore: string): string | null {
  const pulito = valore.trim();
  return pulito === '' ? null : pulito;
}

function scelta<T extends string>(valore: string | null, ammessi: readonly T[]): T | null {
  return ammessi.find((a) => a === valore) ?? null;
}

const MESE_AAAA_MM = /^\d{4}-(0[1-9]|1[0-2])$/;

function voceSenzaDati(voce: VoceModulo): boolean {
  return [
    voce.codice,
    voce.descrizione,
    voce.quantita,
    voce.unita,
    voce.base,
    voce.importoCompetenza,
    voce.importoTrattenuta,
  ].every(vuoto);
}

function rateoSenzaDati(rateo: RateoModulo): boolean {
  return (
    vuoto(rateo.tipo) &&
    vuoto(rateo.unita) &&
    [rateo.residuoAnnoPrecedente, rateo.maturato, rateo.goduto, rateo.saldo].every(vuoto)
  );
}

/** Dati di una voce come verrebbero inviati: riconosce una riga copiata e non modificata. */
function firmaVoce(voce: VoceModulo): string {
  return JSON.stringify([
    testoDa(voce.codice),
    testoDa(voce.descrizione),
    voce.categoria,
    numeroDa(voce.quantita),
    testoDa(voce.unita),
    numeroDa(voce.base),
    numeroDa(voce.importoCompetenza),
    numeroDa(voce.importoTrattenuta),
  ]);
}

function firmaRateo(rateo: RateoModulo): string {
  return JSON.stringify([
    rateo.tipo,
    rateo.unita,
    numeroDa(rateo.residuoAnnoPrecedente),
    numeroDa(rateo.maturato),
    numeroDa(rateo.goduto),
    numeroDa(rateo.saldo),
  ]);
}

/**
 * Voce copiata da un cedolino di riferimento e lasciata com'era: senza quantita' e importi del mese (la copia non li
 * riporta) non viene salvata. Modificata in un qualunque campo diventa una voce dell'utente.
 */
export function voceCopiataVuota(voce: VoceModulo): boolean {
  return (
    voce.copia !== undefined &&
    voce.copia === firmaVoce(voce) &&
    [voce.quantita, voce.importoCompetenza, voce.importoTrattenuta].every(vuoto)
  );
}

/** Rateo copiato e lasciato com'era, senza maturato, goduto e saldo del mese: non viene salvato. */
export function rateoCopiatoVuoto(rateo: RateoModulo): boolean {
  return (
    rateo.copia !== undefined &&
    rateo.copia === firmaRateo(rateo) &&
    [rateo.maturato, rateo.goduto, rateo.saldo].every(vuoto)
  );
}

/** Righe che non vengono inviate: vuote, oppure copiate e non completate. */
function voceNonInviata(voce: VoceModulo): boolean {
  return voceSenzaDati(voce) || voceCopiataVuota(voce);
}

function rateoNonInviato(rateo: RateoModulo): boolean {
  return rateoSenzaDati(rateo) || rateoCopiatoVuoto(rateo);
}

/** Indice nel modulo di ogni riga inviata (le righe vuote non vengono inviate): richiesta -> modulo. */
export interface IndiciRighe {
  voci: number[];
  ratei: number[];
}

export function indiciRighe(valori: ValoriCedolinoManuale): IndiciRighe {
  const indici = <T>(righe: T[], inviata: (riga: T) => boolean) =>
    righe.flatMap((riga, indice) => (inviata(riga) ? [indice] : []));
  return {
    voci: indici(valori.voci, (v) => !voceNonInviata(v)),
    // un rateo senza tipo o unita' e' gia' un errore bloccante: non si puo' inviare
    ratei: indici(
      valori.ratei,
      (r) =>
        !rateoNonInviato(r) && scelta(r.tipo, TIPI_RATEO) !== null && scelta(r.unita, UNITA_RATEO) !== null,
    ),
  };
}

function numeri<K extends string>(gruppo: Record<K, NumeroModulo>): Record<K, number | null> {
  return Object.fromEntries(
    Object.entries<NumeroModulo>(gruppo).map(([campo, valore]) => [campo, numeroDa(valore)]),
  ) as Record<K, number | null>;
}

/** Id di un'anagrafica scelta dall'elenco (valore della tendina) come lo vuole la richiesta. */
function idDa(valore: string | null): number | null {
  const id = valore !== null && valore !== '' ? Number(valore) : NaN;
  return Number.isInteger(id) ? id : null;
}

/**
 * Richiesta CedolinoManuale dai valori del modulo: persona e azienda scelte dall'elenco come id (null per quelle
 * nuove), testi e numeri vuoti diventano null, le righe vuote di voci e ratei (e quelle copiate da un altro cedolino e
 * non completate) non vengono inviate, il TFR senza alcun valore e' null, i buoni pasto non presenti non hanno importi
 * e il tipo tredicesima/quattordicesima implica il flag corrispondente (come la normalizzazione del backend).
 */
export function richiestaDa(valori: ValoriCedolinoManuale): CedolinoManuale {
  const { dipendente, datoreLavoro, datiContrattuali, periodo, retribuzione, buoniPasto } = valori;
  const indici = indiciRighe(valori);
  const tipo = scelta(periodo.tipo, TIPI_MENSILITA);
  const tfr = numeri(valori.tfr);
  const { superminimoAssorbibile, ...importiRetribuzione } = retribuzione;
  const presenti = buoniPasto.presenti;
  return {
    // il backend usa i dati in archivio delle anagrafiche scelte; senza id le crea dai dati inseriti
    personaId: idDa(valori.personaId),
    aziendaId: idDa(valori.aziendaId),
    dipendente: {
      codiceFiscale: testoDa(dipendente.codiceFiscale),
      nome: testoDa(dipendente.nome),
      cognome: testoDa(dipendente.cognome),
      dataNascita: testoDa(dipendente.dataNascita),
      codiceDipendente: testoDa(dipendente.codiceDipendente),
    },
    datoreLavoro: {
      codiceFiscale: testoDa(datoreLavoro.codiceFiscale),
      partitaIva: testoDa(datoreLavoro.partitaIva),
      posizioneInps: testoDa(datoreLavoro.posizioneInps),
      posizioneInail: testoDa(datoreLavoro.posizioneInail),
      ragioneSociale: testoDa(datoreLavoro.ragioneSociale),
      indirizzo: testoDa(datoreLavoro.indirizzo),
      codiceAzienda: testoDa(datoreLavoro.codiceAzienda),
    },
    datiContrattuali: {
      dataAssunzione: testoDa(datiContrattuali.dataAssunzione),
      dataCessazione: testoDa(datiContrattuali.dataCessazione),
      qualifica: testoDa(datiContrattuali.qualifica),
      livello: testoDa(datiContrattuali.livello),
      ccnl: testoDa(datiContrattuali.ccnl),
      codiceCnel: testoDa(datiContrattuali.codiceCnel),
    },
    periodo: {
      anno: numeroDa(periodo.anno),
      mese: periodo.mese !== null && periodo.mese !== '' ? Number(periodo.mese) : null,
      tipo,
      dataElaborazione: testoDa(periodo.dataElaborazione),
      meseVariabili: MESE_AAAA_MM.test(periodo.meseVariabili) ? periodo.meseVariabili : null,
    },
    totali: numeri(valori.totali),
    retribuzione: {
      ...numeri(importiRetribuzione),
      superminimoAssorbibile:
        superminimoAssorbibile === 'SI' ? true : superminimoAssorbibile === 'NO' ? false : null,
    },
    voci: indici.voci.flatMap((indice) => {
      const voce = valori.voci[indice];
      return voce
        ? [
            {
              codice: testoDa(voce.codice),
              descrizione: testoDa(voce.descrizione),
              categoria: scelta(voce.categoria, CATEGORIE_VOCE),
              quantita: numeroDa(voce.quantita),
              unita: testoDa(voce.unita),
              base: numeroDa(voce.base),
              importoCompetenza: numeroDa(voce.importoCompetenza),
              importoTrattenuta: numeroDa(voce.importoTrattenuta),
            },
          ]
        : [];
    }),
    ratei: indici.ratei.flatMap((indice) => {
      const rateo = valori.ratei[indice];
      const tipoRateo = scelta(rateo?.tipo ?? null, TIPI_RATEO);
      const unita = scelta(rateo?.unita ?? null, UNITA_RATEO);
      return rateo && tipoRateo && unita
        ? [
            {
              tipo: tipoRateo,
              unita,
              residuoAnnoPrecedente: numeroDa(rateo.residuoAnnoPrecedente),
              maturato: numeroDa(rateo.maturato),
              goduto: numeroDa(rateo.goduto),
              saldo: numeroDa(rateo.saldo),
            },
          ]
        : [];
    }),
    tfr: Object.values(tfr).every((v) => v === null) ? null : tfr,
    presenze: numeri(valori.presenze),
    scatti: {
      numeroScatti: numeroDa(valori.scatti.numeroScatti),
      dataProssimoScatto: testoDa(valori.scatti.dataProssimoScatto),
    },
    buoniPasto: {
      presenti,
      numero: presenti ? numeroDa(buoniPasto.numero) : null,
      valoreUnitario: presenti ? numeroDa(buoniPasto.valoreUnitario) : null,
      importo: presenti ? numeroDa(buoniPasto.importo) : null,
    },
    progressivi: numeri(valori.progressivi),
    contieneTredicesima: valori.contieneTredicesima || tipo === 'TREDICESIMA',
    contieneQuattordicesima: valori.contieneQuattordicesima || tipo === 'QUATTORDICESIMA',
  };
}

/** Testi di un gruppo della richiesta come valori del modulo (null diventa ''). */
function testiModulo<T extends Record<string, string | null>>(gruppo: T): { [K in keyof T]: string } {
  return Object.fromEntries(Object.entries(gruppo).map(([campo, valore]) => [campo, valore ?? ''])) as {
    [K in keyof T]: string;
  };
}

/** Numeri di un gruppo della richiesta come valori del modulo (null diventa ''). */
function numeriModulo<T extends Record<string, number | null>>(gruppo: T): { [K in keyof T]: NumeroModulo } {
  return Object.fromEntries(
    Object.entries(gruppo).map(([campo, valore]) => [campo, numeroModulo(valore)]),
  ) as { [K in keyof T]: NumeroModulo };
}

/**
 * Valori del modulo dai dati di un cedolino gia' archiviato (l'inverso di richiestaDa), per modificarlo: persona e
 * azienda restano quelle scelte, voci e ratei diventano righe del modulo complete (non copie da completare).
 */
export function valoriDaCedolino(dati: CedolinoManuale): ValoriCedolinoManuale {
  const vuoti = valoriVuoti();
  const { superminimoAssorbibile, ...importiRetribuzione } = dati.retribuzione;
  return {
    personaId: dati.personaId !== null ? String(dati.personaId) : null,
    nuovaPersona: false,
    aziendaId: dati.aziendaId !== null ? String(dati.aziendaId) : null,
    nuovaAzienda: false,
    dipendente: testiModulo(dati.dipendente),
    datoreLavoro: testiModulo(dati.datoreLavoro),
    datiContrattuali: testiModulo(dati.datiContrattuali),
    periodo: {
      anno: numeroModulo(dati.periodo.anno),
      mese: dati.periodo.mese !== null ? String(dati.periodo.mese) : null,
      tipo: dati.periodo.tipo,
      dataElaborazione: dati.periodo.dataElaborazione ?? '',
      meseVariabili: dati.periodo.meseVariabili ?? '',
    },
    totali: { ...vuoti.totali, ...numeriModulo(dati.totali) },
    retribuzione: {
      ...vuoti.retribuzione,
      ...numeriModulo(importiRetribuzione),
      superminimoAssorbibile: SCELTA_SUPERMINIMO(superminimoAssorbibile),
    },
    voci: dati.voci.map((voce) => ({
      ...voceVuota(voce.categoria),
      codice: voce.codice ?? '',
      descrizione: voce.descrizione ?? '',
      quantita: numeroModulo(voce.quantita),
      unita: voce.unita ?? '',
      base: numeroModulo(voce.base),
      importoCompetenza: numeroModulo(voce.importoCompetenza),
      importoTrattenuta: numeroModulo(voce.importoTrattenuta),
    })),
    ratei: dati.ratei.map((rateo) => ({
      ...rateoVuoto(rateo.tipo, rateo.unita),
      residuoAnnoPrecedente: numeroModulo(rateo.residuoAnnoPrecedente),
      maturato: numeroModulo(rateo.maturato),
      goduto: numeroModulo(rateo.goduto),
      saldo: numeroModulo(rateo.saldo),
    })),
    tfr: dati.tfr ? { ...vuoti.tfr, ...numeriModulo(dati.tfr) } : vuoti.tfr,
    presenze: { ...vuoti.presenze, ...numeriModulo(dati.presenze) },
    scatti: {
      numeroScatti: numeroModulo(dati.scatti.numeroScatti),
      dataProssimoScatto: dati.scatti.dataProssimoScatto ?? '',
    },
    buoniPasto: {
      presenti: dati.buoniPasto.presenti,
      numero: numeroModulo(dati.buoniPasto.numero),
      valoreUnitario: numeroModulo(dati.buoniPasto.valoreUnitario),
      importo: numeroModulo(dati.buoniPasto.importo),
    },
    progressivi: { ...vuoti.progressivi, ...numeriModulo(dati.progressivi) },
    contieneTredicesima: dati.contieneTredicesima,
    contieneQuattordicesima: dati.contieneQuattordicesima,
  };
}

/**
 * Percorso di un errore nel modulo: "voci[3].descrizione" diventa "voci.3.descrizione". Con gli indici delle righe
 * inviate, l'indice della richiesta (che non contiene le righe vuote) diventa quello della riga nel modulo.
 */
export function percorsoForm(campo: string | null, indici?: IndiciRighe): string | null {
  if (campo === null) {
    return null;
  }
  return campo
    .replace(/^(voci|ratei)\[(\d+)\]/, (_, lista: 'voci' | 'ratei', indice: string) => {
      const riga = indici ? indici[lista][Number(indice)] : undefined;
      return `${lista}.${riga ?? indice}`;
    })
    .replace(/\[(\d+)\]/g, '.$1');
}

// ------------------------------------------------------------------ errori bloccanti

/** Come il backend: 8 cifre intere (i dati calcolati dal cedolino devono stare nelle colonne). */
const MASSIMO_ASSOLUTO = 1e8;
const DECIMALI_MASSIMI = 5;
const PRIMO_ANNO = 1900;
const ULTIMO_ANNO_DATE = 2999;
const UNDICI_CIFRE = /^\d{11}$/;
const CODICE_FISCALE_PERSONA =
  /^[A-Z]{6}[0-9LMNPQRSTUV]{2}[ABCDEHLMPRST][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]$/;
const CODICE_FISCALE_AZIENDA = /^(\d{11}|[A-Z0-9]{16})$/;
/** Valori dei caratteri in posizione dispari (1-based) per il carattere di controllo del codice fiscale. */
const DISPARI = [
  1, 0, 5, 7, 9, 13, 15, 17, 19, 21, 2, 4, 18, 20, 11, 3, 6, 8, 12, 14, 16, 10, 22, 25, 24, 23,
];

/** Codice fiscale di una persona fisica formalmente valido, carattere di controllo compreso (omocodie ammesse). */
export function codiceFiscaleValido(cf: string): boolean {
  if (!CODICE_FISCALE_PERSONA.test(cf)) {
    return false;
  }
  let somma = 0;
  for (let i = 0; i < 15; i += 1) {
    const carattere = cf.charCodeAt(i);
    const indice = carattere <= 57 ? carattere - 48 : carattere - 65;
    somma += i % 2 === 0 ? (DISPARI[indice] ?? 0) : indice;
  }
  return cf.charCodeAt(15) === 65 + (somma % 26);
}

/** Partita IVA (o codice fiscale numerico) di 11 cifre con la cifra di controllo corretta. */
export function partitaIvaValida(piva: string): boolean {
  if (!UNDICI_CIFRE.test(piva) || piva === '00000000000') {
    return false;
  }
  let somma = 0;
  for (let i = 0; i < 10; i += 1) {
    let cifra = Number(piva[i]);
    if (i % 2 === 1) {
      cifra *= 2;
      if (cifra > 9) {
        cifra -= 9;
      }
    }
    somma += cifra;
  }
  return (10 - (somma % 10)) % 10 === Number(piva[10]);
}

/** Caratteri di controllo ISO (come Character.isISOControl del backend). */
function contieneCaratteriDiControllo(testo: string): boolean {
  for (const carattere of testo) {
    const codice = carattere.codePointAt(0) ?? 0;
    if (codice <= 0x1f || (codice >= 0x7f && codice <= 0x9f)) {
      return true;
    }
  }
  return false;
}

/** Testo come lo normalizza il backend: spazi ripetuti ridotti a uno, senza spazi ai bordi. */
function pulito(valore: string): string {
  return valore.replace(/\s+/g, ' ').trim();
}

/** Codice come lo normalizza il backend: maiuscolo e senza spazi, senza il prefisso IT davanti a 11 cifre. */
export function codiceNormalizzato(valore: string): string {
  const codice = valore.replace(/\s+/g, '').toUpperCase();
  return /^IT\d{11}$/.test(codice) ? codice.slice(2) : codice;
}

function componi(modello: string, valori: Record<string, string | number>): string {
  return Object.entries(valori).reduce(
    (testo, [nome, valore]) => testo.replace(`{${nome}}`, String(valore)),
    modello,
  );
}

/**
 * Cifre decimali del numero come scritto: la sua rappresentazione decimale piu' breve, anche esponenziale ("1e-7").
 * Moltiplicare per 10^5 non basta: oltre il milione l'errore dei decimali binari supera qualunque tolleranza fissa
 * (1234567,89 sembrerebbe avere piu' di 5 decimali).
 */
function decimaliDi(valore: number): number {
  const [mantissa = '', esponente = '0'] = Math.abs(valore).toString().split('e');
  return Math.max(0, (mantissa.split('.')[1] ?? '').length - Number(esponente));
}

function fuoriLimiti(valore: number): boolean {
  return Math.abs(valore) >= MASSIMO_ASSOLUTO || decimaliDi(valore) > DECIMALI_MASSIMI;
}

/**
 * Valore di un campo data che il browser non riesce a interpretare (giorno, mese o anno mancanti o impossibili): il
 * campo `type="date"` restituirebbe un testo vuoto e la data andrebbe persa senza avviso.
 */
export const DATA_INCOMPLETA = 'incompleta';

const DATA_ISO = /^(\d{4})-(\d{2})-(\d{2})$/;

/** Data "AAAA-MM-GG" esistente nel calendario (anno di 4 cifre: il campo del browser ne ammette fino a 6). */
function dataValida(data: string): boolean {
  const match = DATA_ISO.exec(data);
  if (!match) {
    return false;
  }
  const [anno, mese, giorno] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const giorni = new Date(Date.UTC(anno, mese, 0)).getUTCDate();
  return mese >= 1 && mese <= 12 && giorno >= 1 && giorno <= giorni;
}

function annoDi(data: string): number {
  return Number(data.slice(0, 4));
}

/** Data di oggi come "AAAA-MM-GG" nel fuso locale. */
function isoLocale(data: Date): string {
  const mese = String(data.getMonth() + 1).padStart(2, '0');
  const giorno = String(data.getDate()).padStart(2, '0');
  return `${data.getFullYear()}-${mese}-${giorno}`;
}

/** Al piu' un errore per campo (il primo), come il backend. */
class Errori {
  private readonly perCampo = new Map<string, string>();

  aggiungi(campo: string, messaggio: string): void {
    if (!this.perCampo.has(campo)) {
      this.perCampo.set(campo, messaggio);
    }
  }

  /** C'e' gia' un errore su un campo del gruppo (es. "datoreLavoro"). */
  inGruppo(gruppo: string): boolean {
    return [...this.perCampo.keys()].some((campo) => campo.startsWith(`${gruppo}.`));
  }

  testo(campo: string, valore: string, massimo: number, obbligatorio?: string): void {
    const normalizzato = pulito(valore);
    if (normalizzato === '') {
      if (obbligatorio) {
        this.aggiungi(campo, obbligatorio);
      }
    } else if (contieneCaratteriDiControllo(normalizzato)) {
      this.aggiungi(campo, e.testoNonAmmesso);
    } else if (normalizzato.length > massimo) {
      this.aggiungi(campo, componi(e.testoLungo, { massimo }));
    }
  }

  numero(
    campo: string,
    valore: NumeroModulo,
    opzioni: { obbligatorio?: string; senzaSegno?: boolean } = {},
  ): void {
    if (vuoto(valore)) {
      if (opzioni.obbligatorio) {
        this.aggiungi(campo, opzioni.obbligatorio);
      }
      return;
    }
    const numero = numeroDa(valore);
    if (numero === null) {
      this.aggiungi(campo, e.numeroNonValido);
    } else if (opzioni.senzaSegno && numero < 0) {
      this.aggiungi(campo, e.senzaSegno);
    } else if (fuoriLimiti(numero)) {
      this.aggiungi(campo, e.fuoriLimiti);
    }
  }

  numeri(gruppo: string, valori: Record<string, NumeroModulo>): void {
    for (const [campo, valore] of Object.entries(valori)) {
      this.numero(`${gruppo}.${campo}`, valore);
    }
  }

  data(campo: string, valore: string): void {
    if (valore === DATA_INCOMPLETA) {
      this.aggiungi(campo, e.dataIncompleta);
    } else if (
      valore !== '' &&
      (!dataValida(valore) || annoDi(valore) < PRIMO_ANNO || annoDi(valore) > ULTIMO_ANNO_DATE)
    ) {
      this.aggiungi(campo, e.data);
    }
  }

  elenco(): ErroreCampo[] {
    return [...this.perCampo].map(([campo, messaggio]) => ({ campo, messaggio }));
  }
}

const e = testi.manuale.erroreCampo;

/**
 * Anagrafiche in archivio con cui si confrontano i dati di una persona o di un'azienda nuova ("Crea nuovo"): un codice
 * fiscale, una partita IVA o un codice fiscale dell'azienda gia' presenti vanno scelti dall'elenco. Matricola INPS e
 * ragione sociale le confronta solo il backend (con le regole del censimento). La data di nascita in archivio della
 * persona scelta non si controlla, come gli altri dati in archivio.
 */
export interface ArchivioAnagrafiche {
  persone: readonly Pick<PersonaSintesi, 'id' | 'codiceFiscale' | 'dataNascita'>[];
  aziende: readonly Pick<AziendaSelezionabile, 'partitaIva' | 'codiceFiscale'>[];
}

export const ARCHIVIO_VUOTO: ArchivioAnagrafiche = { persone: [], aziende: [] };

/**
 * Errori bloccanti del modulo, come quelli del backend (ValidazioneCedolinoManuale) piu' i numeri non interpretabili
 * e il mese delle variabili incompleto, nell'ordine dei campi del modulo. I percorsi sono quelli della richiesta con
 * gli indici delle righe del modulo ("voci[2].descrizione"): si mostrano con percorsoForm.
 *
 * Persona e azienda vanno scelte dall'elenco o create: quelle scelte hanno i dati anagrafici in archivio (nessun
 * controllo, anche per la data di nascita se l'archivio la ha), quelle nuove i controlli di sempre e non possono avere
 * identificativi gia' in archivio.
 */
export function erroriBloccanti(
  valori: ValoriCedolinoManuale,
  archivio: ArchivioAnagrafiche = ARCHIVIO_VUOTO,
  oggi: Date = new Date(),
): ErroreCampo[] {
  const errori = new Errori();
  const { periodo, dipendente, datoreLavoro: datore, datiContrattuali: contratto, totali } = valori;

  // periodo
  if (scelta(periodo.tipo, TIPI_MENSILITA) === null) {
    errori.aggiungi('periodo.tipo', e.tipo);
  }
  const mese = periodo.mese !== null ? Number(periodo.mese) : NaN;
  if (!Number.isInteger(mese) || mese < 1 || mese > 12) {
    errori.aggiungi('periodo.mese', e.mese);
  }
  const annoMassimo = oggi.getFullYear() + 1;
  const anno = numeroDa(periodo.anno);
  if (anno === null || !Number.isInteger(anno) || anno < PRIMO_ANNO || anno > annoMassimo) {
    errori.aggiungi('periodo.anno', componi(e.anno, { anno: annoMassimo }));
  }
  errori.data('periodo.dataElaborazione', periodo.dataElaborazione);
  if (periodo.meseVariabili !== '') {
    if (!MESE_AAAA_MM.test(periodo.meseVariabili)) {
      errori.aggiungi('periodo.meseVariabili', e.meseVariabili);
    } else {
      // il mese "AAAA-MM" si controlla come data del suo primo giorno (anno nei limiti delle date)
      errori.data('periodo.meseVariabili', `${periodo.meseVariabili}-01`);
    }
  }

  // dipendente
  const persona = modoPersona(valori);
  if (persona === 'nessuna') {
    errori.aggiungi('personaId', e.personaDaScegliere);
  } else if (persona === 'nuova') {
    const cf = codiceNormalizzato(dipendente.codiceFiscale);
    if (cf === '') {
      errori.aggiungi('dipendente.codiceFiscale', e.codiceFiscale);
    } else if (archivio.persone.some((censita) => codiceNormalizzato(censita.codiceFiscale) === cf)) {
      errori.aggiungi('dipendente.codiceFiscale', e.codiceFiscaleInArchivio);
    } else if (!codiceFiscaleValido(cf) && !UNDICI_CIFRE.test(cf)) {
      errori.aggiungi('dipendente.codiceFiscale', e.codiceFiscaleNonValido);
    }
    errori.testo('dipendente.cognome', dipendente.cognome, 100, e.cognome);
    errori.testo('dipendente.nome', dipendente.nome, 100, e.nome);
  }
  // la data di nascita si controlla solo se e' scritta nel modulo: quella in archivio della persona scelta (in sola
  // lettura) e' come l'ha salvata il censimento; senza scelta i dati anagrafici sono nascosti
  const dataScritta =
    persona === 'nuova' ||
    (persona === 'scelta' &&
      !archivio.persone.some((p) => String(p.id) === valori.personaId && p.dataNascita != null));
  if (dataScritta) {
    if (dataValida(dipendente.dataNascita) && dipendente.dataNascita > isoLocale(oggi)) {
      errori.aggiungi('dipendente.dataNascita', e.dataNascitaFutura);
    }
    errori.data('dipendente.dataNascita', dipendente.dataNascita);
  }
  errori.testo('dipendente.codiceDipendente', dipendente.codiceDipendente, 50);

  // datore di lavoro
  const azienda = modoAzienda(valori);
  const partitaIva = codiceNormalizzato(datore.partitaIva);
  const cfAzienda = codiceNormalizzato(datore.codiceFiscale);
  if (azienda === 'nessuna') {
    errori.aggiungi('aziendaId', e.aziendaDaScegliere);
  } else if (azienda === 'nuova') {
    if (!/[\p{L}\p{N}]/u.test(datore.ragioneSociale)) {
      errori.aggiungi('datoreLavoro.ragioneSociale', e.ragioneSociale);
    }
    errori.testo('datoreLavoro.ragioneSociale', datore.ragioneSociale, 200);
    if (partitaIva !== '' && !UNDICI_CIFRE.test(partitaIva)) {
      errori.aggiungi('datoreLavoro.partitaIva', e.partitaIva);
    }
    if (cfAzienda !== '' && !CODICE_FISCALE_AZIENDA.test(cfAzienda)) {
      errori.aggiungi('datoreLavoro.codiceFiscale', e.codiceFiscaleAzienda);
    }
    errori.testo('datoreLavoro.posizioneInps', datore.posizioneInps, 50);
  }
  errori.testo('datoreLavoro.posizioneInail', datore.posizioneInail, 50);
  errori.testo('datoreLavoro.indirizzo', datore.indirizzo, 300);
  errori.testo('datoreLavoro.codiceAzienda', datore.codiceAzienda, 50);
  // come il backend: il confronto con l'archivio solo per dati del datore altrimenti corretti
  if (azienda === 'nuova' && !errori.inGruppo('datoreLavoro')) {
    const inArchivio = new Set(
      archivio.aziende
        .flatMap((a) => [a.partitaIva, a.codiceFiscale])
        .flatMap((codice) => (codice ? [codiceNormalizzato(codice)] : [])),
    );
    if (partitaIva !== '' && inArchivio.has(partitaIva)) {
      errori.aggiungi('datoreLavoro.partitaIva', e.partitaIvaInArchivio);
    } else if (cfAzienda !== '' && inArchivio.has(cfAzienda)) {
      errori.aggiungi('datoreLavoro.codiceFiscale', e.codiceFiscaleAziendaInArchivio);
    }
  }

  // dati contrattuali
  errori.data('datiContrattuali.dataAssunzione', contratto.dataAssunzione);
  if (
    dataValida(contratto.dataAssunzione) &&
    dataValida(contratto.dataCessazione) &&
    contratto.dataCessazione < contratto.dataAssunzione
  ) {
    errori.aggiungi('datiContrattuali.dataCessazione', e.cessazionePrimaAssunzione);
  }
  errori.data('datiContrattuali.dataCessazione', contratto.dataCessazione);
  errori.testo('datiContrattuali.qualifica', contratto.qualifica, 100);
  errori.testo('datiContrattuali.livello', contratto.livello, 50);
  errori.testo('datiContrattuali.ccnl', contratto.ccnl, 300);
  errori.testo('datiContrattuali.codiceCnel', contratto.codiceCnel, 20);

  // totali
  errori.numero('totali.totaleCompetenze', totali.totaleCompetenze, {
    obbligatorio: e.totaleCompetenze,
    senzaSegno: true,
  });
  errori.numero('totali.totaleTrattenute', totali.totaleTrattenute, {
    obbligatorio: e.totaleTrattenute,
    senzaSegno: true,
  });
  errori.numero('totali.arrotondamento', totali.arrotondamento);
  errori.numero('totali.netto', totali.netto, { obbligatorio: e.netto });
  errori.numero('totali.lordo', totali.lordo);

  // voci (solo le righe inviate)
  const indici = indiciRighe(valori);
  for (const i of indici.voci) {
    const voce = valori.voci[i];
    if (!voce) {
      continue;
    }
    const p = `voci[${i}].`;
    errori.testo(`${p}codice`, voce.codice, 30);
    errori.testo(`${p}descrizione`, voce.descrizione, 200, e.descrizioneVoce);
    if (scelta(voce.categoria, CATEGORIE_VOCE) === null) {
      errori.aggiungi(`${p}categoria`, e.categoriaVoce);
    }
    errori.numero(`${p}quantita`, voce.quantita);
    errori.testo(`${p}unita`, voce.unita, 20);
    errori.numero(`${p}base`, voce.base);
    errori.numero(`${p}importoCompetenza`, voce.importoCompetenza, { senzaSegno: true });
    errori.numero(`${p}importoTrattenuta`, voce.importoTrattenuta, { senzaSegno: true });
  }

  // ratei (le righe del tutto vuote e quelle copiate e non completate non vengono inviate)
  const visti = new Set<string>();
  valori.ratei.forEach((rateo, i) => {
    if (rateoNonInviato(rateo)) {
      return;
    }
    const p = `ratei[${i}]`;
    const tipo = scelta(rateo.tipo, TIPI_RATEO);
    const unita = scelta(rateo.unita, UNITA_RATEO);
    if (tipo === null) {
      errori.aggiungi(`${p}.tipo`, e.tipoRateo);
    }
    if (unita === null) {
      errori.aggiungi(`${p}.unita`, e.unitaRateo);
    }
    if (tipo !== null && unita !== null) {
      const chiave = `${tipo}/${unita}`;
      if (visti.has(chiave)) {
        errori.aggiungi(p, e.rateoRipetuto);
      }
      visti.add(chiave);
    }
    const quantita = [rateo.residuoAnnoPrecedente, rateo.maturato, rateo.goduto, rateo.saldo];
    if (quantita.every(vuoto)) {
      errori.aggiungi(p, e.rateoVuoto);
    }
    errori.numero(`${p}.residuoAnnoPrecedente`, rateo.residuoAnnoPrecedente);
    errori.numero(`${p}.maturato`, rateo.maturato);
    errori.numero(`${p}.goduto`, rateo.goduto);
    errori.numero(`${p}.saldo`, rateo.saldo);
  });

  // importi e quantita' delle altre sezioni
  errori.numeri(
    'retribuzione',
    Object.fromEntries(
      Object.entries(valori.retribuzione).filter(([campo]) => campo !== 'superminimoAssorbibile'),
    ),
  );
  errori.numeri('presenze', valori.presenze);
  errori.numeri('tfr', valori.tfr);
  const scatti = valori.scatti.numeroScatti;
  if (!vuoto(scatti)) {
    const numero = numeroDa(scatti);
    if (numero === null || !Number.isInteger(numero) || numero < 0 || numero > 99) {
      errori.aggiungi('scatti.numeroScatti', e.numeroScatti);
    }
  }
  errori.data('scatti.dataProssimoScatto', valori.scatti.dataProssimoScatto);
  if (valori.buoniPasto.presenti) {
    errori.numero('buoniPasto.numero', valori.buoniPasto.numero);
    errori.numero('buoniPasto.valoreUnitario', valori.buoniPasto.valoreUnitario);
    errori.numero('buoniPasto.importo', valori.buoniPasto.importo);
  }
  errori.numeri('progressivi', valori.progressivi);
  return errori.elenco();
}

/**
 * Errori su un'intera riga di voci o ratei (o su un suo campo) e errori che dipendono da un altro campo (cessazione
 * prima dell'assunzione): la modifica di un altro campo puo' risolverli, e il modulo toglie solo l'errore del campo
 * modificato.
 */
const ERRORE_DERIVATO = /^(voci|ratei)\.\d+(\.|$)|^datiContrattuali\.dataCessazione$/;

/**
 * Gli errori mostrati senza quelli derivati che i valori attuali non hanno piu' (stesso oggetto se non ne cambia
 * nessuno). Gli errori nuovi non si aggiungono: compaiono alla verifica o all'uscita dal campo.
 */
export function senzaErroriRisolti<T>(
  errori: Readonly<Record<string, T>>,
  valori: ValoriCedolinoManuale,
  archivio: ArchivioAnagrafiche = ARCHIVIO_VUOTO,
): Readonly<Record<string, T>> {
  const derivati = Object.keys(errori).filter((percorso) => ERRORE_DERIVATO.test(percorso));
  if (derivati.length === 0) {
    return errori;
  }
  const attuali = new Set(erroriBloccanti(valori, archivio).map((e) => percorsoForm(e.campo)));
  const risolti = new Set(derivati.filter((percorso) => !attuali.has(percorso)));
  if (risolti.size === 0) {
    return errori;
  }
  return Object.fromEntries(Object.entries(errori).filter(([percorso]) => !risolti.has(percorso)));
}

// ------------------------------------------------------------------ controlli di coerenza

/** Avviso non bloccante su un campo o una sezione del modulo (percorso del modulo, es. "totali.netto", "voci"). */
export interface AvvisoCoerenza {
  campo: string;
  messaggio: string;
}

const TOLLERANZA_NETTO = 1;
const TOLLERANZA_SOMME = 0.05;
const TOLLERANZA_RATEI = 0.05;

function fuoriTolleranza(a: number, b: number, tolleranza: number): boolean {
  return Math.abs(a - b) > tolleranza + 1e-9;
}

/**
 * Approssimazione nel browser dei controlli di coerenza del backend (salvati come avvisi con il cedolino): netto
 * rispetto ai totali, somme delle voci rispetto ai totali stampati, saldo dei ratei, codici di controllo degli
 * identificativi (solo di persona e azienda nuove: quelli in archivio non si cambiano qui), fine rapporto senza
 * cessazione. Non bloccano il salvataggio.
 */
export function controlliCoerenza(valori: ValoriCedolinoManuale): AvvisoCoerenza[] {
  const c = testi.manuale.controllo;
  const avvisi: AvvisoCoerenza[] = [];
  const competenze = numeroDa(valori.totali.totaleCompetenze);
  const trattenute = numeroDa(valori.totali.totaleTrattenute);
  const netto = numeroDa(valori.totali.netto);

  if (
    scelta(valori.periodo.tipo, TIPI_MENSILITA) === 'FINE_RAPPORTO' &&
    valori.datiContrattuali.dataCessazione === ''
  ) {
    avvisi.push({ campo: 'periodo.tipo', messaggio: c.fineRapportoSenzaCessazione });
  }
  const cf = codiceNormalizzato(valori.dipendente.codiceFiscale);
  if (modoPersona(valori) === 'nuova' && UNDICI_CIFRE.test(cf)) {
    avvisi.push({ campo: 'dipendente.codiceFiscale', messaggio: c.codiceFiscaleProvvisorio });
  }
  if (modoAzienda(valori) === 'nuova') {
    const partitaIva = codiceNormalizzato(valori.datoreLavoro.partitaIva);
    if (UNDICI_CIFRE.test(partitaIva) && !partitaIvaValida(partitaIva)) {
      avvisi.push({ campo: 'datoreLavoro.partitaIva', messaggio: c.partitaIva });
    }
    const cfAzienda = codiceNormalizzato(valori.datoreLavoro.codiceFiscale);
    if (
      CODICE_FISCALE_AZIENDA.test(cfAzienda) &&
      !partitaIvaValida(cfAzienda) &&
      !codiceFiscaleValido(cfAzienda)
    ) {
      avvisi.push({ campo: 'datoreLavoro.codiceFiscale', messaggio: c.codiceFiscaleAzienda });
    }
  }
  if (competenze !== null && trattenute !== null && netto !== null) {
    const differenza = competenze - trattenute;
    const arrotondamento = numeroDa(valori.totali.arrotondamento) ?? 0;
    if (
      fuoriTolleranza(differenza + arrotondamento, netto, TOLLERANZA_NETTO) &&
      fuoriTolleranza(differenza, netto, TOLLERANZA_NETTO)
    ) {
      avvisi.push({ campo: 'totali.netto', messaggio: c.netto });
    }
  }
  const somme = sommeVoci(valori.voci);
  if (somme !== null) {
    if (competenze !== null && fuoriTolleranza(somme.competenze, competenze, TOLLERANZA_SOMME)) {
      avvisi.push({ campo: 'voci', messaggio: c.sommaCompetenze });
    }
    if (trattenute !== null && fuoriTolleranza(somme.trattenute, trattenute, TOLLERANZA_SOMME)) {
      avvisi.push({ campo: 'voci', messaggio: c.sommaTrattenute });
    }
  }
  valori.ratei.forEach((rateo, i) => {
    const atteso = saldoCalcolato(rateo);
    const saldo = numeroDa(rateo.saldo);
    const tipo = scelta(rateo.tipo, TIPI_RATEO);
    const unita = scelta(rateo.unita, UNITA_RATEO);
    if (atteso !== null && saldo !== null && fuoriTolleranza(atteso, saldo, TOLLERANZA_RATEI)) {
      const nome = tipo && unita ? `${testi.rateo[tipo]} (${testi.manuale.unitaRateo[unita]})` : `${i + 1}`;
      avvisi.push({ campo: `ratei.${i}`, messaggio: componi(c.saldoRateo, { rateo: nome }) });
    }
  });
  return avvisi;
}

/**
 * Somme delle voci che entrano nel netto (escluse informative e presenze), come il backend: null se nessuna di
 * queste voci ha un importo (il confronto con i totali non si fa).
 */
export function sommeVoci(voci: VoceModulo[]): { competenze: number; trattenute: number } | null {
  const nelNetto = voci.filter(
    (v) =>
      v.categoria !== 'INFORMATIVA' &&
      v.categoria !== 'PRESENZA' &&
      (numeroDa(v.importoCompetenza) !== null || numeroDa(v.importoTrattenuta) !== null),
  );
  if (nelNetto.length === 0) {
    return null;
  }
  // somma in centesimi di millesimo: evita gli errori di arrotondamento dei decimali binari
  const somma = (campo: 'importoCompetenza' | 'importoTrattenuta') =>
    nelNetto.reduce((s, v) => s + Math.round((numeroDa(v[campo]) ?? 0) * 1e5), 0) / 1e5;
  return { competenze: somma('importoCompetenza'), trattenute: somma('importoTrattenuta') };
}

/** Saldo atteso di un rateo (residuo AP + maturato - goduto); null se manca uno dei tre valori. */
export function saldoCalcolato(rateo: RateoModulo): number | null {
  const residuo = numeroDa(rateo.residuoAnnoPrecedente);
  const maturato = numeroDa(rateo.maturato);
  const goduto = numeroDa(rateo.goduto);
  if (residuo === null || maturato === null || goduto === null) {
    return null;
  }
  return Math.round((residuo + maturato - goduto) * 1e5) / 1e5;
}

/**
 * Avviso della verifica su una persona o un'azienda che verrebbe creata: l'esito lo dice gia' con una riga propria
 * (nuovaPersona, nuovaAzienda), non si ripete tra gli avvisi.
 */
export function avvisoDelleAnagrafiche(avviso: string): boolean {
  return avviso === testi.manuale.nuovaPersona || avviso === testi.manuale.nuovaAzienda;
}

// ------------------------------------------------------------------ sezioni del modulo

export type SezioneModulo =
  | 'periodo'
  | 'dipendente'
  | 'datore'
  | 'contratto'
  | 'totali'
  | 'voci'
  | 'ratei'
  | 'retribuzione'
  | 'presenze'
  | 'tfr'
  | 'scatti'
  | 'progressivi';

/** Sezioni nell'ordine del modulo. */
export const SEZIONI_MODULO: readonly SezioneModulo[] = [
  'periodo',
  'dipendente',
  'datore',
  'contratto',
  'totali',
  'voci',
  'ratei',
  'retribuzione',
  'presenze',
  'tfr',
  'scatti',
  'progressivi',
];

const SEZIONE_DI_GRUPPO: Readonly<Record<string, SezioneModulo>> = {
  periodo: 'periodo',
  contieneTredicesima: 'periodo',
  contieneQuattordicesima: 'periodo',
  dipendente: 'dipendente',
  datoreLavoro: 'datore',
  datiContrattuali: 'contratto',
  totali: 'totali',
  voci: 'voci',
  ratei: 'ratei',
  retribuzione: 'retribuzione',
  presenze: 'presenze',
  tfr: 'tfr',
  scatti: 'scatti',
  buoniPasto: 'scatti',
  progressivi: 'progressivi',
};

/** Scelta di persona e azienda: nella sezione dell'anagrafica, ma non contano tra i campi compilati. */
const SEZIONE_DI_SCELTA: Readonly<Record<string, SezioneModulo>> = {
  personaId: 'dipendente',
  nuovaPersona: 'dipendente',
  aziendaId: 'datore',
  nuovaAzienda: 'datore',
};

/** Sezione del modulo che contiene un campo (percorso del modulo o della richiesta); null se sconosciuto. */
export function sezioneDi(percorso: string): SezioneModulo | null {
  const gruppo = /^[a-zA-Z0-9]+/.exec(percorso)?.[0] ?? '';
  return SEZIONE_DI_GRUPPO[gruppo] ?? SEZIONE_DI_SCELTA[gruppo] ?? null;
}

/** Ordina errori o avvisi come i campi nel modulo (le sezioni nell'ordine del modulo, poi l'ordine originale). */
export function ordinaPerModulo<T extends { campo: string | null }>(elementi: readonly T[]): T[] {
  const posizione = (campo: string | null) => {
    const sezione = campo !== null ? sezioneDi(campo) : null;
    return sezione !== null ? SEZIONI_MODULO.indexOf(sezione) : SEZIONI_MODULO.length;
  };
  return elementi
    .map((elemento, indice) => ({ elemento, indice }))
    .sort((a, b) => posizione(a.elemento.campo) - posizione(b.elemento.campo) || a.indice - b.indice)
    .map(({ elemento }) => elemento);
}

function compilato(valore: unknown): boolean {
  return valore !== false && valore !== 'NON_INDICATO' && !vuoto(valore as NumeroModulo | null);
}

/**
 * Campi compilati di una sezione, per l'intestazione: le voci e i ratei contano una volta per riga con dati, gli
 * importi dei buoni pasto solo se presenti.
 */
export function compilatiSezione(valori: ValoriCedolinoManuale, sezione: SezioneModulo): number {
  let conteggio = 0;
  for (const [gruppo, sezioneGruppo] of Object.entries(SEZIONE_DI_GRUPPO)) {
    if (sezioneGruppo !== sezione) {
      continue;
    }
    const valore: unknown = valori[gruppo as keyof ValoriCedolinoManuale];
    if (gruppo === 'voci') {
      conteggio += valori.voci.filter((v) => !voceNonInviata(v)).length;
    } else if (gruppo === 'ratei') {
      conteggio += valori.ratei.filter((r) => !rateoNonInviato(r)).length;
    } else if (gruppo === 'buoniPasto') {
      const { presenti, ...importi } = valori.buoniPasto;
      conteggio += presenti ? 1 + Object.values(importi).filter(compilato).length : 0;
    } else if (typeof valore === 'object' && valore !== null) {
      conteggio += Object.values(valore).filter(compilato).length;
    } else if (compilato(valore)) {
      conteggio += 1;
    }
  }
  return conteggio;
}

/** Titolo con cui il cedolino verrebbe registrato secondo il periodo indicato; null se il periodo e' incompleto. */
export function titoloPrevisto(periodo: ValoriCedolinoManuale['periodo']): string | null {
  const tipo = scelta(periodo.tipo, TIPI_MENSILITA);
  const anno = numeroDa(periodo.anno);
  const mese = periodo.mese !== null ? Number(periodo.mese) : NaN;
  if (tipo === null || anno === null || !Number.isInteger(anno)) {
    return null;
  }
  switch (tipo) {
    case 'TREDICESIMA':
      return formatMensilita(anno, 13);
    case 'QUATTORDICESIMA':
      return formatMensilita(anno, 14);
    case 'ORDINARIA':
      return mese >= 1 && mese <= 12 ? formatMensilita(anno, mese) : null;
    case 'FINE_RAPPORTO':
      return mese >= 1 && mese <= 12
        ? `${testi.manuale.fineRapporto} – ${formatMensilita(anno, mese)}`
        : null;
  }
}

// ------------------------------------------------------------------ precompilamento

const SCELTA_SUPERMINIMO = (assorbibile: boolean | null): SceltaSuperminimo =>
  assorbibile === true ? 'SI' : assorbibile === false ? 'NO' : 'NON_INDICATO';

/**
 * Periodo dalla mensilita' a cui l'utente ha associato il file: ordinaria con mese e anno; tredicesima a dicembre;
 * quattordicesima nel mese di pagamento del CCNL (luglio se non noto); con il flag corrispondente.
 */
export function daAssociazione(
  attribuzione: Pick<AttribuzioneCaricamento, 'competenza'>,
  meseQuattordicesima: number | null = null,
): ValoriParziali {
  const { anno, mese } = attribuzione.competenza;
  if (mese === 13) {
    return { periodo: { tipo: 'TREDICESIMA', mese: '12', anno }, contieneTredicesima: true };
  }
  if (mese === 14) {
    return {
      periodo: { tipo: 'QUATTORDICESIMA', mese: String(meseQuattordicesima ?? 7), anno },
      contieneQuattordicesima: true,
    };
  }
  if (mese !== null && mese >= 1 && mese <= 12) {
    return { periodo: { tipo: 'ORDINARIA', mese: String(mese), anno } };
  }
  return {};
}

/**
 * Dati di un rapporto gia' presente che il cedolino ripete: la data di assunzione (persona e azienda vengono dagli
 * elenchi delle anagrafiche scelte).
 */
export function daRapporto(rapporto: Pick<RapportoDettaglio, 'dataAssunzione'>): ValoriParziali {
  return { datiContrattuali: { dataAssunzione: rapporto.dataAssunzione ?? '' } };
}

function ordinaleMese(anno: number, mese: number): number {
  return anno * 12 + (mese - 1);
}

/**
 * Cedolino di riferimento per copiare i dati che si ripetono: il piu' vicino al periodo tra quelli presenti e non
 * successivi, altrimenti il primo successivo (le mensilita' ordinarie prima delle altre). Senza periodo, il piu'
 * recente.
 */
export function rigaDiRiferimento(
  mensilita: readonly RigaMensilita[],
  periodo: { anno: number; mese: number } | null,
): RigaMensilita | null {
  const presenti = mensilita.filter((r) => r.stato === 'PRESENTE' && r.cedolinoId !== null);
  const ordinarie = presenti.filter((r) => r.competenza.tipo === 'ORDINARIA');
  const candidate = (ordinarie.length > 0 ? ordinarie : presenti).flatMap((riga) => {
    const match = riga.periodoRetribuzione ? /^(\d{4})-(\d{2})$/.exec(riga.periodoRetribuzione) : null;
    return match ? [{ riga, ordinale: ordinaleMese(Number(match[1]), Number(match[2])) }] : [];
  });
  if (candidate.length === 0) {
    return null;
  }
  const obiettivo = periodo ? ordinaleMese(periodo.anno, periodo.mese) : Number.POSITIVE_INFINITY;
  const precedenti = candidate.filter((c) => c.ordinale <= obiettivo);
  const scelta =
    precedenti.length > 0
      ? precedenti.reduce((a, b) => (b.ordinale > a.ordinale ? b : a))
      : candidate.reduce((a, b) => (b.ordinale < a.ordinale ? b : a));
  return scelta.riga;
}

/** Mese (1..12) del periodo indicato nel modulo, per scegliere il cedolino di riferimento; null se incompleto. */
export function periodoDi(periodo: ValoriCedolinoManuale['periodo']): { anno: number; mese: number } | null {
  const anno = numeroDa(periodo.anno);
  const mese = periodo.mese !== null ? Number(periodo.mese) : NaN;
  return anno !== null && Number.isInteger(anno) && mese >= 1 && mese <= 12 ? { anno, mese } : null;
}

const CATEGORIE_DETTAGLIO: readonly [
  keyof Pick<
    CedolinoDettaglio,
    | 'competenze'
    | 'trattenutePrevidenziali'
    | 'trattenuteFiscali'
    | 'altreTrattenute'
    | 'vociNonRetributive'
    | 'vociInformative'
  >,
  CategoriaVoce,
][] = [
  ['competenze', 'COMPETENZA'],
  ['trattenutePrevidenziali', 'TRATTENUTA_PREVIDENZIALE'],
  ['trattenuteFiscali', 'TRATTENUTA_FISCALE'],
  ['altreTrattenute', 'ALTRA_TRATTENUTA'],
  ['vociNonRetributive', 'NON_RETRIBUTIVA'],
  ['vociInformative', 'INFORMATIVA'],
];

function numeroModulo(valore: number | null): NumeroModulo {
  return valore ?? '';
}

/**
 * Voce del cedolino di riferimento senza quantita' e importi, che cambiano ogni mese: resta un modello (non salvato)
 * finche' non la si completa.
 */
function voceDaRiferimento(voce: Voce, categoria: CategoriaVoce): VoceModulo {
  const riga: VoceModulo = {
    ...voceVuota(categoria),
    codice: voce.codice ?? '',
    descrizione: voce.descrizione,
    unita: voce.unita ?? '',
    base: numeroModulo(voce.base),
  };
  return { ...riga, copia: firmaVoce(riga) };
}

/** Rateo del cedolino di riferimento senza i valori del mese: come le voci, un modello finche' non lo si completa. */
function rateoDaRiferimento(rateo: CedolinoDettaglio['ratei'][number], stessoAnno: boolean): RateoModulo {
  const riga: RateoModulo = {
    ...rateoVuoto(rateo.tipo, rateo.unita),
    residuoAnnoPrecedente: stessoAnno ? numeroModulo(rateo.residuoAnnoPrecedente) : '',
  };
  return { ...riga, copia: firmaRateo(riga) };
}

/**
 * Dati che si ripetono da un cedolino dello stesso rapporto: matricola, inquadramento e CCNL stampato, elementi
 * retributivi, voci senza quantita' e importi, ratei senza i valori del mese (voci e ratei restano modelli non salvati
 * finche' non li si completa; il residuo dell'anno precedente e il fondo TFR solo se dello stesso anno), numero di
 * scatti e buoni pasto. La data di cessazione non viene mai copiata.
 */
export function daCedolinoRiferimento(
  cedolino: CedolinoDettaglio,
  annoPeriodo: number | null,
): ValoriParziali {
  const stessoAnno = annoPeriodo !== null && cedolino.competenza.anno === annoPeriodo;
  const e = cedolino.elementiRetributivi;
  const voci: VoceModulo[] = [
    ...CATEGORIE_DETTAGLIO.flatMap(([elenco, categoria]) =>
      cedolino[elenco].map((voce) => voceDaRiferimento(voce, categoria)),
    ),
    ...cedolino.presenze.voci.map((voce) => voceDaRiferimento(voce, 'PRESENZA')),
  ];
  return {
    dipendente: { codiceDipendente: cedolino.inquadramento.codiceDipendente ?? '' },
    datiContrattuali: {
      qualifica: cedolino.inquadramento.qualifica ?? '',
      livello: cedolino.inquadramento.livello ?? '',
      ccnl: cedolino.inquadramento.ccnlTesto ?? '',
    },
    retribuzione: {
      minimo: numeroModulo(e.minimo),
      contingenza: numeroModulo(e.contingenza),
      superminimo: numeroModulo(e.superminimo),
      superminimoAssorbibile: SCELTA_SUPERMINIMO(e.superminimoAssorbibile),
      importoScatti: numeroModulo(e.importoScatti),
      altriElementiFissi: numeroModulo(e.altriElementiFissi),
      retribuzioneBase: numeroModulo(e.retribuzioneBase),
      totale: numeroModulo(e.totale),
      pagaOraria: numeroModulo(e.pagaOraria),
      pagaGiornaliera: numeroModulo(e.pagaGiornaliera),
      divisoreOrario: numeroModulo(e.divisoreOrario),
      divisoreGiornaliero: numeroModulo(e.divisoreGiornaliero),
    },
    voci,
    ratei: cedolino.ratei.map((r) => rateoDaRiferimento(r, stessoAnno)),
    ...(stessoAnno && cedolino.tfr
      ? { tfr: { fondo31DicembreAnnoPrecedente: numeroModulo(cedolino.tfr.fondo31DicembreAnnoPrecedente) } }
      : {}),
    scatti: { numeroScatti: numeroModulo(cedolino.scatti.numeroScatti) },
    buoniPasto: cedolino.buoniPasto.presenti
      ? { presenti: true, valoreUnitario: numeroModulo(cedolino.buoniPasto.valoreUnitario) }
      : {},
  };
}

function valoreVuoto(valore: unknown): boolean {
  if (Array.isArray(valore)) {
    return valore.length === 0;
  }
  return (
    valore === null ||
    valore === undefined ||
    valore === false ||
    valore === 'NON_INDICATO' ||
    (typeof valore === 'string' && valore.trim() === '')
  );
}

function righePiene(valore: unknown): boolean {
  return (
    Array.isArray(valore) &&
    valore.some((riga: VoceModulo | RateoModulo) =>
      'categoria' in riga ? !voceNonInviata(riga) : !rateoNonInviato(riga),
    )
  );
}

function unisci(
  destinazione: Record<string, unknown>,
  origine: Record<string, unknown>,
  prefisso: string,
  copiati: string[],
): Record<string, unknown> {
  const risultato: Record<string, unknown> = { ...destinazione };
  for (const [campo, nuovo] of Object.entries(origine)) {
    const percorso = prefisso ? `${prefisso}.${campo}` : campo;
    const attuale = destinazione[campo];
    if (Array.isArray(nuovo)) {
      // una lista si copia per intero solo se nel modulo non c'e' ancora nessuna riga compilata
      if (nuovo.length > 0 && !righePiene(attuale)) {
        risultato[campo] = nuovo;
        copiati.push(percorso);
      }
    } else if (typeof nuovo === 'object' && nuovo !== null) {
      if (typeof attuale === 'object' && attuale !== null && !Array.isArray(attuale)) {
        risultato[campo] = unisci(
          attuale as Record<string, unknown>,
          nuovo as Record<string, unknown>,
          percorso,
          copiati,
        );
      }
    } else if (valoreVuoto(attuale) && !valoreVuoto(nuovo)) {
      risultato[campo] = nuovo;
      copiati.push(percorso);
    }
  }
  return risultato;
}

/**
 * Compila con i dati di `origine` solo i campi vuoti del modulo (le liste solo se non hanno righe compilate) e
 * indica i percorsi compilati, da segnalare all'utente come copiati.
 */
export function unisciSeVuoti(
  valori: ValoriCedolinoManuale,
  origine: ValoriParziali,
): { valori: ValoriCedolinoManuale; copiati: string[] } {
  const copiati: string[] = [];
  const risultato = unisci(valori as unknown as Record<string, unknown>, origine, '', copiati);
  return { valori: risultato as unknown as ValoriCedolinoManuale, copiati };
}
