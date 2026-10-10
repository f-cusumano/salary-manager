import { AppShell, Container, Stack, Text } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { modals } from '@mantine/modals';
import { notifications } from '@mantine/notifications';
import { IconTrash } from '@tabler/icons-react';
import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { messaggioErrore } from './api/errors';
import { invalidaDopoAcquisizione } from './api/invalidazioni';
import {
  useCaricamentiInErrore,
  usePersone,
  useRimuoviAssociazione,
  useRimuoviCaricamento,
} from './api/queries';
import type { Caricamento, LottoCaricamento } from './api/types';
import { AssociaMeseModal } from './components/AssociaMeseModal';
import {
  AzioniCaricamentiContext,
  type AzioniCaricamenti,
  type OpzioniAssociazione,
} from './components/azioniCaricamenti';
import { CaricamentiInErroreArea } from './components/CaricamentiInErroreArea';
import { CaricamentoModal } from './components/CaricamentoModal';
import { ErroreConRiprova } from './components/ErroreConRiprova';
import { InserimentoManualeModal } from './components/InserimentoManualeModal';
import { RapportiList } from './components/RapportiList';
import { RiletturaModal } from './components/RiletturaModal';
import { TopBar } from './components/TopBar';
import { INTERVALLO_POLLING_MS, personaDelLotto, useCaricamento } from './hooks/useCaricamento';
import { testi } from './i18n';
import { leggiPersonaRicordata, ricordaPersona } from './memoria';

interface Props {
  /** Intervallo di polling dello stato dei caricamenti (ridotto nei test). */
  intervalloPollingMs?: number;
}

function riepilogoLotto(lotto: LottoCaricamento): string {
  const conteggi = new Map<string, number>();
  for (const c of lotto.caricamenti) {
    conteggi.set(c.stato, (conteggi.get(c.stato) ?? 0) + 1);
  }
  const riepilogo = [...conteggi.entries()]
    .map(
      ([stato, numero]) =>
        `${testi.caricamento.stato[stato as keyof typeof testi.caricamento.stato]}: ${numero}`,
    )
    .join(' · ');
  // i file in errore restano nell'elenco "Cedolini caricati con errore" finche' non vengono completati
  const inErrore = conteggi.get('ERRORE') ?? 0;
  return inErrore > 0 ? `${riepilogo} · ${inErrore} ${testi.caricamento.daCompletare}` : riepilogo;
}

