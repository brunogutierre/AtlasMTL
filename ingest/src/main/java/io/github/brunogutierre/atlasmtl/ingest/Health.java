package io.github.brunogutierre.atlasmtl.ingest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Health and social services installations of the Montréal region (MSSS M02). */
public final class Health {

  /** The Montréal health region in {@code RSS_CODE}. */
  static final String MONTREAL_REGION = "06";

  /**
   * The general-hospital mission ({@code CHSGS}) also covers ambulatory and community centres, so
   * only installations named as hospitals become {@code hospital}.
   */
  private static final Pattern HOSPITAL_NAME =
      Pattern.compile(
          "H[ÔO]PITAL|CENTRE HOSPITALIER|^CHU |INSTITUT DE CARDIOLOGIE|INSTITUT UNIVERSITAIRE DE"
              + " G[ÉE]RIATRIE|H[ÔO]TEL-DIEU");

  private static final Pattern RESEARCH = Pattern.compile("RECHERCHE");

  private Health() {}

  public static List<Rows.Feature> normalize(InputStream in) {
    try (Stream<Map<String, String>> rows = Readers.csv(in)) {
      return rows.filter(r -> MONTREAL_REGION.equals(r.get("RSS_CODE")))
          .flatMap(r -> features(r).stream())
          .toList();
    }
  }

  /** One feature per category an installation belongs to: a CLSC can also host a CHSLD. */
  static List<Rows.Feature> features(Map<String, String> row) {
    String name = row.get("INSTAL_NOM");
    boolean general = yes(row, "CHSGS");
    boolean hospital =
        general && HOSPITAL_NAME.matcher(name).find() && !RESEARCH.matcher(name).find();
    var categories = new ArrayList<String>();
    if (hospital) {
      categories.add("hospital");
    }
    if (yes(row, "CLSC")) {
      categories.add("clsc");
    }
    if (yes(row, "CHSLD")) {
      categories.add("long_term_care");
    }
    if (general && categories.isEmpty()) {
      categories.add("ambulatory_care");
    }
    Map<String, Object> props = new LinkedHashMap<>();
    Rows.putIfPresent(props, "address", row.get("ADRESSE"));
    Rows.putIfPresent(props, "postal_code", row.get("CODE_POSTA"));
    Rows.putIfPresent(props, "operator", row.get("ETAB_NOM"));
    String point =
        "{\"type\":\"Point\",\"coordinates\":["
            + Double.parseDouble(row.get("LONGITUDE"))
            + ","
            + Double.parseDouble(row.get("LATITUDE"))
            + "]}";
    return categories.stream()
        .map(category ->
            new Rows.Feature(
                row.get("INSTAL_COD") + ":" + category,
                category,
                null,
                Text.titleCase(name),
                null,
                props,
                point))
        .toList();
  }

  private static boolean yes(Map<String, String> row, String column) {
    return "Oui".equals(row.get(column));
  }
}
