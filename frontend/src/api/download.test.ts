import { afterEach, describe, expect, it, vi } from 'vitest';
import { creaApiClient } from './client';
import {
  leggiPdfCaricamento,
  nomeDaContentDisposition,
  scaricaPdfCaricamento,
  scaricaPdfCedolino,
} from './download';
import { ApiError, messaggioErrore } from './errors';

describe('nomeDaContentDisposition', () => {
  it('legge il nome tra virgolette o senza', () => {
    expect(nomeDaContentDisposition('attachment; filename="2024.01_3_1.pdf"')).toBe('2024.01_3_1.pdf');
    expect(nomeDaContentDisposition('attachment; filename=2020.TFR01_3_1.pdf')).toBe('2020.TFR01_3_1.pdf');
  });

  it('preferisce la forma estesa RFC 5987', () => {
    expect(nomeDaContentDisposition(`attachment; filename="x.pdf"; filename*=UTF-8''2024.13_1_2.pdf`)).toBe(
      '2024.13_1_2.pdf',
    );
  });

  it("restituisce null se l'header manca", () => {
    expect(nomeDaContentDisposition(null)).toBeNull();
    expect(nomeDaContentDisposition('inline')).toBeNull();
  });
});

describe('scaricaPdfCedolino', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('riporta il motivo del Problem Details quando il download non riesce', async () => {
    const api = creaApiClient(() =>
      Promise.resolve(
        new Response(
          JSON.stringify({
            type: 'about:blank',
            title: 'Risorsa non trovata',
            status: 404,
            detail: 'Cedolino inesistente (id 9).',
          }),
          { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );

    const errore: unknown = await scaricaPdfCedolino(api, 9, 'riserva.pdf').catch((e: unknown) => e);

    expect(errore).toBeInstanceOf(ApiError);
    expect((errore as ApiError).status).toBe(404);
    expect(messaggioErrore(errore)).toBe('Cedolino inesistente (id 9).');
  });

  it('salva il PDF con il nome indicato dal backend', async () => {
    const api = creaApiClient(() =>
      Promise.resolve(
        new Response('%PDF-1.4', {
          status: 200,
          headers: {
            'Content-Type': 'application/pdf',
            'Content-Disposition': 'attachment; filename="2024.TFR01_1_1.pdf"',
          },
        }),
      ),
    );
    const nomi: string[] = [];
    // jsdom non implementa gli URL dei Blob
    vi.stubGlobal(
      'URL',
      class extends URL {
        static override createObjectURL = vi.fn(() => 'blob:cedolino');
        static override revokeObjectURL = vi.fn();
      },
    );
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      nomi.push(this.download);
    });

    await scaricaPdfCedolino(api, 21, '2024.TFR02_1_1.pdf');

    expect(nomi).toEqual(['2024.TFR01_1_1.pdf']);
  });
});

describe('PDF di un file in errore', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  function apiPdf(intestazioni: Record<string, string>, percorsi: string[] = []) {
    return creaApiClient((request) => {
      percorsi.push(new URL(request.url).pathname);
      return Promise.resolve(new Response('%PDF-1.4', { status: 200, headers: intestazioni }));
    });
  }

  it('legge contenuto e nome originale per l’anteprima', async () => {
    const percorsi: string[] = [];
    const api = apiPdf(
      {
        'Content-Type': 'application/pdf',
        'Content-Disposition': `attachment; filename="busta_.pdf"; filename*=UTF-8''busta%E2%80%93maggio.pdf`,
      },
      percorsi,
    );

    const pdf = await leggiPdfCaricamento(api, 7);

    expect(percorsi).toEqual(['/api/v1/caricamenti/7/pdf']);
    expect(pdf.nome).toBe('busta–maggio.pdf');
    expect(pdf.contenuto.type).toBe('application/pdf');
    expect(await pdf.contenuto.text()).toBe('%PDF-1.4');
  });

  it('riporta il motivo quando il PDF non è conservato', async () => {
    const api = creaApiClient(() =>
      Promise.resolve(
        new Response(
          JSON.stringify({ title: 'Risorsa non trovata', status: 404, detail: 'PDF non conservato.' }),
          {
            status: 404,
            headers: { 'Content-Type': 'application/problem+json' },
          },
        ),
      ),
    );

    const errore: unknown = await leggiPdfCaricamento(api, 7).catch((e: unknown) => e);

    expect((errore as ApiError).status).toBe(404);
    expect(messaggioErrore(errore)).toBe('PDF non conservato.');
  });

  it('scarica il PDF con il nome originale o, se manca, con quello di riserva', async () => {
    const nomi: string[] = [];
    vi.stubGlobal(
      'URL',
      class extends URL {
        static override createObjectURL = vi.fn(() => 'blob:caricamento');
        static override revokeObjectURL = vi.fn();
      },
    );
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      nomi.push(this.download);
    });

    await scaricaPdfCaricamento(
      apiPdf({
        'Content-Type': 'application/pdf',
        'Content-Disposition': 'attachment; filename="scansione.pdf"',
      }),
      3,
      'riserva.pdf',
    );
    await scaricaPdfCaricamento(apiPdf({ 'Content-Type': 'application/pdf' }), 3, 'riserva.pdf');

    expect(nomi).toEqual(['scansione.pdf', 'riserva.pdf']);
  });
});
