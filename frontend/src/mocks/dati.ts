import type {
  AziendaSintesi,
  Caricamento,
  CedolinoDettaglio,
  Ccnl,
  Competenza,
  PersonaSintesi,
  RapportoDettaglio,
  RateoCedolino,
  Riepilogo,
  RigaMensilita,
  MotivoErrore,
  Segnalazione,
  TipoRateo,
  UnitaMisura,
  Voce,
} from '../api/types';
import { formatMese } from '../format/format';

/**
 * Dati INVENTATI per lo sviluppo senza backend e per i test. Nessun dato personale reale.
 * Gli importi sono coerenti tra loro solo quanto basta a rendere credibile l'interfaccia.
 */

/** Copia della tabella `ccnl` reale (changelog 2xx): stessi codici, descrizioni, valori e note. */
export const CCNL_MOCK: Ccnl[] = [
  {
    codice: 'ALTRO_13',
    descrizione: 'Altro CCNL (13 mensilità)',
    mensilita: 13,
    periodicitaScattiMesi: null,
    maxScatti: null,
    meseQuattordicesima: null,
    noteVerifica: 'Voce generica per CCNL non presenti in tabella: nessuna regola sugli scatti.',
  },
  {
    codice: 'ALTRO_14',
    descrizione: 'Altro CCNL (14 mensilità)',
    mensilita: 14,
    periodicitaScattiMesi: null,
    maxScatti: null,
    meseQuattordicesima: 7,
    noteVerifica:
      'Voce generica per CCNL non presenti in tabella: quattordicesima assunta a luglio, nessuna regola sugli scatti.',
  },
  {
    codice: 'COMMERCIO',
    descrizione: 'Commercio - Terziario, distribuzione e servizi (Confcommercio)',
    mensilita: 14,
    periodicitaScattiMesi: 36,
    maxScatti: 10,
    meseQuattordicesima: 7,
    noteVerifica: null,
  },
  {
    codice: 'COMMERCIO_CONFESERCENTI',
    descrizione: 'Commercio - Terziario, distribuzione e servizi (Confesercenti)',
    mensilita: 14,
    periodicitaScattiMesi: 36,
    maxScatti: 10,
    meseQuattordicesima: 7,
    noteVerifica: null,
  },
  {
    codice: 'DISTRIBUZIONE_MODERNA_ORGANIZZATA',
    descrizione: 'Distribuzione moderna organizzata - DMO (Federdistribuzione)',
    mensilita: 14,
    periodicitaScattiMesi: 36,
    maxScatti: 10,
    meseQuattordicesima: 7,
    noteVerifica: null,
  },
  {
    codice: 'DISTRIBUZIONE_COOPERATIVA',
    descrizione: 'Distribuzione cooperativa - Cooperative di consumo (ANCC-Coop, Confcooperative, AGCI)',
    mensilita: 14,
    periodicitaScattiMesi: null,
    maxScatti: 10,
    meseQuattordicesima: 6,
    noteVerifica:
      'Scatti: 10 triennali, ma secondo la sintesi Filcams maturano "dal 21° anno di età". Il calcolo parte dalla data di assunzione e sbaglierebbe per chi è stato assunto prima dei 21 anni, quindi la periodicità non è impostata. Va verificato sul testo integrale se la regola vale ancora per tutti o, come nel CCNL DMO (art. 188), solo per gli assunti prima del 1987.',
  },
  {
    codice: 'FARMACIE_PRIVATE',
    descrizione: 'Farmacie private - Dipendenti da farmacia privata (Federfarma)',
    mensilita: 14,
    periodicitaScattiMesi: 24,
    maxScatti: 15,
    meseQuattordicesima: 7,
    noteVerifica: null,
  },
  {
    codice: 'DIRIGENTI_TERZIARIO',
    descrizione:
      'Dirigenti commercio - Dirigenti di aziende del terziario, distribuzione e servizi (Confcommercio-Manageritalia)',
    mensilita: 14,
    periodicitaScattiMesi: null,
    maxScatti: null,
    meseQuattordicesima: 6,
    noteVerifica:
      "Scatti di anzianità aboliti dal 1° luglio 2004 (art. 9). Restano solo, in via transitoria, per i dirigenti in servizio al 30 giugno 2004, quindi periodicità e massimo non sono impostati. Dal 1° gennaio 2026 vale l'ipotesi di rinnovo del 5 novembre 2025: verificare che gli artt. 8-9 non siano cambiati.",
  },
  {
    codice: 'METALMECCANICO_INDUSTRIA',
    descrizione:
      'Metalmeccanici - Industria metalmeccanica e installazione di impianti (Federmeccanica-Assistal)',
    mensilita: 13,
    periodicitaScattiMesi: 24,
    maxScatti: 5,
    meseQuattordicesima: null,
    noteVerifica:
      'Valori verificati: 13 mensilità, 5 aumenti biennali. Norma transitoria: i lavoratori in forza al 16/07/1979 possono maturare fino a 12 aumenti periodici (fonte pmi.it); per questi casi rari il limite di 5 non vale. Esclusi i contratti fratelli (artigianato area meccanica, piccola industria Confapi/Confimi, contratti Confsal-Conflavoro/CIFA).',
  },
  {
    codice: 'METALMECCANICO_PMI',
    descrizione:
      'Metalmeccanici - Piccola e media industria metalmeccanica, orafa e installazione impianti (Unionmeccanica-Confapi)',
    mensilita: 13,
    periodicitaScattiMesi: 24,
    maxScatti: 5,
    meseQuattordicesima: null,
    noteVerifica:
      "Valori verificati: 5 aumenti biennali (art. 41), 13 mensilità. Il CCNL contiene norme transitorie per i lavoratori con elevata anzianità aziendale maturata prima dell'armonizzazione (dal 1/3/2008): per loro gli importi possono differire. 'Piccola industria' o 'p.ind' da soli non bastano: esiste anche il CCNL Confimi.",
  },
  {
    codice: 'METALMECCANICO_ARTIGIANATO',
    descrizione:
      'Metalmeccanici - Artigianato area meccanica, metalmeccanica e installazione impianti (Confartigianato, CNA, Casartigiani, CLAAI)',
    mensilita: 13,
    periodicitaScattiMesi: 24,
    maxScatti: 5,
    meseQuattordicesima: null,
    noteVerifica:
      "Due regimi di calcolo degli scatti in base alla data di maturazione del primo scatto (27/11/1997): cambia l'importo, non la periodicità biennale né il massimo di 5. Per gli operai la tredicesima è una gratifica natalizia di 173 ore. Gli apprendisti maturano gli scatti solo dal 1/1/2025 (rinnovo 2024). Lo stesso CCNL (C030) copre anche orafi e odontotecnici artigiani (anche per loro massimo 5 bienni), che però qui non vengono riconosciuti dalle parole chiave.",
  },
  {
    codice: 'ALIMENTARI_INDUSTRIA',
    descrizione: 'Alimentari - Industria alimentare (Federalimentare e associazioni di settore)',
    mensilita: 14,
    periodicitaScattiMesi: 24,
    maxScatti: 5,
    meseQuattordicesima: null,
    noteVerifica:
      "Valori verificati sul testo contrattuale: 14 mensilità (art. 54), 5 aumenti biennali (art. 53, gli scatti maturati prima del 1/6/1995 contano nel massimo). Mese della quattordicesima non impostato: il CCNL non lo fissa; nella prassi è luglio. Per alcune piccole aziende (Assobibe, Mineracqua) è ammesso il pagamento a rate di 13ª e 14ª entro 2 mesi dalla scadenza. Escluso anche 'unionalimentari' (CCNL piccola industria Confapi).",
  },
  {
    codice: 'TESSILE_ABBIGLIAMENTO_MODA_INDUSTRIA',
    descrizione: 'Tessile, abbigliamento, moda - Industria (Confindustria Moda - Federazione Tessile e Moda)',
    mensilita: 13,
    periodicitaScattiMesi: 24,
    maxScatti: 4,
    meseQuattordicesima: null,
    noteVerifica:
      "Valori verificati: 13 mensilità, 4 aumenti biennali (art. 46; confermato da sintesi MySolution e documento aziendale Thaler; una sola fonte, leggeinchiaro, riporta 5). In caso di passaggio di livello si mantiene l'importo maturato e si possono maturare ulteriori bienni fino al massimo del nuovo livello. Per gli operai gli scatti non incidono su cottimi e incentivi. Codice D014 confermato dal database CNEL-INPS.",
  },
  {
    codice: 'GOMMA_PLASTICA_INDUSTRIA',
    descrizione:
      'Gomma plastica - Industria della gomma, cavi elettrici ed affini e delle materie plastiche (Federazione Gomma Plastica)',
    mensilita: 13,
    periodicitaScattiMesi: 24,
    maxScatti: 5,
    meseQuattordicesima: null,
    noteVerifica:
      "Valori verificati: 13 mensilità, massimo 5 aumenti biennali. In caso di passaggio di livello l'importo degli scatti maturati viene riproporzionato sul valore dello scatto del nuovo livello (frazioni di scatto): il numero di scatti in busta può non essere intero. Escluso il testo con 'chimica' per non confondersi con il CCNL Confapi chimica-gomma-plastica e con i contratti artigiani.",
  },
  {
    codice: 'LEGNO_ARREDAMENTO_INDUSTRIA',
    descrizione:
      'Legno e arredamento - Industria del legno, sughero, mobile e arredamento e boschiva e forestale (FederlegnoArredo)',
    mensilita: 13,
    periodicitaScattiMesi: 24,
    maxScatti: 5,
    meseQuattordicesima: null,
    noteVerifica:
      'Valori verificati: 13 mensilità, massimo 5 aumenti biennali (art. 40). Il CCNL F051 copre anche i settori boschivi e forestali, quindi "boschivi" e "forestali" non escludono il riconoscimento; resta esclusa la sistemazione "idraulico forestale" (contratto agricolo). Alcuni accordi territoriali (es. distretti del mobile di Brianza e Pesaro) prevedono una somma annua aggiuntiva simile a una quattordicesima, non prevista dal CCNL nazionale. Dal 1/1/2027 gli importi degli scatti aumentano di circa il 20%.',
  },
  {
    codice: 'CHIMICO_FARMACEUTICO_INDUSTRIA',
    descrizione:
      'Chimici - Industria chimica, chimico-farmaceutica, fibre chimiche, abrasivi, lubrificanti e GPL (Federchimica-Farmindustria)',
    mensilita: 13,
    periodicitaScattiMesi: null,
    maxScatti: null,
    meseQuattordicesima: null,
    noteVerifica:
      "Mensilità verificate: 13. Gli scatti di anzianità non maturano più: gli importi già maturati sono congelati in un superminimo non assorbibile, quindi nessuna regola sugli scatti. Eccezione: il settore Lubrificanti e GPL (stesso CCNL e stesso codice CNEL) prevede una quattordicesima a fine giugno, che il modello a 13 mensilità non rappresenta: per quei lavoratori una 14ª in busta non è un'anomalia.",
  },
  {
    codice: 'DIRIGENTI_INDUSTRIA',
    descrizione:
      'Dirigenti industria - Dirigenti di aziende produttrici di beni e servizi (Confindustria-Federmanager)',
    mensilita: 13,
    periodicitaScattiMesi: null,
    maxScatti: null,
    meseQuattordicesima: null,
    noteVerifica:
      "Mensilità: 13, ma il CCNL ammette un numero maggiore se previsto in azienda, quindi una 14ª in busta non è per forza un'anomalia. Scatti aboliti dall'accordo 24/11/2004: solo i dirigenti in servizio a quella data maturano ancora 129,11 euro per biennio, fino a 10 aumenti complessivi; il valore dipende dalla data di assunzione e resta non impostato. 'Federmanager' da solo non basta, perché firma anche il CCNL dirigenti PMI Confapi e altri.",
  },
  {
    codice: 'TURISMO',
    descrizione: 'Turismo - Alberghi e pubblici esercizi (Federalberghi-Faita, Fipe - Confcommercio)',
    mensilita: 14,
    periodicitaScattiMesi: null,
    maxScatti: null,
    meseQuattordicesima: 7,
    noteVerifica:
      'La voce raggruppa due CCNL con scatti diversi: Turismo Federalberghi-Faita (scatti triennali) e Pubblici esercizi Fipe dal 2018 (sei scatti quadriennali). Periodicità e massimo non impostati. Quattordicesima con la retribuzione di luglio in entrambi. Il codice CNEL H052 riconosce solo il CCNL Federalberghi; il CCNL Fipe (H05Y) si riconosce dalle parole chiave. Un testo con "commercio" o "terziario" (es. "Commercio e turismo") non viene riconosciuto; "Turismo Confcommercio" sì.',
  },
  {
    codice: 'MULTISERVIZI',
    descrizione: 'Multiservizi - Servizi di pulizia e servizi integrati',
    mensilita: 14,
    periodicitaScattiMesi: null,
    maxScatti: null,
    meseQuattordicesima: null,
    noteVerifica:
      'Scatti diversi per categoria: impiegati 8 scatti biennali, operai una sola quota fissa di anzianità dal quinto anno. Periodicità e massimo non impostati. Mese della quattordicesima non impostato: il CCNL prevede il pagamento "entro il 15 luglio", quindi con il cedolino di giugno o di luglio. Codice CNEL non impostato (K511 o K512 secondo la fonte).',
  },
  {
    codice: 'COOPERATIVE_SOCIALI',
    descrizione:
      'Cooperative sociali - Settore socio-sanitario, assistenziale, educativo e di inserimento lavorativo',
    mensilita: 13,
    periodicitaScattiMesi: 24,
    maxScatti: 5,
    meseQuattordicesima: null,
    noteVerifica:
      'Dal 1° gennaio 2025 il CCNL prevede mezza quattordicesima, pagata con le spettanze di giugno. Il modello prevede solo mensilità intere e non gestisce un istituto che decorre da una data, quindi la voce è impostata a 13 mensilità: la mezza quattordicesima non è controllata e non entra nella RAL. Parola chiave al plurale "cooperative sociali" per non riconoscere il CCNL dal nome di un\'azienda "cooperativa sociale" che applica un altro contratto.',
  },
  {
    codice: 'VIGILANZA_PRIVATA',
    descrizione: 'Vigilanza privata e servizi fiduciari',
    mensilita: 14,
    periodicitaScattiMesi: null,
    maxScatti: null,
    meseQuattordicesima: null,
    noteVerifica:
      'Un unico CCNL con due sezioni. Vigilanza: 6 scatti triennali, quattordicesima entro il 15 luglio (fonte unica). Servizi fiduciari: regole proprie sugli scatti e quattordicesima introdotta in modo progressivo solo dal 2024, quindi per gli anni precedenti le 14 mensilità non valgono. Periodicità, massimo e mese di pagamento della quattordicesima non impostati. Codice CNEL HV17 non accettato dal riconoscitore.',
  },
  {
    codice: 'LOGISTICA_TRASPORTO_MERCI',
    descrizione: 'Logistica, trasporto merci e spedizione',
    mensilita: 14,
    periodicitaScattiMesi: null,
    maxScatti: null,
    meseQuattordicesima: null,
    noteVerifica:
      'Scatti: 5 aumenti biennali (art. 17), ma per i nuovi assunti la maturazione parte dal 4° anno (art. 4 bis) e Magazzini generali e Industria del freddo hanno regole proprie. Periodicità e massimo non impostati. Mese della quattordicesima non impostato: il CCNL fissa il pagamento entro la prima decade di luglio, quindi con il cedolino di giugno o di luglio. La sezione artigiana è esclusa dal riconoscimento.',
  },
  {
    codice: 'IGIENE_AMBIENTALE',
    descrizione: 'Igiene ambientale - Servizi ambientali (Utilitalia, Cisambiente, Assoambiente)',
    mensilita: 14,
    periodicitaScattiMesi: null,
    maxScatti: null,
    meseQuattordicesima: null,
    noteVerifica:
      'Scatti triennali (art. 30) convertibili, su opzione del lavoratore, in previdenza complementare; numero massimo non verificato. Periodicità e massimo non impostati. Mese della quattordicesima non impostato: il CCNL la prevede tra il 15 giugno e il 15 luglio. Codice CNEL non trovato. La sigla Utilitalia non è tra le parole chiave perché firma anche i CCNL gas-acqua ed elettrico.',
  },
  {
    codice: 'AUTOFERROTRANVIERI',
    descrizione: 'Autoferrotranvieri - Internavigatori (Mobilità/TPL)',
    mensilita: 14,
    periodicitaScattiMesi: 24,
    maxScatti: 6,
    meseQuattordicesima: 7,
    noteVerifica: null,
  },
  {
    codice: 'LAVORO_DOMESTICO',
    descrizione: 'Lavoro domestico - Colf e badanti (Fidaldo-Domina)',
    mensilita: 13,
    periodicitaScattiMesi: 24,
    maxScatti: 7,
    meseQuattordicesima: null,
    noteVerifica: null,
  },
  {
    codice: 'STUDI_PROFESSIONALI',
    descrizione: 'Studi professionali (Confprofessioni)',
    mensilita: 14,
    periodicitaScattiMesi: 36,
    maxScatti: 8,
    meseQuattordicesima: null,
    noteVerifica:
      'Mese della quattordicesima non impostato: il CCNL la prevede in coincidenza con le ferie e non oltre il 30 giugno (di norma giugno, ma il mese non è fissato).',
  },
  {
    codice: 'SANITA_PRIVATA_AIOP',
    descrizione: 'Sanità privata - Case di cura private, personale non medico (AIOP-ARIS)',
    mensilita: 13,
    periodicitaScattiMesi: null,
    maxScatti: null,
    meseQuattordicesima: null,
    noteVerifica:
      'Il CCNL AIOP-ARIS non medici non prevede scatti periodici: l\'anzianità pregressa è in una RIA ad personam (art. 56) e la progressione avviene per posizioni economiche (art. 51). Periodicità e massimo non impostati. Il CCNL AIOP RSA e centri di riabilitazione è distinto ed è escluso. "cisal" non è tra le parole escluse perché Cisal Sanità ha aderito a questo CCNL.',
  },
  {
    codice: 'CREDITO',
    descrizione: 'Credito - Bancari: aree professionali e quadri direttivi (ABI)',
    mensilita: 13,
    periodicitaScattiMesi: null,
    maxScatti: null,
    meseQuattordicesima: null,
    noteVerifica:
      "Scatti di anzianità non impostati: il primo matura dopo 4 anni e i successivi ogni 3. Il massimo è 8 per aree professionali e QD 1°-2° livello e 7 per QD 3°-4° livello (12 per chi era in servizio al 19/12/1994). Codice CNEL (probabilmente J241) da verificare sull'archivio CNEL.",
  },
  {
    codice: 'ASSICURAZIONI',
    descrizione: 'Assicurazioni - Imprese assicuratrici, personale non dirigente (ANIA)',
    mensilita: 14,
    periodicitaScattiMesi: null,
    maxScatti: null,
    meseQuattordicesima: null,
    noteVerifica:
      "Scatti non impostati: il CCNL ANIA usa classi di anzianità nelle tabelle retributive, non scatti a cadenza fissa. Mese della quattordicesima non impostato: ilccnl.it riporta il 15 giugno dal testo contrattuale, ma manca una seconda fonte indipendente e Money.it indica luglio. Codice CNEL (probabilmente J121) da verificare sull'archivio CNEL. Esclusi i CCNL delle agenzie in gestione libera (SNA, UNAPASS, ANAPA) e dei broker (AIBA).",
  },
  {
    codice: 'ELETTRICO',
    descrizione:
      'Elettrici - Settore elettrico (Elettricità Futura, Utilitalia, Enel, Terna, GSE, Sogin, Energia Libera)',
    mensilita: 14,
    periodicitaScattiMesi: null,
    maxScatti: null,
    meseQuattordicesima: 6,
    noteVerifica:
      "Scatti non impostati. Chi è stato assunto prima del 2020 matura 5 aumenti biennali, il primo al compimento del primo biennio. Per gli assunti dal 1° gennaio 2020 gli scatti sono sostituiti da versamenti periodici al fondo pensione (art. 46), che non compaiono come scatto in busta. Codice CNEL (probabilmente K051) da verificare sull'archivio CNEL.",
  },
  {
    codice: 'GAS_ACQUA',
    descrizione: 'Gas-acqua (Utilitalia, Anigas, Assogas, Igas, Anfida, Proxigas)',
    mensilita: 14,
    periodicitaScattiMesi: null,
    maxScatti: null,
    meseQuattordicesima: 6,
    noteVerifica:
      "Scatti di anzianità non impostati: sono congelati all'importo maturato al 31/12/2015, pagato come elemento ad personam, e non ne maturano di nuovi. Codice CNEL (probabilmente K321) da verificare sull'archivio CNEL.",
  },
  {
    codice: 'TELECOMUNICAZIONI',
    descrizione: 'Telecomunicazioni - Imprese esercenti servizi di telecomunicazione (Asstel)',
    mensilita: 13,
    periodicitaScattiMesi: 24,
    maxScatti: 7,
    meseQuattordicesima: null,
    noteVerifica: "Codice CNEL (probabilmente K411) da verificare sull'archivio CNEL.",
  },
  {
    codice: 'EDILIZIA_INDUSTRIA',
    descrizione: 'Edilizia - Industria (ANCE, Legacoop, Confcooperative, AGCI)',
    mensilita: 13,
    periodicitaScattiMesi: null,
    maxScatti: null,
    meseQuattordicesima: null,
    noteVerifica:
      "Le regole dipendono dalla categoria. Impiegati: 14 mensilità (premio annuo entro il 30 giugno) e 5 scatti biennali. Operai: tredicesima e ferie accantonate presso la Cassa Edile e anzianità tramite APE invece degli scatti in busta. Mensilità impostata a 13 e scatti non impostati: le regole su mensilità mancanti vanno applicate con cautela. Il codice F012 copre l'unico CCNL ANCE-Cooperative, ma un cedolino con «cooperative» non viene riconosciuto qui per prudenza.",
  },
  {
    codice: 'EDILIZIA_ARTIGIANATO',
    descrizione:
      "Edilizia - Artigianato e PMI: imprese artigiane e piccole imprese industriali dell'edilizia (ANAEPA Confartigianato, CNA Costruzioni, Casartigiani, CLAAI)",
    mensilita: 13,
    periodicitaScattiMesi: null,
    maxScatti: null,
    meseQuattordicesima: null,
    noteVerifica:
      'Le regole dipendono dalla categoria. Impiegati: 14 mensilità (premio annuo entro il 30 giugno) e 5 scatti biennali. Operai: tredicesima pagata dalla Cassa Edile e anzianità tramite APE. Mensilità impostata a 13 e scatti non impostati: le regole su mensilità mancanti vanno applicate con cautela. «Edilizia PMI» da solo non è una parola chiave perché è ambiguo con il CCNL Confapi-ANIEM (F018). Esclusi i CCNL edili artigiani e PMI firmati da altre sigle (es. Confinnova-Cisal).',
  },
];

