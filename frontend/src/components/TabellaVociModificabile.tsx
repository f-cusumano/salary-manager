import { ActionIcon, Button, Group, Stack, Text, Tooltip } from '@mantine/core';
import { IconArrowDown, IconArrowUp, IconPlus, IconTrash } from '@tabler/icons-react';
import { useEffect, useId, useRef } from 'react';
import { formatImporto } from '../format/format';
import {
  CATEGORIE_VOCE,
  numeroDa,
  sommeVoci,
  voceCopiataVuota,
  voceVuota,
} from '../hooks/useCedolinoManuale';
import { testi } from '../i18n';
import { CampoImporto, CampoSceltaRiga, CampoTesto, PuntoCopiato } from './CampiModulo';
import classes from './InserimentoManualeModal.module.css';
import { campoDelModulo, useModulo, useValoreModulo } from './moduloCedolino';

const t = testi.manuale;
const COLONNE = t.colonneVoce;
const CATEGORIE = CATEGORIE_VOCE.map((categoria) => ({ value: categoria, label: t.categorie[categoria] }));
/** Differenza oltre la quale le somme delle voci sono segnalate rispetto ai totali (come il backend). */
const TOLLERANZA_SOMME = 0.05;

/**
 * Voci del cedolino come righe modificabili: tabella sugli schermi larghi, una scheda per voce su quelli stretti
 * (container query). Righe spostabili e rimovibili; in fondo le somme di competenze e trattenute confrontate con i
 * totali stampati. Le voci copiate da un altro cedolino e non completate non vengono salvate: una nota le conta e
 * permette di toglierle.
 */
export function TabellaVociModificabile() {
  const { form, copiati } = useModulo();
  // le righe cambiano solo con le operazioni sulla lista, che ridisegnano il modulo
  const voci = form.getValues().voci;
  const copiateVuote = useValoreModulo((v) => v.voci.filter(voceCopiataVuota).length);
  const daFocalizzare = useRef<string | null>(null);
  const radice = useRef<HTMLDivElement>(null);
  const idUnita = useId();

  useEffect(() => {
    if (daFocalizzare.current !== null) {
      campoDelModulo(radice.current, daFocalizzare.current)?.focus();
      daFocalizzare.current = null;
    }
  });

  const aggiungi = () => {
    const attuali = form.getValues().voci;
    // la categoria proposta e' quella della riga precedente
    const categoria = attuali.at(-1)?.categoria ?? 'COMPETENZA';
    form.insertListItem('voci', voceVuota(categoria));
    daFocalizzare.current = `voci.${attuali.length}`;
  };

  const rimuoviCopiateVuote = () => {
    const righe = form.getValues().voci;
    for (let indice = righe.length - 1; indice >= 0; indice -= 1) {
      const riga = righe[indice];
      if (riga && voceCopiataVuota(riga)) {
        form.removeListItem('voci', indice);
      }
    }
  };

  const sposta = (da: number, a: number) => {
    form.reorderListItem('voci', { from: da, to: a });
  };

  return (
    <Stack gap="xs" className={classes.tabella} ref={radice}>
      <Text size="xs" c="dimmed">
        {t.noIban}
      </Text>
      <datalist id={idUnita}>
        {t.unitaVoce.map((unita) => (
          <option key={unita} value={unita} />
        ))}
      </datalist>
      {copiati.voci && (
        <Text size="xs" c="blue.8">
          {copiati.voci}
          <PuntoCopiato nota={copiati.voci} />
        </Text>
      )}
      {copiateVuote > 0 && (
        <Group gap="xs" wrap="wrap">
          <Text size="xs" c="orange.9">
            {t.vociCopiateVuote.replace('{numero}', String(copiateVuote))}
          </Text>
          <Button variant="subtle" color="orange" size="compact-xs" onClick={rimuoviCopiateVuote}>
            {t.rimuoviVociCopiateVuote}
          </Button>
        </Group>
      )}
      {voci.length === 0 ? (
        <Text size="sm" c="dimmed">
          {t.nessunaVoce}
        </Text>
      ) : (
        <>
          <div className={classes.intestazioneTabella} aria-hidden>
            <span>{COLONNE.codice}</span>
            <span>{COLONNE.descrizione} *</span>
            <span>{COLONNE.categoria} *</span>
            <span>{COLONNE.quantita}</span>
            <span>{COLONNE.unita}</span>
            <span>{COLONNE.base}</span>
            <span>{COLONNE.importoCompetenza}</span>
            <span>{COLONNE.importoTrattenuta}</span>
            <span />
          </div>
          <ul className={classes.righe} aria-label={testi.campoCedolino.voci}>
            {voci.map((voce, indice) => (
              <RigaVoce
                key={voce.chiave}
                indice={indice}
                idUnita={idUnita}
                ultima={indice === voci.length - 1}
                onSposta={sposta}
                onRimuovi={() => form.removeListItem('voci', indice)}
              />
            ))}
          </ul>
        </>
      )}
      <Group justify="space-between" align="flex-start" wrap="wrap" gap="sm">
        <Button variant="light" size="xs" leftSection={<IconPlus size={14} aria-hidden />} onClick={aggiungi}>
          {t.aggiungiVoce}
        </Button>
        <SommeVoci />
      </Group>
    </Stack>
  );
}

