import { Alert, Anchor, List } from '@mantine/core';
import { IconAlertCircle } from '@tabler/icons-react';
import type { ReactNode } from 'react';
import { testi } from '../i18n';
import { etichettaErrore } from './moduloCedolino';

const t = testi.manuale;

export interface ErroreModulo {
  /** Percorso del campo nel modulo; null per gli errori che non riguardano un campo. */
  percorso: string | null;
  messaggio: ReactNode;
}

interface Props {
  errori: readonly ErroreModulo[];
  /** Porta il focus sul campo (aprendo la sua sezione). */
  onVaiAlCampo: (percorso: string) => void;
}

/** "N campi da correggere": ogni voce porta al campo con l'errore. */
export function RiepilogoErrori({ errori, onVaiAlCampo }: Props) {
  if (errori.length === 0) {
    return null;
  }
  const titolo = `${errori.length} ${errori.length === 1 ? t.campoDaCorreggere : t.campiDaCorreggere}`;
  return (
    <Alert color="red" variant="light" title={titolo} icon={<IconAlertCircle aria-hidden />} role="alert">
      <List size="sm" spacing={2}>
        {errori.map(({ percorso, messaggio }, indice) => (
          <List.Item key={percorso ?? `generale-${String(indice)}`}>
            {percorso !== null ? (
              <Anchor
                component="button"
                type="button"
                size="sm"
                c="red.9"
                onClick={() => onVaiAlCampo(percorso)}
              >
                {etichettaErrore(percorso)}: {messaggio}
              </Anchor>
            ) : (
              messaggio
            )}
          </List.Item>
        ))}
      </List>
    </Alert>
  );
}
