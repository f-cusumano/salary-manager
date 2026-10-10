import { describe, expect, it } from 'vitest';
import { creaApiClient, type ApiClient } from '../api/client';
import { leggiPdfCaricamento } from '../api/download';
import { ApiError, erroriDiCampo, esito, messaggioErrore } from '../api/errors';
import { avviaCaricamento, leggiLotto, rileggiCaricamento } from '../api/queries';
import type { CaricamentoInErrore, CedolinoManuale, LottoCaricamento } from '../api/types';
import { cedolinoManuale } from '../test/cedolinoManuale';
import { creaMockBackend } from './backend';
import { chiaveFile, type DatiIniziali } from './dati';

// Solo dati inventati: Mario Rossi e Alfa Software sono le anagrafiche fittizie del backend simulato.

function avvia(datiIniziali: DatiIniziali = 'errori') {
  const backend = creaMockBackend({ datiIniziali });
  return { backend, api: creaApiClient(backend.fetch) };
}

async function inErrore(api: ApiClient): Promise<CaricamentoInErrore[]> {
  return esito(await api.GET('/api/v1/caricamenti/in-errore'));
}

async function fileDi(api: ApiClient, nome: string): Promise<CaricamentoInErrore> {
  const file = (await inErrore(api)).find((c) => c.nomeFileOriginale === nome);
  if (!file) {
    throw new Error(`${nome} non è tra i file in errore`);
  }
  return file;
}

async function rapporto(api: ApiClient, rapportoId: number) {
  return esito(await api.GET('/api/v1/rapporti/{rapportoId}', { params: { path: { rapportoId } } }));
}

async function attendiFine(api: ApiClient, lotto: LottoCaricamento): Promise<LottoCaricamento> {
  for (let passo = 0; passo < 50; passo++) {
    const stato = await leggiLotto(api, lotto.lotto);
    if (stato.completato) {
      return stato;
    }
  }
  throw new Error('lotto non concluso');
}

async function carica(api: ApiClient, nomi: string[], password: string | null = null) {
  const files = nomi.map((nome) => new File(['%PDF-1.4 finto'], nome, { type: 'application/pdf' }));
  return attendiFine(api, await avviaCaricamento(api, { files, password }));
}

async function errore(chiamata: Promise<unknown>): Promise<ApiError> {
  const risultato: unknown = await chiamata.then(
    () => null,
    (e: unknown) => e,
  );
  if (!(risultato instanceof ApiError)) {
    throw new Error('attesa una risposta di errore');
  }
  return risultato;
}

function vuoto(): CedolinoManuale {
  return cedolinoManuale((d) => {
    d.dipendente = {
      codiceFiscale: null,
      nome: null,
      cognome: null,
      dataNascita: null,
      codiceDipendente: null,
    };
    d.datoreLavoro.ragioneSociale = '  ';
    d.datoreLavoro.partitaIva = null;
    d.periodo = { anno: null, mese: null, tipo: null, dataElaborazione: null, meseVariabili: null };
    d.totali = {
      lordo: null,
      totaleCompetenze: null,
      totaleTrattenute: null,
      arrotondamento: null,
      netto: null,
    };
    d.voci = [];
  });
}

function verifica(api: ApiClient, caricamentoId: number, dati: CedolinoManuale) {
  return api
    .POST('/api/v1/caricamenti/{caricamentoId}/cedolino/verifica', {
      params: { path: { caricamentoId } },
      body: dati,
    })
    .then((r) => esito(r));
}

function inserisci(api: ApiClient, caricamentoId: number, dati: CedolinoManuale) {
  return api
    .POST('/api/v1/caricamenti/{caricamentoId}/cedolino', { params: { path: { caricamentoId } }, body: dati })
    .then((r) => esito(r));
}

function associa(api: ApiClient, caricamentoId: number, rapportoId: number, anno: number, mese: number) {
  return api
    .PUT('/api/v1/caricamenti/{caricamentoId}/attribuzione', {
      params: { path: { caricamentoId } },
      body: { rapportoId, anno, mese },
    })
    .then((r) => esito(r));
}

