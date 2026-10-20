import type { components } from './schema';

/** Alias leggibili dei tipi generati dal contratto OpenAPI (src/api/schema.d.ts). */
type Schemi = components['schemas'];

export type PersonaSintesi = Schemi['PersonaSintesi'];
export type RapportoSintesi = Schemi['RapportoSintesi'];
export type RapportoDettaglio = Schemi['RapportoDettaglio'];
export type AziendaSintesi = Schemi['AziendaSintesi'];
export type AziendaSelezionabile = Schemi['AziendaSelezionabile'];
export type Periodo = Schemi['Periodo'];
export type Ccnl = Schemi['Ccnl'];
export type Segnalazione = Schemi['Segnalazione'];
export type Evidenza = Schemi['Evidenza'];
export type Riepilogo = Schemi['Riepilogo'];
export type Retribuzione = Schemi['Retribuzione'];
export type TfrRiepilogo = Schemi['TfrRiepilogo'];
export type SaldoRateo = Schemi['SaldoRateo'];
export type Liquidazione = Schemi['Liquidazione'];
export type VoceLiquidazione = Schemi['VoceLiquidazione'];
export type RigaMensilita = Schemi['RigaMensilita'];
export type Competenza = Schemi['Competenza'];
export type CedolinoDettaglio = Schemi['CedolinoDettaglio'];
export type Voce = Schemi['Voce'];
export type RateoCedolino = Schemi['RateoCedolino'];
export type TfrCedolino = Schemi['TfrCedolino'];
export type ElementiRetributiviCedolino = Schemi['ElementiRetributiviCedolino'];
export type Caricamento = Schemi['Caricamento'];
export type LottoCaricamento = Schemi['LottoCaricamento'];
export type StatoCaricamento = Schemi['StatoCaricamento'];
export type UnitaMisura = Schemi['UnitaMisura'];
export type TipoRateo = Schemi['TipoRateo'];
export type TipoVoceLiquidazione = Schemi['TipoVoceLiquidazione'];
export type FonteDato = Schemi['FonteDato'];
export type Problema = Schemi['Problema'];
export type ErroreCampo = Schemi['ErroreCampo'];
export type TipoMensilita = Schemi['TipoMensilita'];
export type MotivoErrore = Schemi['MotivoErrore'];
export type CaricamentoInErrore = Schemi['CaricamentoInErrore'];
export type AttribuzioneCaricamento = Schemi['AttribuzioneCaricamento'];
export type RichiestaAttribuzione = Schemi['RichiestaAttribuzione'];
export type CedolinoManuale = Schemi['CedolinoManuale'];
export type VoceManuale = Schemi['VoceManuale'];
export type CategoriaVoce = Schemi['CategoriaVoce'];
export type VerificaCedolinoManuale = Schemi['VerificaCedolinoManuale'];

/** Stati terminali di un caricamento (polling concluso per quel file). */
export const STATI_TERMINALI: readonly StatoCaricamento[] = [
  'ACQUISITO',
  'DUPLICATO',
  'AGGIUNTIVO_NON_GESTITO',
  'ERRORE',
];
