import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TesButton, TesCard, TesseraTheme } from '@brunogutierre/tessera';
import { BoroughApi } from './api';
import { ChatPanel } from './chat/chat-panel';
import { ChatSession } from './chat/chat-session';
import { I18n } from './i18n';
import { MapView } from './map/map-view';

type Mode = 'light' | 'dark' | 'system';

/** The page frame: header, chat panel, map and data attribution. */
@Component({
  selector: 'app-root',
  imports: [TesButton, TesCard, MapView, ChatPanel],
  templateUrl: './app.html',
  styleUrl: './app.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  protected readonly i18n = inject(I18n);
  protected readonly theme = inject(TesseraTheme);
  protected readonly modes: readonly Mode[] = ['light', 'dark', 'system'];
  protected readonly boroughs = inject(BoroughApi).boroughsResource();
  /** The latest answer's places, and the one highlighted in the list and on the map. */
  protected readonly session = inject(ChatSession);
}
