import {
  Badge,
  em,
  Group,
  List,
  Paper,
  SimpleGrid,
  Skeleton,
  Stack,
  Table,
  Text,
  Title,
} from '@mantine/core';
import { useMediaQuery } from '@mantine/hooks';
import { useId, type ReactNode } from 'react';
import { useCedolino } from '../api/queries';
import type { CedolinoDettaglio, RateoCedolino, Voce } from '../api/types';
import {
  etichettaCampoCedolino,
  formatData,
  formatImporto,
  formatIstante,
  formatMese,
  formatMeseDiData,
  formatNumero,
  formatPercentuale,
} from '../format/format';
import { testi } from '../i18n';
import { ElencoValori, type RigaValore } from './ElencoValori';
import { ErroreConRiprova } from './ErroreConRiprova';

const t = testi.cedolino;

interface Props {
  cedolinoId: number;
  /** Il dettaglio si scarica solo quando la barra è espansa. */
  abilitato: boolean;
}

/** Dettaglio della busta paga per aree tematiche. */
export function CedolinoDettaglioView({ cedolinoId, abilitato }: Props) {
  const cedolino = useCedolino(cedolinoId, abilitato);

  if (cedolino.isPending) {
    return (
      <Stack gap="sm" aria-busy="true" aria-label={t.caricamento}>
        <Skeleton height={120} radius="md" />
        <Skeleton height={160} radius="md" />
      </Stack>
    );
  }
  if (cedolino.isError) {
    return (
      <ErroreConRiprova titolo={t.errore} errore={cedolino.error} onRiprova={() => void cedolino.refetch()} />
    );
  }
  return <Busta cedolino={cedolino.data} />;
}

function importoORiga(etichetta: string, valore: number | null, enfasi = false): RigaValore | null {
  return valore === null ? null : { etichetta, valore: formatImporto(valore), enfasi };
}

function numeroORiga(etichetta: string, valore: number | null): RigaValore | null {
  return valore === null ? null : { etichetta, valore: formatNumero(valore) };
}

function testoORiga(etichetta: string, valore: string | null): RigaValore | null {
  return valore === null || valore.length === 0 ? null : { etichetta, valore };
}

