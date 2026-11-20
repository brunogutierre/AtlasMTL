# AtlasMTL

Ask questions about Montreal in plain language and see the answer on a map. *"Schools within 1 km of a hospital"*, *"Which borough has the most parks per km²?"*: an AI agent picks typed geospatial tools, runs safe PostGIS queries over the city's open data, streams its answer and draws the results.

> **Status: planning.** No code yet. The plan is in [ROADMAP.md](ROADMAP.md), the data sources were checked in the [data spike](docs/data-spike.md), and the decisions are recorded as [ADRs](docs/adr/).

## Goal

Show an end-to-end LLM feature built the way production teams need it:

- **The LLM never writes SQL.** It calls typed, range-checked tools; every query is parameterized.
- **Streaming everywhere.** Tokens, tool calls and map features arrive as typed server-sent events.
- **Measured, not guessed.** An evaluation suite of golden questions, token and latency metrics, and per-visitor cost limits on the public demo.

## Stack (planned)

| Area | Choice |
|---|---|
| Backend | Java 25, Spring Boot 4 (Spring MVC + virtual threads) |
| AI | Spring AI 2.0 with Claude (Anthropic) and typed `@Tool`s, run by our own bounded tool loop |
| Data | PostgreSQL + PostGIS, Flyway, `JdbcClient` with explicit SQL |
| Frontend | Angular 22 (signals, zoneless), [Tessera](https://github.com/brunogutierre/Tessera) design system, typed EN/FR messages |
| Map | MapLibre GL JS + OpenFreeMap tiles |
| API contract | [RecordBridge](https://github.com/brunogutierre/RecordBridge) generates the Angular client and event types |
| Tests | JUnit 6, Testcontainers PostGIS, stub `ChatModel`, eval suite, Vitest, Playwright + axe |
| Deploy | One container on Render, Neon (PostGIS) |

## Architecture (planned)

```
Angular (map + chat) ──POST /api/ask (SSE)──► Spring Boot
       ▲                                          │ own tool loop (≤ 6 tool calls)
       │ Token | ToolCall | Features | Done ...   ▼
       └──────────────────────────────── Tools (find, within_distance, count_by_borough, geocode)
                                                  │ parameterized SQL
                                                  ▼
                                    PostGIS ◄── weekly ingest (GitHub Actions)
                                                  ▲
                                   donnees.montreal.ca / Données Québec
```

## Data sources

Five CC-BY 4.0 datasets from [donnees.montreal.ca](https://donnees.montreal.ca) (parks, boroughs, address points) and [Données Québec](https://www.donneesquebec.ca) (MSSS health facilities, MEQ schools). The [data spike](docs/data-spike.md) lists each source, its format, update cadence and caveats. The app credits every publisher.

## How to run and test

Requirements: JDK 17+ to run Gradle (the build provisions a JDK 25 toolchain), Node 24, and Docker or Podman.

```bash
docker compose up -d            # local PostGIS (postgis/postgis:17-3.5) on port 5432
./gradlew build                 # server: compile, format check, tests (Testcontainers starts its own PostGIS)
ANTHROPIC_API_KEY=… ./gradlew :server:bootRun   # API on http://localhost:8080 (health: /actuator/health)

cd web && npm ci
npx ng serve                    # Angular dev server on http://localhost:4200
npx ng test --watch=false       # Vitest
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

Run `./gradlew spotlessApply` on its own to format Java (google-java-format) and Gradle scripts (ktlint).

With Podman instead of Docker, point Testcontainers at the Podman socket: `DOCKER_HOST=unix://$XDG_RUNTIME_DIR/podman/podman.sock TESTCONTAINERS_RYUK_DISABLED=true ./gradlew build`.

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
