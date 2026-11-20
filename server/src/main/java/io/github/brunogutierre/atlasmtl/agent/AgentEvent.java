package io.github.brunogutierre.atlasmtl.agent;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.github.brunogutierre.atlasmtl.geo.Place;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * What the answer stream carries, in order: text tokens, each tool call and its result, the places
 * to draw, the token usage, and done (or an error). See docs/adr/0003-sse-agent-events.md.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(AgentEvent.Token.class),
  @JsonSubTypes.Type(AgentEvent.ToolCall.class),
  @JsonSubTypes.Type(AgentEvent.ToolResult.class),
  @JsonSubTypes.Type(AgentEvent.Features.class),
  @JsonSubTypes.Type(AgentEvent.Usage.class),
  @JsonSubTypes.Type(AgentEvent.Done.class),
  @JsonSubTypes.Type(AgentEvent.Failure.class)
})
public sealed interface AgentEvent {

  /** A piece of the answer's text. */
  @JsonTypeName("token")
  record Token(String text) implements AgentEvent {}

  /** The model asked for a tool. */
  @JsonTypeName("tool_call")
  record ToolCall(String id, String tool, JsonNode arguments) implements AgentEvent {}

  /** What the tool answered the model: a summary, or an error with a hint. */
  @JsonTypeName("tool_result")
  record ToolResult(String id, String tool, JsonNode result) implements AgentEvent {}

  /** Places a tool found, to draw on the map. */
  @JsonTypeName("features")
  record Features(String resultId, String tool, List<Place> places) implements AgentEvent {}

  /** Tokens used by the whole answer, all model calls included. */
  @JsonTypeName("usage")
  record Usage(int inputTokens, int outputTokens, int toolCalls) implements AgentEvent {}

  /** The answer is complete. */
  @JsonTypeName("done")
  record Done() implements AgentEvent {}

  /**
   * The answer stopped; the message is safe to show. Named {@code Failure} so the generated
   * TypeScript doesn't shadow the global {@code Error}; on the wire it is {@code "error"}.
   */
  @JsonTypeName("error")
  record Failure(String message) implements AgentEvent {}
}
