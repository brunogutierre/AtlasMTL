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
 * Keeps the full results of tool calls for the map, while the model only sees a summary and the
 * result id. Bounded in size and age: results are needed for a few minutes, not forever.
 */
@Component
public class ResultStore {

  static final int MAX_RESULTS = 1_000;
  static final Duration MAX_AGE = Duration.ofMinutes(30);

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

  public synchronized Optional<Result> get(String id) {
    Result result = results.get(id);
    if (result == null || result.storedAt().plus(MAX_AGE).isBefore(clock.instant())) {
      results.remove(id);
      return Optional.empty();
    }
    return Optional.of(result);
  }
}
