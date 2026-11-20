# 0004. One feature table, explicit spatial SQL

**Status:** Accepted, 2026-11-20

## Context

The tools ask the same spatial questions of every category: within a borough, within a distance of another category, counts per borough. Sources differ in shape, ranging from park polygons to school and hospital points.

## Decision

```
dataset(id, source_url, license, fetched_at, checksum)
borough(id, name, kind, geom)
feature(id, dataset_id, category, subcategory, name_fr, name_en, props jsonb, geom geometry(Geometry, 4326))
address(id, number_from, number_to, street, orientation, borough_id, geom)
query_log(id, asked_at, ip_hash, question, tools jsonb, input_tokens, output_tokens, cost_usd, latency_ms)
```

- Every source is normalized into `feature`, and source-specific fields stay in `props`.
- Distances use `geography` (`ST_DWithin(a.geom::geography, b.geom::geography, :meters)`), backed by a GiST index on `(geom::geography)`.
- Access goes through `JdbcClient` with SQL written out in the repository. Migrations use Flyway.

## Consequences

- A new category is ingest work only: no schema change and no new tool.
- Spatial SQL is visible in code review and tested against real PostGIS (Testcontainers).
- `props` isn't typed. Tools never filter on it, and it's only shown in the UI.

## Alternatives rejected

- **One table per category:** every tool would need dynamic table names or unions.
- **JPA + Hibernate Spatial:** hides the SQL that matters most, and spatial joins are awkward in JPQL.
