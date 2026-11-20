package io.github.brunogutierre.atlasmtl.agent;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/** Answers a question from the cache when possible, otherwise with the agent, and caches it. */
@Service
public class Answers {

  private static final Logger log = LoggerFactory.getLogger(Answers.class);

  private final Agent agent;
  private final AnswerCache cache;

  public Answers(Agent agent, AnswerCache cache) {
    this.agent = agent;
    this.cache = cache;
  }

  public Flux<AgentEvent> ask(String question) {
    var cached = cache.get(question);
    if (cached.isPresent()) {
      return Flux.fromIterable(replay(cached.get()));
    }
    var events = new ArrayList<AgentEvent>();
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
