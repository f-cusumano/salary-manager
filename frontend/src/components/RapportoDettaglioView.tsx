import { Skeleton, Stack } from '@mantine/core';
import { useRapporto } from '../api/queries';
import { testi } from '../i18n';
import { ErroreConRiprova } from './ErroreConRiprova';
import { MensilitaArea } from './MensilitaArea';
import { RiepilogoArea } from './RiepilogoArea';
import { SegnalazioniArea } from './SegnalazioniArea';

interface Props {
  rapportoId: number;
}

/** Dettaglio di un'azienda nelle tre aree: segnalazioni (se presenti), riepilogo, mensilità. */
export function RapportoDettaglioView({ rapportoId }: Props) {
  const rapporto = useRapporto(rapportoId);

  if (rapporto.isPending) {
    return (
      <Stack gap="md" aria-busy="true" aria-label={testi.dettaglio.caricamento}>
        <Skeleton height={180} radius="md" />
        <Skeleton height={48} radius="md" />
        <Skeleton height={48} radius="md" />
        <Skeleton height={48} radius="md" />
      </Stack>
    );
  }
  if (rapporto.isError) {
    return (
      <ErroreConRiprova
        titolo={testi.dettaglio.errore}
        errore={rapporto.error}
        onRiprova={() => void rapporto.refetch()}
      />
    );
  }

  const dettaglio = rapporto.data;
  return (
    <Stack gap="lg">
      {dettaglio.segnalazioni.length > 0 && <SegnalazioniArea segnalazioni={dettaglio.segnalazioni} />}
      <RiepilogoArea rapporto={dettaglio} />
      <MensilitaArea rapportoId={dettaglio.id} mensilita={dettaglio.mensilita} />
    </Stack>
  );
}
