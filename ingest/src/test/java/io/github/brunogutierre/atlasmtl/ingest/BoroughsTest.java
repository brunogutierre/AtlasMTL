package io.github.brunogutierre.atlasmtl.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BoroughsTest {

  @Test
  void tellsBoroughsFromLinkedCities() {
    assertThat(Boroughs.normalize(Fixtures.open("boroughs.geojson")))
        .extracting(Rows.Borough::name, Rows.Borough::kind)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("LaSalle", "borough"),
            org.assertj.core.groups.Tuple.tuple("Dollard-des-Ormeaux", "linked_city"));
  }
}