interface PropsRiga {
  indice: number;
  /** Id del `<datalist>` delle unita' suggerite. */
  idUnita: string;
  ultima: boolean;
  onSposta: (da: number, a: number) => void;
  onRimuovi: () => void;
}

function RigaVoce({ indice, idUnita, ultima, onSposta, onRimuovi }: PropsRiga) {
  const p = `voci.${indice}`;
  const numero = indice + 1;
  const nome = `${t.laVoce} ${numero}`;
  return (
    <li className={classes.riga} aria-label={`${t.voce} ${numero}`}>
      <CampoTesto percorso={`${p}.codice`} etichetta={COLONNE.codice} inRiga maxLength={30} />
      <CampoTesto
        percorso={`${p}.descrizione`}
        etichetta={COLONNE.descrizione}
        obbligatorio
        inRiga
        className={classes.esteso}
      />
      <CampoSceltaRiga
        percorso={`${p}.categoria`}
        etichetta={COLONNE.categoria}
        obbligatorio
        inRiga
        data={CATEGORIE}
      />
      <CampoImporto percorso={`${p}.quantita`} etichetta={COLONNE.quantita} inRiga decimali={5} />
      <CampoTesto
        percorso={`${p}.unita`}
        etichetta={COLONNE.unita}
        inRiga
        maxLength={20}
        suggerimenti={idUnita}
      />
      <CampoImporto percorso={`${p}.base`} etichetta={COLONNE.base} inRiga decimali={5} />
      <CampoImporto
        percorso={`${p}.importoCompetenza`}
        etichetta={COLONNE.importoCompetenza}
        inRiga
        senzaSegno
      />
      <CampoImporto
        percorso={`${p}.importoTrattenuta`}
        etichetta={COLONNE.importoTrattenuta}
        inRiga
        senzaSegno
      />
      <div className={classes.azioniRiga}>
        <Tooltip label={`${t.spostaSu} ${nome}`}>
          <ActionIcon
            variant="subtle"
            color="gray"
            aria-label={`${t.spostaSu} ${nome}`}
            disabled={indice === 0}
            onClick={() => onSposta(indice, indice - 1)}
          >
            <IconArrowUp size={16} aria-hidden />
          </ActionIcon>
        </Tooltip>
        <Tooltip label={`${t.spostaGiu} ${nome}`}>
          <ActionIcon
            variant="subtle"
            color="gray"
            aria-label={`${t.spostaGiu} ${nome}`}
            disabled={ultima}
            onClick={() => onSposta(indice, indice + 1)}
          >
            <IconArrowDown size={16} aria-hidden />
          </ActionIcon>
        </Tooltip>
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
    </li>
  );
}

/** Somme delle voci che entrano nel netto rispetto ai totali stampati, in arancione se diverse oltre 5 centesimi. */
function SommeVoci() {
  const competenze = useValoreModulo((v) => sommeVoci(v.voci)?.competenze ?? null);
  const trattenute = useValoreModulo((v) => sommeVoci(v.voci)?.trattenute ?? null);
  const totaleCompetenze = useValoreModulo((v) => numeroDa(v.totali.totaleCompetenze));
  const totaleTrattenute = useValoreModulo((v) => numeroDa(v.totali.totaleTrattenute));
  if (competenze === null || trattenute === null) {
    return null;
  }
  return (
    <Stack gap={2} align="flex-end">
      <div className={classes.somme}>
        <Somma etichetta={t.sommaCompetenze} somma={competenze} totale={totaleCompetenze} />
        <Somma etichetta={t.sommaTrattenute} somma={trattenute} totale={totaleTrattenute} />
      </div>
      <Text size="xs" c="dimmed">
        {t.sommeNota}
      </Text>
    </Stack>
  );
}

function Somma({ etichetta, somma, totale }: { etichetta: string; somma: number; totale: number | null }) {
  const diversa = totale !== null && Math.abs(somma - totale) > TOLLERANZA_SOMME + 1e-9;
  return (
    <Text size="sm" c={diversa ? 'orange.8' : undefined} fw={diversa ? 600 : undefined}>
      {etichetta}: {formatImporto(somma)}
      {totale !== null && (
        <Text span size="sm" c={diversa ? 'orange.8' : 'dimmed'}>
          {' '}
          ({t.controTotale} {formatImporto(totale)})
        </Text>
      )}
    </Text>
  );
}
