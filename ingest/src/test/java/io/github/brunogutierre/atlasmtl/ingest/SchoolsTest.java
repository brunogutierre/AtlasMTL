package io.github.brunogutierre.atlasmtl.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import org.junit.jupiter.api.Test;

class SchoolsTest {

  @Test
  void keepsPublicSchoolsOfTheMontrealRegionWithTheirLevel() {
    assertThat(Schools.publicSchools(Fixtures.open("schools-public.geojson")))
        .extracting(Rows.Feature::nameFr, Rows.Feature::subcategory)
        .containsExactly(
            tuple("Marc-Favreau, annexe", "primary"),
            tuple("Antoine-de-Saint-Exupéry annexe", "secondary"),
            tuple("Centre de services aux entreprises", "vocational"));
  }

  @Test
  void identifiesASchoolByBuildingAndOrganization() {
    Rows.Feature school = Schools.publicSchools(Fixtures.open("schools-public.geojson")).get(0);

    assertThat(school.sourceId()).matches("762824:\\d+");
    assertThat(school.props()).containsEntry("language", "fr");
  }

  @Test
  void keepsPrivateSchoolsOfTheAgglomeration() {
    assertThat(Schools.privateSchools(Fixtures.open("schools-private.geojson")))
        .extracting(Rows.Feature::nameFr, Rows.Feature::subcategory)
        .containsExactly(tuple("L'Académie Beth Rivkah pour filles", "private"));
  }
}
