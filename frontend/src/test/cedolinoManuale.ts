import type { CedolinoManuale } from '../api/types';

/**
 * Richiesta di inserimento a mano con dati INVENTATI (le anagrafiche fittizie del backend simulato): cedolino di
 * Agosto 2025 di Mario Rossi presso Alfa Software, coerente nei totali, modificabile per il singolo test.
 */
export function cedolinoManuale(
  modifiche: (dati: CedolinoManuale) => void = () => undefined,
): CedolinoManuale {
  const dati: CedolinoManuale = {
    dipendente: {
      codiceFiscale: 'rssmra80a01h501u ',
      nome: 'Mario',
      cognome: 'Rossi',
      dataNascita: null,
      codiceDipendente: null,
    },
    datoreLavoro: {
      codiceFiscale: null,
      partitaIva: 'IT01234567890',
      posizioneInps: null,
      posizioneInail: null,
      ragioneSociale: 'Alfa Software S.r.l.',
      indirizzo: null,
      codiceAzienda: null,
    },
    datiContrattuali: {
      dataAssunzione: null,
      dataCessazione: null,
      qualifica: null,
      livello: null,
      ccnl: null,
      codiceCnel: null,
    },
    periodo: { anno: 2025, mese: 8, tipo: 'ORDINARIA', dataElaborazione: null, meseVariabili: null },
    totali: { lordo: null, totaleCompetenze: 2100, totaleTrattenute: 450, arrotondamento: null, netto: 1650 },
    retribuzione: {
      minimo: null,
      contingenza: null,
      superminimo: null,
      superminimoAssorbibile: null,
      importoScatti: null,
      altriElementiFissi: null,
      retribuzioneBase: null,
      totale: null,
      pagaOraria: null,
      pagaGiornaliera: null,
      divisoreOrario: null,
      divisoreGiornaliero: null,
    },
    voci: [
      {
        codice: null,
        descrizione: 'Retribuzione ordinaria',
        categoria: 'COMPETENZA',
        quantita: null,
        unita: null,
        base: null,
        importoCompetenza: 2100,
        importoTrattenuta: null,
      },
      {
        codice: null,
        descrizione: 'Contributi previdenziali',
        categoria: 'TRATTENUTA_PREVIDENZIALE',
        quantita: null,
        unita: null,
        base: null,
        importoCompetenza: null,
        importoTrattenuta: 450,
      },
    ],
    ratei: [],
    tfr: null,
    presenze: { giorniLavorati: null, oreLavorate: null, giorniRetribuiti: null },
    scatti: { numeroScatti: null, dataProssimoScatto: null },
    buoniPasto: { presenti: false, numero: null, valoreUnitario: null, importo: null },
    progressivi: { imponibilePrevidenziale: null, imponibileIrpef: null, irpefTrattenuta: null },
    contieneTredicesima: false,
    contieneQuattordicesima: false,
  };
  modifiche(dati);
  return dati;
}
