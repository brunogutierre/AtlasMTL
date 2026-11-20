package io.github.brunogutierre.atlasmtl.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Finds and downloads dataset files through a CKAN portal's API. */
public class Ckan {

  /** donnees.montreal.ca rejects curl's default User-Agent; every request names this client. */
  static final String USER_AGENT =
      "atlasmtl-ingest/0.1 (+https://github.com/brunogutierre/AtlasMTL)";

  private final HttpClient http =
      HttpClient.newBuilder()
          .followRedirects(HttpClient.Redirect.NORMAL)
          .connectTimeout(Duration.ofSeconds(30))
          .build();
  private final JsonMapper json = JsonMapper.builder().build();

  /** A downloaded file and the SHA-256 of its bytes. */
  public record Download(String url, Path file, String checksum) {}

  /** The URL of the source's resource, read from {@code package_show}. */
  public String resourceUrl(Source source) {
    URI api = URI.create(source.portal() + "/api/3/action/package_show?id=" + source.packageName());
    JsonNode result;
    try (InputStream body = get(api)) {
      result = json.readTree(body).path("result");
    } catch (IOException e) {
      throw new IllegalStateException("Can't read " + api, e);
    }
    for (JsonNode resource : result.path("resources")) {
      String format = resource.path("format").asString("");
      String name = resource.path("name").asString("");
      if (format.equalsIgnoreCase(source.format()) && name.contains(source.resourceName())) {
        return resource.path("url").asString();
      }
    }
    throw new IllegalStateException(
        "No " + source.format() + " resource named like '" + source.resourceName() + "' in " + api);
  }

  /** Downloads the source's file to a temporary file. */
  public Download download(Source source) {
    String url = resourceUrl(source);
    try {
      Path file = Files.createTempFile("atlasmtl-" + source.id() + "-", ".download");
      MessageDigest sha = MessageDigest.getInstance("SHA-256");
      try (InputStream body = new DigestInputStream(get(URI.create(url)), sha)) {
        Files.copy(body, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
      return new Download(url, file, HexFormat.of().formatHex(sha.digest()));
    } catch (IOException e) {
      throw new IllegalStateException("Can't download " + url, e);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private InputStream get(URI uri) throws IOException {
    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .header("User-Agent", USER_AGENT)
            .timeout(Duration.ofMinutes(5))
            .build();
    try {
      HttpResponse<InputStream> response =
          http.send(request, HttpResponse.BodyHandlers.ofInputStream());
      if (response.statusCode() != 200) {
        response.body().close();
        throw new IOException(uri + " answered HTTP " + response.statusCode());
      }
      return response.body();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while fetching " + uri, e);
    }
  }
}
