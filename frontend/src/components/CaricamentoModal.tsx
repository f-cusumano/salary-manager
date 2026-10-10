import {
  ActionIcon,
  Alert,
  Badge,
  Button,
  Group,
  Loader,
  Modal,
  Paper,
  PasswordInput,
  Progress,
  ScrollArea,
  Stack,
  Text,
  ThemeIcon,
} from '@mantine/core';
import { Dropzone, MIME_TYPES, type FileRejection } from '@mantine/dropzone';
import {
  IconAlertCircle,
  IconCircleCheck,
  IconCopy,
  IconFileTypePdf,
  IconHourglassHigh,
  IconUpload,
  IconX,
  IconLock,
  IconFilePlus,
} from '@tabler/icons-react';
import { useState, type ReactNode } from 'react';
import { messaggioErrore } from '../api/errors';
import type { Caricamento, StatoCaricamento } from '../api/types';
import { STATI_TERMINALI } from '../api/types';
import { formatDimensione } from '../format/format';
import { caricamentiInEvidenza, type StatoCaricamentoLotto } from '../hooks/useCaricamento';
import { testi } from '../i18n';
import classes from './CaricamentoModal.module.css';

const t = testi.caricamento;
const DIMENSIONE_MASSIMA = 25 * 1024 * 1024;

interface Props {
  aperta: boolean;
  onChiudi: () => void;
  caricamento: StatoCaricamentoLotto;
}

/** Modale di caricamento multiplo dei PDF con lo stato di ogni file aggiornato in polling. */
export function CaricamentoModal({ aperta, onChiudi, caricamento }: Props) {
  return (
    <Modal
      opened={aperta}
      onClose={onChiudi}
      title={
        <Group gap="xs">
          <IconUpload size={20} aria-hidden color="var(--mantine-color-marchio-7)" />
          <Text fw={700} size="lg">
            {t.titolo}
          </Text>
        </Group>
      }
      size="lg"
      centered
      closeButtonProps={{ 'aria-label': testi.comune.chiudiFinestra }}
    >
      {caricamento.lotto ? (
        <StatoLotto caricamento={caricamento} onChiudi={onChiudi} />
      ) : (
        <SelezioneFile caricamento={caricamento} onChiudi={onChiudi} />
      )}
    </Modal>
  );
}

// ------------------------------------------------------------------ selezione dei file

function chiaveFile(file: File): string {
  return `${file.name}|${file.size}|${file.lastModified}`;
}

