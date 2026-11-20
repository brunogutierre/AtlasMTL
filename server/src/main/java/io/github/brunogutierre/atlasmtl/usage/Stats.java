package io.github.brunogutierre.atlasmtl.usage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Public, aggregate statistics: no question, visitor or single answer can be read from them.
 *
 * @param cachedShare the share of answers replayed from the cache, from 0 to 1
 * @param averageCostUsd the average estimated model cost of an answer
 */
public record Stats(
    long questions,
    long lastSevenDays,
    double cachedShare,
    BigDecimal averageCostUsd,
    int medianLatencyMs,
    int p95LatencyMs,
    double averageToolCalls,
    List<ToolUse> tools,
    List<Day> days) {

  public record ToolUse(String tool, long calls) {}

  public record Day(LocalDate day, long questions) {}
}
