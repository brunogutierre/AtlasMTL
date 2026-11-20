package io.github.brunogutierre.atlasmtl.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.brunogutierre.atlasmtl.TestDatabase;
import io.github.brunogutierre.atlasmtl.agent.ScriptedChatModel;
import io.github.brunogutierre.atlasmtl.agent.ScriptedChatModel.Turn;
import io.github.brunogutierre.atlasmtl.agent.ScriptedModelConfig;
import io.github.brunogutierre.atlasmtl.usage.Visitors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "atlas.limits.questions-per-minute=100",
      "atlas.limits.visitor-daily-tokens=1000",
      "atlas.limits.global-daily-usd=0.05"
    })
@Import({TestDatabase.class, ScriptedModelConfig.class})
class BudgetTest {

  @LocalServerPort int port;
  @Autowired ScriptedChatModel model;
  @Autowired JdbcClient jdbc;
  @Autowired Visitors visitors;

  @BeforeEach
  void clean() {
    jdbc.sql("delete from query_log").update();
    jdbc.sql("delete from answer_cache").update();
  }

  private void spent(String visitor, int tokens, String usd) {
    jdbc.sql(
            """
            insert into query_log (visitor, question, outcome, cached, tools, input_tokens,
              output_tokens, cost_usd, latency_ms)
            values (:visitor, 'q', 'done', false, '[]', :tokens, 0, cast(:usd as numeric), 1)
            """)
        .param("visitor", visitor)
        .param("tokens", tokens)
        .param("usd", usd)
        .update();
  }

  @Test
  void refusesAVisitorOverTheDailyTokens() throws Exception {
    spent(visitors.id("127.0.0.1"), 1000, "0.001");

    var response = AskHttp.ask(port, "More parks?");

    assertThat(response.statusCode()).isEqualTo(429);
    assertThat(response.body()).contains("\"code\":\"visitor_budget\"");
  }

  @Test
  void servesOnlyCachedAnswersOnceTheGlobalBudgetIsSpent() throws Exception {
    spent("someone-else", 10, "0.05");
    jdbc.sql(
            """
            insert into answer_cache (question_key, question, events)
            values ('parks in verdun', 'Parks in Verdun?',
              '[{"type":"token","text":"Verdun has parks."},{"type":"done"}]')
            """)
        .update();
    model.play(Turn.text("must not be called"));

    var fresh = AskHttp.ask(port, "Schools in Anjou?");
    var cached = AskHttp.ask(port, "parks in Verdun");

    assertThat(fresh.statusCode()).isEqualTo(429);
    assertThat(fresh.body()).contains("\"code\":\"global_budget\"");
    assertThat(cached.statusCode()).isEqualTo(200);
    assertThat(cached.body()).contains("Verdun has parks.");
    assertThat(model.prompts).isEmpty();
  }
}
