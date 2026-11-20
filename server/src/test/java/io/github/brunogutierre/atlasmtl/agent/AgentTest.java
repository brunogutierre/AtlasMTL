package io.github.brunogutierre.atlasmtl.agent;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.brunogutierre.atlasmtl.TestDatabase;
import io.github.brunogutierre.atlasmtl.agent.ScriptedChatModel.Turn;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@Import({TestDatabase.class, AgentTest.Model.class})
@Sql(scripts = "/geo-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(
    statements = "delete from feature; delete from borough; delete from dataset",
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class AgentTest {

  @TestConfiguration(proxyBeanMethods = false)
  static class Model {
    @Bean
    @Primary
    ScriptedChatModel scriptedChatModel() {
      return new ScriptedChatModel();
    }
  }

  @Autowired Agent agent;
  @Autowired ScriptedChatModel model;

  private List<AgentEvent> ask(String question) {
    return agent.ask(question).collectList().block(Duration.ofSeconds(30));
  }

  @Test
  void streamsAPlainAnswerAsTokens() {
    model.play(Turn.text("Bonjour", " !"));

    assertThat(ask("Salut ?"))
        .containsExactly(
            new AgentEvent.Token("Bonjour"),
            new AgentEvent.Token(" !"),
            new AgentEvent.Usage(100, 20, 0),
            new AgentEvent.Done());
  }

  @Test
  void runsToolsAndReportsEveryStep() {
    model.play(
        Turn.call("find_features", "{\"category\": \"SCHOOL\", \"borough\": \"plateau\"}"),
        Turn.text("I found 2 schools in Le Plateau-Mont-Royal."));

    List<AgentEvent> events = ask("Schools in the Plateau?");

    assertThat(events)
        .extracting(e -> e.getClass().getSimpleName())
        .containsExactly("ToolCall", "ToolResult", "Features", "Token", "Usage", "Done");
    var call = (AgentEvent.ToolCall) events.get(0);
    assertThat(call.arguments().get("category").asString()).isEqualTo("SCHOOL");
    var result = (AgentEvent.ToolResult) events.get(1);
    assertThat(result.result().get("total").asInt()).isEqualTo(2);
    var features = (AgentEvent.Features) events.get(2);
    assertThat(features.places()).hasSize(2);
    assertThat(events.get(4)).isEqualTo(new AgentEvent.Usage(200, 40, 1));
    // The second model call saw the tool's answer.
    assertThat(model.prompts.get(1).getInstructions().getLast())
        .isInstanceOf(ToolResponseMessage.class);
  }

  @Test
  void stopsRunningToolsAfterTheLimit() {
    var turns = new Turn[Agent.MAX_TOOL_CALLS + 2];
    for (int i = 0; i <= Agent.MAX_TOOL_CALLS; i++) {
      turns[i] = Turn.call("count_by_borough", "{\"category\": \"PARK\"}");
    }
    turns[Agent.MAX_TOOL_CALLS + 1] = Turn.text("Here is what I found.");
    model.play(turns);

    List<AgentEvent> events = ask("Count everything, again and again");

    List<AgentEvent.ToolResult> results =
        events.stream()
            .filter(AgentEvent.ToolResult.class::isInstance)
            .map(AgentEvent.ToolResult.class::cast)
            .toList();
    assertThat(results).hasSize(Agent.MAX_TOOL_CALLS + 1);
    assertThat(results.getLast().result().get("error").asString()).contains("limit");
    assertThat(events.getLast()).isEqualTo(new AgentEvent.Done());
  }

  @Test
  void endsWithAnErrorWhenTheModelFails() {
    model.play();
    model.failure = new IllegalStateException("401 from the provider");

    List<AgentEvent> events = ask("Anything");

    assertThat(events)
        .containsExactly(new AgentEvent.Error("The assistant couldn't answer. Please try again."));
  }
}
