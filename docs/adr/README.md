# Architecture decision records

Short records of decisions that are expensive to reverse. Each record states its context, the decision, its consequences and the alternatives that were rejected. A superseded record stays and links to its replacement.

| # | Decision | Status |
|---|---|---|
| [0001](0001-typed-tools-no-text-to-sql.md) | The LLM calls typed, range-checked tools and never writes SQL | Accepted |
| [0002](0002-spring-ai-and-claude.md) | Spring AI 2.0 with Claude, on Spring Boot 4.1 | Accepted |
| [0003](0003-sse-agent-events.md) | Answers stream as typed server-sent events over `POST` | Accepted |
| [0004](0004-one-feature-table-explicit-sql.md) | One `feature` table in PostGIS, queried with explicit SQL | Accepted |
| [0005](0005-ingest-as-ci-job.md) | Ingest is a separate module run weekly by GitHub Actions | Accepted |
| [0006](0006-local-geocoder.md) | A local geocoder built from the city's address points | Accepted |
| [0007](0007-maplibre-openfreemap.md) | MapLibre GL JS with OpenFreeMap tiles | Accepted |
| [0008](0008-hosting-render-neon.md) | One container on Render, PostGIS on Neon | Accepted |
| [0009](0009-own-tool-loop.md) | Our own tool loop on the chat model, with per-answer limits | Accepted |
