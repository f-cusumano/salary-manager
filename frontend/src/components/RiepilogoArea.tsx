import { Badge, Card, Group, SimpleGrid, Stack, Table, Text, Title, Tooltip } from '@mantine/core';
import {
  IconBriefcase,
  IconDoorExit,
  IconInfoCircle,
  IconBeach,
  IconCalendarStats,
  IconCash,
  IconFileText,
  IconNotes,
  IconHourglass,
  IconPigMoney,
} from '@tabler/icons-react';
import { useId, type ReactNode } from 'react';
import type {
  DurataPreavviso,
  Liquidazione,
  Preavviso,
  RapportoDettaglio,
  Riepilogo,
  RigaMensilita,
  SaldoRateo,
  VoceLiquidazione,
} from '../api/types';
import {
  formatAnzianita,
  formatData,
  formatDurataPreavviso,
  formatImporto,
  formatIstante,
  formatMese,
  formatMeseDiData,
  formatPercentuale,
  formatQuantita,
} from '../format/format';
import { testi } from '../i18n';
import { CcnlCampo } from './CcnlCampo';
import { ElencoValori } from './ElencoValori';
import { NoteCampo } from './NoteCampo';
import { Stima } from './Stima';
import { TitoloArea } from './TitoloArea';

const t = testi.riepilogo;

interface Props {
  rapporto: RapportoDettaglio;
}

/**
 * Area riepilogo: anzianità e scatti, retribuzione, contratto, note, ferie/permessi (solo azienda attuale), TFR,
 * preavviso (solo azienda attuale in corso) e stima della liquidazione (azienda attuale o ultima azienda finché il TFR
 * non è liquidato).
 */
export function RiepilogoArea({ rapporto }: Props) {
  const idTitolo = useId();
  const { riepilogo } = rapporto;

  return (
    <section aria-labelledby={idTitolo}>
      <TitoloArea
        id={idTitolo}
        icona={<IconCalendarStats size={20} aria-hidden color="var(--mantine-color-marchio-7)" />}
        destra={
          riepilogo && (
            <Text size="xs" c="dimmed">
              {t.calcolatoIl} {formatIstante(riepilogo.calcolatoIl)}
            </Text>
          )
        }
      >
        {t.titolo}
      </TitoloArea>
      <SimpleGrid cols={{ base: 1, md: 2 }} spacing="md">
        {riepilogo ? (
          <>
            <BloccoAnzianita riepilogo={riepilogo} ccnlDaIndicare={rapporto.ccnlDaIndicare} />
            <BloccoRetribuzione riepilogo={riepilogo} mensilita={rapporto.mensilita} />
          </>
        ) : (
          <Card>
            <Text c="dimmed" size="sm">
              {t.nonCalcolato}
            </Text>
          </Card>
        )}
        <Blocco titolo={t.ccnl} icona={<IconFileText size={18} aria-hidden />}>
          <Stack gap="sm">
            <CcnlCampo rapporto={rapporto} />
            <ElencoValori
              righe={[
                rapporto.qualifica !== null && { etichetta: t.qualifica, valore: rapporto.qualifica },
                rapporto.livello !== null && { etichetta: t.livello, valore: rapporto.livello },
              ]}
            />
          </Stack>
        </Blocco>
        <Blocco titolo={t.note} icona={<IconNotes size={18} aria-hidden />}>
          <NoteCampo rapportoId={rapporto.id} note={rapporto.note} />
        </Blocco>
        {riepilogo && rapporto.attuale && <BloccoFerie saldi={riepilogo.ratei.saldi} />}
        {riepilogo && <BloccoTfr riepilogo={riepilogo} attuale={rapporto.attuale} />}
        {riepilogo?.preavviso && <BloccoPreavviso preavviso={riepilogo.preavviso} />}
        {riepilogo?.liquidazione && <BloccoLiquidazione liquidazione={riepilogo.liquidazione} />}
      </SimpleGrid>
    </section>
  );
}

