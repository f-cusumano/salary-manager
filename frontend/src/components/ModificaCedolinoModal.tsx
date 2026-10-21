import { Group, Modal, Stack, Text } from '@mantine/core';
import { useMediaQuery } from '@mantine/hooks';
import { modals } from '@mantine/modals';
import { IconPencil } from '@tabler/icons-react';
import { useCallback, useRef, useState } from 'react';
import { useAziende, useCedolino, useDatiCedolino, usePersone } from '../api/queries';
import type { CedolinoModificato } from '../api/types';
import { valoriDaCedolino, type ValoriCedolinoManuale } from '../hooks/useCedolinoManuale';
import { testi } from '../i18n';
import { AnteprimaPdf } from './AnteprimaPdf';
import { ErroreConRiprova } from './ErroreConRiprova';
import { Disposizione, Preparazione, SCHERMO_STRETTO } from './InserimentoManualeModal';
import classes from './InserimentoManualeModal.module.css';
import { ModuloCedolinoManuale } from './ModuloCedolinoManuale';

const t = testi.modifica;

/** Nessun campo e' copiato da un altro cedolino: i valori sono quelli del cedolino stesso. */
const NESSUN_COPIATO: Readonly<Record<string, string>> = {};

interface Props {
  /** Cedolino da modificare; null = modale chiusa. */
  cedolinoId: number | null;
  onChiudi: () => void;
  /** Dati salvati: il cedolino che ha sostituito quello modificato. */
  onModificato: (modificato: CedolinoModificato) => void;
}

/**
 * Modifica dei dati di un cedolino archiviato, a schermo intero come l'inserimento a mano: il PDF del cedolino accanto
 * al modulo, precompilato con i dati con cui il cedolino e' stato archiviato.
 */
export function ModificaCedolinoModal({ cedolinoId, onChiudi, onModificato }: Props) {
  if (cedolinoId === null) {
    return null;
  }
  return (
    <FinestraModifica
      key={cedolinoId}
      cedolinoId={cedolinoId}
      onChiudi={onChiudi}
      onModificato={onModificato}
    />
  );
}

function FinestraModifica({ cedolinoId, onChiudi, onModificato }: Props & { cedolinoId: number }) {
  const cedolino = useCedolino(cedolinoId, true);
  const stretto = useMediaQuery(SCHERMO_STRETTO, false, { getInitialValueInEffect: false });

  const controlloModifiche = useRef<() => boolean>(() => false);
  const registraModifiche = useCallback((modificato: () => boolean) => {
    controlloModifiche.current = modificato;
  }, []);

  const chiudi = () => {
    if (!controlloModifiche.current()) {
      onChiudi();
      return;
    }
    modals.openConfirmModal({
      title: testi.manuale.chiudiTitolo,
      children: <Text size="sm">{testi.manuale.chiudiTesto}</Text>,
      labels: { confirm: testi.manuale.chiudiConferma, cancel: testi.manuale.continua },
      confirmProps: { color: 'red' },
      onConfirm: onChiudi,
    });
  };

  // il titolo resta quello del cedolino aperto anche dopo il salvataggio, quando il cedolino non esiste piu'
  const [titolo, setTitolo] = useState<string | null>(null);
  if (cedolino.data && titolo === null) {
    setTitolo(cedolino.data.titolo);
  }

  return (
    <Modal.Root opened onClose={chiudi} fullScreen>
      <Modal.Overlay />
      <Modal.Content className={classes.contenuto}>
        <Modal.Header className={classes.intestazione}>
          <Group gap="sm" wrap="wrap" style={{ minWidth: 0 }}>
            <IconPencil size={20} aria-hidden color="var(--mantine-color-marchio-7)" />
            <Modal.Title fw={700} fz="lg">
              {titolo ? `${t.titolo} · ${titolo}` : t.titolo}
            </Modal.Title>
          </Group>
          <Modal.CloseButton aria-label={testi.comune.chiudiFinestra} />
        </Modal.Header>
        <Modal.Body className={classes.corpo}>
          <Disposizione
            stretto={stretto}
            anteprima={
              <AnteprimaPdf
                id={cedolinoId}
                sorgente="cedolino"
                nomeFile={cedolino.data?.nomeFile ?? `${String(cedolinoId)}.pdf`}
              />
            }
            modulo={
              <PreparazioneModifica
                cedolinoId={cedolinoId}
                registraModifiche={registraModifiche}
                onChiudi={onChiudi}
                onModificato={onModificato}
              />
            }
          />
        </Modal.Body>
      </Modal.Content>
    </Modal.Root>
  );
}

interface PropsPreparazione {
  cedolinoId: number;
  registraModifiche: (modificato: () => boolean) => void;
  onChiudi: () => void;
  onModificato: (modificato: CedolinoModificato) => void;
}

/**
 * Attende i dati del cedolino e gli elenchi di persone e aziende (le tendine mostrano quelle del suo rapporto): i
 * valori iniziali si fissano una volta sola, perche' dopo il salvataggio il cedolino non esiste piu'.
 */
function PreparazioneModifica({ cedolinoId, registraModifiche, onChiudi, onModificato }: PropsPreparazione) {
  const dati = useDatiCedolino(cedolinoId);
  const persone = usePersone();
  const aziende = useAziende();
  const [iniziali, setIniziali] = useState<ValoriCedolinoManuale | null>(null);
  if (iniziali === null && dati.data && !persone.isPending && !aziende.isPending) {
    setIniziali(valoriDaCedolino(dati.data));
  }
  if (iniziali === null) {
    if (dati.isError) {
      return (
        <Stack p="md">
          <ErroreConRiprova
            titolo={t.erroreLettura}
            errore={dati.error}
            onRiprova={() => void dati.refetch()}
          />
        </Stack>
      );
    }
    return <Preparazione />;
  }
  return (
    <ModuloCedolinoManuale
      destinazione={{ tipo: 'modifica', cedolinoId, onModificato }}
      iniziali={iniziali}
      copiatiIniziali={NESSUN_COPIATO}
      registraModifiche={registraModifiche}
      onChiudi={onChiudi}
    />
  );
}
