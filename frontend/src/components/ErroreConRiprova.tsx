import { Alert, Button } from '@mantine/core';
import { IconAlertCircle, IconRefresh } from '@tabler/icons-react';
import { messaggioErrore } from '../api/errors';
import { testi } from '../i18n';

interface Props {
  titolo: string;
  errore: unknown;
  onRiprova?: () => void;
}

/** Errore di caricamento dati, con il motivo e il pulsante per riprovare. */
export function ErroreConRiprova({ titolo, errore, onRiprova }: Props) {
  return (
    <Alert color="red" variant="light" title={titolo} icon={<IconAlertCircle aria-hidden />} role="alert">
      {messaggioErrore(errore)}
      {onRiprova && (
        <div>
          <Button
            mt="sm"
            size="xs"
            variant="white"
            color="red"
            leftSection={<IconRefresh size={14} aria-hidden />}
            onClick={onRiprova}
          >
            {testi.comune.riprova}
          </Button>
        </div>
      )}
    </Alert>
  );
}
