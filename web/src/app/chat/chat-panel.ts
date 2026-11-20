import { ChangeDetectionStrategy, Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { TesAlert, TesButton, TesFormField, TesInput } from '@brunogutierre/tessera';
import { I18n } from '../i18n';
import { ChatSession, type Step } from './chat-session';

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

  /** One line per tool call: what it did, with what, and what it found. */
  protected describe(step: Step): { tool: string; arguments: string; outcome: string } {
    const t = this.i18n.t().trace;
    const result = step.result as { error?: string; total?: number; address?: string } | undefined;
    let outcome = '…';
    if (result?.error) {
      outcome = `${t.failed}: ${result.error}`;
    } else if (typeof result?.total === 'number') {
      outcome = t.found(result.total);
    } else if (result?.address) {
      outcome = result.address;
    } else if (result) {
      outcome = '✓';
    }
    const args = Object.entries((step.arguments ?? {}) as Record<string, unknown>)
      .map(([key, value]) => `${key}: ${String(value)}`)
      .join(', ');
    return { tool: t.tools[step.tool] ?? step.tool, arguments: args, outcome };
  }

  /** Enter sends; Shift+Enter starts a new line. */
  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      this.submit(event);
    }
  }
}
