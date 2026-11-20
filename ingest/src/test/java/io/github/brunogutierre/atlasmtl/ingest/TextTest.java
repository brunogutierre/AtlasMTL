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

  @Test
  void titleCasesUpperCaseFrenchNames() {
    assertThat(Text.titleCase("HÔPITAL DU SACRÉ-COEUR-DE-MONTRÉAL"))
        .isEqualTo("Hôpital du Sacré-Coeur-de-Montréal");
    assertThat(Text.titleCase("CENTRE D'HÉBERGEMENT DE L'ÎLE"))
        .isEqualTo("Centre d'Hébergement de l'Île");
    assertThat(Text.titleCase("L'ACADÉMIE")).isEqualTo("L'Académie");
    assertThat(Text.titleCase("HÔPITAL NOTRE-DAME-DE-GRÂCE"))
        .isEqualTo("Hôpital Notre-Dame-de-Grâce");
    assertThat(Text.titleCase("CHSLD POLONAIS")).isEqualTo("CHSLD Polonais");
  }

  @Test
  void leavesMixedCaseNamesAlone() {
    assertThat(Text.titleCase("Parc La Fontaine")).isEqualTo("Parc La Fontaine");
  }
}
