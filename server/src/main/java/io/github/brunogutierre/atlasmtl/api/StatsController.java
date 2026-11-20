package io.github.brunogutierre.atlasmtl.api;

import io.github.brunogutierre.atlasmtl.usage.Stats;
import io.github.brunogutierre.atlasmtl.usage.UsageStats;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The public usage statistics: volume, cache share, cost, latency and tools. */
@RestController
@RequestMapping("/api")
public class StatsController {

  private final UsageStats stats;

  public StatsController(UsageStats stats) {
    this.stats = stats;
  }

  @GetMapping("/stats")
  public Stats stats() {
    return stats.stats();
  }
}
