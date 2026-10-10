import { Alert, Button, em, Group, Loader, Modal, Stack, Tabs, Text } from '@mantine/core';
import { useMediaQuery } from '@mantine/hooks';
import { modals } from '@mantine/modals';
import { IconFileOff, IconPencil } from '@tabler/icons-react';
import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { useCaricamentiInErrore, useCedolino, useRapporto } from '../api/queries';
import type {
  Caricamento,
  CaricamentoInErrore,
  CedolinoDettaglio,
  RapportoDettaglio,
  RigaMensilita,
} from '../api/types';
import {
  daAssociazione,
  daCedolinoRiferimento,
  daRapporto,
  periodoDi,
  rigaDiRiferimento,
  unisciSeVuoti,
  valoriVuoti,
} from '../hooks/useCedolinoManuale';
import { testi } from '../i18n';
import { AnteprimaPdf } from './AnteprimaPdf';
import { ErroreConRiprova } from './ErroreConRiprova';
import classes from './InserimentoManualeModal.module.css';
import { ModuloCedolinoManuale } from './ModuloCedolinoManuale';
import { copiaSeVuoti, notaCopia, type FonteCopia } from './moduloCedolino';
import { MotivoErroreBadge } from './MotivoErroreBadge';

const t = testi.manuale;

/** Sotto il breakpoint md (62em) PDF e modulo sono due schede. */
const SCHERMO_STRETTO = `(max-width: ${em(991)})`;

interface Props {
  /** File in errore da completare; null = modale chiusa. */
  caricamentoId: number | null;
  onChiudi: () => void;
  /** Cedolino salvato: il caricamento restituito dal backend (ACQUISITO, con persona e rapporto). */
  onAcquisito: (caricamento: Caricamento) => void;
}

/**
 * Inserimento a mano del cedolino di un file caricato con errore, a schermo intero: anteprima del PDF accanto al
 * modulo (schede "Dati" / "PDF" sugli schermi stretti), precompilato dal mese associato e dal rapporto.
 */
export function InserimentoManualeModal({ caricamentoId, onChiudi, onAcquisito }: Props) {
  if (caricamentoId === null) {
    return null;
  }
  // un nuovo file (anche "Salva e apri il successivo") riparte da un modulo nuovo
  return (
    <FinestraInserimento
      key={caricamentoId}
      caricamentoId={caricamentoId}
      onChiudi={onChiudi}
      onAcquisito={onAcquisito}
    />
  );
}

