package io.github.brunogutierre.atlasmtl.ingest;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Text normalization shared by the normalizers and, later, the geocoder. */
public final class Text {

  /** French words that stay in lower case inside a name. */
  private static final Set<String> SMALL_WORDS =
      Set.of("de", "du", "des", "la", "le", "les", "et", "en", "au", "aux", "sur", "pour", "à");

  /** Health network acronyms that stay in upper case. */
  private static final Set<String> ACRONYMS =
      Set.of("CLSC", "CHSLD", "CHUM", "CHU", "CUSM", "CIUSSS", "CISSS", "GMF", "GMF-U", "IRCM");

  private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+(?:['’][\\p{L}]+)?");

  private Text() {}

  /**
   * Turns an upper-case name into title case, as French place names are written: {@code "HÔPITAL DU
   * SACRÉ-COEUR-DE-MONTRÉAL"} → {@code "Hôpital du Sacré-Coeur-de-Montréal"}. Names that already
   * mix cases are returned as they are.
   */
  public static String titleCase(String name) {
    if (!name.equals(name.toUpperCase(Locale.ROOT))) {
      return name;
    }
    Matcher words = WORD.matcher(name);
    var out = new StringBuilder();
    boolean first = true;
    while (words.find()) {
      String word = words.group();
      String replacement;
      if (ACRONYMS.contains(word)) {
        replacement = word;
      } else {
        String lower = word.toLowerCase(Locale.CANADA_FRENCH);
        if (first) {
          replacement = upperFirst(capitalize(lower));
        } else if (SMALL_WORDS.contains(lower)) {
          replacement = lower;
        } else {
          replacement = capitalize(lower);
        }
      }
      words.appendReplacement(out, Matcher.quoteReplacement(replacement));
      first = false;
    }
    words.appendTail(out);
    return out.toString();
  }

  /** {@code "l'île"} → {@code "L'Île"}; {@code "d'adaptation"} → {@code "d'Adaptation"}. */
  private static String capitalize(String word) {
    int apostrophe = Math.max(word.indexOf('\''), word.indexOf('’'));
    if (apostrophe == 1 && word.length() > 2) {
      return word.substring(0, 2) + upperFirst(word.substring(2));
    }
    return upperFirst(word);
  }

  private static String upperFirst(String word) {
    return word.isEmpty()
        ? word
        : word.substring(0, 1).toUpperCase(Locale.CANADA_FRENCH) + word.substring(1);
  }

  /**
   * The key street names are matched on: lower case, no accents, words separated by single spaces.
   * {@code "Côte-des-Neiges"} → {@code "cote des neiges"}.
   */
  public static String key(String text) {
    String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    return plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
  }
}