describe('backend simulato: cedolini caricati con errore', () => {
  it('il dataset "errori" elenca tre file in errore dal più recente, con il PDF conservato', async () => {
    const { api } = avvia();

    const elenco = await inErrore(api);

    expect(elenco.map((c) => [c.nomeFileOriginale, c.motivo, c.pdfDisponibile, c.inElaborazione])).toEqual([
      ['busta-scansione.pdf', 'PDF_SENZA_TESTO', true, false],
      ['cedolino-protetto.pdf', 'PDF_PROTETTO', true, false],
      ['ristampa.pdf', 'LAYOUT_NON_RICONOSCIUTO', true, false],
    ]);
    expect(elenco.find((c) => c.nomeFileOriginale === 'ristampa.pdf')?.attribuzione).toMatchObject({
      rapportoId: 1,
      personaId: 1,
      persona: 'Mario Rossi',
      azienda: 'Alfa Software S.r.l.',
      titolo: 'Agosto 2025',
      competenza: { chiave: '2025.08', tipo: 'ORDINARIA' },
      competenzaPresente: false,
    });
    expect(elenco.filter((c) => c.attribuzione === null)).toHaveLength(2);
  });

  it('i dati "demo" e "vuoto" restano senza file in errore', async () => {
    expect(await inErrore(avvia('demo').api)).toEqual([]);
    expect(await inErrore(avvia('vuoto').api)).toEqual([]);
  });

  it('la mensilità associata diventa IN_ERRORE con la segnalazione del cedolino in errore, e torna mancante', async () => {
    const { api, backend } = avvia();
    const ristampa = await fileDi(api, 'ristampa.pdf');

    const associato = await rapporto(api, 1);
    expect(associato.mensilita.find((r) => r.chiave === '2025.08')).toMatchObject({
      stato: 'IN_ERRORE',
      titolo: 'Agosto 2025',
      periodoRetribuzione: '2025-08',
      cedolinoId: null,
      caricamentoId: ristampa.id,
      motivoErrore: 'LAYOUT_NON_RICONOSCIUTO',
    });
    expect(associato.segnalazioni.map((s) => s.tipo)).toEqual(['CEDOLINO_IN_ERRORE']);
    expect(associato.segnalazioni[0]).toMatchObject({
      gravita: 'AVVISO',
      titolo: 'Cedolino caricato con errore: Agosto 2025',
      competenza: { chiave: '2025.08' },
      evidenze: [{ descrizione: 'Motivo', valore: 'Layout non riconosciuto' }],
    });
    // il dato salvato non cambia: la riga in errore e' composta in lettura, come nel backend
    expect(backend.db.rapporti.get(1)?.mensilita.find((r) => r.chiave === '2025.08')?.stato).toBe('MANCANTE');

    const aggiornato = esito(
      await api.DELETE('/api/v1/caricamenti/{caricamentoId}/attribuzione', {
        params: { path: { caricamentoId: ristampa.id } },
      }),
    );
    expect(aggiornato.attribuzione).toBeNull();
    const ripristinato = await rapporto(api, 1);
    expect(ripristinato.mensilita.find((r) => r.chiave === '2025.08')?.stato).toBe('MANCANTE');
    expect(ripristinato.segnalazioni.map((s) => s.tipo)).toEqual(['MENSILITA_MANCANTE']);
  });

  it('l’esito del caricamento dipende dal nome: motivo e PDF conservato solo se completabile', async () => {
    const { api } = avvia('demo');

    const lotto = await carica(api, [
      'busta-scansione-1.pdf',
      'ristampa-2.pdf',
      'cedolino-protetto-3.pdf',
      'danneggiato.pdf',
      'errore.pdf',
      'settembre.pdf',
    ]);

    expect(lotto.caricamenti.map((c) => [c.stato, c.motivo, c.pdfDisponibile])).toEqual([
      ['ERRORE', 'PDF_SENZA_TESTO', true],
      ['ERRORE', 'LAYOUT_NON_RICONOSCIUTO', true],
      ['ERRORE', 'PDF_PROTETTO', true],
      ['ERRORE', 'PDF_NON_VALIDO', false],
      ['ERRORE', 'PIU_CEDOLINI', false],
      ['ACQUISITO', null, false],
    ]);
    expect(lotto.caricamenti[3]?.messaggio).toBe('Il PDF è danneggiato o non leggibile');
    // anche i file senza PDF sono in errore: si possono solo associare, rimuovere o ricaricare
    expect((await inErrore(api)).map((c) => [c.nomeFileOriginale, c.pdfDisponibile])).toEqual(
      expect.arrayContaining([
        ['danneggiato.pdf', false],
        ['errore.pdf', false],
        ['busta-scansione-1.pdf', true],
      ]),
    );
    expect(await inErrore(api)).toHaveLength(5);
  });

  it('lo stesso file caricato due volte compare una volta sola, con l’ultimo tentativo', async () => {
    const { api, backend } = avvia('demo');

    await carica(api, ['scansione.pdf']);
    const secondo = await carica(api, ['scansione.pdf']);

    const elenco = await inErrore(api);
    expect(elenco).toHaveLength(1);
    expect(elenco[0]?.id).toBe(secondo.caricamenti[0]?.id);
    expect(backend.db.pdfFile.size).toBe(1);
  });

  it('serve il PDF conservato con il nome originale; senza PDF risponde 404', async () => {
    const { api } = avvia();
    const scansione = await fileDi(api, 'busta-scansione.pdf');

    const pdf = await leggiPdfCaricamento(api, scansione.id);
    expect(pdf.nome).toBe('busta-scansione.pdf');
    expect(pdf.contenuto.type).toBe('application/pdf');
    expect(await pdf.contenuto.text()).toMatch(/^%PDF-1\.4/);

    await carica(api, ['danneggiato.pdf']);
    const danneggiato = await fileDi(api, 'danneggiato.pdf');
    expect((await errore(leggiPdfCaricamento(api, danneggiato.id))).status).toBe(404);
  });

  it('rimuove un file in errore con tutti i suoi tentativi, il PDF e l’associazione', async () => {
    const { api, backend } = avvia();
    const ristampa = await fileDi(api, 'ristampa.pdf');
    const chiave = chiaveFile(ristampa);

    const risposta = await api.DELETE('/api/v1/caricamenti/{caricamentoId}', {
      params: { path: { caricamentoId: ristampa.id } },
    });

    expect(risposta.response.status).toBe(204);
    expect((await inErrore(api)).map((c) => c.nomeFileOriginale)).not.toContain('ristampa.pdf');
    expect(backend.db.caricamenti.some((c) => chiaveFile(c) === chiave)).toBe(false);
    expect(backend.db.pdfFile.has(chiave)).toBe(false);
    expect(backend.db.associazioni.has(chiave)).toBe(false);
    expect((await rapporto(api, 1)).mensilita.find((r) => r.chiave === '2025.08')?.stato).toBe('MANCANTE');

    const ancora = await api.DELETE('/api/v1/caricamenti/{caricamentoId}', {
      params: { path: { caricamentoId: ristampa.id } },
    });
    expect(ancora.response.status).toBe(404);
  });

  it('associa un file con i controlli del backend', async () => {
    const { api } = avvia();
    const scansione = await fileDi(api, 'busta-scansione.pdf');

    const occupata = await errore(associa(api, scansione.id, 1, 2025, 8));
    expect(occupata.status).toBe(409);
    expect(messaggioErrore(occupata)).toBe('Agosto 2025 è già associato a un altro file in errore');

    const presente = await errore(associa(api, scansione.id, 1, 2025, 9));
    expect(messaggioErrore(presente)).toBe('Per Settembre 2025 è già presente un cedolino');

    const nonValida = await errore(associa(api, scansione.id, 1, 2025, 15));
    expect(nonValida.status).toBe(400);
    // stessi campi e testi del backend
    expect(erroriDiCampo(nonValida)).toEqual([
      {
        campo: 'mese',
        messaggio: 'Indica il mese (da 1 a 12, 13 per la tredicesima, 14 per la quattordicesima)',
      },
    ]);
    const senzaRapporto = await errore(associa(api, scansione.id, 0, 2025, 10));
    expect(erroriDiCampo(senzaRapporto)).toEqual([
      { campo: 'rapportoId', messaggio: 'Indica il rapporto di lavoro' },
    ]);

    expect((await errore(associa(api, scansione.id, 999, 2025, 10))).status).toBe(404);

    // fuori dal periodo atteso: la tredicesima 2026 non e' tra le mensilita' mancanti
    const associato = await associa(api, scansione.id, 1, 2026, 13);
    expect(associato.attribuzione).toMatchObject({ titolo: 'Tredicesima 2026', competenzaPresente: false });
    const dettaglio = await rapporto(api, 1);
    expect(dettaglio.mensilita.find((r) => r.chiave === '2026.13')).toMatchObject({
      stato: 'IN_ERRORE',
      periodoRetribuzione: null,
      caricamentoId: scansione.id,
    });
    expect(dettaglio.segnalazioni.map((s) => s.titolo)).toContain(
      'Cedolino caricato con errore: Tredicesima 2026',
    );
  });

  it('l’associazione sparisce con il rapporto quando se ne elimina l’ultimo cedolino', async () => {
    const { api } = avvia();
    const scansione = await fileDi(api, 'busta-scansione.pdf');
    await associa(api, scansione.id, 3, 2025, 10);

    for (const riga of (await rapporto(api, 3)).mensilita) {
      if (riga.cedolinoId !== null) {
        await api.DELETE('/api/v1/cedolini/{cedolinoId}', {
          params: { path: { cedolinoId: riga.cedolinoId } },
        });
      }
    }

    expect((await fileDi(api, 'busta-scansione.pdf')).attribuzione).toBeNull();
  });
});

