package io.github.brunogutierre.atlasmtl.ingest;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Parks and green spaces of the City of Montréal. */
public final class Parks {

  /** {@code TYPO1} values that are parks, and the subcategory each becomes. */
  private static final Map<String, String> TYPES =
      Map.of(
          "Parc d'arrondissement", "borough_park",
          "Grand parc", "large_park",
          "Autre espace vert", "other_green_space");

  private Parks() {}

  /**
   * The parks. Street spaces ({@code Espace voirie}) and spaces still being validated are left out:
   * they are medians and squares, not places people look for.
   */
  public static List<Rows.Feature> normalize(InputStream in) {
    return Readers.geoJson(in).stream()
        .filter(f -> isPark(f.text("TYPO1")))
        .map(Parks::park)
        .toList();
  }

  private static boolean isPark(@Nullable String type) {
    return type != null && TYPES.containsKey(type); // Map.of rejects null keys
  }

  private static Rows.Feature park(Readers.GeoFeature f) {
    Map<String, Object> props = new LinkedHashMap<>();
    @Nullable String area = f.text("SUPERFICIE");
    if (area != null) {
      props.put("area_ha", Math.round(Double.parseDouble(area) * 100) / 100.0);
    }
    putIfPresent(props, "owner", f.text("PROPRIETE"));
    putIfPresent(props, "use", f.text("TYPO2"));
    return new Rows.Feature(
        String.valueOf(f.text("OBJECTID")),
        "park",
        TYPES.get(f.text("TYPO1")),
        f.text("Nom"),
        null,
        props,
        f.geometry());
  }

  static void putIfPresent(Map<String, Object> props, String key, @Nullable String value) {
    if (value != null) {
      props.put(key, value);
    }
  }
}
