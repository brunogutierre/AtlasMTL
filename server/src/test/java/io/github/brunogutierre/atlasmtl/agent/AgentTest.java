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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@Import({TestDatabase.class, ScriptedModelConfig.class})
@Sql(scripts = "/geo-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(
    statements = "delete from feature; delete from borough; delete from dataset",
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class AgentTest {

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
        .containsExactly(new AgentEvent.Usage(0, 0, 0), new AgentEvent.Failure(Agent.FAILURE));
  }

  @Test
  void stopsAModelThatKeepsCallingToolsAfterTheLimit() {
    var turns = new Turn[Agent.MAX_MODEL_CALLS + 5];
    java.util.Arrays.fill(turns, Turn.call("count_by_borough", "{\"category\": \"PARK\"}"));
    model.play(turns);

    List<AgentEvent> events = ask("Never stop");

    assertThat(model.prompts).hasSizeLessThanOrEqualTo(Agent.MAX_MODEL_CALLS);
    assertThat(events.getLast()).isEqualTo(new AgentEvent.Done());
  }

  @Test
  void refusesATurnWhoseCallsWouldCrossTheLimit() {
    var turns = new java.util.ArrayList<Turn>();
    for (int i = 0; i < Agent.MAX_TOOL_CALLS - 1; i++) {
      turns.add(Turn.call("count_by_borough", "{\"category\": \"PARK\"}"));
    }
    turns.add(
        Turn.calls(
            "count_by_borough", "{\"category\": \"SCHOOL\"}",
            "count_by_borough", "{\"category\": \"CLSC\"}"));
    turns.add(Turn.text("Done."));
    model.play(turns.toArray(Turn[]::new));

    List<AgentEvent.ToolResult> results =
        ask("Count").stream()
            .filter(AgentEvent.ToolResult.class::isInstance)
            .map(AgentEvent.ToolResult.class::cast)
            .toList();

    assertThat(results).hasSize(Agent.MAX_TOOL_CALLS + 1);
    assertThat(results.subList(Agent.MAX_TOOL_CALLS - 1, Agent.MAX_TOOL_CALLS + 1))
        .allSatisfy(r -> assertThat(r.result().get("error").asString()).contains("limit"));
  }

  @Test
  void explainsInvalidArgumentsToTheModel() {
    model.play(Turn.call("find_features", "{\"category\": \"MUSEUM\"}"), Turn.text("Sorry."));

    var result =
        ask("Museums?").stream()
            .filter(AgentEvent.ToolResult.class::isInstance)
            .map(AgentEvent.ToolResult.class::cast)
            .findFirst()
            .orElseThrow();

    assertThat(result.result().get("error").asString()).contains("Invalid arguments", "MUSEUM");
  }

  @Test
  void stopsWhenTheVisitorLeaves() throws InterruptedException {
    model.play(Turn.call("count_by_borough", "{\"category\": \"PARK\"}"), Turn.text("Never sent."));

    agent.ask("Parks per borough?").take(1).blockLast(Duration.ofSeconds(10));
    Thread.sleep(300);

    assertThat(model.prompts).hasSize(1);
  }
}
