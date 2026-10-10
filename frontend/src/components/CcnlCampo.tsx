import { Alert, Badge, Button, Group, Select, Stack, Text, Tooltip } from '@mantine/core';
import { modals } from '@mantine/modals';
import { notifications } from '@mantine/notifications';
import { IconInfoCircle, IconPencil } from '@tabler/icons-react';
import { useState } from 'react';
import { messaggioErrore } from '../api/errors';
import { useElencoCcnl, useImpostaCcnl } from '../api/queries';
import type { RapportoDettaglio } from '../api/types';
import { testi } from '../i18n';
import { TriangoloAvviso } from './TriangoloAvviso';

const t = testi.riepilogo;

interface Props {
  rapporto: RapportoDettaglio;
}

/**
 * CCNL del rapporto. Se non e' stato riconosciuto dai cedolini l'utente lo sceglie (una sola volta per rapporto);
 * resta modificabile e ogni cambio fa ricalcolare riepilogo e segnalazioni (con conferma).
 */
export function CcnlCampo({ rapporto }: Props) {
  const [inModifica, setInModifica] = useState(false);
  const [selezionato, setSelezionato] = useState<string | null>(null);
  const daIndicare = rapporto.ccnlDaIndicare || rapporto.ccnl === null;
  const elenco = useElencoCcnl(daIndicare || inModifica);
  const imposta = useImpostaCcnl(rapporto.id);

  const opzioni = (elenco.data ?? []).map((c) => ({ value: c.codice, label: c.descrizione }));

  const salva = (codice: string) => {
    imposta.mutate(codice, {
      onSuccess: () => {
        setInModifica(false);
        setSelezionato(null);
        notifications.show({ color: 'teal', message: t.ccnlSalvato });
      },
      onError: (errore) =>
        notifications.show({ color: 'red', title: t.ccnlErrore, message: messaggioErrore(errore) }),
    });
  };

  const selettore = (valore: string | null, onChange: (codice: string | null) => void) => (
    <Select
      aria-label={t.ccnlSeleziona}
      placeholder={t.ccnlSeleziona}
      data={opzioni}
      value={valore}
      onChange={onChange}
      searchable
      disabled={imposta.isPending}
      rightSection={elenco.isPending ? <Text size="xs">…</Text> : undefined}
      error={elenco.isError ? messaggioErrore(elenco.error) : undefined}
      comboboxProps={{ shadow: 'md', withinPortal: true }}
    />
  );

  if (daIndicare) {
    return (
      <Alert
        color="orange"
        variant="light"
        p="sm"
        icon={<TriangoloAvviso />}
        title={t.ccnlDaIndicare}
        styles={{ title: { color: 'var(--mantine-color-orange-9)' } }}
      >
        <Stack gap="xs">
          <Text size="sm" c="orange.9">
            {t.ccnlDaIndicareDescrizione}
          </Text>
          {selettore(null, (codice) => {
            if (codice) {
              salva(codice);
            }
          })}
        </Stack>
      </Alert>
    );
  }

  const ccnl = rapporto.ccnl;
  if (inModifica && ccnl) {
    const valore = selezionato ?? ccnl.codice;
    return (
      <Stack gap="xs">
        {selettore(valore, setSelezionato)}
        <Group gap="xs" justify="flex-end">
          <Button
            variant="default"
            size="xs"
            onClick={() => {
              setInModifica(false);
              setSelezionato(null);
            }}
          >
            {testi.comune.annulla}
          </Button>
          <Button
            size="xs"
            loading={imposta.isPending}
            disabled={valore === ccnl.codice}
            onClick={() =>
              modals.openConfirmModal({
                title: t.ccnlConfermaTitolo,
                children: <Text size="sm">{t.ccnlConfermaTesto}</Text>,
                labels: { confirm: t.ccnlConferma, cancel: testi.comune.annulla },
                onConfirm: () => salva(valore),
              })
            }
          >
            {t.ccnlConferma}
          </Button>
        </Group>
      </Stack>
    );
  }

  return (
    <Group justify="space-between" align="flex-start" wrap="nowrap" gap="sm">
      <div style={{ flex: 1, minWidth: 0 }}>
        <Text fw={600} size="sm">
          {ccnl?.descrizione}
        </Text>
        <Group gap={6} mt={4}>
          {ccnl && (
            <Badge variant="light" color="marchio" size="sm" style={{ textTransform: 'none' }}>
              {ccnl.mensilita} {t.ccnlMensilita}
            </Badge>
          )}
          {rapporto.ccnlSceltoDaUtente && (
            <Badge variant="outline" color="gray" size="sm" style={{ textTransform: 'none' }}>
              {t.ccnlSceltoDaUtente}
            </Badge>
          )}
          {ccnl?.noteVerifica && (
            <Tooltip label={`${t.ccnlNoteVerifica}: ${ccnl.noteVerifica}`}>
              <IconInfoCircle
                size={16}
                color="var(--mantine-color-gray-6)"
                tabIndex={0}
                role="img"
                aria-label={`${t.ccnlNoteVerifica}: ${ccnl.noteVerifica}`}
              />
            </Tooltip>
          )}
        </Group>
      </div>
      <Button
        variant="subtle"
        size="xs"
        style={{ flexShrink: 0 }}
        leftSection={<IconPencil size={14} aria-hidden />}
        onClick={() => setInModifica(true)}
        aria-label={t.ccnlModificaAria}
      >
        {t.ccnlModifica}
      </Button>
    </Group>
  );
}