export function trovaCcnl(codice: string): Ccnl | undefined {
  return CCNL_MOCK.find((c) => c.codice === codice);
}

// ------------------------------------------------------------------ anagrafiche inventate

const MARIO: PersonaSintesi = {
  id: 1,
  nome: 'Mario',
  cognome: 'Rossi',
  codiceFiscale: 'RSSMRA80A01H501U',
  dataNascita: '1980-01-01',
  etichetta: 'Mario Rossi',
};

const LAURA: PersonaSintesi = {
  id: 2,
  nome: 'Laura',
  cognome: 'Bianchi',
  codiceFiscale: 'BNCLRA85M41F205X',
  dataNascita: '1985-08-01',
  etichetta: 'Laura Bianchi',
};

const ALFA: AziendaSintesi = {
  id: 1,
  ragioneSociale: 'Alfa Software S.r.l.',
  partitaIva: '01234567897',
  codiceFiscale: '01234567897',
  posizioneInps: '1234567890',
  indirizzo: 'Via Roma 1, 00100 Roma',
};

const BETA: AziendaSintesi = {
  id: 2,
  ragioneSociale: 'Beta Consulting S.p.A.',
  partitaIva: '09876543217',
  codiceFiscale: '09876543217',
  posizioneInps: '0987654321',
  indirizzo: 'Corso Italia 10, 20100 Milano',
};

