import type {
  Caricamento,
  ErroreCampo,
  LottoCaricamento,
  MotivoErrore,
  RapportoDettaglio,
  RapportoSintesi,
  StatoCaricamento,
} from '../api/types';
import { STATI_TERMINALI } from '../api/types';
import { elencoAziende, elencoPersone } from './anagrafiche';
import {
  CCNL_MOCK,
  MESSAGGI_ERRORE,
  PDF_MINIMO,
  chiaveFile,
  creaDbMock,
  popolaMario,
  rinumeraFineRapporto,
  trovaCcnl,
  type DatiIniziali,
  type MockDb,
} from './dati';
import {
  MESSAGGI_FILE_IN_ERRORE,
  RifiutoMock,
  associa,
  chiudiTentativo,
  eliminaAssociazioniDelRapporto,
  elencoInErrore,
  fileInErrore,
  rapportoConFileInErrore,
  rimuovi,
  rimuoviAssociazione,
} from './fileInErrore';
import {
  datiCedolino,
  inserisciManuale,
  modificaCedolino,
  verificaManuale,
  verificaModifica,
} from './inserimentoManuale';

/**
 * Backend simulato in memoria, usato SOLO in sviluppo (`npm run dev:mock`) e nei test: implementa il contratto
 * `docs/openapi.yaml` con dati inventati. In produzione non viene incluso nel bundle.
 */

export interface OpzioniMock {
  /** Ritardo artificiale delle risposte (ms), per vedere gli stati di caricamento. */
  ritardoMs?: number;
  /**
   * "vuoto" simula il primo avvio, prima di qualsiasi caricamento; "errori" aggiunge ai dati dimostrativi tre cedolini
   * caricati con errore (uno associato alla mensilita' mancante).
   */
  datiIniziali?: DatiIniziali;
}

interface EsitoFinale {
  stato: StatoCaricamento;
  messaggio: string | null;
  motivo: MotivoErrore | null;
  cedolinoEsistenteId: number | null;
}

interface FileInLotto {
  caricamento: Caricamento;
  /** Contenuto del file, tenuto in memoria fino alla fine dell'elaborazione come nel backend. */
  contenuto: Blob;
  esitoFinale: EsitoFinale;
}

interface LottoMock {
  id: string;
  /** Ogni lettura del lotto fa avanzare l'elaborazione di un passo (deterministico, indipendente dal tempo). */
  passi: number;
  file: FileInLotto[];
}

export interface MockBackend {
  fetch: (request: Request) => Promise<Response>;
  db: MockDb;
}

function json(corpo: unknown, status = 200, intestazioni: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(corpo), {
    status,
    headers: { 'Content-Type': 'application/json', ...intestazioni },
  });
}

function problema(
  status: number,
  title: string,
  detail: string | null = null,
  errori: ErroreCampo[] | null = null,
): Response {
  return new Response(
    JSON.stringify({
      type: 'about:blank',
      title,
      status,
      detail,
      instance: null,
      ...(errori ? { errori } : {}),
    }),
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );
}

const TITOLI_PROBLEMA: Record<number, string> = {
  400: 'Richiesta non valida',
  404: 'Risorsa non trovata',
  409: 'Operazione non consentita',
};

function rifiuto(errore: RifiutoMock): Response {
  return problema(
    errore.status,
    TITOLI_PROBLEMA[errore.status] ?? 'Errore nella richiesta',
    errore.message,
    errore.errori,
  );
}

