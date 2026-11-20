import { ChangeDetectionStrategy, Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { TesAlert, TesButton, TesFormField, TesInput } from '@brunogutierre/tessera';
import { I18n } from '../i18n';
import { ChatSession } from './chat-session';

/** The conversation and the question form. */
@Component({
  selector: 'app-chat-panel',
  imports: [TesAlert, TesButton, TesFormField, TesInput],
  templateUrl: './chat-panel.html',
  styleUrl: './chat-panel.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatPanel {
  protected readonly session = inject(ChatSession);
  protected readonly i18n = inject(I18n);
  protected readonly draft = signal('');
  protected readonly maxLength = 500;
  private readonly textarea = viewChild.required('question', {
    read: ElementRef<HTMLTextAreaElement>,
  });

  protected submit(event?: Event): void {
    event?.preventDefault();
    if (this.session.busy() || !this.draft().trim()) {
      return;
    }
    this.session.ask(this.draft());
    this.draft.set('');
    // The [value] binding may not see a change when typing and sending happen in one tick.
    this.textarea().nativeElement.value = '';
  }

  /** Enter sends; Shift+Enter starts a new line. */
  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      this.submit(event);
    }
  }
}
