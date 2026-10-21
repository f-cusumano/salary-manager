import { Text } from '@mantine/core';
import type { ReactNode } from 'react';
import { AiutoCampo } from './AiutoCampo';
import classes from './ElencoValori.module.css';

export interface RigaValore {
  etichetta: string;
  valore: ReactNode;
  /** Riga evidenziata (es. totale). */
  enfasi?: boolean;
  /** Spiegazione del dato (icona "i" accanto all'etichetta). */
  aiuto?: string | null;
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
            {riga.aiuto && <AiutoCampo campo={riga.etichetta} testo={riga.aiuto} />}
          </Text>
          <Text component="dd" size="sm" fw={riga.enfasi ? 700 : 500} className={classes.valore}>
            {riga.valore}
          </Text>
        </div>
      ))}
    </dl>
  );
}