function Busta({ cedolino: c }: { cedolino: CedolinoDettaglio }) {
  const e = c.elementiRetributivi;
  const tfr = c.tfr;

  return (
    <Stack gap="sm">
      <SimpleGrid cols={{ base: 1, md: 2 }} spacing="sm">
        <Sezione titolo={t.generale}>
          <ElencoValori
            righe={[
              { etichetta: t.periodoRetribuzione, valore: formatMese(c.periodoRetribuzione) },
              c.meseVariabili !== null &&
                c.meseVariabili !== c.periodoRetribuzione && {
                  etichetta: t.meseVariabili,
                  valore: formatMese(c.meseVariabili),
                },
              c.dataElaborazione !== null && {
                etichetta: t.dataElaborazione,
                valore: formatData(c.dataElaborazione),
              },
              testoORiga(t.qualifica, c.inquadramento.qualifica),
              testoORiga(t.livello, c.inquadramento.livello),
              testoORiga(t.ccnlTesto, c.inquadramento.ccnlTesto),
              c.inquadramento.dataAssunzione !== null && {
                etichetta: t.dataAssunzione,
                valore: formatData(c.inquadramento.dataAssunzione),
              },
              c.inquadramento.dataCessazione !== null && {
                etichetta: t.dataCessazione,
                valore: formatData(c.inquadramento.dataCessazione),
              },
              { etichetta: t.nomeFile, valore: c.nomeFile },
            ]}
          />
          {(c.contieneTredicesima || c.contieneQuattordicesima) && (
            <Group gap={6} mt="xs">
              {c.contieneTredicesima && <BadgeInfo>{t.contieneTredicesima}</BadgeInfo>}
              {c.contieneQuattordicesima && <BadgeInfo>{t.contieneQuattordicesima}</BadgeInfo>}
            </Group>
          )}
        </Sezione>
        <Sezione titolo={t.totali}>
          <ElencoValori
            righe={[
              importoORiga(t.lordo, c.totali.lordo),
              importoORiga(t.totaleCompetenze, c.totali.totaleCompetenze),
              importoORiga(t.totaleTrattenute, c.totali.totaleTrattenute),
              c.totali.arrotondamento !== null &&
                c.totali.arrotondamento !== 0 &&
                importoORiga(t.arrotondamento, c.totali.arrotondamento),
              importoORiga(t.netto, c.totali.netto, true),
            ]}
          />
        </Sezione>
        <Sezione titolo={t.elementiRetributivi}>
          <ElencoValori
            righe={[
              importoORiga(t.retribuzioneBase, e.retribuzioneBase),
              importoORiga(t.minimo, e.minimo),
              importoORiga(t.contingenza, e.contingenza),
              e.superminimo !== null && {
                etichetta: e.superminimoAssorbibile
                  ? `${t.superminimo} (${t.superminimoAssorbibile})`
                  : t.superminimo,
                valore: formatImporto(e.superminimo),
              },
              importoORiga(t.importoScatti, e.importoScatti),
              importoORiga(t.altriElementiFissi, e.altriElementiFissi),
              importoORiga(t.totaleElementi, e.totale, true),
              importoORiga(t.pagaGiornaliera, e.pagaGiornaliera),
              importoORiga(t.pagaOraria, e.pagaOraria),
              numeroORiga(t.divisoreGiornaliero, e.divisoreGiornaliero),
              numeroORiga(t.divisoreOrario, e.divisoreOrario),
            ]}
          />
        </Sezione>
        <Sezione titolo={t.presenze}>
          <ElencoValori
            righe={[
              numeroORiga(t.giorniLavorati, c.presenze.giorniLavorati),
              numeroORiga(t.oreLavorate, c.presenze.oreLavorate),
              numeroORiga(t.giorniRetribuiti, c.presenze.giorniRetribuiti),
            ]}
          />
          {c.presenze.voci.length > 0 && <TabellaVoci voci={c.presenze.voci} senzaImporti />}
          {c.presenze.giorniLavorati === null &&
            c.presenze.oreLavorate === null &&
            c.presenze.giorniRetribuiti === null &&
            c.presenze.voci.length === 0 && <NessunDato />}
        </Sezione>
      </SimpleGrid>

      <SezioneVoci titolo={t.competenze} voci={c.competenze} />
      <SezioneVoci titolo={t.trattenutePrevidenziali} voci={c.trattenutePrevidenziali} />
      <SezioneVoci titolo={t.trattenuteFiscali} voci={c.trattenuteFiscali} />
      <SezioneVoci titolo={t.altreTrattenute} voci={c.altreTrattenute} />
      <SezioneVoci
        titolo={t.vociNonRetributive}
        voci={c.vociNonRetributive}
        nota={t.vociNonRetributiveNota}
      />
      <SezioneVoci titolo={t.vociInformative} voci={c.vociInformative} />

      <SimpleGrid cols={{ base: 1, md: 2 }} spacing="sm">
        {tfr && (
          <Sezione titolo={t.tfr}>
            <ElencoValori
              righe={[
                importoORiga(t.tfrFondoPrecedente, tfr.fondo31DicembreAnnoPrecedente),
                importoORiga(t.tfrRivalutazione, tfr.rivalutazione),
                importoORiga(t.tfrImpostaSostitutiva, tfr.impostaSostitutiva),
                importoORiga(t.tfrQuotaAnno, tfr.quotaAnno),
                importoORiga(t.tfrQuotaMese, tfr.quotaMese),
                tfr.quotaFondi !== 0 && importoORiga(t.tfrQuotaFondi, tfr.quotaFondi),
                tfr.quotaBusta !== 0 && importoORiga(t.tfrQuotaBusta, tfr.quotaBusta),
                tfr.anticipi !== 0 && importoORiga(t.tfrAnticipi, tfr.anticipi),
                importoORiga(t.tfrRetribuzioneUtile, tfr.retribuzioneUtile),
                importoORiga(t.tfrLiquidatoLordo, tfr.liquidatoLordo),
                importoORiga(t.tfrRitenuta, tfr.ritenuta),
              ]}
            />
          </Sezione>
        )}
        {c.ratei.length > 0 && (
          <Sezione titolo={t.ratei}>
            <TabellaRatei ratei={c.ratei} />
          </Sezione>
        )}
        <Sezione titolo={t.scattiEBuoni}>
          <ElencoValori
            righe={[
              c.scatti.numeroScatti !== null && {
                etichetta: t.numeroScatti,
                valore: String(c.scatti.numeroScatti),
              },
              c.scatti.prossimoScatto !== null && {
                etichetta: t.prossimoScatto,
                valore: formatMeseDiData(c.scatti.prossimoScatto),
              },
              {
                etichetta: t.buoniPasto,
                valore: c.buoniPasto.presenti ? testi.comune.si : testi.comune.no,
              },
              numeroORiga(t.buoniNumero, c.buoniPasto.numero),
              importoORiga(t.buoniValore, c.buoniPasto.valoreUnitario),
              importoORiga(t.buoniImporto, c.buoniPasto.importo),
            ]}
          />
        </Sezione>
        <Sezione titolo={t.progressivi}>
          <ElencoValori
            righe={[
              importoORiga(t.imponibilePrevidenziale, c.progressivi.imponibilePrevidenziale),
              importoORiga(t.imponibileIrpef, c.progressivi.imponibileIrpef),
              importoORiga(t.irpefTrattenuta, c.progressivi.irpefTrattenuta),
            ]}
          />
          {c.progressivi.imponibilePrevidenziale === null &&
            c.progressivi.imponibileIrpef === null &&
            c.progressivi.irpefTrattenuta === null && <NessunDato />}
        </Sezione>
      </SimpleGrid>

      <Text size="xs" c="dimmed">
        {t.acquisitoIl} {formatIstante(c.acquisitoIl)}
        {c.confidenza !== null && ` · ${t.confidenza}: ${formatPercentuale(c.confidenza)}`}
        {c.campiNonTrovati.length > 0 &&
          ` · ${t.campiNonTrovati}: ${c.campiNonTrovati.map(etichettaCampoCedolino).join(', ')}`}
      </Text>
      {c.avvisiEstrazione.length > 0 && (
        <Sezione titolo={t.avvisiEstrazione}>
          <List size="sm" spacing={2}>
            {c.avvisiEstrazione.map((avviso) => (
              <List.Item key={avviso}>{avviso}</List.Item>
            ))}
          </List>
        </Sezione>
      )}
    </Stack>
  );
}