const GAMMA: AziendaSintesi = {
  id: 3,
  ragioneSociale: 'Gamma Servizi S.r.l.',
  partitaIva: '11223344554',
  codiceFiscale: null,
  posizioneInps: null,
  indirizzo: null,
};

// ------------------------------------------------------------------ utilità

/** Pseudo-casuale deterministico in [0, 1) per variare gli importi senza perdere la riproducibilità. */
function rumore(seme: number): number {
  const x = Math.sin(seme * 9301 + 49297) * 233280;
  return x - Math.floor(x);
}

function arrotonda(valore: number): number {
  return Math.round(valore * 100) / 100;
}

export function mese(anno: number, m: number): string {
  return `${anno}-${String(m).padStart(2, '0')}`;
}

/** Mesi da `da` ad `a` inclusi, come coppie [anno, mese]. */
function intervalloMesi(da: [number, number], a: [number, number]): [number, number][] {
  const risultato: [number, number][] = [];
  let [anno, m] = da;
  while (anno < a[0] || (anno === a[0] && m <= a[1])) {
    risultato.push([anno, m]);
    m += 1;
    if (m > 12) {
      m = 1;
      anno += 1;
    }
  }
  return risultato;
}

export function competenzaOrdinaria(anno: number, m: number): Competenza {
  return {
    chiave: `${anno}.${String(m).padStart(2, '0')}`,
    tipo: 'ORDINARIA',
    anno,
    mese: m,
    progressivoTfr: null,
  };
}

export function competenzaSpeciale(anno: number, tipo: 'TREDICESIMA' | 'QUATTORDICESIMA'): Competenza {
  const m = tipo === 'TREDICESIMA' ? 13 : 14;
  return { chiave: `${anno}.${m}`, tipo, anno, mese: m, progressivoTfr: null };
}

export function competenzaFineRapporto(anno: number, progressivo: number): Competenza {
  return {
    chiave: `${anno}.TFR${String(progressivo).padStart(2, '0')}`,
    tipo: 'FINE_RAPPORTO',
    anno,
    mese: null,
    progressivoTfr: progressivo,
  };
}

export function titoloCompetenza(competenza: Competenza, periodo: string | null): string {
  switch (competenza.tipo) {
    case 'ORDINARIA':
      return formatMese(mese(competenza.anno, competenza.mese ?? 1));
    case 'TREDICESIMA':
      return `Tredicesima ${competenza.anno}`;
    case 'QUATTORDICESIMA':
      return `Quattordicesima ${competenza.anno}`;
    case 'FINE_RAPPORTO':
      return `Fine rapporto ${competenza.progressivoTfr ?? 1} – ${formatMese(periodo)}`;
  }
}

function voce(descrizione: string, valori: Partial<Voce>): Voce {
  return {
    codice: null,
    descrizione,
    quantita: null,
    unita: null,
    base: null,
    importoCompetenza: null,
    importoTrattenuta: null,
    ...valori,
  };
}

