import {
  Alert,
  Button,
  Chip,
  em,
  Group,
  List,
  Modal,
  NumberInput,
  SegmentedControl,
  Select,
  Stack,
  Text,
  Tooltip,
} from '@mantine/core';
import { useMediaQuery } from '@mantine/hooks';
import { notifications } from '@mantine/notifications';
import { IconAlertCircle, IconCalendarPlus, IconLink } from '@tabler/icons-react';
import { useId, useState } from 'react';
import { erroriDiCampo, messaggioErrore } from '../api/errors';
import {
  useAssociaCaricamento,
  useCaricamentiInErrore,
  usePersone,
  useRapporti,
  useRapporto,
} from '../api/queries';
import type { CaricamentoInErrore, RigaMensilita } from '../api/types';
import { formatEtichettaRapporto, formatIstante, formatMensilita } from '../format/format';
import { chiaveMensilita } from '../hooks/useCedolinoManuale';
import { testi } from '../i18n';
import type { OpzioniAssociazione } from './azioniCaricamenti';

const t = testi.associa;

const TIPI_ALTRO_MESE = ['ORDINARIA', 'TREDICESIMA', 'QUATTORDICESIMA'] as const;
type TipoAltroMese = (typeof TIPI_ALTRO_MESE)[number];

/** Mensilita' come la vuole RichiestaAttribuzione: anno + mese 1..14 (13 tredicesima, 14 quattordicesima). */
interface MeseScelto {
  anno: number;
  mese: number;
}

/** Mese scelto con un clic tra le righe del rapporto, oppure indicato a mano con "Altro mese…". */
type Scelta = { modo: 'riga'; chiave: string } | { modo: 'altro' };

interface Props {
  /** null = modale chiusa. */
  opzioni: OpzioniAssociazione | null;
  /** Persona mostrata nella pagina, proposta come prima scelta. */
  personaPredefinita: number | null;
  onChiudi: () => void;
}

/**
 * Associazione di un file in errore alla sua mensilita': dall'elenco dei file (persona, azienda e mese da scegliere)
 * o da una mensilita' mancante (si sceglie solo il file). Il mese associato non risulta piu' mancante.
 */
export function AssociaMeseModal({ opzioni, personaPredefinita, onChiudi }: Props) {
  const inErrore = useCaricamentiInErrore();
  const elenco = inErrore.data ?? [];
  const caricamentoId = opzioni?.caricamentoId;
  const elemento = caricamentoId !== undefined ? elenco.find((e) => e.id === caricamentoId) : undefined;
  const titolo =
    caricamentoId !== undefined
      ? `${t.titoloFile}${elemento ? ` – ${elemento.nomeFileOriginale}` : ''}`
      : titoloDaMese(opzioni?.titolo);

  return (
    <Modal
      opened={opzioni !== null}
      onClose={onChiudi}
      title={
        <Group gap="xs" wrap="nowrap">
          <IconLink size={20} aria-hidden color="var(--mantine-color-marchio-7)" />
          <Text fw={700} size="lg">
            {titolo}
          </Text>
        </Group>
      }
      size="lg"
      centered
      closeButtonProps={{ 'aria-label': testi.comune.chiudiFinestra }}
    >
      {opzioni !== null && (
        <ContenutoAssocia
          key={`${String(opzioni.caricamentoId)}-${String(opzioni.rapportoId)}-${String(opzioni.chiave)}`}
          opzioni={opzioni}
          elenco={elenco}
          personaPredefinita={personaPredefinita}
          onChiudi={onChiudi}
        />
      )}
    </Modal>
  );
}

/** Davanti a un titolo che inizia per "a" la preposizione prende la d eufonica: "ad Agosto 2023". */
function conDEufonica(titolo: string): boolean {
  return /^a/i.test(titolo);
}

/** "Associa un file in errore a Maggio 2023" / "… ad Agosto 2023". */
function titoloDaMese(titolo: string | undefined): string {
  if (!titolo) {
    return t.titoloMese;
  }
  return `${conDEufonica(titolo) ? t.titoloMeseAd : t.titoloMeseA} ${titolo}`;
}

