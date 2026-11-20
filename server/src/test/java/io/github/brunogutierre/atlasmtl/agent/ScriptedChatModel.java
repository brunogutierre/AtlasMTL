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
class ScriptedChatModel implements ChatModel {

  record Turn(List<String> text, List<AssistantMessage.ToolCall> toolCalls) {
    static Turn text(String... pieces) {
      return new Turn(List.of(pieces), List.of());
    }

    static Turn call(String tool, String arguments) {
      return new Turn(
          List.of(),
          List.of(new AssistantMessage.ToolCall("call-" + tool, "function", tool, arguments)));
    }
  }

  final Deque<Turn> script = new ConcurrentLinkedDeque<>();
  final List<Prompt> prompts = new ArrayList<>();
  RuntimeException failure;

  void play(Turn... turns) {
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
