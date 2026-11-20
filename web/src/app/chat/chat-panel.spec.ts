import { TestBed } from '@angular/core/testing';
import { provideTessera } from '@brunogutierre/tessera';
import { Subject } from 'rxjs';
import { AskApi, type AgentEvent, type Place } from '../api';
import { ChatSession } from './chat-session';
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

  it('explains each step and lists the places to pick on the map', async () => {
    const fixture = TestBed.createComponent(ChatPanel);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const place: Place = {
      id: 42,
      category: 'PARK',
      subcategory: null,
      name: 'Parc La Fontaine',
      borough: 'Le Plateau-Mont-Royal',
      props: {},
      longitude: -73.57,
      latitude: 45.52,
      geometry: { type: 'Point', coordinates: [-73.57, 45.52] },
    };
    TestBed.inject(ChatSession).ask('Parks in the Plateau?');
    stream.next({ type: 'tool_call', id: 'c1', tool: 'find_features', arguments: { category: 'PARK', borough: 'plateau' } });
    stream.next({ type: 'tool_result', id: 'c1', tool: 'find_features', result: { total: 80 } });
    stream.next({ type: 'features', resultId: 'r1', tool: 'find_features', places: [place] });
    stream.next({ type: 'done' });
    await fixture.whenStable();

    expect(element.querySelector('.trace summary')?.textContent).toBe('How I found it (1 step)');
    expect(element.querySelector('.trace li')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      'Searched for places category: PARK, borough: plateau → 80 found',
    );
    expect(element.querySelector('#results-heading')?.textContent).toBe('On the map (1)');

    const button = element.querySelector<HTMLButtonElement>('.result')!;
    button.click();
    await fixture.whenStable();
    expect(TestBed.inject(ChatSession).selected()).toBe(42);
    expect(button.getAttribute('aria-pressed')).toBe('true');
  });

  it('offers example questions that ask right away', async () => {
    const fixture = TestBed.createComponent(ChatPanel);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const examples = element.querySelectorAll<HTMLButtonElement>('.example');

    examples[2].click();
    await fixture.whenStable();

    expect(examples).toHaveLength(5);
    expect(asked).toEqual([{ text: 'Which borough has the most parks per km²?' }]);
    expect(element.querySelector('.examples')).toBeNull();
  });
});
