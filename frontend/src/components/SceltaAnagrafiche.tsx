import { Button, Group, Loader, Select, Text, type ComboboxData, type OptionsFilter } from '@mantine/core';
import { IconList, IconPlus, IconRefresh } from '@tabler/icons-react';
import { useEffect, useMemo, useRef, type ReactNode } from 'react';
import { messaggioErrore } from '../api/errors';
import { useAziende, usePersone, useRapporti } from '../api/queries';
import { filtroAnagrafiche, opzioniAziende } from '../hooks/sceltaAnagrafiche';
import { modoAzienda, modoPersona, type ModoAnagrafica } from '../hooks/useCedolinoManuale';
import { testi } from '../i18n';
import { ErroreConRiprova } from './ErroreConRiprova';
import classes from './InserimentoManualeModal.module.css';
import { ANAGRAFICA_NUOVA, campoDelModulo, useModulo, useValoreModulo } from './moduloCedolino';

const t = testi.manuale;

interface PropsRiga {
  percorso: 'personaId' | 'aziendaId';
  /** Primo dato anagrafico da compilare dopo "Crea nuovo" (riceve il focus). */
  primoCampo: string;
  modo: ModoAnagrafica;
  valore: string | null;
  data: ComboboxData;
  filtro: OptionsFilter;
  testi: {
    etichetta: string;
    placeholder: string;
    descrizione: string;
    nessunaTrovata: string;
    nuova: string;
    archivioVuoto: string;
    crea: string;
    creaNome: string;
    /** Nome accessibile di "Scegli dall'elenco" (i due pulsanti possono essere visibili insieme). */
    scegliNome: string;
    erroreElenco: string;
  };
  elenco: { inCaricamento: boolean; vuoto: boolean; errore: unknown; riprova: () => void };
  onScegli: (scelta: string | null) => void;
  children?: ReactNode;
}

/**
 * Tendina di un'anagrafica in archivio con "Crea nuovo" accanto; dopo "Crea nuovo" un testo e "Scegli dall'elenco" per
 * tornare alla tendina. Con l'archivio vuoto resta solo l'inserimento a mano.
 */
function RigaScelta({
  percorso,
  primoCampo,
  modo,
  valore,
  data,
  filtro,
  testi: tr,
  elenco,
  onScegli,
  children,
}: PropsRiga) {
  const { form } = useModulo();
  const radice = useRef<HTMLDivElement>(null);
  // dopo "Crea nuovo" il focus va al primo dato da inserire, dopo "Scegli dall'elenco" alla tendina: il campo esiste
  // solo dopo il rendering del nuovo modo
  const focusDopo = useRef<string | null>(null);
  useEffect(() => {
    if (focusDopo.current !== null) {
      campoDelModulo(radice.current?.closest('form') ?? null, focusDopo.current)?.focus();
      focusDopo.current = null;
    }
  });
  const passaA = (scelta: string | null, focus: string) => {
    focusDopo.current = focus;
    onScegli(scelta);
  };

  const crea = () => {
    passaA(ANAGRAFICA_NUOVA, primoCampo);
  };
  const pulsanteCrea = (
    <Button
      variant="light"
      leftSection={<IconPlus size={16} aria-hidden />}
      aria-label={tr.creaNome}
      onClick={crea}
    >
      {tr.crea}
    </Button>
  );

  let contenuto: ReactNode;
  if (modo === 'nuova' || elenco.vuoto) {
    contenuto = (
      <Group gap="xs" wrap="wrap" justify="space-between">
        <Text size="sm" style={{ flex: '1 1 14rem' }}>
          {elenco.vuoto ? tr.archivioVuoto : tr.nuova}
        </Text>
        {modo !== 'nuova'
          ? pulsanteCrea
          : !elenco.vuoto && (
              <Button
                variant="light"
                size="xs"
                leftSection={<IconList size={14} aria-hidden />}
                aria-label={tr.scegliNome}
                onClick={() => {
                  passaA(null, percorso);
                }}
              >
                {t.scegliDallElenco}
              </Button>
            )}
      </Group>
    );
  } else {
    contenuto = (
      <Select
        label={tr.etichetta}
        placeholder={elenco.inCaricamento ? t.caricamentoElenco : tr.placeholder}
        description={tr.descrizione}
        data={data}
        value={valore}
        onChange={onScegli}
        searchable
        allowDeselect={false}
        filter={filtro}
        nothingFoundMessage={tr.nessunaTrovata}
        maxDropdownHeight={280}
        comboboxProps={{ withinPortal: true }}
        disabled={elenco.inCaricamento}
        error={form.errors[percorso]}
        data-path={percorso}
        autoComplete="off"
        inputContainer={(input) => (
          <Group gap="xs" wrap="wrap" align="flex-start">
            <div style={{ flex: '1 1 14rem', minWidth: 0 }}>{input}</div>
            {pulsanteCrea}
          </Group>
        )}
      />
    );
  }

  return (
    <div ref={radice} className={classes.largo}>
      {contenuto}
      {elenco.errore !== null && modo !== 'nuova' && (
        <ErroreConRiprova titolo={tr.erroreElenco} errore={elenco.errore} onRiprova={elenco.riprova} />
      )}
      {children}
    </div>
  );
}