export function App({ intervalloPollingMs = INTERVALLO_POLLING_MS }: Props) {
  const queryClient = useQueryClient();
  const persone = usePersone();
  const [personaScelta, setPersonaScelta] = useState<number | null>(leggiPersonaRicordata);
  const [modaleAperta, { open: apriModaleCaricamento, close: chiudiModaleCaricamento }] =
    useDisclosure(false);
  // modali dei cedolini caricati con errore, aperte da qualunque punto tramite AzioniCaricamentiContext
  const [inserimento, setInserimento] = useState<{ caricamentoId: number } | null>(null);
  const [associazione, setAssociazione] = useState<OpzioniAssociazione | null>(null);
  const [rilettura, setRilettura] = useState<{ caricamentoId: number } | null>(null);

  // la persona ricordata vale solo se esiste ancora (puo' sparire eliminando l'ultimo cedolino)
  const personaId =
    personaScelta !== null && persone.data?.some((p) => p.id === personaScelta) ? personaScelta : null;
  const personaCorrente = useRef(personaId);
  useEffect(() => {
    personaCorrente.current = personaId;
  }, [personaId]);
  // letta al momento della chiamata: tra un await e l'altro l'utente puo' aver scelto una persona
  const nessunaPersonaSelezionata = useCallback(() => personaCorrente.current === null, []);

  const selezionaPersona = useCallback((id: number | null) => {
    setPersonaScelta(id);
    ricordaPersona(id);
  }, []);

  const caricamentoCompletato = useCallback(
    async (lotto: LottoCaricamento) => {
      await invalidaDopoAcquisizione(queryClient);
      notifications.show({
        color: lotto.caricamenti.some((c) => c.stato === 'ERRORE') ? 'orange' : 'teal',
        title: testi.caricamento.completato,
        message: riepilogoLotto(lotto),
      });
      // senza una persona selezionata si mostra quella appena caricata
      const nuova = personaDelLotto(lotto);
      if (nuova !== null && nessunaPersonaSelezionata()) {
        selezionaPersona(nuova);
      }
    },
    [queryClient, selezionaPersona, nessunaPersonaSelezionata],
  );

  const caricamento = useCaricamento((lotto) => void caricamentoCompletato(lotto), intervalloPollingMs);
  const lottoCompletato = caricamento.lotto?.completato ?? false;
  const { azzera } = caricamento;

  const apriModale = useCallback(() => {
    if (lottoCompletato) {
      azzera();
    }
    apriModaleCaricamento();
  }, [lottoCompletato, azzera, apriModaleCaricamento]);

  // cedolino inserito a mano: come a fine lotto, senza una persona selezionata si mostra la sua
  const cedolinoInserito = useCallback(
    (acquisito: Caricamento) => {
      if (acquisito.personaId !== null && nessunaPersonaSelezionata()) {
        selezionaPersona(acquisito.personaId);
      }
    },
    [selezionaPersona, nessunaPersonaSelezionata],
  );

  const inErrore = useCaricamentiInErrore();
  const elencoInErrore = inErrore.data;
  const { mutate: rimuoviFile } = useRimuoviCaricamento();
  const { mutate: rimuoviAssociazione } = useRimuoviAssociazione();

  const azioni = useMemo<AzioniCaricamenti>(() => {
    const t = testi.inErrore;
    return {
      apriInserimento: (caricamentoId) => setInserimento({ caricamentoId }),
      apriAssociazione: setAssociazione,
      apriRilettura: (caricamentoId) => setRilettura({ caricamentoId }),
      rimuovi: (caricamentoId) => {
        const nome = elencoInErrore?.find((e) => e.id === caricamentoId)?.nomeFileOriginale;
        modals.openConfirmModal({
          title: t.rimuoviTitolo,
          children: (
            <Text size="sm">
              {nome && (
                <>
                  <strong>{nome}</strong> —{' '}
                </>
              )}
              {t.rimuoviTesto}
            </Text>
          ),
          labels: { confirm: t.rimuoviConferma, cancel: testi.comune.annulla },
          confirmProps: { color: 'red', leftSection: <IconTrash size={16} aria-hidden /> },
          onConfirm: () =>
            rimuoviFile(caricamentoId, {
              onSuccess: () =>
                notifications.show({ color: 'teal', message: nome ? `${t.rimosso}: ${nome}` : t.rimosso }),
              onError: (errore) =>
                notifications.show({
                  color: 'red',
                  title: t.rimuoviErrore,
                  message: messaggioErrore(errore),
                }),
            }),
        });
      },
      rimuoviAssociazione: (caricamentoId) =>
        rimuoviAssociazione(caricamentoId, {
          onSuccess: () => notifications.show({ color: 'teal', message: t.associazioneRimossa }),
          onError: (errore) =>
            notifications.show({
              color: 'red',
              title: t.rimuoviAssociazioneErrore,
              message: messaggioErrore(errore),
            }),
        }),
      caricaDiNuovo: apriModale,
    };
  }, [elencoInErrore, rimuoviFile, rimuoviAssociazione, apriModale]);

  return (
    <AzioniCaricamentiContext.Provider value={azioni}>
      <AppShell header={{ height: 68 }} padding={0}>
        <AppShell.Header withBorder={false} style={{ boxShadow: 'var(--mantine-shadow-xs)' }}>
          <TopBar
            personaId={personaId}
            onSelezionaPersona={selezionaPersona}
            onCaricaCedolini={apriModale}
            caricamentoInCorso={caricamento.inElaborazione}
          />
        </AppShell.Header>
        <AppShell.Main bg="gray.1">
          <Container size="lg" py={{ base: 'md', sm: 'xl' }} px={{ base: 'sm', sm: 'lg' }}>
            <Stack gap="md">
              {persone.isError && (
                <ErroreConRiprova
                  titolo={testi.topBar.errorePersone}
                  errore={persone.error}
                  onRiprova={() => void persone.refetch()}
                />
              )}
              {/* indipendente dalla persona: un file non letto non e' ancora di nessuno */}
              <CaricamentiInErroreArea />
              {personaId !== null && <RapportiList key={personaId} personaId={personaId} />}
            </Stack>
          </Container>
        </AppShell.Main>
        <CaricamentoModal
          aperta={modaleAperta}
          onChiudi={chiudiModaleCaricamento}
          caricamento={caricamento}
        />
        <AssociaMeseModal
          opzioni={associazione}
          personaPredefinita={personaId}
          onChiudi={() => setAssociazione(null)}
        />
        <RiletturaModal
          caricamentoId={rilettura?.caricamentoId ?? null}
          caricamento={caricamento}
          onChiudi={() => setRilettura(null)}
          onAvviata={() => {
            setRilettura(null);
            apriModaleCaricamento();
          }}
        />
        <InserimentoManualeModal
          caricamentoId={inserimento?.caricamentoId ?? null}
          onChiudi={() => setInserimento(null)}
          onAcquisito={cedolinoInserito}
        />
      </AppShell>
    </AzioniCaricamentiContext.Provider>
  );
}
