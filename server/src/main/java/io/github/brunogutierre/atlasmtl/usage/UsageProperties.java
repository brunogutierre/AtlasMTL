package io.github.brunogutierre.atlasmtl.usage;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for usage accounting.
 *
 * @param inputUsdPerMillion the model's price per million input tokens, in US dollars
 * @param outputUsdPerMillion the price per million output tokens
 * @param visitorKey the secret that keys visitor hashes; empty means a random key per run
 */
@ConfigurationProperties("atlas.usage")
public record UsageProperties(
    BigDecimal inputUsdPerMillion, BigDecimal outputUsdPerMillion, String visitorKey) {

  public UsageProperties {
    inputUsdPerMillion = inputUsdPerMillion == null ? BigDecimal.ONE : inputUsdPerMillion;
    outputUsdPerMillion = outputUsdPerMillion == null ? BigDecimal.valueOf(5) : outputUsdPerMillion;
    visitorKey = visitorKey == null ? "" : visitorKey;
  }

  /** The estimated cost of an answer. */
  public BigDecimal cost(int inputTokens, int outputTokens) {
    BigDecimal million = BigDecimal.valueOf(1_000_000);
    return inputUsdPerMillion
        .multiply(BigDecimal.valueOf(inputTokens))
        .add(outputUsdPerMillion.multiply(BigDecimal.valueOf(outputTokens)))
        .divide(million, 6, java.math.RoundingMode.HALF_UP);
  }
}
