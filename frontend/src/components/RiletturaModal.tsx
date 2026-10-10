import { Alert, Button, Group, Modal, PasswordInput, Stack, Text } from '@mantine/core';
import { IconAlertCircle, IconLock, IconRefresh } from '@tabler/icons-react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useApiClient } from '../api/context';
import { messaggioErrore } from '../api/errors';
import { chiavi, rileggiCaricamento, useCaricamentiInErrore } from '../api/queries';
import type { CaricamentoInErrore } from '../api/types';
import type { StatoCaricamentoLotto } from '../hooks/useCaricamento';
import { testi } from '../i18n';
import { MotivoErroreBadge } from './MotivoErroreBadge';

const t = testi.rilettura;

interface Props {
  /** File in errore da rileggere; null = modale chiusa. */
  caricamentoId: number | null;
  /** Caricamento della radice: la rilettura e' un nuovo lotto, seguito come un invio. */
  caricamento: StatoCaricamentoLotto;
  onChiudi: () => void;
  /** Lotto registrato: la radice mostra la modale di caricamento con lo stato del file. */
  onAvviata: () => void;
}

/** Nuova lettura del PDF conservato di un file in errore, con la password se il PDF è protetto. */
export function RiletturaModal({ caricamentoId, caricamento, onChiudi, onAvviata }: Props) {
  const inErrore = useCaricamentiInErrore();
  const elemento = inErrore.data?.find((e) => e.id === caricamentoId);
  const protetto = elemento?.motivo === 'PDF_PROTETTO';
  const titolo = `${protetto ? t.titoloPassword : t.titolo}${elemento ? ` · ${elemento.nomeFileOriginale}` : ''}`;

  return (
    <Modal
      opened={caricamentoId !== null}
      onClose={onChiudi}
      title={
        <Group gap="xs" wrap="nowrap">
          <IconRefresh size={20} aria-hidden color="var(--mantine-color-marchio-7)" />
          <Text fw={700} size="lg">
            {titolo}
          </Text>
        </Group>
      }
      size="md"
      centered
      closeButtonProps={{ 'aria-label': testi.comune.chiudiFinestra }}
    >
      {/* smontato alla chiusura: la password resta solo in memoria e solo finche' la modale e' aperta */}
      {caricamentoId !== null && (
        <ContenutoRilettura
          key={caricamentoId}
          caricamentoId={caricamentoId}
          elemento={elemento}
          caricamento={caricamento}
          onChiudi={onChiudi}
          onAvviata={onAvviata}
        />
      )}
    </Modal>
  );
}

interface ContenutoProps extends Omit<Props, 'caricamentoId'> {
  caricamentoId: number;
  elemento: CaricamentoInErrore | undefined;
}

function ContenutoRilettura({ caricamentoId, elemento, caricamento, onChiudi, onAvviata }: ContenutoProps) {
  const api = useApiClient();
  const queryClient = useQueryClient();
  const [password, setPassword] = useState('');
  const protetto = elemento?.motivo === 'PDF_PROTETTO';
  const { segui } = caricamento;

  const rilettura = useMutation({
    mutationFn: () => rileggiCaricamento(api, caricamentoId, password.length > 0 ? password : null),
    onSuccess: (lotto) => {
      segui(lotto);
      // il file risulta subito "in elaborazione" nell'elenco; alla fine del lotto viene riletto con il resto
      void queryClient.invalidateQueries({ queryKey: chiavi.caricamentiInErrore });
      onAvviata();
    },
  });

  const occupato = caricamento.inElaborazione;
  const campoPassword = (
    <PasswordInput
      label={protetto ? t.password : t.passwordFacoltativa}
      description={protetto ? undefined : t.passwordDescrizione}
      value={password}
      onChange={(evento) => setPassword(evento.currentTarget.value)}
      leftSection={<IconLock size={16} aria-hidden />}
      visibilityToggleButtonProps={{ 'aria-label': t.mostraPassword }}
      autoComplete="off"
      maxLength={200}
      data-autofocus={protetto || undefined}
      disabled={rilettura.isPending}
    />
  );

  return (
    <form
      onSubmit={(evento) => {
        evento.preventDefault();
        if (!occupato) {
          rilettura.mutate();
        }
      }}
    >
      <Stack gap="md">
        {elemento && (
          <Group gap="xs">
            <MotivoErroreBadge motivo={elemento.motivo} />
          </Group>
        )}
        {protetto ? (
          <>
            {campoPassword}
            <Text size="sm" c="dimmed">
              {t.spiegazionePassword} {t.spiegazione}
            </Text>
          </>
        ) : (
          <>
            <Text size="sm">{t.spiegazione}</Text>
            {campoPassword}
          </>
        )}
        {occupato && (
          <Alert color="orange" variant="light" icon={<IconAlertCircle aria-hidden />}>
            {t.attendiCaricamento}
          </Alert>
        )}
        {rilettura.isError && (
          <Alert
            color="red"
            variant="light"
            title={t.errore}
            icon={<IconAlertCircle aria-hidden />}
            role="alert"
          >
            {messaggioErrore(rilettura.error)}
          </Alert>
        )}
        <Group justify="flex-end" gap="sm">
          <Button variant="default" onClick={onChiudi}>
            {testi.comune.annulla}
          </Button>
          <Button
            type="submit"
            leftSection={<IconRefresh size={16} aria-hidden />}
            loading={rilettura.isPending}
            disabled={occupato}
          >
            {t.rileggi}
          </Button>
        </Group>
      </Stack>
    </form>
  );
}
