package io.github.brunogutierre.atlasmtl.eval;

import io.github.brunogutierre.atlasmtl.agent.AgentEvent;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/** Checks an answer's events against a golden question's expectations. */
final class Scorer {

  /** The result for one question; {@code failures} is empty when it passed. */
  record Score(
      String id,
      List<String> failures,
      List<String> tools,
      String answer,
      int inputTokens,
      int outputTokens,
      long millis) {
    boolean passed() {
      return failures.isEmpty();
    }
  }

  private static final Pattern FRENCH =
      Pattern.compile("\\b(les|des|dans|à|il y a|une|du|est|sont|qui)\\b");
  private static final Pattern ENGLISH =
      Pattern.compile("\\b(the|in|are|there|is|which|with|of)\\b");

  private Scorer() {}

  static Score score(Golden golden, List<AgentEvent> events, long millis) {
    var failures = new ArrayList<String>();
    var calls =
        events.stream()
            .filter(AgentEvent.ToolCall.class::isInstance)
            .map(AgentEvent.ToolCall.class::cast)
            .toList();
    var results =
        events.stream()
            .filter(AgentEvent.ToolResult.class::isInstance)
            .map(AgentEvent.ToolResult.class::cast)
            .toList();
    var answer = new StringBuilder();
    events.stream()
        .filter(AgentEvent.Token.class::isInstance)
        .forEach(t -> answer.append(((AgentEvent.Token) t).text()));
    var usage =
        events.stream()
            .filter(AgentEvent.Usage.class::isInstance)
            .map(AgentEvent.Usage.class::cast)
            .reduce((a, b) -> b)
            .orElse(new AgentEvent.Usage(0, 0, 0));
    Golden.Expect expect = golden.expect();

    if (events.isEmpty() || !(events.getLast() instanceof AgentEvent.Done)) {
      failures.add("the answer did not complete");
    }
    List<String> tools = calls.stream().map(AgentEvent.ToolCall::tool).toList();
    if (expect.tools() != null) {
      for (String tool : expect.tools()) {
        if (!tools.contains(tool)) {
          failures.add("expected a call to " + tool + ", got " + tools);
        }
      }
    }
    if (expect.arguments() != null) {
      for (var entry : expect.arguments().entrySet()) {
        boolean matched =
            calls.stream()
                .filter(c -> c.tool().equals(entry.getKey()))
                .anyMatch(c -> matches(c.arguments(), entry.getValue()));
        if (!matched) {
          failures.add(entry.getKey() + " was never called with " + entry.getValue());
        }
      }
    }
    if (expect.total() != null) {
      Golden.Total total = expect.total();
      boolean inRange =
          results.stream()
              .filter(r -> r.tool().equals(total.tool()) && r.result().has("total"))
              .map(r -> r.result().get("total").asInt())
              .anyMatch(n -> n >= total.min() && n <= total.max());
      if (!inRange) {
        failures.add(total.tool() + " total not within " + total.min() + "–" + total.max());
      }
    }
    if (expect.maxToolCalls() != null && calls.size() > expect.maxToolCalls()) {
      failures.add(calls.size() + " tool calls, at most " + expect.maxToolCalls() + " expected");
    }
    String text = plain(answer.toString());
    if (expect.answerMentions() != null) {
      for (String word : expect.answerMentions()) {
        if (!text.contains(plain(word))) {
          failures.add("the answer does not mention " + word);
        }
      }
    }
    if ("fr".equals(expect.answerLanguage()) && count(FRENCH, text) <= count(ENGLISH, text)) {
      failures.add("the answer is not in French");
    }
    return new Score(
        golden.id(),
        failures,
        tools,
        answer.toString(),
        usage.inputTokens(),
        usage.outputTokens(),
        millis);
  }

  /** Numbers must be equal; text must be contained, ignoring case and accents. */
  static boolean matches(JsonNode arguments, Map<String, Object> expected) {
    for (var entry : expected.entrySet()) {
      JsonNode actual = arguments.get(entry.getKey());
      if (actual == null || actual.isNull()) {
        return false;
      }
      Object want = entry.getValue();
      if (want instanceof Number number) {
        if (!actual.isNumber() || Math.abs(actual.asDouble() - number.doubleValue()) > 1e-9) {
          return false;
        }
      } else if (!plain(actual.asString()).contains(plain(String.valueOf(want)))) {
        return false;
      }
    }
    return true;
  }

  static String plain(String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFKD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{L}\\p{N}]+", " ");
  }

  private static long count(Pattern pattern, String text) {
    return pattern.matcher(text).results().count();
  }
}
