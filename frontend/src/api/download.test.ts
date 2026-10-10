import { afterEach, describe, expect, it, vi } from 'vitest';
import { creaApiClient } from './client';
import { nomeDaContentDisposition, scaricaPdfCedolino } from './download';
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