function SelezioneFile({
  caricamento,
  onChiudi,
}: {
  caricamento: StatoCaricamentoLotto;
  onChiudi: () => void;
}) {
  const [files, setFiles] = useState<File[]>([]);
  const [scartati, setScartati] = useState<FileRejection[]>([]);
  const [password, setPassword] = useState('');

  const aggiungi = (nuovi: File[]) =>
    setFiles((attuali) => {
      const presenti = new Set(attuali.map(chiaveFile));
      return [...attuali, ...nuovi.filter((f) => !presenti.has(chiaveFile(f)))];
    });

  const motivoScarto = (scarto: FileRejection) =>
    scarto.errors.some((e) => e.code === 'file-too-large') ? t.scartoDimensione : t.scartoTipo;

  return (
    <Stack gap="md">
      <Dropzone
        onDrop={(accettati) => {
          aggiungi(accettati);
          setScartati([]);
        }}
        onReject={setScartati}
        accept={[MIME_TYPES.pdf]}
        maxSize={DIMENSIONE_MASSIMA}
        multiple
        disabled={caricamento.inviando}
        className={classes.dropzone}
        aria-label={t.dropzoneAria}
      >
        <Group justify="center" gap="lg" mih={120} wrap="nowrap" style={{ pointerEvents: 'none' }}>
          <Dropzone.Accept>
            <IconUpload size={48} color="var(--mantine-color-marchio-6)" aria-hidden />
          </Dropzone.Accept>
          <Dropzone.Reject>
            <IconX size={48} color="var(--mantine-color-red-6)" aria-hidden />
          </Dropzone.Reject>
          <Dropzone.Idle>
            <IconFileTypePdf size={48} color="var(--mantine-color-marchio-5)" stroke={1.4} aria-hidden />
          </Dropzone.Idle>
          <div>
            <Text size="md" fw={600}>
              {t.dropzoneTitolo}
            </Text>
            <Text size="sm" c="dimmed" mt={4}>
              {t.dropzoneDescrizione}
            </Text>
          </div>
        </Group>
      </Dropzone>

      {scartati.length > 0 && (
        <Alert color="red" variant="light" title={t.scartati} icon={<IconAlertCircle aria-hidden />} p="sm">
          <Stack gap={2}>
            {scartati.map((s) => (
              <Text key={chiaveFile(s.file)} size="sm">
                {s.file.name}: {motivoScarto(s)}
              </Text>
            ))}
          </Stack>
        </Alert>
      )}

      {files.length > 0 && (
        <div>
          <Text size="sm" fw={600} mb={6}>
            {t.fileSelezionati} ({files.length})
          </Text>
          <ScrollArea.Autosize mah={220} type="auto" offsetScrollbars>
            <Stack gap={6} component="ul" className={classes.elenco}>
              {files.map((file) => (
                <Paper
                  component="li"
                  key={chiaveFile(file)}
                  withBorder
                  px="sm"
                  py={6}
                  className={classes.file}
                >
                  <IconFileTypePdf size={20} color="var(--mantine-color-red-7)" aria-hidden />
                  <Text size="sm" className={classes.nomeFile} title={file.name}>
                    {file.name}
                  </Text>
                  <Text size="xs" c="dimmed">
                    {formatDimensione(file.size)}
                  </Text>
                  <ActionIcon
                    variant="subtle"
                    color="gray"
                    onClick={() => setFiles((attuali) => attuali.filter((f) => f !== file))}
                    aria-label={`${t.rimuovi} ${file.name}`}
                    disabled={caricamento.inviando}
                  >
                    <IconX size={16} aria-hidden />
                  </ActionIcon>
                </Paper>
              ))}
            </Stack>
          </ScrollArea.Autosize>
        </div>
      )}

      <PasswordInput
        label={t.password}
        description={t.passwordDescrizione}
        value={password}
        onChange={(evento) => setPassword(evento.currentTarget.value)}
        leftSection={<IconLock size={16} aria-hidden />}
        visibilityToggleButtonProps={{ 'aria-label': t.mostraPassword }}
        autoComplete="off"
        disabled={caricamento.inviando}
      />

      {caricamento.erroreInvio !== null && (
        <Alert
          color="red"
          variant="light"
          title={t.erroreInvio}
          icon={<IconAlertCircle aria-hidden />}
          role="alert"
        >
          {messaggioErrore(caricamento.erroreInvio)}
        </Alert>
      )}

      <Group justify="flex-end" gap="sm">
        <Button variant="default" onClick={onChiudi}>
          {testi.comune.annulla}
        </Button>
        <Button
          leftSection={<IconUpload size={16} aria-hidden />}
          disabled={files.length === 0}
          loading={caricamento.inviando}
          onClick={() => caricamento.avvia({ files, password: password.length > 0 ? password : null })}
        >
          {t.avvia}
          {files.length > 0 ? ` (${files.length})` : ''}
        </Button>
      </Group>
    </Stack>
  );
}

// ------------------------------------------------------------------ stato del lotto

const COLORE_STATO: Record<StatoCaricamento, string> = {
  IN_CODA: 'gray',
  IN_LETTURA: 'marchio',
  ACQUISITO: 'teal',
  DUPLICATO: 'yellow',
  AGGIUNTIVO_NON_GESTITO: 'orange',
  ERRORE: 'red',
};

