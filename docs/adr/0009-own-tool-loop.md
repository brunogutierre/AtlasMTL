# 0009. Our own tool loop on the chat model

**Status:** Accepted, 2026-11-20. Refines [0002](0002-spring-ai-and-claude.md).

## Context

In Spring AI 2.0, the Anthropic chat model no longer runs tools itself: it returns the tool calls, and the `ChatClient`'s tool-calling advisor runs them in a loop. The public demo needs three things that advisor hides:
- each tool call and result shown to the visitor as it happens;
- a hard cap on tool calls and paid model calls per answer;
- token usage per answer.

## Decision

- `Agent` calls `ChatModel.stream` one turn at a time with the tools attached. It forwards text as tokens and collects the tool calls.
- It runs the calls with Spring AI's `ToolCallingManager` and emits `ToolCall`, `ToolResult` and `Features` events.
- Limits:
  - at most 6 tool calls. Calls beyond that get an error result (Anthropic needs a result for every call), and the next model call uses `tool_choice: none`;
  - at most 8 model calls per answer, as a backstop.
- A `ToolExecutionExceptionProcessor` explains bad arguments to the model and replaces any other failure with a generic error, so internal details never reach the stream.

## Consequences

- Every step is visible and bounded, and the loop is tested with a scripted chat model, with no API key and no randomness.
- About 150 lines of orchestration are ours to maintain. The tool execution itself stays Spring AI's.
- The loop knows about Anthropic in one place (`tool_choice: none`); other providers fall back to the call cap.

## Alternatives rejected

- **`ChatClient` + tool-calling advisor:** less code, but the steps are invisible until the end and the cap is per tool rather than per answer.
- **Running tools inside the model's own loop** (Spring AI 1.x): gone in 2.0, and it had the same visibility problem.
