package io.github.brunogutierre.atlasmtl.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.brunogutierre.atlasmtl.TestDatabase;
import io.github.brunogutierre.atlasmtl.agent.ScriptedChatModel;
import io.github.brunogutierre.atlasmtl.agent.ScriptedChatModel.Turn;
import io.github.brunogutierre.atlasmtl.agent.ScriptedModelConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/** The endpoint over real HTTP, as the browser sees it. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestDatabase.class, ScriptedModelConfig.class})
class AskControllerTest {

  @LocalServerPort int port;
  @Autowired ScriptedChatModel model;
  private final HttpClient http = HttpClient.newHttpClient();

  private HttpResponse<String> ask(String body) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/ask"))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return http.send(request, HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void streamsTheAnswerAsServerSentEvents() throws Exception {
    model.play(Turn.text("Hello", " Montréal"));

    HttpResponse<String> response = ask("{\"text\": \"Hi?\"}");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(type -> assertThat(type).startsWith("text/event-stream"));
    List<String> data =
        response
            .body()
            .lines()
            .filter(l -> l.startsWith("data:"))
            .map(l -> l.substring(5))
            .toList();
    assertThat(data)
        .containsExactly(
            "{\"type\":\"token\",\"text\":\"Hello\"}",
            "{\"type\":\"token\",\"text\":\" Montréal\"}",
            "{\"type\":\"usage\",\"inputTokens\":100,\"outputTokens\":20,\"toolCalls\":0}",
            "{\"type\":\"done\"}");
  }

  @Test
  void rejectsEmptyAndOverlongQuestionsWithAProblem() throws Exception {
    HttpResponse<String> empty = ask("{\"text\": \"   \"}");
    HttpResponse<String> overlong = ask("{\"text\": \"" + "x".repeat(501) + "\"}");

    assertThat(empty.statusCode()).isEqualTo(400);
    assertThat(empty.body()).contains("\"title\":\"Invalid question\"", "Ask a question.");
    assertThat(overlong.statusCode()).isEqualTo(400);
    assertThat(overlong.body()).contains("limited to 500 characters");
  }
}
