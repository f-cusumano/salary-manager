import type { ComboboxData, ComboboxParsedItem } from '@mantine/core';
import { describe, expect, it } from 'vitest';
import type { AziendaSintesi, RapportoDettaglio, RapportoSintesi } from '../api/types';
import { elencoAziende, elencoPersone } from '../mocks/anagrafiche';
import { creaDbMock } from '../mocks/dati';
import {
  applicaScelta,
  corrisponde,
  filtroAnagrafiche,
  identitaAzienda,
  identitaPersona,
  liberaCopiati,
  opzioniAziende,
  rapportoDellaCoppia,
  valoriIniziali,
  type FontiScelta,
} from './sceltaAnagrafiche';
import {
  daCedolinoRiferimento,
  rigaDiRiferimento,
  valoriVuoti,
  voceVuota,
  type ValoriCedolinoManuale,
} from './useCedolinoManuale';

// dati INVENTATI del backend simulato: Mario Rossi (Alfa Software, poi Beta Consulting), Laura Bianchi (Gamma Servizi)
const db = creaDbMock('errori');
const persone = elencoPersone(db);
const aziende = elencoAziende(db);
const mario = persone.find((p) => p.nome === 'Mario')!;
const alfa = aziende.find((a) => a.ragioneSociale.startsWith('Alfa'))!;
const beta = aziende.find((a) => a.ragioneSociale.startsWith('Beta'))!;
const gamma = aziende.find((a) => a.ragioneSociale.startsWith('Gamma'))!;
const rapportoAlfa = db.rapporti.get(1)!;
const rapportoBeta = db.rapporti.get(2)!;

function sintesi(id: number, azienda: AziendaSintesi): RapportoSintesi {
  return {
    id,
    personaId: mario.id,
    azienda,
    etichetta: azienda.ragioneSociale,
    periodo: { da: null, a: null, fonteInizio: 'CEDOLINI', fonteFine: 'CEDOLINI' },
    attuale: false,
    haSegnalazioni: false,
    ccnlDaIndicare: false,
  };
}

/** Cedolino di riferimento del rapporto per Agosto 2025. */
function riferimentoDi(rapporto: RapportoDettaglio): NonNullable<FontiScelta['riferimento']> {
  const riga = rigaDiRiferimento(rapporto.mensilita, { anno: 2025, mese: 8 })!;
  return { riga, cedolino: db.cedolini.get(riga.cedolinoId!)! };
}

/** Modulo di Agosto 2025 (periodo dal mese associato) senza scelte. */
function agosto(): { valori: ValoriCedolinoManuale; copiati: Record<string, string> } {
  const valori = valoriVuoti();
  valori.periodo = { ...valori.periodo, tipo: 'ORDINARIA', mese: '8', anno: 2025 };
  const nota = 'Dal mese associato al file (Agosto 2025): verifica';
  return { valori, copiati: { 'periodo.tipo': nota, 'periodo.mese': nota, 'periodo.anno': nota } };
}

function conScelte(
  valori: ValoriCedolinoManuale,
  personaId: number | null,
  aziendaId: number | null,
): ValoriCedolinoManuale {
  return {
    ...valori,
    personaId: personaId !== null ? String(personaId) : null,
    nuovaPersona: false,
    aziendaId: aziendaId !== null ? String(aziendaId) : null,
    nuovaAzienda: false,
  };
}

describe('dati anagrafici in archivio', () => {
  it('della persona e dell’azienda, vuoti quando mancano o per una nuova', () => {
    expect(identitaPersona(mario)).toEqual({
      dipendente: {
        codiceFiscale: 'RSSMRA80A01H501U',
        cognome: 'Rossi',
        nome: 'Mario',
        dataNascita: '1980-01-01',
      },
    });
    expect(identitaPersona({ ...mario, dataNascita: null }).dipendente?.dataNascita).toBe('');
    expect(identitaPersona(null)).toEqual({
      dipendente: { codiceFiscale: '', cognome: '', nome: '', dataNascita: '' },
    });
    expect(identitaAzienda(gamma)).toEqual({
      datoreLavoro: {
        ragioneSociale: 'Gamma Servizi S.r.l.',
        partitaIva: '11223344554',
        codiceFiscale: '',
        posizioneInps: '',
      },
    });
    expect(identitaAzienda(null).datoreLavoro?.ragioneSociale).toBe('');
  });

  it('il rapporto della coppia è tra quelli della persona', () => {
    const rapporti = [sintesi(2, rapportoBeta.azienda), sintesi(1, rapportoAlfa.azienda)];
    expect(rapportoDellaCoppia(rapporti, alfa.id)?.id).toBe(1);
    expect(rapportoDellaCoppia(rapporti, gamma.id)).toBeNull();
  });
});

