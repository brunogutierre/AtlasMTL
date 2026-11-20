package io.github.brunogutierre.atlasmtl.ingest;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Public and private schools of the Montréal agglomeration (MEQ). */
public final class Schools {

  private Schools() {}

  /**
   * Public school buildings in the Montréal region. A building can house several schools, so the id
   * pairs the building with the school organization.
   */
  public static List<Rows.Feature> publicSchools(InputStream in) {
    return Readers.geoJson(in).stream()
        .filter(f -> "Montréal".equals(f.text("NOM_REG_ADM")))
        .map(f -> {
          Map<String, Object> props = new LinkedHashMap<>();
          Rows.putIfPresent(props, "school_board", f.text("NOM_CS"));
          Rows.putIfPresent(props, "organization", f.text("NOM_OFFCL_ORGNS"));
          Rows.putIfPresent(props, "address", f.text("ADRS_GEO_L1_GDUNO_IMM"));
          Rows.putIfPresent(props, "language", language(f.text("TYPE_CS")));
          Rows.putIfPresent(props, "website", f.text("SITE_WEB_ORGNS"));
          return new Rows.Feature(
              f.text("CD_IMM") + ":" + f.text("CD_ORGNS"),
              "school",
              level(f),
              f.text("NOM_IMM"),
              null,
              props,
              f.geometry());
        })
        .toList();
  }

  /**
   * Private schools of the agglomeration. This file has no region, so the municipality code is
   * used: every municipality of the Montréal region has a code starting with 66.
   */
  public static List<Rows.Feature> privateSchools(InputStream in) {
    return Readers.geoJson(in).stream()
        .filter(f -> String.valueOf(f.text("CD_MUNCP")).startsWith("66"))
        .map(f -> {
          Map<String, Object> props = new LinkedHashMap<>();
          Rows.putIfPresent(props, "address", f.text("ADRS_GEO_L1_GDUNO"));
          Rows.putIfPresent(props, "website", f.text("SITE_WEB"));
          return new Rows.Feature(
              String.valueOf(f.text("CD_ORGNS")),
              "school",
              "private",
              f.text("NOM_OFFCL"),
              null,
              props,
              f.geometry());
        })
        .toList();
  }

  /** {@code PRIM} and {@code SEC} are 0/1 flags; other buildings are named by their order. */
  private static String level(Readers.GeoFeature f) {
    boolean primary = "1".equals(f.text("PRIM"));
    boolean secondary = "1".equals(f.text("SEC"));
    if (primary && secondary) {
      return "primary_secondary";
    }
    if (primary) {
      return "primary";
    }
    if (secondary) {
      return "secondary";
    }
    String order = String.valueOf(f.text("ORDRE_ENS"));
    if (order.contains("professionnelle")) {
      return "vocational";
    }
    return order.contains("adultes") ? "adult" : "other";
  }

  private static @Nullable String language(@Nullable String board) {
    if (board == null) {
      return null;
    }
    return switch (board) {
      case "Franco" -> "fr";
      case "Anglo" -> "en";
      default -> null;
    };
  }
}
