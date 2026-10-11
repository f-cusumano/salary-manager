import { Accordion, Alert, Button, Group, Stack, Text, VisuallyHidden } from '@mantine/core';
import { useForm, type FormErrors, type SetErrors } from '@mantine/form';
import { notifications } from '@mantine/notifications';
import { IconAlertCircle, IconChecks, IconDeviceFloppy } from '@tabler/icons-react';
import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { ApiError, erroriDiCampo, messaggioErrore } from '../api/errors';
import {
  chiavi,
  useAziende,
  useCaricamentiInErrore,
  useInserisciCedolinoManuale,
  useLetture,
  usePersone,
  useVerificaCedolinoManuale,
} from '../api/queries';
import type { Caricamento, CaricamentoInErrore, VerificaCedolinoManuale } from '../api/types';
import {
  applicaScelta,
  cambiaIdentita,
  chiaveRiferimento,
  rapportoDellaCoppia,
  type CambioScelta,
  type FontiScelta,
} from '../hooks/sceltaAnagrafiche';
import {
  ARCHIVIO_VUOTO,
  avvisoDelleAnagrafiche,
  completabileAMano,
  erroriBloccanti,
  indiciRighe,
  modoAzienda,
  modoPersona,
  ordinaPerModulo,
  percorsoForm,
  periodoDi,
  richiestaDa,
  rigaDiRiferimento,
  senzaErroriRisolti,
  SEZIONI_MODULO,
  sezioneDi,
  type ArchivioAnagrafiche,
  type SezioneModulo,
  type ValoriCedolinoManuale,
} from '../hooks/useCedolinoManuale';
import { testi } from '../i18n';
import { useAzioniCaricamenti } from './azioniCaricamenti';
import { ControlliCoerenza } from './ControlliCoerenza';
import classes from './InserimentoManualeModal.module.css';
import {
  ANAGRAFICA_NUOVA,
  campoDelModulo,
  creaNegozioValori,
  ModuloCedolinoContext,
  useValoreModulo,
  valoreDi,
  type ContestoModulo,
  type LetturaRapporto,
} from './moduloCedolino';
import { PannelloVerifica } from './PannelloVerifica';
import { RiepilogoErrori, type ErroreModulo } from './RiepilogoErrori';
import { ContenutoSezione, IntestazioneSezione } from './SezioniCedolinoManuale';

const t = testi.manuale;

/** Sezioni aperte all'inizio: quelle che servono a ogni cedolino. */
const SEZIONI_APERTE: SezioneModulo[] = ['periodo', 'dipendente', 'datore', 'contratto', 'totali', 'voci'];

/** Ultima verifica riuscita: la richiesta verificata (serializzata) e il suo esito. */
interface Verifica {
  richiesta: string;
  esito: VerificaCedolinoManuale;
}

interface Problema {
  titolo: string;
  messaggio: string;
}

interface Props {
  elemento: CaricamentoInErrore;
  /** Valori di partenza, gia' precompilati. */
  iniziali: ValoriCedolinoManuale;
  /** Campi precompilati con la loro provenienza. */
  copiatiIniziali: Readonly<Record<string, string>>;
  /** Registra il controllo delle modifiche non salvate, chiesto alla chiusura della finestra. */
  registraModifiche: (modificato: () => boolean) => void;
  onChiudi: () => void;
  onAcquisito: (caricamento: Caricamento) => void;
}

/** Errori bloccanti calcolati nel browser, sui percorsi del modulo. */
function erroriDelModulo(valori: ValoriCedolinoManuale, archivio: ArchivioAnagrafiche): FormErrors {
  const errori: Record<string, string> = {};
  for (const { campo, messaggio } of erroriBloccanti(valori, archivio)) {
    const percorso = percorsoForm(campo);
    if (percorso !== null) {
      errori[percorso] ??= messaggio;
    }
  }
  return errori;
}

/** Testo per i lettori di schermo all'arrivo dell'esito della verifica (il pannello compare in fondo al modulo). */
function annuncioVerifica(esito: VerificaCedolinoManuale): string {
  if (esito.esito !== 'ACQUISIBILE') {
    return esito.messaggio ? `${t.annuncioNonSalvabile}: ${esito.messaggio}` : t.annuncioNonSalvabile;
  }
  const avvisi = esito.avvisi.filter((avviso) => !avvisoDelleAnagrafiche(avviso)).length;
  const parti = [esito.titolo ? `${t.annuncioSalvabile} (${esito.titolo})` : t.annuncioSalvabile];
  if (esito.nuovaPersona) {
    parti.push(t.nuovaPersona);
  }
  if (esito.nuovaAzienda) {
    parti.push(t.nuovaAzienda);
  }
  if (avvisi > 0) {
    parti.push(t.annuncioAvvisi.replace('{numero}', String(avvisi)));
  }
  return `${parti.join('. ')}.`;
}

