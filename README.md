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
| AI | Spring AI 2.0 `ChatClient` with tool calling; Claude (Anthropic) by default, Ollama for local dev |
| Data | PostgreSQL + PostGIS, Flyway, `JdbcClient` with explicit SQL |
| Frontend | Angular 22 (signals, zoneless), [Tessera](https://github.com/brunogutierre/Tessera) design system, Transloco EN/FR |
| Map | MapLibre GL JS + OpenFreeMap tiles |
| API contract | [RecordBridge](https://github.com/brunogutierre/RecordBridge) generates the Angular client and event types |
| Tests | JUnit 6, Testcontainers PostGIS, stub `ChatModel`, eval suite, Vitest, Playwright + axe |
| Deploy | One container on Render, Neon (PostGIS) |

## Architecture (planned)

```
Angular (map + chat) ──POST /api/ask (SSE)──► Spring Boot
       ▲                                          │ ChatClient + tool-calling advisor
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
./gradlew :server:bootRun       # API on http://localhost:8080 (health: /actuator/health)

cd web && npm ci
npx ng serve                    # Angular dev server on http://localhost:4200
npx ng test --watch=false       # Vitest
```

Run `./gradlew spotlessApply` on its own to format Java (google-java-format) and Gradle scripts (ktlint).

With Podman instead of Docker, point Testcontainers at the Podman socket: `DOCKER_HOST=unix://$XDG_RUNTIME_DIR/podman/podman.sock TESTCONTAINERS_RYUK_DISABLED=true ./gradlew build`.

### Layout

| Path | Purpose |
|---|---|
| `build-logic/` | Convention plugins: `atlas.java` (JDK 25 toolchain, `-Werror`, Spring Boot and Spring AI BOMs, Spotless) and `atlas.spring-boot` |
| `db/` | Flyway migrations (the schema) and a PostGIS Testcontainers fixture for tests |
| `ingest/` | Reads the open datasets and normalizes them into the schema's rows (loading and the weekly job arrive next) |
| `server/` | Spring Boot 4.1 application (Spring MVC on virtual threads); applies the migrations on startup |
| `web/` | Angular 22 app (zoneless, Vitest) |
| `compose.yaml` | Local PostGIS |
| `docs/` | [Data spike](docs/data-spike.md) and [ADRs](docs/adr/) |

## License

[MIT](LICENSE)
