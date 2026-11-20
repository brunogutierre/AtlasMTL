import type { BoroughShape, Place } from '../api';

/** GeoJSON as MapLibre's sources take it; the API types geometries as `unknown`. */
export interface FeatureCollection {
  type: 'FeatureCollection';
  features: {
    type: 'Feature';
    id: number;
    geometry: unknown;
    properties: Record<string, string | number | null>;
  }[];
}

/** The places of an answer, with what the layers style and label them by. */
export function placesCollection(places: readonly Place[]): FeatureCollection {
  return {
    type: 'FeatureCollection',
    features: places.map((place) => ({
      type: 'Feature',
      id: place.id,
      geometry: place.geometry,
      properties: {
        name: place.name,
        category: place.category,
        borough: place.borough,
      },
    })),
  };
}

export function boroughsCollection(boroughs: readonly BoroughShape[]): FeatureCollection {
  return {
    type: 'FeatureCollection',
    features: boroughs.map((borough) => ({
      type: 'Feature',
      id: borough.id,
      geometry: borough.geometry,
      properties: { name: borough.name, kind: borough.kind },
    })),
  };
}

/** The corners around the places' label points, or `null` when there are none. */
export function bounds(
  places: readonly Place[],
): [[number, number], [number, number]] | null {
  if (places.length === 0) {
    return null;
  }
  let west = Infinity;
  let south = Infinity;
  let east = -Infinity;
  let north = -Infinity;
  for (const { longitude, latitude } of places) {
    west = Math.min(west, longitude);
    east = Math.max(east, longitude);
    south = Math.min(south, latitude);
    north = Math.max(north, latitude);
  }
  return [
    [west, south],
    [east, north],
  ];
}

/** The agglomeration, where the map opens. */
export const MONTREAL: [[number, number], [number, number]] = [
  [-73.98, 45.41],
  [-73.47, 45.7],
];
