import { Button, Group, Text, Textarea } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconDeviceFloppy } from '@tabler/icons-react';
import { useState } from 'react';
import { messaggioErrore } from '../api/errors';
import { useAggiornaNote } from '../api/queries';
import { testi } from '../i18n';

const t = testi.riepilogo;
const LUNGHEZZA_MASSIMA = 4000;

interface Props {
  rapportoId: number;
  note: string | null;
}

/** Note libere sul rapporto, salvate sul backend. */
export function NoteCampo({ rapportoId, note }: Props) {
  const [testo, setTesto] = useState(note ?? '');
  const [salvato, setSalvato] = useState(note ?? '');
  const aggiorna = useAggiornaNote(rapportoId);
  // se le note cambiano sul server (es. dopo un ricalcolo) e l'utente non sta scrivendo, si riallinea
  if ((note ?? '') !== salvato && testo === salvato) {
    setSalvato(note ?? '');
    setTesto(note ?? '');
  }
  const modificato = testo !== salvato;

  const salva = () => {
    const daSalvare = testo.trim().length > 0 ? testo : null;
    aggiorna.mutate(daSalvare, {
      onSuccess: (risposta) => {
        setSalvato(risposta.note ?? '');
        setTesto(risposta.note ?? '');
        notifications.show({ color: 'teal', message: t.noteSalvate });
      },
      onError: (errore) =>
        notifications.show({ color: 'red', title: t.noteErrore, message: messaggioErrore(errore) }),
    });
  };

  return (
    <form
      onSubmit={(evento) => {
        evento.preventDefault();
        if (modificato) {
          salva();
        }
      }}
    >
      <Textarea
        aria-label={t.note}
        placeholder={t.notePlaceholder}
        value={testo}
        onChange={(evento) => setTesto(evento.currentTarget.value)}
        onKeyDown={(evento) => {
          if (evento.key === 'Enter' && (evento.ctrlKey || evento.metaKey) && modificato) {
            evento.preventDefault();
            salva();
          }
        }}
        autosize
        minRows={3}
        maxRows={10}
        maxLength={LUNGHEZZA_MASSIMA}
      />
      <Group justify="space-between" mt="xs">
        <Text size="xs" c="dimmed">
          {testo.length}/{LUNGHEZZA_MASSIMA} {t.noteCaratteri}
        </Text>
        <Button
          type="submit"
          size="xs"
          disabled={!modificato}
          loading={aggiorna.isPending}
          leftSection={<IconDeviceFloppy size={14} aria-hidden />}
        >
          {t.noteSalva}
        </Button>
      </Group>
    </form>
  );
}
