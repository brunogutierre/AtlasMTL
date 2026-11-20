package io.github.brunogutierre.atlasmtl.ingest;

import java.io.InputStream;
import java.util.List;

/** The 19 boroughs and 15 linked cities of the Montréal agglomeration. */
public final class Boroughs {

  private Boroughs() {}

  public static List<Rows.Borough> normalize(InputStream in) {
    return Readers.geoJson(in).stream()
        .map(f ->
            new Rows.Borough(
                Integer.parseInt(required(f, "CODEID")),
                required(f, "NOM"),
                "Arrondissement".equals(f.text("TYPE")) ? "borough" : "linked_city",
                f.geometry()))
        .toList();
  }

  private static String required(Readers.GeoFeature f, String name) {
    String value = f.text(name);
    if (value == null) {
      throw new IllegalArgumentException("A borough has no " + name + ": " + f.properties());
    }
    return value;
  }
}
