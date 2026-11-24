package io.github.brunogutierre.atlasmtl.eval;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.brunogutierre.atlasmtl.agent.Agent;
import io.github.brunogutierre.atlasmtl.agent.AgentEvent;
import io.github.brunogutierre.atlasmtl.usage.UsageProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Asks every golden question to the real model, against a database the ingest job filled, and
 * writes build/reports/evals. Run with {@code ./gradlew :server:evals}; it never runs with the
 * regular tests, because every question costs tokens.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
@SpringBootTest
class LiveEvalTest {

  /** Below this share of passing questions, the run fails. */
  static final double PASS_RATE = 0.8;

  @Autowired Agent agent;
  @Autowired UsageProperties prices;

  @Test
  void answersTheGoldenQuestions() throws IOException {
    var scores = new ArrayList<Scorer.Score>();
    for (Golden golden : Golden.load("/evals/golden.yaml")) {
      long started = System.nanoTime();
      List<AgentEvent> events =
          agent.ask(golden.question()).collectList().block(Duration.ofMinutes(2));
      scores.add(
          Scorer.score(
              golden,
              events == null ? List.of() : events,
              (System.nanoTime() - started) / 1_000_000));
    }
    double rate = scores.stream().filter(Scorer.Score::passed).count() / (double) scores.size();
    write(scores, rate);

    assertThat(rate)
        .as("pass rate, see build/reports/evals/report.md")
        .isGreaterThanOrEqualTo(PASS_RATE);
  }

  private void write(List<Scorer.Score> scores, double rate) throws IOException {
    Path dir = Path.of(System.getProperty("evals.reports", "build/reports/evals"));
    Files.createDirectories(dir);
    BigDecimal cost =
        scores.stream()
            .map(s -> prices.cost(s.inputTokens(), s.outputTokens()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    var md = new StringBuilder("# AtlasMTL eval report\n\n");
    md.append(
        String.format(
            "Pass rate: **%.0f%%** (%d/%d) · estimated cost: US$%s%n%n",
            rate * 100, scores.stream().filter(Scorer.Score::passed).count(), scores.size(), cost));
    md.append("| Question | Result | Tools | Tokens in/out | Time |\n|---|---|---|---|---|\n");
    for (var s : scores) {
      md.append(
          String.format(
              "| %s | %s | %s | %d / %d | %.1f s |%n",
              s.id(),
              s.passed() ? "pass" : "FAIL: " + String.join("; ", s.failures()),
              String.join(", ", s.tools()),
              s.inputTokens(),
              s.outputTokens(),
              s.millis() / 1000.0));
    }
    Files.writeString(dir.resolve("report.md"), md);
    Files.writeString(
        dir.resolve("report.json"),
        JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(scores));
  }
}
