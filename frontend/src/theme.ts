import { createTheme, type MantineColorsTuple } from '@mantine/core';

/** Blu-petrolio del marchio (logo e azioni principali); l'arancione e' riservato agli avvisi. */
const marchio: MantineColorsTuple = [
  '#e8f4fb',
  '#d3e6f2',
  '#a3cbe6',
  '#70aed9',
  '#4796ce',
  '#2e87c8',
  '#1f7fc6',
  '#106caf',
  '#00609d',
  '#00538b',
];

export const theme = createTheme({
  primaryColor: 'marchio',
  primaryShade: 7,
  colors: { marchio },
  defaultRadius: 'md',
  fontFamily:
    'Inter, "Segoe UI", system-ui, -apple-system, Roboto, "Helvetica Neue", Arial, "Noto Sans", sans-serif',
  fontFamilyMonospace: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
  headings: {
    fontWeight: '650',
  },
  focusRing: 'auto',
  cursorType: 'pointer',
  components: {
    Tooltip: {
      defaultProps: {
        withArrow: true,
        multiline: true,
        maw: 320,
        openDelay: 150,
        // visibili anche con la tastiera (focus), non solo al passaggio del mouse
        events: { hover: true, focus: true, touch: true },
      },
    },
    Paper: {
      defaultProps: { radius: 'md' },
    },
    Card: {
      defaultProps: { radius: 'md', withBorder: true, padding: 'lg' },
    },
  },
});