describe('backend simulato: inserimento a mano', () => {
  it('rifiuta i dati obbligatori mancanti con gli errori sui campi, senza salvare nulla', async () => {
    const { api, backend } = avvia();
    const ristampa = await fileDi(api, 'ristampa.pdf');
    const cedolini = backend.db.cedolini.size;

    const rifiuto = await errore(inserisci(api, ristampa.id, vuoto()));

    expect(rifiuto.status).toBe(400);
    // nell'ordine dei controlli del backend (ordine dello schema)
    expect(erroriDiCampo(rifiuto).map((e) => e.campo)).toEqual([
      'dipendente.codiceFiscale',
      'dipendente.nome',
      'dipendente.cognome',
      'datoreLavoro.ragioneSociale',
      'periodo.anno',
      'periodo.mese',
      'periodo.tipo',
      'totali.totaleCompetenze',
      'totali.totaleTrattenute',
      'totali.netto',
    ]);
    expect(messaggioErrore(rifiuto)).toBe('10 campi da correggere');
    expect(backend.db.cedolini.size).toBe(cedolini);
    expect((await fileDi(api, 'ristampa.pdf')).id).toBe(ristampa.id);
  });

  it('segnala i valori non validi con i percorsi dello schema, senza ripeterli', async () => {
    const { api } = avvia();
    const ristampa = await fileDi(api, 'ristampa.pdf');
    const dati = cedolinoManuale((d) => {
      d.dipendente.codiceFiscale = 'ABCDEF';
      d.datoreLavoro.partitaIva = '123';
      d.voci.push({
        codice: null,
        descrizione: null,
        categoria: null,
        quantita: null,
        unita: null,
        base: 1.123456,
        importoCompetenza: -5,
        importoTrattenuta: null,
      });
      d.ratei = [
        {
          tipo: 'FERIE',
          unita: 'GIORNI',
          residuoAnnoPrecedente: 1,
          maturato: null,
          goduto: null,
          saldo: null,
        },
        {
          tipo: 'FERIE',
          unita: 'GIORNI',
          residuoAnnoPrecedente: null,
          maturato: null,
          goduto: null,
          saldo: null,
        },
      ];
    });

    const errori = erroriDiCampo(await errore(verifica(api, ristampa.id, dati)));

    // al piu' un errore per campo (il primo controllato), nell'ordine dei controlli del backend
    expect(errori).toEqual([
      { campo: 'dipendente.codiceFiscale', messaggio: 'Codice fiscale non valido: controlla i 16 caratteri' },
      { campo: 'datoreLavoro.partitaIva', messaggio: 'Partita IVA non valida: deve avere 11 cifre' },
      { campo: 'voci[2].descrizione', messaggio: 'Indica la descrizione della voce' },
      { campo: 'voci[2].categoria', messaggio: 'Indica la categoria della voce' },
      { campo: 'voci[2].base', messaggio: 'Valore fuori dai limiti (massimo 10 cifre intere e 5 decimali)' },
      { campo: 'voci[2].importoCompetenza', messaggio: "Indica l'importo senza segno" },
      { campo: 'ratei[1]', messaggio: 'Rateo ripetuto: uno solo per tipo e unità' },
    ]);
    expect(errori.map((e) => e.messaggio).join(' ')).not.toMatch(/ABCDEF|123/);
  });

  it('verifica e inserisce il cedolino della mensilità associata', async () => {
    const { api, backend } = avvia();
    const ristampa = await fileDi(api, 'ristampa.pdf');

    const prova = await verifica(api, ristampa.id, cedolinoManuale());
    expect(prova).toEqual({
      esito: 'ACQUISIBILE',
      messaggio: null,
      competenza: expect.objectContaining({ chiave: '2025.08', tipo: 'ORDINARIA' }) as unknown,
      titolo: 'Agosto 2025',
      nuovaPersona: false,
      nuovoRapporto: false,
      rapportoId: 1,
      cedolinoEsistenteId: null,
      avvisi: [],
    });
    expect(backend.db.rapporti.get(1)?.mensilita.find((r) => r.chiave === '2025.08')?.stato).toBe('MANCANTE');

    const risposta = await api.POST('/api/v1/caricamenti/{caricamentoId}/cedolino', {
      params: { path: { caricamentoId: ristampa.id } },
      body: cedolinoManuale(),
    });
    const caricamento = esito(risposta);

    expect(risposta.response.status).toBe(201);
    expect(caricamento).toMatchObject({
      id: ristampa.id,
      stato: 'ACQUISITO',
      motivo: null,
      pdfDisponibile: false,
      personaId: 1,
      rapportoId: 1,
    });
    expect(risposta.response.headers.get('Location')).toBe(
      `/api/v1/cedolini/${String(caricamento.cedolinoId)}`,
    );
    expect((await inErrore(api)).map((c) => c.nomeFileOriginale)).not.toContain('ristampa.pdf');
    const dettaglio = await rapporto(api, 1);
    expect(dettaglio.mensilita.find((r) => r.chiave === '2025.08')).toMatchObject({
      stato: 'PRESENTE',
      cedolinoId: caricamento.cedolinoId,
      netto: 1650,
    });
    expect(dettaglio.segnalazioni).toEqual([]);
    const cedolino = esito(
      await api.GET('/api/v1/cedolini/{cedolinoId}', {
        params: { path: { cedolinoId: caricamento.cedolinoId ?? 0 } },
      }),
    );
    expect(cedolino).toMatchObject({ inseritoAMano: true, confidenza: null, titolo: 'Agosto 2025' });
    expect(cedolino.competenze.map((v) => v.descrizione)).toEqual(['Retribuzione ordinaria']);
    expect(cedolino.trattenutePrevidenziali.map((v) => v.importoTrattenuta)).toEqual([450]);

    const ancora = await errore(verifica(api, ristampa.id, cedolinoManuale()));
    expect(ancora.status).toBe(409);
    expect(messaggioErrore(ancora)).toBe(
      'Il file non è più in errore: è già stato inserito, rimosso o ricaricato',
    );
  });

  it('riconosce duplicati e aggiuntivi di una mensilità già presente: il file resta in errore', async () => {
    const { api, backend } = avvia();
    const scansione = await fileDi(api, 'busta-scansione.pdf');
    const settembre = backend.db.rapporti.get(1)?.mensilita.find((r) => r.chiave === '2025.09');
    const esistente = backend.db.cedolini.get(settembre?.cedolinoId ?? 0);
    if (!esistente) {
      throw new Error('cedolino di Settembre 2025 mancante');
    }
    const stessiTotali = cedolinoManuale((d) => {
      d.periodo.mese = 9;
      d.totali = { ...esistente.totali };
    });

    const duplicato = await verifica(api, scansione.id, stessiTotali);
    expect(duplicato).toMatchObject({
      esito: 'DUPLICATO',
      messaggio: 'Cedolino di Settembre 2025 già presente con gli stessi importi: scartato',
      competenza: null,
      titolo: null,
      cedolinoEsistenteId: esistente.id,
    });

    const diverso = cedolinoManuale((d) => {
      d.periodo.mese = 9;
    });
    expect((await verifica(api, scansione.id, diverso)).esito).toBe('AGGIUNTIVO_NON_GESTITO');
    const rifiuto = await errore(inserisci(api, scansione.id, diverso));
    expect(rifiuto.status).toBe(409);
    expect(messaggioErrore(rifiuto)).toMatch(
      /^Per Settembre 2025 è già presente un cedolino con importi diversi/,
    );
    expect((await fileDi(api, 'busta-scansione.pdf')).pdfDisponibile).toBe(true);
  });

  it('crea persona, azienda e rapporto nuovi e lo dice nella verifica', async () => {
    const { api } = avvia();
    const scansione = await fileDi(api, 'busta-scansione.pdf');
    const nuova = cedolinoManuale((d) => {
      d.dipendente = {
        codiceFiscale: 'NRENNA90A41F205Y',
        nome: 'Anna',
        cognome: 'Neri',
        dataNascita: '1990-01-01',
        codiceDipendente: null,
      };
      d.datoreLavoro.ragioneSociale = 'Delta Servizi S.r.l.';
      d.datoreLavoro.partitaIva = '22334455668';
      d.datiContrattuali.dataAssunzione = '2025-06-01';
      d.voci = [];
    });

    const prova = await verifica(api, scansione.id, nuova);
    expect(prova).toMatchObject({
      esito: 'ACQUISIBILE',
      nuovaPersona: true,
      nuovoRapporto: true,
      rapportoId: null,
    });
    expect(prova.avvisi).toEqual([
      'Verrà creata una nuova persona: controlla il codice fiscale',
      "Verrà creato un nuovo rapporto di lavoro: se l'azienda esiste già controlla ragione sociale, partita IVA e " +
        'codice fiscale',
    ]);

    const caricamento = await inserisci(api, scansione.id, nuova);

    const persone = esito(await api.GET('/api/v1/persone'));
    expect(persone.map((p) => p.etichetta)).toEqual(['Laura Bianchi', 'Anna Neri', 'Mario Rossi']);
    expect(persone.find((p) => p.etichetta === 'Anna Neri')?.id).toBe(caricamento.personaId);
    const nuovo = await rapporto(api, caricamento.rapportoId ?? 0);
    expect(nuovo).toMatchObject({
      etichetta: 'Delta Servizi S.r.l.',
      periodo: { da: '2025-06', a: null, fonteInizio: 'DATA_CONTRATTUALE' },
      ccnlDaIndicare: true,
    });
    expect(nuovo.mensilita.map((r) => [r.titolo, r.stato])).toEqual([['Agosto 2025', 'PRESENTE']]);
  });

  it('salva gli avvisi di coerenza con il cedolino', async () => {
    const { api } = avvia();
    const scansione = await fileDi(api, 'busta-scansione.pdf');
    const incoerente = cedolinoManuale((d) => {
      d.periodo.mese = 10;
      d.totali.netto = 1500;
      d.voci = d.voci.slice(0, 1);
    });

    const prova = await verifica(api, scansione.id, incoerente);

    expect(prova.avvisi).toEqual([
      'Totale competenze meno totale trattenute non corrisponde al netto',
      'La somma delle voci di trattenuta non corrisponde al totale trattenute stampato',
    ]);
  });
});

