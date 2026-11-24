package io.github.brunogutierre.atlasmtl.usage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Aggregates the query log for the public /stats page. */
@Service
public class UsageStats {

  private final JdbcClient jdbc;

  public UsageStats(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Stats stats() {
    var summary =
        jdbc.sql(
                """
                select count(*) as questions,
                  count(*) filter (where asked_at > now() - interval '7 days') as week,
                  coalesce(avg(case when cached then 1.0 else 0.0 end), 0) as cached,
                  coalesce(avg(cost_usd), 0) as cost,
                  coalesce(percentile_cont(0.5) within group (order by latency_ms), 0) as p50,
                  coalesce(percentile_cont(0.95) within group (order by latency_ms), 0) as p95,
                  coalesce(avg(jsonb_array_length(tools)), 0) as tools
                from query_log
                where outcome = 'done'
                """)
            .query((rs, i) ->
                new Object[] {
                  rs.getLong("questions"),
                  rs.getLong("week"),
                  rs.getDouble("cached"),
                  rs.getBigDecimal("cost"),
                  (int) Math.round(rs.getDouble("p50")),
                  (int) Math.round(rs.getDouble("p95")),
                  rs.getDouble("tools")
                })
            .single();
    List<Stats.ToolUse> tools =
        jdbc.sql(
                """
                select tool, count(*) as calls
                from query_log, jsonb_array_elements_text(tools) as tool
                group by tool order by calls desc, tool
                """)
            .query((rs, i) -> new Stats.ToolUse(rs.getString("tool"), rs.getLong("calls")))
            .list();
    List<Stats.Day> days =
        jdbc.sql(
                """
                select d::date as day, count(q.id) as questions
                from generate_series(current_date - 13, current_date, interval '1 day') as d
                left join query_log q on q.asked_at::date = d::date
                group by d order by d
                """)
            .query(
                (rs, i) -> new Stats.Day(rs.getDate("day").toLocalDate(), rs.getLong("questions")))
            .list();
    return new Stats(
        (long) summary[0],
        (long) summary[1],
        Math.round((double) summary[2] * 1000) / 1000.0,
        ((BigDecimal) summary[3]).setScale(6, RoundingMode.HALF_UP),
        (int) summary[4],
        (int) summary[5],
        Math.round((double) summary[6] * 10) / 10.0,
        tools,
        days);
  }
}