/** "Persona in archivio": persone dell'elenco (con il codice fiscale per gli omonimi) e "Crea nuovo". */
export function SceltaPersona() {
  const { scegliPersona } = useModulo();
  const persone = usePersone();
  const modo = useValoreModulo(modoPersona);
  const valore = useValoreModulo((v) => v.personaId);
  const { data: elenco } = persone;
  const data = useMemo(
    () => (elenco ?? []).map((p) => ({ value: String(p.id), label: p.etichetta })),
    [elenco],
  );
  const filtro = useMemo(
    () => filtroAnagrafiche(new Map((elenco ?? []).map((p) => [String(p.id), [p.codiceFiscale]]))),
    [elenco],
  );
  return (
    <RigaScelta
      percorso="personaId"
      primoCampo="dipendente.codiceFiscale"
      modo={modo}
      valore={valore}
      data={data}
      filtro={filtro}
      testi={{
        etichetta: t.personaInArchivio,
        placeholder: t.personaPlaceholder,
        descrizione: t.personaDescrizione,
        nessunaTrovata: t.nessunaPersonaTrovata,
        nuova: t.nuovaPersonaTesto,
        archivioVuoto: t.nessunaPersonaInArchivio,
        crea: t.creaNuovo,
        creaNome: t.creaNuovoDipendente,
        scegliNome: t.scegliDallElencoPersone,
        erroreElenco: t.erroreElencoPersone,
      }}
      elenco={{
        inCaricamento: persone.isPending && persone.fetchStatus !== 'idle',
        vuoto: elenco?.length === 0,
        errore: persone.isError ? persone.error : null,
        riprova: () => void persone.refetch(),
      }}
      onScegli={scegliPersona}
    />
  );
}

/**
 * "Azienda in archivio": aziende dell'elenco (con partita IVA, codice fiscale o posizione INPS per le omonime), prima
 * quelle della persona scelta, e "Crea nuova". Sotto, lo stato della lettura dei dati del rapporto da precompilare.
 */
export function SceltaAzienda() {
  const { scegliAzienda, letturaRapporto, riprovaLetturaRapporto } = useModulo();
  const aziende = useAziende();
  const persone = usePersone();
  const modo = useValoreModulo(modoAzienda);
  const valore = useValoreModulo((v) => v.aziendaId);
  const personaId = useValoreModulo((v) => v.personaId);
  const persona = persone.data?.find((p) => String(p.id) === personaId) ?? null;
  const rapporti = useRapporti(persona?.id ?? null);
  const { data: elenco } = aziende;
  const data = useMemo(
    () => opzioniAziende(elenco ?? [], rapporti.data ?? null, persona?.etichetta ?? null),
    [elenco, rapporti.data, persona?.etichetta],
  );
  const filtro = useMemo(
    () =>
      filtroAnagrafiche(new Map((elenco ?? []).map((a) => [String(a.id), [a.partitaIva, a.codiceFiscale]]))),
    [elenco],
  );
  return (
    <RigaScelta
      percorso="aziendaId"
      primoCampo="datoreLavoro.ragioneSociale"
      modo={modo}
      valore={valore}
      data={data}
      filtro={filtro}
      testi={{
        etichetta: t.aziendaInArchivio,
        placeholder: t.aziendaPlaceholder,
        descrizione: t.aziendaDescrizione,
        nessunaTrovata: t.nessunaAziendaTrovata,
        nuova: t.nuovaAziendaTesto,
        archivioVuoto: t.nessunaAziendaInArchivio,
        crea: t.creaNuova,
        creaNome: t.creaNuovaAzienda,
        scegliNome: t.scegliDallElencoAziende,
        erroreElenco: t.erroreElencoAziende,
      }}
      elenco={{
        inCaricamento: aziende.isPending && aziende.fetchStatus !== 'idle',
        vuoto: elenco?.length === 0,
        errore: aziende.isError ? aziende.error : null,
        riprova: () => void aziende.refetch(),
      }}
      onScegli={scegliAzienda}
    >
      {/* regione sempre presente: i lettori di schermo annunciano la lettura quando inizia */}
      <div role="status">
        {letturaRapporto?.stato === 'inCorso' && (
          <Group gap="xs" mt={4} wrap="nowrap">
            <Loader size="xs" />
            <Text size="sm" c="dimmed">
              {t.letturaRapporto}
            </Text>
          </Group>
        )}
      </div>
      {letturaRapporto?.stato === 'errore' && (
        <Group gap="xs" mt={4} wrap="wrap" role="alert">
          <Text size="sm" c="red.8">
            {t.letturaRapportoErrore}: {messaggioErrore(letturaRapporto.errore)}
          </Text>
          <Button
            variant="subtle"
            color="red"
            size="compact-sm"
            leftSection={<IconRefresh size={14} aria-hidden />}
            onClick={riprovaLetturaRapporto}
          >
            {testi.comune.riprova}
          </Button>
        </Group>
      )}
    </RigaScelta>
  );
}