function BadgeInfo({ children }: { children: ReactNode }) {
  return (
    <Badge size="sm" variant="light" color="marchio" style={{ textTransform: 'none' }}>
      {children}
    </Badge>
  );
}

function NessunDato() {
  return (
    <Text size="sm" c="dimmed">
      {t.nessunDato}
    </Text>
  );
}

interface SezioneProps {
  titolo: string;
  children: ReactNode;
  nota?: string;
}

function Sezione({ titolo, children, nota }: SezioneProps) {
  const id = useId();
  return (
    <Paper withBorder p="sm" component="section" aria-labelledby={id}>
      <Title order={5} size="sm" id={id} mb={4} c="marchio.8">
        {titolo}
      </Title>
      {nota && (
        <Text size="xs" c="dimmed" mb={4}>
          {nota}
        </Text>
      )}
      {children}
    </Paper>
  );
}

function SezioneVoci({ titolo, voci, nota }: { titolo: string; voci: Voce[]; nota?: string }) {
  if (voci.length === 0) {
    return null;
  }
  return (
    <Sezione titolo={titolo} {...(nota ? { nota } : {})}>
      <TabellaVoci voci={voci} />
    </Sezione>
  );
}

const cellaNumerica = {
  textAlign: 'right',
  fontVariantNumeric: 'tabular-nums',
  whiteSpace: 'nowrap',
} as const;

function quantita(voce: Voce): string {
  if (voce.quantita === null) {
    return '';
  }
  return voce.unita ? `${formatNumero(voce.quantita)} ${voce.unita}` : formatNumero(voce.quantita);
}

/** Vero sugli schermi del telefono, dove le tabelle vengono compattate. */
function useSchermoStretto(): boolean {
  return useMediaQuery(`(max-width: ${em(575)})`);
}

