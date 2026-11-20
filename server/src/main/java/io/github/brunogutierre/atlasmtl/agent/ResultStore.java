package io.github.brunogutierre.atlasmtl.agent;

import io.github.brunogutierre.atlasmtl.geo.Place;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Hands the full results of tool calls to the agent, while the model only sees a summary and the
 * result id. The agent takes each result once, right after the tool ran, so the store stays nearly
 * empty; the bounds only matter if an answer fails before taking its results.
 */
@Component
public class ResultStore {

  static final int MAX_RESULTS = 100;
  static final Duration MAX_AGE = Duration.ofMinutes(5);

  /** A stored result: what the tool found, in the order it found it. */
  public record Result(String id, String tool, List<Place> places, Instant storedAt) {}

  private final Clock clock;
  private final Map<String, Result> results =
      new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Result> eldest) {
          return size() > MAX_RESULTS;
        }
      };

  public ResultStore() {
    this(Clock.systemUTC());
  }

  ResultStore(Clock clock) {
    this.clock = clock;
  }

  /** Stores the places and returns the new result's id. */
  public synchronized String put(String tool, List<Place> places) {
    String id = "r-" + UUID.randomUUID().toString().substring(0, 8);
    results.put(id, new Result(id, tool, List.copyOf(places), clock.instant()));
    return id;
  }

  /** Removes and returns the result, if it is still there. */
  public synchronized Optional<Result> take(String id) {
    Result result = results.remove(id);
    if (result == null || result.storedAt().plus(MAX_AGE).isBefore(clock.instant())) {
      return Optional.empty();
    }
    return Optional.of(result);
  }
}
