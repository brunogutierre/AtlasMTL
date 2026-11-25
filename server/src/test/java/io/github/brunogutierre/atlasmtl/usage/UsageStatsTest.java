package io.github.brunogutierre.atlasmtl.usage;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.brunogutierre.atlasmtl.TestDatabase;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@Import(TestDatabase.class)
@Sql(statements = "delete from query_log")
class UsageStatsTest {

  @Autowired UsageStats stats;
  @Autowired QueryLog log;

  private void answer(boolean cached, List<String> tools, String cost, int latency) {
    log.record(
        new QueryLog.Entry(
            "v", "q", "done", cached, tools, 100, 10, new BigDecimal(cost), latency));
  }

  @Test
  void aggregatesCompleteAnswersOnly() {
    answer(false, List.of("geocode", "find_features"), "0.002", 1000);
    answer(true, List.of("find_features"), "0", 20);
    answer(false, List.of("count_by_borough"), "0.001", 3000);
    log.record(new QueryLog.Entry("v", "q", "error", false, List.of(), 0, 0, BigDecimal.ZERO, 5));

    Stats result = stats.stats();

    assertThat(result.questions()).isEqualTo(3);
    assertThat(result.cachedShare()).isEqualTo(0.333);
    assertThat(result.averageCostUsd()).isEqualByComparingTo("0.001");
    assertThat(result.medianLatencyMs()).isEqualTo(1000);
    assertThat(result.averageToolCalls()).isEqualTo(1.3);
    assertThat(result.tools().getFirst()).isEqualTo(new Stats.ToolUse("find_features", 2));
    assertThat(result.days()).hasSize(14);
    assertThat(result.days().getLast().questions()).isEqualTo(4);
  }

  @Test
  void costsFollowTheConfiguredPrices() {
    var prices = new UsageProperties(new BigDecimal("1"), new BigDecimal("5"), "", "");

    assertThat(prices.cost(1_000_000, 100_000)).isEqualByComparingTo("1.5");
  }
}
