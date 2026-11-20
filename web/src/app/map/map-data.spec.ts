import type { Place } from '../api';
import { bounds, placesCollection } from './map-data';

function place(id: number, longitude: number, latitude: number): Place {
  return {
    id,
    category: 'PARK',
    subcategory: null,
    name: `Park ${id}`,
    borough: 'Verdun',
    props: {},
    longitude,
    latitude,
    geometry: { type: 'Point', coordinates: [longitude, latitude] },
  };
}

describe('map data', () => {
  it('turns places into GeoJSON features with their labels', () => {
    const collection = placesCollection([place(7, -73.57, 45.5)]);

    expect(collection.features[0]).toEqual({
      type: 'Feature',
      id: 7,
      geometry: { type: 'Point', coordinates: [-73.57, 45.5] },
      properties: { name: 'Park 7', category: 'PARK', borough: 'Verdun' },
    });
  });

  it('bounds the places, or nothing without places', () => {
    expect(bounds([place(1, -73.6, 45.5), place(2, -73.5, 45.55)])).toEqual([
      [-73.6, 45.5],
      [-73.5, 45.55],
    ]);
    expect(bounds([])).toBeNull();
  });
});
