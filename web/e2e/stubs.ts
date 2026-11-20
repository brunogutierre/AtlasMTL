import type { Page } from '@playwright/test';

/** A blank map style: the tests check our layers, not OpenFreeMap's tiles. */
const BLANK_STYLE = {
  version: 8,
  sources: {},
  layers: [{ id: 'background', type: 'background', paint: { 'background-color': '#eeeeee' } }],
};

const square = (west: number, south: number, size = 0.02) => ({
  type: 'MultiPolygon',
  coordinates: [[[[west, south], [west + size, south], [west + size, south + size], [west, south + size], [west, south]]]],
});

export const BOROUGHS = [
  { id: 1, name: 'Le Plateau-Mont-Royal', kind: 'borough', geometry: square(-73.6, 45.51) },
  { id: 2, name: 'Westmount', kind: 'linked_city', geometry: square(-73.62, 45.47) },
];

export const PARKS = [
  { id: 101, name: 'La Fontaine', lon: -73.569, lat: 45.527 },
  { id: 102, name: 'Jeanne-Mance', lon: -73.586, lat: 45.514 },
].map((p) => ({
  id: p.id,
  category: 'PARK',
  subcategory: 'large_park',
  name: p.name,
  borough: 'Le Plateau-Mont-Royal',
  props: {},
  longitude: p.lon,
  latitude: p.lat,
  geometry: { type: 'Point', coordinates: [p.lon, p.lat] },
}));

export const ANSWER = [
  { type: 'tool_call', id: 'c1', tool: 'find_features', arguments: { category: 'PARK', borough: 'Plateau' } },
  { type: 'tool_result', id: 'c1', tool: 'find_features', result: { total: 2 } },
  { type: 'features', resultId: 'r1', tool: 'find_features', places: PARKS },
  { type: 'token', text: 'I found 2 large parks in Le Plateau-Mont-Royal: ' },
  { type: 'token', text: 'La Fontaine and Jeanne-Mance.' },
  { type: 'usage', inputTokens: 800, outputTokens: 40, toolCalls: 1 },
  { type: 'done' },
];

export const STATS = {
  questions: 1234,
  lastSevenDays: 120,
  cachedShare: 0.375,
  averageCostUsd: 0.0021,
  medianLatencyMs: 2400,
  p95LatencyMs: 8100,
  averageToolCalls: 1.8,
  tools: [{ tool: 'find_features', calls: 900 }],
  days: [
    { day: '2026-11-19', questions: 10 },
    { day: '2026-11-20', questions: 20 },
  ],
};

/** Stubs the API and the map styles; `ask` decides how /api/ask answers. */
export async function stubApi(
  page: Page,
  ask: { status: number; body: unknown } | { events: unknown[] } = { events: ANSWER },
): Promise<string[]> {
  const questions: string[] = [];
  await page.route('https://tiles.openfreemap.org/**', (route) => route.fulfill({ json: BLANK_STYLE }));
  await page.route('**/api/boroughs', (route) => route.fulfill({ json: BOROUGHS }));
  await page.route('**/api/stats', (route) => route.fulfill({ json: STATS }));
  await page.route('**/api/ask', async (route) => {
    questions.push((route.request().postDataJSON() as { text: string }).text);
    if ('events' in ask) {
      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        body: ask.events.map((event) => `data:${JSON.stringify(event)}\n\n`).join(''),
      });
    } else {
      await route.fulfill({ status: ask.status, json: ask.body });
    }
  });
  return questions;
}
