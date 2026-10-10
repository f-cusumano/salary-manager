import { Button, Group, Image, Loader, Select, Title } from '@mantine/core';
import { IconUpload } from '@tabler/icons-react';
import { usePersone } from '../api/queries';
import { testi } from '../i18n';
import classes from './TopBar.module.css';

interface Props {
  personaId: number | null;
  onSelezionaPersona: (id: number | null) => void;
  onCaricaCedolini: () => void;
  caricamentoInCorso: boolean;
}

/** Barra superiore: logo e nome a sinistra, tendina persone e pulsante di caricamento a destra. */
export function TopBar({ personaId, onSelezionaPersona, onCaricaCedolini, caricamentoInCorso }: Props) {
  const persone = usePersone();
  const opzioni = (persone.data ?? []).map((p) => ({ value: String(p.id), label: p.etichetta }));
  const iconaCarica = caricamentoInCorso ? (
    <Loader size={16} color="white" aria-label={testi.topBar.caricamentoInCorso} />
  ) : (
    <IconUpload size={18} aria-hidden />
  );

  return (
    <Group h="100%" px={{ base: 'sm', sm: 'lg' }} justify="space-between" wrap="nowrap" gap="sm">
      <Group gap="sm" wrap="nowrap" className={classes.marchio}>
        <Image
          src={`${import.meta.env.BASE_URL}logo.png`}
          alt={testi.app.logoAlt}
          w={40}
          h={40}
          fit="contain"
        />
        <Title order={1} className={classes.nome} visibleFrom="xs">
          {testi.app.nome}
        </Title>
      </Group>
      <Group gap="sm" wrap="nowrap">
        <Select
          className={classes.persona}
          aria-label={testi.topBar.personaAria}
          placeholder={testi.topBar.selezionaPersona}
          data={opzioni}
          value={personaId !== null ? String(personaId) : null}
          onChange={(valore) => onSelezionaPersona(valore ? Number(valore) : null)}
          nothingFoundMessage={testi.topBar.nessunaPersona}
          searchable={opzioni.length > 8}
          clearable
          allowDeselect={false}
          // il motivo e il pulsante per riprovare sono nell'area principale (vedi App)
          error={persone.isError}
          comboboxProps={{ shadow: 'md' }}
          classNames={{ input: classes.personaInput }}
        />
        <Button
          onClick={onCaricaCedolini}
          aria-label={testi.topBar.caricaCedolini}
          aria-busy={caricamentoInCorso}
          className={classes.carica}
          px={{ base: 10, sm: 'md' }}
        >
          {iconaCarica}
          <span className={classes.testoCarica}>{testi.topBar.caricaCedolini}</span>
        </Button>
      </Group>
    </Group>
  );
}
