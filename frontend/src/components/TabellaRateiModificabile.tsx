import { ActionIcon, Button, Group, Stack, Text, Tooltip } from '@mantine/core';
import { IconPlus, IconTrash } from '@tabler/icons-react';
import { useEffect, useRef } from 'react';
import type { TipoRateo, UnitaMisura } from '../api/types';
import { formatNumero } from '../format/format';
import {
  rateoCopiatoVuoto,
  rateoVuoto,
  saldoCalcolato,
  TIPI_RATEO,
  UNITA_RATEO,
} from '../hooks/useCedolinoManuale';
import { testi } from '../i18n';
import { CampoImporto, CampoSceltaRiga, PuntoCopiato } from './CampiModulo';
import classes from './InserimentoManualeModal.module.css';
import { campoDelModulo, useModulo, useValoreModulo } from './moduloCedolino';

const t = testi.manuale;
const COLONNE = t.colonneRateo;
const TIPI = TIPI_RATEO.map((tipo) => ({ value: tipo, label: testi.rateo[tipo] }));
const UNITA = UNITA_RATEO.map((unita) => ({ value: unita, label: t.unitaRateo[unita] }));

/** Aggiunte rapide dei ratei piu' comuni, nell'unita' in cui li stampano di solito i cedolini. */
const RATEI_COMUNI: readonly (readonly [TipoRateo, UnitaMisura])[] = [
  ['FERIE', 'GIORNI'],
  ['PERMESSI', 'ORE'],
  ['ROL', 'ORE'],
  ['EX_FESTIVITA', 'ORE'],
];

/**
 * Ratei (ferie, permessi…) come righe modificabili, con le aggiunte rapide dei piu' comuni (disabilitate se gia'
 * presenti) e il saldo calcolato (residuo + maturato − goduto) come suggerimento del saldo. I ratei copiati da un
 * altro cedolino e non completati non vengono salvati: una nota li conta e permette di toglierli.
 */
export function TabellaRateiModificabile() {
  const { form, copiati } = useModulo();
  const ratei = form.getValues().ratei;
  const presenti = useValoreModulo((v) => v.ratei.map((r) => `${r.tipo}/${r.unita}`).join(','));
  const copiatiVuoti = useValoreModulo((v) => v.ratei.filter(rateoCopiatoVuoto).length);
  const daFocalizzare = useRef<string | null>(null);
  const radice = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (daFocalizzare.current !== null) {
      campoDelModulo(radice.current, daFocalizzare.current)?.focus();
      daFocalizzare.current = null;
    }
  });

  const rimuoviCopiatiVuoti = () => {
    const righe = form.getValues().ratei;
    for (let indice = righe.length - 1; indice >= 0; indice -= 1) {
      const riga = righe[indice];
      if (riga && rateoCopiatoVuoto(riga)) {
        form.removeListItem('ratei', indice);
      }
    }
  };

  const aggiungi = (tipo: TipoRateo | null, unita: UnitaMisura | null) => {
    const numero = form.getValues().ratei.length;
    form.insertListItem('ratei', rateoVuoto(tipo, unita));
    // con tipo e unita' gia' scelti si parte dal primo valore
    daFocalizzare.current = tipo !== null ? `ratei.${numero}.residuoAnnoPrecedente` : `ratei.${numero}`;
  };

  return (
    <Stack gap="xs" className={`${classes.tabella} ${classes.ratei}`} ref={radice}>
      {copiati.ratei && (
        <Text size="xs" c="blue.8">
          {copiati.ratei}
          <PuntoCopiato nota={copiati.ratei} />
        </Text>
      )}
      {copiatiVuoti > 0 && (
        <Group gap="xs" wrap="wrap">
          <Text size="xs" c="orange.9">
            {t.rateiCopiatiVuoti.replace('{numero}', String(copiatiVuoti))}
          </Text>
          <Button variant="subtle" color="orange" size="compact-xs" onClick={rimuoviCopiatiVuoti}>
            {t.rimuoviRateiCopiatiVuoti}
          </Button>
        </Group>
      )}
      {ratei.length === 0 ? (
        <Text size="sm" c="dimmed">
          {t.nessunRateo}
        </Text>
      ) : (
        <>
          <div className={classes.intestazioneTabella} aria-hidden>
            <span>{COLONNE.tipo} *</span>
            <span>{COLONNE.unita} *</span>
            <span>{COLONNE.residuoAnnoPrecedente}</span>
            <span>{COLONNE.maturato}</span>
            <span>{COLONNE.goduto}</span>
            <span>{COLONNE.saldo}</span>
            <span />
          </div>
          <ul className={classes.righe} aria-label={testi.campoCedolino.ratei}>
            {ratei.map((rateo, indice) => (
              <RigaRateo
                key={rateo.chiave}
                indice={indice}
                onRimuovi={() => form.removeListItem('ratei', indice)}
              />
            ))}
          </ul>
        </>
      )}
      <Group gap="xs" wrap="wrap">
        <Button
          variant="light"
          size="xs"
          leftSection={<IconPlus size={14} aria-hidden />}
          onClick={() => aggiungi(null, null)}
        >
          {t.aggiungiRateo}
        </Button>
        {RATEI_COMUNI.map(([tipo, unita]) => (
          <Button
            key={tipo}
            variant="default"
            size="xs"
            disabled={presenti.split(',').includes(`${tipo}/${unita}`)}
            onClick={() => aggiungi(tipo, unita)}
          >
            + {testi.rateo[tipo]} ({t.unitaRateo[unita]})
          </Button>
        ))}
      </Group>
    </Stack>
  );
}

