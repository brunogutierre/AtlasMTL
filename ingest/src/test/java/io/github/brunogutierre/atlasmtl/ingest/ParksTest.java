package io.github.brunogutierre.atlasmtl.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ParksTest {

  private final List<Rows.Feature> parks = Parks.normalize(Fixtures.open("parks.geojson"));

  @Test
  void keepsParksAndLeavesStreetSpacesAndUntypedSpacesOut() {
    assertThat(parks)
        .extracting(Rows.Feature::nameFr, Rows.Feature::subcategory)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("Dieppe", "large_park"),
            org.assertj.core.groups.Tuple.tuple("Maurice-Cullen", "borough_park"),
            org.assertj.core.groups.Tuple.tuple(
                "Université Concordia - Campus Loyola", "other_green_space"));
  }

  @Test
  void keepsTheAreaInHectaresAndTheGeometryAsGeoJson() {
    Rows.Feature dieppe = parks.get(0);

    assertThat(dieppe.category()).isEqualTo("park");
    assertThat(dieppe.props()).containsKey("area_ha");
    assertThat(dieppe.props().get("area_ha")).isInstanceOf(Double.class);
    assertThat(dieppe.geometry()).startsWith("{\"type\":\"Polygon\"");
    assertThat(dieppe.sourceId()).matches("\\d+");
  }
}
