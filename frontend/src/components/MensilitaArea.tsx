import {
  ActionIcon,
  Badge,
  Box,
  Button,
  Collapse,
  Group,
  Menu,
  Paper,
  Stack,
  Text,
  Tooltip,
  UnstyledButton,
  VisuallyHidden,
} from '@mantine/core';
import { modals } from '@mantine/modals';
import { notifications } from '@mantine/notifications';
import {
  IconCalendarMonth,
  IconChevronDown,
  IconDots,
  IconDownload,
  IconFileAlert,
  IconLink,
  IconLinkOff,
  IconPencil,
  IconStairsUp,
  IconTrash,
  IconTrendingUp,
} from '@tabler/icons-react';
import { useState, useId, type ReactNode } from 'react';
import { useApiClient } from '../api/context';
import { scaricaPdfCedolino } from '../api/download';
import { messaggioErrore } from '../api/errors';
import { useCaricamentiInErrore, useEliminaCedolino } from '../api/queries';
import type { CaricamentoInErrore, CedolinoModificato, RigaMensilita } from '../api/types';
import { formatImporto, formatQuantitaBreve } from '../format/format';
import { completabileAMano } from '../hooks/useCedolinoManuale';
import { testi } from '../i18n';
import { useAzioniCaricamenti } from './azioniCaricamenti';
import { CedolinoDettaglioView } from './CedolinoDettaglioView';
import { TitoloArea } from './TitoloArea';
import { TriangoloAvviso } from './TriangoloAvviso';
import classes from './MensilitaArea.module.css';

const t = testi.mensilita;

interface Props {
  rapportoId: number;
  /** Già in ordine cronologico inverso (dal backend). */
  mensilita: RigaMensilita[];
}

/**
 * Area mensilità: una barra per cedolino; più barre possono essere aperte insieme. Le barre aperte sono ricordate per
 * id del cedolino: la chiave di competenza non è stabile (eliminando un cedolino di fine rapporto i successivi vengono
 * rinumerati e uno di essi eredita la chiave di quello eliminato). Le mensilità senza cedolino sono mancanti
 * (arancioni) o, se l'utente vi ha associato un file caricato con errore, in errore (rosse).
 */
export function MensilitaArea({ rapportoId, mensilita }: Props) {
  const idTitolo = useId();
  const [espanse, setEspanse] = useState<ReadonlySet<number>>(() => new Set());
  const inErrore = useCaricamentiInErrore();
  // una mensilità mancante si può associare solo se c'è un file in errore libero
  const fileAssociabili = (inErrore.data ?? []).some((e) => e.attribuzione === null && !e.inElaborazione);

  const alterna = (cedolinoId: number) =>
    setEspanse((attuali) => {
      const nuove = new Set(attuali);
      if (nuove.has(cedolinoId)) {
        nuove.delete(cedolinoId);
      } else {
        nuove.add(cedolinoId);
      }
      return nuove;
    });

  // il cedolino modificato e' sostituito da uno nuovo (nuovo id): la sua barra resta aperta
  const sostituisci = (precedente: number, modificato: CedolinoModificato) =>
    setEspanse((attuali) => {
      const nuove = new Set(attuali);
      nuove.delete(precedente);
      if (modificato.rapportoId === rapportoId) {
        nuove.add(modificato.cedolinoId);
      }
      return nuove;
    });

  const presenti = mensilita.filter((m) => m.stato === 'PRESENTE').length;

  return (
    <section aria-labelledby={idTitolo}>
      <TitoloArea
        id={idTitolo}
        icona={<IconCalendarMonth size={20} aria-hidden color="var(--mantine-color-marchio-7)" />}
        destra={
          <Badge variant="light" color="gray" style={{ textTransform: 'none' }}>
            {presenti}
          </Badge>
        }
      >
        {t.titolo}
      </TitoloArea>
      {mensilita.length === 0 ? (
        <Text c="dimmed" size="sm">
          {t.nessuna}
        </Text>
      ) : (
        <Stack gap={8} component="ul" className={classes.elenco}>
          {mensilita.map((riga): ReactNode => {
            switch (riga.stato) {
              case 'PRESENTE':
                return riga.cedolinoId === null ? (
                  <MensilitaMancante
                    key={riga.chiave}
                    rapportoId={rapportoId}
                    riga={riga}
                    associabile={false}
                  />
                ) : (
                  <MensilitaPresente
                    key={`cedolino-${riga.cedolinoId}`}
                    rapportoId={rapportoId}
                    riga={riga}
                    cedolinoId={riga.cedolinoId}
                    espansa={espanse.has(riga.cedolinoId)}
                    onAlterna={alterna}
                    onModificato={sostituisci}
                  />
                );
              case 'IN_ERRORE':
                return (
                  <MensilitaInErrore
                    key={riga.chiave}
                    riga={riga}
                    elemento={inErrore.data?.find((e) => e.id === riga.caricamentoId)}
                    elencoCaricato={inErrore.isSuccess}
                  />
                );
              case 'MANCANTE':
                return (
                  <MensilitaMancante
                    key={riga.chiave}
                    rapportoId={rapportoId}
                    riga={riga}
                    associabile={fileAssociabili}
                  />
                );
            }
          })}
        </Stack>
      )}
    </section>
  );
}