/** "2023.05" => anno 2023, mese 5; null per le chiavi di fine rapporto (non associabili). */
function meseDaChiave(chiave: string | undefined): MeseScelto | null {
  const match = chiave ? /^(\d{4})\.(\d{2})$/.exec(chiave) : null;
  return match ? { anno: Number(match[1]), mese: Number(match[2]) } : null;
}

function meseDaRiga(riga: RigaMensilita | undefined): MeseScelto | null {
  return riga?.competenza.mese != null ? { anno: riga.competenza.anno, mese: riga.competenza.mese } : null;
}

/** Mese indicato con "Altro mese…", se completo e plausibile. */
function meseAltro(tipo: TipoAltroMese, mese: string | null, anno: string | number): MeseScelto | null {
  if (typeof anno !== 'number' || !Number.isInteger(anno) || anno < 1900 || anno > 2999) {
    return null;
  }
  switch (tipo) {
    case 'TREDICESIMA':
      return { anno, mese: 13 };
    case 'QUATTORDICESIMA':
      return { anno, mese: 14 };
    case 'ORDINARIA':
      return mese !== null ? { anno, mese: Number(mese) } : null;
  }
}

/** Una mensilita' gia' presente o gia' associata a un altro file non si puo' scegliere (il backend la rifiuta). */
function conflitto(riga: RigaMensilita | undefined, caricamentoId: number | null): string | null {
  if (riga?.stato === 'PRESENTE') {
    return `${t.giaPresente} ${riga.titolo}`;
  }
  if (riga?.stato === 'IN_ERRORE' && riga.caricamentoId !== caricamentoId) {
    return `${t.giaAssociato} ${riga.titolo}`;
  }
  return null;
}

interface ContenutoProps {
  opzioni: OpzioniAssociazione;
  elenco: CaricamentoInErrore[];
  personaPredefinita: number | null;
  onChiudi: () => void;
}

