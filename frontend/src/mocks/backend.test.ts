import { describe, expect, it } from 'vitest';
import { creaApiClient, type ApiClient } from '../api/client';
import { leggiPdfCaricamento } from '../api/download';
import { ApiError, erroriDiCampo, esito, messaggioErrore } from '../api/errors';
import { avviaCaricamento, leggiLotto, rileggiCaricamento } from '../api/queries';
import type { CaricamentoInErrore, CedolinoManuale, LottoCaricamento } from '../api/types';
import { cedolinoManuale } from '../test/cedolinoManuale';
import { creaMockBackend } from './backend';
import { chiaveFile, PDF_MINIMO, type DatiIniziali } from './dati';

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

/** Anagrafiche nuove ("Crea nuovo"), senza dati. */
function vuoto(): CedolinoManuale {
  return cedolinoManuale((d) => {
    d.personaId = null;
    d.aziendaId = null;
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
      descrizione:
        'Il cedolino di Agosto 2025 è stato caricato ma non è stato letto (Layout non riconosciuto): ' +
        "inseriscilo a mano dall'elenco «Cedolini caricati con errore» o riprova la lettura.",
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

  it('senza il PDF conservato la segnalazione propone solo di caricare di nuovo il file', async () => {
    const { api, backend } = avvia();
    const ristampa = await fileDi(api, 'ristampa.pdf');
    // come un errore registrato prima dei motivi: nessun PDF conservato
    for (const chiave of [...backend.db.pdfFile.keys()]) {
      backend.db.pdfFile.delete(chiave);
    }
    const riga = backend.db.caricamenti.find((c) => c.id === ristampa.id);
    if (riga) {
      riga.motivo = null;
      riga.pdfDisponibile = false;
    }

    const segnalazione = (await rapporto(api, 1)).segnalazioni.find((s) => s.tipo === 'CEDOLINO_IN_ERRORE');

    expect(segnalazione?.descrizione).toBe(
      'Il cedolino di Agosto 2025 è stato caricato ma non è stato letto: il PDF non è stato conservato, ' +
        'caricalo di nuovo.',
    );
    expect(segnalazione?.evidenze).toEqual([]);
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
      d.personaId = null;
      d.aziendaId = null;
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
      { campo: 'voci[2].base', messaggio: 'Valore fuori dai limiti (massimo 8 cifre intere e 5 decimali)' },
      { campo: 'voci[2].importoCompetenza', messaggio: "Indica l'importo senza segno" },
      { campo: 'ratei[1]', messaggio: 'Rateo ripetuto: uno solo per tipo e unità' },
    ]);
    expect(errori.map((e) => e.messaggio).join(' ')).not.toMatch(/ABCDEF|123/);
  });

  it('inserito a mano un tentativo, i successivi in errore dello stesso file diventano duplicati', async () => {
    const { api, backend } = avvia();
    const ristampa = await fileDi(api, 'ristampa.pdf');
    // lo stesso file (stesso nome e dimensione) caricato di nuovo e finito ancora in errore: e' il tentativo piu'
    // recente
    const stessoFile = new File([PDF_MINIMO], 'ristampa.pdf', { type: 'application/pdf' });
    await attendiFine(api, await avviaCaricamento(api, { files: [stessoFile], password: null }));
    const nuovo = await fileDi(api, 'ristampa.pdf');
    expect(nuovo.id).not.toBe(ristampa.id);

    const inserito = await inserisci(api, ristampa.id, cedolinoManuale());

    const successivo = backend.db.caricamenti.find((c) => c.id === nuovo.id);
    expect(successivo).toMatchObject({
      stato: 'DUPLICATO',
      motivo: null,
      messaggio: 'Lo stesso file è già stato caricato: scartato',
      cedolinoEsistenteId: inserito.cedolinoId,
      pdfDisponibile: false,
    });
    expect((await inErrore(api)).map((c) => c.nomeFileOriginale)).not.toContain('ristampa.pdf');
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
      nuovaAzienda: false,
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
      d.personaId = null;
      d.aziendaId = null;
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
      nuovaAzienda: true,
      nuovoRapporto: true,
      rapportoId: null,
    });
    // il nuovo rapporto non si annuncia: con persona o azienda nuove e' ovvio
    expect(prova.avvisi).toEqual([
      'Verrà creata una nuova persona: controlla il codice fiscale',
      'Verrà creata una nuova azienda: controlla ragione sociale, partita IVA e codice fiscale',
    ]);

    const caricamento = await inserisci(api, scansione.id, nuova);

    const persone = esito(await api.GET('/api/v1/persone'));
    expect(persone.map((p) => p.etichetta)).toEqual(['Laura Bianchi', 'Anna Neri', 'Mario Rossi']);
    expect(persone.find((p) => p.etichetta === 'Anna Neri')).toMatchObject({
      id: caricamento.personaId,
      dataNascita: '1990-01-01',
    });
    const aziende = esito(await api.GET('/api/v1/aziende'));
    expect(aziende.map((a) => a.etichetta)).toEqual([
      'Alfa Software S.r.l.',
      'Beta Consulting S.p.A.',
      'Delta Servizi S.r.l.',
      'Gamma Servizi S.r.l.',
    ]);
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

describe('backend simulato: persona e azienda dall’elenco', () => {
  it('elenca le aziende dei rapporti per ragione sociale, con l’identificativo solo per le omonime', async () => {
    const { api, backend } = avvia();

    expect(esito(await api.GET('/api/v1/aziende'))).toEqual([
      {
        id: 1,
        ragioneSociale: 'Alfa Software S.r.l.',
        partitaIva: '01234567897',
        codiceFiscale: '01234567897',
        posizioneInps: '1234567890',
        indirizzo: 'Via Roma 1, 00100 Roma',
        codiceAzienda: null,
        etichetta: 'Alfa Software S.r.l.',
      },
      expect.objectContaining({ id: 2, etichetta: 'Beta Consulting S.p.A.' }) as unknown,
      expect.objectContaining({ id: 3, etichetta: 'Gamma Servizi S.r.l.', codiceFiscale: null }) as unknown,
    ]);

    // un'omonima con un'altra partita IVA (identificativi in conflitto: non e' la stessa azienda)
    const scansione = await fileDi(api, 'busta-scansione.pdf');
    await inserisci(
      api,
      scansione.id,
      cedolinoManuale((d) => {
        d.aziendaId = null;
        d.datoreLavoro = {
          ...d.datoreLavoro,
          ragioneSociale: 'ALFA SOFTWARE SRL',
          partitaIva: '22334455668',
        };
      }),
    );
    // e un omonimo di Mario Rossi, con un altro codice fiscale
    backend.db.persone.push({
      id: 50,
      nome: 'Mario',
      cognome: 'Rossi',
      codiceFiscale: 'RSSMRA85C12F205M',
      dataNascita: null,
      etichetta: 'Mario Rossi',
    });

    const aziende = esito(await api.GET('/api/v1/aziende'));
    expect(aziende.map((a) => a.etichetta)).toEqual([
      'Alfa Software S.r.l. (P.IVA 01234567897)',
      'ALFA SOFTWARE SRL (P.IVA 22334455668)',
      'Beta Consulting S.p.A.',
      'Gamma Servizi S.r.l.',
    ]);
    // e una persona aggiunta per ultima: l'elenco e' per cognome e nome, come nel backend
    backend.db.persone.push({
      id: 51,
      nome: 'Anna',
      cognome: 'Abate',
      codiceFiscale: 'BTANNA90A41H501X',
      dataNascita: null,
      etichetta: 'Anna Abate',
    });
    const persone = esito(await api.GET('/api/v1/persone'));
    expect(persone.map((p) => p.etichetta)).toEqual([
      'Anna Abate',
      'Laura Bianchi',
      'Mario Rossi (RSSMRA80A01H501U)',
      'Mario Rossi (RSSMRA85C12F205M)',
    ]);
    expect(persone[1]).toMatchObject({ codiceFiscale: 'BNCLRA85M41F205X', dataNascita: '1985-08-01' });
  });

  it('usa i dati in archivio della persona e dell’azienda scelte, qualunque cosa sia stata inviata', async () => {
    const { api, backend } = avvia();
    const ristampa = await fileDi(api, 'ristampa.pdf');
    const conRefusi = cedolinoManuale((d) => {
      d.dipendente = { ...d.dipendente, codiceFiscale: 'RSSMRA80A01H501X', nome: 'Maro', cognome: '' };
      d.datoreLavoro = { ...d.datoreLavoro, ragioneSociale: 'Alfa Sofware', partitaIva: '123' };
    });

    expect(await verifica(api, ristampa.id, conRefusi)).toMatchObject({
      esito: 'ACQUISIBILE',
      nuovaPersona: false,
      nuovaAzienda: false,
      nuovoRapporto: false,
      rapportoId: 1,
      avvisi: [],
    });
    const caricamento = await inserisci(api, ristampa.id, conRefusi);

    expect(caricamento).toMatchObject({ personaId: 1, rapportoId: 1 });
    expect(backend.db.persone).toHaveLength(2);
    expect(esito(await api.GET('/api/v1/aziende'))).toHaveLength(3);
  });

  it('una persona o un’azienda nuova già in archivio va scelta dall’elenco: 400 sul campo', async () => {
    const { api, backend } = avvia();
    const scansione = await fileDi(api, 'busta-scansione.pdf');
    const cedolini = backend.db.cedolini.size;
    const campoInErrore = async (modifica: (d: CedolinoManuale) => void) =>
      erroriDiCampo(await errore(inserisci(api, scansione.id, cedolinoManuale(modifica))));

    expect(
      await campoInErrore((d) => {
        d.personaId = null;
        d.dipendente.codiceFiscale = ' rssmra80a01h501u';
      }),
    ).toEqual([
      {
        campo: 'dipendente.codiceFiscale',
        messaggio: "Questo codice fiscale è già in archivio: seleziona la persona dall'elenco",
      },
    ]);
    const azienda = (datore: Partial<CedolinoManuale['datoreLavoro']>) => (d: CedolinoManuale) => {
      d.aziendaId = null;
      d.datoreLavoro = {
        ...d.datoreLavoro,
        ragioneSociale: 'Delta Servizi S.r.l.',
        partitaIva: null,
        codiceFiscale: null,
        ...datore,
      };
    };
    expect(await campoInErrore(azienda({ partitaIva: '01234567897' }))).toEqual([
      {
        campo: 'datoreLavoro.partitaIva',
        messaggio: "Questa partita IVA è già in archivio: seleziona l'azienda dall'elenco",
      },
    ]);
    // codice fiscale numerico uguale alla partita IVA di un'azienda in archivio
    expect(await campoInErrore(azienda({ codiceFiscale: '09876543217' }))).toEqual([
      {
        campo: 'datoreLavoro.codiceFiscale',
        messaggio: "Questo codice fiscale è già in archivio: seleziona l'azienda dall'elenco",
      },
    ]);
    // anche con il prefisso IT, come lo toglie il backend
    expect(await campoInErrore(azienda({ codiceFiscale: 'IT09876543217' }))).toEqual([
      {
        campo: 'datoreLavoro.codiceFiscale',
        messaggio: "Questo codice fiscale è già in archivio: seleziona l'azienda dall'elenco",
      },
    ]);
    // la matricola e' il primo gruppo di 10 cifre, anche stampata con la posizione INAIL
    for (const posizioneInps of ['1234567890/01', '00123 1234567890']) {
      expect(await campoInErrore(azienda({ posizioneInps }))).toEqual([
        {
          campo: 'datoreLavoro.posizioneInps',
          messaggio: "Questa posizione INPS è già in archivio: seleziona l'azienda dall'elenco",
        },
      ]);
    }
    // ragione sociale normalizzata come nel censimento: sigle puntate e forme societarie per esteso
    for (const ragioneSociale of [
      'ALFA SOFTWARE SRL',
      'Alfa Software S. R. L. a socio unico',
      'Alfa Software Società a responsabilità limitata',
    ]) {
      expect(await campoInErrore(azienda({ ragioneSociale }))).toEqual([
        {
          campo: 'datoreLavoro.ragioneSociale',
          messaggio: "Un'azienda con questa ragione sociale è già in archivio: selezionala dall'elenco",
        },
      ]);
    }
    // il confronto con l'archivio solo con i dati del datore altrimenti corretti
    expect(await campoInErrore(azienda({ partitaIva: '01234567897', codiceFiscale: 'X' }))).toEqual([
      { campo: 'datoreLavoro.codiceFiscale', messaggio: "Codice fiscale dell'azienda non valido" },
    ]);
    expect(backend.db.cedolini.size).toBe(cedolini);
    expect((await fileDi(api, 'busta-scansione.pdf')).id).toBe(scansione.id);
  });

  it('come il censimento, identificativi in conflitto fanno un’azienda nuova anche con matricola o nome uguali', async () => {
    const { api } = avvia();
    const scansione = await fileDi(api, 'busta-scansione.pdf');
    const nuova = (datore: Partial<CedolinoManuale['datoreLavoro']>) =>
      cedolinoManuale((d) => {
        d.aziendaId = null;
        d.datoreLavoro = { ...d.datoreLavoro, partitaIva: null, codiceFiscale: null, ...datore };
      });

    // matricola INPS di Alfa Software, ma un'altra partita IVA
    const stessaMatricola = nuova({
      ragioneSociale: 'Delta Servizi S.r.l.',
      partitaIva: '55555555550',
      posizioneInps: '1234567890',
    });
    // stesso nome di Alfa Software, ma un'altra matricola INPS
    const stessoNome = nuova({ ragioneSociale: 'ALFA SOFTWARE SRL', posizioneInps: '5555555555' });
    for (const dati of [stessaMatricola, stessoNome]) {
      expect(await verifica(api, scansione.id, dati)).toMatchObject({
        esito: 'ACQUISIBILE',
        nuovaAzienda: true,
        nuovoRapporto: true,
      });
    }
    // un codice fiscale alfanumerico non contrasta con la sola partita IVA di Gamma Servizi: stessa azienda
    const rifiuto = await errore(
      verifica(
        api,
        scansione.id,
        nuova({ ragioneSociale: 'GAMMA SERVIZI SRL', codiceFiscale: 'GMMSRV80A01H501U' }),
      ),
    );
    expect(erroriDiCampo(rifiuto)).toEqual([
      {
        campo: 'datoreLavoro.ragioneSociale',
        messaggio: "Un'azienda con questa ragione sociale è già in archivio: selezionala dall'elenco",
      },
    ]);

    // l'azienda creata e' in elenco con la matricola come la salva il backend
    await inserisci(
      api,
      scansione.id,
      nuova({ ragioneSociale: 'Delta Servizi S.r.l.', posizioneInps: '7777777777/22' }),
    );
    expect(esito(await api.GET('/api/v1/aziende'))).toContainEqual(
      expect.objectContaining({ ragioneSociale: 'Delta Servizi S.r.l.', posizioneInps: '7777777777' }),
    );
  });

  it('una scelta non più in archivio dà i soli errori della scelta', async () => {
    const { api } = avvia();
    const scansione = await fileDi(api, 'busta-scansione.pdf');
    const sceltaPersa = cedolinoManuale((d) => {
      d.personaId = 999;
      d.aziendaId = 0;
      d.totali.netto = null;
    });

    const rifiuto = await errore(verifica(api, scansione.id, sceltaPersa));

    expect(rifiuto.status).toBe(400);
    expect(erroriDiCampo(rifiuto)).toEqual([
      {
        campo: 'personaId',
        messaggio: "La persona scelta non è più in archivio: scegline un'altra o creane una nuova",
      },
      {
        campo: 'aziendaId',
        messaggio: "L'azienda scelta non è più in archivio: scegline un'altra o creane una nuova",
      },
    ]);
    const malformato = await errore(
      api
        .POST('/api/v1/caricamenti/{caricamentoId}/cedolino/verifica', {
          params: { path: { caricamentoId: scansione.id } },
          body: { ...cedolinoManuale(), personaId: 'uno' as unknown as number },
        })
        .then((r) => esito(r)),
    );
    expect(malformato.status).toBe(400);
  });

  it('con un’azienda nuova o una coppia senza rapporto la verifica lo dice e il rapporto nasce', async () => {
    const { api, backend } = avvia();
    const scansione = await fileDi(api, 'busta-scansione.pdf');
    const aziendaNuova = cedolinoManuale((d) => {
      d.aziendaId = null;
      d.datoreLavoro = {
        ...d.datoreLavoro,
        ragioneSociale: 'Delta Servizi S.r.l.',
        partitaIva: '22334455668',
      };
    });
    expect(await verifica(api, scansione.id, aziendaNuova)).toMatchObject({
      nuovaPersona: false,
      nuovaAzienda: true,
      nuovoRapporto: true,
      rapportoId: null,
      avvisi: ['Verrà creata una nuova azienda: controlla ragione sociale, partita IVA e codice fiscale'],
    });

    // Laura Bianchi con Alfa Software: entrambe in archivio, nessun rapporto tra loro
    const coppiaNuova = cedolinoManuale((d) => (d.personaId = 2));
    const provaCoppia = await verifica(api, scansione.id, coppiaNuova);
    expect(provaCoppia).toMatchObject({ nuovaPersona: false, nuovaAzienda: false, nuovoRapporto: true });
    // (il codice fiscale inventato di Laura Bianchi non ha il carattere di controllo giusto: avviso di coerenza)
    expect(provaCoppia.avvisi.filter((a) => a.startsWith('Verrà'))).toEqual([
      "Verrà creato un nuovo rapporto di lavoro: la persona scelta non ha ancora cedolini con l'azienda scelta",
    ]);
    const caricamento = await inserisci(api, scansione.id, coppiaNuova);
    const nuovo = await rapporto(api, caricamento.rapportoId ?? 0);
    expect(nuovo.azienda.id).toBe(1);
    expect(nuovo.persona.id).toBe(2);
    expect(backend.db.persone).toHaveLength(2);
    expect(esito(await api.GET('/api/v1/aziende'))).toHaveLength(3);
  });

  it('avvisa se il cedolino andrebbe in un rapporto diverso da quello del mese associato', async () => {
    const { api } = avvia();
    const ristampa = await fileDi(api, 'ristampa.pdf');

    const prova = await verifica(
      api,
      ristampa.id,
      cedolinoManuale((d) => (d.aziendaId = 2)),
    );

    expect(prova).toMatchObject({ rapportoId: 2, nuovoRapporto: false });
    expect(prova.avvisi).toEqual([
      'Persona o azienda diverse da quelle del mese associato al file (Agosto 2025)',
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
