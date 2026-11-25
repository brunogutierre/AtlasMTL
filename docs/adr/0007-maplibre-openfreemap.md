# 0007. MapLibre GL JS with OpenFreeMap tiles

**Status:** Accepted, 2026-11-20

## Context

Answers can draw hundreds of polygons and points, and the map should follow Tessera's colors in light and dark themes.

## Decision

- MapLibre GL JS renders vector tiles from OpenFreeMap. Result layers are GeoJSON sources.
- Layer colors come from Tessera design tokens, so the map changes with the theme.
- The base maps are OpenFreeMap's grey Positron (light) and Dark styles, repainted when they load
  (`map-style.ts`, through MapLibre's `transformStyle`): the land takes a theme color, and parks,
  woods and water get muted natural tints. Dark gains the park and grass layers it lacks. The tints
  stay soft so the answer's places remain the strongest color. (Added 2026-11-25.)
- Attribution covers OpenStreetMap, OpenFreeMap and each dataset publisher.

## Consequences

- No API key and no per-view billing.
- WebGL rendering handles large GeoJSON smoothly.
- The repaint matches layers by their OpenMapTiles source layer, not by id, so it survives small
  style updates; a layer it doesn't recognize keeps OpenFreeMap's color.
- The tile provider is a style URL. It can be swapped for self-hosted tiles if OpenFreeMap's terms change.

## Alternatives rejected

- **Leaflet:** raster-first, and slow with large vector layers.
- **Mapbox GL:** needs a key and has usage pricing.
- **OpenLayers:** capable, but a heavier API for this scope.
- **Colorful ready-made styles (Liberty, Fiord):** Liberty's many icons compete with the answer's
  places, and Fiord keeps the low contrast.
- **Our own full style JSON:** total control, but thousands of lines to maintain.
