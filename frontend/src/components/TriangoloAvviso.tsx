import { IconAlertTriangleFilled } from '@tabler/icons-react';

interface Props {
  /** Testo per le tecnologie assistive; se assente l'icona e' decorativa. */
  etichetta?: string;
  dimensione?: number;
}

/** Triangolo arancione con punto esclamativo, usato per segnalazioni e mensilità mancanti. */
export function TriangoloAvviso({ etichetta, dimensione = 20 }: Props) {
  return (
    <IconAlertTriangleFilled
      size={dimensione}
      color="var(--mantine-color-orange-6)"
      style={{ flexShrink: 0 }}
      role={etichetta ? 'img' : undefined}
      aria-label={etichetta}
      aria-hidden={etichetta ? undefined : true}
    />
  );
}
