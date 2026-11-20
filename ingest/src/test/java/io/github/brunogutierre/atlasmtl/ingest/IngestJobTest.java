package io.github.brunogutierre.atlasmtl.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.brunogutierre.atlasmtl.db.Postgis;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("test")
class IngestJobTest {

  /** Serves the fixtures instead of the portals; the checksum is whatever a test sets. */
  static class FixtureCkan extends Ckan {
    static final Map<String, String> FILES =
        Map.of(
            "boroughs", "boroughs.geojson",
            "parks", "parks.geojson",
            "health", "installations.csv",
            "public-schools", "schools-public.geojson",
            "private-schools", "schools-private.geojson",
            "addresses", "addresses.csv");

    String checksum = "v1";

    /** Serves this file instead, for every source, when set. */
    String override;

    @Override
    public Download download(Source source) {
      try {
        Path file = Files.createTempFile("fixture-", ".download");
        Files.copy(
            Fixtures.open(override != null ? override : FILES.get(source.id())),
            file,
            StandardCopyOption.REPLACE_EXISTING);
        return new Download("fixture:" + FILES.get(source.id()), file, checksum);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Config {
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgis() {
      return Postgis.container();
    }

    @Bean
    @Primary
    FixtureCkan fixtureCkan() {
      return new FixtureCkan();
    }
  }

  @Autowired IngestJob job;
  @Autowired FixtureCkan ckan;
  @Autowired JdbcClient jdbc;

  @BeforeEach
  void loadEverything() {
    ckan.checksum = "v1";
    ckan.override = null;
    job.run(Set.of(), true);
  }

  @Test
  void loadsEveryDatasetWithValidGeometries() {
    assertThat(count("select count(*) from borough")).isEqualTo(2);
    assertThat(count("select count(*) from address")).isEqualTo(3);
    assertThat(categories())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("park", 3L, "hospital", 1L, "clsc", 1L, "long_term_care", 1L, "school", 4L));
    assertThat(
            count(
                "select count(*) from feature where not st_isvalid(geom) or st_srid(geom) <> 4326"))
        .isZero();
    assertThat(count("select count(*) from dataset where checksum = 'v1'")).isEqualTo(6);
  }

  @Test
  void skipsUnchangedFilesAndReplacesChangedOnes() {
    var before = fetchedAt("parks");

    job.run(Set.of("parks"), false);
    assertThat(fetchedAt("parks")).isEqualTo(before);

    ckan.checksum = "v2";
    job.run(Set.of("parks"), false);
    assertThat(fetchedAt("parks")).isAfter(before);
    assertThat(count("select count(*) from feature where dataset_id = 'parks'")).isEqualTo(3);
  }

  @Test
  void storesPropsAsJson() {
    String area =
        jdbc.sql("select props->>'area_ha' from feature where name_fr = 'Dieppe'")
            .query(String.class)
            .single();

    assertThat(Double.parseDouble(area)).isPositive();
  }

  private long count(String sql) {
    return jdbc.sql(sql).query(Long.class).single();
  }

  private Map<String, Long> categories() {
    return jdbc
        .sql("select category, count(*) from feature group by category")
        .query((rs, i) -> Map.entry(rs.getString(1), rs.getLong(2)))
        .list()
        .stream()
        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private java.time.OffsetDateTime fetchedAt(String id) {
    return jdbc.sql("select fetched_at from dataset where id = :id")
        .param("id", id)
        .query(java.time.OffsetDateTime.class)
        .single();
  }

  @Test
  void refusesToEmptyADatasetAndKeepsItsRows() {
    ckan.checksum = "v2";
    ckan.override = "empty.geojson";

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> job.run(Set.of("parks"), false))
        .hasMessageContaining("refusing to replace 3 rows with 0");
    assertThat(count("select count(*) from feature where dataset_id = 'parks'")).isEqualTo(3);
    assertThat(count("select count(*) from dataset where id = 'parks' and checksum = 'v1'"))
        .isOne();
  }
}
