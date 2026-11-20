import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { AskApi, type AgentEvent, type Place } from '../api';
import { ChatSession } from './chat-session';

const school: Place = {
  id: 1,
  category: 'SCHOOL',
  subcategory: 'primary',
  name: 'École Alpha',
  borough: 'Le Plateau-Mont-Royal',
  props: {},
  longitude: -73.58,
  latitude: 45.52,
  geometry: { type: 'Point', coordinates: [-73.58, 45.52] },
};

describe('ChatSession', () => {
  let stream: Subject<AgentEvent>;
  let session: ChatSession;
  let asked: unknown[];

  beforeEach(() => {
    asked = [];
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AskApi,
          useValue: {
            ask: (question: unknown) => {
              asked.push(question);
              stream = new Subject<AgentEvent>();
              return stream;
            },
          },
        },
      ],
    });
    session = TestBed.inject(ChatSession);
  });

  it('folds the event stream into the latest exchange', () => {
    session.ask('  Schools in the Plateau?  ');
    stream.next({ type: 'tool_call', id: 'c1', tool: 'find_features', arguments: { category: 'SCHOOL' } });
    stream.next({ type: 'tool_result', id: 'c1', tool: 'find_features', result: { total: 1 } });
    stream.next({ type: 'features', resultId: 'r1', tool: 'find_features', places: [school] });
    stream.next({ type: 'token', text: 'One school' });
    stream.next({ type: 'token', text: ': École Alpha.' });
    expect(session.busy()).toBe(true);
    stream.next({ type: 'usage', inputTokens: 10, outputTokens: 5, toolCalls: 1 });
    stream.next({ type: 'done' });

    const exchange = session.exchanges()[0];
    expect(asked).toEqual([{ text: 'Schools in the Plateau?' }]);
    expect(exchange.answer).toBe('One school: École Alpha.');
    expect(exchange.steps).toEqual([
      { id: 'c1', tool: 'find_features', arguments: { category: 'SCHOOL' }, result: { total: 1 } },
    ]);
    expect(exchange.status).toBe('done');
    expect(session.places()).toEqual([school]);
    expect(session.busy()).toBe(false);
  });

  it('shows the server’s explanation of a rejected question', () => {
    session.ask('x');
    stream.error(
      new HttpErrorResponse({
        status: 400,
        error: JSON.stringify({ title: 'Invalid question', detail: 'Questions are limited to 500 characters.' }),
      }),
    );

    expect(session.exchanges()[0]).toMatchObject({
      status: 'error',
      error: 'Questions are limited to 500 characters.',
    });
  });

  it('keeps the code of a refused question for a translated message', () => {
    session.ask('x');
    stream.error(
      new HttpErrorResponse({
        status: 429,
        error: JSON.stringify({ title: 'Limit reached', detail: 'Too many questions.', code: 'rate_limited' }),
      }),
    );

    expect(session.exchanges()[0].errorCode).toBe('rate_limited');
  });

  it('reports an error event and a stream cut off before the end', () => {
    session.ask('first');
    stream.next({ type: 'error', message: 'The assistant couldn’t answer.' });
    expect(session.exchanges()[0].status).toBe('error');
    expect(session.exchanges()[0].error).toBeUndefined(); // shown in the visitor's language

    session.ask('second');
    stream.next({ type: 'token', text: 'Half' });
    stream.complete();
    expect(session.exchanges()[1].status).toBe('error');
    expect(session.exchanges()[1].error).toBeUndefined();
  });

  it('clears the map for a new question and stops on demand', () => {
    session.ask('first');
    stream.next({ type: 'features', resultId: 'r1', tool: 'find_features', places: [school] });
    session.ask('second');
    expect(session.places()).toEqual([]);

    session.stop();
    expect(stream.observed).toBe(false);
    expect(session.exchanges()[1].status).toBe('done');
  });
});
