# AtlasMTL

[![CI](https://github.com/brunogutierre/AtlasMTL/actions/workflows/ci.yml/badge.svg)](https://github.com/brunogutierre/AtlasMTL/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

Ask questions about Montréal in plain language and see the answer on a map. *"Schools within 1 km of a hospital"*, *"Which borough has the most parks per km²?"*, *"Parks within 500 m of 1000 rue Sherbrooke Ouest"*: an AI agent picks typed geospatial tools, runs safe PostGIS queries over the city's open data, streams its answer and draws the places it found.

> **Status:** feature-complete and tested end to end (with a recorded Anthropic stream and a stubbed browser). The public demo goes live once hosting and a model key are set up. The plan and what's left are in [ROADMAP.md](ROADMAP.md).

## What it shows

An LLM feature built the way production teams need it:

- **The LLM never writes SQL.** It can only call six typed, range-checked tools; each one runs a fixed, parameterized query ([ADR 0001](docs/adr/0001-typed-tools-no-text-to-sql.md)).
- **Every step is visible.** Text tokens, tool calls, tool results and map features stream as typed server-sent events, so the visitor sees *how* each answer was found ([ADR 0003](docs/adr/0003-sse-agent-events.md)).
- **Bounded cost.** Our own tool loop caps tool calls (6) and paid model calls (8) per answer ([ADR 0009](docs/adr/0009-own-tool-loop.md)). Repeated questions replay from a cache at zero cost. Visitors are rate-limited, and both visitors and the whole demo have daily budgets.
- **Measured, not guessed.** Golden-question evals against the real model, a per-question usage log, and a public [/stats](#public-statistics) page.
- **Real data, kept fresh.** A weekly job pulls five open datasets (346k address points included) and replaces them atomically ([ADR 0005](docs/adr/0005-ingest-as-ci-job.md)). A local geocoder resolves addresses without any external service ([ADR 0006](docs/adr/0006-local-geocoder.md)).
- **One contract, two languages.** The Angular client and the event union are generated from the Java code by [RecordBridge](https://github.com/brunogutierre/RecordBridge), and CI fails if they drift.

## How it works

```mermaid
flowchart LR
  subgraph Browser
    UI[Angular 22 + Tessera<br/>chat · trace · map]
  end
  subgraph Server[Spring Boot 4]
    API[POST /api/ask<br/>SSE] --> LIM[Rate limit<br/>+ budgets]
    LIM --> CACHE{Answer<br/>cache}
    CACHE -- miss --> LOOP[Tool loop<br/>≤ 6 tool calls]
    LOOP <--> LLM[(Claude<br/>via Spring AI)]
    LOOP --> TOOLS[6 typed tools]
    TOOLS --> SQL[Fixed SQL<br/>JdbcClient]
  end
  SQL --> DB[(PostgreSQL<br/>+ PostGIS)]
  INGEST[Weekly ingest job<br/>GitHub Actions] --> DB
  PORTALS[donnees.montreal.ca<br/>Données Québec] --> INGEST
  UI -- question --> API
  API -- Token · ToolCall · ToolResult · Features · Usage · Done --> UI
```

### The tools

| Tool | What it does |
|---|---|
| `describe_categories` | Lists the kinds of places and how many exist |
| `find_features` | Places of a category, optionally in a borough, near a point, or above a minimum area |
| `within_distance` | Places of one category within N meters of another category, each with its nearest one |
| `count_by_borough` | Count and density per km² in each borough and linked city |
| `geocode` | A Montréal street address to coordinates (French or English, number ranges, typos) |
| `get_borough` | A borough or linked city by name, ignoring case and accents |

The model sees a short summary of each result. The full GeoJSON goes to the map only.

## Stack

| Area | Choice |
|---|---|
| Backend | Java 25, Spring Boot 4.1 (Spring MVC on virtual threads) |
| AI | Spring AI 2.0 with Claude (Anthropic), typed `@Tool`s, our own bounded tool loop |
| Data | PostgreSQL 17 + PostGIS 3.5, Flyway, `JdbcClient` with explicit SQL, `pg_trgm` |
| Frontend | Angular 22 (signals, zoneless), [Tessera](https://github.com/brunogutierre/Tessera) design system, typed EN/FR messages |
| Map | MapLibre GL JS + OpenFreeMap tiles, colored by Tessera tokens in light and dark |
| API contract | [RecordBridge](https://github.com/brunogutierre/RecordBridge) Gradle plugin |
| Cost controls | Bucket4j rate limits, budgets from the usage log, answer cache |
| Tests | JUnit 6, Testcontainers PostGIS, scripted and recorded-stream model tests, golden-question evals, Vitest, Playwright + axe |
| Deploy | One container (Render free tier: sleeps when idle, about a minute to wake up) + managed PostGIS (Neon) |

## Data sources

All five datasets are published under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/); the app credits each publisher.

| Dataset | Publisher |
|---|---|
| [Parks and green spaces](https://donnees.montreal.ca/dataset/grands-parcs-parcs-d-arrondissements-et-espaces-publics) | Ville de Montréal |
| [Administrative boundaries](https://donnees.montreal.ca/dataset/limites-administratives-agglomeration) | Ville de Montréal |
| [Address points](https://donnees.montreal.ca/dataset/adresses-ponctuelles) | Ville de Montréal |
| [Health and social services installations](https://www.donneesquebec.ca/recherche/dataset/fichiers-cartographiques-m02-des-installations-et-etablissements) | Ministère de la Santé et des Services sociaux |
| [School locations](https://www.donneesquebec.ca/recherche/dataset/localisation-des-etablissements-d-enseignement-du-reseau-scolaire-au-quebec) | Ministère de l'Éducation |

The [data spike](docs/data-spike.md) documents each source's format, update cadence and caveats. Map tiles © OpenStreetMap contributors, served by OpenFreeMap.

## Public statistics

`/stats` shows aggregate numbers over completed answers, never a question or a visitor:
- questions answered;
- the share replayed from the cache;
- the average model cost;
- median and p95 answer time;
- tool calls per answer and the most used tools;
- a 14-day series.

## Decisions

The architecture decisions, with their context, consequences and rejected alternatives, are in [docs/adr](docs/adr/).

## How to run and test

Requirements: JDK 17+ to run Gradle (the build provisions a JDK 25 toolchain), Node 24, and Docker or Podman.

```bash
docker compose up -d            # local PostGIS (postgis/postgis:17-3.5) on port 5432
./gradlew build                 # server: compile, format check, tests (Testcontainers starts its own PostGIS)
ANTHROPIC_API_KEY=… ./gradlew :server:bootRun   # API on http://localhost:8080 (health: /actuator/health)

cd web && npm ci
npx ng serve                    # Angular dev server on http://localhost:4200
npx ng test --watch=false       # Vitest
npx playwright test             # end to end in Chromium, with axe; API and map tiles stubbed
```

Ask a question from the command line (the answer streams as server-sent events):

```bash
curl -N -X POST localhost:8080/api/ask -H 'Content-Type: application/json' \
  -d '{"text": "Schools within 1 km of a hospital in Rosemont?"}'
```

`DATABASE_URL` is a JDBC URL (`jdbc:postgresql://host/db`); hosted providers often hand out `postgres://user:pass@host/db`, which must be split into `DATABASE_URL`, `DATABASE_USER` and `DATABASE_PASSWORD`. Without `ANTHROPIC_API_KEY` the server still starts, and answers end with an `error` event. `ATLAS_MODEL` picks another Claude model.

Load the real open data into the local database (downloads ~60 MB; unchanged files are skipped on the next run):

```bash
./gradlew :ingest:bootRun                                  # every dataset
./gradlew :ingest:bootRun --args="--only=parks --force"    # one dataset, even if unchanged
```

The Angular client in `web/src/app/api` is generated from the Java controllers and committed; CI fails if it is stale:

```bash
./gradlew :server:generateTypeScript   # after changing a controller, an event or a DTO
```

`npx ng serve` proxies `/api` to the server on port 8080, or on `API_PORT` when set (`web/proxy.conf.mjs`).

Evaluate the agent against the real model (costs tokens; needs data loaded by the ingest job):

```bash
ANTHROPIC_API_KEY=… ./gradlew :server:evals   # report in server/build/reports/evals/report.md
```

The golden questions live in `server/src/test/resources/evals/golden.yaml`. Each one names the tools a good answer calls, their arguments and the expected number of places. The `Evals` workflow runs them on demand.

Run `./gradlew spotlessApply` on its own to format Java (google-java-format) and Gradle scripts (ktlint).

With Podman instead of Docker, point Testcontainers at the Podman socket: `DOCKER_HOST=unix://$XDG_RUNTIME_DIR/podman/podman.sock TESTCONTAINERS_RYUK_DISABLED=true ./gradlew build`.

### Container

One image holds the API and the Angular build (ADR 0008):

```bash
docker build -t atlasmtl .
docker run -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://host:5432/atlas -e DATABASE_USER=… -e DATABASE_PASSWORD=… \
  -e ANTHROPIC_API_KEY=… -e ATLAS_VISITOR_KEY=… atlasmtl
```

Visitor ids, which the rate limits and budgets use, come from the connection's address, or from `X-Forwarded-For` when the proxy is on a private network (`server.forward-headers-strategy: native`). Render sits behind Cloudflare, whose public edge address ends that chain and is shared by many visitors, so `render.yaml` sets `ATLAS_CLIENT_IP_HEADER=CF-Connecting-IP`: a header Cloudflare overwrites with the visitor's address. Leave it empty anywhere else, or visitors could choose their own address.

### Layout

| Path | Purpose |
|---|---|
| `build-logic/` | Convention plugins: `atlas.java` (JDK 25 toolchain, `-Werror`, Spring Boot and Spring AI BOMs, Spotless) and `atlas.spring-boot` |
| `db/` | Flyway migrations (the schema) and a PostGIS Testcontainers fixture for tests |
| `ingest/` | Command-line job: finds each dataset through CKAN, downloads it, normalizes it and replaces its rows; run weekly by `.github/workflows/ingest.yml` |
| `server/` | Spring Boot 4.1 application (Spring MVC on virtual threads); applies the migrations on startup. The [RecordBridge](https://github.com/brunogutierre/RecordBridge) Gradle plugin generates the Angular client from it |
| `web/` | Angular 22 app (zoneless, Vitest) on the Tessera design system (Enterprise theme, light/dark/system), in English and French |
| `compose.yaml` | Local PostGIS |
| `docs/` | [Data spike](docs/data-spike.md) and [ADRs](docs/adr/) |

## License

[MIT](LICENSE)
