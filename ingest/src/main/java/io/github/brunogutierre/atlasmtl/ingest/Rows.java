package io.github.brunogutierre.atlasmtl.ingest;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/** What the normalizers produce: one record per row of the target tables. */
public final class Rows {

  private Rows() {}

  /** Adds a property only when the source has a value, so {@code props} has no nulls. */
  static void putIfPresent(Map<String, Object> props, String key, @Nullable String value) {
    if (value != null && !value.isBlank()) {
      props.put(key, value.strip());
    }
  }

  /**
   * A row of {@code feature}.
   *
   * @param geometry GeoJSON geometry, loaded with {@code ST_GeomFromGeoJSON}
   */
  public record Feature(
      String sourceId,
      String category,
      @Nullable String subcategory,
      @Nullable String nameFr,
      @Nullable String nameEn,
      Map<String, Object> props,
      String geometry) {}

  /** A row of {@code borough}. */
  public record Borough(int id, String name, String kind, String geometry) {}

  /** A row of {@code address}. */
  public record Address(
      long id,
      int numberFrom,
      int numberTo,
      String streetType,
      @Nullable String streetLink,
      String streetName,
      @Nullable String orientation,
      String streetKey,
      double longitude,
      double latitude) {}
}
