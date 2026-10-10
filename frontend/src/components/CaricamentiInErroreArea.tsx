import {
  ActionIcon,
  Badge,
  Button,
  Group,
  Loader,
  Menu,
  Paper,
  Stack,
  Text,
  ThemeIcon,
  Tooltip,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconCalendarMonth,
  IconDots,
  IconDownload,
  IconFileAlert,
  IconKey,
  IconLink,
  IconLinkOff,
  IconPencil,
  IconRefresh,
  IconTrash,
  IconUpload,
} from '@tabler/icons-react';
import { useId, type ReactNode } from 'react';
import { useApiClient } from '../api/context';
import { scaricaPdfCaricamento } from '../api/download';
import { messaggioErrore } from '../api/errors';
import { useCaricamentiInErrore } from '../api/queries';
import type { AttribuzioneCaricamento, CaricamentoInErrore } from '../api/types';
import { formatIstante } from '../format/format';
import { azioniPerCaricamento, type AzioneFileInErrore } from '../hooks/useCedolinoManuale';
import { testi } from '../i18n';
import { useAzioniCaricamenti } from './azioniCaricamenti';
import { ErroreConRiprova } from './ErroreConRiprova';
import { MotivoErroreBadge } from './MotivoErroreBadge';
import { TitoloArea } from './TitoloArea';
import classes from './CaricamentiInErroreArea.module.css';

const t = testi.inErrore;

/** Id dell'area, raggiunta dall'indicatore della barra superiore. */
export const ID_AREA_CARICAMENTI_IN_ERRORE = 'caricamenti-in-errore';

const ICONA_AZIONE: Record<AzioneFileInErrore, ReactNode> = {
  INSERISCI_A_MANO: <IconPencil size={14} aria-hidden />,
  RIPROVA_CON_PASSWORD: <IconKey size={14} aria-hidden />,
  RIPROVA: <IconRefresh size={14} aria-hidden />,
  ASSOCIA: <IconLink size={14} aria-hidden />,
  CAMBIA_MESE: <IconCalendarMonth size={14} aria-hidden />,
  RIMUOVI_ASSOCIAZIONE: <IconLinkOff size={14} aria-hidden />,
  SCARICA_PDF: <IconDownload size={14} aria-hidden />,
  RIMUOVI: <IconTrash size={14} aria-hidden />,
  CARICA_DI_NUOVO: <IconUpload size={14} aria-hidden />,
};

/**
 * Cedolini caricati ma non letti, per tutte le persone: in cima alla pagina finché ce n'è almeno uno, in rosso per
 * distinguerli dalle mensilità mancanti (arancioni), con le azioni per completarli.
 */
export function CaricamentiInErroreArea() {
  const inErrore = useCaricamentiInErrore();
  const idTitolo = useId();

  if (inErrore.isError) {
    return (
      <ErroreConRiprova titolo={t.errore} errore={inErrore.error} onRiprova={() => void inErrore.refetch()} />
    );
  }
  const elenco = inErrore.data ?? [];
  if (elenco.length === 0) {
    return null;
  }

  return (
    <Paper
      component="section"
      id={ID_AREA_CARICAMENTI_IN_ERRORE}
      aria-labelledby={idTitolo}
      tabIndex={-1}
      withBorder
      p={{ base: 'sm', sm: 'md' }}
      className={classes.area}
    >
      <TitoloArea
        id={idTitolo}
        icona={<IconFileAlert size={20} aria-hidden color="var(--mantine-color-red-7)" />}
        destra={
          <Badge color="red" variant="filled" style={{ textTransform: 'none' }}>
            {elenco.length}
          </Badge>
        }
      >
        {t.titolo}
      </TitoloArea>
      <Text size="sm" c="dimmed" mt={-4} mb="sm">
        {t.spiegazione}
      </Text>
      <Stack gap={8} component="ul" className={classes.elenco}>
        {elenco.map((elemento) => (
          <RigaCaricamentoInErrore key={elemento.id} elemento={elemento} />
        ))}
      </Stack>
    </Paper>
  );
}

function ChipAssociazione({ attribuzione }: { attribuzione: AttribuzioneCaricamento | null }) {
  if (attribuzione === null) {
    return (
      <Badge size="sm" color="gray" variant="outline" style={{ textTransform: 'none' }}>
        {t.nonAssociato}
      </Badge>
    );
  }
  if (attribuzione.competenzaPresente) {
    return (
      <Badge size="sm" color="teal" variant="light" maw="100%" style={{ textTransform: 'none' }}>
        {attribuzione.titolo} {t.oraPresente}
      </Badge>
    );
  }
  return (
    <Badge
      size="sm"
      color="gray"
      variant="light"
      maw="100%"
      leftSection={<IconLink size={12} aria-hidden />}
      title={`${attribuzione.persona} · ${attribuzione.azienda}`}
      style={{ textTransform: 'none' }}
    >
      {attribuzione.titolo} · {attribuzione.azienda}
    </Badge>
  );
}

