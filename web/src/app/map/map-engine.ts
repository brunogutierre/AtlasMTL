import { InjectionToken } from '@angular/core';
import type { FeatureCollection } from './map-data';
import { shadeStyle, type Basemap } from './map-style';

/** What the map view needs from a map library; MapLibre in the app, a fake in tests. */
export interface MapHandle {
  /** Draws the outlines and the answer's places, in the given colors. */
  show(layers: MapLayers): void;
  fit(bounds: [[number, number], [number, number]]): void;
  /** Loads another base map, such as the dark one when the theme changes. */
  setStyle(basemap: Basemap): void;
  /** Highlights one place (or none) and, with a center, brings it into view. */
  select(id: number | null, center?: [number, number]): void;
  /** Called with the place clicked on the map. */
  onSelect(listener: (id: number) => void): void;
  destroy(): void;
}

export interface MapLayers {
  boroughs: FeatureCollection;
  places: FeatureCollection;
  colors: { places: string; outline: string; boroughs: string };
}

export type MapEngine = (container: HTMLElement, basemap: Basemap) => MapHandle;

/** Loads MapLibre only when a map is created, so it stays out of the initial bundle. */
export const MAP_ENGINE = new InjectionToken<MapEngine>('MAP_ENGINE', {
  factory: () => (container, basemap) => createMapLibre(container, basemap),
});

function createMapLibre(container: HTMLElement, basemap: Basemap): MapHandle {
  let pending: MapLayers | null = null;
  let map: import('maplibre-gl').Map | null = null;
  let destroyed = false;
  // isStyleLoaded() stays false until the tiles arrive; layers can be added as soon as the style is.
  let styleReady = false;
  // A fit asked for before MapLibre finished loading is applied once the map exists.
  let pendingFit: [[number, number], [number, number]] | null = null;
  let current = basemap;
  // Repaints the fetched style before MapLibre commits it, so the grey base never flashes.
  const transformStyle = (_: unknown, next: import('maplibre-gl').StyleSpecification) =>
    shadeStyle(next, current.palette);
  let selected: number | null = null;
  let selectListener: ((id: number) => void) | null = null;

  const apply = (layers: MapLayers) => {
    if (!map || !styleReady) {
      return;
    }
    for (const [id, data] of [
      ['boroughs', layers.boroughs],
      ['places', layers.places],
    ] as const) {
      const source = map.getSource(id) as import('maplibre-gl').GeoJSONSource | undefined;
      if (source) {
        source.setData(data as never);
      } else {
        map.addSource(id, { type: 'geojson', data: data as never });
      }
    }
    if (!map.getLayer('boroughs-line')) {
      map.addLayer({
        id: 'boroughs-line',
        type: 'line',
        source: 'boroughs',
        paint: { 'line-width': 1.25, 'line-opacity': 0.7 },
      });
      map.addLayer({
        id: 'places-fill',
        type: 'fill',
        source: 'places',
        filter: ['==', ['geometry-type'], 'Polygon'],
        paint: {
          'fill-opacity': ['case', ['boolean', ['feature-state', 'selected'], false], 0.65, 0.35],
        },
      });
      map.addLayer({
        id: 'places-point',
        type: 'circle',
        source: 'places',
        filter: ['==', ['geometry-type'], 'Point'],
        paint: {
          'circle-radius': ['case', ['boolean', ['feature-state', 'selected'], false], 10, 6],
          'circle-stroke-width': 1.5,
        },
      });
    }
    map.setPaintProperty('boroughs-line', 'line-color', layers.colors.boroughs);
    map.setPaintProperty('places-fill', 'fill-color', layers.colors.places);
    map.setPaintProperty('places-point', 'circle-color', layers.colors.places);
    map.setPaintProperty('places-point', 'circle-stroke-color', layers.colors.outline);
    if (selected !== null) {
      map.setFeatureState({ source: 'places', id: selected }, { selected: true });
    }
  };

  void import('maplibre-gl').then(({ Map, setWorkerUrl }) => {
    if (destroyed) {
      return;
    }
    // MapLibre 6 runs its worker from a separate file, copied to the site root by angular.json.
    setWorkerUrl(new URL('maplibre-gl-worker.mjs', container.ownerDocument.baseURI).href);
    map = new Map({
      container,
      style: current.url,
      transformStyle,
      bounds: pendingFit ?? [-73.98, 45.41, -73.47, 45.7],
    });
    // A new style drops every source and layer, so they are added again after each load.
    for (const layer of ['places-point', 'places-fill']) {
      map.on('click', layer, (event) => {
        const id = event.features?.[0]?.id;
        if (typeof id === 'number') {
          selectListener?.(id);
        }
      });
      map.on('mouseenter', layer, () => (map!.getCanvas().style.cursor = 'pointer'));
      map.on('mouseleave', layer, () => (map!.getCanvas().style.cursor = ''));
    }
    map.on('style.load', () => {
      styleReady = true;
      if (pending) {
        apply(pending);
      }
    });
  });

  return {
    show(layers) {
      pending = layers;
      apply(layers);
    },
    fit(bounds) {
      if (map) {
        map.fitBounds(bounds, { padding: 48, maxZoom: 15, duration: 600 });
      } else {
        pendingFit = bounds;
      }
    },
    setStyle(next) {
      // Setting the same style again fires no 'style.load', which would leave the layers off.
      if (next.url === current.url && next.palette.land === current.palette.land) {
        return;
      }
      current = next;
      if (map) {
        styleReady = false;
        // diff: false reloads the style, so transformStyle sees the original colors again.
        map.setStyle(next.url, { diff: false, transformStyle });
      }
    },
    select(id, center) {
      if (map && styleReady && map.getSource('places')) {
        if (selected !== null) {
          map.setFeatureState({ source: 'places', id: selected }, { selected: false });
        }
        if (id !== null) {
          map.setFeatureState({ source: 'places', id }, { selected: true });
        }
      }
      selected = id;
      if (map && center) {
        map.easeTo({ center, zoom: Math.max(map.getZoom(), 14), duration: 600 });
      }
    },
    onSelect(listener) {
      selectListener = listener;
    },
    destroy() {
      destroyed = true;
      map?.remove();
    },
  };
}
