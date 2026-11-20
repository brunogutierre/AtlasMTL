package io.github.brunogutierre.atlasmtl.ingest;

import java.text.Normalizer;
import java.util.Locale;

/** Text normalization shared by the normalizers and, later, the geocoder. */
public final class Text {

  private Text() {}

  /**
   * The key street names are matched on: lower case, no accents, words separated by single spaces.
   * {@code "Côte-des-Neiges"} → {@code "cote des neiges"}.
   */
  public static String key(String text) {
    String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    return plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
  }
}
