import { Badge, Group, Tooltip } from '@mantine/core';
import type { ReactNode } from 'react';
import { testi } from '../i18n';

interface Props {
  children: ReactNode;
  /** Spiegazione della stima mostrata nel tooltip (raggiungibile anche da tastiera). */
  spiegazione: string;
}

/** Valore stimato: affianca al valore un badge "stima" con la spiegazione nel tooltip. */
export function Stima({ children, spiegazione }: Props) {
  return (
    <Group gap={6} wrap="nowrap" component="span" display="inline-flex">
      {children}
      <Tooltip label={spiegazione}>
        <Badge
          component="span"
          variant="light"
          color="gray"
          size="xs"
          radius="sm"
          tabIndex={0}
          aria-label={`${testi.comune.stima}: ${spiegazione}`}
          style={{ cursor: 'help', textTransform: 'none' }}
        >
          {testi.comune.stima}
        </Badge>
      </Tooltip>
    </Group>
  );
}
