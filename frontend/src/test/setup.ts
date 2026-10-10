import '@testing-library/jest-dom/vitest';
import { notifications } from '@mantine/notifications';
import { cleanup } from '@testing-library/react';
import { File as FileNode } from 'node:buffer';
import { afterEach } from 'vitest';

// jsdom sostituisce File e FormData con le proprie implementazioni, che il fetch di Node (usato da Request) non
// riconosce: i nomi dei file caricati andrebbero persi. Nei test si usano quelle native di Node.
const FormDataNode = (
  await new Response('', { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }).formData()
).constructor as typeof FormData;
Object.assign(globalThis, { File: FileNode, FormData: FormDataNode });

// API del browser assenti in jsdom ma usate da Mantine
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => undefined,
    removeListener: () => undefined,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    dispatchEvent: () => false,
  }),
});

class ResizeObserverFinto {
  observe() {
    return undefined;
  }
  unobserve() {
    return undefined;
  }
  disconnect() {
    return undefined;
  }
}
window.ResizeObserver = ResizeObserverFinto;
window.HTMLElement.prototype.scrollIntoView = () => undefined;
// document.fonts (FontFaceSet) e' usato dalla Textarea autosize
Object.defineProperty(document, 'fonts', {
  configurable: true,
  value: {
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    ready: Promise.resolve(),
  },
});

afterEach(() => {
  cleanup();
  // lo store delle notifiche e' globale: senza pulizia la coda di un test passerebbe al successivo
  notifications.clean();
  window.localStorage.clear();
});