describe('opzioni della tendina delle aziende', () => {
  const valoriDi = (data: ComboboxData) =>
    (data as { group?: string; items?: { value: string }[]; value?: string }[]).map((o) =>
      o.group !== undefined ? [o.group, o.items?.map((i) => i.value)] : o.value,
    );

  it('senza persona, o se la persona non ha aziende in elenco, un elenco semplice', () => {
    const atteso = aziende.map((a) => String(a.id));
    expect(valoriDi(opzioniAziende(aziende, null, null))).toEqual(atteso);
    expect(valoriDi(opzioniAziende(aziende, [], 'Mario Rossi'))).toEqual(atteso);
    const ignota = { ...rapportoAlfa.azienda, id: 99 };
    expect(valoriDi(opzioniAziende(aziende, [sintesi(9, ignota)], 'Mario Rossi'))).toEqual(atteso);
  });

  it('prima le aziende della persona nell’ordine dei rapporti, una volta sola, poi le altre', () => {
    const rapporti = [
      sintesi(2, rapportoBeta.azienda),
      sintesi(1, rapportoAlfa.azienda),
      sintesi(5, rapportoBeta.azienda),
    ];
    expect(valoriDi(opzioniAziende(aziende, rapporti, 'Mario Rossi'))).toEqual([
      ['Aziende di Mario Rossi', [String(beta.id), String(alfa.id)]],
      ['Altre aziende', [String(gamma.id)]],
    ]);
  });

  it('senza altre aziende il solo gruppo della persona', () => {
    const rapporti = [sintesi(1, rapportoAlfa.azienda)];
    expect(valoriDi(opzioniAziende([alfa], rapporti, 'Mario Rossi'))).toEqual([
      ['Aziende di Mario Rossi', [String(alfa.id)]],
    ]);
  });
});

describe('ricerca nelle tendine', () => {
  it('tutte le parole, senza distinguere maiuscole e accenti, nell’etichetta o nei codici', () => {
    expect(corrisponde('rossi mario', 'Mario Rossi')).toBe(true);
    expect(corrisponde('NICOLÒ', 'Nicolo Verdi')).toBe(true);
    expect(corrisponde('nicolo', 'Nicolò Verdi')).toBe(true);
    expect(corrisponde('mario bianchi', 'Mario Rossi')).toBe(false);
    expect(corrisponde('rssmra80', 'Mario Rossi', 'RSSMRA80A01H501U')).toBe(true);
    expect(corrisponde('0123456', 'Alfa Software S.r.l.', '01234567897', null)).toBe(true);
    expect(corrisponde('   ', 'Qualsiasi')).toBe(true);
  });

  it('il filtro tiene le opzioni che corrispondono e toglie i gruppi vuoti', () => {
    const codici = new Map(aziende.map((a) => [String(a.id), [a.partitaIva, a.codiceFiscale]]));
    const filtro = filtroAnagrafiche(codici);
    const opzioni = opzioniAziende(aziende, [sintesi(1, rapportoAlfa.azienda)], 'Mario Rossi');
    const cerca = (search: string) =>
      filtro({ options: opzioni as ComboboxParsedItem[], search, limit: 100 });
    expect(cerca('09876543217')).toEqual([
      { group: 'Altre aziende', items: [{ value: String(beta.id), label: beta.etichetta }] },
    ]);
    expect(cerca('software')).toEqual([
      { group: 'Aziende di Mario Rossi', items: [{ value: String(alfa.id), label: alfa.etichetta }] },
    ]);
    expect(cerca('nessuna')).toEqual([]);
  });
});