/** PDF come allegato con il nome indicato (forma semplice ASCII e forma estesa RFC 5987, come il backend). */
async function pdf(contenuto: Blob, nome: string): Promise<Response> {
  const ascii = nome.replace(/[^\x20-\x7e]|["\\]/g, '_');
  // i byte e non il Blob: nei test il Blob di jsdom non e' accettato dalla Response di Node
  return new Response(await contenuto.arrayBuffer(), {
    status: 200,
    headers: {
      'Content-Type': 'application/pdf',
      'Content-Disposition': `attachment; filename="${ascii}"; filename*=UTF-8''${encodeURIComponent(nome)}`,
    },
  });
}

function nonTrovato(): Response {
  return problema(404, 'Risorsa non trovata', 'La risorsa richiesta non esiste.');
}

function sintesi(rapporto: RapportoDettaglio): RapportoSintesi {
  return {
    id: rapporto.id,
    personaId: rapporto.persona.id,
    azienda: rapporto.azienda,
    etichetta: rapporto.etichetta,
    periodo: rapporto.periodo,
    attuale: rapporto.attuale,
    haSegnalazioni: rapporto.segnalazioni.length > 0,
    ccnlDaIndicare: rapporto.ccnlDaIndicare,
  };
}

function rapportiDiPersona(db: MockDb, personaId: number): RapportoSintesi[] {
  return [...db.rapporti.values()]
    .filter((r) => r.persona.id === personaId)
    .map((r) => rapportoConFileInErrore(db, r))
    .sort(
      (a, b) =>
        Number(b.attuale) - Number(a.attuale) || (b.periodo.da ?? '').localeCompare(a.periodo.da ?? ''),
    )
    .map(sintesi);
}

function esitoInErrore(motivo: keyof typeof MESSAGGI_ERRORE): EsitoFinale {
  return { stato: 'ERRORE', messaggio: MESSAGGI_ERRORE[motivo], motivo, cedolinoEsistenteId: null };
}

/**
 * Esito simulato di un file in base al nome: duplicato, aggiuntivo, protetto (senza password), danneggiato,
 * scansione, ristampa ed errore (piu' cedolini nello stesso PDF); ogni altro file viene acquisito.
 */
function esitoPerNome(db: MockDb, nome: string, password: string | null): EsitoFinale {
  const minuscolo = nome.toLowerCase();
  const esistente = [...db.cedolini.keys()][0] ?? null;
  if (minuscolo.includes('duplicato')) {
    return {
      stato: 'DUPLICATO',
      messaggio: 'Cedolino già presente: Gennaio 2024 di Alfa Software S.r.l.',
      motivo: null,
      cedolinoEsistenteId: esistente,
    };
  }
  if (minuscolo.includes('aggiuntivo')) {
    return {
      stato: 'AGGIUNTIVO_NON_GESTITO',
      messaggio:
        'Esiste già il cedolino di Marzo 2025 con contenuto diverso (possibile aggiuntivo per premi o arretrati).',
      motivo: null,
      cedolinoEsistenteId: esistente,
    };
  }
  if (minuscolo.includes('protetto') && !password) {
    return esitoInErrore('PDF_PROTETTO');
  }
  if (minuscolo.includes('danneggiato')) {
    return esitoInErrore('PDF_NON_VALIDO');
  }
  if (minuscolo.includes('scansione')) {
    return esitoInErrore('PDF_SENZA_TESTO');
  }
  if (minuscolo.includes('ristampa')) {
    return esitoInErrore('LAYOUT_NON_RICONOSCIUTO');
  }
  if (minuscolo.includes('errore')) {
    return esitoInErrore('PIU_CEDOLINI');
  }
  return { stato: 'ACQUISITO', messaggio: null, motivo: null, cedolinoEsistenteId: null };
}

/** Persona e rapporto del cedolino creato o di quello con cui il file confligge, come nel backend. */
function attribuzione(db: MockDb, cedolinoId: number | null): Pick<Caricamento, 'personaId' | 'rapportoId'> {
  const cedolino = cedolinoId !== null ? db.cedolini.get(cedolinoId) : undefined;
  const rapporto = cedolino ? db.rapporti.get(cedolino.rapportoId) : undefined;
  return rapporto
    ? { personaId: rapporto.persona.id, rapportoId: rapporto.id }
    : { personaId: null, rapportoId: null };
}

function avanzaLotto(db: MockDb, lotto: LottoMock): LottoCaricamento {
  lotto.passi += 1;
  const adesso = new Date().toISOString();
  lotto.file.forEach((voce, indice) => {
    const { caricamento } = voce;
    if (STATI_TERMINALI.includes(caricamento.stato)) {
      return;
    }
    // i file sono elaborati uno dopo l'altro: in coda, poi in lettura per un passo, poi esito
    const inizio = indice + 1;
    if (lotto.passi < inizio) {
      return;
    }
    if (lotto.passi === inizio) {
      caricamento.stato = 'IN_LETTURA';
    } else {
      caricamento.stato = voce.esitoFinale.stato;
      caricamento.messaggio = voce.esitoFinale.messaggio;
      caricamento.motivo = voce.esitoFinale.motivo;
      caricamento.cedolinoEsistenteId = voce.esitoFinale.cedolinoEsistenteId;
      if (caricamento.stato === 'ACQUISITO') {
        popolaMario(db);
        const ultimo = db.rapporti.get(1)?.mensilita.find((r) => r.stato === 'PRESENTE');
        caricamento.cedolinoId = ultimo?.cedolinoId ?? null;
      }
      Object.assign(caricamento, attribuzione(db, caricamento.cedolinoId ?? caricamento.cedolinoEsistenteId));
      // PDF conservato (o eliminato) e associazione del file come alla chiusura di un tentativo nel backend
      chiudiTentativo(db, caricamento, voce.contenuto);
    }
    caricamento.aggiornatoIl = adesso;
  });
  const caricamenti = lotto.file.map((f) => ({ ...f.caricamento }));
  return {
    lotto: lotto.id,
    completato: caricamenti.every((c) => STATI_TERMINALI.includes(c.stato)),
    caricamenti,
  };
}

function eliminaCedolino(db: MockDb, cedolinoId: number): boolean {
  const cedolino = db.cedolini.get(cedolinoId);
  if (!cedolino) {
    return false;
  }
  db.cedolini.delete(cedolinoId);
  const rapporto = db.rapporti.get(cedolino.rapportoId);
  if (!rapporto) {
    return true;
  }
  rapporto.mensilita = rapporto.mensilita.filter((r) => r.cedolinoId !== cedolinoId);
  rinumeraFineRapporto(db, rapporto);
  if (!rapporto.mensilita.some((r) => r.stato === 'PRESENTE')) {
    // ultimo cedolino: il rapporto sparisce, e con lui la persona se non ha altri rapporti
    db.rapporti.delete(rapporto.id);
    eliminaAssociazioniDelRapporto(db, rapporto.id);
    const personaId = rapporto.persona.id;
    if (![...db.rapporti.values()].some((r) => r.persona.id === personaId)) {
      db.persone = db.persone.filter((p) => p.id !== personaId);
    }
  }
  return true;
}

function impostaCcnl(rapporto: RapportoDettaglio, codice: string): RapportoDettaglio | null {
  const ccnl = trovaCcnl(codice);
  if (!ccnl) {
    return null;
  }
  rapporto.ccnl = ccnl;
  rapporto.ccnlDaIndicare = false;
  rapporto.ccnlSceltoDaUtente = true;
  rapporto.segnalazioni = rapporto.segnalazioni.filter((s) => s.tipo !== 'CCNL_NON_DETERMINATO');
  const retribuzione = rapporto.riepilogo?.retribuzione;
  if (retribuzione?.lordoMensile != null) {
    retribuzione.mensilita = ccnl.mensilita;
    retribuzione.ral = Math.round(retribuzione.lordoMensile * ccnl.mensilita * 100) / 100;
    retribuzione.stimata = false;
    retribuzione.nota = null;
  }
  return rapporto;
}

const ROTTE = {
  persone: /^\/api\/v1\/persone$/,
  aziende: /^\/api\/v1\/aziende$/,
  rapportiPersona: /^\/api\/v1\/persone\/(\d+)\/rapporti$/,
  rapporto: /^\/api\/v1\/rapporti\/(\d+)$/,
  note: /^\/api\/v1\/rapporti\/(\d+)\/note$/,
  ccnlRapporto: /^\/api\/v1\/rapporti\/(\d+)\/ccnl$/,
  cedolino: /^\/api\/v1\/cedolini\/(\d+)$/,
  pdf: /^\/api\/v1\/cedolini\/(\d+)\/pdf$/,
  datiCedolino: /^\/api\/v1\/cedolini\/(\d+)\/dati$/,
  verificaModifica: /^\/api\/v1\/cedolini\/(\d+)\/dati\/verifica$/,
  caricamenti: /^\/api\/v1\/caricamenti$/,
  lotto: /^\/api\/v1\/caricamenti\/lotti\/([0-9a-f-]+)$/,
  inErrore: /^\/api\/v1\/caricamenti\/in-errore$/,
  riletturaProtetti: /^\/api\/v1\/caricamenti\/in-errore\/rilettura-protetti$/,
  fileInErrore: /^\/api\/v1\/caricamenti\/(\d+)$/,
  pdfCaricamento: /^\/api\/v1\/caricamenti\/(\d+)\/pdf$/,
  rilettura: /^\/api\/v1\/caricamenti\/(\d+)\/rilettura$/,
  attribuzione: /^\/api\/v1\/caricamenti\/(\d+)\/attribuzione$/,
  verificaManuale: /^\/api\/v1\/caricamenti\/(\d+)\/cedolino\/verifica$/,
  inserimentoManuale: /^\/api\/v1\/caricamenti\/(\d+)\/cedolino$/,
  ccnl: /^\/api\/v1\/ccnl$/,
};

export function creaMockBackend(opzioni: OpzioniMock = {}): MockBackend {
  const db = creaDbMock(opzioni.datiIniziali ?? 'demo');
  const lotti = new Map<string, LottoMock>();
  // come il backend: la password indicata per un PDF protetto viene ricordata e provata sui successivi (nel mock ogni
  // password apre i PDF "protetti")
  let passwordRicordata: string | null = null;

  /** Registra un nuovo lotto: un caricamento IN_CODA per file, con l'esito deciso dal nome. */
  function registraLotto(
    file: { nome: string; contenuto: Blob }[],
    password: string | null,
  ): LottoCaricamento {
    const efficace = password ?? passwordRicordata;
    if (password !== null && file.some(({ nome }) => nome.toLowerCase().includes('protetto'))) {
      passwordRicordata = password;
    }
    const adesso = new Date().toISOString();
    const lotto: LottoMock = { id: crypto.randomUUID(), passi: 0, file: [] };
    for (const { nome, contenuto } of file) {
      const caricamento: Caricamento = {
        id: db.contatori.prossimoIdCaricamento++,
        lotto: lotto.id,
        nomeFileOriginale: nome,
        dimensione: contenuto.size,
        stato: 'IN_CODA',
        messaggio: null,
        motivo: null,
        pdfDisponibile: false,
        cedolinoId: null,
        cedolinoEsistenteId: null,
        personaId: null,
        rapportoId: null,
        creatoIl: adesso,
        aggiornatoIl: adesso,
      };
      lotto.file.push({ caricamento, contenuto, esitoFinale: esitoPerNome(db, nome, efficace) });
      db.caricamenti.unshift(caricamento);
    }
    lotti.set(lotto.id, lotto);
    return { lotto: lotto.id, completato: false, caricamenti: lotto.file.map((f) => ({ ...f.caricamento })) };
  }

  async function gestisci(request: Request): Promise<Response> {
    try {
      return await instrada(request);
    } catch (errore) {
      if (errore instanceof RifiutoMock) {
        return rifiuto(errore);
      }
      throw errore;
    }
  }

  async function instrada(request: Request): Promise<Response> {
    const { pathname, searchParams } = new URL(request.url);
    const metodo = request.method.toUpperCase();
    let m: RegExpExecArray | null;

    if (metodo === 'GET' && ROTTE.persone.test(pathname)) {
      return json(elencoPersone(db));
    }
    if (metodo === 'GET' && ROTTE.aziende.test(pathname)) {
      return json(elencoAziende(db));
    }
    if (metodo === 'GET' && (m = ROTTE.rapportiPersona.exec(pathname))) {
      const personaId = Number(m[1]);
      return db.persone.some((p) => p.id === personaId)
        ? json(rapportiDiPersona(db, personaId))
        : nonTrovato();
    }
    if (metodo === 'GET' && (m = ROTTE.rapporto.exec(pathname))) {
      const rapporto = db.rapporti.get(Number(m[1]));
      return rapporto ? json(rapportoConFileInErrore(db, rapporto)) : nonTrovato();
    }
    if (metodo === 'PUT' && (m = ROTTE.note.exec(pathname))) {
      const rapporto = db.rapporti.get(Number(m[1]));
      if (!rapporto) {
        return nonTrovato();
      }
      const corpo = (await request.json()) as { note?: unknown };
      const note = typeof corpo.note === 'string' && corpo.note.trim().length > 0 ? corpo.note : null;
      if (note && note.length > 4000) {
        return problema(400, 'Richiesta non valida', 'Le note non possono superare 4000 caratteri.');
      }
      rapporto.note = note;
      return json({ note });
    }
    if (metodo === 'PUT' && (m = ROTTE.ccnlRapporto.exec(pathname))) {
      const rapporto = db.rapporti.get(Number(m[1]));
      if (!rapporto) {
        return nonTrovato();
      }
      const corpo = (await request.json()) as { ccnlCodice?: unknown };
      const aggiornato =
        typeof corpo.ccnlCodice === 'string' ? impostaCcnl(rapporto, corpo.ccnlCodice) : null;
      return aggiornato
        ? json(rapportoConFileInErrore(db, aggiornato))
        : problema(400, 'Richiesta non valida', 'CCNL sconosciuto.');
    }
    if (metodo === 'GET' && (m = ROTTE.cedolino.exec(pathname))) {
      const cedolino = db.cedolini.get(Number(m[1]));
      return cedolino ? json(cedolino) : nonTrovato();
    }
    if (metodo === 'GET' && (m = ROTTE.datiCedolino.exec(pathname))) {
      return json(datiCedolino(db, Number(m[1])));
    }
    if (metodo === 'PUT' && (m = ROTTE.datiCedolino.exec(pathname))) {
      return json(modificaCedolino(db, Number(m[1]), await request.json()));
    }
    if (metodo === 'POST' && (m = ROTTE.verificaModifica.exec(pathname))) {
      return json(verificaModifica(db, Number(m[1]), await request.json()));
    }
    if (metodo === 'DELETE' && (m = ROTTE.cedolino.exec(pathname))) {
      return eliminaCedolino(db, Number(m[1])) ? new Response(null, { status: 204 }) : nonTrovato();
    }
    if (metodo === 'GET' && (m = ROTTE.pdf.exec(pathname))) {
      const cedolino = db.cedolini.get(Number(m[1]));
      if (!cedolino) {
        return nonTrovato();
      }
      return await pdf(new Blob([PDF_MINIMO], { type: 'application/pdf' }), cedolino.nomeFile);
    }
    if (metodo === 'GET' && ROTTE.inErrore.test(pathname)) {
      return json(elencoInErrore(db));
    }
    if (metodo === 'POST' && ROTTE.riletturaProtetti.test(pathname)) {
      const daRileggere = elencoInErrore(db)
        .filter((e) => e.motivo === 'PDF_PROTETTO' && e.pdfDisponibile && !e.inElaborazione)
        .flatMap((e) => {
          const { caricamento, chiave } = fileInErrore(db, e.id);
          const contenuto = db.pdfFile.get(chiave);
          return contenuto ? [{ nome: caricamento.nomeFileOriginale, contenuto }] : [];
        });
      if (daRileggere.length === 0) {
        return new Response(null, { status: 204 });
      }
      const lotto = registraLotto(daRileggere, null);
      return json(lotto, 202, { Location: `/api/v1/caricamenti/lotti/${lotto.lotto}` });
    }
    if (metodo === 'DELETE' && (m = ROTTE.fileInErrore.exec(pathname))) {
      rimuovi(db, Number(m[1]));
      return new Response(null, { status: 204 });
    }
    if (metodo === 'GET' && (m = ROTTE.pdfCaricamento.exec(pathname))) {
      const id = Number(m[1]);
      const caricamento = db.caricamenti.find((c) => c.id === id);
      const contenuto = caricamento?.stato === 'ERRORE' ? db.pdfFile.get(chiaveFile(caricamento)) : undefined;
      if (!caricamento || !contenuto) {
        throw new RifiutoMock(404, `PDF conservato del caricamento inesistente (id ${id}).`);
      }
      return await pdf(contenuto, caricamento.nomeFileOriginale);
    }
    if (metodo === 'POST' && (m = ROTTE.rilettura.exec(pathname))) {
      const { caricamento, chiave } = fileInErrore(db, Number(m[1]));
      const contenuto = db.pdfFile.get(chiave);
      if (!contenuto) {
        throw new RifiutoMock(409, MESSAGGI_FILE_IN_ERRORE.pdfNonConservato);
      }
      const corpo = (await request.json()) as { password?: unknown };
      const password =
        typeof corpo.password === 'string' && corpo.password.length > 0 ? corpo.password : null;
      const lotto = registraLotto([{ nome: caricamento.nomeFileOriginale, contenuto }], password);
      return json(lotto, 202, { Location: `/api/v1/caricamenti/lotti/${lotto.lotto}` });
    }
    if (metodo === 'PUT' && (m = ROTTE.attribuzione.exec(pathname))) {
      return json(associa(db, Number(m[1]), await request.json()));
    }
    if (metodo === 'DELETE' && (m = ROTTE.attribuzione.exec(pathname))) {
      return json(rimuoviAssociazione(db, Number(m[1])));
    }
    if (metodo === 'POST' && (m = ROTTE.verificaManuale.exec(pathname))) {
      return json(verificaManuale(db, Number(m[1]), await request.json()));
    }
    if (metodo === 'POST' && (m = ROTTE.inserimentoManuale.exec(pathname))) {
      const caricamento = inserisciManuale(db, Number(m[1]), await request.json());
      return json(caricamento, 201, { Location: `/api/v1/cedolini/${String(caricamento.cedolinoId)}` });
    }
    if (metodo === 'POST' && ROTTE.caricamenti.test(pathname)) {
      const formData = await request.formData();
      const files = formData.getAll('files').filter((f): f is File => typeof f !== 'string');
      const password = formData.get('password');
      if (files.length === 0) {
        return problema(400, 'Richiesta non valida', 'Nessun file ricevuto.');
      }
      const lotto = registraLotto(
        files.map((f) => ({ nome: f.name, contenuto: f })),
        typeof password === 'string' ? password : null,
      );
      return json(lotto, 202);
    }
    if (metodo === 'GET' && ROTTE.caricamenti.test(pathname)) {
      const limite = Number(searchParams.get('limite') ?? '50');
      return json(db.caricamenti.slice(0, limite).map((c) => ({ ...c })));
    }
    if (metodo === 'GET' && (m = ROTTE.lotto.exec(pathname))) {
      const lotto = lotti.get(m[1] ?? '');
      return lotto ? json(avanzaLotto(db, lotto)) : nonTrovato();
    }
    if (metodo === 'GET' && ROTTE.ccnl.test(pathname)) {
      return json([...CCNL_MOCK].sort((a, b) => a.descrizione.localeCompare(b.descrizione, 'it')));
    }
    return nonTrovato();
  }

  return {
    db,
    fetch: async (request) => {
      if (opzioni.ritardoMs) {
        await new Promise((risolvi) => setTimeout(risolvi, opzioni.ritardoMs));
      }
      // le risposte sono serializzate in JSON: i componenti non condividono oggetti con il "database"
      return gestisci(request);
    },
  };
}
