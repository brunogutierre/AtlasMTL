package io.github.brunogutierre.atlasmtl.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CkanTest {

  private HttpServer server;
  private final List<String> userAgents = new ArrayList<>();
  private String base;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    base = "http://127.0.0.1:" + server.getAddress().getPort();
    server.createContext(
        "/api/3/action/package_show",
        exchange -> {
          userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
          String body =
              """
              {"result": {"resources": [
                {"format": "SHP", "name": "Parcs (WGS84)", "url": "%1$s/files/parcs.zip"},
                {"format": "GeoJSON", "name": "Parcs (MTM8)", "url": "%1$s/files/mtm8.geojson"},
                {"format": "GeoJSON", "name": "Parcs (WGS84)", "url": "%1$s/files/wgs84.geojson"}
              ]}}
              """
                  .formatted(base);
          send(exchange, body);
        });
    server.createContext(
        "/files/wgs84.geojson",
        exchange -> {
          userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
          send(exchange, "{\"type\":\"FeatureCollection\",\"features\":[]}");
        });
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private Source source() {
    return new Source("parks", "Parcs", "Ville", base, "parcs", "geojson", "WGS84");
  }

  @Test
  void picksTheResourceByFormatAndName() {
    assertThat(new Ckan().resourceUrl(source())).isEqualTo(base + "/files/wgs84.geojson");
  }

  @Test
  void downloadsWithItsOwnUserAgentAndAChecksum() throws IOException {
    Ckan.Download download = new Ckan().download(source());

    assertThat(Files.readString(download.file())).contains("FeatureCollection");
    assertThat(download.checksum()).hasSize(64);
    assertThat(userAgents).allMatch(ua -> ua.startsWith("atlasmtl-ingest/"));
    Files.delete(download.file());
  }

  private static void send(com.sun.net.httpserver.HttpExchange exchange, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(200, bytes.length);
    try (var out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }
}
