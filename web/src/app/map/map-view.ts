import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { TesseraTheme } from '@brunogutierre/tessera';
import type { BoroughShape, Place } from '../api';
import { MONTREAL, boroughsCollection, bounds, placesCollection } from './map-data';
import { MAP_ENGINE, type MapHandle } from './map-engine';
import { PALETTES, type Basemap } from './map-style';

const STYLES = {
  light: 'https://tiles.openfreemap.org/styles/positron',
  dark: 'https://tiles.openfreemap.org/styles/dark',
};

/**
 * The page background in light mode, so roads (white) stand out from the land; the card surface in
 * dark mode, a step lighter than the page, so the map doesn't read as a black hole.
 */
const LAND_TOKENS = { light: '--tes-color-bg', dark: '--tes-color-surface' };

/** The answer's places over the borough outlines, on a softly tinted base map in the theme's colors. */
@Component({
  selector: 'app-map-view',
  template: '',
  styles: ':host { display: block; height: 100%; min-height: inherit; }',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MapView {
  readonly places = input<readonly Place[]>([]);
  readonly boroughs = input<readonly BoroughShape[]>([]);
  /** The highlighted place, shared with the result list. */
  readonly selected = input<number | null>(null);
  readonly selectedChange = output<number>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly engine = inject(MAP_ENGINE);
  private readonly theme = inject(TesseraTheme);
  private readonly map = signal<MapHandle | null>(null);
  /** The base map for the current mode, its land painted in a theme color. */
  private readonly basemap = computed<Basemap>(() => {
    const mode = this.theme.resolvedMode();
    return {
      url: STYLES[mode],
      palette: {
        ...PALETTES[mode],
        land: tokenColor(this.host.nativeElement, LAND_TOKENS[mode]),
      },
    };
  });

  constructor() {
    afterNextRender(() => {
      const map = this.engine(this.host.nativeElement, this.basemap());
      map.onSelect((id) => this.selectedChange.emit(id));
      this.map.set(map);
    });
    inject(DestroyRef).onDestroy(() => this.map()?.destroy());

    effect(() => this.map()?.setStyle(this.basemap()));
    effect(() => {
      const map = this.map();
      this.theme.resolvedMode(); // colors follow the theme
      map?.show({
        boroughs: boroughsCollection(this.boroughs()),
        places: placesCollection(this.places()),
        colors: {
          places: tokenColor(this.host.nativeElement, '--tes-color-primary'),
          outline: tokenColor(this.host.nativeElement, '--tes-color-surface'),
          boroughs: tokenColor(this.host.nativeElement, '--tes-color-text-muted'),
        },
      });
    });
    effect(() => {
      // An answer's places, or the whole agglomeration before the first answer.
      const area = bounds(this.places()) ?? (this.boroughs().length > 0 ? MONTREAL : null);
      if (area) {
        this.map()?.fit(area);
      }
    });
    effect(() => {
      const id = this.selected();
      const place = this.places().find((p) => p.id === id);
      this.map()?.select(id, place ? [place.longitude, place.latitude] : undefined);
    });
  }
}

/**
 * A Tessera token as an `rgb()` color. Tokens are `light-dark(oklch(…), oklch(…))`, which MapLibre
 * can't parse: a probe element lets the browser pick the side for the current color scheme, then a
 * one-pixel canvas turns the result into rgb.
 */
function tokenColor(element: HTMLElement, token: string): string {
  const document = element.ownerDocument;
  const probe = document.createElement('span');
  probe.style.color = `var(${token})`;
  probe.style.display = 'none';
  element.append(probe);
  const resolved = getComputedStyle(probe).color;
  probe.remove();
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = 1;
  const context = canvas.getContext('2d', { willReadFrequently: true });
  if (!context || !resolved) {
    return '#2563eb';
  }
  context.fillStyle = resolved;
  context.fillRect(0, 0, 1, 1);
  const [r, g, b] = context.getImageData(0, 0, 1, 1).data;
  return `rgb(${r}, ${g}, ${b})`;
}
