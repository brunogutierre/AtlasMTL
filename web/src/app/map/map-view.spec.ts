import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TesseraTheme, provideTessera } from '@brunogutierre/tessera';
import type { Place } from '../api';
import { MAP_ENGINE, type MapHandle, type MapLayers } from './map-engine';
import { MapView } from './map-view';

describe('MapView', () => {
  let shown: MapLayers[];
  let styles: string[];
  let fitted: unknown[];

  beforeEach(() => {
    shown = [];
    styles = [];
    fitted = [];
    const handle: MapHandle = {
      show: (layers) => shown.push(layers),
      fit: (area) => fitted.push(area),
      setStyle: (url) => styles.push(url),
      destroy() {},
    };
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideTessera({ theme: 'enterprise', mode: 'light' }),
        { provide: MAP_ENGINE, useValue: () => handle },
      ],
    });
  });

  it('draws the places and fits the map to them', async () => {
    const fixture = TestBed.createComponent(MapView);
    const place: Place = {
      id: 1,
      category: 'SCHOOL',
      subcategory: null,
      name: 'École',
      borough: null,
      props: {},
      longitude: -73.58,
      latitude: 45.52,
      geometry: { type: 'Point', coordinates: [-73.58, 45.52] },
    };
    fixture.componentRef.setInput('places', [place]);
    await fixture.whenStable();

    expect(shown.at(-1)?.places.features).toHaveLength(1);
    expect(shown.at(-1)?.colors.places).toMatch(/^rgb\(|^#/);
    expect(fitted).toHaveLength(1);
  });

  it('switches to the dark map with the dark theme', async () => {
    const fixture = TestBed.createComponent(MapView);
    await fixture.whenStable();

    TestBed.inject(TesseraTheme).mode.set('dark');
    await fixture.whenStable();

    expect(styles.at(-1)).toContain('/styles/dark');
  });
});
