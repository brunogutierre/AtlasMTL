# 0007. MapLibre GL JS with OpenFreeMap tiles

**Status:** Accepted, 2026-11-20

## Context

Answers can draw hundreds of polygons and points, and the map should follow Tessera's colors in light and dark themes.

## Decision

- MapLibre GL JS renders vector tiles from OpenFreeMap. Result layers are GeoJSON sources.
- Layer colors come from Tessera design tokens, so the map changes with the theme.
- Attribution covers OpenStreetMap, OpenFreeMap and each dataset publisher.

## Consequences

- No API key and no per-view billing.
- WebGL rendering handles large GeoJSON smoothly.
- The tile provider is a style URL. It can be swapped for self-hosted tiles if OpenFreeMap's terms change.

## Alternatives rejected

- **Leaflet:** raster-first, and slow with large vector layers.
- **Mapbox GL:** needs a key and has usage pricing.
- **OpenLayers:** capable, but a heavier API for this scope.
