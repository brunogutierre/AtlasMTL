package io.github.brunogutierre.atlasmtl.agent;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.brunogutierre.atlasmtl.TestDatabase;
import io.github.brunogutierre.atlasmtl.agent.ScriptedChatModel.Turn;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@Import({TestDatabase.class, ScriptedModelConfig.class})
@Sql(scripts = "/geo-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(
    statements =
        "delete from query_log; delete from answer_cache; delete from feature; delete from borough;"
            + " delete from dataset",
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class AnswersTest {

  @Autowired Answers answers;
  @Autowired ScriptedChatModel model;
  @Autowired JdbcClient jdbc;

  @BeforeEach
  void emptyCache() {
    jdbc.sql("delete from answer_cache").update();
    jdbc.sql("delete from query_log").update();
  }

  private List<AgentEvent> ask(String question) {
    return answers.ask(question, "visitor-1").collectList().block(Duration.ofSeconds(30));
  }

  @Test
  void replaysARepeatedQuestionWithoutCallingTheModel() {
    model.play(
        Turn.call("find_features", "{\"category\": \"SCHOOL\", \"borough\": \"plateau\"}"),
        Turn.text("Two schools."));

    List<AgentEvent> first = ask("Écoles dans le Plateau ?");
    List<AgentEvent> again = ask("  ecoles DANS le plateau  ");

    assertThat(model.prompts).hasSize(2); // both turns of the first answer, none for the replay
    assertThat(again).hasSameSizeAs(first);
    assertThat(again).filteredOn(AgentEvent.Features.class::isInstance).hasSize(1);
    assertThat(again).contains(new AgentEvent.Usage(0, 0, 1)).endsWith(new AgentEvent.Done());
    assertThat(jdbc.sql("select hits from answer_cache").query(Integer.class).single()).isOne();
  }

  @Test
  void neverCachesAFailedAnswer() {
    model.play();
    model.failure = new IllegalStateException("provider down");

    ask("Parks?");

    assertThat(jdbc.sql("select count(*) from answer_cache").query(Long.class).single()).isZero();
  }

  @Test
  void keysIgnoreCaseAccentsAndPunctuation() {
    assertThat(AnswerCache.key("Parcs à Verdun ?")).isEqualTo("parcs a verdun");
    assertThat(AnswerCache.key("Which borough has the most parks per km²?"))
        .isEqualTo("which borough has the most parks per km2");
  }

  @Test
  void logsEveryAnswerWithItsToolsCostAndOutcome() throws InterruptedException {
    model.play(
        Turn.call("count_by_borough", "{\"category\": \"PARK\"}"), Turn.text("Plateau leads."));

    ask("Parks per borough?");
    ask("parks per borough");
    model.play();
    model.failure = new IllegalStateException("down");
    ask("Something else");
    awaitRows(3); // doFinally logs after the subscriber already saw the end of the stream

    var rows =
        jdbc.sql(
                "select outcome, cached, tools::text, input_tokens, cost_usd::text, visitor"
                    + " from query_log order by id")
            .query((rs, i) ->
                List.of(
                    rs.getString(1),
                    rs.getBoolean(2),
                    rs.getString(3),
                    rs.getInt(4),
                    rs.getString(5),
                    rs.getString(6)))
            .list();
    assertThat(rows)
        .containsExactly(
            List.of("done", false, "[\"count_by_borough\"]", 200, "0.000400", "visitor-1"),
            List.of("done", true, "[\"count_by_borough\"]", 0, "0.000000", "visitor-1"),
            List.of("error", false, "[]", 0, "0.000000", "visitor-1"));
  }

  @Test
  void logsAnAnswerTheVisitorLeft() throws InterruptedException {
    model.play(Turn.text("A long", " answer"));

    answers.ask("Leave early", "visitor-2").take(1).blockLast(Duration.ofSeconds(10));
    awaitRows(1);

    assertThat(jdbc.sql("select outcome from query_log").query(String.class).single())
        .isEqualTo("cancelled");
  }

  private void awaitRows(long expected) throws InterruptedException {
    for (int i = 0; i < 50; i++) {
      if (jdbc.sql("select count(*) from query_log").query(Long.class).single() >= expected) {
        return;
      }
      Thread.sleep(50);
    }
  }
}
