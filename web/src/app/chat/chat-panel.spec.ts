import { TestBed } from '@angular/core/testing';
import { provideTessera } from '@brunogutierre/tessera';
import { Subject } from 'rxjs';
import { AskApi, type AgentEvent } from '../api';
import { I18n } from '../i18n';
import { ChatPanel } from './chat-panel';

describe('ChatPanel', () => {
  let stream: Subject<AgentEvent>;
  let asked: { text: string }[];

  beforeEach(() => {
    asked = [];
    TestBed.configureTestingModule({
      providers: [
        provideTessera({ theme: 'enterprise', mode: 'light' }),
        {
          provide: AskApi,
          useValue: {
            ask: (question: { text: string }) => {
              asked.push(question);
              stream = new Subject<AgentEvent>();
              return stream;
            },
          },
        },
      ],
    });
    TestBed.inject(I18n).language.set('en');
  });

  it('sends the question on Enter and streams the answer in', async () => {
    const fixture = TestBed.createComponent(ChatPanel);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const textarea = element.querySelector('textarea')!;

    textarea.value = 'Parks in Verdun?';
    textarea.dispatchEvent(new Event('input'));
    textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', cancelable: true }));
    await fixture.whenStable();
    expect(asked).toEqual([{ text: 'Parks in Verdun?' }]);
    expect(element.querySelector('.thinking')?.textContent).toContain('Looking it up');
    expect(element.querySelector('.answer')?.getAttribute('aria-live')).toBe('polite');

    stream.next({ type: 'token', text: 'Verdun has 41 parks.' });
    stream.next({ type: 'done' });
    await fixture.whenStable();
    expect(element.querySelector('.answer p')?.textContent).toBe('Verdun has 41 parks.');
    expect(textarea.value).toBe('');
  });

  it('shows a failed answer as an alert', async () => {
    const fixture = TestBed.createComponent(ChatPanel);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const textarea = element.querySelector('textarea')!;
    textarea.value = 'Anything';
    textarea.dispatchEvent(new Event('input'));
    (element.querySelector('form') as HTMLFormElement).requestSubmit();

    stream.next({ type: 'error', message: 'The assistant couldn’t answer.' });
    await fixture.whenStable();

    expect(element.querySelector('tes-alert')?.textContent).toContain('The assistant couldn’t answer. Please try again.');
  });
});