function RigaCaricamentoInErrore({ elemento: e }: { elemento: CaricamentoInErrore }) {
  const api = useApiClient();
  const azioni = useAzioniCaricamenti();
  const { principale, menu, suggerimento, bloccate } = azioniPerCaricamento(e);

  const scarica = async () => {
    try {
      await scaricaPdfCaricamento(api, e.id, e.nomeFileOriginale);
    } catch (errore) {
      notifications.show({ color: 'red', title: t.scaricaErrore, message: messaggioErrore(errore) });
    }
  };

  const esegui = (azione: AzioneFileInErrore) => {
    switch (azione) {
      case 'INSERISCI_A_MANO':
        azioni.apriInserimento(e.id);
        return;
      case 'RIPROVA_CON_PASSWORD':
      case 'RIPROVA':
        azioni.apriRilettura(e.id);
        return;
      case 'ASSOCIA':
      case 'CAMBIA_MESE':
        azioni.apriAssociazione({ caricamentoId: e.id });
        return;
      case 'RIMUOVI_ASSOCIAZIONE':
        azioni.rimuoviAssociazione(e.id);
        return;
      case 'SCARICA_PDF':
        void scarica();
        return;
      case 'RIMUOVI':
        azioni.rimuovi(e.id);
        return;
      case 'CARICA_DI_NUOVO':
        azioni.caricaDiNuovo();
        return;
    }
  };

  return (
    <Paper
      component="li"
      withBorder
      className={classes.riga}
      aria-label={`${e.nomeFileOriginale}: ${t.suffisso}`}
      aria-busy={e.inElaborazione || undefined}
    >
      <ThemeIcon variant="light" color="red" radius="xl" size={30} className={classes.icona}>
        <IconFileAlert size={18} aria-hidden />
      </ThemeIcon>
      <div className={classes.testo}>
        <Group gap="xs" wrap="wrap" style={{ rowGap: 0 }}>
          <Text size="sm" fw={600} className={classes.nomeFile} title={e.nomeFileOriginale}>
            {e.nomeFileOriginale}
          </Text>
          <Text size="xs" c="dimmed">
            {formatIstante(e.creatoIl)}
          </Text>
        </Group>
        <Group gap={6} mt={4} wrap="wrap">
          <MotivoErroreBadge motivo={e.motivo} />
          <ChipAssociazione attribuzione={e.attribuzione} />
          {e.inElaborazione && (
            <Badge
              size="sm"
              color="marchio"
              variant="light"
              leftSection={<Loader size={10} color="marchio" />}
              style={{ textTransform: 'none' }}
            >
              {t.inElaborazione}
            </Badge>
          )}
        </Group>
        {e.messaggio.length > 0 && (
          <Text size="xs" c="red.9" mt={4}>
            {e.messaggio}
          </Text>
        )}
        {suggerimento !== null && (
          <Text size="xs" fw={600} c="dark.4" mt={2}>
            {t.suggerimento[suggerimento]}
          </Text>
        )}
      </div>
      <Group gap={6} wrap="nowrap" className={classes.azioni}>
        <Tooltip label={t.attendiElaborazione} disabled={!bloccate}>
          <Button
            size="xs"
            variant={principale === 'RIMUOVI' ? 'light' : 'filled'}
            color={principale === 'RIMUOVI' ? 'red' : undefined}
            leftSection={ICONA_AZIONE[principale]}
            // disabilitato ma raggiungibile: il tooltip spiega perche'
            data-disabled={bloccate || undefined}
            aria-disabled={bloccate || undefined}
            onClick={(evento) => {
              if (bloccate) {
                evento.preventDefault();
                return;
              }
              esegui(principale);
            }}
          >
            {t.azione[principale]}
          </Button>
        </Tooltip>
        <Menu position="bottom-end" shadow="md" disabled={bloccate}>
          <Menu.Target>
            <ActionIcon
              variant="subtle"
              color="gray"
              size={30}
              aria-label={`${t.altreAzioni} ${e.nomeFileOriginale}`}
              disabled={bloccate}
            >
              <IconDots size={18} aria-hidden />
            </ActionIcon>
          </Menu.Target>
          <Menu.Dropdown>
            {menu.map((azione) => (
              <Menu.Item
                key={azione}
                leftSection={ICONA_AZIONE[azione]}
                color={azione === 'RIMUOVI' ? 'red' : undefined}
                onClick={() => esegui(azione)}
              >
                {t.azione[azione]}
              </Menu.Item>
            ))}
          </Menu.Dropdown>
        </Menu>
      </Group>
    </Paper>
  );
}
