import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import type { Subscription } from 'rxjs';
import { AskApi, type AgentEvent, type Place, type Usage } from '../api';

/** A tool the agent called while answering, with its result once it arrives. */
export interface Step {
  id: string;
  tool: string;
  arguments: unknown;
  result?: unknown;
}

/** One question and its answer, as it streams in. */
export interface Exchange {
  question: string;
  answer: string;
  status: 'streaming' | 'done' | 'error';
  /** The server's explanation of a failure, when it gave one. */
  error?: string;
  steps: Step[];
  usage?: Usage;
}

/**
 * The conversation: sends questions, folds the answer's event stream into the latest exchange,
 * and keeps the places to draw for the latest answer.
 */
@Injectable({ providedIn: 'root' })
export class ChatSession {
  private readonly api = inject(AskApi);
  private subscription?: Subscription;

  readonly exchanges = signal<readonly Exchange[]>([]);
  readonly places = signal<readonly Place[]>([]);
  /** The place highlighted in the list and on the map. */
  readonly selected = signal<number | null>(null);
  readonly busy = computed(() => this.exchanges().at(-1)?.status === 'streaming');

  ask(question: string): void {
    const text = question.trim();
    if (!text) {
      return;
    }
    this.subscription?.unsubscribe();
    this.exchanges.update((all) => [...all, { question: text, answer: '', status: 'streaming', steps: [] }]);
    this.places.set([]);
    this.selected.set(null);
    this.subscription = this.api.ask({ text }).subscribe({
      next: (event) => this.apply(event),
      error: (error: unknown) => this.update({ status: 'error', error: problemDetail(error) }),
      complete: () => {
        // A stream that ends without "done" or "error" was cut off.
        if (this.exchanges().at(-1)?.status === 'streaming') {
          this.update({ status: 'error' });
        }
      },
    });
  }

  /** Stops the current answer; the server stops calling the model when the stream closes. */
  stop(): void {
    this.subscription?.unsubscribe();
    if (this.busy()) {
      this.update({ status: 'done' });
    }
  }

  private apply(event: AgentEvent): void {
    switch (event.type) {
      case 'token':
        this.update((last) => ({ answer: last.answer + event.text }));
        break;
      case 'tool_call':
        this.update((last) => ({
          steps: [...last.steps, { id: event.id, tool: event.tool, arguments: event.arguments }],
        }));
        break;
      case 'tool_result':
        this.update((last) => ({
          steps: last.steps.map((step) =>
            step.id === event.id ? { ...step, result: event.result } : step,
          ),
        }));
        break;
      case 'features':
        this.places.update((shown) => {
          const ids = new Set(shown.map((place) => place.id));
          return [...shown, ...event.places.filter((place) => !ids.has(place.id))];
        });
        break;
      case 'usage':
        this.update({ usage: event });
        break;
      case 'done':
        this.update({ status: 'done' });
        break;
      case 'error':
        // The server's message is a generic English one; the panel shows it in the visitor's language.
        this.update({ status: 'error' });
        break;
    }
  }

  private update(change: Partial<Exchange> | ((last: Exchange) => Partial<Exchange>)): void {
    this.exchanges.update((all) => {
      const last = all.at(-1);
      if (!last) {
        return all;
      }
      const patch = typeof change === 'function' ? change(last) : change;
      return [...all.slice(0, -1), { ...last, ...patch }];
    });
  }
}

/**
 * The `detail` of a ProblemDetail error. The stream is read as text, so the error body arrives as
 * a JSON string rather than an object.
 */
function problemDetail(error: unknown): string | undefined {
  if (!(error instanceof HttpErrorResponse) || error.status === 0) {
    return undefined;
  }
  let body: unknown = error.error;
  if (typeof body === 'string') {
    try {
      body = JSON.parse(body);
    } catch {
      return undefined;
    }
  }
  const detail = (body as { detail?: unknown } | null)?.detail;
  return typeof detail === 'string' ? detail : undefined;
}
