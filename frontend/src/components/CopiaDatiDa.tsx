import { Button, Collapse, Group, Paper, Select, Stack, Text } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconChevronDown, IconChevronUp, IconCopy } from '@tabler/icons-react';
import { useId, useState } from 'react';
import { messaggioErrore } from '../api/errors';
import { useCedolino, usePersone, useRapporti, useRapporto } from '../api/queries';
import { formatEtichettaRapporto } from '../format/format';
import { daCedolinoRiferimento, daRapporto, periodoDi, rigaDiRiferimento } from '../hooks/useCedolinoManuale';
import { testi } from '../i18n';
import { copiaSeVuoti, notaCopia, useModulo, useValoreModulo, type FonteCopia } from './moduloCedolino';

const t = testi.manuale;

interface Props {
  /** Persona e rapporto proposti (quelli del mese associato al file). */
  personaIniziale: number | null;
  rapportoIniziale: number | null;
}

/**
 * "Copia dati da…": compila i soli campi vuoti con anagrafiche e assunzione di un rapporto gia' presente e con i dati
 * che si ripetono dal suo cedolino piu' vicino al periodo indicato.
 */
export function CopiaDatiDa({ personaIniziale, rapportoIniziale }: Props) {
  const { form, aggiungiCopiati } = useModulo();
  const [aperta, { toggle }] = useDisclosure(false);
  const idPannello = useId();
  const persone = usePersone();
  const elencoPersone = persone.data ?? [];
  const [personaScelta, setPersonaScelta] = useState<number | null>(personaIniziale);
  const personaId = elencoPersone.some((p) => p.id === personaScelta) ? personaScelta : null;
  const rapporti = useRapporti(aperta ? personaId : null);
  const elencoRapporti = rapporti.data ?? [];
  const [rapportoScelto, setRapportoScelto] = useState<number | null>(rapportoIniziale);
  const rapportoId = elencoRapporti.some((r) => r.id === rapportoScelto)
    ? rapportoScelto
    : elencoRapporti.length === 1
      ? (elencoRapporti[0]?.id ?? null)
      : null;
  const rapporto = useRapporto(rapportoId ?? 0, aperta && rapportoId !== null);
  // cedolino di riferimento vicino al periodo indicato nel modulo
  const meseIndicato = useValoreModulo((v) => {
    const periodo = periodoDi(v.periodo);
    return periodo ? periodo.anno * 100 + periodo.mese : null;
  });
  const periodo =
    meseIndicato !== null ? { anno: Math.floor(meseIndicato / 100), mese: meseIndicato % 100 } : null;
  const riga = rapporto.data ? rigaDiRiferimento(rapporto.data.mensilita, periodo) : null;
  const cedolino = useCedolino(riga?.cedolinoId ?? 0, aperta && riga?.cedolinoId != null);
  const pronto = rapporto.isSuccess && (riga === null || !cedolino.isPending);
  const errore = rapporto.error ?? cedolino.error;
  const [esito, setEsito] = useState<string | null>(null);

  const copia = () => {
    if (!rapporto.data) {
      return;
    }
    const fonti: FonteCopia[] = [
      { dati: daRapporto(rapporto.data), nota: notaCopia(rapporto.data.etichetta) },
    ];
    if (riga && cedolino.data) {
      fonti.push({
        dati: daCedolinoRiferimento(cedolino.data, periodo?.anno ?? null),
        nota: notaCopia(riga.titolo),
      });
    }
    const { valori, copiati } = copiaSeVuoti(form.getValues(), fonti);
    const quanti = Object.keys(copiati).length;
    if (quanti === 0) {
      setEsito(t.copiaNessunCampo);
      return;
    }
    form.setValues(valori);
    aggiungiCopiati(copiati);
    setEsito(`${t.copiaFatta}: ${String(quanti)}`);
  };

  return (
    <Paper withBorder radius="md" p="xs">
      <Button
        variant="subtle"
        size="xs"
        leftSection={<IconCopy size={14} aria-hidden />}
        rightSection={
          aperta ? <IconChevronUp size={14} aria-hidden /> : <IconChevronDown size={14} aria-hidden />
        }
        aria-expanded={aperta}
        aria-controls={idPannello}
        onClick={toggle}
      >
        {t.copiaDa}
      </Button>
      <Collapse expanded={aperta} id={idPannello} transitionDuration={0}>
        <Stack gap="xs" p="xs">
          <Text size="xs" c="dimmed">
            {t.copiaDescrizione}
          </Text>
          <Group gap="sm" align="flex-end" wrap="wrap">
            <Select
              label={t.copiaPersona}
              placeholder={t.copiaPersonaPlaceholder}
              data={elencoPersone.map((p) => ({ value: String(p.id), label: p.etichetta }))}
              value={personaId !== null ? String(personaId) : null}
              onChange={(valore) => {
                setPersonaScelta(valore ? Number(valore) : null);
                setRapportoScelto(null);
                setEsito(null);
              }}
              searchable={elencoPersone.length > 8}
              allowDeselect={false}
              style={{ flex: '1 1 12rem' }}
            />
            <Select
              label={t.copiaRapporto}
              placeholder={t.copiaRapportoPlaceholder}
              data={elencoRapporti.map((r) => ({
                value: String(r.id),
                label: formatEtichettaRapporto(r.etichetta, r.periodo),
              }))}
              value={rapportoId !== null ? String(rapportoId) : null}
              onChange={(valore) => {
                setRapportoScelto(valore ? Number(valore) : null);
                setEsito(null);
              }}
              allowDeselect={false}
              disabled={personaId === null}
              style={{ flex: '1 1 14rem' }}
            />
            <Button
              variant="light"
              size="sm"
              onClick={copia}
              disabled={rapportoId === null || !pronto}
              loading={rapportoId !== null && !pronto && errore === null}
            >
              {t.copia}
            </Button>
          </Group>
          {errore !== null && (
            <Text size="sm" c="red.8" role="alert">
              {t.copiaErrore}: {messaggioErrore(errore)}
            </Text>
          )}
          <Text size="sm" c="teal.8" role="status">
            {esito}
          </Text>
        </Stack>
      </Collapse>
    </Paper>
  );
}
