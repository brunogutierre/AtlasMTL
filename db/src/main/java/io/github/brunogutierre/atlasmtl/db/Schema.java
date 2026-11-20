package io.github.brunogutierre.atlasmtl.db;

/** Where the schema lives: Flyway migrations on the classpath of every module that needs them. */
public final class Schema {

  /** The Flyway location of the migrations. */
  public static final String MIGRATIONS = "classpath:db/migration";

  private Schema() {}
}
