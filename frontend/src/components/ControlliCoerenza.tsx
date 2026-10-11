import { Alert, Anchor, List, Text } from '@mantine/core';
import { IconAlertTriangle } from '@tabler/icons-react';
import { useMemo } from 'react';
import { controlliCoerenza, type AvvisoCoerenza } from '../hooks/useCedolinoManuale';
import { testi } from '../i18n';
import { useValoreModulo } from './moduloCedolino';

const t = testi.manuale;

interface Props {
  onVaiAlCampo: (percorso: string) => void;
}

/**
 * Controlli di coerenza calcolati durante la compilazione (netto, somme delle voci, saldi dei ratei, codici di
 * controllo): avvisi arancioni che non bloccano il salvataggio.
 */
export function ControlliCoerenza({ onVaiAlCampo }: Props) {
  // stringa: il componente si ridisegna solo quando cambiano gli avvisi
  const serializzati = useValoreModulo((v) => JSON.stringify(controlliCoerenza(v)));
  const avvisi = useMemo(() => JSON.parse(serializzati) as AvvisoCoerenza[], [serializzati]);
  if (avvisi.length === 0) {
    return null;
  }
  return (
    <Alert
      color="orange"
      variant="light"
      title={t.controlli}
      icon={<IconAlertTriangle aria-hidden />}
      aria-label={t.controlli}
      role="region"
    >
      <Text size="xs" c="dimmed" mb={4}>
        {t.controlliNota}
      </Text>
      <List size="sm" spacing={2}>
        {avvisi.map(({ campo, messaggio }) => (
          <List.Item key={`${campo}-${messaggio}`}>
            <Anchor
              component="button"
              type="button"
              size="sm"
              c="orange.9"
              onClick={() => onVaiAlCampo(campo)}
            >
              {messaggio}
            </Anchor>
          </List.Item>
        ))}
      </List>
    </Alert>
  );
}
