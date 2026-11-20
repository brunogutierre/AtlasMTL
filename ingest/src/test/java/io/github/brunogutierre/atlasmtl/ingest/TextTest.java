package io.github.brunogutierre.atlasmtl.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextTest {

  @Test
  void keysIgnoreCaseAccentsAndPunctuation() {
    assertThat(Text.key("Côte-des-Neiges")).isEqualTo("cote des neiges");
    assertThat(Text.key("  Saint-Laurent  ")).isEqualTo("saint laurent");
    assertThat(Text.key("L'Assomption")).isEqualTo("l assomption");
    assertThat(Text.key("1re")).isEqualTo("1re");
  }
}
