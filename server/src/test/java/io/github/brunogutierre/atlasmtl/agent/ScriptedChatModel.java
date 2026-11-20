package io.github.brunogutierre.atlasmtl.agent;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * A model that plays back scripted turns, so the agent loop is tested without an API key or any
 * randomness. Each turn streams its text in pieces, then its tool calls, then usage.
 */
public class ScriptedChatModel implements ChatModel {

  public record Turn(List<String> text, List<AssistantMessage.ToolCall> toolCalls) {
    public static Turn text(String... pieces) {
      return new Turn(List.of(pieces), List.of());
    }

    /** Several calls in one turn, given as tool, arguments, tool, arguments... */
    public static Turn calls(String... toolsAndArguments) {
      var calls = new ArrayList<AssistantMessage.ToolCall>();
      for (int i = 0; i < toolsAndArguments.length; i += 2) {
        calls.add(
            new AssistantMessage.ToolCall(
                "call-" + i, "function", toolsAndArguments[i], toolsAndArguments[i + 1]));
      }
      return new Turn(List.of(), calls);
    }

    public static Turn call(String tool, String arguments) {
      return new Turn(
          List.of(),
          List.of(new AssistantMessage.ToolCall("call-" + tool, "function", tool, arguments)));
    }
  }

  public final Deque<Turn> script = new ConcurrentLinkedDeque<>();
  public final List<Prompt> prompts = new ArrayList<>();
  public RuntimeException failure;

  public void play(Turn... turns) {
    script.clear();
    prompts.clear();
    failure = null;
    script.addAll(List.of(turns));
  }

  @Override
  public ChatResponse call(Prompt prompt) {
    throw new UnsupportedOperationException("the agent streams");
  }

  @Override
  public Flux<ChatResponse> stream(Prompt prompt) {
    prompts.add(prompt);
    if (failure != null) {
      return Flux.error(failure);
    }
    Turn turn = script.removeFirst();
    var responses = new ArrayList<ChatResponse>();
    for (String piece : turn.text()) {
      responses.add(response(AssistantMessage.builder().content(piece).build(), null));
    }
    responses.add(
        response(
            AssistantMessage.builder().content("").toolCalls(turn.toolCalls()).build(),
            new DefaultUsage(100, 20)));
    return Flux.fromIterable(responses);
  }

  private static ChatResponse response(AssistantMessage message, DefaultUsage usage) {
    var metadata = ChatResponseMetadata.builder();
    if (usage != null) {
      metadata.usage(usage);
    }
    return new ChatResponse(List.of(new Generation(message)), metadata.build());
  }
}