// ------------------------------------------------------------------ generazione cedolini

interface ProfiloRapporto {
  rapportoId: number;
  personaId: number;
  aziendaId: number;
  qualifica: string;
  livello: string;
  ccnlTesto: string | null;
  dataAssunzione: string;
  dataCessazione: string | null;
  /** Lordo mensile contrattuale in funzione del mese (gli aumenti cambiano gli elementi). */
  elementi: (
    anno: number,
    m: number,
  ) => { minimo: number; contingenza: number; superminimo: number; scatti: number };
  numeroScatti: (anno: number, m: number) => number;
  buoniPasto: boolean;
  ratei: { tipo: TipoRateo; unita: UnitaMisura; maturatoMese: number }[];
  /** Avvisi della lettura riportati su ogni cedolino del rapporto. */
  avvisiEstrazione: string[];
}

interface Contatori {
  prossimoIdCedolino: number;
  prossimoIdCaricamento: number;
  /** Persone, aziende e rapporti creati dall'inserimento a mano (lontani dagli id dei dati dimostrativi). */
  prossimoIdAnagrafica: number;
}

function generaCedolino(
  profilo: ProfiloRapporto,
  competenza: Competenza,
  periodo: [number, number],
  contatori: Contatori,
): CedolinoDettaglio {
  const id = contatori.prossimoIdCedolino++;
  const [anno, m] = periodo;
  const seme = anno * 100 + m + (competenza.mese ?? 20) + profilo.rapportoId * 7;
  const e = profilo.elementi(anno, m);
  const totaleFisso = arrotonda(e.minimo + e.contingenza + e.superminimo + e.scatti);
  const ordinaria = competenza.tipo === 'ORDINARIA';
  const fineRapporto = competenza.tipo === 'FINE_RAPPORTO';

  const competenze: Voce[] = [];
  if (ordinaria) {
    competenze.push(
      voce('Retribuzione ordinaria', {
        codice: '0001',
        quantita: 26,
        unita: 'gg',
        importoCompetenza: totaleFisso,
      }),
    );
    if (rumore(seme) > 0.6) {
      const ore = Math.round(rumore(seme + 1) * 10) + 2;
      const paga = arrotonda((totaleFisso / 168) * 1.25);
      competenze.push(
        voce('Straordinario 25%', {
          codice: '0250',
          quantita: ore,
          unita: 'ore',
          base: paga,
          importoCompetenza: arrotonda(ore * paga),
        }),
      );
    }
  } else if (competenza.tipo === 'TREDICESIMA') {
    competenze.push(
      voce('13ma Mensilità', {
        codice: '0130',
        quantita: 12,
        unita: 'ratei',
        importoCompetenza: totaleFisso,
      }),
    );
  } else if (competenza.tipo === 'QUATTORDICESIMA') {
    competenze.push(
      voce('14ma Mensilità', {
        codice: '0140',
        quantita: 12,
        unita: 'ratei',
        importoCompetenza: totaleFisso,
      }),
    );
  } else {
    competenze.push(
      voce('Ferie non godute', {
        codice: '0410',
        quantita: 6.5,
        unita: 'gg',
        base: arrotonda(totaleFisso / 26),
        importoCompetenza: arrotonda((totaleFisso / 26) * 6.5),
      }),
      voce('Rateo 13ma Mensilità', {
        codice: '0131',
        quantita: 2,
        unita: 'ratei',
        importoCompetenza: arrotonda((totaleFisso / 12) * 2),
      }),
      voce('Indennità sostitutiva del preavviso', {
        codice: '0420',
        importoCompetenza: arrotonda(totaleFisso * 0.5),
      }),
    );
  }

  const lordo = arrotonda(competenze.reduce((s, v) => s + (v.importoCompetenza ?? 0), 0));
  const inps = arrotonda(lordo * 0.0919);
  const imponibileIrpef = lordo - inps;
  const irpef = arrotonda(imponibileIrpef * (fineRapporto ? 0.23 : 0.24));
  const detrazioni = ordinaria ? arrotonda(110 + rumore(seme + 3) * 20) : 0;
  const addizionali = ordinaria ? arrotonda(imponibileIrpef * 0.0223) : 0;

  const trattenutePrevidenziali = [
    voce('Contributo IVS a carico dipendente', {
      codice: 'C001',
      base: lordo,
      quantita: 9.19,
      unita: '%',
      importoTrattenuta: inps,
    }),
  ];
  const trattenuteFiscali = [
    voce('IRPEF lorda', { codice: 'F001', base: arrotonda(imponibileIrpef), importoTrattenuta: irpef }),
    ...(detrazioni > 0
      ? [voce('Detrazioni lavoro dipendente', { codice: 'F010', importoCompetenza: detrazioni })]
      : []),
    ...(addizionali > 0
      ? [
          voce('Addizionale regionale', { codice: 'F020', importoTrattenuta: arrotonda(addizionali * 0.7) }),
          voce('Addizionale comunale', { codice: 'F021', importoTrattenuta: arrotonda(addizionali * 0.3) }),
        ]
      : []),
  ];
  const altreTrattenute =
    ordinaria && profilo.rapportoId === 1
      ? [voce('Quota associativa sindacale', { codice: 'T100', importoTrattenuta: 12 })]
      : [];

  const vociNonRetributive: Voce[] = [];
  const numeroBuoni = ordinaria && profilo.buoniPasto ? 18 + Math.round(rumore(seme + 5) * 4) : 0;
  if (numeroBuoni > 0) {
    vociNonRetributive.push(
      voce('Buoni pasto elettronici', {
        codice: 'N001',
        quantita: numeroBuoni,
        unita: 'n.',
        base: 8,
        importoCompetenza: arrotonda(numeroBuoni * 8),
      }),
    );
  }
  if (ordinaria && rumore(seme + 7) > 0.7) {
    const km = Math.round(rumore(seme + 8) * 300) + 40;
    vociNonRetributive.push(
      voce('Rimborso chilometrico', {
        codice: 'N010',
        quantita: km,
        unita: 'km',
        base: 0.42,
        importoCompetenza: arrotonda(km * 0.42),
      }),
    );
  }

  const totaleTrattenute = arrotonda(
    [...trattenutePrevidenziali, ...trattenuteFiscali, ...altreTrattenute].reduce(
      (s, v) => s + (v.importoTrattenuta ?? 0) - (v.importoCompetenza ?? 0),
      0,
    ),
  );
  const nonRetributivo = arrotonda(vociNonRetributive.reduce((s, v) => s + (v.importoCompetenza ?? 0), 0));
  const nettoGrezzo = lordo - totaleTrattenute + nonRetributivo;
  const netto = Math.round(nettoGrezzo);
  const arrotondamento = arrotonda(netto - nettoGrezzo);

  const meseIndice = (anno - 2020) * 12 + m;
  const ratei: RateoCedolino[] = profilo.ratei.map((r, i) => {
    const maturato = arrotonda(r.maturatoMese * m);
    const residuo = r.unita === 'GIORNI' ? 6 + i : 12 + i * 4;
    const goduto = arrotonda(
      Math.min(maturato + residuo - 1, maturato * (0.4 + rumore(meseIndice + i) * 0.4)),
    );
    return {
      tipo: r.tipo,
      unita: r.unita,
      residuoAnnoPrecedente: residuo,
      maturato,
      goduto,
      saldo: fineRapporto ? 0 : arrotonda(residuo + maturato - goduto),
    };
  });

  const quotaMese = arrotonda(lordo / 13.5);
  const fondoPrecedente = arrotonda(1800 + (anno - 2018) * 2350);
  const tfr = {
    fondo31DicembreAnnoPrecedente: fondoPrecedente,
    rivalutazione: arrotonda(fondoPrecedente * 0.0021 * m),
    impostaSostitutiva: arrotonda(fondoPrecedente * 0.0021 * m * 0.17),
    quotaAnno: arrotonda(quotaMese * m),
    quotaMese,
    quotaFondi: profilo.rapportoId === 1 ? arrotonda(quotaMese * 0.35) : 0,
    quotaBusta: 0,
    anticipi: 0,
    retribuzioneUtile: lordo,
    liquidatoLordo: fineRapporto ? 9870 : null,
    ritenuta: fineRapporto ? 2170.4 : null,
  };

  return {
    id,
    rapportoId: profilo.rapportoId,
    titolo: titoloCompetenza(competenza, mese(anno, m)),
    competenza,
    periodoRetribuzione: mese(anno, m),
    meseVariabili: ordinaria ? mese(m === 1 ? anno - 1 : anno, m === 1 ? 12 : m - 1) : null,
    dataElaborazione: `${mese(anno, m)}-27`,
    nomeFile: `${competenza.chiave}_${profilo.aziendaId}_${profilo.personaId}.pdf`,
    acquisitoIl: '2026-10-01T09:30:00Z',
    confidenza: arrotonda(0.86 + rumore(seme + 11) * 0.13),
    inseritoAMano: false,
    modificatoAMano: false,
    campiNonTrovati: profilo.ccnlTesto ? [] : ['datiContrattuali.ccnl', 'datiContrattuali.codiceCnel'],
    avvisiEstrazione: profilo.avvisiEstrazione,
    inquadramento: {
      qualifica: profilo.qualifica,
      livello: profilo.livello,
      codiceDipendente: String(1000 + profilo.personaId),
      ccnlTesto: profilo.ccnlTesto,
      dataAssunzione: profilo.dataAssunzione,
      dataCessazione: profilo.dataCessazione,
    },
    totali: {
      lordo,
      totaleCompetenze: arrotonda(lordo + nonRetributivo + detrazioni),
      totaleTrattenute: arrotonda(totaleTrattenute + detrazioni),
      arrotondamento,
      netto,
    },
    elementiRetributivi: {
      minimo: e.minimo,
      contingenza: e.contingenza,
      superminimo: e.superminimo,
      superminimoAssorbibile: true,
      importoScatti: e.scatti,
      altriElementiFissi: null,
      retribuzioneBase: null,
      totale: totaleFisso,
      pagaOraria: arrotonda(totaleFisso / 168),
      pagaGiornaliera: arrotonda(totaleFisso / 26),
      divisoreOrario: 168,
      divisoreGiornaliero: 26,
    },
    presenze: {
      giorniLavorati: ordinaria ? 21 : null,
      oreLavorate: ordinaria ? 168 : null,
      giorniRetribuiti: ordinaria ? 26 : null,
      voci:
        ordinaria && rumore(seme + 13) > 0.5
          ? [
              voce('Ferie godute', { quantita: 2, unita: 'gg' }),
              voce('Permessi retribuiti', { quantita: 4, unita: 'ore' }),
            ]
          : [],
    },
    competenze,
    trattenutePrevidenziali,
    trattenuteFiscali,
    altreTrattenute,
    vociNonRetributive,
    vociInformative: ordinaria
      ? [voce('Imponibile fiscale del mese', { importoCompetenza: arrotonda(imponibileIrpef) })]
      : [],
    tfr,
    ratei,
    scatti: {
      numeroScatti: profilo.numeroScatti(anno, m),
      prossimoScatto: profilo.rapportoId === 1 ? '2028-03-01' : null,
    },
    buoniPasto: {
      presenti: numeroBuoni > 0,
      numero: numeroBuoni > 0 ? numeroBuoni : null,
      valoreUnitario: numeroBuoni > 0 ? 8 : null,
      importo: numeroBuoni > 0 ? arrotonda(numeroBuoni * 8) : null,
    },
    progressivi: {
      imponibilePrevidenziale: arrotonda(lordo * m),
      imponibileIrpef: arrotonda(imponibileIrpef * m),
      irpefTrattenuta: arrotonda(irpef * m * 0.8),
    },
    contieneTredicesima: competenza.tipo === 'TREDICESIMA',
    contieneQuattordicesima: competenza.tipo === 'QUATTORDICESIMA',
  };
}

