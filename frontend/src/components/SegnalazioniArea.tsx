import { Badge, Box, Group, List, Paper, SimpleGrid, Text } from '@mantine/core';
import { IconAlertTriangle } from '@tabler/icons-react';
import { useId } from 'react';
import type { Segnalazione } from '../api/types';
import { testi } from '../i18n';
import { TitoloArea } from './TitoloArea';
import { TriangoloAvviso } from './TriangoloAvviso';
import classes from './SegnalazioniArea.module.css';

interface Props {
  segnalazioni: Segnalazione[];
}

/** Area segnalazioni: un riquadro arancione per ogni anomalia rilevata. */
export function SegnalazioniArea({ segnalazioni }: Props) {
  const idTitolo = useId();
  return (
    <section aria-labelledby={idTitolo}>
      <TitoloArea id={idTitolo} icona={<TriangoloAvviso dimensione={20} />}>
        {testi.segnalazioni.titolo}
      </TitoloArea>
      <SimpleGrid cols={{ base: 1, md: segnalazioni.length > 1 ? 2 : 1 }} spacing="sm">
        {segnalazioni.map((s) => (
          <SegnalazioneRiquadro key={s.id} segnalazione={s} />
        ))}
      </SimpleGrid>
    </section>
  );
}

function SegnalazioneRiquadro({ segnalazione }: { segnalazione: Segnalazione }) {
  return (
    <Paper className={classes.riquadro} p="md" role="article" aria-label={segnalazione.titolo}>
      <IconAlertTriangle className={classes.filigrana} aria-hidden stroke={1.5} />
      <Box className={classes.contenuto}>
        <Group gap="xs" wrap="nowrap" align="flex-start" justify="space-between">
          <Group gap="xs" wrap="nowrap" align="flex-start">
            <TriangoloAvviso dimensione={22} />
            <Text fw={700} lh={1.35}>
              {segnalazione.titolo}
            </Text>
          </Group>
          <Badge size="xs" variant="outline" color="orange" className={classes.gravita}>
            {testi.segnalazioni.gravita[segnalazione.gravita]}
          </Badge>
        </Group>
        <Text c="orange.9" size="sm" mt={6} className={classes.descrizione}>
          {segnalazione.descrizione}
        </Text>
        {segnalazione.evidenze.length > 0 && (
          <List
            size="xs"
            mt="xs"
            spacing={2}
            className={classes.evidenze}
            aria-label={testi.segnalazioni.evidenze}
          >
            {segnalazione.evidenze.map((e, i) => (
              <List.Item key={i}>
                {e.descrizione}
                {e.valore && (
                  <Text component="span" fw={600} inherit>
                    : {e.valore}
                  </Text>
                )}
              </List.Item>
            ))}
          </List>
        )}
      </Box>
    </Paper>
  );
}
