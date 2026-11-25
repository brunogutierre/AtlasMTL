package io.github.brunogutierre.atlasmtl.usage;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for usage accounting.
 *
 * @param inputUsdPerMillion the model's price per million input tokens, in US dollars
 * @param outputUsdPerMillion the price per million output tokens
 * @param visitorKey the secret that keys visitor hashes; empty means a random key per run
 * @param clientIpHeader a header that an edge proxy overwrites with the visitor's address, such as
 *     Cloudflare's {@code CF-Connecting-IP}; empty means the connection's address. Set it only
 *     behind a proxy that overwrites it, or visitors could pick their own address.
 */
@ConfigurationProperties("atlas.usage")
public record UsageProperties(
    BigDecimal inputUsdPerMillion,
    BigDecimal outputUsdPerMillion,
    String visitorKey,
    String clientIpHeader) {

  public UsageProperties {
    inputUsdPerMillion = inputUsdPerMillion == null ? new BigDecimal("0.10") : inputUsdPerMillion;
    outputUsdPerMillion =
        outputUsdPerMillion == null ? new BigDecimal("0.50") : outputUsdPerMillion;
    visitorKey = visitorKey == null ? "" : visitorKey;
    clientIpHeader = clientIpHeader == null ? "" : clientIpHeader.strip();
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
