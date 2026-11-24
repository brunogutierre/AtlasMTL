package io.github.brunogutierre.atlasmtl.agent;

import io.github.brunogutierre.atlasmtl.usage.QueryLog;
import io.github.brunogutierre.atlasmtl.usage.UsageProperties;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

/**
 * Answers a question from the cache when possible, otherwise with the agent; caches complete
 * answers and logs every one, cancelled ones included.
 */
@Service
public class Answers {

  private static final Logger log = LoggerFactory.getLogger(Answers.class);

  private final Agent agent;
  private final AnswerCache cache;
  private final QueryLog queryLog;
  private final UsageProperties prices;

  public Answers(Agent agent, AnswerCache cache, QueryLog queryLog, UsageProperties prices) {
    this.agent = agent;
    this.cache = cache;
    this.queryLog = queryLog;
    this.prices = prices;
  }

  /**
   * The answer's events.
   *
   * @param visitor the pseudonymous visitor id, for the log and the budgets
   */
  public Flux<AgentEvent> ask(String question, String visitor) {
    long started = System.nanoTime();
    var cached = cache.get(question);
    var events = new ArrayList<AgentEvent>();
    Flux<AgentEvent> answer =
        cached.isPresent() ? Flux.fromIterable(replay(cached.get())) : live(question, events);
    return answer
        .doOnNext(cached.isPresent() ? events::add : e -> {})
        .doFinally(
            signal -> record(question, visitor, cached.isPresent(), events, signal, started));
  }

  private Flux<AgentEvent> live(String question, List<AgentEvent> events) {
    return agent
        .ask(question)
        .doOnNext(events::add)
        .doOnComplete(() -> {
          if (!events.isEmpty() && events.getLast() instanceof AgentEvent.Done) {
            try {
              cache.put(question, events);
            } catch (RuntimeException e) {
              log.warn("Couldn't cache an answer", e); // the visitor already has it
            }
          }
        });
  }

  private void record(
      String question,
      String visitor,
      boolean cached,
      List<AgentEvent> events,
      SignalType signal,
      long started) {
    String outcome =
        signal == SignalType.CANCEL
            ? "cancelled"
            : !events.isEmpty() && events.getLast() instanceof AgentEvent.Done ? "done" : "error";
    var usage =
        events.stream()
            .filter(AgentEvent.Usage.class::isInstance)
            .map(AgentEvent.Usage.class::cast)
            .reduce((first, second) -> second)
            .orElse(new AgentEvent.Usage(0, 0, 0));
    List<String> tools =
        events.stream()
            .filter(AgentEvent.ToolCall.class::isInstance)
            .map(e -> ((AgentEvent.ToolCall) e).tool())
            .toList();
    try {
      queryLog.record(
          new QueryLog.Entry(
              visitor,
              question,
              outcome,
              cached,
              tools,
              usage.inputTokens(),
              usage.outputTokens(),
              prices.cost(usage.inputTokens(), usage.outputTokens()),
              (int) ((System.nanoTime() - started) / 1_000_000)));
    } catch (RuntimeException e) {
      log.warn("Couldn't log an answer", e);
    }
  }

  /** The original events, with the usage set to zero tokens: a replay costs nothing. */
  static List<AgentEvent> replay(List<AgentEvent> events) {
    return events.stream()
        .map(event ->
            event instanceof AgentEvent.Usage usage
                ? new AgentEvent.Usage(0, 0, usage.toolCalls())
                : event)
        .toList();
  }
}