interface MancanteProps {
  rapportoId: number;
  riga: RigaMensilita;
  /** Esistono file in errore non ancora associati a una mensilità. */
  associabile: boolean;
}

function MensilitaMancante({ rapportoId, riga, associabile }: MancanteProps) {
  const azioni = useAzioniCaricamenti();
  return (
    <Paper component="li" className={classes.mancante} aria-label={`${riga.titolo}: ${t.mancante}`}>
      <div className={classes.testoRiga}>
        <Text fw={600} c="orange.9">
          {riga.titolo}
        </Text>
        <Text size="xs" c="dark.4">
          {t.nessunCedolino}
        </Text>
      </div>
      <Group gap="xs" wrap="nowrap" className={classes.azioniRiga}>
        {associabile && (
          <Button
            size="xs"
            variant="subtle"
            color="dark"
            leftSection={<IconLink size={14} aria-hidden />}
            onClick={() => azioni.apriAssociazione({ rapportoId, chiave: riga.chiave, titolo: riga.titolo })}
          >
            {t.associaFile}
          </Button>
        )}
        <Tooltip label={t.mancante}>
          <span className={classes.iconaDestra} tabIndex={0} role="img" aria-label={t.mancante}>
            <TriangoloAvviso dimensione={22} />
          </span>
        </Tooltip>
      </Group>
    </Paper>
  );
}

interface InErroreProps {
  riga: RigaMensilita;
  /** Elemento dell'elenco dei file in errore associato alla mensilità (per PDF e stato dell'elaborazione). */
  elemento: CaricamentoInErrore | undefined;
  elencoCaricato: boolean;
}

/** Mensilità senza cedolino a cui l'utente ha associato un file caricato con errore. */
function MensilitaInErrore({ riga, elemento, elencoCaricato }: InErroreProps) {
  const azioni = useAzioniCaricamenti();
  const caricamentoId = riga.caricamentoId;
  const motivo = riga.motivoErrore !== null ? ` · ${testi.motivoErrore[riga.motivoErrore]}` : '';

  let azione: ReactNode = null;
  if (elemento && caricamentoId !== null && completabileAMano(elemento)) {
    azione = (
      <Button
        size="xs"
        variant="light"
        color="red"
        leftSection={<IconPencil size={14} aria-hidden />}
        onClick={() => azioni.apriInserimento(caricamentoId)}
      >
        {testi.inErrore.azione.INSERISCI_A_MANO}
      </Button>
    );
  } else if (elemento?.inElaborazione) {
    azione = (
      <Text size="xs" c="red.9">
        {testi.inErrore.inElaborazione}
      </Text>
    );
  } else if (elencoCaricato) {
    azione = (
      <Text size="xs" c="red.9">
        {t.caricaDiNuovo}
      </Text>
    );
  }

  return (
    <Paper component="li" className={classes.inErrore} aria-label={`${riga.titolo}: ${t.inErrore}`}>
      <div className={classes.testoRiga}>
        <Text fw={600} c="red.9">
          {riga.titolo}
        </Text>
        <Text size="xs" c="red.9">
          {t.inErrore}
          {motivo}
        </Text>
      </div>
      <Group gap="xs" wrap="nowrap" className={classes.azioniRiga}>
        {azione}
        {caricamentoId !== null && (
          <Menu position="bottom-end" shadow="md" disabled={elemento?.inElaborazione}>
            <Menu.Target>
              <ActionIcon
                variant="subtle"
                color="red"
                size={28}
                aria-label={`${testi.inErrore.altreAzioni} ${riga.titolo}`}
                disabled={elemento?.inElaborazione}
              >
                <IconDots size={18} aria-hidden />
              </ActionIcon>
            </Menu.Target>
            <Menu.Dropdown>
              <Menu.Item
                leftSection={<IconLinkOff size={14} aria-hidden />}
                onClick={() => azioni.rimuoviAssociazione(caricamentoId)}
              >
                {testi.inErrore.azione.RIMUOVI_ASSOCIAZIONE}
              </Menu.Item>
            </Menu.Dropdown>
          </Menu>
        )}
        <IconFileAlert
          size={22}
          color="var(--mantine-color-red-7)"
          className={classes.iconaDestra}
          aria-hidden
        />
      </Group>
    </Paper>
  );
}

interface PresenteProps {
  rapportoId: number;
  riga: RigaMensilita;
  cedolinoId: number;
  espansa: boolean;
  onAlterna: (cedolinoId: number) => void;
  onModificato: (precedente: number, modificato: CedolinoModificato) => void;
}