function ContenutoAssocia({ opzioni, elenco, personaPredefinita, onChiudi }: ContenutoProps) {
  const associa = useAssociaCaricamento();
  const daFile = opzioni.caricamentoId !== undefined;
  const elemento = elenco.find((e) => e.id === opzioni.caricamentoId);
  const attuale = elemento?.attribuzione ?? null;
  const idMese = useId();
  const stretto = useMediaQuery(`(max-width: ${em(480)})`);

  // file: fissato dall'elenco, da scegliere tra quelli non associati se si parte da una mensilita'
  const associabili = elenco.filter((e) => e.attribuzione === null && !e.inElaborazione);
  const [fileScelto, setFileScelto] = useState<number | null>(null);
  const caricamentoId =
    opzioni.caricamentoId ?? fileScelto ?? (associabili.length === 1 ? (associabili[0]?.id ?? null) : null);

  // persona e azienda (solo partendo dal file); un'unica possibilita' e' gia' scelta
  const persone = usePersone();
  const elencoPersone = persone.data ?? [];
  const [personaScelta, setPersonaScelta] = useState<number | null>(attuale?.personaId ?? personaPredefinita);
  const personaId = !daFile
    ? null
    : elencoPersone.some((p) => p.id === personaScelta)
      ? personaScelta
      : elencoPersone.length === 1
        ? (elencoPersone[0]?.id ?? null)
        : null;
  const rapporti = useRapporti(personaId);
  const elencoRapporti = rapporti.data ?? [];
  const [rapportoScelto, setRapportoScelto] = useState<number | null>(attuale?.rapportoId ?? null);
  const rapportoId = !daFile
    ? (opzioni.rapportoId ?? null)
    : elencoRapporti.some((r) => r.id === rapportoScelto)
      ? rapportoScelto
      : elencoRapporti.length === 1
        ? (elencoRapporti[0]?.id ?? null)
        : null;

  // mese: righe mancanti (o gia' associate) del rapporto, oppure "Altro mese…"
  const rapporto = useRapporto(rapportoId ?? 0, rapportoId !== null);
  const righe = rapportoId !== null ? (rapporto.data?.mensilita ?? []) : [];
  const [scelta, setScelta] = useState<Scelta | null>(
    attuale ? { modo: 'riga', chiave: attuale.competenza.chiave } : null,
  );
  const [tipoAltro, setTipoAltro] = useState<TipoAltroMese>('ORDINARIA');
  const [meseAltroScelto, setMeseAltroScelto] = useState<string | null>(null);
  const [annoAltro, setAnnoAltro] = useState<string | number>('');

  const meseScelto: MeseScelto | null = !daFile
    ? meseDaChiave(opzioni.chiave)
    : scelta?.modo === 'riga'
      ? meseDaRiga(righe.find((r) => r.chiave === scelta.chiave))
      : scelta?.modo === 'altro'
        ? meseAltro(tipoAltro, meseAltroScelto, annoAltro)
        : null;
  const rigaScelta = meseScelto
    ? righe.find((r) => r.chiave === chiaveMensilita(meseScelto.anno, meseScelto.mese))
    : undefined;
  const motivoConflitto = conflitto(rigaScelta, caricamentoId);
  const selezionabili = righe.filter((r) => r.stato === 'MANCANTE' || r.stato === 'IN_ERRORE');
  const pronto =
    caricamentoId !== null && rapportoId !== null && meseScelto !== null && motivoConflitto === null;

  const salva = () => {
    if (!pronto) {
      return;
    }
    associa.mutate(
      { caricamentoId, attribuzione: { rapportoId, anno: meseScelto.anno, mese: meseScelto.mese } },
      {
        onSuccess: (aggiornato) => {
          const titolo = aggiornato.attribuzione?.titolo ?? formatMensilita(meseScelto.anno, meseScelto.mese);
          notifications.show({
            color: 'teal',
            message: `${conDEufonica(titolo) ? t.associatoAd : t.associatoA} ${titolo}`,
          });
          onChiudi();
        },
      },
    );
  };

  const errori = erroriDiCampo(associa.error);

  return (
    <Stack gap="md">
      <Text size="sm" c="dimmed">
        {t.descrizione}
      </Text>

      {!daFile &&
        (associabili.length === 0 ? (
          <Text size="sm">{t.nessunFile}</Text>
        ) : (
          <Select
            label={t.file}
            placeholder={t.filePlaceholder}
            data={associabili.map((e) => ({
              value: String(e.id),
              label: `${e.nomeFileOriginale} · ${formatIstante(e.creatoIl)}`,
            }))}
            value={caricamentoId !== null ? String(caricamentoId) : null}
            onChange={(valore) => setFileScelto(valore ? Number(valore) : null)}
            allowDeselect={false}
            data-autofocus
          />
        ))}

      {daFile &&
        (persone.isSuccess && elencoPersone.length === 0 ? (
          <Alert color="orange" variant="light" icon={<IconAlertCircle aria-hidden />}>
            {t.personaAssente}
          </Alert>
        ) : (
          <>
            <Select
              label={t.persona}
              description={t.personaDescrizione}
              placeholder={t.personaPlaceholder}
              data={elencoPersone.map((p) => ({ value: String(p.id), label: p.etichetta }))}
              value={personaId !== null ? String(personaId) : null}
              onChange={(valore) => {
                setPersonaScelta(valore ? Number(valore) : null);
                setRapportoScelto(null);
                setScelta(null);
              }}
              searchable={elencoPersone.length > 8}
              allowDeselect={false}
              data-autofocus
            />
            {personaId !== null && (
              <Select
                label={t.rapporto}
                placeholder={rapporti.isPending ? t.caricamento : t.rapportoPlaceholder}
                data={elencoRapporti.map((r) => ({
                  value: String(r.id),
                  label: formatEtichettaRapporto(r.etichetta, r.periodo),
                }))}
                value={rapportoId !== null ? String(rapportoId) : null}
                onChange={(valore) => {
                  setRapportoScelto(valore ? Number(valore) : null);
                  setScelta(null);
                }}
                allowDeselect={false}
                disabled={rapporti.isPending}
              />
            )}
          </>
        ))}

      {daFile && rapportoId !== null && (
        <div role="group" aria-labelledby={idMese}>
          <Text size="sm" fw={500} id={idMese} mb={6}>
            {t.mese}
          </Text>
          {rapporto.isPending ? (
            <Text size="sm" c="dimmed">
              {t.caricamento}
            </Text>
          ) : (
            <Stack gap="sm">
              {selezionabili.length === 0 ? (
                <Text size="sm" c="dimmed">
                  {t.nessunMancante}
                </Text>
              ) : (
                <Chip.Group
                  value={scelta?.modo === 'riga' ? scelta.chiave : null}
                  onChange={(chiave) => setScelta({ modo: 'riga', chiave })}
                >
                  <Group gap={6}>
                    {selezionabili.map((riga) => (
                      <ChipMese key={riga.chiave} riga={riga} caricamentoId={caricamentoId} />
                    ))}
                  </Group>
                </Chip.Group>
              )}
              <div>
                <Button
                  size="xs"
                  variant={scelta?.modo === 'altro' ? 'light' : 'subtle'}
                  leftSection={<IconCalendarPlus size={14} aria-hidden />}
                  aria-expanded={scelta?.modo === 'altro'}
                  onClick={() => setScelta({ modo: 'altro' })}
                >
                  {t.altroMese}
                </Button>
              </div>
              {scelta?.modo === 'altro' && (
                <Stack gap="xs">
                  <SegmentedControl
                    aria-label={t.tipo}
                    data={TIPI_ALTRO_MESE.map((tipo) => ({ value: tipo, label: t.tipi[tipo] }))}
                    value={tipoAltro}
                    onChange={(valore) => {
                      const tipo = TIPI_ALTRO_MESE.find((v) => v === valore);
                      if (tipo) {
                        setTipoAltro(tipo);
                      }
                    }}
                    orientation={stretto ? 'vertical' : 'horizontal'}
                    fullWidth={stretto}
                    size="xs"
                  />
                  <Group gap="sm" align="flex-start" wrap="wrap">
                    {tipoAltro === 'ORDINARIA' && (
                      <Select
                        label={t.meseCalendario}
                        placeholder={t.meseCalendarioPlaceholder}
                        data={testi.mesi.map((nome, indice) => ({ value: String(indice + 1), label: nome }))}
                        value={meseAltroScelto}
                        onChange={setMeseAltroScelto}
                        allowDeselect={false}
                        w={180}
                      />
                    )}
                    <NumberInput
                      label={t.anno}
                      value={annoAltro}
                      onChange={setAnnoAltro}
                      allowDecimal={false}
                      allowNegative={false}
                      min={1900}
                      max={2999}
                      clampBehavior="none"
                      hideControls
                      inputMode="numeric"
                      w={110}
                    />
                  </Group>
                </Stack>
              )}
            </Stack>
          )}
        </div>
      )}

      {motivoConflitto !== null && (
        <Text size="sm" c="red.8" role="status">
          {motivoConflitto}
        </Text>
      )}

      {associa.isError && (
        <Alert
          color="red"
          variant="light"
          title={t.errore}
          icon={<IconAlertCircle aria-hidden />}
          role="alert"
        >
          {errori.length > 1 ? (
            <List size="sm" spacing={2}>
              {errori.map((e) => (
                <List.Item key={`${String(e.campo)}-${e.messaggio}`}>{e.messaggio}</List.Item>
              ))}
            </List>
          ) : (
            messaggioErrore(associa.error)
          )}
        </Alert>
      )}

      <Group justify="flex-end" gap="sm">
        <Button variant="default" onClick={onChiudi}>
          {testi.comune.annulla}
        </Button>
        <Button
          leftSection={<IconLink size={16} aria-hidden />}
          disabled={!pronto}
          loading={associa.isPending}
          onClick={salva}
        >
          {testi.comune.salva}
        </Button>
      </Group>
    </Stack>
  );
}

/** Mensilita' scelta con un clic; quelle gia' associate a un altro file sono disabilitate e spiegate. */
function ChipMese({ riga, caricamentoId }: { riga: RigaMensilita; caricamentoId: number | null }) {
  const occupata = riga.stato === 'IN_ERRORE' && riga.caricamentoId !== caricamentoId;
  const chip = (
    <Chip value={riga.chiave} size="sm" disabled={occupata} color="marchio">
      {riga.titolo}
    </Chip>
  );
  return occupata ? (
    <Tooltip label={`${t.giaAssociato} ${riga.titolo}`}>
      <span>{chip}</span>
    </Tooltip>
  ) : (
    chip
  );
}
