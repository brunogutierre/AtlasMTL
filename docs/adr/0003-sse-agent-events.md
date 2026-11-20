# 0003. Typed server-sent events over POST

**Status:** Accepted, 2026-11-20

## Context

An answer has several parts that arrive over time: text tokens, tool calls, tool results, map features, usage and errors. The browser must render each part as it arrives.

## Decision

- `POST /api/ask` returns `text/event-stream`.
- The server emits a sealed `AgentEvent` hierarchy: `Token | ToolCall | ToolResult | Features | Usage | Done | Error`, tagged by a `type` property.
- RecordBridge generates the TypeScript union and the Angular client from the Java types, so both sides share one definition.
- The server uses Spring MVC with virtual threads. The stream comes from a `Flux<ServerSentEvent<AgentEvent>>` returned by the controller.

## Consequences

- One-way streaming over plain HTTP: it works through proxies and is easy to replay. Cached gallery answers are replayed through the same endpoint.
- `EventSource` can't send a `POST` body, so the client reads the stream with `HttpClient` progress events. RecordBridge generates that code.
- Adding an event type is a compile error on both sides until it's handled.

## Alternatives rejected

- **WebSockets:** two-way traffic isn't needed, and they are harder to cache, replay and proxy.
- **`EventSource` with GET:** questions would end up in URLs and logs.
- **WebFlux for the whole server:** reactive code everywhere for one streaming endpoint.