export function rigaDaCedolino(
  cedolino: CedolinoDettaglio,
  indicatori: { scattoInBusta: boolean; aumentoFuoriScatti: boolean },
): RigaMensilita {
  return {
    chiave: cedolino.competenza.chiave,
    stato: 'PRESENTE',
    titolo: cedolino.titolo,
    competenza: cedolino.competenza,
    periodoRetribuzione: cedolino.periodoRetribuzione,
    cedolinoId: cedolino.id,
    netto: cedolino.totali.netto,
    lordo: cedolino.totali.lordo,
    ratei:
      cedolino.competenza.tipo === 'ORDINARIA'
        ? cedolino.ratei.flatMap((r) =>
            r.saldo === null ? [] : [{ tipo: r.tipo, unita: r.unita, saldo: r.saldo }],
          )
        : [],
    scattoInBusta: indicatori.scattoInBusta,
    aumentoFuoriScatti: indicatori.aumentoFuoriScatti,
    nomeFile: cedolino.nomeFile,
    caricamentoId: null,
    motivoErrore: null,
  };
}

function rigaMancante(competenza: Competenza, periodo: string): RigaMensilita {
  return {
    chiave: competenza.chiave,
    stato: 'MANCANTE',
    titolo: titoloCompetenza(competenza, periodo),
    competenza,
    periodoRetribuzione: periodo,
    cedolinoId: null,
    netto: null,
    lordo: null,
    ratei: [],
    scattoInBusta: false,
    aumentoFuoriScatti: false,
    nomeFile: null,
    caricamentoId: null,
    motivoErrore: null,
  };
}

/** Ordine cronologico: la tredicesima segue dicembre, la quattordicesima luglio, il fine rapporto il suo periodo. */
export function ordineCompetenza(riga: Pick<RigaMensilita, 'competenza' | 'periodoRetribuzione'>): number {
  const { competenza } = riga;
  switch (competenza.tipo) {
    case 'ORDINARIA':
      return competenza.anno * 1000 + (competenza.mese ?? 0) * 10;
    case 'TREDICESIMA':
      return competenza.anno * 1000 + 125;
    case 'QUATTORDICESIMA':
      return competenza.anno * 1000 + 75;
    case 'FINE_RAPPORTO': {
      const [a, m] = (riga.periodoRetribuzione ?? `${competenza.anno}-12`).split('-').map(Number);
      return (a ?? competenza.anno) * 1000 + (m ?? 12) * 10 + 8 + (competenza.progressivoTfr ?? 0) / 100;
    }
  }
}

// ------------------------------------------------------------------ database in memoria

/** Mensilita' (rapporto + anno + mese 1..14, 13 e 14 tredicesima e quattordicesima) associata a un file in errore. */
export interface AssociazioneMock {
  rapportoId: number;
  anno: number;
  mese: number;
}

export interface MockDb {
  persone: PersonaSintesi[];
  rapporti: Map<number, RapportoDettaglio>;
  cedolini: Map<number, CedolinoDettaglio>;
  contatori: Contatori;
  /** Tutti i tentativi di caricamento, dal piu' recente. */
  caricamenti: Caricamento[];
  /** PDF conservati dei file in errore completabili a mano, per file (vedi chiaveFile). */
  pdfFile: Map<string, Blob>;
  /** Mensilita' a cui l'utente ha associato un file in errore, per file (vedi chiaveFile). */
  associazioni: Map<string, AssociazioneMock>;
}

/**
 * Surrogato dell'hash del contenuto con cui il backend identifica un file: nel mock stesso nome e stessa dimensione
 * sono lo stesso file.
 */
export function chiaveFile(caricamento: Pick<Caricamento, 'nomeFileOriginale' | 'dimensione'>): string {
  return `${caricamento.nomeFileOriginale}:${caricamento.dimensione}`;
}

function segnalazione(
  id: number,
  valori: Omit<Segnalazione, 'id' | 'cedolinoId' | 'competenza' | 'evidenze'> & Partial<Segnalazione>,
): Segnalazione {
  return { id, cedolinoId: null, competenza: null, evidenze: [], ...valori };
}

/** Cedolino ordinario più recente (righe in ordine cronologico): riferimento della RAL, come nel backend. */
function ultimaOrdinaria(righe: RigaMensilita[]): number | null {
  return (
    righe.findLast((r) => r.stato === 'PRESENTE' && r.competenza.tipo === 'ORDINARIA')?.cedolinoId ?? null
  );
}