/** Il file si puo' proporre come successivo da completare: PDF conservato, nessun tentativo in corso, mese libero. */
function daCompletare(elemento: CaricamentoInErrore): boolean {
  return completabileAMano(elemento) && !elemento.attribuzione?.competenzaPresente;
}

function aggiungiSezioni(aperte: string[], sezioni: Iterable<SezioneModulo | null>): string[] {
  const nuove = [...sezioni].filter((s): s is SezioneModulo => s !== null && !aperte.includes(s));
  return nuove.length > 0 ? [...aperte, ...new Set(nuove)] : aperte;
}

/** Percorsi (campi, o liste intere) con un valore diverso tra due stati del modulo. */
function percorsiCambiati(prima: unknown, dopo: unknown, prefisso = ''): string[] {
  const oggetto = (x: unknown): x is Record<string, unknown> =>
    typeof x === 'object' && x !== null && !Array.isArray(x);
  if (oggetto(prima) && oggetto(dopo)) {
    return [...new Set([...Object.keys(prima), ...Object.keys(dopo)])].flatMap((campo) =>
      percorsiCambiati(prima[campo], dopo[campo], prefisso ? `${prefisso}.${campo}` : campo),
    );
  }
  const uguali = Array.isArray(prima)
    ? JSON.stringify(prima) === JSON.stringify(dopo)
    : Object.is(prima, dopo);
  return uguali ? [] : [prefisso];
}

/**
 * Errori legati alla scelta di persona o azienda: la scelta stessa e i dati anagrafici (in archivio o da scrivere),
 * che cambiano con la scelta.
 */
const ERRORI_SCELTA = {
  persona: /^(personaId|dipendente\.(codiceFiscale|cognome|nome|dataNascita))$/,
  azienda: /^(aziendaId|datoreLavoro\.(ragioneSociale|partitaIva|codiceFiscale|posizioneInps))$/,
} as const;

/** Periodo che decide il cedolino di riferimento della precompilazione ("" se incompleto). */
function chiavePeriodo(valori: ValoriCedolinoManuale): string {
  const periodo = periodoDi(valori.periodo);
  return periodo ? `${String(periodo.anno)}-${String(periodo.mese)}` : '';
}

/** Errori del server su una scelta o su un dato che rivela un'anagrafica gia' in archivio: gli elenchi vanno riletti. */
const ERRORE_ANAGRAFICHE =
  /^(personaId|aziendaId|dipendente\.codiceFiscale|datoreLavoro\.(ragioneSociale|partitaIva|codiceFiscale|posizioneInps))$/;

/**
 * Modulo dell'inserimento a mano (@mantine/form non controllato: la digitazione non ridisegna il modulo). Verifica
 * (anche con Ctrl/Cmd+Invio) con gli errori bloccanti calcolati nel browser e poi dal backend; il salvataggio e'
 * possibile solo se l'ultima verifica riguarda esattamente i dati attuali ed e' acquisibile.
 */
