package io.github.brunogutierre.atlasmtl.usage;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Service;

/**
 * The public demo's limits: a per-visitor rate, a per-visitor daily token budget and a global daily
 * spend. Cached answers cost nothing, so only the rate applies to them.
 */
@Service
public class Limits {

  private final LimitProperties properties;
  private final QueryLog log;
  private final Cache<String, Bucket> buckets =
      Caffeine.newBuilder().maximumSize(10_000).expireAfterAccess(Duration.ofHours(1)).build();

  public Limits(LimitProperties properties, QueryLog log) {
    this.properties = properties;
    this.log = log;
  }

  /** Counts a question against the visitor's rate. */
  public void checkRate(String visitor) {
    ConsumptionProbe probe =
        buckets.get(visitor, key -> newBucket()).tryConsumeAndReturnRemaining(1);
    if (!probe.isConsumed()) {
      long seconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
      throw new LimitExceeded(
          LimitExceeded.Code.RATE_LIMITED, "Too many questions; wait a moment.", seconds);
    }
  }

  /** Before a model call: the visitor's tokens today and everyone's spend today. */
  public void checkBudgets(String visitor) {
    if (log.costToday().compareTo(properties.globalDailyUsd()) >= 0) {
      throw new LimitExceeded(
          LimitExceeded.Code.GLOBAL_BUDGET,
          "Today's budget for new answers is spent; the example questions still work.",
          secondsUntilTomorrow());
    }
    if (log.tokensToday(visitor) >= properties.visitorDailyTokens()) {
      throw new LimitExceeded(
          LimitExceeded.Code.VISITOR_BUDGET,
          "You have reached today's limit; come back tomorrow.",
          secondsUntilTomorrow());
    }
  }

  private Bucket newBucket() {
    int perMinute = properties.questionsPerMinute();
    return Bucket.builder()
        .addLimit(
            Bandwidth.builder()
                .capacity(perMinute)
                .refillGreedy(perMinute, Duration.ofMinutes(1))
                .build())
        .build();
  }

  private static long secondsUntilTomorrow() {
    ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
    ZonedDateTime midnight =
        now.toLocalDate().plusDays(1).atTime(LocalTime.MIDNIGHT).atZone(ZoneOffset.UTC);
    return Duration.between(now, midnight).toSeconds();
  }
}
