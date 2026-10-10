import { Badge, Group, Text } from '@mantine/core';
import { useElencoCcnl } from '../api/queries';
import {
  compilatiSezione,
  controlliCoerenza,
  sezioneDi,
  TIPI_MENSILITA,
  titoloPrevisto,
  type SezioneModulo,
} from '../hooks/useCedolinoManuale';
import { testi } from '../i18n';
import {
  CampoBuoniPasto,
  CampoData,
  CampoFlagMensilita,
  CampoImporto,
  CampoMeseVariabili,
  CampoScelta,
  CampoSuggerimenti,
  CampoSuperminimoAssorbibile,
  CampoTesto,
} from './CampiModulo';
import classes from './InserimentoManualeModal.module.css';
import { useModulo, useValoreModulo } from './moduloCedolino';
import { TabellaRateiModificabile } from './TabellaRateiModificabile';
import { TabellaVociModificabile } from './TabellaVociModificabile';

const t = testi.manuale;
const TIPI = TIPI_MENSILITA.map((tipo) => ({ value: tipo, label: t.tipi[tipo] }));
const MESI = testi.mesi.map((nome, indice) => ({ value: String(indice + 1), label: nome }));

function plurale(numero: number, singolare: string, plurale: string): string {
  return `${String(numero)} ${numero === 1 ? singolare : plurale}`;
}

interface PropsIntestazione {
  sezione: SezioneModulo;
  /** Errori bloccanti mostrati nei campi della sezione. */
  errori: number;
  codiciFiscaliCensiti: readonly string[];
}

/** Titolo della sezione con i campi compilati, gli errori e gli avvisi che contiene. */
export function IntestazioneSezione({ sezione, errori, codiciFiscaliCensiti }: PropsIntestazione) {
  const compilati = useValoreModulo((v) => compilatiSezione(v, sezione));
  const avvisi = useValoreModulo(
    (v) => controlliCoerenza(v, codiciFiscaliCensiti).filter((a) => sezioneDi(a.campo) === sezione).length,
  );
  return (
    <Group gap="xs" wrap="wrap">
      <Text fw={600} size="sm">
        {t.sezioni[sezione]}
      </Text>
      <Badge size="xs" variant="light" color="gray" style={{ textTransform: 'none' }}>
        {plurale(compilati, t.compilato, t.compilati)}
      </Badge>
      {errori > 0 && (
        <Badge size="xs" variant="filled" color="red" style={{ textTransform: 'none' }}>
          {plurale(errori, t.errore, t.errori)}
        </Badge>
      )}
      {avvisi > 0 && (
        <Badge size="xs" variant="light" color="orange" style={{ textTransform: 'none' }}>
          {plurale(avvisi, t.avviso, t.avvisi)}
        </Badge>
      )}
    </Group>
  );
}