export function ModuloCedolinoManuale({
  elemento,
  iniziali,
  copiatiIniziali,
  registraModifiche,
  onChiudi,
  onAcquisito,
}: Props) {
  const azioni = useAzioniCaricamenti();
  const queryClient = useQueryClient();
  const persone = usePersone();
  const aziende = useAziende();
  const archivio = useMemo<ArchivioAnagrafiche>(
    () => ({
      persone: persone.data ?? [],
      aziende: aziende.data ?? [],
    }),
    [persone.data, aziende.data],
  );
  const [negozio] = useState(() => creaNegozioValori(iniziali));
  const [richiestaIniziale] = useState(() => JSON.stringify(richiestaDa(iniziali)));
  // letti dalla modifica dei valori, che il modulo puo' conservare dalla prima creazione
  const archivioAttuale = useRef<ArchivioAnagrafiche>(ARCHIVIO_VUOTO);
  const impostaErrori = useRef<SetErrors | null>(null);
  const periodoCambiato = useRef<(() => void) | null>(null);
  const form = useForm({
    mode: 'uncontrolled',
    initialValues: iniziali,
    validate: (valori: ValoriCedolinoManuale) => erroriDelModulo(valori, archivio),
    validateInputOnBlur: true,
    onValuesChange: (valori, precedenti) => {
      negozio.imposta(valori);
      // un errore di riga o tra campi si risolve anche modificando un altro campo: non deve restare in vista
      impostaErrori.current?.(
        (attuali) => senzaErroriRisolti(attuali, valori, archivioAttuale.current) as FormErrors,
      );
      if (chiavePeriodo(valori) !== chiavePeriodo(precedenti)) {
        periodoCambiato.current?.();
      }
    },
  });
  useEffect(() => {
    archivioAttuale.current = archivio;
    impostaErrori.current = form.setErrors;
  });

  // le modifiche non salvate (dati che andrebbero persi) si confrontano sulla richiesta, non sulle righe vuote
  useEffect(() => {
    registraModifiche(() => JSON.stringify(richiestaDa(form.getValues())) !== richiestaIniziale);
  });

  // i copiati si leggono anche dopo le letture asincrone del rapporto: il riferimento e' sempre quello attuale
  const [copiati, setCopiati] = useState<Readonly<Record<string, string>>>(copiatiIniziali);
  const copiatiAttuali = useRef<Readonly<Record<string, string>>>(copiatiIniziali);
  const impostaCopiati = useCallback((nuovi: Readonly<Record<string, string>>) => {
    copiatiAttuali.current = nuovi;
    setCopiati(nuovi);
  }, []);
  const rimuoviCopiato = useCallback(
    (percorso: string) => {
      const attuali = copiatiAttuali.current;
      if (percorso in attuali) {
        impostaCopiati(Object.fromEntries(Object.entries(attuali).filter(([p]) => p !== percorso)));
      }
    },
    [impostaCopiati],
  );

  /** Imposta solo i campi cambiati: si ridisegnano (e perdono l'errore) solo quelli, gli altri restano come sono. */
  const impostaValori = (nuovi: ValoriCedolinoManuale) => {
    for (const percorso of percorsiCambiati(form.getValues(), nuovi)) {
      form.setFieldValue(percorso, valoreDi(nuovi, percorso));
    }
  };

  // scelta di persona e azienda: dati anagrafici in archivio, poi (con entrambe) i dati del loro rapporto
  const letture = useLetture();
  const [letturaRapporto, setLetturaRapporto] = useState<LetturaRapporto>(null);
  // numero dell'ultima scelta: la lettura di una scelta superata (o del modulo chiuso) non compila nulla
  const ultimaScelta = useRef(0);
  useEffect(
    () => () => {
      ultimaScelta.current += 1;
    },
    [],
  );
  // riferimento (chiaveRiferimento) dell'ultima precompilazione dal rapporto; undefined se va rifatta comunque
  const riferimentoUsato = useRef<string | undefined>(undefined);

  const anagraficheScelte = (valori: ValoriCedolinoManuale) => ({
    persona: (persone.data ?? []).find((p) => String(p.id) === valori.personaId) ?? null,
    azienda: (aziende.data ?? []).find((a) => String(a.id) === valori.aziendaId) ?? null,
  });

  /**
   * Dati del rapporto di persona e azienda (se esiste) e del suo cedolino piu' vicino al periodo: i dati copiati in
   * precedenza si tolgono e i campi vuoti si riempiono di nuovo (quelli modificati dall'utente restano). Al cambio del
   * periodo (`soloSeCambiato`) solo se il cedolino di riferimento o il criterio "stesso anno" cambiano.
   */
  const compilaDalRapporto = async (numero: number, soloSeCambiato = false) => {
    const { persona, azienda } = anagraficheScelte(form.getValues());
    if (!persona || !azienda) {
      return;
    }
    if (!soloSeCambiato) {
      setLetturaRapporto({ stato: 'inCorso' });
    }
    try {
      const coppia = rapportoDellaCoppia(await letture.rapporti(persona.id), azienda.id);
      let fonti: Pick<FontiScelta, 'rapporto' | 'riferimento'> | null = null;
      let chiave = chiaveRiferimento(null, null);
      if (coppia) {
        const rapporto = await letture.rapporto(coppia.id);
        const periodo = periodoDi(form.getValues().periodo);
        const riga = rigaDiRiferimento(rapporto.mensilita, periodo);
        const cedolino = riga?.cedolinoId != null ? await letture.cedolino(riga.cedolinoId) : null;
        const riferimento = riga && cedolino ? { riga, cedolino } : null;
        fonti = { rapporto, riferimento };
        chiave = chiaveRiferimento(riferimento, periodo);
      }
      if (numero !== ultimaScelta.current) {
        return;
      }
      setLetturaRapporto(null);
      if (soloSeCambiato && chiave === riferimentoUsato.current) {
        return;
      }
      riferimentoUsato.current = chiave;
      if (fonti) {
        const risultato = applicaScelta(
          form.getValues(),
          copiatiAttuali.current,
          { persona: false, azienda: false },
          { persona, azienda, ...fonti },
        );
        impostaValori(risultato.valori);
        impostaCopiati(risultato.copiati);
      }
    } catch (errore) {
      if (numero === ultimaScelta.current) {
        setLetturaRapporto({ stato: 'errore', errore });
      }
    }
  };

  /** Con persona e azienda scelte, rilegge il riferimento: la precompilazione segue il periodo indicato. */
  const ripetiPrecompilazione = () => {
    const { persona, azienda } = anagraficheScelte(form.getValues());
    if (persona && azienda) {
      ultimaScelta.current += 1;
      void compilaDalRapporto(ultimaScelta.current, true);
    }
  };
  useEffect(() => {
    periodoCambiato.current = ripetiPrecompilazione;
  });

  const aggiornaScelta = (
    scelta:
      | Pick<ValoriCedolinoManuale, 'personaId' | 'nuovaPersona'>
      | Pick<ValoriCedolinoManuale, 'aziendaId' | 'nuovaAzienda'>,
    anagrafica: 'persona' | 'azienda',
    cambio: CambioScelta,
  ) => {
    const valori = { ...form.getValues(), ...scelta };
    const { persona, azienda } = anagraficheScelte(valori);
    ultimaScelta.current += 1;
    riferimentoUsato.current = undefined;
    const risultato = applicaScelta(valori, copiatiAttuali.current, cambio, {
      persona,
      azienda,
      rapporto: null,
      riferimento: null,
    });
    impostaValori(risultato.valori);
    impostaCopiati(risultato.copiati);
    // gli errori della scelta precedente (scelta mancante, dati anagrafici, anagrafica non piu' in archivio) non
    // valgono piu': restano solo quelli che la prossima verifica trovera' di nuovo
    form.setErrors((attuali) =>
      Object.fromEntries(
        Object.entries(attuali).filter(([percorso]) => !ERRORI_SCELTA[anagrafica].test(percorso)),
      ),
    );
    if (persona && azienda) {
      void compilaDalRapporto(ultimaScelta.current);
    } else {
      setLetturaRapporto(null);
    }
  };

  const scegliPersona = (scelta: string | null) => {
    const nuovaPersona = scelta === ANAGRAFICA_NUOVA;
    const personaId = nuovaPersona ? null : scelta;
    const attuali = form.getValues();
    if (attuali.personaId !== personaId || attuali.nuovaPersona !== nuovaPersona) {
      const identita = cambiaIdentita(modoPersona(attuali), modoPersona({ personaId, nuovaPersona }));
      aggiornaScelta({ personaId, nuovaPersona }, 'persona', { persona: identita, azienda: false });
    }
  };

  const scegliAzienda = (scelta: string | null) => {
    const nuovaAzienda = scelta === ANAGRAFICA_NUOVA;
    const aziendaId = nuovaAzienda ? null : scelta;
    const attuali = form.getValues();
    if (attuali.aziendaId !== aziendaId || attuali.nuovaAzienda !== nuovaAzienda) {
      const identita = cambiaIdentita(modoAzienda(attuali), modoAzienda({ aziendaId, nuovaAzienda }));
      aggiornaScelta({ aziendaId, nuovaAzienda }, 'azienda', { persona: false, azienda: identita });
    }
  };

  const riprovaLetturaRapporto = () => {
    ultimaScelta.current += 1;
    riferimentoUsato.current = undefined;
    setLetturaRapporto(null);
    void compilaDalRapporto(ultimaScelta.current);
  };

  const [aperte, setAperte] = useState<string[]>(SEZIONI_APERTE);
  // una sezione si disegna alla prima apertura e poi resta montata (i valori sono comunque nel modulo)
  const [montate, setMontate] = useState<ReadonlySet<string>>(() => new Set(SEZIONI_APERTE));
  if (aperte.some((sezione) => !montate.has(sezione))) {
    setMontate(new Set([...montate, ...aperte]));
  }
  const onTipoCambiato = useCallback((tipo: string | null) => {
    // il TFR si compila nei cedolini di fine rapporto
    if (tipo === 'FINE_RAPPORTO') {
      setAperte((attuali) => aggiungiSezioni(attuali, ['tfr']));
    }
  }, []);

  // focus su un campo, anche in una sezione chiusa: prima si apre la sezione, poi (dopo il disegno) il focus
  const radice = useRef<HTMLFormElement>(null);
  const [richiestaFocus, setRichiestaFocus] = useState<{ percorso: string } | null>(null);
  useEffect(() => {
    if (richiestaFocus !== null) {
      campoDelModulo(radice.current, richiestaFocus.percorso)?.focus();
    }
  }, [richiestaFocus]);
  const vaiAlCampo = useCallback((percorso: string) => {
    setAperte((attuali) => aggiungiSezioni(attuali, [sezioneDi(percorso)]));
    setRichiestaFocus({ percorso });
  }, []);

  const [riepilogoVisibile, setRiepilogoVisibile] = useState(false);
  const [erroriGenerali, setErroriGenerali] = useState<string[]>([]);
  const [problema, setProblema] = useState<Problema | null>(null);
  const [verifica, setVerifica] = useState<Verifica | null>(null);
  // esito della verifica annunciato ai lettori di schermo (il contatore ripete l'annuncio di un esito uguale)
  const [annuncio, setAnnuncio] = useState<{ testo: string; numero: number }>({ testo: '', numero: 0 });
  const verificaCedolino = useVerificaCedolinoManuale();
  const inserisci = useInserisciCedolinoManuale();
  const [salvataggio, setSalvataggio] = useState<'singolo' | 'successivo' | null>(null);

  const erroriCampi = ordinaPerModulo(
    Object.entries(form.errors)
      .filter(([, messaggio]) => messaggio !== null && messaggio !== undefined && messaggio !== false)
      .map(([campo, messaggio]) => ({ campo, messaggio })),
  );
  const elencoErrori: ErroreModulo[] = [
    ...erroriCampi.map(({ campo, messaggio }) => ({ percorso: campo, messaggio })),
    ...erroriGenerali.map((messaggio) => ({ percorso: null, messaggio })),
  ];

  /** Mostra il riepilogo, apre le sezioni con errori e porta il focus sul primo. */
  const mostraErrori = (percorsi: string[]) => {
    setRiepilogoVisibile(true);
    setAperte((attuali) => aggiungiSezioni(attuali, percorsi.map(sezioneDi)));
    const primo = ordinaPerModulo(percorsi.map((campo) => ({ campo })))[0];
    if (primo) {
      setRichiestaFocus({ percorso: primo.campo });
    }
  };

  const gestisciErrore = (errore: unknown, valori: ValoriCedolinoManuale, titolo: string) => {
    if (errore instanceof ApiError && errore.status === 404) {
      setProblema({ titolo: t.fileNonTrovato, messaggio: t.fileNonTrovatoTesto });
      return;
    }
    const errori = erroriDiCampo(errore);
    if (errori.length === 0) {
      // 409 (duplicato, file in elaborazione…) o altro: i dati restano nel modulo
      setProblema({ titolo, messaggio: messaggioErrore(errore) });
      return;
    }
    // 400: gli indici delle righe della richiesta (senza righe vuote) tornano quelli del modulo
    const indici = indiciRighe(valori);
    const perCampo: Record<string, string> = {};
    const generali: string[] = [];
    for (const { campo, messaggio } of errori) {
      const percorso = percorsoForm(campo, indici);
      if (percorso !== null) {
        perCampo[percorso] ??= messaggio;
      } else {
        generali.push(messaggio);
      }
    }
    form.setErrors(perCampo);
    setErroriGenerali(generali);
    mostraErrori(Object.keys(perCampo));
    // una persona o un'azienda scelta e non piu' in archivio, o una "nuova" che nel frattempo e' stata censita
    if (errori.some((e) => e.campo !== null && ERRORE_ANAGRAFICHE.test(e.campo))) {
      void queryClient.invalidateQueries({ queryKey: chiavi.persone });
      void queryClient.invalidateQueries({ queryKey: chiavi.aziende });
    }
  };

  const occupato = verificaCedolino.isPending || inserisci.isPending;

  const verificaDati = () => {
    if (occupato) {
      return;
    }
    setProblema(null);
    setErroriGenerali([]);
    const risultato = form.validate();
    if (risultato.hasErrors) {
      mostraErrori(Object.keys(risultato.errors));
      return;
    }
    setRiepilogoVisibile(false);
    const valori = form.getValues();
    const dati = richiestaDa(valori);
    verificaCedolino.mutate(
      { caricamentoId: elemento.id, dati },
      {
        onSuccess: (esito) => {
          setVerifica({ richiesta: JSON.stringify(dati), esito });
          setAnnuncio((attuale) => ({ testo: annuncioVerifica(esito), numero: attuale.numero + 1 }));
        },
        onError: (errore) => gestisciErrore(errore, valori, t.erroreVerifica),
      },
    );
  };

  const inErrore = useCaricamentiInErrore();
  const successivi = (inErrore.data ?? []).filter((e) => e.id !== elemento.id && daCompletare(e));

  const salva = (apriSuccessivo: boolean) => {
    const valori = form.getValues();
    const dati = richiestaDa(valori);
    if (occupato || verifica?.esito.esito !== 'ACQUISIBILE' || JSON.stringify(dati) !== verifica.richiesta) {
      return;
    }
    setProblema(null);
    const titolo = verifica.esito.titolo;
    // il successivo si sceglie ora: dopo il salvataggio l'elenco viene riletto
    const successivo = apriSuccessivo ? (successivi[0]?.id ?? null) : null;
    setSalvataggio(apriSuccessivo ? 'successivo' : 'singolo');
    inserisci.mutate(
      { caricamentoId: elemento.id, dati },
      {
        onSuccess: (caricamento) => {
          notifications.show({ color: 'teal', message: titolo ? `${t.inserito}: ${titolo}` : t.inserito });
          onAcquisito(caricamento);
          if (successivo !== null) {
            azioni.apriInserimento(successivo);
          } else {
            onChiudi();
          }
        },
        onError: (errore) => gestisciErrore(errore, valori, t.erroreSalvataggio),
        onSettled: () => setSalvataggio(null),
      },
    );
  };

  const onKeyDown = (evento: KeyboardEvent<HTMLFormElement>) => {
    if (evento.key === 'Enter' && (evento.ctrlKey || evento.metaKey)) {
      evento.preventDefault();
      verificaDati();
    }
  };

  const contesto: ContestoModulo = {
    form,
    negozio,
    copiati,
    rimuoviCopiato,
    scegliPersona,
    scegliAzienda,
    letturaRapporto,
    riprovaLetturaRapporto,
    onTipoCambiato,
  };

  return (
    <ModuloCedolinoContext.Provider value={contesto}>
      <form
        ref={radice}
        className={classes.modulo}
        aria-label={t.modulo}
        noValidate
        onSubmit={(evento) => evento.preventDefault()}
        onKeyDown={onKeyDown}
      >
        <Stack gap="md" className={classes.contenutoModulo}>
          <Text size="sm">{t.introduzione}</Text>
          {Object.keys(copiati).length > 0 && (
            <Text size="xs" c="blue.8">
              <span className={classes.puntoCopiato} aria-hidden /> {t.copiatiNota}
            </Text>
          )}
          {problema !== null && (
            <Alert
              color="red"
              variant="light"
              title={problema.titolo}
              icon={<IconAlertCircle aria-hidden />}
              role="alert"
              withCloseButton
              closeButtonLabel={testi.comune.chiudi}
              onClose={() => setProblema(null)}
            >
              {problema.messaggio}
            </Alert>
          )}
          {riepilogoVisibile && <RiepilogoErrori errori={elencoErrori} onVaiAlCampo={vaiAlCampo} />}
          <Accordion
            multiple
            value={aperte}
            onChange={setAperte}
            variant="separated"
            chevronPosition="left"
            keepMounted
            keepMountedMode="display-none"
            transitionDuration={0}
          >
            {SEZIONI_MODULO.map((sezione) => (
              <Accordion.Item key={sezione} value={sezione}>
                <Accordion.Control>
                  <IntestazioneSezione
                    sezione={sezione}
                    errori={erroriCampi.filter((e) => sezioneDi(e.campo) === sezione).length}
                  />
                </Accordion.Control>
                <Accordion.Panel>
                  {montate.has(sezione) && <ContenutoSezione sezione={sezione} />}
                </Accordion.Panel>
              </Accordion.Item>
            ))}
          </Accordion>
          <ControlliCoerenza onVaiAlCampo={vaiAlCampo} />
          {verifica !== null && (
            <EsitoVerifica
              verifica={verifica}
              onRimuoviFile={() => azioni.rimuovi(elemento.id, { onRimosso: onChiudi })}
            />
          )}
          {/* regione sempre presente: un lettore di schermo annuncia i cambiamenti, non le regioni nuove */}
          <VisuallyHidden aria-live="polite" aria-atomic="true" data-annuncio-verifica>
            {annuncio.testo}
            {annuncio.numero % 2 === 1 ? '\u00a0' : ''}
          </VisuallyHidden>
        </Stack>
        <BarraAzioni
          verifica={verifica}
          inVerifica={verificaCedolino.isPending}
          salvataggio={salvataggio}
          successivi={successivi.length}
          onVerifica={verificaDati}
          onSalva={salva}
        />
      </form>
    </ModuloCedolinoContext.Provider>
  );
}

