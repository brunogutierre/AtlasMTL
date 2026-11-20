import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TesCard } from '@brunogutierre/tessera';
import { BoroughApi } from '../api';
import { ChatPanel } from '../chat/chat-panel';
import { ChatSession } from '../chat/chat-session';
import { I18n } from '../i18n';
import { MapView } from '../map/map-view';

/** The main page: the conversation next to the map of its answer. */
@Component({
  selector: 'app-home',
  imports: [TesCard, MapView, ChatPanel],
  templateUrl: './home.html',
  styleUrl: './home.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Home {
  protected readonly i18n = inject(I18n);
  protected readonly boroughs = inject(BoroughApi).boroughsResource();
  /** The latest answer's places, and the one highlighted in the list and on the map. */
  protected readonly session = inject(ChatSession);
}
