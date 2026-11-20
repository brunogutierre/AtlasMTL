package io.github.brunogutierre.atlasmtl.ingest;

import java.io.InputStream;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/** Address points of the Montréal agglomeration, for the geocoder. */
public final class Addresses {

  private Addresses() {}

  /** The address rows, read lazily. Close the stream to release the file. */
  public static Stream<Rows.Address> normalize(InputStream in) {
    return Readers.csv(in).map(Addresses::address);
  }

  static Rows.Address address(Map<String, String> row) {
    String name = row.get("SPECIFIQUE");
    return new Rows.Address(
        Long.parseLong(row.get("ID_ADRESSE")),
        Integer.parseInt(row.get("ADDR_DE")),
        Integer.parseInt(row.get("ADDR_A")),
        row.get("GENERIQUE"),
        blankToNull(row.get("LIEN")),
        name,
        orientation(row.get("ORIENTATION")),
        Text.key(name),
        Double.parseDouble(row.get("LONGITUDE")),
        Double.parseDouble(row.get("LATITUDE")));
  }

  /** {@code E}, {@code O}, {@code N} or {@code S}; the source writes {@code X} for none. */
  private static @Nullable String orientation(@Nullable String value) {
    return value == null || value.isEmpty() || value.equals("X") ? null : value;
  }

  private static @Nullable String blankToNull(@Nullable String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
