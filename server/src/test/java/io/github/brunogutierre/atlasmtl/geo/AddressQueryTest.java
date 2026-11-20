package io.github.brunogutierre.atlasmtl.geo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AddressQueryTest {

  private static AddressQuery parse(String text) {
    return AddressQuery.parse(text).orElseThrow();
  }

  @Test
  void readsFrenchAddresses() {
    assertThat(parse("1000 rue Sherbrooke Ouest"))
        .isEqualTo(new AddressQuery(1000, "sherbrooke", "sherbrooke", "rue", "O", null));
    assertThat(parse("3869, chemin de la Côte-des-Neiges, Montréal, QC H3H 1V6"))
        .isEqualTo(
            new AddressQuery(
                3869, "cote des neiges", "de la cote des neiges", "chemin", null, null));
    assertThat(parse("5664 1re Avenue"))
        .isEqualTo(new AddressQuery(5664, "1re", "1re", "avenue", null, null));
  }

  @Test
  void readsEnglishAddresses() {
    assertThat(parse("1000 Sherbrooke St W"))
        .isEqualTo(new AddressQuery(1000, "sherbrooke", "sherbrooke", "rue", "O", null));
    assertThat(parse("350 Victoria Ave, Westmount, QC"))
        .isEqualTo(new AddressQuery(350, "victoria", "victoria", "avenue", null, "westmount"));
  }

  @Test
  void needsACivicNumberAndAStreet() {
    assertThat(AddressQuery.parse("Olympic Stadium")).isEmpty();
    assertThat(AddressQuery.parse("1000")).isEmpty();
  }
}
