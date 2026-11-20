package io.github.brunogutierre.atlasmtl.agent;

import com.anthropic.models.messages.ToolChoice;
import com.anthropic.models.messages.ToolChoiceNone;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
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
 * that each tool call is visible as it happens and the number of calls is capped. See
 * docs/adr/0009-own-tool-loop.md.
 */
@Service
public class Agent {

  /** The most tool calls one answer may make. */
  static final int MAX_TOOL_CALLS = 6;

  /**
   * The most model calls one answer may make: one per tool call, plus the final answer, plus one
   * last call without tools after the limit. A backstop against a model that keeps asking.
   */
  static final int MAX_MODEL_CALLS = MAX_TOOL_CALLS + 2;

  static final String FAILURE = "The assistant couldn't answer. Please try again.";

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
            new Run(question, sink::next, cancelled).execute();
            sink.complete();
          });
    });
  }

  /** One answer: its history, counters and events. */
  private final class Run {
    private final String question;
    private final Consumer<AgentEvent> emit;
    private final AtomicBoolean cancelled;
    private int inputTokens;
    private int outputTokens;
    private int toolCalls;
    private int modelCalls;

    Run(String question, Consumer<AgentEvent> emit, AtomicBoolean cancelled) {
      this.question = question;
      this.emit = emit;
      this.cancelled = cancelled;
    }

    void execute() {
      try {
        loop();
        emit.accept(usage());
        emit.accept(new AgentEvent.Done());
      } catch (RuntimeException e) {
        // The question itself stays out of the logs; its fingerprint links related lines.
        log.warn("An answer failed (question {})", fingerprint(question), e);
        emit.accept(usage()); // failed answers still cost tokens
        emit.accept(new AgentEvent.Error(FAILURE));
      }
    }

    private void loop() {
      List<Message> history =
          new ArrayList<>(List.of(new SystemMessage(systemPrompt), new UserMessage(question)));
      boolean limitReached = false;
      while (!cancelled.get() && modelCalls < MAX_MODEL_CALLS) {
        Prompt prompt = new Prompt(history, limitReached ? finalOptions() : options());
        AssistantMessage message = stream(prompt);
        List<AssistantMessage.ToolCall> calls = message.getToolCalls();
        if (calls.isEmpty() || cancelled.get()) {
          return;
        }
        calls.forEach(
            c -> emit.accept(new AgentEvent.ToolCall(c.id(), c.name(), parse(c.arguments()))));
        if (limitReached) {
          // It asked again although tools were off: stop here rather than pay for more turns.
          return;
        }
        List<Message> next;
        if (toolCalls + calls.size() > MAX_TOOL_CALLS) {
          next = refuse(history, message);
          limitReached = true;
        } else {
          var response = new ChatResponse(List.of(new Generation(message)));
          next = toolCalling.executeToolCalls(prompt, response).conversationHistory();
        }
        toolCalls += calls.size();
        if (next.getLast() instanceof ToolResponseMessage responses) {
          responses.getResponses().forEach(this::report);
        }
        history = new ArrayList<>(next);
      }
    }

    /** One model call, streamed: text goes out as tokens, tool calls are collected. */
    private AssistantMessage stream(Prompt prompt) {
      modelCalls++;
      var text = new StringBuilder();
      var calls = new ArrayList<AssistantMessage.ToolCall>();
      int input = 0;
      int output = 0;
      for (ChatResponse response : model.stream(prompt).toIterable()) {
        if (cancelled.get()) {
          break; // leaving the loop cancels the model's stream
        }
        for (Generation generation : response.getResults()) {
          AssistantMessage message = generation.getOutput();
          String chunk = message.getText();
          if (chunk != null && !chunk.isEmpty()) {
            text.append(chunk);
            emit.accept(new AgentEvent.Token(chunk));
          }
          calls.addAll(message.getToolCalls());
        }
        var usage = response.getMetadata().getUsage();
        if (usage != null) {
          // Streams report running totals, so the last (largest) value is the turn's.
          input = Math.max(input, nonNull(usage.getPromptTokens()));
          output = Math.max(output, nonNull(usage.getCompletionTokens()));
        }
      }
      inputTokens += input;
      outputTokens += output;
      return AssistantMessage.builder().content(text.toString()).toolCalls(calls).build();
    }

    private void report(ToolResponseMessage.ToolResponse response) {
      JsonNode result = parse(response.responseData());
      emit.accept(new AgentEvent.ToolResult(response.id(), response.name(), result));
      JsonNode resultId = result.get("resultId");
      if (resultId != null && resultId.isString()) {
        store
            .take(resultId.asString())
            .ifPresent(r -> emit.accept(new AgentEvent.Features(r.id(), r.tool(), r.places())));
      }
    }

    private AgentEvent.Usage usage() {
      return new AgentEvent.Usage(inputTokens, outputTokens, toolCalls);
    }
  }

  /**
   * The model's configured defaults (model name, max tokens, temperature) plus the tools. Options
   * passed with a prompt replace the defaults rather than adding to them, so they must start from a
   * copy of the defaults: a bare {@code ToolCallingChatOptions} sent the provider's default model
   * and no tool definitions at all.
   */
  private ToolCallingChatOptions.Builder<?> defaults() {
    return model.getOptions() instanceof ToolCallingChatOptions options
        ? options.mutate()
        : ToolCallingChatOptions.builder();
  }

  private ChatOptions options() {
    return defaults().toolCallbacks(tools).build();
  }

  /**
   * The call after the tool limit. Anthropic rejects a conversation with tool calls but no tool
   * definitions, so the tools stay defined and {@code tool_choice: none} forbids using them.
   */
  private ChatOptions finalOptions() {
    if (model.getOptions() instanceof AnthropicChatOptions anthropic) {
      return anthropic
          .mutate()
          .toolCallbacks(tools)
          .toolChoice(ToolChoice.ofNone(ToolChoiceNone.builder().build()))
          .build();
    }
    return options();
  }

  /** Answers every pending call with an error, so the model wraps up with what it has. */
  private static List<Message> refuse(List<Message> history, AssistantMessage message) {
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

  private JsonNode parse(String text) {
    try {
      return json.readTree(text == null || text.isBlank() ? "{}" : text);
    } catch (RuntimeException e) {
      return json.getNodeFactory().stringNode(text);
    }
  }

  /** A short, stable fingerprint of a question for logs: no content, only correlation. */
  static String fingerprint(String question) {
    try {
      byte[] digest =
          java.security.MessageDigest.getInstance("SHA-256")
              .digest(question.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, 4) + "/" + question.length() + "ch";
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static int nonNull(Integer value) {
    return value == null ? 0 : value;
  }
}
