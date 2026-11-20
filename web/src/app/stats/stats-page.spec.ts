import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideTessera } from '@brunogutierre/tessera';
import type { Stats } from '../api';
import { I18n } from '../i18n';
import { StatsPage } from './stats-page';

const stats: Stats = {
  questions: 1234,
  lastSevenDays: 120,
  cachedShare: 0.375,
  averageCostUsd: 0.0021,
  medianLatencyMs: 2400,
  p95LatencyMs: 8100,
  averageToolCalls: 1.8,
  tools: [
    { tool: 'find_features', calls: 900 },
    { tool: 'geocode', calls: 300 },
  ],
  days: [
    { day: '2026-11-19', questions: 10 },
    { day: '2026-11-20', questions: 20 },
  ],
};

describe('StatsPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideTessera({ theme: 'enterprise', mode: 'light' }),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
  });

  async function render(language: 'en' | 'fr') {
    TestBed.inject(I18n).language.set(language);
    const fixture = TestBed.createComponent(StatsPage);
    TestBed.tick();
    TestBed.inject(HttpTestingController).expectOne('/api/stats').flush(stats);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the figures, the daily bars and the tools in English', async () => {
    const element = await render('en');
    const text = element.textContent!.replace(/\s+/g, ' ');

    expect(text).toContain('1,234');
    expect(text).toContain('37.5%');
    expect(text).toContain('US$0.0021');
    expect(text).toContain('2.4 s');
    expect(text).toContain('Searched for places 900');
    expect(element.querySelectorAll<HTMLElement>('.bar span')[1].style.width).toBe('100%');
  });

  it('formats numbers the French Canadian way', async () => {
    const text = (await render('fr')).textContent!.replace(/\s+/g, ' ');

    expect(text).toContain('1 234');
    expect(text).toContain('37,5 %');
    expect(text).toContain('Recherche de lieux 900');
  });
});