function MensilitaPresente({
  rapportoId,
  riga,
  cedolinoId,
  espansa,
  onAlterna,
  onModificato,
}: PresenteProps) {
  const api = useApiClient();
  const azioni = useAzioniCaricamenti();
  const elimina = useEliminaCedolino();
  const [scaricando, setScaricando] = useState(false);
  const idCorpo = `cedolino-${rapportoId}-${cedolinoId}`;

  const scarica = async () => {
    setScaricando(true);
    try {
      await scaricaPdfCedolino(api, cedolinoId, riga.nomeFile ?? `${riga.chiave}.pdf`);
    } catch (errore) {
      notifications.show({ color: 'red', title: t.scaricaErrore, message: messaggioErrore(errore) });
    } finally {
      setScaricando(false);
    }
  };

  const confermaEliminazione = () =>
    modals.openConfirmModal({
      title: t.eliminaTitolo,
      children: (
        <Text size="sm">
          <strong>{riga.titolo}</strong> — {t.eliminaTesto}
        </Text>
      ),
      labels: { confirm: t.eliminaConferma, cancel: testi.comune.annulla },
      confirmProps: { color: 'red', leftSection: <IconTrash size={16} aria-hidden /> },
      onConfirm: () =>
        elimina.mutate(cedolinoId, {
          onSuccess: () => notifications.show({ color: 'teal', message: `${t.eliminato}: ${riga.titolo}` }),
          onError: (errore) =>
            notifications.show({ color: 'red', title: t.eliminaErrore, message: messaggioErrore(errore) }),
        }),
    });

  return (
    <Paper component="li" withBorder className={classes.presente} data-espansa={espansa || undefined}>
      {/* il nome accessibile nasce dal contenuto: titolo, indicatori, ratei e netto vengono tutti annunciati */}
      <UnstyledButton
        className={classes.barra}
        onClick={() => onAlterna(cedolinoId)}
        aria-expanded={espansa}
        aria-controls={idCorpo}
      >
        <Box className={classes.titolo}>
          <Text fw={650}>
            <VisuallyHidden component="span">{espansa ? t.comprimi : t.espandi}</VisuallyHidden> {riga.titolo}
          </Text>
          {(riga.scattoInBusta || riga.aumentoFuoriScatti) && (
            <Group gap={6} wrap="nowrap">
              {riga.scattoInBusta && (
                <Tooltip label={t.scattoInBustaTooltip}>
                  <Badge
                    size="sm"
                    color="teal"
                    variant="light"
                    leftSection={<IconStairsUp size={12} aria-hidden />}
                    style={{ textTransform: 'none' }}
                  >
                    {t.scattoInBusta}
                  </Badge>
                </Tooltip>
              )}
              {riga.aumentoFuoriScatti && (
                <Tooltip label={t.aumentoTooltip}>
                  <Badge
                    size="sm"
                    color="marchio"
                    variant="light"
                    leftSection={<IconTrendingUp size={12} aria-hidden />}
                    style={{ textTransform: 'none' }}
                  >
                    {t.aumento}
                  </Badge>
                </Tooltip>
              )}
            </Group>
          )}
        </Box>
        {riga.ratei.length > 0 && (
          <Group gap={6} className={classes.ratei} wrap="wrap">
            {riga.ratei.map((r) => (
              <Text key={r.tipo} size="xs" c="dimmed" className={classes.rateo}>
                {testi.rateo[r.tipo]}{' '}
                <Text component="span" fw={600} c="dark.4" inherit>
                  {formatQuantitaBreve(r.saldo, r.unita)}
                </Text>
              </Text>
            ))}
          </Group>
        )}
        <Box className={classes.netto}>
          <Text size="xs" c="dimmed" lh={1}>
            {t.netto}
          </Text>
          <Text fw={700} style={{ fontVariantNumeric: 'tabular-nums' }}>
            {formatImporto(riga.netto)}
          </Text>
        </Box>
        <IconChevronDown className={classes.freccia} size={22} stroke={2.2} aria-hidden />
      </UnstyledButton>
      <Collapse expanded={espansa} keepMounted={false} transitionDuration={200} id={idCorpo}>
        <Box className={classes.corpo}>
          <Group justify="flex-end" gap="xs" mb="sm">
            <Button
              size="xs"
              variant="default"
              leftSection={<IconDownload size={14} aria-hidden />}
              loading={scaricando}
              onClick={() => void scarica()}
            >
              {t.scaricaPdf}
            </Button>
            <Button
              size="xs"
              variant="default"
              leftSection={<IconPencil size={14} aria-hidden />}
              onClick={() =>
                azioni.apriModifica({
                  cedolinoId,
                  rapportoId,
                  onModificato: (modificato) => onModificato(cedolinoId, modificato),
                })
              }
            >
              {testi.modifica.modifica}
            </Button>
            <Button
              size="xs"
              variant="light"
              color="red"
              leftSection={<IconTrash size={14} aria-hidden />}
              loading={elimina.isPending}
              onClick={confermaEliminazione}
            >
              {t.elimina}
            </Button>
          </Group>
          <CedolinoDettaglioView cedolinoId={cedolinoId} abilitato={espansa} />
        </Box>
      </Collapse>
    </Paper>
  );
}
