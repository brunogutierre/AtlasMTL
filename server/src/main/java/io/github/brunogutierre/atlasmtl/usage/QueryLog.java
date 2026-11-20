package io.github.brunogutierre.atlasmtl.usage;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Writes one row per answered question. */
@Component
public class QueryLog {

  /** One answer, as logged. */
  public record Entry(
      String visitor,
      String question,
      String outcome,
      boolean cached,
      List<String> tools,
      int inputTokens,
      int outputTokens,
      BigDecimal costUsd,
      int latencyMs) {}

  private final JdbcClient jdbc;
  private final JsonMapper json;

  public QueryLog(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public void record(Entry entry) {
    jdbc.sql(
            """
            insert into query_log (visitor, question, outcome, cached, tools, input_tokens,
              output_tokens, cost_usd, latency_ms)
            values (:visitor, :question, :outcome, :cached, cast(:tools as jsonb), :input,
              :output, :cost, :latency)
            """)
        .param("visitor", entry.visitor())
        .param("question", entry.question())
        .param("outcome", entry.outcome())
        .param("cached", entry.cached())
        .param("tools", json.writeValueAsString(entry.tools()))
        .param("input", entry.inputTokens())
        .param("output", entry.outputTokens())
        .param("cost", entry.costUsd())
        .param("latency", entry.latencyMs())
        .update();
  }

  /** Model tokens the visitor used since midnight UTC. */
  public long tokensToday(String visitor) {
    return jdbc.sql(
            """
            select coalesce(sum(input_tokens + output_tokens), 0) from query_log
            where visitor = :visitor and asked_at >= date_trunc('day', now() at time zone 'utc')
              at time zone 'utc'
            """)
        .param("visitor", visitor)
        .query(Long.class)
        .single();
  }

  /** Everyone's estimated model spend since midnight UTC. */
  public BigDecimal costToday() {
    return jdbc.sql(
            """
            select coalesce(sum(cost_usd), 0) from query_log
            where asked_at >= date_trunc('day', now() at time zone 'utc') at time zone 'utc'
            """)
        .query(BigDecimal.class)
        .single();
  }
}
