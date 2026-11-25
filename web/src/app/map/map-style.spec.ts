import { PALETTES, shadeStyle, type BasemapPalette, type StyleLike } from './map-style';

const palette: BasemapPalette = { ...PALETTES.dark, land: 'rgb(15, 23, 42)' };

function paintOf(style: StyleLike, id: string): Record<string, unknown> | undefined {
  return style.layers.find((layer) => layer.id === id)?.paint;
}

describe('shadeStyle', () => {
  const style: StyleLike = {
    layers: [
      { id: 'background', type: 'background', paint: { 'background-color': 'rgb(12,12,12)' } },
      { id: 'water', type: 'fill', 'source-layer': 'water', paint: { 'fill-color': '#111' } },
      {
        id: 'landuse_park',
        type: 'fill',
        'source-layer': 'landuse',
        paint: { 'fill-color': '#222' },
      },
      {
        id: 'landcover_wood',
        type: 'fill',
        'source-layer': 'landcover',
        paint: { 'fill-color': '#333', 'fill-opacity': 0.8 },
      },
      { id: 'highway_major_casing', type: 'line', 'source-layer': 'transportation', paint: {} },
      { id: 'highway_major_inner', type: 'line', 'source-layer': 'transportation', paint: {} },
      { id: 'railway_dashline', type: 'line', 'source-layer': 'transportation', paint: {} },
      { id: 'place_city', type: 'symbol', 'source-layer': 'place', paint: { 'icon-opacity': 0.7 } },
      {
        id: 'boundary_state',
        type: 'line',
        'source-layer': 'boundary',
        paint: { 'line-color': '#444' },
      },
    ],
  };

  const shaded = shadeStyle(style, palette);

  it('tints land, water, parks and woods', () => {
    expect(paintOf(shaded, 'background')).toEqual({ 'background-color': palette.land });
    expect(paintOf(shaded, 'water')?.['fill-color']).toBe(palette.water);
    expect(paintOf(shaded, 'landuse_park')?.['fill-color']).toBe(palette.park);
    expect(paintOf(shaded, 'landcover_wood')).toEqual({
      'fill-color': palette.wood,
      'fill-opacity': 0.8,
    });
  });

  it('lifts roads above the land and keeps rail gaps in the land color', () => {
    expect(paintOf(shaded, 'highway_major_casing')?.['line-color']).toBe(palette.roadCasing);
    expect(paintOf(shaded, 'highway_major_inner')?.['line-color']).toBe(palette.majorRoad);
    expect(paintOf(shaded, 'railway_dashline')?.['line-color']).toBe(palette.land);
  });

  it('makes labels readable and keeps the other paint properties', () => {
    expect(paintOf(shaded, 'place_city')).toEqual({
      'icon-opacity': 0.7,
      'text-color': palette.label,
      'text-halo-color': palette.labelHalo,
    });
  });

  it('adds the park and grass layers a base map lacks, right above the background', () => {
    expect(shaded.layers.slice(0, 3).map((l) => l.id)).toEqual([
      'background',
      'atlas_landcover_grass',
      'atlas_park',
    ]);
    expect(paintOf(shaded, 'atlas_park')).toEqual({ 'fill-color': palette.park });

    const withPark = shadeStyle(
      {
        layers: [
          { id: 'bg', type: 'background' },
          { id: 'park', type: 'fill', 'source-layer': 'park' },
        ],
      },
      palette,
    );
    expect(withPark.layers.filter((l) => l['source-layer'] === 'park')).toHaveLength(1);
  });

  it('leaves unknown layers and the original style untouched', () => {
    expect(paintOf(shaded, 'boundary_state')).toEqual({ 'line-color': '#444' });
    expect(paintOf(style, 'water')?.['fill-color']).toBe('#111');
  });
});