interface BloccoProps {
  titolo: string;
  icona: ReactNode;
  children: ReactNode;
  /** Contenuto a destra del titolo. */
  destra?: ReactNode;
  /** Occupa tutta la larghezza della griglia. */
  pieno?: boolean;
}

function Blocco({ titolo, icona, children, destra, pieno }: BloccoProps) {
  const id = useId();
  return (
    <Card component="section" aria-labelledby={id} style={pieno ? { gridColumn: '1 / -1' } : undefined}>
      <Group justify="space-between" mb="sm" gap="xs">
        <Group gap={8} wrap="nowrap" c="marchio.8">
          {icona}
          <Title order={4} size="h5" id={id} c="dark.6">
            {titolo}
          </Title>
        </Group>
        {destra}
      </Group>
      {children}
    </Card>
  );
}

function ValoreGrande({ children }: { children: ReactNode }) {
  return (
    <Text fz={26} fw={700} lh={1.15} c="dark.7" style={{ fontVariantNumeric: 'tabular-nums' }}>
      {children}
    </Text>
  );
}

/**
 * Testo del prossimo scatto. "Nessuno previsto" solo quando e' certo (rapporto concluso o numero massimo di scatti
 * raggiunto); se la data non e' determinabile lo si dice, senza far credere che non ci siano altri scatti.
 */
function testoProssimoScatto(riepilogo: Riepilogo, ccnlDaIndicare: boolean): string {
  const { anzianita, scatti } = riepilogo;
  if (scatti.prossimoScatto) {
    return formatMeseDiData(scatti.prossimoScatto);
  }
  const massimoRaggiunto =
    scatti.attuali !== null && scatti.massimo !== null && scatti.attuali >= scatti.massimo;
  if (!anzianita.inCorso || massimoRaggiunto) {
    return t.nessunProssimoScatto;
  }
  return ccnlDaIndicare ? t.prossimoScattoCcnlDaIndicare : testi.comune.nonDisponibile;
}

function BloccoAnzianita({ riepilogo, ccnlDaIndicare }: { riepilogo: Riepilogo; ccnlDaIndicare: boolean }) {
  const { anzianita, scatti } = riepilogo;
  const fonti =
    anzianita.fonteInizio === anzianita.fonteFine
      ? t.fonte[anzianita.fonteInizio]
      : `${t.fonte[anzianita.fonteInizio]} / ${t.fonte[anzianita.fonteFine]}`;
  const dal = anzianita.dal ? `${t.anzianitaDal} ${formatMese(anzianita.dal)}` : null;
  // per un rapporto in corso l'anzianita' arriva all'ultimo cedolino ordinario, non a oggi
  const al = anzianita.al
    ? `${t.anzianitaAl} ${formatMese(anzianita.al)}${
        anzianita.inCorso && anzianita.fonteFine === 'CEDOLINI' ? ` ${t.anzianitaUltimoCedolino}` : ''
      }`
    : null;
  const prossimo = testoProssimoScatto(riepilogo, ccnlDaIndicare);
  const attuali =
    scatti.attuali === null
      ? testi.comune.nonDisponibile
      : scatti.massimo !== null
        ? `${scatti.attuali} ${t.scattiSu} ${scatti.massimo}`
        : String(scatti.attuali);

  return (
    <Blocco titolo={t.anzianita} icona={<IconBriefcase size={18} aria-hidden />}>
      <ValoreGrande>{formatAnzianita(anzianita.anni, anzianita.mesi)}</ValoreGrande>
      <Text size="sm" c="dimmed" mt={4}>
        {[dal, al].filter(Boolean).join(' ')}
        {' · '}
        {t.fonteDate}: {fonti}
      </Text>
      <Title order={5} size="sm" mt="md" mb={4} c="dimmed" fw={600}>
        {t.scatti}
      </Title>
      <ElencoValori
        righe={[
          {
            etichetta: t.scattiAttuali,
            valore:
              scatti.attuali !== null && scatti.attualiStimati ? (
                <Stima spiegazione={t.scattiAttualiDaCcnl}>{attuali}</Stima>
              ) : (
                attuali
              ),
          },
          {
            etichetta: t.prossimoScatto,
            valore:
              scatti.prossimoScatto && scatti.fonteProssimo === 'CCNL' ? (
                <Stima spiegazione={t.prossimoScattoDaCcnl}>{prossimo}</Stima>
              ) : (
                prossimo
              ),
          },
        ]}
      />
    </Blocco>
  );
}

