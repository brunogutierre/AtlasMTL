package io.github.brunogutierre.atlasmtl.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.util.List;
import org.junit.jupiter.api.Test;

class HealthTest {

  private final List<Rows.Feature> features = Health.normalize(Fixtures.open("installations.csv"));

  @Test
  void keepsTheMontrealRegionAndOneFeaturePerCategory() {
    assertThat(features)
        .extracting(Rows.Feature::sourceId, Rows.Feature::category)
        .containsExactly(
            tuple("12475976:hospital", "hospital"),
            tuple("51218170:clsc", "clsc"),
            tuple("12387692:long_term_care", "long_term_care"));
  }

  @Test
  void aClscWithTheGeneralMissionIsNotAHospital() {
    assertThat(features.get(1).nameFr())
        .isEqualTo(
            "CLSC de la Visitation et Groupe de Médecine de Famille Universitaire des Faubourgs");
  }

  @Test
  void titleCasesNamesAndBuildsAPoint() {
    Rows.Feature hospital = features.get(0);

    assertThat(hospital.nameFr()).isEqualTo("Hôpital du Sacré-Coeur-de-Montréal");
    assertThat(hospital.geometry()).startsWith("{\"type\":\"Point\",\"coordinates\":[-73.");
    assertThat(hospital.props()).containsKeys("address", "operator");
  }
}
