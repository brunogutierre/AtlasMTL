package io.github.brunogutierre.atlasmtl.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Asks a question over real HTTP, as the browser does. */
final class AskHttp {

  private static final HttpClient HTTP = HttpClient.newHttpClient();

  private AskHttp() {}

  static HttpResponse<String> ask(int port, String question) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/ask"))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString("{\"text\": \"" + question + "\"}"))
            .build();
    return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
