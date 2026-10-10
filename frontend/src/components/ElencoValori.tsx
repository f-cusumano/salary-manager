import { Text } from '@mantine/core';
import type { ReactNode } from 'react';
import classes from './ElencoValori.module.css';

export interface RigaValore {
  etichetta: string;
  valore: ReactNode;
  /** Riga evidenziata (es. totale). */
  enfasi?: boolean;
}

interface Props {
  righe: (RigaValore | null | false | undefined)[];
}

/** Elenco etichetta/valore (lista di descrizioni) che omette le righe assenti. */
export function ElencoValori({ righe }: Props) {
  const visibili = righe.filter((r): r is RigaValore => Boolean(r));
  if (visibili.length === 0) {
    return null;
  }
  return (
    <dl className={classes.elenco}>
      {visibili.map((riga) => (
        <div key={riga.etichetta} className={classes.riga} data-enfasi={riga.enfasi ? true : undefined}>
          <Text component="dt" size="sm" c="dimmed">
            {riga.etichetta}
          </Text>
          <Text component="dd" size="sm" fw={riga.enfasi ? 700 : 500} className={classes.valore}>
            {riga.valore}
          </Text>
        </div>
      ))}
    </dl>
  );
}