function BloccoRetribuzione({ riepilogo, mensilita }: { riepilogo: Riepilogo; mensilita: RigaMensilita[] }) {
  const r = riepilogo.retribuzione;
  const ral = formatImporto(r.ral);
  const riferimento = mensilita.find(
    (m) => m.cedolinoId !== null && m.cedolinoId === r.cedolinoRiferimentoId,
  )?.titolo;
  return (
    <Blocco titolo={t.retribuzione} icona={<IconCash size={18} aria-hidden />}>
      <Group justify="space-between" align="flex-start" wrap="nowrap" gap="sm">
        <div>
          <Text size="sm" c="dimmed">
            {t.ral}
          </Text>
          <ValoreGrande>
            {r.stimata && r.ral !== null ? <Stima spiegazione={r.nota ?? t.ralTooltip}>{ral}</Stima> : ral}
          </ValoreGrande>
        </div>
        <Stack gap={2} align="flex-end">
          <Text size="xs" c="dimmed">
            {t.buoniPasto}
          </Text>
          <Badge
            variant={riepilogo.buoniPasto ? 'filled' : 'light'}
            color={riepilogo.buoniPasto ? 'teal' : 'gray'}
            aria-label={`${t.buoniPasto}: ${riepilogo.buoniPasto ? testi.comune.si : testi.comune.no}`}
          >
            {riepilogo.buoniPasto ? testi.comune.si : testi.comune.no}
          </Badge>
        </Stack>
      </Group>
      {r.nota && !r.stimata && (
        <Text size="xs" c="dimmed" mt={4}>
          {r.nota}
        </Text>
      )}
      <ElencoValori
        righe={[
          r.lordoMensile !== null && {
            etichetta: t.lordoMensile,
            valore: r.mensilita
              ? `${formatImporto(r.lordoMensile)} × ${r.mensilita} ${t.mensilita}`
              : formatImporto(r.lordoMensile),
          },
          r.retribuzioneBase !== null && {
            etichetta: t.retribuzioneBase,
            valore: formatImporto(r.retribuzioneBase),
          },
          r.minimo !== null && { etichetta: t.minimo, valore: formatImporto(r.minimo) },
          r.contingenza !== null && { etichetta: t.contingenza, valore: formatImporto(r.contingenza) },
          r.superminimo !== null && { etichetta: t.superminimo, valore: formatImporto(r.superminimo) },
          r.importoScatti !== null && { etichetta: t.importoScatti, valore: formatImporto(r.importoScatti) },
          r.altriElementiFissi !== null && {
            etichetta: t.altriElementiFissi,
            valore: formatImporto(r.altriElementiFissi),
          },
        ]}
      />
      {riferimento && (
        <Text size="xs" c="dimmed" mt="xs">
          {t.retribuzioneRiferimento} {riferimento}
        </Text>
      )}
    </Blocco>
  );
}

