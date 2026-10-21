import {
  Autocomplete,
  Checkbox,
  Group,
  Input,
  NativeSelect,
  SegmentedControl,
  Select,
  Switch,
  TextInput,
  Tooltip,
  type ComboboxData,
} from '@mantine/core';
import { useId, type ReactNode } from 'react';
import { DATA_INCOMPLETA, type SceltaSuperminimo } from '../hooks/useCedolinoManuale';
import { testi } from '../i18n';
import { AiutoCampo } from './AiutoCampo';
import { aiutoDi } from './aiuti';
import { CampoNumero } from './CampoNumero';
import classes from './InserimentoManualeModal.module.css';
import { etichettaCampo, useModulo, useValoreModulo, valoreDi } from './moduloCedolino';

const t = testi.manuale;

/**
 * Campi del modulo di inserimento a mano, collegati per percorso al modulo non controllato (@mantine/form): valore
 * iniziale, errore, validazione all'uscita dal campo, `data-path` per spostarvi il focus dall'elenco degli errori e il
 * punto blu dei campi compilati in automatico.
 */

interface PropsBase {
  /** Percorso del modulo, es. "dipendente.nome" o "voci.2.descrizione". */
  percorso: string;
  /** Etichetta visibile (predefinita: il nome del campo del cedolino). */
  etichetta?: string;
  obbligatorio?: boolean;
  descrizione?: ReactNode;
  /** Etichetta di una riga di voce o rateo: nascosta a vista quando la riga e' una riga di tabella. */
  inRiga?: boolean;
  className?: string;
  /**
   * Dato in archivio di un'anagrafica scelta dall'elenco: non modificabile, senza asterisco ne' punto blu, con
   * "Non presente in archivio" se vuoto.
   */
  soloLettura?: boolean;
}

/** Punto blu accanto all'etichetta di un campo compilato in automatico, con la provenienza. */
export function PuntoCopiato({ nota }: { nota: string }) {
  return (
    <Tooltip label={nota} withArrow multiline maw={280}>
      <span className={classes.puntoCopiato} aria-hidden data-copiato />
    </Tooltip>
  );
}

interface PropsEtichetta {
  testo: string;
  copiato: string | undefined;
  /** Spiegazione del campo (icona "i"); null o assente se non prevista. */
  aiuto?: string | null;
}

function Etichetta({ testo, copiato, aiuto }: PropsEtichetta) {
  if (!copiato && !aiuto) {
    return testo;
  }
  return (
    <>
      {testo}
      {copiato && <PuntoCopiato nota={copiato} />}
      {aiuto && <AiutoCampo campo={testo} testo={aiuto} />}
    </>
  );
}

/**
 * Nome accessibile esplicito dei campi con la spiegazione: senza, il nome del pulsante "i" dentro l'etichetta
 * entrerebbe nel nome del campo.
 */
function nomeAccessibile(testo: string, aiuto: string | null) {
  return aiuto ? { 'aria-label': testo } : {};
}

/** Proprieta' comuni dei campi collegati al modulo. */
function useCampo({
  percorso,
  etichetta,
  obbligatorio,
  descrizione,
  inRiga,
  className,
  soloLettura = false,
}: PropsBase) {
  const { form, copiati, rimuoviCopiato } = useModulo();
  const inputProps = form.getInputProps(percorso);
  const aggiorna = inputProps.onChange as (valore: unknown) => void;
  const copiato = soloLettura ? undefined : copiati[percorso];
  const testo = etichetta ?? etichettaCampo(percorso);
  // nelle righe di voci e ratei le spiegazioni sono nella guida della sezione, non ripetute su ogni riga
  const aiuto = inRiga ? null : aiutoDi(percorso);
  return {
    key: form.key(percorso),
    props: {
      ...inputProps,
      onChange: (valore: unknown) => {
        aggiorna(valore);
        if (copiato !== undefined) {
          rimuoviCopiato(percorso);
        }
      },
      label: <Etichetta testo={testo} copiato={copiato} aiuto={aiuto} />,
      ...nomeAccessibile(testo, aiuto),
      withAsterisk: !soloLettura && (obbligatorio ?? false),
      description: descrizione,
      className,
      ...(inRiga ? { classNames: { label: classes.etichettaRiga } } : {}),
      ...(soloLettura ? { readOnly: true, variant: 'filled' as const, placeholder: t.nonInArchivio } : {}),
    },
  };
}

