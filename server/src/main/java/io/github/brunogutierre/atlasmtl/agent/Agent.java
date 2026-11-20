package io.github.brunogutierre.atlasmtl.agent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Answers a question by letting the model call the tools, one turn at a time, and turning every
 * step into {@link AgentEvent}s. The loop is ours rather than Spring AI's tool-calling advisor, so
 * that each tool call is visible as it happens and the number of calls is capped.
 */
@Service
public class Agent {

  /** The most tool calls one answer may make. */
  static final int MAX_TOOL_CALLS = 6;

  private static final Logger log = LoggerFactory.getLogger(Agent.class);

  private final ChatModel model;
  private final ToolCallingManager toolCalling;
  private final ResultStore store;
  private final JsonMapper json;
  private final List<ToolCallback> tools;
  private final String systemPrompt;

  public Agent(
      ChatModel model,
      ToolCallingManager toolCalling,
      AtlasTools atlasTools,
      ResultStore store,
      JsonMapper json,
      @Value("classpath:prompts/system.md") Resource systemPrompt) {
    this.model = model;
    this.toolCalling = toolCalling;
    this.store = store;
    this.json = json;
    this.tools = List.of(ToolCallbacks.from(atlasTools));
    try {
      this.systemPrompt = systemPrompt.getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The answer as a stream of events, produced on a virtual thread. */
  public Flux<AgentEvent> ask(String question) {
    return Flux.create(sink -> {
      var cancelled = new AtomicBoolean();
      sink.onDispose(() -> cancelled.set(true));
      Thread.ofVirtual()
          .name("agent")
          .start(() -> {
            try {
              run(question, sink::next, cancelled);
            } catch (RuntimeException e) {
              log.warn("The answer to '{}' failed", question, e);
              sink.next(new AgentEvent.Error("The assistant couldn't answer. Please try again."));
            }
            sink.complete();
          });
    });
  }

  void run(String question, Consumer<AgentEvent> emit, AtomicBoolean cancelled) {
    List<Message> history =
        new ArrayList<>(List.of(new SystemMessage(systemPrompt), new UserMessage(question)));
    int input = 0;
    int output = 0;
    int calls = 0;
    while (!cancelled.get()) {
      Prompt prompt =
          new Prompt(history, ToolCallingChatOptions.builder().toolCallbacks(tools).build());
      Turn turn = stream(prompt, emit);
      input += turn.inputTokens();
      output += turn.outputTokens();
      if (turn.message().getToolCalls().isEmpty()) {
        break;
      }
      for (AssistantMessage.ToolCall call : turn.message().getToolCalls()) {
        emit.accept(new AgentEvent.ToolCall(call.id(), call.name(), parse(call.arguments())));
      }
      ChatResponse response = new ChatResponse(List.of(new Generation(turn.message())));
      List<Message> next;
      if (calls + turn.message().getToolCalls().size() > MAX_TOOL_CALLS) {
        next = refuse(history, turn.message());
      } else {
        ToolExecutionResult result = toolCalling.executeToolCalls(prompt, response);
        next = result.conversationHistory();
      }
      calls += turn.message().getToolCalls().size();
      if (next.getLast() instanceof ToolResponseMessage responses) {
        responses.getResponses().forEach(r -> reportResult(r, emit));
      }
      history = new ArrayList<>(next);
    }
    emit.accept(new AgentEvent.Usage(input, output, calls));
    emit.accept(new AgentEvent.Done());
  }

  /** One model call, streamed: text goes out as tokens, tool calls are collected. */
  private Turn stream(Prompt prompt, Consumer<AgentEvent> emit) {
    var text = new StringBuilder();
    var toolCalls = new ArrayList<AssistantMessage.ToolCall>();
    int input = 0;
    int output = 0;
    for (ChatResponse response : model.stream(prompt).toIterable()) {
      for (Generation generation : response.getResults()) {
        AssistantMessage message = generation.getOutput();
        String chunk = message.getText();
        if (chunk != null && !chunk.isEmpty()) {
          text.append(chunk);
          emit.accept(new AgentEvent.Token(chunk));
        }
        toolCalls.addAll(message.getToolCalls());
      }
      var usage = response.getMetadata().getUsage();
      if (usage != null) {
        input = Math.max(input, nonNull(usage.getPromptTokens()));
        output = Math.max(output, nonNull(usage.getCompletionTokens()));
      }
    }
    var message = AssistantMessage.builder().content(text.toString()).toolCalls(toolCalls).build();
    return new Turn(message, input, output);
  }

  /** Answers every pending call with an error, so the model wraps up with what it has. */
  private List<Message> refuse(List<Message> history, AssistantMessage message) {
    var responses =
        message.getToolCalls().stream()
            .map(call ->
                new ToolResponseMessage.ToolResponse(
                    call.id(),
                    call.name(),
                    "{\"error\":\"Tool call limit reached\",\"hint\":\"Answer now with the"
                        + " results you already have.\"}"))
            .toList();
    var next = new ArrayList<>(history);
    next.add(message);
    next.add(ToolResponseMessage.builder().responses(responses).build());
    return next;
  }

  private void reportResult(ToolResponseMessage.ToolResponse response, Consumer<AgentEvent> emit) {
    JsonNode result = parse(response.responseData());
    emit.accept(new AgentEvent.ToolResult(response.id(), response.name(), result));
    JsonNode resultId = result.get("resultId");
    if (resultId != null && resultId.isString()) {
      store
          .get(resultId.asString())
          .ifPresent(r -> emit.accept(new AgentEvent.Features(r.id(), r.tool(), r.places())));
    }
  }

  private JsonNode parse(String text) {
    try {
      return json.readTree(text == null || text.isBlank() ? "{}" : text);
    } catch (RuntimeException e) {
      return json.getNodeFactory().stringNode(text);
    }
  }

  private static int nonNull(Integer value) {
    return value == null ? 0 : value;
  }

  private record Turn(AssistantMessage message, int inputTokens, int outputTokens) {}
}
