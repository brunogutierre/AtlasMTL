package io.github.brunogutierre.atlasmtl;

import io.github.brunogutierre.atlasmtl.db.Postgis;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** One PostGIS container for every server test that imports it (the context is cached). */
@TestConfiguration(proxyBeanMethods = false)
public class TestDatabase {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgis() {
    return Postgis.container();
  }
}