interface PropsTesto extends PropsBase {
  maxLength?: number;
  /** Codici (codice fiscale): maiuscolo durante la digitazione, carattere a larghezza fissa. */
  codice?: boolean;
  autoComplete?: string;
  /** Id di un `<datalist>` con i valori suggeriti. */
  suggerimenti?: string;
}

export function CampoTesto({
  maxLength,
  codice = false,
  autoComplete = 'off',
  suggerimenti,
  ...base
}: PropsTesto) {
  const { key, props } = useCampo({
    ...base,
    className: [base.className, codice ? classes.codice : undefined].filter(Boolean).join(' ') || undefined,
  });
  const onChange = props.onChange;
  return (
    <TextInput
      key={key}
      {...props}
      onChange={(evento) => {
        const input = evento.currentTarget;
        if (codice && input.value !== input.value.toUpperCase()) {
          // maiuscolo durante la digitazione, senza spostare il cursore
          const { selectionStart, selectionEnd } = input;
          input.value = input.value.toUpperCase();
          input.setSelectionRange(selectionStart, selectionEnd);
        }
        onChange(input.value);
      }}
      maxLength={maxLength}
      autoComplete={autoComplete}
      spellCheck={codice ? false : undefined}
      list={suggerimenti}
    />
  );
}

/**
 * Data ISO (AAAA-MM-GG) con il selettore del browser. Una data che il browser non riesce a interpretare (incompleta o
 * inesistente) ha valore vuoto: diventa DATA_INCOMPLETA, segnalata come errore invece di andare persa.
 */
export function CampoData(base: PropsBase) {
  const { form } = useModulo();
  const { key, props } = useCampo(base);
  const onChange = props.onChange;
  const onBlur = props.onBlur as (() => void) | undefined;
  const aggiorna = (input: HTMLInputElement) => {
    const valore = input.validity.badInput ? DATA_INCOMPLETA : input.value;
    // senza cambiare la data mostrata: il campo resta com'e', il modulo riceve il valore da segnalare. Il confronto e'
    // con il valore attuale del modulo, che la scelta di persona e azienda puo' aver cambiato (o svuotato) nel frattempo
    if (valore !== valoreDi(form.getValues(), base.percorso)) {
      onChange(valore);
    }
  };
  return (
    <TextInput
      key={key}
      {...props}
      type="date"
      onChange={(evento) => aggiorna(evento.currentTarget)}
      onBlur={(evento) => {
        // da una data valida a una incompleta il browser non sempre notifica la modifica: si ricontrolla all'uscita
        aggiorna(evento.currentTarget);
        onBlur?.();
      }}
    />
  );
}

interface PropsNumero extends PropsBase {
  decimali?: number;
  negativi?: boolean;
  /** Importo senza segno (voci): il segno di un numero incollato si ignora. */
  senzaSegno?: boolean;
  intero?: boolean;
  placeholder?: string;
}

export function CampoImporto({ decimali, negativi, senzaSegno, intero, placeholder, ...base }: PropsNumero) {
  const { key, props } = useCampo(base);
  return (
    <CampoNumero
      key={key}
      {...props}
      {...(decimali !== undefined ? { decimali } : {})}
      {...(negativi !== undefined ? { negativi } : {})}
      {...(senzaSegno !== undefined ? { senzaSegno } : {})}
      {...(intero !== undefined ? { intero } : {})}
      placeholder={placeholder}
    />
  );
}

