# 0002. Spring AI 2.0 with Claude, on Spring Boot 4.1

**Status:** Accepted, 2026-11-20

## Context

The agent loop needs tool calling, streaming, token usage and observability, in a Spring Boot application.

## Decision

- Spring AI `ChatClient` with `@Tool` beans and its tool-calling support. The model is Claude, through the Anthropic starter.
- A fast, low-cost Claude model by default (configurable). The system prompt and the tool definitions use prompt caching.
- An Ollama profile for local development without an API key.
- Versions: **Spring Boot 4.1.3** and **Spring AI 2.0.3**, the latest patches of lines that are already mature. Spring AI 2.0.3 is built against Boot 4.1.3. Boot 4.2.0 and Spring AI 2.1.0 came out on 2026-11-19; we move to them after their first patch release.

## Consequences

- The model provider is a dependency and a property, not code. Tests use a stub `ChatModel`.
- Micrometer observations for tokens and latency come with Spring AI.
- The project follows Spring AI's release pace. Upgrades get their own PRs, run against the eval suite.

## Alternatives rejected

- **LangChain4j:** capable, but less integrated with Spring Boot configuration and observability.
- **A hand-written loop over the Anthropic SDK:** more code to maintain for the same behavior, and locked to one provider.