function TabellaVoci({ voci, senzaImporti = false }: { voci: Voce[]; senzaImporti?: boolean }) {
  // si mostrano solo le colonne con almeno un valore; sugli schermi stretti si omette la base, la meno utile,
  // e si compatta la tabella perche' gli importi restino visibili senza scorrere
  const schermoStretto = useSchermoStretto();
  const conQuantita = voci.some((v) => v.quantita !== null);
  const conBase = !senzaImporti && !schermoStretto && voci.some((v) => v.base !== null);
  const conCompetenza = !senzaImporti && voci.some((v) => v.importoCompetenza !== null);
  const conTrattenuta = !senzaImporti && voci.some((v) => v.importoTrattenuta !== null);
  const colonneNumeriche = [conQuantita, conBase, conCompetenza, conTrattenuta].filter(Boolean).length;
  return (
    <Table.ScrollContainer minWidth={110 + colonneNumeriche * 80} type="native">
      <Table
        fz={schermoStretto ? 'xs' : 'sm'}
        verticalSpacing={4}
        horizontalSpacing={schermoStretto ? 6 : 'xs'}
        striped
        stripedColor="gray.0"
      >
        <Table.Thead>
          <Table.Tr>
            <Table.Th>{t.voceDescrizione}</Table.Th>
            {conQuantita && <Table.Th style={cellaNumerica}>{t.voceQuantita}</Table.Th>}
            {conBase && <Table.Th style={cellaNumerica}>{t.voceBase}</Table.Th>}
            {conCompetenza && <Table.Th style={cellaNumerica}>{t.voceCompetenza}</Table.Th>}
            {conTrattenuta && <Table.Th style={cellaNumerica}>{t.voceTrattenuta}</Table.Th>}
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {voci.map((v, i) => (
            <Table.Tr key={`${v.codice ?? ''}-${v.descrizione}-${i}`}>
              <Table.Td>
                {v.codice && (
                  <Text component="span" size="xs" c="dimmed" ff="monospace" mr={6}>
                    {v.codice}
                  </Text>
                )}
                {v.descrizione}
              </Table.Td>
              {conQuantita && <Table.Td style={cellaNumerica}>{quantita(v)}</Table.Td>}
              {conBase && (
                <Table.Td style={cellaNumerica}>{v.base !== null ? formatNumero(v.base, 5) : ''}</Table.Td>
              )}
              {conCompetenza && (
                <Table.Td style={cellaNumerica}>
                  {v.importoCompetenza !== null ? formatImporto(v.importoCompetenza) : ''}
                </Table.Td>
              )}
              {conTrattenuta && (
                <Table.Td style={{ ...cellaNumerica, color: 'var(--mantine-color-red-8)' }}>
                  {v.importoTrattenuta !== null ? formatImporto(v.importoTrattenuta) : ''}
                </Table.Td>
              )}
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </Table.ScrollContainer>
  );
}

function TabellaRatei({ ratei }: { ratei: RateoCedolino[] }) {
  // sul telefono il residuo dell'anno precedente si omette: restano maturato, goduto e saldo
  const schermoStretto = useSchermoStretto();
  const unita = (r: RateoCedolino) => (r.unita === 'GIORNI' ? testi.unita.giornoBreve : testi.unita.oraBreve);
  const valore = (r: RateoCedolino, v: number | null) => (v === null ? '' : `${formatNumero(v)} ${unita(r)}`);
  return (
    <Table.ScrollContainer minWidth={260} type="native">
      <Table
        fz={schermoStretto ? 'xs' : 'sm'}
        verticalSpacing={4}
        horizontalSpacing={schermoStretto ? 6 : 'xs'}
      >
        <Table.Thead>
          <Table.Tr>
            <Table.Th />
            {!schermoStretto && <Table.Th style={cellaNumerica}>{t.rateoResiduoPrecedente}</Table.Th>}
            <Table.Th style={cellaNumerica}>{t.rateoMaturato}</Table.Th>
            <Table.Th style={cellaNumerica}>{t.rateoGoduto}</Table.Th>
            <Table.Th style={cellaNumerica}>{t.rateoSaldo}</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {ratei.map((r) => (
            <Table.Tr key={r.tipo}>
              <Table.Td>{testi.rateo[r.tipo]}</Table.Td>
              {!schermoStretto && (
                <Table.Td style={cellaNumerica}>{valore(r, r.residuoAnnoPrecedente)}</Table.Td>
              )}
              <Table.Td style={cellaNumerica}>{valore(r, r.maturato)}</Table.Td>
              <Table.Td style={cellaNumerica}>{valore(r, r.goduto)}</Table.Td>
              <Table.Td style={{ ...cellaNumerica, fontWeight: 600 }}>{valore(r, r.saldo)}</Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </Table.ScrollContainer>
  );
}