function BloccoFerie({ saldi }: { saldi: SaldoRateo[] }) {
  return (
    <Blocco titolo={t.ferie} icona={<IconBeach size={18} aria-hidden />}>
      {saldi.length === 0 ? (
        <Text size="sm" c="dimmed">
          {t.ferieNessuno}
        </Text>
      ) : (
        <Table verticalSpacing={6} horizontalSpacing={0} fz="sm">
          <Table.Thead>
            <Table.Tr>
              <Table.Th fw={500} c="dimmed">
                {t.rateoTipo}
              </Table.Th>
              <Table.Th fw={500} c="dimmed" ta="right">
                {t.rateoSaldo}
              </Table.Th>
              <Table.Th fw={500} c="dimmed" ta="right">
                {t.rateoValore}
              </Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {saldi.map((s) => (
              <Table.Tr key={s.tipo}>
                <Table.Td>{testi.rateo[s.tipo]}</Table.Td>
                <Table.Td ta="right" fw={600} style={{ fontVariantNumeric: 'tabular-nums' }}>
                  {formatQuantita(s.saldo, s.unita)}
                </Table.Td>
                <Table.Td ta="right" style={{ fontVariantNumeric: 'tabular-nums' }}>
                  {s.valoreStimato !== null ? (
                    <Group justify="flex-end" wrap="nowrap">
                      <Stima
                        spiegazione={s.nota ? `${t.rateoValoreTooltip} ${s.nota}` : t.rateoValoreTooltip}
                      >
                        {formatImporto(s.valoreStimato)}
                      </Stima>
                    </Group>
                  ) : (
                    testi.comune.nonDisponibile
                  )}
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}
      <Text size="xs" c="dimmed" mt="xs">
        {t.ferieRiferimento}
      </Text>
    </Blocco>
  );
}

function positivo(valore: number | null): valore is number {
  return valore !== null && valore > 0;
}

function BloccoTfr({ riepilogo, attuale }: { riepilogo: Riepilogo; attuale: boolean }) {
  const tfr = riepilogo.tfr;
  const spiegazioneNetto =
    tfr.aliquotaStimata !== null
      ? `${t.tfrStimaNettoTooltip} (${t.tfrAliquota} ${formatPercentuale(tfr.aliquotaStimata)})`
      : t.tfrStimaNettoTooltip;
  // liquidato con il cedolino di fine rapporto: il valore principale e' l'importo liquidato, non il residuo (0)
  const [etichettaPrincipale, principale] =
    tfr.liquidato && tfr.importoLiquidato !== null
      ? [t.tfrLiquidato, formatImporto(tfr.importoLiquidato)]
      : [t.tfrTotale, formatImporto(tfr.totaleAccumulato)];
  const principaleStimato = !tfr.liquidato && tfr.stimato && tfr.totaleAccumulato !== null;
  const quotaMensile = formatImporto(tfr.quotaMensile);
  return (
    <Blocco titolo={t.tfr} icona={<IconPigMoney size={18} aria-hidden />}>
      <Text size="sm" c="dimmed">
        {etichettaPrincipale}
      </Text>
      <ValoreGrande>
        {principaleStimato ? (
          <Stima spiegazione={tfr.nota ?? t.tfrStimatoTooltip}>{principale}</Stima>
        ) : (
          principale
        )}
      </ValoreGrande>
      <Text size="xs" c="dimmed" mt={4}>
        {tfr.riferimento && `${t.tfrRiferimento} ${formatMese(tfr.riferimento)}`}
        {tfr.nota && !principaleStimato && `${tfr.riferimento ? ' · ' : ''}${tfr.nota}`}
      </Text>
      <ElencoValori
        righe={[
          tfr.liquidato &&
            tfr.nettoLiquidato !== null && {
              etichetta: t.tfrNettoLiquidato,
              valore: formatImporto(tfr.nettoLiquidato),
              enfasi: true,
            },
          tfr.quotaMensile !== null && {
            etichetta: t.tfrQuotaMensile,
            valore: tfr.quotaMensileStimata ? (
              <Stima spiegazione={t.tfrQuotaMensileStimataTooltip}>{quotaMensile}</Stima>
            ) : (
              quotaMensile
            ),
          },
          attuale &&
            positivo(tfr.quotaInAzienda) && {
              etichetta: t.tfrQuotaInAzienda,
              valore: formatImporto(tfr.quotaInAzienda),
            },
          positivo(tfr.quotaFondi) && { etichetta: t.tfrQuotaFondi, valore: formatImporto(tfr.quotaFondi) },
          positivo(tfr.quotaBusta) && { etichetta: t.tfrQuotaBusta, valore: formatImporto(tfr.quotaBusta) },
          positivo(tfr.anticipi) && { etichetta: t.tfrAnticipi, valore: formatImporto(tfr.anticipi) },
          tfr.stimaNetto !== null && {
            etichetta: t.tfrStimaNetto,
            valore: <Stima spiegazione={spiegazioneNetto}>{formatImporto(tfr.stimaNetto)}</Stima>,
            enfasi: true,
          },
        ]}
      />
    </Blocco>
  );
}

const numeri = { fontVariantNumeric: 'tabular-nums' } as const;

/** "TFR in azienda", "Ferie · 12,5 giorni", "Ratei di tredicesima · 9 mesi". */
function etichettaVoce(voce: VoceLiquidazione): string {
  const nome = t.voceLiquidazione[voce.tipo];
  if (voce.quantita === null) {
    return nome;
  }
  const quantita =
    voce.unita !== null
      ? formatQuantita(voce.quantita, voce.unita)
      : `${voce.quantita} ${voce.quantita === 1 ? t.liquidazioneMese : t.liquidazioneMesi}`;
  return `${nome} · ${quantita}`;
}

/** "lordo 8.120,40 € · contributi 0,00 € · imposte 1.867,69 €" (le parti non stimabili sono omesse). */
function dettaglioImporti(lordo: number | null, contributi: number | null, imposte: number | null): string {
  return [
    lordo !== null && `${t.liquidazioneLordo} ${formatImporto(lordo)}`,
    contributi !== null && contributi !== 0 && `${t.liquidazioneContributi} ${formatImporto(contributi)}`,
    imposte !== null && `${t.liquidazioneImposte} ${formatImporto(imposte)}`,
  ]
    .filter(Boolean)
    .join(' · ');
}

function RigaVoceLiquidazione({ voce }: { voce: VoceLiquidazione }) {
  const etichetta = etichettaVoce(voce);
  const stimabile = voce.netto !== null;
  return (
    <Group
      component="li"
      justify="space-between"
      align="flex-start"
      wrap="nowrap"
      gap="sm"
      py={6}
      style={{ borderBottom: '1px dashed var(--mantine-color-gray-2)' }}
    >
      <div style={{ minWidth: 0 }}>
        <Group gap={4} wrap="nowrap">
          <Text size="sm" fw={500}>
            {etichetta}
          </Text>
          {stimabile && voce.nota && (
            <Tooltip label={voce.nota} multiline w={320} events={{ hover: true, focus: true, touch: true }}>
              <span
                tabIndex={0}
                role="img"
                aria-label={`${etichetta}: ${voce.nota}`}
                style={{ display: 'inline-flex', cursor: 'help', color: 'var(--mantine-color-dimmed)' }}
              >
                <IconInfoCircle size={14} aria-hidden />
              </span>
            </Tooltip>
          )}
        </Group>
        <Text size="xs" c="dimmed">
          {stimabile ? dettaglioImporti(voce.lordo, voce.contributi, voce.imposte) : voce.nota}
        </Text>
      </div>
      <Stack gap={0} align="flex-end">
        <Text size="sm" fw={600} style={numeri} c={stimabile ? undefined : 'dimmed'}>
          {stimabile ? formatImporto(voce.netto) : t.liquidazioneNonStimabile}
        </Text>
        {voce.netto !== null && voce.netto < 0 && (
          <Text size="xs" c="red.7">
            {t.liquidazioneDebito}
          </Text>
        )}
      </Stack>
    </Group>
  );
}

function BloccoLiquidazione({ liquidazione }: { liquidazione: Liquidazione }) {
  return (
    <Blocco
      titolo={t.liquidazione}
      icona={<IconDoorExit size={18} aria-hidden />}
      pieno
      destra={
        <Text size="xs" c="dimmed">
          {t.liquidazioneAl} {formatData(liquidazione.dataRiferimento)}
        </Text>
      }
    >
      <Text size="sm" c="dimmed">
        {t.liquidazioneNetto}
      </Text>
      <ValoreGrande>
        <Stima spiegazione={t.liquidazioneNettoTooltip}>{formatImporto(liquidazione.totaleNetto)}</Stima>
      </ValoreGrande>
      <Text size="xs" c="dimmed" mt={4} style={numeri}>
        {dettaglioImporti(
          liquidazione.totaleLordo,
          liquidazione.totaleContributi,
          liquidazione.totaleImposte,
        )}
      </Text>
      {!liquidazione.completa && (
        <Text size="xs" c="orange.8" mt={4}>
          {t.liquidazioneParziale}
        </Text>
      )}
      <Stack component="ul" gap={0} mt="sm" p={0} style={{ listStyle: 'none', margin: 0 }}>
        {liquidazione.voci.map((voce) => (
          <RigaVoceLiquidazione key={voce.tipo} voce={voce} />
        ))}
      </Stack>
      {liquidazione.nota && (
        <>
          <Title order={5} size="xs" mt="sm" mb={2} c="dimmed" fw={600}>
            {t.liquidazioneDettagli}
          </Title>
          <Text size="xs" c="dimmed">
            {liquidazione.nota}
          </Text>
        </>
      )}
    </Blocco>
  );
}

function durata(d: DurataPreavviso | null): string {
  return d ? formatDurataPreavviso(d.mesi, d.giorni) : testi.comune.nonDisponibile;
}

/** Termini di preavviso del CCNL per livello e anzianità, con l'indennità sostitutiva se non lavorato. */
function BloccoPreavviso({ preavviso: p }: { preavviso: Preavviso }) {
  const verificato = p.stato === 'VERIFICATO';
  return (
    <Blocco titolo={t.preavviso} icona={<IconHourglass size={18} aria-hidden />}>
      {verificato ? (
        <>
          <Text size="sm" c="dimmed">
            {[p.gruppoLivelli, p.fascia && `${t.preavvisoAnzianita} ${p.fascia}`].filter(Boolean).join(' · ')}
          </Text>
          <ElencoValori
            righe={[
              { etichetta: t.preavvisoDimissioni, valore: durata(p.dimissioni), enfasi: true },
              { etichetta: t.preavvisoLicenziamento, valore: durata(p.licenziamento), enfasi: true },
            ]}
          />
          {(p.indennitaDimissioni !== null || p.indennitaLicenziamento !== null) && (
            <>
              <Title order={5} size="sm" mt="md" mb={4} c="dimmed" fw={600}>
                {t.preavvisoNonLavorato}
              </Title>
              <ElencoValori
                righe={[
                  p.indennitaDimissioni !== null && {
                    etichetta: t.preavvisoTrattenuta,
                    valore: (
                      <Stima spiegazione={p.nota ?? t.preavvisoIndennitaTooltip}>
                        {formatImporto(p.indennitaDimissioni)}
                      </Stima>
                    ),
                  },
                  p.indennitaLicenziamento !== null && {
                    etichetta: t.preavvisoIndennita,
                    valore: (
                      <Stima spiegazione={p.nota ?? t.preavvisoIndennitaTooltip}>
                        {formatImporto(p.indennitaLicenziamento)}
                      </Stima>
                    ),
                  },
                ]}
              />
            </>
          )}
          <Text size="xs" c="dimmed" mt="xs">
            {[
              p.prossimaFascia && `${t.preavvisoProssimaFascia} ${formatMese(p.prossimaFascia)}.`,
              p.riferimento && `${t.preavvisoCcnl}: ${p.riferimento}.`,
              t.preavvisoEsclusioni,
              p.indennitaDimissioni === null && p.nota,
            ]
              .filter(Boolean)
              .join(' ')}
          </Text>
        </>
      ) : (
        <Text size="sm" c="dimmed">
          {p.nota}
        </Text>
      )}
    </Blocco>
  );
}
