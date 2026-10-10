import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { defineConfig, devices } from '@playwright/test';

/**
 * Suite end-to-end facoltativa (`npm run e2e`), esclusa dal build e da `npm test`: avvia il backend in modalità
 * demo (lettore a fixture, nessuna lettura reale dei PDF) con un database H2 nuovo in una cartella temporanea e il
 * server di sviluppo Vite con il proxy /api verso quel backend, poi esegue il flusso completo nel browser.
 * Il backend ascolta solo su 127.0.0.1 e non legge il file .env dello sviluppatore (SAM_ENV_FILE punta a un file
 * inesistente nella cartella temporanea): l'esito non dipende dalla configurazione locale.
 *
 * Variabili d'ambiente facoltative:
 * - SAM_E2E_BACKEND_PORT / SAM_E2E_FRONTEND_PORT: porte (default 18080 e 15173);
 * - SAM_E2E_BASE_URL: usa uno stack già avviato (con database vuoto) invece di avviarlo;
 * - SAM_E2E_CHROMIUM: eseguibile di Chromium da usare al posto di quello installato da Playwright;
 * - SAM_E2E_SCREENSHOT_DIR: cartella in cui salvare gli screenshot del flusso (desktop e mobile).
 */
const portaBackend = Number(process.env.SAM_E2E_BACKEND_PORT ?? '18080');
const portaFrontend = Number(process.env.SAM_E2E_FRONTEND_PORT ?? '15173');
const stackEsterno = process.env.SAM_E2E_BASE_URL;
const baseURL = stackEsterno ?? `http://localhost:${portaFrontend}`;
const urlBackend = `http://127.0.0.1:${portaBackend}`;
const chromium = process.env.SAM_E2E_CHROMIUM;
// la configurazione viene caricata anche dai processi worker: la cartella del database si crea una sola volta nel
// processo principale e si passa ai worker (e alla pulizia finale) tramite l'ambiente
if (!stackEsterno && !process.env.SAM_E2E_CARTELLA_DB) {
  process.env.SAM_E2E_CARTELLA_DB = mkdtempSync(join(tmpdir(), 'sam-e2e-'));
}
const cartellaDb = process.env.SAM_E2E_CARTELLA_DB ?? '';
const mvnw = process.platform === 'win32' ? 'mvnw.cmd' : './mvnw';

export default defineConfig({
  testDir: './e2e',
  globalTeardown: './e2e/pulizia.ts',
  // il flusso costruisce i dati passo dopo passo su un unico backend: un solo worker, test in sequenza
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 120_000,
  expect: { timeout: 15_000 },
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  outputDir: 'test-results',
  use: {
    baseURL,
    locale: 'it-IT',
    timezoneId: 'Europe/Rome',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1366, height: 900 },
        ...(chromium ? { launchOptions: { executablePath: chromium } } : {}),
      },
    },
  ],
  webServer: stackEsterno
    ? undefined
    : [
        {
          command: `${mvnw} -B spring-boot:run`,
          cwd: '../backend',
          url: `${urlBackend}/api/v1/ccnl`,
          timeout: 300_000,
          reuseExistingServer: false,
          stdout: 'ignore',
          stderr: 'pipe',
          env: {
            SAM_LETTORE: 'fixture',
            SAM_PORT: String(portaBackend),
            SAM_SERVER_ADDRESS: '127.0.0.1',
            SAM_ENV_FILE: join(cartellaDb, 'nessun-file.env'),
            SAM_DB_PATH: join(cartellaDb, 'salary-manager'),
            SAM_CORS_ORIGINS: `http://localhost:${portaFrontend}`,
          },
        },
        {
          command: `npx vite --port ${portaFrontend} --strictPort`,
          url: `http://localhost:${portaFrontend}`,
          timeout: 60_000,
          reuseExistingServer: false,
          env: { SAM_BACKEND_URL: urlBackend },
        },
      ],
});