function FinestraInserimento({ caricamentoId, onChiudi, onAcquisito }: Props & { caricamentoId: number }) {
  const [apertaIl] = useState(() => Date.now());
  const inErrore = useCaricamentiInErrore();
  const trovato = inErrore.data?.find((e) => e.id === caricamentoId) ?? null;
  // un file appena andato in errore (es. dal riepilogo di un lotto ancora in corso) puo' mancare dall'elenco in cache:
  // prima di dire che non esiste si rilegge l'elenco una volta
  const elencoPrecedente = inErrore.dataUpdatedAt < apertaIl;
  const daRileggere = trovato === null && elencoPrecedente && !inErrore.isFetching && !inErrore.isError;
  const { refetch } = inErrore;
  useEffect(() => {
    if (daRileggere) {
      void refetch();
    }
  }, [daRileggere, refetch]);
  // dopo il salvataggio (o la rimozione) il file esce dall'elenco prima che la finestra si chiuda: resta l'ultimo noto
  const [ultimo, setUltimo] = useState<CaricamentoInErrore | null>(trovato);
  if (trovato !== null && trovato !== ultimo) {
    setUltimo(trovato);
  }
  const elemento = trovato ?? ultimo;
  const stretto = useMediaQuery(SCHERMO_STRETTO, false, { getInitialValueInEffect: false });

  // il modulo registra come sapere se ci sono dati non salvati; letto solo alla chiusura
  const controlloModifiche = useRef<() => boolean>(() => false);
  const registraModifiche = useCallback((modificato: () => boolean) => {
    controlloModifiche.current = modificato;
  }, []);

  const chiudi = () => {
    if (!controlloModifiche.current()) {
      onChiudi();
      return;
    }
    modals.openConfirmModal({
      title: t.chiudiTitolo,
      children: <Text size="sm">{t.chiudiTesto}</Text>,
      labels: { confirm: t.chiudiConferma, cancel: t.continua },
      confirmProps: { color: 'red' },
      onConfirm: onChiudi,
    });
  };

  let contenuto;
  if (elemento !== null) {
    contenuto = (
      <Disposizione
        elemento={elemento}
        stretto={stretto}
        registraModifiche={registraModifiche}
        onChiudi={onChiudi}
        onAcquisito={onAcquisito}
      />
    );
  } else if (inErrore.isError && !inErrore.isFetching) {
    contenuto = (
      <Stack p="md">
        <ErroreConRiprova
          titolo={testi.inErrore.errore}
          errore={inErrore.error}
          onRiprova={() => void inErrore.refetch()}
        />
      </Stack>
    );
  } else if (inErrore.isPending || inErrore.isFetching || elencoPrecedente) {
    contenuto = <Preparazione />;
  } else {
    contenuto = (
      <Stack p="md" align="flex-start">
        <Alert color="gray" variant="light" title={t.fileNonTrovato} icon={<IconFileOff aria-hidden />}>
          {t.fileNonTrovatoTesto}
        </Alert>
        <Button variant="default" onClick={onChiudi}>
          {testi.comune.chiudi}
        </Button>
      </Stack>
    );
  }

  return (
    <Modal.Root opened onClose={chiudi} fullScreen>
      <Modal.Overlay />
      <Modal.Content className={classes.contenuto}>
        <Modal.Header className={classes.intestazione}>
          <Group gap="sm" wrap="wrap" style={{ minWidth: 0 }}>
            <IconPencil size={20} aria-hidden color="var(--mantine-color-marchio-7)" />
            <Modal.Title fw={700} fz="lg">
              {elemento ? `${t.titolo} · ${elemento.nomeFileOriginale}` : t.titolo}
            </Modal.Title>
            {elemento && <MotivoErroreBadge motivo={elemento.motivo} />}
          </Group>
          <Modal.CloseButton aria-label={testi.comune.chiudiFinestra} />
        </Modal.Header>
        <Modal.Body className={classes.corpo}>{contenuto}</Modal.Body>
      </Modal.Content>
    </Modal.Root>
  );
}

function Preparazione() {
  return (
    <Group gap="sm" p="md">
      <Loader size="sm" />
      <Text size="sm" c="dimmed">
        {t.preparazione}
      </Text>
    </Group>
  );
}

interface PropsDisposizione {
  elemento: CaricamentoInErrore;
  stretto: boolean;
  registraModifiche: (modificato: () => boolean) => void;
  onChiudi: () => void;
  onAcquisito: (caricamento: Caricamento) => void;
}

/**
 * PDF e modulo affiancati, oppure in due schede sotto md. La struttura non cambia con la larghezza (ruotando un
 * tablet il modulo non viene ricreato e non perde i dati): cambiano solo le schede e i pannelli nascosti.
 */
function Disposizione({ elemento, stretto, registraModifiche, onChiudi, onAcquisito }: PropsDisposizione) {
  const [scheda, setScheda] = useState<string | null>('dati');
  const idSchede = useId();
  const pannello = (valore: string) =>
    stretto
      ? {
          role: 'tabpanel',
          id: `${idSchede}-panel-${valore}`,
          'aria-labelledby': `${idSchede}-tab-${valore}`,
          hidden: scheda !== valore,
        }
      : {};
  return (
    <div className={classes.disposizione} data-stretta={stretto || undefined}>
      {stretto && (
        <Tabs id={idSchede} value={scheda} onChange={setScheda} className={classes.schede}>
          <Tabs.List grow>
            <Tabs.Tab value="dati" aria-controls={`${idSchede}-panel-dati`}>
              {t.schedaDati}
            </Tabs.Tab>
            <Tabs.Tab value="pdf" aria-controls={`${idSchede}-panel-pdf`}>
              {t.schedaPdf}
            </Tabs.Tab>
          </Tabs.List>
        </Tabs>
      )}
      <section className={classes.colonnaPdf} aria-label={t.pdf} {...pannello('pdf')}>
        <AnteprimaPdf
          caricamentoId={elemento.id}
          nomeFile={elemento.nomeFileOriginale}
          motivo={elemento.motivo}
        />
      </section>
      <div className={classes.colonnaModulo} {...pannello('dati')}>
        <PreparazioneModulo
          elemento={elemento}
          registraModifiche={registraModifiche}
          onChiudi={onChiudi}
          onAcquisito={onAcquisito}
        />
      </div>
    </div>
  );
}

