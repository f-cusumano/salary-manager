import { ActionIcon, Indicator, Tooltip } from '@mantine/core';
import { IconFileAlert } from '@tabler/icons-react';
import { useCaricamentiInErrore } from '../api/queries';
import { testi } from '../i18n';
import { ID_AREA_CARICAMENTI_IN_ERRORE } from './CaricamentiInErroreArea';

/** Porta all'area dei cedolini caricati con errore, sotto l'intestazione fissa. */
function vaiAllArea() {
  const area = document.getElementById(ID_AREA_CARICAMENTI_IN_ERRORE);
  if (!area) {
    return;
  }
  const ridotto = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  area.scrollIntoView({ behavior: ridotto ? 'auto' : 'smooth', block: 'start' });
  // il focus segue lo scorrimento: chi usa la tastiera o un lettore di schermo prosegue dall'area
  area.focus({ preventScroll: true });
}

/** Contatore rosso dei cedolini caricati con errore nella barra superiore; nascosto quando non ce ne sono. */
export function IndicatoreCaricamentiInErrore() {
  const inErrore = useCaricamentiInErrore();
  const numero = inErrore.data?.length ?? 0;
  if (numero === 0) {
    return null;
  }
  const etichetta = `${testi.inErrore.titolo} (${numero})`;
  return (
    <Tooltip label={etichetta}>
      <Indicator
        label={<span aria-hidden>{numero}</span>}
        color="red"
        size={18}
        offset={4}
        withBorder
        styles={{ indicator: { fontWeight: 700 } }}
      >
        <ActionIcon variant="light" color="red" size={36} aria-label={etichetta} onClick={vaiAllArea}>
          <IconFileAlert size={20} aria-hidden />
        </ActionIcon>
      </Indicator>
    </Tooltip>
  );
}
