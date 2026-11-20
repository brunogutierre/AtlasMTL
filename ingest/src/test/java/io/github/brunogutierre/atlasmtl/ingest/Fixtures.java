package io.github.brunogutierre.atlasmtl.ingest;

import java.io.InputStream;
import java.util.Objects;

final class Fixtures {

  private Fixtures() {}

  static InputStream open(String name) {
    return Objects.requireNonNull(
        Fixtures.class.getResourceAsStream("/fixtures/" + name), "missing fixture " + name);
  }
}
