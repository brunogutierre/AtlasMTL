package io.github.brunogutierre.atlasmtl.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AddressesTest {

  private List<Rows.Address> read() {
    try (Stream<Rows.Address> rows = Addresses.normalize(Fixtures.open("addresses.csv"))) {
      return rows.toList();
    }
  }

  @Test
  void readsNumberRangesAndStreetParts() {
    Rows.Address sherbrooke = read().get(0);

    assertThat(sherbrooke)
        .isEqualTo(
            new Rows.Address(
                360984,
                914,
                1002,
                "rue",
                null,
                "Sherbrooke",
                "O",
                "sherbrooke",
                -73.575328,
                45.502714));
  }

  @Test
  void keepsTheLinkWordAndNormalizesTheKey() {
    Rows.Address cdn = read().get(1);

    assertThat(cdn.streetLink()).isEqualTo("de la");
    assertThat(cdn.orientation()).isNull(); // X in the source
    assertThat(cdn.streetKey()).isEqualTo("cote des neiges");
  }
}
