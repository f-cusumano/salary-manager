import {
  Badge,
  Box,
  Button,
  Collapse,
  Group,
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
  IconDownload,
  IconStairsUp,
  IconTrash,
  IconTrendingUp,
} from '@tabler/icons-react';
import { useState, useId } from 'react';
import { useApiClient } from '../api/context';
import { scaricaPdfCedolino } from '../api/download';
import { messaggioErrore } from '../api/errors';
import { useEliminaCedolino } from '../api/queries';
import type { RigaMensilita } from '../api/types';
import { formatImporto, formatQuantitaBreve } from '../format/format';
import { testi } from '../i18n';
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
 * rinumerati e uno di essi eredita la chiave di quello eliminato).
 */
export function MensilitaArea({ rapportoId, mensilita }: Props) {
  const idTitolo = useId();
  const [espanse, setEspanse] = useState<ReadonlySet<number>>(() => new Set());

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
          {mensilita.map((riga) =>
            riga.stato === 'MANCANTE' || riga.cedolinoId === null ? (
              <MensilitaMancante key={riga.chiave} riga={riga} />
            ) : (
              <MensilitaPresente
                key={`cedolino-${riga.cedolinoId}`}
                rapportoId={rapportoId}
                riga={riga}
                cedolinoId={riga.cedolinoId}
                espansa={espanse.has(riga.cedolinoId)}
                onAlterna={alterna}
              />
            ),
          )}
        </Stack>
      )}
    </section>
  );
}

function MensilitaMancante({ riga }: { riga: RigaMensilita }) {
  return (
    <Paper component="li" className={classes.mancante} aria-label={`${riga.titolo}: ${t.mancante}`}>
      <Text fw={600} c="orange.9">
        {riga.titolo}
      </Text>
      <Tooltip label={t.mancante}>
        <span className={classes.iconaDestra} tabIndex={0} role="img" aria-label={t.mancante}>
          <TriangoloAvviso dimensione={22} />
        </span>
      </Tooltip>
    </Paper>
  );
}

interface PresenteProps {
  rapportoId: number;
  riga: RigaMensilita;
  cedolinoId: number;
  espansa: boolean;
  onAlterna: (cedolinoId: number) => void;
}

function MensilitaPresente({ rapportoId, riga, cedolinoId, espansa, onAlterna }: PresenteProps) {
  const api = useApiClient();
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
