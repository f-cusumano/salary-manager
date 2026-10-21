import { Alert, Button, Group, List, Paper, Stack, Text, ThemeIcon } from '@mantine/core';
import {
  IconAlertTriangle,
  IconBan,
  IconBuildingFactory2,
  IconBuildingSkyscraper,
  IconCalendarCheck,
  IconTrash,
  IconUser,
} from '@tabler/icons-react';
import type { ReactNode, Ref } from 'react';
import type { VerificaCedolinoManuale } from '../api/types';
import { avvisoDelleAnagrafiche } from '../hooks/useCedolinoManuale';
import { testi } from '../i18n';

const t = testi.manuale;

interface Props {
  esito: VerificaCedolinoManuale;
  /** I dati sono cambiati dopo la verifica: va ripetuta prima di salvare. */
  superata: boolean;
  /** Rimozione del file scartato; senza, nessun pulsante (nella modifica di un cedolino il file resta). */
  onRimuoviFile?: () => void;
  ref?: Ref<HTMLDivElement>;
}

function Riga({ icona, colore, children }: { icona: ReactNode; colore: string; children: ReactNode }) {
  return (
    <Group gap="xs" wrap="nowrap" align="flex-start">
      <ThemeIcon variant="light" color={colore} size="sm" radius="xl" aria-hidden>
        {icona}
      </ThemeIcon>
      <Text size="sm" c={colore === 'orange' ? 'orange.9' : undefined}>
        {children}
      </Text>
    </Group>
  );
}

/**
 * Esito della verifica dell'inserimento a mano: come verra' registrato il cedolino, se persona, azienda e rapporto
 * esistono gia' o verranno creati, gli avvisi; per un duplicato o un aggiuntivo il motivo e la rimozione del file.
 */
export function PannelloVerifica({ esito, superata, onRimuoviFile, ref }: Props) {
  const avvisi = esito.avvisi.filter((avviso) => !avvisoDelleAnagrafiche(avviso));
  const scartato = esito.esito !== 'ACQUISIBILE';
  return (
    <Paper
      ref={ref}
      withBorder
      p="sm"
      radius="md"
      role="region"
      aria-label={t.esitoVerifica}
      tabIndex={-1}
      style={{ opacity: superata ? 0.6 : 1 }}
    >
      <Stack gap="xs">
        <Text fw={600} size="sm">
          {t.esitoVerifica}
        </Text>
        {scartato ? (
          <Alert color="red" variant="light" title={t.nonSalvabile} icon={<IconBan aria-hidden />}>
            <Stack gap="xs" align="flex-start">
              <Text size="sm">{esito.messaggio}</Text>
              {onRimuoviFile && (
                <Button
                  size="xs"
                  color="red"
                  variant="light"
                  leftSection={<IconTrash size={14} aria-hidden />}
                  onClick={onRimuoviFile}
                >
                  {t.rimuoviFile}
                </Button>
              )}
            </Stack>
          </Alert>
        ) : (
          <>
            {esito.titolo && (
              <Riga icona={<IconCalendarCheck size={14} />} colore="teal">
                {t.registratoCome} <strong>{esito.titolo}</strong>
              </Riga>
            )}
            {esito.nuovaPersona ? (
              <Riga icona={<IconUser size={14} />} colore="orange">
                {t.nuovaPersona}
              </Riga>
            ) : (
              <Riga icona={<IconUser size={14} />} colore="teal">
                {t.dipendentePresente}
              </Riga>
            )}
            {esito.nuovaAzienda ? (
              <Riga icona={<IconBuildingFactory2 size={14} />} colore="orange">
                {t.nuovaAzienda}
              </Riga>
            ) : (
              <Riga icona={<IconBuildingFactory2 size={14} />} colore="teal">
                {t.aziendaPresente}
              </Riga>
            )}
            {esito.nuovoRapporto ? (
              <Riga icona={<IconBuildingSkyscraper size={14} />} colore="orange">
                {t.nuovoRapporto}
              </Riga>
            ) : (
              <Riga icona={<IconBuildingSkyscraper size={14} />} colore="teal">
                {t.rapportoPresente}
              </Riga>
            )}
          </>
        )}
        {avvisi.length > 0 && (
          <List
            size="sm"
            spacing={2}
            icon={<IconAlertTriangle size={14} color="var(--mantine-color-orange-7)" />}
          >
            {avvisi.map((avviso) => (
              <List.Item key={avviso}>
                <Text span size="sm" c="orange.9">
                  {avviso}
                </Text>
              </List.Item>
            ))}
          </List>
        )}
        {superata && (
          <Text size="sm" c="dimmed" fs="italic">
            {t.verificaDaRifare}
          </Text>
        )}
      </Stack>
    </Paper>
  );
}
