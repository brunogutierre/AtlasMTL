package io.github.brunogutierre.atlasmtl.agent;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Stores complete answers by question, so a repeated question costs no model call. */
@Component
public class AnswerCache {

  /** Answers heavier than this (many polygons) are not worth a database row. */
  static final int MAX_BYTES = 1_000_000;

  private static final TypeReference<List<AgentEvent>> EVENTS = new TypeReference<>() {};

  private final JdbcClient jdbc;
  private final JsonMapper json;

  public AnswerCache(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  /** The stored answer to the question, counting the hit. */
  public Optional<List<AgentEvent>> get(String question) {
    return jdbc.sql(
            """
            update answer_cache set hits = hits + 1 where question_key = :key
            returning events::text
            """)
        .param("key", key(question))
        .query(String.class)
        .optional()
        .map(events -> json.readerFor(EVENTS).<List<AgentEvent>>readValue(events));
  }

  /** Stores a complete answer, unless it is too large; the first answer to a question wins. */
  public void put(String question, List<AgentEvent> events) {
    String serialized = json.writerFor(EVENTS).writeValueAsString(events);
    if (serialized.length() > MAX_BYTES) {
      return;
    }
    jdbc.sql(
            """
            insert into answer_cache (question_key, question, events)
            values (:key, :question, cast(:events as jsonb))
            on conflict (question_key) do nothing
            """)
        .param("key", key(question))
        .param("question", question)
        .param("events", serialized)
        .update();
  }

  /**
   * Lower case, no accents, no punctuation, single spaces: "Parcs à Verdun ?" = "parcs a verdun".
   */
  static String key(String question) {
    return Normalizer.normalize(question, Normalizer.Form.NFKD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{L}\\p{N}]+", " ")
        .strip();
  }
}
