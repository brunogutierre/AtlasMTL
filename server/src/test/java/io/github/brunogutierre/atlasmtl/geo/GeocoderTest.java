package io.github.brunogutierre.atlasmtl.geo;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.brunogutierre.atlasmtl.TestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@Import(TestDatabase.class)
@Sql(scripts = "/geocoder-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(statements = "delete from address", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class GeocoderTest {

  @Autowired Geocoder geocoder;

  @Test
  void findsANumberInsideARangeOnTheSameSide() {
    Geocoded found = geocoder.geocode("1000 rue Sherbrooke Ouest").orElseThrow();

    assertThat(found.address()).isEqualTo("1000 rue Sherbrooke Ouest");
    assertThat(found.exact()).isTrue();
    assertThat(found.longitude()).isEqualTo(-73.575328);
  }

  @Test
  void tellsEastFromWest() {
    assertThat(geocoder.geocode("1000 Sherbrooke St E"))
        .map(Geocoded::longitude)
        .contains(-73.565422);
  }

  @Test
  void toleratesTyposInTheStreetName() {
    assertThat(geocoder.geocode("1000 Sherbroke Ouest")).map(Geocoded::exact).contains(true);
  }

  @Test
  void fallsBackToTheNearestNumberOnTheStreet() {
    Geocoded found = geocoder.geocode("3871 Côte-des-Neiges").orElseThrow();

    assertThat(found.exact()).isFalse();
    assertThat(found.address()).isEqualTo("3869 chemin de la Côte-des-Neiges");
  }

  @Test
  void keepsOfficialLinkWordsAndPrefersTheStreetType() {
    assertThat(geocoder.geocode("1455 boul. de Maisonneuve O"))
        .map(Geocoded::address)
        .contains("1455 boulevard De Maisonneuve Ouest");
  }

  @Test
  void writesNumberedStreetsTheLocalWay() {
    assertThat(geocoder.geocode("5664 1re Avenue"))
        .map(Geocoded::address)
        .contains("5664 1re Avenue");
  }

  @Test
  void returnsNothingForUnknownStreets() {
    assertThat(geocoder.geocode("12 rue Imaginaire")).isEmpty();
  }
}