describe('precompilazione alla scelta', () => {
  it('scegliendo la persona ne copia i dati anagrafici e lascia quelli dell’azienda', () => {
    const { valori, copiati } = agosto();
    valori.datoreLavoro.ragioneSociale = 'Scritta a mano';
    const risultato = applicaScelta(
      conScelte(valori, mario.id, null),
      copiati,
      { persona: true, azienda: false },
      { persona: mario, azienda: null, rapporto: null, riferimento: null },
    );
    expect(risultato.valori.dipendente).toMatchObject({
      codiceFiscale: 'RSSMRA80A01H501U',
      cognome: 'Rossi',
      nome: 'Mario',
      dataNascita: '1980-01-01',
    });
    expect(risultato.valori.datoreLavoro.ragioneSociale).toBe('Scritta a mano');
    // i dati anagrafici non sono "copiati": sono quelli in archivio (in sola lettura)
    expect(Object.keys(risultato.copiati)).toEqual(['periodo.tipo', 'periodo.mese', 'periodo.anno']);
  });

  it('scegliendo l’azienda compila i campi vuoti da azienda, rapporto e cedolino di riferimento', () => {
    const { valori, copiati } = agosto();
    valori.datiContrattuali.livello = '5';
    const risultato = applicaScelta(
      conScelte(valori, mario.id, alfa.id),
      copiati,
      { persona: false, azienda: true },
      { persona: mario, azienda: alfa, rapporto: rapportoAlfa, riferimento: riferimentoDi(rapportoAlfa) },
    );
    const { datoreLavoro, datiContrattuali, voci } = risultato.valori;
    expect(datoreLavoro).toMatchObject({
      ragioneSociale: 'Alfa Software S.r.l.',
      partitaIva: '01234567897',
      indirizzo: 'Via Roma 1, 00100 Roma',
    });
    expect(datiContrattuali).toMatchObject({
      dataAssunzione: '2022-03-01',
      qualifica: 'Impiegato',
      livello: '5',
    });
    expect(voci.length).toBeGreaterThan(0);
    expect(risultato.copiati).toMatchObject({
      'periodo.mese': 'Dal mese associato al file (Agosto 2025): verifica',
      'datoreLavoro.indirizzo': 'Dall’azienda scelta: verifica',
      'datiContrattuali.dataAssunzione': 'Copiato da Alfa Software S.r.l.: verifica',
      'datiContrattuali.qualifica': 'Copiato da Luglio 2025: verifica',
      voci: 'Copiato da Luglio 2025: verifica',
    });
    expect(risultato.copiati).not.toHaveProperty('datiContrattuali.livello');
  });

  it('cambiando azienda sostituisce i dati copiati e conserva quelli modificati', () => {
    const iniziale = agosto();
    const conAlfa = applicaScelta(
      conScelte(iniziale.valori, mario.id, alfa.id),
      iniziale.copiati,
      { persona: true, azienda: true },
      { persona: mario, azienda: alfa, rapporto: rapportoAlfa, riferimento: riferimentoDi(rapportoAlfa) },
    );
    // l'utente corregge la qualifica (non e' piu' tra i copiati), completa una voce e ne aggiunge una
    const modificati = structuredClone(conAlfa.valori);
    modificati.datiContrattuali.qualifica = 'Quadro';
    modificati.voci[0] = { ...modificati.voci[0]!, importoCompetenza: 2100 };
    modificati.voci.push({ ...voceVuota(), descrizione: 'Premio' });
    const copiatiRimasti = Object.fromEntries(
      Object.entries(conAlfa.copiati).filter(([percorso]) => percorso !== 'datiContrattuali.qualifica'),
    );

    const conBeta = applicaScelta(
      conScelte(modificati, mario.id, beta.id),
      copiatiRimasti,
      { persona: false, azienda: true },
      { persona: mario, azienda: beta, rapporto: rapportoBeta, riferimento: riferimentoDi(rapportoBeta) },
    );
    const { datoreLavoro, datiContrattuali, voci, periodo } = conBeta.valori;
    expect(datoreLavoro).toMatchObject({
      ragioneSociale: 'Beta Consulting S.p.A.',
      partitaIva: '09876543217',
      indirizzo: 'Corso Italia 10, 20100 Milano',
    });
    expect(datiContrattuali).toMatchObject({
      dataAssunzione: '2018-09-03',
      qualifica: 'Quadro',
      livello: 'C3',
    });
    expect(periodo).toMatchObject({ tipo: 'ORDINARIA', mese: '8', anno: 2025 });
    // restano la voce completata e quella aggiunta; le copie non toccate spariscono (la lista non e' piu' vuota)
    expect(voci.map((v) => v.descrizione)).toEqual([conAlfa.valori.voci[0]!.descrizione, 'Premio']);
    expect(conBeta.copiati['datiContrattuali.dataAssunzione']).toBe(
      'Copiato da Beta Consulting S.p.A.: verifica',
    );
    expect(conBeta.copiati).not.toHaveProperty('datiContrattuali.qualifica');
    expect(conBeta.copiati).not.toHaveProperty('voci');
  });

  it('«Crea nuovo» svuota i dati anagrafici e i dati copiati dalla scelta precedente', () => {
    const iniziale = agosto();
    const conMario = applicaScelta(
      conScelte(iniziale.valori, mario.id, alfa.id),
      iniziale.copiati,
      { persona: true, azienda: true },
      { persona: mario, azienda: alfa, rapporto: rapportoAlfa, riferimento: riferimentoDi(rapportoAlfa) },
    );
    const nuova = applicaScelta(
      { ...conMario.valori, personaId: null, nuovaPersona: true },
      conMario.copiati,
      { persona: true, azienda: false },
      { persona: null, azienda: alfa, rapporto: null, riferimento: null },
    );
    expect(nuova.valori.dipendente).toMatchObject({
      codiceFiscale: '',
      nome: '',
      cognome: '',
      dataNascita: '',
    });
    expect(nuova.valori.datoreLavoro).toMatchObject({
      ragioneSociale: 'Alfa Software S.r.l.',
      indirizzo: 'Via Roma 1, 00100 Roma',
    });
    expect(nuova.valori.datiContrattuali.dataAssunzione).toBe('');
    expect(nuova.valori.voci).toEqual([]);
    expect(nuova.valori.periodo.mese).toBe('8');
  });

  it('liberando i copiati tiene il periodo del mese associato e le righe dell’utente', () => {
    const { valori, copiati } = agosto();
    valori.contieneTredicesima = true;
    valori.datiContrattuali.qualifica = 'Impiegato';
    const copiata = daCedolinoRiferimento(riferimentoDi(rapportoAlfa).cedolino, 2025).voci![0]!;
    valori.voci = [copiata, { ...voceVuota(), descrizione: 'Premio' }];
    const liberati = liberaCopiati(valori, {
      ...copiati,
      contieneTredicesima: 'nota',
      'datiContrattuali.qualifica': 'nota',
      voci: 'nota',
    });
    expect(liberati.valori.periodo).toMatchObject({ tipo: 'ORDINARIA', mese: '8', anno: 2025 });
    expect(liberati.valori.contieneTredicesima).toBe(true);
    expect(liberati.valori.datiContrattuali.qualifica).toBe('');
    expect(liberati.valori.voci.map((v) => v.descrizione)).toEqual(['Premio']);
    expect(Object.keys(liberati.copiati)).toEqual([
      'periodo.tipo',
      'periodo.mese',
      'periodo.anno',
      'contieneTredicesima',
    ]);
  });
});

