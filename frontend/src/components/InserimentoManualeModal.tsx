import { Button, Group, Modal, Stack, Text } from '@mantine/core';
import { IconPencil } from '@tabler/icons-react';
import { useCaricamentiInErrore } from '../api/queries';
import type { Caricamento } from '../api/types';
import { testi } from '../i18n';
import { MotivoErroreBadge } from './MotivoErroreBadge';

const t = testi.manuale;

interface Props {
  /** File in errore da completare; null = modale chiusa. */
  caricamentoId: number | null;
  onChiudi: () => void;
  /** Cedolino salvato: il caricamento restituito dal backend (ACQUISITO, con persona e rapporto). */
  onAcquisito: (caricamento: Caricamento) => void;
}

/**
 * Inserimento a mano del cedolino di un file caricato con errore, a schermo intero.
 * Versione provvisoria: titolo e motivo del file; anteprima del PDF e modulo arrivano con l'inserimento a mano.
 */
export function InserimentoManualeModal({ caricamentoId, onChiudi }: Props) {
  const inErrore = useCaricamentiInErrore();
  const elemento = inErrore.data?.find((e) => e.id === caricamentoId);
  const titolo = elemento ? `${t.titolo} · ${elemento.nomeFileOriginale}` : t.titolo;

  return (
    <Modal.Root opened={caricamentoId !== null} onClose={onChiudi} fullScreen>
      <Modal.Overlay />
      <Modal.Content>
        <Modal.Header>
          <Group gap="sm" wrap="wrap" style={{ minWidth: 0 }}>
            <IconPencil size={20} aria-hidden color="var(--mantine-color-marchio-7)" />
            <Modal.Title fw={700} fz="lg">
              {titolo}
            </Modal.Title>
            {elemento && <MotivoErroreBadge motivo={elemento.motivo} />}
          </Group>
          <Modal.CloseButton aria-label={testi.comune.chiudiFinestra} />
        </Modal.Header>
        <Modal.Body>
          <Stack gap="md" maw={640}>
            <Text>{t.inPreparazione}</Text>
            <Group>
              <Button variant="default" onClick={onChiudi}>
                {testi.comune.chiudi}
              </Button>
            </Group>
          </Stack>
        </Modal.Body>
      </Modal.Content>
    </Modal.Root>
  );
}
