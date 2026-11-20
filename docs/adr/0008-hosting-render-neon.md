# 0008. One container on Render, PostGIS on Neon

**Status:** Proposed, 2026-11-20. Prices and limits are verified in PR 20, before deploying.

## Context

The demo must answer quickly on the first visit (no cold start) and cost a few dollars a month.

## Decision

- One container: Spring Boot serves the Angular build and the API. It runs on a paid Render instance that doesn't sleep.
- Neon (serverless PostgreSQL with PostGIS) hosts the data. CI can use Neon branches.

## Consequences

- There is one deployable artifact, and CORS isn't needed.
- The database is reached over the internet, and the latency is acceptable for a few queries per answer.

## Alternatives rejected

- **Render's free tier:** cold starts of tens of seconds ruin a demo.
- **Supabase free tier:** pauses idle projects.