interface PropsScelta extends PropsBase {
  data: ComboboxData;
  placeholder?: string;
  /** Il valore si puo' togliere (scelte facoltative). */
  annullabile?: boolean;
  onScelta?: (valore: string | null) => void;
}

export function CampoScelta({ data, placeholder, annullabile = false, onScelta, ...base }: PropsScelta) {
  const { key, props } = useCampo(base);
  const onChange = props.onChange;
  return (
    <Select
      key={key}
      {...props}
      onChange={(valore: string | null) => {
        onChange(valore);
        onScelta?.(valore);
      }}
      data={data}
      placeholder={placeholder}
      allowDeselect={annullabile}
      clearable={annullabile}
    />
  );
}

interface PropsSceltaRiga extends PropsBase {
  data: { value: string; label: string }[];
}

/**
 * Scelta nelle righe di voci e ratei: select nativa, leggera anche con decine di righe e comoda sui telefoni. La
 * scelta vuota vale come non indicata.
 */
export function CampoSceltaRiga({ data, ...base }: PropsSceltaRiga) {
  const { key, props } = useCampo(base);
  const iniziale: unknown = props.defaultValue;
  return (
    <NativeSelect
      key={key}
      {...props}
      defaultValue={typeof iniziale === 'string' ? iniziale : ''}
      data={[{ value: '', label: '' }, ...data]}
    />
  );
}

interface PropsSuggerimenti extends PropsBase {
  data: string[];
  maxLength?: number;
}

/** Testo libero con suggerimenti (CCNL, unita' delle voci). */
export function CampoSuggerimenti({ data, maxLength, ...base }: PropsSuggerimenti) {
  const { key, props } = useCampo(base);
  return <Autocomplete key={key} {...props} data={data} maxLength={maxLength} autoComplete="off" />;
}

/**
 * Flag di tredicesima o quattordicesima: imposto (e non modificabile) quando il tipo di cedolino e' proprio quello,
 * altrimenti a scelta per le mensilita' che le contengono.
 */
export function CampoFlagMensilita({
  percorso,
  tipo,
}: {
  percorso: 'contieneTredicesima' | 'contieneQuattordicesima';
  tipo: 'TREDICESIMA' | 'QUATTORDICESIMA';
}) {
  const { form, copiati, rimuoviCopiato } = useModulo();
  const valore = useValoreModulo((v) => v[percorso]);
  const imposto = useValoreModulo((v) => v.periodo.tipo === tipo);
  const testo = etichettaCampo(percorso);
  const aiuto = aiutoDi(percorso);
  return (
    <Checkbox
      label={<Etichetta testo={testo} copiato={copiati[percorso]} aiuto={aiuto} />}
      {...nomeAccessibile(testo, aiuto)}
      checked={valore || imposto}
      disabled={imposto}
      data-path={percorso}
      onChange={(evento) => {
        form.setFieldValue(percorso, evento.currentTarget.checked);
        rimuoviCopiato(percorso);
      }}
    />
  );
}

const SCELTE_SUPERMINIMO: SceltaSuperminimo[] = ['SI', 'NO', 'NON_INDICATO'];

export function CampoSuperminimoAssorbibile() {
  const percorso = 'retribuzione.superminimoAssorbibile';
  const { form, copiati, rimuoviCopiato } = useModulo();
  const valore = useValoreModulo((v) => v.retribuzione.superminimoAssorbibile);
  const idEtichetta = useId();
  return (
    <Input.Wrapper
      label={
        <Etichetta testo={etichettaCampo(percorso)} copiato={copiati[percorso]} aiuto={aiutoDi(percorso)} />
      }
      labelElement="div"
      labelProps={{ id: idEtichetta }}
    >
      <SegmentedControl
        aria-labelledby={idEtichetta}
        data-path={percorso}
        value={valore}
        onChange={(scelta) => {
          form.setFieldValue(percorso, scelta);
          rimuoviCopiato(percorso);
        }}
        data={SCELTE_SUPERMINIMO.map((scelta) => ({ value: scelta, label: t.superminimo[scelta] }))}
        size="xs"
        mt={4}
        fullWidth
      />
    </Input.Wrapper>
  );
}