function IconaStato({ stato }: { stato: StatoCaricamento }): ReactNode {
  const colore = COLORE_STATO[stato];
  switch (stato) {
    case 'IN_CODA':
      return (
        <ThemeIcon variant="light" color={colore} radius="xl" size={30}>
          <IconHourglassHigh size={16} aria-hidden />
        </ThemeIcon>
      );
    case 'IN_LETTURA':
      return (
        <ThemeIcon variant="light" color={colore} radius="xl" size={30}>
          <Loader size={14} color={colore} />
        </ThemeIcon>
      );
    case 'ACQUISITO':
      return (
        <ThemeIcon variant="light" color={colore} radius="xl" size={30}>
          <IconCircleCheck size={18} aria-hidden />
        </ThemeIcon>
      );
    case 'DUPLICATO':
      return (
        <ThemeIcon variant="light" color={colore} radius="xl" size={30}>
          <IconCopy size={16} aria-hidden />
        </ThemeIcon>
      );
    case 'AGGIUNTIVO_NON_GESTITO':
      return (
        <ThemeIcon variant="light" color={colore} radius="xl" size={30}>
          <IconFilePlus size={16} aria-hidden />
        </ThemeIcon>
      );
    case 'ERRORE':
      return (
        <ThemeIcon variant="light" color={colore} radius="xl" size={30}>
          <IconAlertCircle size={18} aria-hidden />
        </ThemeIcon>
      );
  }
}

function RigaCaricamento({ caricamento: c }: { caricamento: Caricamento }) {
  const colore = COLORE_STATO[c.stato];
  return (
    <Paper component="li" withBorder px="sm" py={8} className={classes.riga} data-stato={c.stato}>
      <IconaStato stato={c.stato} />
      <div className={classes.testoRiga}>
        <Group gap="xs" justify="space-between" wrap="nowrap">
          <Text size="sm" fw={600} className={classes.nomeFile} title={c.nomeFileOriginale}>
            {c.nomeFileOriginale}
          </Text>
          <Badge color={colore} variant="light" style={{ textTransform: 'none', flexShrink: 0 }}>
            {t.stato[c.stato]}
          </Badge>
        </Group>
        <Text size="xs" c={c.messaggio && c.stato !== 'ACQUISITO' ? `${colore}.8` : 'dimmed'} mt={2}>
          {c.messaggio ?? t.statoDescrizione[c.stato]} · {formatDimensione(c.dimensione)}
        </Text>
      </div>
    </Paper>
  );
}

function StatoLotto({ caricamento, onChiudi }: { caricamento: StatoCaricamentoLotto; onChiudi: () => void }) {
  const lotto = caricamento.lotto;
  if (!lotto) {
    return null;
  }
  const totale = lotto.caricamenti.length;
  const conclusi = lotto.caricamenti.filter((c) => STATI_TERMINALI.includes(c.stato)).length;
  const acquisiti = lotto.caricamenti.filter((c) => c.stato === 'ACQUISITO').length;
  const percentuale = totale > 0 ? Math.round((conclusi / totale) * 100) : 0;

  return (
    <Stack gap="md">
      <div>
        <Group justify="space-between" mb={6}>
          <Text size="sm" fw={600} aria-live="polite">
            {lotto.completato ? t.completato : `${conclusi} ${t.di} ${totale}`}
          </Text>
          <Text size="sm" c="dimmed">
            {acquisiti} {t.di} {totale} {t.riepilogo}
          </Text>
        </Group>
        <Progress
          value={percentuale}
          color={lotto.completato ? 'teal' : 'marchio'}
          animated={!lotto.completato}
          striped={!lotto.completato}
          aria-label={`${conclusi} ${t.di} ${totale}`}
        />
      </div>

      {!lotto.completato && (
        <Text size="sm" c="dimmed">
          {t.elaborazione}
        </Text>
      )}
      {caricamento.errorePolling !== null && (
        <Alert color="red" variant="light" title={t.erroreStato} icon={<IconAlertCircle aria-hidden />}>
          {messaggioErrore(caricamento.errorePolling)}
        </Alert>
      )}

      <ScrollArea.Autosize mah={360} type="auto" offsetScrollbars>
        <Stack gap={6} component="ul" className={classes.elenco} aria-label={t.titolo}>
          {caricamentiInEvidenza(lotto).map((c) => (
            <RigaCaricamento key={c.id} caricamento={c} />
          ))}
        </Stack>
      </ScrollArea.Autosize>

      <Group justify="flex-end" gap="sm">
        {lotto.completato && (
          <Button
            variant="default"
            leftSection={<IconUpload size={16} aria-hidden />}
            onClick={caricamento.azzera}
          >
            {t.caricaAltri}
          </Button>
        )}
        <Button onClick={onChiudi}>{testi.comune.chiudi}</Button>
      </Group>
    </Stack>
  );
}
