package io.github.brunogutierre.atlasmtl.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.github.brunogutierre.atlasmtl.TestDatabase;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The real Anthropic chat model against a local server that replays recorded Messages API streams.
 * It checks what a scripted model can't: how streamed tool calls and usage are read, and that the
 * second request carries a valid tool_use / tool_result pair.
 */
@SpringBootTest
@Import(TestDatabase.class)
@Sql(scripts = "/geo-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(
    statements = "delete from feature; delete from borough; delete from dataset",
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class AnthropicContractTest {

  private static final String TOOL_USE = "/anthropic/turn1-tool-use.sse";
  private static final String TEXT = "/anthropic/turn2-text.sse";
  private static volatile List<String> replies = List.of(TOOL_USE, TEXT);
  private static final List<String> requests = new CopyOnWriteArrayList<>();
  private static final AtomicInteger turn = new AtomicInteger();
  private static final HttpServer server = start();

  private static HttpServer start() {
    try {
      HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      http.createContext(
          "/",
          exchange -> {
            requests.add(
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body;
            try (InputStream in =
                Objects.requireNonNull(
                    AnthropicContractTest.class.getResourceAsStream(
                        replies.get(Math.min(turn.getAndIncrement(), replies.size() - 1))))) {
              body = in.readAllBytes();
            }
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) {
              out.write(body);
            }
          });
      http.start();
      return http;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void anthropic(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.ai.anthropic.base-url", () -> "http://127.0.0.1:" + server.getAddress().getPort());
    registry.add("spring.ai.anthropic.api-key", () -> "test-key");
  }

  @AfterAll
  static void stop() {
    server.stop(0);
  }

  @Autowired Agent agent;
  @Autowired ChatModel model;
  private final JsonMapper json = JsonMapper.builder().build();

  private static void replay(List<String> script) {
    replies = script;
    requests.clear();
    turn.set(0);
  }

  @Test
  void runsAToolRoundTripThroughTheRealAnthropicModel() {
    replay(List.of(TOOL_USE, TEXT));
    assertThat(model).isInstanceOf(AnthropicChatModel.class);

    List<AgentEvent> events =
        agent.ask("Schools in the Plateau?").collectList().block(Duration.ofSeconds(30));

    assertThat(events)
        .extracting(e -> e.getClass().getSimpleName())
        .containsExactly("Token", "ToolCall", "ToolResult", "Features", "Token", "Usage", "Done");
    var call = (AgentEvent.ToolCall) events.get(1);
    assertThat(call.id()).isEqualTo("toolu_01");
    assertThat(call.arguments().get("category").asString()).isEqualTo("SCHOOL");
    assertThat(((AgentEvent.ToolResult) events.get(2)).result().get("total").asInt()).isEqualTo(2);
    assertThat(events.get(5)).isEqualTo(new AgentEvent.Usage(420, 57, 1));

    JsonNode second = json.readTree(requests.get(1));
    JsonNode messages = second.get("messages");
    JsonNode toolUse = messages.get(1).get("content").findValue("id");
    JsonNode toolResult = messages.get(2).get("content").get(0);
    assertThat(toolUse.asString()).isEqualTo("toolu_01");
    assertThat(toolResult.get("type").asString()).isEqualTo("tool_result");
    assertThat(toolResult.get("tool_use_id").asString()).isEqualTo("toolu_01");
    // Every request carries the tools and the configured model settings.
    for (String request : requests) {
      JsonNode body = json.readTree(request);
      assertThat(body.get("tools")).hasSize(6);
      assertThat(body.get("model").asString()).isEqualTo("claude-haiku-5-5");
      assertThat(body.get("max_tokens").asInt()).isEqualTo(1024);
      assertThat(body.has("temperature")).as("Claude Haiku 5.5 rejects temperature").isFalse();
    }
  }

  @Test
  void forbidsToolsInTheLastCallWhileStillDefiningThem() {
    var script = new java.util.ArrayList<String>();
    for (int i = 0; i <= Agent.MAX_TOOL_CALLS; i++) {
      script.add(TOOL_USE);
    }
    script.add(TEXT);
    replay(script);

    List<AgentEvent> events =
        agent.ask("Schools, again and again").collectList().block(Duration.ofSeconds(30));

    assertThat(events.getLast()).isEqualTo(new AgentEvent.Done());
    JsonNode last = json.readTree(requests.getLast());
    assertThat(last.at("/tool_choice/type").asString()).isEqualTo("none");
    assertThat(last.get("tools")).hasSize(6);
    assertThat(requests).hasSize(Agent.MAX_TOOL_CALLS + 2);
  }
}
