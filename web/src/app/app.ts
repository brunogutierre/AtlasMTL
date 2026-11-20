import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { TesButton, TesCard, TesseraTheme } from '@brunogutierre/tessera';
import { BoroughApi, type Place } from './api';
import { I18n } from './i18n';
import { MapView } from './map/map-view';

type Mode = 'light' | 'dark' | 'system';

/** The page frame: header, chat panel, map and data attribution. */
@Component({
  selector: 'app-root',
  imports: [TesButton, TesCard, MapView],
  templateUrl: './app.html',
  styleUrl: './app.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  protected readonly i18n = inject(I18n);
  protected readonly theme = inject(TesseraTheme);
  protected readonly modes: readonly Mode[] = ['light', 'dark', 'system'];
  protected readonly boroughs = inject(BoroughApi).boroughsResource();
  /** The places of the current answer; the chat panel fills it. */
  protected readonly places = signal<Place[]>([]);
}