/** I dati attuali sono esattamente quelli dell'ultima verifica (si ridisegna solo quando la risposta cambia). */
function useVerificaAttuale(verifica: Verifica | null): boolean {
  return useValoreModulo((v) => verifica !== null && JSON.stringify(richiestaDa(v)) === verifica.richiesta);
}

function EsitoVerifica({ verifica, onRimuoviFile }: { verifica: Verifica; onRimuoviFile: () => void }) {
  const attuale = useVerificaAttuale(verifica);
  const pannello = useRef<HTMLDivElement>(null);
  useEffect(() => {
    pannello.current?.scrollIntoView({ block: 'nearest' });
  }, [verifica]);
  return (
    <PannelloVerifica
      ref={pannello}
      esito={verifica.esito}
      superata={!attuale}
      onRimuoviFile={onRimuoviFile}
    />
  );
}

interface PropsBarra {
  verifica: Verifica | null;
  inVerifica: boolean;
  salvataggio: 'singolo' | 'successivo' | null;
  /** File in errore che si possono completare dopo questo. */
  successivi: number;
  onVerifica: () => void;
  onSalva: (apriSuccessivo: boolean) => void;
}

function BarraAzioni({ verifica, inVerifica, salvataggio, successivi, onVerifica, onSalva }: PropsBarra) {
  const attuale = useVerificaAttuale(verifica);
  const salvabile = attuale && verifica?.esito.esito === 'ACQUISIBILE';
  const occupato = inVerifica || salvataggio !== null;
  const nota = verifica === null ? t.verificaPrima : attuale ? t.scorciatoia : t.verificaDaRifare;
  return (
    <div className={classes.azioni}>
      <Group justify="space-between" gap="sm" wrap="wrap">
        <Text
          size="xs"
          c={verifica !== null && !attuale ? 'orange.8' : 'dimmed'}
          style={{ flex: '1 1 14rem' }}
        >
          {nota}
        </Text>
        <Group gap="xs" wrap="wrap" justify="flex-end">
          <Button
            type="button"
            variant={salvabile ? 'default' : 'filled'}
            leftSection={<IconChecks size={16} aria-hidden />}
            onClick={onVerifica}
            loading={inVerifica}
            disabled={salvataggio !== null}
            aria-keyshortcuts="Control+Enter Meta+Enter"
          >
            {t.verifica}
          </Button>
          {successivi > 0 && (
            <Button
              type="button"
              variant="light"
              onClick={() => onSalva(true)}
              disabled={!salvabile || (occupato && salvataggio !== 'successivo')}
              loading={salvataggio === 'successivo'}
            >
              {`${t.salvaESuccessivo} (${String(successivi)})`}
            </Button>
          )}
          <Button
            type="button"
            variant={salvabile ? 'filled' : 'default'}
            leftSection={<IconDeviceFloppy size={16} aria-hidden />}
            onClick={() => onSalva(false)}
            disabled={!salvabile || (occupato && salvataggio !== 'singolo')}
            loading={salvataggio === 'singolo'}
          >
            {t.salva}
          </Button>
        </Group>
      </Group>
    </div>
  );
}
