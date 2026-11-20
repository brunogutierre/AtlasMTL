package io.github.brunogutierre.atlasmtl;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.brunogutierre.atlasmtl.db.Postgis;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
class AtlasApplicationTest {

  @TestConfiguration(proxyBeanMethods = false)
  static class Containers {
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgis() {
      return Postgis.container();
    }
  }

  @Autowired JdbcClient jdbc;

  @Test
  void migratesTheSchemaOnStartup() {
    String version = jdbc.sql("select postgis_lib_version()").query(String.class).single();
    long tables =
        jdbc.sql(
                "select count(*) from information_schema.tables where table_name in"
                    + " ('dataset', 'borough', 'feature', 'address')")
            .query(Long.class)
            .single();

    assertThat(version).startsWith("3.5");
    assertThat(tables).isEqualTo(4);
  }
}
