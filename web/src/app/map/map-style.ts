/**
 * Gives OpenFreeMap's grey base maps (Positron, Dark) a little depth: muted park, wood and water
 * tints, roads a step brighter than the land, and readable labels. The answer's places stay the
 * strongest color on the map.
 *
 * Layers are matched by their OpenMapTiles source layer and type, not by id, so both styles (whose
 * ids differ) go through the same rules.
 */

/** A base map: the style to load and the colors it is repainted with. */
export interface Basemap {
  url: string;
  palette: BasemapPalette;
}

export interface BasemapPalette {
  land: string;
  park: string;
  wood: string;
  water: string;
  waterLabel: string;
  building: string;
  buildingOutline: string;
  road: string;
  majorRoad: string;
  roadCasing: string;
  rail: string;
  label: string;
  labelHalo: string;
}

/** Muted natural tints; `land` comes from a theme token, so the map matches the page. */
export const PALETTES: Record<'light' | 'dark', Omit<BasemapPalette, 'land'>> = {
  light: {
    park: '#dde8d3',
    wood: '#d2e1c8',
    water: '#bfd4e2',
    waterLabel: '#4f6b85',
    building: '#e6e3dc',
    buildingOutline: '#d2cec5',
    road: '#ffffff',
    majorRoad: '#ffffff',
    roadCasing: '#d4d0c8',
    rail: '#cfcac1',
    label: '#3b3f45',
    labelHalo: '#ffffff',
  },
  dark: {
    park: '#1d3a2c',
    wood: '#1a3427',
    water: '#183552',
    waterLabel: '#7d9cbd',
    building: '#253244',
    buildingOutline: '#2f3e52',
    road: '#212b3a',
    majorRoad: '#334156',
    roadCasing: '#18212e',
    rail: '#2c384a',
    label: '#b4bfcc',
    labelHalo: '#0f172a',
  },
};

/** The parts of a MapLibre style this module reads and writes. */
export interface StyleLike {
  layers: LayerLike[];
}

interface LayerLike {
  id: string;
  type: string;
  source?: string;
  'source-layer'?: string;
  filter?: unknown;
  paint?: Record<string, unknown>;
}

/**
 * Green areas every base map should show. Dark draws no `park` layer and neither style draws grass,
 * so the ones missing are added right above the background, under water, roads and labels.
 */
const GREEN_LAYERS: LayerLike[] = [
  {
    id: 'atlas_landcover_grass',
    type: 'fill',
    source: 'openmaptiles',
    'source-layer': 'landcover',
    filter: ['==', ['get', 'class'], 'grass'],
  },
  { id: 'atlas_park', type: 'fill', source: 'openmaptiles', 'source-layer': 'park' },
];

/** A copy of the style with the palette applied; layers it doesn't know keep their colors. */
export function shadeStyle<S extends StyleLike>(style: S, palette: BasemapPalette): S {
  const has = (sourceLayer: string, word: string) =>
    style.layers.some((l) => l['source-layer'] === sourceLayer && l.id.includes(word));
  const missing = GREEN_LAYERS.filter(
    (l) => !has(l['source-layer']!, l['source-layer'] === 'park' ? 'park' : 'grass'),
  );
  const background = style.layers.findIndex((l) => l.type === 'background');
  const layers = [...style.layers];
  layers.splice(background + 1, 0, ...missing);
  return { ...style, layers: layers.map((layer) => shadeLayer(layer, palette)) };
}

function shadeLayer<L extends LayerLike>(layer: L, p: BasemapPalette): L {
  const paint = colorsFor(layer, p);
  return paint ? { ...layer, paint: { ...layer.paint, ...paint } } : layer;
}

function colorsFor(layer: LayerLike, p: BasemapPalette): Record<string, unknown> | null {
  const source = layer['source-layer'] ?? '';
  const id = layer.id;
  switch (layer.type) {
    case 'background':
      return { 'background-color': p.land };
    case 'fill':
      if (source === 'water') return { 'fill-color': p.water };
      if (source === 'park' || (source === 'landuse' && id.includes('park'))) {
        return { 'fill-color': p.park };
      }
      if (source === 'landcover' && /wood|grass/.test(id)) return { 'fill-color': p.wood };
      if (source === 'landuse' && id.includes('residential')) return { 'fill-color': p.land };
      if (source === 'building') {
        return { 'fill-color': p.building, 'fill-outline-color': p.buildingOutline };
      }
      if (source === 'aeroway') return { 'fill-color': p.building };
      if (source === 'transportation') return { 'fill-color': p.land }; // piers
      return null;
    case 'line':
      if (source === 'waterway') return { 'line-color': p.water };
      if (source === 'aeroway') return { 'line-color': p.majorRoad };
      if (source !== 'transportation') return null;
      if (id.includes('dash')) return { 'line-color': p.land }; // gaps in the rail line
      if (id.includes('pier')) return { 'line-color': p.land };
      if (id.includes('rail')) return { 'line-color': p.rail };
      if (id.includes('casing')) return { 'line-color': p.roadCasing };
      if (/motorway|major/.test(id)) return { 'line-color': p.majorRoad };
      if (/minor|path/.test(id)) return { 'line-color': p.road };
      return null;
    case 'symbol':
      if (source === 'water_name' || source === 'waterway') {
        return { 'text-color': p.waterLabel, 'text-halo-color': p.labelHalo };
      }
      if (source === 'place' || source === 'transportation_name' || source === 'aerodrome_label') {
        return { 'text-color': p.label, 'text-halo-color': p.labelHalo };
      }
      return null;
    default:
      return null;
  }
}