/**
 * Valori iniziali del modulo: periodo dal mese associato, anagrafiche dal rapporto e dati che si ripetono dal
 * cedolino di riferimento, solo nei campi vuoti e con la loro provenienza.
 */
function precompilazione(
  elemento: CaricamentoInErrore,
  rapporto: RapportoDettaglio | undefined,
  riferimento: { riga: RigaMensilita; cedolino: CedolinoDettaglio } | null,
) {
  const { attribuzione } = elemento;
  const fonti: FonteCopia[] = [];
  if (attribuzione) {
    fonti.push({
      dati: daAssociazione(attribuzione, rapporto?.ccnl?.meseQuattordicesima ?? null),
      nota: `${t.dalMeseAssociato} (${attribuzione.titolo}): ${t.daVerificare}`,
    });
  }
  if (rapporto) {
    fonti.push({ dati: daRapporto(rapporto), nota: notaCopia(rapporto.etichetta) });
  }
  if (riferimento) {
    fonti.push({
      dati: daCedolinoRiferimento(riferimento.cedolino, attribuzione?.competenza.anno ?? null),
      nota: notaCopia(riferimento.riga.titolo),
    });
  }
  return copiaSeVuoti(valoriVuoti(), fonti);
}

/**
 * Attende i dati del rapporto associato (e del cedolino di riferimento) prima di creare il modulo. I valori iniziali
 * si fissano una volta sola: dopo il salvataggio le stesse letture vengono aggiornate (il mese diventa presente) e il
 * modulo non deve essere ricreato mentre conclude il salvataggio.
 */
function PreparazioneModulo({
  elemento,
  registraModifiche,
  onChiudi,
  onAcquisito,
}: Omit<PropsDisposizione, 'stretto'>) {
  const { attribuzione } = elemento;
  const [preparato, setPreparato] = useState<ReturnType<typeof precompilazione> | null>(null);
  const daLeggere = preparato === null;
  const rapporto = useRapporto(attribuzione?.rapportoId ?? 0, daLeggere && attribuzione !== null);
  const meseQuattordicesima = rapporto.data?.ccnl?.meseQuattordicesima ?? null;
  const periodo = attribuzione
    ? periodoDi(
        unisciSeVuoti(valoriVuoti(), daAssociazione(attribuzione, meseQuattordicesima)).valori.periodo,
      )
    : null;
  const riga = rapporto.data ? rigaDiRiferimento(rapporto.data.mensilita, periodo) : null;
  const cedolinoId = riga?.cedolinoId ?? null;
  const cedolino = useCedolino(cedolinoId ?? 0, daLeggere && cedolinoId !== null);
  // senza il rapporto (errore) si parte dal solo mese associato; senza il cedolino, senza i suoi dati
  const pronto =
    attribuzione === null ||
    rapporto.isError ||
    (rapporto.isSuccess && (cedolinoId === null || !cedolino.isPending));
  if (preparato === null && pronto) {
    setPreparato(
      precompilazione(
        elemento,
        rapporto.data,
        riga && cedolino.data ? { riga, cedolino: cedolino.data } : null,
      ),
    );
  }
  if (preparato === null) {
    return <Preparazione />;
  }
  const { valori, copiati } = preparato;
  return (
    <ModuloCedolinoManuale
      elemento={elemento}
      iniziali={valori}
      copiatiIniziali={copiati}
      registraModifiche={registraModifiche}
      onChiudi={onChiudi}
      onAcquisito={onAcquisito}
    />
  );
}
