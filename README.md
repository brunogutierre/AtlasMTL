# AtlasMTL

Ask questions about Montreal in plain language and see the answer on a map. *"Schools within 1 km of a hospital"*, *"Which borough has the most parks per resident?"*: an AI agent picks typed geospatial tools, runs safe PostGIS queries over the city's open data, streams its answer and draws the results.

> **Status: planning.** No code yet. The full plan, decisions and milestones are in [ROADMAP.md](ROADMAP.md).

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
| Tests | JUnit 5, Testcontainers PostGIS, stub `ChatModel`, eval suite, Vitest, Playwright + axe |
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

Montreal and Québec open data portals ([donnees.montreal.ca](https://donnees.montreal.ca), [Données Québec](https://www.donneesquebec.ca)). Licenses and attribution are listed per dataset in the app and in this README once ingestion lands.

## How to run and test

There is no code yet. This section is filled in as the milestones land.

## License

[MIT](LICENSE)