describe('backend simulato: rilettura', () => {
  it('con la password acquisisce il PDF protetto; durante la lettura le azioni sono bloccate', async () => {
    const { api, backend } = avvia();
    const protetto = await fileDi(api, 'cedolino-protetto.pdf');

    const lotto = await rileggiCaricamento(api, protetto.id, 'parola-segreta');

    expect(lotto.completato).toBe(false);
    expect(lotto.caricamenti.map((c) => [c.nomeFileOriginale, c.stato])).toEqual([
      ['cedolino-protetto.pdf', 'IN_CODA'],
    ]);
    expect((await fileDi(api, 'cedolino-protetto.pdf')).inElaborazione).toBe(true);
    const bloccato = await errore(
      api
        .DELETE('/api/v1/caricamenti/{caricamentoId}', { params: { path: { caricamentoId: protetto.id } } })
        .then((r) => esito(r)),
    );
    expect(messaggioErrore(bloccato)).toBe('Il file è in elaborazione: attendi la fine');

    const fine = await attendiFine(api, lotto);

    expect(fine.caricamenti[0]?.stato).toBe('ACQUISITO');
    expect((await inErrore(api)).map((c) => c.nomeFileOriginale)).not.toContain('cedolino-protetto.pdf');
    expect(backend.db.pdfFile.has(chiaveFile(protetto))).toBe(false);
  });

  it('senza esito utile il file resta in errore con il nuovo tentativo e il PDF', async () => {
    const { api } = avvia();
    const scansione = await fileDi(api, 'busta-scansione.pdf');

    const fine = await attendiFine(api, await rileggiCaricamento(api, scansione.id, ''));

    const dopo = await fileDi(api, 'busta-scansione.pdf');
    expect(dopo.id).toBe(fine.caricamenti[0]?.id);
    expect(dopo.id).not.toBe(scansione.id);
    expect(dopo.pdfDisponibile).toBe(true);
  });

  it('senza PDF conservato non si può rileggere', async () => {
    const { api } = avvia('demo');
    await carica(api, ['danneggiato.pdf']);
    const danneggiato = await fileDi(api, 'danneggiato.pdf');

    const rifiuto = await errore(rileggiCaricamento(api, danneggiato.id, null));

    expect(rifiuto.status).toBe(409);
    expect(messaggioErrore(rifiuto)).toBe('PDF del file non conservato: caricalo di nuovo');
  });
});
