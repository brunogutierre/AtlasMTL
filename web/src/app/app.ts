import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TesButton, TesCard, TesseraTheme } from '@brunogutierre/tessera';
import { I18n } from './i18n';

type Mode = 'light' | 'dark' | 'system';

/** The page frame: header, chat panel, map and data attribution. */
@Component({
  selector: 'app-root',
  imports: [TesButton, TesCard],
  templateUrl: './app.html',
  styleUrl: './app.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  protected readonly i18n = inject(I18n);
  protected readonly theme = inject(TesseraTheme);
  protected readonly modes: readonly Mode[] = ['light', 'dark', 'system'];
}