function rapportoMario1(db: MockDb): RapportoDettaglio {
  const profilo: ProfiloRapporto = {
    rapportoId: 1,
    personaId: MARIO.id,
    aziendaId: ALFA.id,
    qualifica: 'Impiegato',
    livello: '2',
    ccnlTesto: 'CCNL Terziario Confcommercio',
    dataAssunzione: '2022-03-01',
    dataCessazione: null,
    elementi: (anno, m) => {
      const rinnovo = anno > 2024 || (anno === 2024 && m >= 4);
      const scatto = anno > 2025 || (anno === 2025 && m >= 3);
      const promozione = anno >= 2026;
      return {
        minimo: promozione ? 1990.5 : rinnovo ? 1850.25 : 1780.1,
        contingenza: 532.15,
        superminimo: promozione ? 310 : rinnovo ? 180 : 250.15,
        scatti: scatto ? 30.33 : 0,
      };
    },
    numeroScatti: (anno, m) => (anno > 2025 || (anno === 2025 && m >= 3) ? 1 : 0),
    buoniPasto: true,
    avvisiEstrazione: [],
    ratei: [
      { tipo: 'FERIE', unita: 'GIORNI', maturatoMese: 2.17 },
      { tipo: 'PERMESSI', unita: 'ORE', maturatoMese: 6 },
      { tipo: 'EX_FESTIVITA', unita: 'ORE', maturatoMese: 2.67 },
    ],
  };

  const righe: RigaMensilita[] = [];
  for (const [anno, m] of intervalloMesi([2024, 1], [2026, 9])) {
    if (anno === 2025 && m === 8) {
      righe.push(rigaMancante(competenzaOrdinaria(anno, m), mese(anno, m)));
      continue;
    }
    const cedolino = generaCedolino(profilo, competenzaOrdinaria(anno, m), [anno, m], db.contatori);
    db.cedolini.set(cedolino.id, cedolino);
    righe.push(
      rigaDaCedolino(cedolino, {
        scattoInBusta: anno === 2025 && m === 3,
        aumentoFuoriScatti: (anno === 2024 && m === 4) || (anno === 2026 && m === 1),
      }),
    );
  }
  for (const anno of [2024, 2025, 2026]) {
    const quattordicesima = generaCedolino(
      profilo,
      competenzaSpeciale(anno, 'QUATTORDICESIMA'),
      [anno, 7],
      db.contatori,
    );
    db.cedolini.set(quattordicesima.id, quattordicesima);
    righe.push(rigaDaCedolino(quattordicesima, { scattoInBusta: false, aumentoFuoriScatti: false }));
  }
  for (const anno of [2024, 2025]) {
    const tredicesima = generaCedolino(
      profilo,
      competenzaSpeciale(anno, 'TREDICESIMA'),
      [anno, 12],
      db.contatori,
    );
    db.cedolini.set(tredicesima.id, tredicesima);
    righe.push(rigaDaCedolino(tredicesima, { scattoInBusta: false, aumentoFuoriScatti: false }));
  }

  const riepilogo: Riepilogo = {
    calcolatoIl: '2026-10-01T09:31:00Z',
    anzianita: {
      dal: '2022-03',
      al: '2026-09',
      inCorso: true,
      anni: 4,
      mesi: 7,
      fonteInizio: 'DATA_CONTRATTUALE',
      fonteFine: 'CEDOLINI',
    },
    scatti: {
      attuali: 1,
      massimo: 10,
      prossimoScatto: '2028-03-01',
      fonteProssimo: 'CEDOLINI',
      fonteAttuali: 'CEDOLINI',
      attualiStimati: false,
    },
    retribuzione: {
      ral: 40081.72,
      lordoMensile: 2862.98,
      mensilita: 14,
      minimo: 1990.5,
      contingenza: 532.15,
      superminimo: 310,
      importoScatti: 30.33,
      altriElementiFissi: null,
      retribuzioneBase: null,
      stimata: false,
      nota: null,
      cedolinoRiferimentoId: ultimaOrdinaria(righe),
    },
    buoniPasto: true,
    ratei: {
      cedolinoRiferimentoId: righe.find((r) => r.chiave === '2026.09')?.cedolinoId ?? null,
      saldi: [
        {
          tipo: 'FERIE',
          unita: 'GIORNI',
          saldo: 12.5,
          valoreStimato: 1376.4,
          nota: 'Giorni x paga giornaliera stampata sul cedolino.',
        },
        {
          tipo: 'PERMESSI',
          unita: 'ORE',
          saldo: 32,
          valoreStimato: 545.33,
          nota: 'Ore x paga oraria stampata sul cedolino.',
        },
        {
          tipo: 'EX_FESTIVITA',
          unita: 'ORE',
          saldo: 16,
          valoreStimato: 272.67,
          nota: 'Ore x paga oraria stampata sul cedolino.',
        },
      ],
    },
    tfr: {
      totaleAccumulato: 12345.67,
      quotaMensile: 212.07,
      quotaInAzienda: 8120.4,
      quotaFondi: 4225.27,
      quotaBusta: 0,
      anticipi: 0,
      stimaNetto: 9506.17,
      aliquotaStimata: 0.23,
      stimato: false,
      riferimento: '2026-09',
      nota: null,
      quotaMensileStimata: false,
      liquidato: false,
      importoLiquidato: null,
      nettoLiquidato: null,
    },
    liquidazione: {
      dataRiferimento: '2026-09-30',
      voci: [
        {
          tipo: 'TFR',
          quantita: null,
          unita: null,
          lordo: 8120.4,
          contributi: 0,
          imposte: 1867.69,
          netto: 6252.71,
          nota:
            'Tassazione separata: aliquota media del 23% sul reddito di riferimento di 2.693,60 € ' +
            '(12.345,67 € x 12 / 55 mesi di anzianità), con gli scaglioni IRPEF 2026.',
        },
        {
          tipo: 'FERIE',
          quantita: 12.5,
          unita: 'GIORNI',
          lordo: 1376.4,
          contributi: 126.49,
          imposte: 412.47,
          netto: 837.44,
          nota: 'Saldo dell’ultimo cedolino che riporta i ratei. Giorni x paga giornaliera stampata sul cedolino.',
        },
        {
          tipo: 'PERMESSI',
          quantita: 32,
          unita: 'ORE',
          lordo: 545.33,
          contributi: 50.12,
          imposte: 163.42,
          netto: 331.79,
          nota: 'Saldo dell’ultimo cedolino che riporta i ratei. Ore x paga oraria stampata sul cedolino.',
        },
        {
          tipo: 'EX_FESTIVITA',
          quantita: 16,
          unita: 'ORE',
          lordo: 272.67,
          contributi: 25.06,
          imposte: 81.71,
          netto: 165.9,
          nota: 'Saldo dell’ultimo cedolino che riporta i ratei. Ore x paga oraria stampata sul cedolino.',
        },
        {
          tipo: 'RATEI_TREDICESIMA',
          quantita: 9,
          unita: null,
          lordo: 2147.24,
          contributi: 197.33,
          imposte: 643.47,
          netto: 1306.44,
          nota: '9/12 del lordo mensile (2.862,98 €) per i mesi da Gennaio 2026 a Settembre 2026.',
        },
        {
          tipo: 'RATEI_QUATTORDICESIMA',
          quantita: 3,
          unita: null,
          lordo: 715.75,
          contributi: 65.78,
          imposte: 214.49,
          netto: 435.48,
          nota:
            '3/12 del lordo mensile (2.862,98 €) per i mesi da Luglio 2026 a Settembre 2026 (periodo di ' +
            'maturazione da Luglio 2026 a Giugno 2027).',
        },
      ],
      totaleLordo: 13177.79,
      totaleContributi: 464.78,
      totaleImposte: 3383.25,
      totaleNetto: 9329.76,
      completa: true,
      aliquotaContributi: 0.0919,
      aliquotaContributiStampata: true,
      aliquotaIrpefMarginale: 0.33,
      aliquotaTfr: 0.23,
      nota:
        'Stima senza proiezioni dopo l’ultimo cedolino, con il preavviso considerato lavorato. Non comprende ' +
        'straordinari, premi e altre voci variabili. Le quote di TFR versate ai fondi pensione (4.225,27 €) non ' +
        'sono comprese: le liquida il fondo. Contributi INPS al 9,19%, come stampato sul cedolino. IRPEF ' +
        'all’aliquota marginale del 33% stimata sulla RAL al netto dei contributi, senza addizionali, detrazioni ' +
        'e conguaglio di fine anno.',
    },
    preavviso: {
      stato: 'VERIFICATO',
      livello: '1',
      gruppoLivelli: 'Quadri e 1° livello',
      fascia: 'fino a 5 anni',
      prossimaFascia: '2027-03',
      dimissioni: { mesi: 0, giorni: 45 },
      licenziamento: { mesi: 0, giorni: 60 },
      indennitaDimissioni: 5010.22,
      indennitaLicenziamento: 6680.29,
      riferimento: 'Art. 247 (licenziamento) e 254 (dimissioni)',
      nota:
        'Indennità stimata sul lordo mensile di 2.862,98 € con i ratei delle mensilità aggiuntive (x 14 / 12), ' +
        'al lordo di contributi e imposte. Anzianità all’ultimo cedolino.',
    },
  };

  const mancante = righe.find((r) => r.stato === 'MANCANTE');
  return {
    id: 1,
    persona: MARIO,
    azienda: ALFA,
    etichetta: ALFA.ragioneSociale,
    periodo: { da: '2022-03', a: null, fonteInizio: 'DATA_CONTRATTUALE', fonteFine: 'CEDOLINI' },
    attuale: true,
    dataAssunzione: '2022-03-01',
    dataCessazione: null,
    qualifica: 'Impiegato',
    livello: '1',
    ccnl: trovaCcnl('COMMERCIO') ?? null,
    ccnlDaIndicare: false,
    ccnlSceltoDaUtente: false,
    note: 'Accordo integrativo aziendale firmato a Gennaio 2026.',
    segnalazioni: mancante
      ? [
          segnalazione(1, {
            tipo: 'MENSILITA_MANCANTE',
            gravita: 'AVVISO',
            titolo: `Manca il cedolino di ${mancante.titolo}`,
            descrizione: `Tra i cedolini caricati manca la mensilità ordinaria di ${mancante.titolo}: caricala per completare i calcoli.`,
            competenza: mancante.competenza,
            evidenze: [
              {
                descrizione: 'Cedolino precedente',
                cedolinoId: null,
                competenza: null,
                valore: 'Luglio 2025',
              },
              {
                descrizione: 'Cedolino successivo',
                cedolinoId: null,
                competenza: null,
                valore: 'Settembre 2025',
              },
            ],
          }),
        ]
      : [],
    riepilogo,
    mensilita: righe,
  };
}

