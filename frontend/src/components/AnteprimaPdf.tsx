import { Alert, Button, Group, Skeleton, Stack } from '@mantine/core';
import { IconDownload, IconExternalLink, IconLock } from '@tabler/icons-react';
import type { MotivoErrore } from '../api/types';
import { useUrlPdf, type SorgentePdf } from '../hooks/useUrlPdf';
import { testi } from '../i18n';
import classes from './InserimentoManualeModal.module.css';
import { ErroreConRiprova } from './ErroreConRiprova';

const t = testi.manuale;

interface Props {
  /** Id del file in errore (o del cedolino, con sorgente 'cedolino'). */
  id: number;
  sorgente?: SorgentePdf;
  /** Nome con cui il file era stato caricato (riserva se il backend non lo indica). */
  nomeFile: string;
  motivo?: MotivoErrore | null;
}

/**
 * Anteprima del PDF conservato accanto al modulo: URL `blob:` in un iframe (il visualizzatore del browser), con
 * l'apertura in una nuova scheda per i telefoni che non mostrano i PDF negli iframe e lo scaricamento.
 */
export function AnteprimaPdf({ id, sorgente = 'caricamento', nomeFile, motivo }: Props) {
  const pdf = useUrlPdf(id, sorgente);

  if (pdf.errore) {
    return <ErroreConRiprova titolo={t.pdfErrore} errore={pdf.errore} onRiprova={pdf.riprova} />;
  }

  return (
    <Stack gap="xs" className={classes.anteprima}>
      <Group gap="xs" justify="flex-end" wrap="wrap">
        <Button
          component="a"
          href={pdf.url ?? undefined}
          target="_blank"
          rel="noopener"
          variant="default"
          size="xs"
          leftSection={<IconExternalLink size={14} aria-hidden />}
          disabled={pdf.url === null}
        >
          {t.apriScheda}
        </Button>
        <Button
          component="a"
          href={pdf.url ?? undefined}
          download={pdf.nome ?? nomeFile}
          variant="default"
          size="xs"
          leftSection={<IconDownload size={14} aria-hidden />}
          disabled={pdf.url === null}
        >
          {t.scarica}
        </Button>
      </Group>
      {motivo === 'PDF_PROTETTO' && (
        <Alert color="marchio" variant="light" p="xs" icon={<IconLock size={16} aria-hidden />}>
          {t.pdfProtetto}
        </Alert>
      )}
      {pdf.url === null ? (
        <Skeleton className={classes.cornice} aria-busy="true" aria-label={t.pdfCaricamento} />
      ) : (
        <iframe src={pdf.url} title={t.pdf} className={classes.cornice} />
      )}
    </Stack>
  );
}
