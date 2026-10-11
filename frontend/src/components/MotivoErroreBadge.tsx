import { Badge, type BadgeProps } from '@mantine/core';
import type { MotivoErrore } from '../api/types';
import { testi } from '../i18n';

interface Props {
  /** null per gli errori registrati prima dei motivi: nessun badge. */
  motivo: MotivoErrore | null;
  size?: BadgeProps['size'];
}

/** Motivo per cui un cedolino caricato non è stato letto. */
export function MotivoErroreBadge({ motivo, size = 'sm' }: Props) {
  if (motivo === null) {
    return null;
  }
  return (
    <Badge size={size} color="red" variant="light" maw="100%" style={{ textTransform: 'none' }}>
      {testi.motivoErrore[motivo]}
    </Badge>
  );
}
