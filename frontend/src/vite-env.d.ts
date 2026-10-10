/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** "true" solo in modalita' mock (sviluppo senza backend). */
  readonly VITE_USE_MOCKS?: string;
  /** Origine del backend se diversa da quella del frontend (default: stessa origine). */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
