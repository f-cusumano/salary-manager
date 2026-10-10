import type {
  Caricamento,
  LottoCaricamento,
  RapportoDettaglio,
  RapportoSintesi,
  StatoCaricamento,
} from '../api/types';
import { STATI_TERMINALI } from '../api/types';
import {
  CCNL_MOCK,
  MESSAGGIO_PDF_PROTETTO,
  creaDbMock,
  popolaMario,
  rinumeraFineRapporto,
  trovaCcnl,
  type MockDb,
} from './dati';

/**
 * Backend simulato in memoria, usato SOLO in sviluppo (`npm run dev:mock`) e nei test: implementa il contratto
 * `docs/openapi.yaml` con dati inventati. In produzione non viene incluso nel bundle.
 */

export interface OpzioniMock {
  /** Ritardo artificiale delle risposte (ms), per vedere gli stati di caricamento. */
  ritardoMs?: number;
  /** "vuoto" simula il primo avvio, prima di qualsiasi caricamento. */
  datiIniziali?: 'demo' | 'vuoto';
}

interface FileInLotto {
  caricamento: Caricamento;
  esitoFinale: { stato: StatoCaricamento; messaggio: string | null; cedolinoEsistenteId: number | null };
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

const PDF_MINIMO =
  '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n' +
  '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 595 842]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n';

function json(corpo: unknown, status = 200): Response {
  return new Response(JSON.stringify(corpo), { status, headers: { 'Content-Type': 'application/json' } });
}

function problema(status: number, title: string, detail: string | null = null): Response {
  return new Response(JSON.stringify({ type: 'about:blank', title, status, detail, instance: null }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
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
    .sort(
      (a, b) =>
        Number(b.attuale) - Number(a.attuale) || (b.periodo.da ?? '').localeCompare(a.periodo.da ?? ''),
    )
    .map(sintesi);
}

function esitoPerNome(db: MockDb, nome: string, password: string | null): FileInLotto['esitoFinale'] {
  const minuscolo = nome.toLowerCase();
  const esistente = [...db.cedolini.keys()][0] ?? null;
  if (minuscolo.includes('duplicato')) {
    return {
      stato: 'DUPLICATO',
      messaggio: 'Cedolino già presente: Gennaio 2024 di Alfa Software S.r.l.',
      cedolinoEsistenteId: esistente,
    };
  }
  if (minuscolo.includes('aggiuntivo')) {
    return {
      stato: 'AGGIUNTIVO_NON_GESTITO',
      messaggio:
        'Esiste già il cedolino di Marzo 2025 con contenuto diverso (possibile aggiuntivo per premi o arretrati).',
      cedolinoEsistenteId: esistente,
    };
  }
  if (minuscolo.includes('protetto') && !password) {
    return { stato: 'ERRORE', messaggio: MESSAGGIO_PDF_PROTETTO, cedolinoEsistenteId: null };
  }
  if (minuscolo.includes('errore')) {
    return {
      stato: 'ERRORE',
      messaggio: 'Il documento contiene più cedolini distinti: caricali come file separati.',
      cedolinoEsistenteId: null,
    };
  }
  return { stato: 'ACQUISITO', messaggio: null, cedolinoEsistenteId: null };
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
      caricamento.cedolinoEsistenteId = voce.esitoFinale.cedolinoEsistenteId;
      if (caricamento.stato === 'ACQUISITO') {
        popolaMario(db);
        const ultimo = db.rapporti.get(1)?.mensilita.find((r) => r.stato === 'PRESENTE');
        caricamento.cedolinoId = ultimo?.cedolinoId ?? null;
      }
      Object.assign(caricamento, attribuzione(db, caricamento.cedolinoId ?? caricamento.cedolinoEsistenteId));
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
  rapportiPersona: /^\/api\/v1\/persone\/(\d+)\/rapporti$/,
  rapporto: /^\/api\/v1\/rapporti\/(\d+)$/,
  note: /^\/api\/v1\/rapporti\/(\d+)\/note$/,
  ccnlRapporto: /^\/api\/v1\/rapporti\/(\d+)\/ccnl$/,
  cedolino: /^\/api\/v1\/cedolini\/(\d+)$/,
  pdf: /^\/api\/v1\/cedolini\/(\d+)\/pdf$/,
  caricamenti: /^\/api\/v1\/caricamenti$/,
  lotto: /^\/api\/v1\/caricamenti\/lotti\/([0-9a-f-]+)$/,
  ccnl: /^\/api\/v1\/ccnl$/,
};

export function creaMockBackend(opzioni: OpzioniMock = {}): MockBackend {
  const db = creaDbMock(opzioni.datiIniziali ?? 'demo');
  const lotti = new Map<string, LottoMock>();
  const storico: Caricamento[] = [];
  let prossimoIdCaricamento = 1;

  async function gestisci(request: Request): Promise<Response> {
    const { pathname, searchParams } = new URL(request.url);
    const metodo = request.method.toUpperCase();
    let m: RegExpExecArray | null;

    if (metodo === 'GET' && ROTTE.persone.test(pathname)) {
      return json(db.persone);
    }
    if (metodo === 'GET' && (m = ROTTE.rapportiPersona.exec(pathname))) {
      const personaId = Number(m[1]);
      return db.persone.some((p) => p.id === personaId)
        ? json(rapportiDiPersona(db, personaId))
        : nonTrovato();
    }
    if (metodo === 'GET' && (m = ROTTE.rapporto.exec(pathname))) {
      const rapporto = db.rapporti.get(Number(m[1]));
      return rapporto ? json(rapporto) : nonTrovato();
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
      return aggiornato ? json(aggiornato) : problema(400, 'Richiesta non valida', 'CCNL sconosciuto.');
    }
    if (metodo === 'GET' && (m = ROTTE.cedolino.exec(pathname))) {
      const cedolino = db.cedolini.get(Number(m[1]));
      return cedolino ? json(cedolino) : nonTrovato();
    }
    if (metodo === 'DELETE' && (m = ROTTE.cedolino.exec(pathname))) {
      return eliminaCedolino(db, Number(m[1])) ? new Response(null, { status: 204 }) : nonTrovato();
    }
    if (metodo === 'GET' && (m = ROTTE.pdf.exec(pathname))) {
      const cedolino = db.cedolini.get(Number(m[1]));
      if (!cedolino) {
        return nonTrovato();
      }
      return new Response(new Blob([PDF_MINIMO], { type: 'application/pdf' }), {
        status: 200,
        headers: {
          'Content-Type': 'application/pdf',
          'Content-Disposition': `attachment; filename="${cedolino.nomeFile}"`,
        },
      });
    }
    if (metodo === 'POST' && ROTTE.caricamenti.test(pathname)) {
      const formData = await request.formData();
      const files = formData.getAll('files').filter((f): f is File => typeof f !== 'string');
      const password = formData.get('password');
      if (files.length === 0) {
        return problema(400, 'Richiesta non valida', 'Nessun file ricevuto.');
      }
      const adesso = new Date().toISOString();
      const lotto: LottoMock = { id: crypto.randomUUID(), passi: 0, file: [] };
      for (const file of files) {
        const caricamento: Caricamento = {
          id: prossimoIdCaricamento++,
          lotto: lotto.id,
          nomeFileOriginale: file.name,
          dimensione: file.size,
          stato: 'IN_CODA',
          messaggio: null,
          cedolinoId: null,
          cedolinoEsistenteId: null,
          personaId: null,
          rapportoId: null,
          creatoIl: adesso,
          aggiornatoIl: adesso,
        };
        lotto.file.push({
          caricamento,
          esitoFinale: esitoPerNome(db, file.name, typeof password === 'string' ? password : null),
        });
        storico.unshift(caricamento);
      }
      lotti.set(lotto.id, lotto);
      return json(
        { lotto: lotto.id, completato: false, caricamenti: lotto.file.map((f) => ({ ...f.caricamento })) },
        202,
      );
    }
    if (metodo === 'GET' && ROTTE.caricamenti.test(pathname)) {
      const limite = Number(searchParams.get('limite') ?? '50');
      return json(storico.slice(0, limite).map((c) => ({ ...c })));
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
