import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideTessera } from '@brunogutierre/tessera';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // The same theme and mode as the attributes in index.html, so the first paint matches.
    provideTessera({ theme: 'enterprise', mode: 'system' }),
  ],
};