function rapportoMario2(db: MockDb): RapportoDettaglio {
  const profilo: ProfiloRapporto = {
    rapportoId: 2,
    personaId: MARIO.id,
    aziendaId: BETA.id,
    qualifica: 'Impiegato tecnico',
    livello: 'C3',
    ccnlTesto: 'CONTRATTO DI LAVORO Metalmeccanici imp. CNEL C011',
    dataAssunzione: '2018-09-03',
    dataCessazione: '2022-02-28',
    elementi: () => ({ minimo: 1910.4, contingenza: 0, superminimo: 380, scatti: 26.5 }),
    numeroScatti: () => 1,
    buoniPasto: false,
    avvisiEstrazione: [],
    ratei: [
      { tipo: 'FERIE', unita: 'ORE', maturatoMese: 13.33 },
      { tipo: 'PERMESSI', unita: 'ORE', maturatoMese: 8.67 },
    ],
  };
  const righe: RigaMensilita[] = [];
  for (const [anno, m] of intervalloMesi([2021, 1], [2022, 2])) {
    const cedolino = generaCedolino(profilo, competenzaOrdinaria(anno, m), [anno, m], db.contatori);
    db.cedolini.set(cedolino.id, cedolino);
    righe.push(rigaDaCedolino(cedolino, { scattoInBusta: false, aumentoFuoriScatti: false }));
  }
  const tredicesima = generaCedolino(
    profilo,
    competenzaSpeciale(2021, 'TREDICESIMA'),
    [2021, 12],
    db.contatori,
  );
  db.cedolini.set(tredicesima.id, tredicesima);
  righe.push(rigaDaCedolino(tredicesima, { scattoInBusta: false, aumentoFuoriScatti: false }));
  // due cedolini dopo la cessazione: fine rapporto e successivo conguaglio, numerati per periodo stampato
  for (const [progressivo, periodo] of [
    [1, [2022, 3]],
    [2, [2022, 5]],
  ] as const) {
    const fine = generaCedolino(
      profilo,
      competenzaFineRapporto(2022, progressivo),
      [...periodo],
      db.contatori,
    );
    db.cedolini.set(fine.id, fine);
    righe.push(rigaDaCedolino(fine, { scattoInBusta: false, aumentoFuoriScatti: false }));
  }

  return {
    id: 2,
    persona: MARIO,
    azienda: BETA,
    etichetta: BETA.ragioneSociale,
    periodo: {
      da: '2018-09',
      a: '2022-02',
      fonteInizio: 'DATA_CONTRATTUALE',
      fonteFine: 'DATA_CONTRATTUALE',
    },
    attuale: false,
    dataAssunzione: '2018-09-03',
    dataCessazione: '2022-02-28',
    qualifica: 'Impiegato tecnico',
    livello: 'C3',
    ccnl: trovaCcnl('METALMECCANICO_INDUSTRIA') ?? null,
    ccnlDaIndicare: false,
    ccnlSceltoDaUtente: false,
    note: null,
    segnalazioni: [],
    riepilogo: {
      calcolatoIl: '2026-10-01T09:31:00Z',
      anzianita: {
        dal: '2018-09',
        al: '2022-02',
        inCorso: false,
        anni: 3,
        mesi: 6,
        fonteInizio: 'DATA_CONTRATTUALE',
        fonteFine: 'DATA_CONTRATTUALE',
      },
      scatti: {
        attuali: 1,
        massimo: 5,
        prossimoScatto: null,
        fonteProssimo: 'NON_DISPONIBILE',
        fonteAttuali: 'CEDOLINI',
        attualiStimati: false,
      },
      retribuzione: {
        ral: 30119.7,
        lordoMensile: 2316.9,
        mensilita: 13,
        minimo: 1910.4,
        contingenza: null,
        superminimo: 380,
        importoScatti: 26.5,
        altriElementiFissi: null,
        retribuzioneBase: null,
        stimata: false,
        nota: null,
        cedolinoRiferimentoId: ultimaOrdinaria(righe),
      },
      buoniPasto: false,
      ratei: { cedolinoRiferimentoId: null, saldi: [] },
      tfr: {
        totaleAccumulato: 0,
        quotaMensile: 171.62,
        quotaInAzienda: 0,
        quotaFondi: 0,
        quotaBusta: 0,
        anticipi: 0,
        stimaNetto: null,
        aliquotaStimata: 0.22,
        stimato: false,
        riferimento: '2022-03',
        nota: 'TFR liquidato nel cedolino di fine rapporto di Marzo 2022: nessun TFR residuo in azienda.',
        quotaMensileStimata: false,
        liquidato: true,
        importoLiquidato: 9870,
        nettoLiquidato: 7699.6,
      },
      liquidazione: null,
      preavviso: null,
    },
    mensilita: righe,
  };
}

function rapportoLaura(db: MockDb): RapportoDettaglio {
  const profilo: ProfiloRapporto = {
    rapportoId: 3,
    personaId: LAURA.id,
    aziendaId: GAMMA.id,
    qualifica: 'Impiegata',
    livello: '3',
    ccnlTesto: null,
    dataAssunzione: '2025-11-03',
    dataCessazione: null,
    elementi: () => ({ minimo: 1650, contingenza: 0, superminimo: 300, scatti: 0 }),
    numeroScatti: () => 0,
    buoniPasto: false,
    avvisiEstrazione: ["Rimossi dati bancari presenti nell'estrazione: non vengono salvati"],
    ratei: [
      { tipo: 'FERIE', unita: 'ORE', maturatoMese: 14.67 },
      { tipo: 'ROL', unita: 'ORE', maturatoMese: 6 },
    ],
  };
  const righe: RigaMensilita[] = [];
  for (const [anno, m] of intervalloMesi([2025, 11], [2026, 9])) {
    const cedolino = generaCedolino(profilo, competenzaOrdinaria(anno, m), [anno, m], db.contatori);
    db.cedolini.set(cedolino.id, cedolino);
    righe.push(rigaDaCedolino(cedolino, { scattoInBusta: false, aumentoFuoriScatti: false }));
  }
  const tredicesima = generaCedolino(
    profilo,
    competenzaSpeciale(2025, 'TREDICESIMA'),
    [2025, 12],
    db.contatori,
  );
  db.cedolini.set(tredicesima.id, tredicesima);
  righe.push(rigaDaCedolino(tredicesima, { scattoInBusta: false, aumentoFuoriScatti: false }));

  return {
    id: 3,
    persona: LAURA,
    azienda: GAMMA,
    etichetta: GAMMA.ragioneSociale,
    periodo: { da: '2025-11', a: null, fonteInizio: 'DATA_CONTRATTUALE', fonteFine: 'CEDOLINI' },
    attuale: true,
    dataAssunzione: '2025-11-03',
    dataCessazione: null,
    qualifica: 'Impiegata',
    livello: '3',
    ccnl: null,
    ccnlDaIndicare: true,
    ccnlSceltoDaUtente: false,
    note: null,
    segnalazioni: [
      segnalazione(2, {
        tipo: 'CCNL_NON_DETERMINATO',
        gravita: 'INFORMATIVA',
        titolo: 'CCNL non determinato',
        descrizione:
          'Il CCNL non è stampato sui cedolini: indicalo nel riepilogo del rapporto. Finché non è indicato la RAL è ' +
          'solo stimata e non è possibile controllare quattordicesima, scatti di anzianità e livello secondo le ' +
          'regole del contratto.',
      }),
    ],
    riepilogo: {
      calcolatoIl: '2026-10-01T09:31:00Z',
      anzianita: {
        dal: '2025-11',
        al: '2026-09',
        inCorso: true,
        anni: 0,
        mesi: 11,
        fonteInizio: 'DATA_CONTRATTUALE',
        fonteFine: 'CEDOLINI',
      },
      scatti: {
        attuali: 0,
        massimo: null,
        prossimoScatto: null,
        fonteProssimo: 'NON_DISPONIBILE',
        fonteAttuali: 'CCNL',
        attualiStimati: true,
      },
      retribuzione: {
        ral: 25350,
        lordoMensile: 1950,
        mensilita: 13,
        minimo: 1650,
        contingenza: null,
        superminimo: 300,
        importoScatti: null,
        altriElementiFissi: null,
        retribuzioneBase: null,
        stimata: true,
        nota: 'CCNL non indicato: RAL calcolata su 13 mensilità.',
        cedolinoRiferimentoId: ultimaOrdinaria(righe),
      },
      buoniPasto: false,
      ratei: {
        cedolinoRiferimentoId: righe.find((r) => r.chiave === '2026.09')?.cedolinoId ?? null,
        saldi: [
          {
            tipo: 'FERIE',
            unita: 'ORE',
            saldo: 61.5,
            valoreStimato: 713.88,
            nota: 'Ore x lordo mensile / divisore orario 173 (da configurazione).',
          },
          { tipo: 'ROL', unita: 'ORE', saldo: 22, valoreStimato: null, nota: null },
        ],
      },
      tfr: {
        totaleAccumulato: 1590.12,
        quotaMensile: 144.44,
        quotaInAzienda: 1590.12,
        quotaFondi: 0,
        quotaBusta: 0,
        anticipi: 0,
        stimaNetto: 1224.39,
        aliquotaStimata: 0.23,
        stimato: true,
        riferimento: '2026-09',
        nota: 'Fondo al 31/12 non stampato: totale ricostruito sommando le quote mensili.',
        quotaMensileStimata: true,
        liquidato: false,
        importoLiquidato: null,
        nettoLiquidato: null,
      },
      liquidazione: {
        dataRiferimento: '2026-09-30',
        voci: [
          {
            tipo: 'TFR',
            quantita: null,
            unita: null,
            lordo: 1590.12,
            contributi: 0,
            imposte: 365.73,
            netto: 1224.39,
            nota:
              'Tassazione separata: aliquota media del 23% sul reddito di riferimento di 1.734,68 € ' +
              '(1.590,12 € x 12 / 11 mesi di anzianità), con gli scaglioni IRPEF 2026.',
          },
          {
            tipo: 'FERIE',
            quantita: 61.5,
            unita: 'ORE',
            lordo: 713.88,
            contributi: 65.61,
            imposte: 149.1,
            netto: 499.17,
            nota: 'Saldo dell’ultimo cedolino che riporta i ratei. Ore x lordo mensile / divisore orario 173 (da configurazione).',
          },
          {
            tipo: 'ROL',
            quantita: 22,
            unita: 'ORE',
            lordo: null,
            contributi: null,
            imposte: null,
            netto: null,
            nota: 'Valore non stimabile: paga giornaliera/oraria e lordo mensile non disponibili.',
          },
          {
            tipo: 'RATEI_TREDICESIMA',
            quantita: 9,
            unita: null,
            lordo: 1462.5,
            contributi: 134.4,
            imposte: 305.46,
            netto: 1022.64,
            nota: '9/12 del lordo mensile (1.950,00 €) per i mesi da Gennaio 2026 a Settembre 2026.',
          },
        ],
        totaleLordo: 3766.5,
        totaleContributi: 200.01,
        totaleImposte: 820.29,
        totaleNetto: 2746.2,
        completa: false,
        aliquotaContributi: 0.0919,
        aliquotaContributiStampata: false,
        aliquotaIrpefMarginale: 0.23,
        aliquotaTfr: 0.23,
        nota:
          'Stima senza proiezioni dopo l’ultimo cedolino, con il preavviso considerato lavorato. Non comprende ' +
          'straordinari, premi e altre voci variabili. Contributi INPS al 9,19% (aliquota non stampata sul ' +
          'cedolino). IRPEF all’aliquota marginale del 23% stimata sulla RAL al netto dei contributi, senza ' +
          'addizionali, detrazioni e conguaglio di fine anno.',
      },
      preavviso: {
        stato: 'CCNL_DA_INDICARE',
        livello: '3',
        gruppoLivelli: null,
        fascia: null,
        prossimaFascia: null,
        dimissioni: null,
        licenziamento: null,
        indennitaDimissioni: null,
        indennitaLicenziamento: null,
        riferimento: null,
        nota: 'Indica il CCNL del rapporto per conoscere il preavviso.',
      },
    },
    mensilita: righe,
  };
}

