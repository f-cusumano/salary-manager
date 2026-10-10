import { AppShell, Container, Stack } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { notifications } from '@mantine/notifications';
import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useRef, useState } from 'react';
import { chiavi, usePersone } from './api/queries';
import type { LottoCaricamento } from './api/types';
import { CaricamentoModal } from './components/CaricamentoModal';
import { ErroreConRiprova } from './components/ErroreConRiprova';
import { RapportiList } from './components/RapportiList';
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
  return [...conteggi.entries()]
    .map(
      ([stato, numero]) =>
        `${testi.caricamento.stato[stato as keyof typeof testi.caricamento.stato]}: ${numero}`,
    )
    .join(' · ');
}

export function App({ intervalloPollingMs = INTERVALLO_POLLING_MS }: Props) {
  const queryClient = useQueryClient();
  const persone = usePersone();
  const [personaScelta, setPersonaScelta] = useState<number | null>(leggiPersonaRicordata);
  const [modaleAperta, modale] = useDisclosure(false);

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
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: chiavi.persone }),
        queryClient.invalidateQueries({ queryKey: chiavi.rapportiTutti }),
        queryClient.invalidateQueries({ queryKey: chiavi.rapportoTutti }),
        // un nuovo cedolino puo' riclassificare quelli gia' presenti (progressivo TFR e nome del file)
        queryClient.invalidateQueries({ queryKey: chiavi.cedolinoTutti }),
      ]);
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

  const apriModale = () => {
    if (caricamento.lotto?.completato) {
      caricamento.azzera();
    }
    modale.open();
  };

  return (
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
            {personaId !== null && <RapportiList key={personaId} personaId={personaId} />}
          </Stack>
        </Container>
      </AppShell.Main>
      <CaricamentoModal aperta={modaleAperta} onChiudi={modale.close} caricamento={caricamento} />
    </AppShell>
  );
}
