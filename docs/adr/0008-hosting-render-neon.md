# 0008. One container on Render, PostGIS on Neon

**Status:** Accepted, 2026-11-24 (proposed 2026-11-20 with a paid instance).

## Context

The demo should answer quickly and cost as little as possible: ideally nothing while nobody uses it.

## Decision

- One container: Spring Boot serves the Angular build and the API. It runs on Render's free instance.
  The instance type is one line in `render.yaml`, so moving to a paid instance needs no code change.
- Neon (serverless PostgreSQL with PostGIS) hosts the data. CI can use Neon branches.

## Consequences

- There is one deployable artifact, and CORS isn't needed.
- The database is reached over the internet, and the latency is acceptable for a few queries per answer.
- The free instance sleeps after 15 minutes without traffic. The first visit after that waits about a
  minute while the container starts. A "waking up" banner like the one in a split frontend/API setup
  isn't possible here, because the same sleeping container serves the page.
- JVM flags in `render.yaml` (serial GC, C1 only, smaller stacks and code cache) shorten that start on
  0.1 CPU and fit 512 MB.
- Render sits behind Cloudflare. `X-Forwarded-For` ends with a public Cloudflare edge address that
  Tomcat doesn't trust, and many visitors share it. Visitor ids come from `CF-Connecting-IP` instead,
  which Cloudflare overwrites with the visitor's address (`ATLAS_CLIENT_IP_HEADER` in `render.yaml`).
- Usage limits survive the sleep: daily budgets are summed from `query_log` in the database, not kept
  in memory. Only the per-minute rate buckets reset, which is harmless.

## Alternatives rejected

- **Render Starter (paid, never sleeps):** about US$7/month and a card on file, for a portfolio demo that
  is idle most of the time. Kept as the upgrade path if cold starts become a problem.
- **Supabase free tier:** pauses idle projects.
