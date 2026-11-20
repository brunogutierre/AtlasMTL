package io.github.brunogutierre.atlasmtl.usage;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limits that keep the public demo affordable.
 *
 * @param questionsPerMinute how often one visitor may ask, cached answers included
 * @param visitorDailyTokens model tokens one visitor may use per day (UTC)
 * @param globalDailyUsd the estimated model spend per day for everyone; past it, only cached
 *     answers are served
 */
@ConfigurationProperties("atlas.limits")
public record LimitProperties(
    int questionsPerMinute, int visitorDailyTokens, BigDecimal globalDailyUsd) {

  public LimitProperties {
    questionsPerMinute = questionsPerMinute <= 0 ? 5 : questionsPerMinute;
    visitorDailyTokens = visitorDailyTokens <= 0 ? 60_000 : visitorDailyTokens;
    globalDailyUsd = globalDailyUsd == null ? new BigDecimal("2.00") : globalDailyUsd;
  }
}
