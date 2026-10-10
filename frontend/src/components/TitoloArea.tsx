import { Group, Title } from '@mantine/core';
import type { ReactNode } from 'react';

interface Props {
  id: string;
  icona: ReactNode;
  children: ReactNode;
  destra?: ReactNode;
}

/** Intestazione di una delle tre aree del dettaglio azienda. */
export function TitoloArea({ id, icona, children, destra }: Props) {
  return (
    <Group justify="space-between" mb="sm" wrap="nowrap">
      <Group gap="xs" wrap="nowrap">
        {icona}
        <Title order={3} size="h4" id={id}>
          {children}
        </Title>
      </Group>
      {destra}
    </Group>
  );
}
