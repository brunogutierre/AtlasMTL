package io.github.brunogutierre.atlasmtl.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.brunogutierre.atlasmtl.TestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@Import(TestDatabase.class)
@Sql(scripts = "/geo-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(
    statements = "delete from feature; delete from borough; delete from dataset",
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class GeoQueriesTest {

  @Autowired GeoQueries geo;

  private Borough plateau() {
    return geo.borough("plateau").orElseThrow();
  }

  @Test
  void findsPlacesInABoroughSortedByName() {
    Found<Place> found = geo.find(Category.SCHOOL, plateau(), null, 10);

    assertThat(found.total()).isEqualTo(2);
    assertThat(found.places()).extracting(Place::name).containsExactly("École Alpha", "École Bêta");
    Place alpha = found.places().get(0);
    assertThat(alpha.borough()).isEqualTo("Le Plateau-Mont-Royal");
    assertThat(alpha.props()).containsEntry("language", "fr");
    assertThat(alpha.geometry().get("type").asString()).isEqualTo("Point");
  }

  @Test
  void findsPlacesNearAPointNearestFirst() {
    Found<Place> found = geo.find(Category.SCHOOL, null, new Near(-73.586, 45.5205, 1000), 10);

    assertThat(found.places()).extracting(Place::name).containsExactly("École Alpha", "École Bêta");
  }

  @Test
  void reportsTheTotalBeyondTheLimit() {
    Found<Place> found = geo.find(Category.SCHOOL, null, null, 1);

    assertThat(found.total()).isEqualTo(3);
    assertThat(found.places()).hasSize(1);
  }

  @Test
  void pairsPlacesWithTheNearestOneOfAnotherCategory() {
    Found<Match> near = geo.withinDistance(Category.SCHOOL, Category.HOSPITAL, 600, null, 10);
    Found<Match> far = geo.withinDistance(Category.SCHOOL, Category.HOSPITAL, 1000, null, 10);

    assertThat(near.places()).extracting(m -> m.place().name()).containsExactly("École Alpha");
    assertThat(near.places().get(0).meters()).isCloseTo(556, within(3));
    assertThat(near.places().get(0).nearest().name()).isEqualTo("Hôpital Test");
    assertThat(far.places())
        .extracting(m -> m.place().name())
        .containsExactly("École Alpha", "École Bêta");
  }

  @Test
  void countsPerBoroughWithDensity() {
    assertThat(geo.countByBorough(Category.SCHOOL))
        .extracting(BoroughCount::name, BoroughCount::count)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("Le Plateau-Mont-Royal", 2),
            org.assertj.core.groups.Tuple.tuple("Westmount", 1));
    BoroughCount plateau = geo.countByBorough(Category.SCHOOL).get(0);
    assertThat(plateau.perKm2()).isCloseTo(2 / plateau.areaKm2(), within(0.01));
  }

  @Test
  void findsBoroughsIgnoringCaseAccentsAndPartialNames() {
    assertThat(geo.borough("WESTMOUNT")).map(Borough::kind).contains("linked_city");
    assertThat(geo.borough("le plateau mont royal"))
        .map(Borough::name)
        .contains("Le Plateau-Mont-Royal");
    assertThat(geo.borough("Atlantis")).isEmpty();
  }

  @Test
  void rejectsArgumentsOutsideTheLimits() {
    assertThatThrownBy(() -> geo.find(Category.PARK, null, null, 501))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> geo.withinDistance(Category.PARK, Category.SCHOOL, 5001, null, 10))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
