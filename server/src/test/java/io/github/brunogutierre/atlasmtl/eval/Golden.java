package io.github.brunogutierre.atlasmtl.eval;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.dataformat.yaml.YAMLMapper;

/** A golden question and what a good answer to it does. */
public record Golden(String id, String question, Expect expect) {

  /**
   * @param tools tools the answer must call, in any order
   * @param arguments per tool, arguments its calls must carry: numbers equal, text contained
   *     (ignoring case and accents)
   * @param total how many places a tool should report
   * @param maxToolCalls at most this many tool calls
   * @param answerMentions words the answer text must contain
   * @param answerLanguage "fr" or "en" when the answer must be in that language only
   */
  public record Expect(
      @Nullable List<String> tools,
      @Nullable Map<String, Map<String, Object>> arguments,
      @Nullable Total total,
      @Nullable Integer maxToolCalls,
      @Nullable List<String> answerMentions,
      @Nullable String answerLanguage) {}

  public record Total(String tool, int min, int max) {}

  static List<Golden> load(String resource) {
    try (InputStream in =
        Objects.requireNonNull(Golden.class.getResourceAsStream(resource), resource)) {
      return YAMLMapper.builder().build().readValue(in, new TypeReference<List<Golden>>() {});
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
