import { provideHttpClient, withFetch } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideTessera } from '@brunogutierre/tessera';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withFetch()),
    provideRouter(routes),
    // The same theme and mode as the attributes in index.html, so the first paint matches.
    provideTessera({ theme: 'enterprise', mode: 'system' }),
  ],
};