/**
 * Rinumera i cedolini di fine rapporto di un rapporto in ordine di periodo stampato (TFR01, TFR02, ...), come fa il
 * backend dopo un'eliminazione: cambiano competenza, titolo e nome del file.
 */
export function rinumeraFineRapporto(db: MockDb, rapporto: RapportoDettaglio): void {
  const fineRapporto = rapporto.mensilita
    .filter((r) => r.competenza.tipo === 'FINE_RAPPORTO' && r.cedolinoId !== null)
    .sort((a, b) => (a.periodoRetribuzione ?? '').localeCompare(b.periodoRetribuzione ?? ''));
  fineRapporto.forEach((riga, indice) => {
    const cedolino = riga.cedolinoId !== null ? db.cedolini.get(riga.cedolinoId) : undefined;
    if (!cedolino || riga.competenza.progressivoTfr === indice + 1) {
      return;
    }
    const competenza = competenzaFineRapporto(riga.competenza.anno, indice + 1);
    const titolo = titoloCompetenza(competenza, riga.periodoRetribuzione);
    const nomeFile = `${competenza.chiave}_${rapporto.azienda.id}_${rapporto.persona.id}.pdf`;
    Object.assign(cedolino, { competenza, titolo, nomeFile });
    Object.assign(riga, { chiave: competenza.chiave, competenza, titolo, nomeFile });
  });
}

/** Ordina le mensilità in ordine cronologico inverso. */
export function ordinaMensilita(righe: RigaMensilita[]): RigaMensilita[] {
  return [...righe].sort((a, b) => ordineCompetenza(b) - ordineCompetenza(a));
}

export function aggiungiRapporto(db: MockDb, rapporto: RapportoDettaglio) {
  rapporto.mensilita = ordinaMensilita(rapporto.mensilita);
  db.rapporti.set(rapporto.id, rapporto);
  if (!db.persone.some((p) => p.id === rapporto.persona.id)) {
    db.persone.push(rapporto.persona);
    db.persone.sort((a, b) => a.cognome.localeCompare(b.cognome, 'it') || a.nome.localeCompare(b.nome, 'it'));
  }
}

/** Aggiunge i dati di Mario Rossi (due aziende): usato anche dal caricamento simulato a database vuoto. */
export function popolaMario(db: MockDb): void {
  if (!db.rapporti.has(1)) {
    aggiungiRapporto(db, rapportoMario1(db));
  }
  if (!db.rapporti.has(2)) {
    aggiungiRapporto(db, rapportoMario2(db));
  }
}

/**
 * Tre file caricati ma non letti, con il PDF conservato: una scansione non associata, un PDF protetto da password e
 * una ristampa associata dall'utente alla mensilita' mancante di Agosto 2025 di Alfa Software.
 */
function aggiungiFileInErrore(db: MockDb): void {
  const contenuto = new Blob([PDF_MINIMO], { type: 'application/pdf' });
  const lotto = '5a3d2c1b-0000-4000-8000-00000000e770';
  const falliti: { nome: string; motivo: keyof typeof MESSAGGI_ERRORE; creatoIl: string }[] = [
    { nome: 'ristampa.pdf', motivo: 'LAYOUT_NON_RICONOSCIUTO', creatoIl: '2026-10-01T09:20:00Z' },
    { nome: 'cedolino-protetto.pdf', motivo: 'PDF_PROTETTO', creatoIl: '2026-10-01T09:20:05Z' },
    { nome: 'busta-scansione.pdf', motivo: 'PDF_SENZA_TESTO', creatoIl: '2026-10-01T09:20:10Z' },
  ];
  for (const { nome, motivo, creatoIl } of falliti) {
    const caricamento: Caricamento = {
      id: db.contatori.prossimoIdCaricamento++,
      lotto,
      nomeFileOriginale: nome,
      dimensione: contenuto.size,
      stato: 'ERRORE',
      messaggio: MESSAGGI_ERRORE[motivo],
      motivo,
      pdfDisponibile: true,
      cedolinoId: null,
      cedolinoEsistenteId: null,
      personaId: null,
      rapportoId: null,
      creatoIl,
      aggiornatoIl: creatoIl,
    };
    db.caricamenti.unshift(caricamento);
    db.pdfFile.set(chiaveFile(caricamento), contenuto);
    if (nome === 'ristampa.pdf') {
      db.associazioni.set(chiaveFile(caricamento), { rapportoId: 1, anno: 2025, mese: 8 });
    }
  }
}

/** Dati iniziali: dimostrativi, vuoti (primo avvio) o dimostrativi con tre cedolini caricati con errore. */
export type DatiIniziali = 'demo' | 'vuoto' | 'errori';

export function creaDbMock(datiIniziali: DatiIniziali): MockDb {
  const db: MockDb = {
    persone: [],
    rapporti: new Map(),
    cedolini: new Map(),
    contatori: { prossimoIdCedolino: 101, prossimoIdCaricamento: 1, prossimoIdAnagrafica: 101 },
    caricamenti: [],
    pdfFile: new Map(),
    associazioni: new Map(),
  };
  if (datiIniziali !== 'vuoto') {
    popolaMario(db);
    aggiungiRapporto(db, rapportoLaura(db));
  }
  if (datiIniziali === 'errori') {
    aggiungiFileInErrore(db);
  }
  return db;
}

/** Testo usato nei messaggi del caricamento simulato. */
export const MESSAGGIO_PDF_PROTETTO = 'PDF protetto da password: riprova indicando la password';

/** PDF valido di una pagina vuota, servito come contenuto dei PDF simulati. */
export const PDF_MINIMO =
  '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n' +
  '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 595 842]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n';

/** Messaggi del backend per i motivi d'errore simulati (testi dei lettori, mai dati personali). */
export const MESSAGGI_ERRORE = {
  PDF_PROTETTO: MESSAGGIO_PDF_PROTETTO,
  PDF_SENZA_TESTO:
    "Il PDF non contiene testo selezionabile (probabilmente è una scansione o un'immagine) e il lettore locale non " +
    'può interpretarlo.',
  LAYOUT_NON_RICONOSCIUTO: 'Il lettore locale non riconosce il layout di questo cedolino.',
  PDF_NON_VALIDO: 'Il PDF è danneggiato o non leggibile',
  PIU_CEDOLINI: 'Il documento contiene più cedolini distinti: caricali come file separati.',
} as const satisfies Partial<Record<MotivoErrore, string>>;
