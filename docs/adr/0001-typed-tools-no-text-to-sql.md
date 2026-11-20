# 0001. Typed tools, no text-to-SQL

**Status:** Accepted, 2026-11-20

## Context

Visitors ask free-form questions on a public demo. If the model writes SQL, anyone can steer it into expensive or unintended queries, and its behavior can't be unit tested.

## Decision

The model can only call a small set of typed tools:

| Tool | Arguments |
|---|---|
| `describe_categories` | none |
| `find_features` | `category`, `borough?`, `near?`, `limit` |
| `within_distance` | `sourceCategory`, `targetCategory`, `meters`, `borough?` |
| `count_by_borough` | `category` |
| `geocode` | `text` |
| `get_borough` | `name` |

- Every argument is validated before any query runs: categories are enums, `meters ≤ 5000`, `limit ≤ 500`, and a question may use at most 6 tool calls.
- Each tool runs one parameterized SQL statement that lives in the codebase.
- The model receives a short summary and a `resultId`. The full GeoJSON goes only to the browser, which keeps tokens low.

## Consequences

- No SQL injection, and the cost of a query is bounded by its arguments.
- Each tool's SQL is tested against PostGIS with fixtures, and the orchestration is tested with a scripted model.
- Questions the tools can't express get an honest "I can't answer that" instead of a guess. The eval suite measures how often that happens.

## Alternatives rejected

- **Text-to-SQL with a read-only role:** still allows runaway queries, and the output can't be tested.
- **One generic `query` tool with a filter DSL:** it becomes text-to-SQL in disguise.
