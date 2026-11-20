package io.github.brunogutierre.atlasmtl.db;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** The PostGIS image every test runs against, the same one as compose.yaml. */
public final class Postgis {

  public static final DockerImageName IMAGE =
      DockerImageName.parse("postgis/postgis:17-3.5").asCompatibleSubstituteFor("postgres");

  private Postgis() {}

  /** A new, not yet started container. */
  public static PostgreSQLContainer container() {
    return new PostgreSQLContainer(IMAGE);
  }
}