describe('valori iniziali del modulo', () => {
  const attribuzione = {
    personaId: mario.id,
    titolo: 'Agosto 2025',
    competenza: {
      chiave: '2025.08',
      tipo: 'ORDINARIA' as const,
      anno: 2025,
      mese: 8,
      progressivoTfr: null,
    },
  };

  it('con il mese associato: periodo, persona e azienda del rapporto già scelte e dati precompilati', () => {
    const { valori, copiati } = valoriIniziali(
      attribuzione,
      { persone, aziende },
      rapportoAlfa,
      riferimentoDi(rapportoAlfa),
    );
    expect(valori).toMatchObject({
      personaId: String(mario.id),
      nuovaPersona: false,
      aziendaId: String(alfa.id),
      nuovaAzienda: false,
      periodo: { tipo: 'ORDINARIA', mese: '8', anno: 2025 },
      dipendente: { codiceFiscale: 'RSSMRA80A01H501U', nome: 'Mario' },
      datoreLavoro: { ragioneSociale: 'Alfa Software S.r.l.', indirizzo: 'Via Roma 1, 00100 Roma' },
      datiContrattuali: { dataAssunzione: '2022-03-01', qualifica: 'Impiegato' },
    });
    expect(valori.voci.length).toBeGreaterThan(0);
    expect(copiati['periodo.anno']).toBe('Dal mese associato al file (Agosto 2025): verifica');
    expect(copiati).not.toHaveProperty('dipendente.codiceFiscale');
  });

  it('senza associazione nulla è scelto; con un archivio vuoto «Crea nuovo» è già attivo', () => {
    const senza = valoriIniziali(null, { persone, aziende }, undefined, null);
    expect(senza.valori).toEqual(valoriVuoti());
    expect(senza.copiati).toEqual({});
    const vuoto = valoriIniziali(null, { persone: [], aziende: [] }, undefined, null);
    expect(vuoto.valori).toMatchObject({
      personaId: null,
      nuovaPersona: true,
      aziendaId: null,
      nuovaAzienda: true,
    });
    // elenchi non letti: si sceglie dopo, quando la tendina li ha
    const nonLetti = valoriIniziali(
      attribuzione,
      { persone: undefined, aziende: undefined },
      rapportoAlfa,
      null,
    );
    expect(nonLetti.valori).toMatchObject({ personaId: null, nuovaPersona: false, aziendaId: null });
  });

  it('una persona non più in elenco non è scelta, e senza coppia nulla viene dal rapporto', () => {
    const { valori } = valoriIniziali(
      attribuzione,
      { persone: persone.filter((p) => p.id !== mario.id), aziende },
      rapportoAlfa,
      riferimentoDi(rapportoAlfa),
    );
    expect(valori).toMatchObject({ personaId: null, aziendaId: String(alfa.id) });
    expect(valori.dipendente.codiceFiscale).toBe('');
    expect(valori.datoreLavoro.indirizzo).toBe('Via Roma 1, 00100 Roma');
    expect(valori.datiContrattuali.dataAssunzione).toBe('');
    expect(valori.voci).toEqual([]);
  });
});