function RigaRateo({ indice, onRimuovi }: { indice: number; onRimuovi: () => void }) {
  const { form } = useModulo();
  const p = `ratei.${indice}`;
  const numero = indice + 1;
  const nome = `${t.ilRateo} ${numero}`;
  const calcolato = useValoreModulo((v) => {
    const rateo = v.ratei[indice];
    return rateo ? saldoCalcolato(rateo) : null;
  });
  // errori sull'intera riga (rateo ripetuto, nessun valore)
  const errore = form.errors[p];
  const idErrore = `errore-rateo-${String(numero)}`;
  return (
    <li
      className={classes.riga}
      aria-label={`${t.rateo} ${numero}`}
      aria-describedby={errore ? idErrore : undefined}
    >
      <CampoSceltaRiga percorso={`${p}.tipo`} etichetta={COLONNE.tipo} obbligatorio inRiga data={TIPI} />
      <CampoSceltaRiga percorso={`${p}.unita`} etichetta={COLONNE.unita} obbligatorio inRiga data={UNITA} />
      {/* giorni e ore con i decimali dei cedolini (es. 2,16667); anche negativi, come li ammette il backend */}
      <CampoImporto
        percorso={`${p}.residuoAnnoPrecedente`}
        etichetta={COLONNE.residuoAnnoPrecedente}
        inRiga
        negativi
        decimali={5}
      />
      <CampoImporto percorso={`${p}.maturato`} etichetta={COLONNE.maturato} inRiga negativi decimali={5} />
      <CampoImporto percorso={`${p}.goduto`} etichetta={COLONNE.goduto} inRiga negativi decimali={5} />
      <CampoImporto
        percorso={`${p}.saldo`}
        etichetta={COLONNE.saldo}
        inRiga
        negativi
        decimali={5}
        placeholder={calcolato !== null ? formatNumero(calcolato) : undefined}
      />
      <div className={classes.azioniRiga}>
        <Tooltip label={`${t.rimuoviRiga} ${nome}`}>
          <ActionIcon
            variant="subtle"
            color="red"
            aria-label={`${t.rimuoviRiga} ${nome}`}
            onClick={onRimuovi}
          >
            <IconTrash size={16} aria-hidden />
          </ActionIcon>
        </Tooltip>
      </div>
      {errore && (
        <div id={idErrore} className={classes.erroreRiga}>
          {errore}
        </div>
      )}
    </li>
  );
}
