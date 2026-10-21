import { Badge, Group, List, Stack, Text } from '@mantine/core';
import { useElencoCcnl, usePersone } from '../api/queries';
import {
  CATEGORIE_VOCE,
  compilatiSezione,
  controlliCoerenza,
  modoAzienda,
  modoPersona,
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
import { SceltaAzienda, SceltaPersona } from './SceltaAnagrafiche';
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
}

/** Titolo della sezione con i campi compilati, gli errori e gli avvisi che contiene. */
export function IntestazioneSezione({ sezione, errori }: PropsIntestazione) {
  const compilati = useValoreModulo((v) => compilatiSezione(v, sezione));
  const avvisi = useValoreModulo(
    (v) => controlliCoerenza(v).filter((a) => sezioneDi(a.campo) === sezione).length,
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

type SezioneConGuida = keyof typeof testi.aiuti.sezioni;

/** Come compilare una sezione (totali, voci, ratei, TFR), in testa ai suoi campi. */
function GuidaSezione({ sezione }: { sezione: SezioneConGuida }) {
  return (
    <Text size="xs" c="dimmed">
      {testi.aiuti.sezioni[sezione]}
    </Text>
  );
}

/** Le categorie delle voci, con cosa comprende ciascuna: aperte a richiesta. */
function GuidaCategorie() {
  return (
    <details className={classes.guidaCategorie}>
      <summary>
        <Text span size="xs" fw={600}>
          {testi.aiuti.categorieTitolo}
        </Text>
      </summary>
      <List size="xs" spacing={2} mt={4}>
        {CATEGORIE_VOCE.map((categoria) => (
          <List.Item key={categoria}>
            <strong>{t.categorie[categoria]}</strong>: {testi.aiuti.categorie[categoria]}
          </List.Item>
        ))}
      </List>
    </details>
  );
}

/** Campi di una sezione del modulo (tabella di §9.7: nessun dato bancario). */
export function ContenutoSezione({ sezione }: { sezione: SezioneModulo }) {
  switch (sezione) {
    case 'periodo':
      return <SezionePeriodo />;
    case 'dipendente':
      return <SezioneDipendente />;
    case 'datore':
      return <SezioneDatore />;
    case 'contratto':
      return <SezioneContratto />;
    case 'totali':
      return (
        <Stack gap="xs">
          <GuidaSezione sezione="totali" />
          <div className={classes.campi}>
            <CampoImporto percorso="totali.totaleCompetenze" obbligatorio />
            <CampoImporto percorso="totali.totaleTrattenute" obbligatorio />
            <CampoImporto percorso="totali.arrotondamento" negativi />
            <CampoImporto percorso="totali.netto" obbligatorio negativi />
            <CampoImporto percorso="totali.lordo" />
          </div>
        </Stack>
      );
    case 'voci':
      return (
        <Stack gap="xs">
          <GuidaSezione sezione="voci" />
          <GuidaCategorie />
          <TabellaVociModificabile />
        </Stack>
      );
    case 'ratei':
      return (
        <Stack gap="xs">
          <GuidaSezione sezione="ratei" />
          <TabellaRateiModificabile />
        </Stack>
      );
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
        <Stack gap="xs">
          <GuidaSezione sezione="tfr" />
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
        </Stack>
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

/**
 * Persona scelta dall'elenco (dati anagrafici in archivio, in sola lettura: la data di nascita si puo' indicare se
 * l'archivio non la ha) o nuova (dati come stampati); la matricola e' del rapporto e resta modificabile.
 */
function SezioneDipendente() {
  const modo = useValoreModulo(modoPersona);
  const personaId = useValoreModulo((v) => v.personaId);
  const persone = usePersone();
  const scelta = modo === 'scelta';
  const dataNascitaInArchivio = persone.data?.find((p) => String(p.id) === personaId)?.dataNascita != null;
  return (
    <div className={classes.campi}>
      <SceltaPersona />
      {modo !== 'nessuna' && (
        <>
          <CampoTesto
            percorso="dipendente.codiceFiscale"
            obbligatorio
            codice
            maxLength={16}
            soloLettura={scelta}
          />
          <CampoTesto percorso="dipendente.cognome" obbligatorio soloLettura={scelta} />
          <CampoTesto percorso="dipendente.nome" obbligatorio soloLettura={scelta} />
          <CampoData percorso="dipendente.dataNascita" soloLettura={scelta && dataNascitaInArchivio} />
          {scelta && (
            <Text size="xs" c="dimmed" className={classes.largo}>
              {t.datiInArchivioPersona}
            </Text>
          )}
        </>
      )}
      <CampoTesto percorso="dipendente.codiceDipendente" />
    </div>
  );
}

/**
 * Azienda scelta dall'elenco (ragione sociale e identificativi in archivio, in sola lettura) o nuova; posizione
 * INAIL, codice azienda e indirizzo restano modificabili.
 */
function SezioneDatore() {
  const modo = useValoreModulo(modoAzienda);
  const scelta = modo === 'scelta';
  return (
    <div className={classes.campi}>
      <SceltaAzienda />
      {modo !== 'nessuna' && (
        <>
          <CampoTesto
            percorso="datoreLavoro.ragioneSociale"
            obbligatorio
            className={classes.largo}
            soloLettura={scelta}
          />
          <CampoTesto percorso="datoreLavoro.partitaIva" codice soloLettura={scelta} />
          <CampoTesto percorso="datoreLavoro.codiceFiscale" codice soloLettura={scelta} />
          <CampoTesto percorso="datoreLavoro.posizioneInps" soloLettura={scelta} />
          {scelta && (
            <Text size="xs" c="dimmed" className={classes.largo}>
              {t.datiInArchivioAzienda}
            </Text>
          )}
        </>
      )}
      <CampoTesto percorso="datoreLavoro.posizioneInail" />
      <CampoTesto percorso="datoreLavoro.codiceAzienda" />
      <CampoTesto percorso="datoreLavoro.indirizzo" descrizione={t.noIban} className={classes.largo} />
    </div>
  );
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