/** Campi di una sezione del modulo (tabella di §9.7: nessun dato bancario). */
export function ContenutoSezione({ sezione }: { sezione: SezioneModulo }) {
  switch (sezione) {
    case 'periodo':
      return <SezionePeriodo />;
    case 'dipendente':
      return (
        <div className={classes.campi}>
          <CampoTesto percorso="dipendente.codiceFiscale" obbligatorio codice maxLength={16} />
          <CampoTesto percorso="dipendente.cognome" obbligatorio />
          <CampoTesto percorso="dipendente.nome" obbligatorio />
          <CampoData percorso="dipendente.dataNascita" />
          <CampoTesto percorso="dipendente.codiceDipendente" />
        </div>
      );
    case 'datore':
      return (
        <div className={classes.campi}>
          <CampoTesto percorso="datoreLavoro.ragioneSociale" obbligatorio className={classes.largo} />
          <CampoTesto percorso="datoreLavoro.partitaIva" codice />
          <CampoTesto percorso="datoreLavoro.codiceFiscale" codice />
          <CampoTesto percorso="datoreLavoro.posizioneInps" />
          <CampoTesto percorso="datoreLavoro.posizioneInail" />
          <CampoTesto percorso="datoreLavoro.codiceAzienda" />
          <CampoTesto percorso="datoreLavoro.indirizzo" descrizione={t.noIban} className={classes.largo} />
        </div>
      );
    case 'contratto':
      return <SezioneContratto />;
    case 'totali':
      return (
        <div className={classes.campi}>
          <CampoImporto percorso="totali.totaleCompetenze" obbligatorio />
          <CampoImporto percorso="totali.totaleTrattenute" obbligatorio />
          <CampoImporto percorso="totali.arrotondamento" negativi />
          <CampoImporto percorso="totali.netto" obbligatorio negativi />
          <CampoImporto percorso="totali.lordo" />
        </div>
      );
    case 'voci':
      return <TabellaVociModificabile />;
    case 'ratei':
      return <TabellaRateiModificabile />;
    case 'retribuzione':
      return (
        <div className={classes.campi}>
          <CampoImporto percorso="retribuzione.minimo" />
          <CampoImporto percorso="retribuzione.contingenza" />
          <CampoImporto percorso="retribuzione.superminimo" />
          <CampoSuperminimoAssorbibile />
          <CampoImporto percorso="retribuzione.importoScatti" />
          <CampoImporto percorso="retribuzione.altriElementiFissi" />
          <CampoImporto percorso="retribuzione.retribuzioneBase" />
          <CampoImporto percorso="retribuzione.totale" />
          <CampoImporto percorso="retribuzione.pagaOraria" decimali={5} />
          <CampoImporto percorso="retribuzione.pagaGiornaliera" decimali={5} />
          <CampoImporto percorso="retribuzione.divisoreOrario" />
          <CampoImporto percorso="retribuzione.divisoreGiornaliero" />
        </div>
      );
    case 'presenze':
      return (
        <div className={classes.campi}>
          <CampoImporto percorso="presenze.giorniLavorati" />
          <CampoImporto percorso="presenze.oreLavorate" />
          <CampoImporto percorso="presenze.giorniRetribuiti" />
        </div>
      );
    case 'tfr':
      return (
        <div className={classes.campi}>
          <CampoImporto percorso="tfr.fondo31DicembreAnnoPrecedente" />
          <CampoImporto percorso="tfr.rivalutazione" />
          <CampoImporto percorso="tfr.impostaSostitutiva" />
          <CampoImporto percorso="tfr.quotaAnno" />
          <CampoImporto percorso="tfr.quotaMese" />
          <CampoImporto percorso="tfr.quotaFondi" />
          <CampoImporto percorso="tfr.quotaBusta" />
          <CampoImporto percorso="tfr.anticipi" />
          <CampoImporto percorso="tfr.retribuzioneUtile" />
          <CampoImporto percorso="tfr.liquidatoLordo" />
          <CampoImporto percorso="tfr.ritenuta" />
        </div>
      );
    case 'scatti':
      return (
        <div className={classes.campi}>
          <CampoImporto percorso="scatti.numeroScatti" intero />
          <CampoData percorso="scatti.dataProssimoScatto" />
          <CampoBuoniPasto>
            <CampoImporto percorso="buoniPasto.numero" />
            <CampoImporto percorso="buoniPasto.valoreUnitario" />
            <CampoImporto percorso="buoniPasto.importo" />
          </CampoBuoniPasto>
        </div>
      );
    case 'progressivi':
      return (
        <div className={classes.campi}>
          <CampoImporto percorso="progressivi.imponibilePrevidenziale" />
          <CampoImporto percorso="progressivi.imponibileIrpef" />
          <CampoImporto percorso="progressivi.irpefTrattenuta" />
        </div>
      );
  }
}

function SezionePeriodo() {
  const { onTipoCambiato } = useModulo();
  return (
    <div className={classes.campi}>
      <CampoScelta
        percorso="periodo.tipo"
        obbligatorio
        data={TIPI}
        descrizione={t.tipoAiuto}
        onScelta={onTipoCambiato}
      />
      <CampoScelta percorso="periodo.mese" obbligatorio data={MESI} placeholder={t.mesePlaceholder} />
      <CampoImporto percorso="periodo.anno" obbligatorio intero />
      <CampoData percorso="periodo.dataElaborazione" />
      <CampoMeseVariabili datiMesi={MESI} />
      <CampoFlagMensilita percorso="contieneTredicesima" tipo="TREDICESIMA" />
      <CampoFlagMensilita percorso="contieneQuattordicesima" tipo="QUATTORDICESIMA" />
      <TitoloPrevisto />
    </div>
  );
}

/** "Sarà registrato come: Maggio 2023" mentre si compila il periodo; il titolo definitivo arriva dalla verifica. */
function TitoloPrevisto() {
  const titolo = useValoreModulo((v) => titoloPrevisto(v.periodo));
  if (titolo === null) {
    return null;
  }
  return (
    <Text size="sm" className={classes.largo} aria-live="polite">
      {t.registratoCome} <strong>{titolo}</strong>{' '}
      <Text span size="xs" c="dimmed">
        ({t.registratoComeNota})
      </Text>
    </Text>
  );
}

function SezioneContratto() {
  // suggerimenti: le descrizioni dei CCNL noti (il testo resta libero, come stampato)
  const ccnl = useElencoCcnl(true);
  const suggerimenti = [...new Set((ccnl.data ?? []).map((c) => c.descrizione))];
  return (
    <div className={classes.campi}>
      <CampoData percorso="datiContrattuali.dataAssunzione" />
      <CampoData percorso="datiContrattuali.dataCessazione" />
      <CampoTesto percorso="datiContrattuali.qualifica" />
      <CampoTesto percorso="datiContrattuali.livello" />
      <CampoSuggerimenti
        percorso="datiContrattuali.ccnl"
        data={suggerimenti}
        maxLength={300}
        className={classes.largo}
      />
      <CampoTesto percorso="datiContrattuali.codiceCnel" />
    </div>
  );
}
