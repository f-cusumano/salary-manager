import { ActionIcon, Popover, Text } from '@mantine/core';
import { IconInfoCircle } from '@tabler/icons-react';
import type { MouseEvent } from 'react';
import { testi } from '../i18n';

interface Props {
  /** Nome del campo, per il nome accessibile del pulsante ("Spiegazione: totale competenze"). */
  campo: string;
  testo: string;
}

/**
 * Icona "i" accanto all'etichetta di un campo: al clic (o tocco, o Invio) apre la spiegazione del campo; Esc la chiude.
 * Dentro un'etichetta il clic non sposta il focus sul campo (un pulsante non attiva la sua `<label>`) e non cambia una
 * casella di spunta.
 */
export function AiutoCampo({ campo, testo }: Props) {
  const ferma = (evento: MouseEvent) => {
    // l'etichetta di checkbox e switch non deve cambiarne il valore
    evento.preventDefault();
    evento.stopPropagation();
  };
  return (
    <Popover width={320} position="top-start" withArrow shadow="md" returnFocus>
      <Popover.Target>
        <ActionIcon
          variant="subtle"
          color="gray"
          size="xs"
          radius="xl"
          aria-label={`${testi.aiuti.apri} ${campo}`}
          onClick={ferma}
          onMouseDown={(evento) => evento.stopPropagation()}
          style={{ verticalAlign: 'text-bottom', marginInlineStart: 4 }}
        >
          <IconInfoCircle size={14} aria-hidden />
        </ActionIcon>
      </Popover.Target>
      <Popover.Dropdown>
        <Text size="sm">{testo}</Text>
      </Popover.Dropdown>
    </Popover>
  );
}
