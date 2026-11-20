package io.github.brunogutierre.atlasmtl.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

class SchemaTest {

  private static final PostgreSQLContainer POSTGIS = Postgis.container();
  private static JdbcClient jdbc;

  @BeforeAll
  static void migrate() {
    POSTGIS.start();
    var dataSource =
        new DriverManagerDataSource(
            POSTGIS.getJdbcUrl(), POSTGIS.getUsername(), POSTGIS.getPassword());
    Flyway.configure().dataSource(dataSource).locations(Schema.MIGRATIONS).load().migrate();
    jdbc = JdbcClient.create(dataSource);
    jdbc.sql(
            """
            insert into dataset (id, title, publisher, source_url, license, fetched_at, checksum)
            values ('test', 'Test', 'Tests', 'https://example.org', 'CC-BY-4.0', now(), 'x')
            """)
        .update();
  }

  @AfterAll
  static void stop() {
    POSTGIS.stop();
  }

  @Test
  void installsTheExtensionsTheToolsNeed() {
    assertThat(jdbc.sql("select extname from pg_extension").query(String.class).list())
        .contains("postgis", "pg_trgm", "unaccent");
  }

  @Test
  void measuresDistancesInMetersOnTheGeography() {
    // Two points on rue Sherbrooke, about 880 m apart.
    insertPoint("a", -73.5772, 45.5048);
    insertPoint("b", -73.5675, 45.5089);

    String sql =
        """
        select count(*) from feature a join feature b
          on st_dwithin(a.geom::geography, b.geom::geography, :meters)
        where a.source_id = 'a' and b.source_id = 'b'
        """;
    assertThat(jdbc.sql(sql).param("meters", 1000).query(Long.class).single()).isOne();
    assertThat(jdbc.sql(sql).param("meters", 500).query(Long.class).single()).isZero();
  }

  @Test
  void rejectsAnAddressRangeThatRunsBackwards() {
    assertThatThrownBy(() ->
            jdbc.sql(
                    """
                    insert into address (id, number_from, number_to, street_type, street_name,
                      street_key, geom)
                    values (1, 1002, 914, 'rue', 'Sherbrooke', 'sherbrooke',
                      st_setsrid(st_makepoint(-73.5753, 45.5027), 4326))
                    """)
                .update())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private static void insertPoint(String id, double lon, double lat) {
    jdbc.sql(
            """
            insert into feature (dataset_id, source_id, category, geom)
            values ('test', :id, 'park', st_setsrid(st_makepoint(:lon, :lat), 4326))
            """)
        .param("id", id)
        .param("lon", lon)
        .param("lat", lat)
        .update();
  }
}
