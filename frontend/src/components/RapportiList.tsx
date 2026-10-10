import { Collapse, Paper, Skeleton, Stack, Text, Tooltip, UnstyledButton } from '@mantine/core';
import { IconChevronRight } from '@tabler/icons-react';
import { useState } from 'react';
import { useRapporti } from '../api/queries';
import type { RapportoSintesi } from '../api/types';
import { formatPeriodo } from '../format/format';
import { testi } from '../i18n';
import { ErroreConRiprova } from './ErroreConRiprova';
import { RapportoDettaglioView } from './RapportoDettaglioView';
import { TriangoloAvviso } from './TriangoloAvviso';
import classes from './RapportiList.module.css';

interface Props {
  personaId: number;
}

/** Rapporto aperto di default: quello attuale, altrimenti il più recente. */
function rapportoPredefinito(rapporti: RapportoSintesi[]): number | null {
  return (rapporti.find((r) => r.attuale) ?? rapporti[0])?.id ?? null;
}

/**
 * Aziende della persona in ordine cronologico inverso (ordine fornito dal backend), con un solo dettaglio aperto
 * alla volta.
 */
export function RapportiList({ personaId }: Props) {
  const rapporti = useRapporti(personaId);
  // undefined = nessuna scelta dell'utente: vale il rapporto predefinito
  const [scelto, setScelto] = useState<number | null | undefined>(undefined);

  if (rapporti.isPending) {
    return (
      <Stack gap="sm" aria-busy="true" aria-label={testi.rapporti.caricamento}>
        <Skeleton height={56} radius="md" />
        <Skeleton height={56} radius="md" />
      </Stack>
    );
  }
  if (rapporti.isError) {
    return (
      <ErroreConRiprova
        titolo={testi.rapporti.errore}
        errore={rapporti.error}
        onRiprova={() => void rapporti.refetch()}
      />
    );
  }

  const elenco = rapporti.data;
  if (elenco.length === 0) {
    return <Text c="dimmed">{testi.rapporti.nessuno}</Text>;
  }
  const esiste = (id: number | null | undefined) => id != null && elenco.some((r) => r.id === id);
  const aperto = scelto === null ? null : esiste(scelto) ? (scelto ?? null) : rapportoPredefinito(elenco);

  return (
    <Stack gap="sm" component="section" aria-label={testi.rapporti.titolo}>
      {elenco.map((rapporto) => (
        <RapportoVoce
          key={rapporto.id}
          rapporto={rapporto}
          aperto={rapporto.id === aperto}
          onToggle={() => setScelto(rapporto.id === aperto ? null : rapporto.id)}
        />
      ))}
    </Stack>
  );
}

interface VoceProps {
  rapporto: RapportoSintesi;
  aperto: boolean;
  onToggle: () => void;
}

function RapportoVoce({ rapporto, aperto, onToggle }: VoceProps) {
  const periodo = formatPeriodo(rapporto.periodo);
  const idDettaglio = `rapporto-${rapporto.id}-dettaglio`;

  return (
    <Paper
      withBorder
      className={classes.voce}
      data-aperto={aperto || undefined}
      shadow={aperto ? 'sm' : undefined}
    >
      <UnstyledButton
        className={classes.intestazione}
        onClick={onToggle}
        aria-expanded={aperto}
        aria-controls={idDettaglio}
        aria-label={`${aperto ? testi.rapporti.chiudi : testi.rapporti.apri} ${rapporto.etichetta}${periodo ? ` (${periodo})` : ''}${rapporto.haSegnalazioni ? `. ${testi.rapporti.conSegnalazioni}` : ''}`}
      >
        <IconChevronRight className={classes.freccia} size={20} stroke={2.4} aria-hidden />
        <Text component="span" className={classes.etichetta}>
          <Text component="span" fw={650} inherit>
            {rapporto.etichetta}
          </Text>
          {periodo && (
            <Text component="span" c="dimmed" inherit className={classes.periodo}>
              {' '}
              ({periodo})
            </Text>
          )}
        </Text>
        {rapporto.haSegnalazioni && (
          <Tooltip label={testi.rapporti.conSegnalazioni}>
            <span className={classes.avviso}>
              <TriangoloAvviso dimensione={22} />
            </span>
          </Tooltip>
        )}
      </UnstyledButton>
      {/* il dettaglio si smonta a fine chiusura: i dati restano nella cache delle query */}
      <Collapse expanded={aperto} keepMounted={false} transitionDuration={220} id={idDettaglio}>
        <div className={classes.corpo}>
          <RapportoDettaglioView rapportoId={rapporto.id} />
        </div>
      </Collapse>
    </Paper>
  );
}
