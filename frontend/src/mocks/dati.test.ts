import { describe, expect, it } from 'vitest';
import { creaDbMock, ordinaMensilita, rinumeraFineRapporto } from './dati';

describe('dati simulati', () => {
  it('ordinano le mensilità in ordine cronologico inverso con 13a dopo dicembre e 14a dopo luglio', () => {
    const db = creaDbMock('demo');
    const chiavi = db.rapporti.get(1)?.mensilita.map((m) => m.chiave) ?? [];
    expect(chiavi.slice(0, 3)).toEqual(['2026.09', '2026.08', '2026.14']);
    expect(chiavi.indexOf('2025.13')).toBe(chiavi.indexOf('2025.12') - 1);
    expect(chiavi.indexOf('2025.14')).toBe(chiavi.indexOf('2025.07') - 1);
  });

  it("mettono il cedolino di fine rapporto dopo l'ultima mensilità", () => {
    const db = creaDbMock('demo');
    const righe = ordinaMensilita(db.rapporti.get(2)?.mensilita ?? []);
    expect(righe.slice(0, 3).map((r) => r.titolo)).toEqual([
      'Fine rapporto 2 – Maggio 2022',
      'Fine rapporto 1 – Marzo 2022',
      'Febbraio 2022',
    ]);
  });

  it('rinumerano i cedolini di fine rapporto come il backend dopo un’eliminazione', () => {
    const db = creaDbMock('demo');
    const rapporto = db.rapporti.get(2);
    if (!rapporto) {
      throw new Error('rapporto simulato mancante');
    }
    const primo = rapporto.mensilita.find((r) => r.chiave === '2022.TFR01');
    rapporto.mensilita = rapporto.mensilita.filter((r) => r !== primo);
    rinumeraFineRapporto(db, rapporto);

    const rimasto = rapporto.mensilita[0];
    expect(rimasto).toMatchObject({
      chiave: '2022.TFR01',
      titolo: 'Fine rapporto 1 – Maggio 2022',
      nomeFile: '2022.TFR01_2_1.pdf',
    });
    const cedolino = rimasto?.cedolinoId != null ? db.cedolini.get(rimasto.cedolinoId) : undefined;
    expect(cedolino?.nomeFile).toBe('2022.TFR01_2_1.pdf');
    expect(cedolino?.competenza.progressivoTfr).toBe(1);
  });

  it('non contengono dati reali: solo nominativi inventati', () => {
    const db = creaDbMock('demo');
    expect(db.persone.map((p) => p.etichetta)).toEqual(['Laura Bianchi', 'Mario Rossi']);
  });
});
