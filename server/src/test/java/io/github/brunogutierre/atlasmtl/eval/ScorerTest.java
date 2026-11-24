package io.github.brunogutierre.atlasmtl.eval;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.brunogutierre.atlasmtl.agent.AgentEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ScorerTest {

  private final JsonMapper json = JsonMapper.builder().build();
  private final Golden golden =
      Golden.load("/evals/golden.yaml").stream()
          .filter(g -> g.id().equals("large-parks-rosemont"))
          .findFirst()
          .orElseThrow();

  private List<AgentEvent> answer(String arguments, int total, String text) {
    return List.of(
        new AgentEvent.ToolCall("c1", "find_features", json.readTree(arguments)),
        new AgentEvent.ToolResult(
            "c1", "find_features", json.readTree("{\"total\": " + total + "}")),
        new AgentEvent.Token(text),
        new AgentEvent.Usage(900, 50, 1),
        new AgentEvent.Done());
  }

  @Test
  void loadsEveryGoldenQuestion() {
    assertThat(Golden.load("/evals/golden.yaml")).hasSizeGreaterThanOrEqualTo(10);
  }

  @Test
  void passesAnAnswerThatCallsTheRightToolWithTheRightArguments() {
    var score =
        Scorer.score(
            golden,
            answer(
                "{\"category\": \"PARK\", \"borough\": \"Rosemont-La Petite-Patrie\","
                    + " \"minAreaHectares\": 2.0}",
                12,
                "I found 12 parks."),
            1500);

    assertThat(score.failures()).isEmpty();
    assertThat(score.inputTokens()).isEqualTo(900);
  }

  @Test
  void explainsEveryMissedExpectation() {
    var score =
        Scorer.score(
            golden, answer("{\"category\": \"PARK\", \"borough\": \"Verdun\"}", 90, "Many."), 10);

    assertThat(score.failures())
        .containsExactly(
            "find_features was never called with {category=PARK, borough=Rosemont, minAreaHectares=2}",
            "find_features total not within 5–40");
  }

  @Test
  void tellsFrenchFromEnglish() {
    Golden french =
        Golden.load("/evals/golden.yaml").stream()
            .filter(g -> g.id().equals("fr-chsld-par-arrondissement"))
            .findFirst()
            .orElseThrow();
    List<AgentEvent> english =
        List.of(
            new AgentEvent.ToolCall(
                "c", "count_by_borough", json.readTree("{\"category\":\"LONG_TERM_CARE\"}")),
            new AgentEvent.Token("There are 12 in the borough of Ahuntsic, which leads."),
            new AgentEvent.Done());

    assertThat(Scorer.score(french, english, 1).failures())
        .containsExactly("the answer is not in French");
  }
}
