# 0006. Local geocoder from the city's address points

**Status:** Accepted, 2026-11-20

## Context

Questions like "what's near 1000 rue Sherbrooke Ouest?" need an address turned into a point. The demo is public, and requests come from the model.

## Decision

- The geocoder uses the agglomeration's [address points](https://donnees.montreal.ca/dataset/adresses-ponctuelles): 345,979 points, many covering a number range.
- Steps:
  1. parse the number, street type, street name, orientation (`Ouest`/`O`/`West`) and an optional locality after a comma, in French or English;
  2. match the street with a `pg_trgm` similarity index on a normalized street name (no accents or case), with and without its link words, because official names sometimes keep them (*boulevard De Maisonneuve*);
  3. break ties by street type, then orientation, then the locality's borough or linked city;
  4. pick the point whose range contains the number on the same side (parity), or else the nearest number on that street.
- The result includes the matched address, so the answer shows what was actually found.

## Consequences

- No external service, so there's no rate limit, usage policy or cost.
- Only addresses in the Montréal agglomeration resolve, which is exactly the app's scope.
- Place names ("Olympic Stadium") don't resolve in v1. `find_features` by name covers parks and facilities.

## Alternatives rejected

- **Public Nominatim:** its usage policy forbids this kind of automated, high-volume use.
- **Paid geocoding APIs:** cost and keys, for a city-only scope.