/** Interruttore dei buoni pasto: rivela numero, valore unitario e importo. */
export function CampoBuoniPasto({ children }: { children: ReactNode }) {
  const percorso = 'buoniPasto.presenti';
  const { form, copiati, rimuoviCopiato } = useModulo();
  const presenti = useValoreModulo((v) => v.buoniPasto.presenti);
  return (
    <>
      <Switch
        className={classes.largo}
        label={
          <Etichetta testo={etichettaCampo(percorso)} copiato={copiati[percorso]} aiuto={aiutoDi(percorso)} />
        }
        {...nomeAccessibile(etichettaCampo(percorso), aiutoDi(percorso))}
        checked={presenti}
        data-path={percorso}
        onChange={(evento) => {
          form.setFieldValue(percorso, evento.currentTarget.checked);
          rimuoviCopiato(percorso);
        }}
      />
      {presenti && children}
    </>
  );
}

/** "AAAA-MM" dal mese (1..12) e dall'anno; incompleto se ne manca uno, vuoto se mancano entrambi. */
function meseAnno(mese: string | null, anno: number | string): string {
  const parteMese = mese ? mese.padStart(2, '0') : '';
  const parteAnno = anno === '' ? '' : String(anno);
  return parteMese === '' && parteAnno === '' ? '' : `${parteAnno}-${parteMese}`;
}

function partiMeseAnno(valore: string): { mese: string | null; anno: number | string } {
  const match = /^(\d*)-(\d*)$/.exec(valore);
  return {
    mese: match?.[2] ? String(Number(match[2])) : null,
    anno: match?.[1] ? Number(match[1]) : '',
  };
}

/**
 * Mese delle variabili come mese e anno separati (il campo `type="month"` non esiste in Firefox): il modulo riceve
 * "AAAA-MM", oppure un valore incompleto segnalato come errore.
 */
export function CampoMeseVariabili({ datiMesi }: { datiMesi: ComboboxData }) {
  const percorso = 'periodo.meseVariabili';
  const { form, copiati, rimuoviCopiato } = useModulo();
  const iniziale = partiMeseAnno(form.getValues().periodo.meseVariabili);
  const errore: ReactNode = form.errors[percorso];
  const aggiorna = (modifica: Partial<ReturnType<typeof partiMeseAnno>>) => {
    const attuale = partiMeseAnno(form.getValues().periodo.meseVariabili);
    const nuovo = { ...attuale, ...modifica };
    // senza ridisegnare i due campi (perderebbero il focus durante la digitazione)
    form.setFieldValue(percorso, meseAnno(nuovo.mese, nuovo.anno), { forceUpdate: false });
    form.clearFieldError(percorso);
    rimuoviCopiato(percorso);
  };
  return (
    <Group key={form.key(percorso)} gap="sm" align="flex-start" wrap="nowrap" className={classes.largo}>
      <Select
        label={
          <Etichetta testo={t.meseVariabiliMese} copiato={copiati[percorso]} aiuto={aiutoDi(percorso)} />
        }
        {...nomeAccessibile(t.meseVariabiliMese, aiutoDi(percorso))}
        data={datiMesi}
        defaultValue={iniziale.mese}
        onChange={(mese) => aggiorna({ mese })}
        onBlur={() => form.validateField(percorso)}
        placeholder={t.mesePlaceholder}
        clearable
        error={errore}
        data-path={percorso}
        style={{ flex: 2, minWidth: 0 }}
      />
      <CampoNumero
        label={t.meseVariabiliAnno}
        intero
        defaultValue={iniziale.anno}
        onChange={(anno) => aggiorna({ anno })}
        onBlur={() => form.validateField(percorso)}
        error={errore ? true : undefined}
        style={{ flex: 1, minWidth: 0 }}
      />
    </Group>
  );
}
