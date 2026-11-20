package io.github.brunogutierre.atlasmtl.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResultStoreTest {

  static class TestClock extends Clock {
    Instant now = Instant.parse("2026-11-20T12:00:00Z");

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  @Test
  void handsEachResultOutOnce() {
    var store = new ResultStore();
    String id = store.put("find_features", List.of());

    assertThat(store.take(id)).isPresent();
    assertThat(store.take(id)).isEmpty();
  }

  @Test
  void dropsResultsNobodyTookWithinFiveMinutes() {
    var clock = new TestClock();
    var store = new ResultStore(clock);
    String id = store.put("find_features", List.of());

    clock.now = clock.now.plus(Duration.ofMinutes(6));
    assertThat(store.take(id)).isEmpty();
  }

  @Test
  void keepsAtMostAHundredResults() {
    var store = new ResultStore();
    String first = store.put("find_features", List.of());
    for (int i = 0; i < ResultStore.MAX_RESULTS; i++) {
      store.put("find_features", List.of());
    }

    assertThat(store.take(first)).isEmpty();
  }
}
