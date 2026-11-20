package io.github.brunogutierre.atlasmtl.geo;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A street address parsed from free text, in French or English: {@code "1000 rue Sherbrooke
 * Ouest"}, {@code "1000 Sherbrooke St W"}, {@code "3869 chemin de la Côte-des-Neiges, Montréal"}.
 *
 * @param streetKey the street name in the form of {@code address.street_key}: lower case, no
 *     accents, single spaces, without leading link words ({@code "cote des neiges"})
 * @param fullKey the same with its link words, since some official names keep them ({@code "de
 *     maisonneuve"})
 * @param streetType the type of street in French ({@code rue}, {@code boulevard}...), if given
 * @param orientation {@code E}, {@code O}, {@code N} or {@code S}
 * @param locality what follows the first comma, such as a linked city ({@code "westmount"})
 */
public record AddressQuery(
    int number,
    String streetKey,
    String fullKey,
    @Nullable String streetType,
    @Nullable String orientation,
    @Nullable String locality) {

  private static final Pattern NUMBER = Pattern.compile("^\\s*#?(\\d{1,6})[a-z]?\\b[\\s,-]*(.+)$");

  /** Street type words, in French or English, and the French type each one means. */
  private static final Map<String, String> STREET_TYPES =
      Map.ofEntries(
          Map.entry("rue", "rue"),
          Map.entry("street", "rue"),
          Map.entry("st", "rue"),
          Map.entry("avenue", "avenue"),
          Map.entry("av", "avenue"),
          Map.entry("ave", "avenue"),
          Map.entry("boulevard", "boulevard"),
          Map.entry("boul", "boulevard"),
          Map.entry("blvd", "boulevard"),
          Map.entry("bd", "boulevard"),
          Map.entry("chemin", "chemin"),
          Map.entry("ch", "chemin"),
          Map.entry("road", "chemin"),
          Map.entry("rd", "chemin"),
          Map.entry("place", "place"),
          Map.entry("pl", "place"),
          Map.entry("montee", "montée"),
          Map.entry("terrasse", "terrasse"),
          Map.entry("croissant", "croissant"),
          Map.entry("crescent", "croissant"),
          Map.entry("square", "square"),
          Map.entry("allee", "allée"),
          Map.entry("promenade", "promenade"),
          Map.entry("route", "route"),
          Map.entry("impasse", "impasse"));

  private static final Set<String> LINKS = Set.of("de", "du", "des", "la", "le", "les", "d", "l");

  private static final Map<String, String> ORIENTATIONS =
      Map.ofEntries(
          Map.entry("ouest", "O"),
          Map.entry("o", "O"),
          Map.entry("west", "O"),
          Map.entry("w", "O"),
          Map.entry("est", "E"),
          Map.entry("e", "E"),
          Map.entry("east", "E"),
          Map.entry("nord", "N"),
          Map.entry("n", "N"),
          Map.entry("north", "N"),
          Map.entry("sud", "S"),
          Map.entry("s", "S"),
          Map.entry("south", "S"));

  /** City, province and postal code words that may follow the street. */
  private static final Pattern TRAILER =
      Pattern.compile("\\b(montreal|qc|quebec|canada|[a-z]\\d[a-z] ?\\d[a-z]\\d)\\b.*$");

  /** Parses the text, or returns empty when it doesn't start with a civic number. */
  public static Optional<AddressQuery> parse(String text) {
    Matcher m = NUMBER.matcher(plain(text));
    if (!m.matches()) {
      return Optional.empty();
    }
    String street = m.group(2);
    String locality = null;
    int comma = street.indexOf(',');
    if (comma > 0) {
      locality = words(TRAILER.matcher(street.substring(comma + 1)).replaceFirst(""));
      street = street.substring(0, comma);
    }
    List<String> words =
        new ArrayList<>(Arrays.asList(words(TRAILER.matcher(street).replaceFirst("")).split(" ")));
    String orientation = null;
    if (words.size() > 1 && ORIENTATIONS.containsKey(words.getLast())) {
      orientation = ORIENTATIONS.get(words.removeLast());
    }
    String type = null;
    if (words.size() > 1 && STREET_TYPES.containsKey(words.getLast())) {
      type = STREET_TYPES.get(words.removeLast()); // "Sherbrooke St", "16e Avenue"
    }
    if (words.size() > 1 && STREET_TYPES.containsKey(words.getFirst())) {
      type = STREET_TYPES.get(words.removeFirst()); // "rue Sherbrooke"
    }
    String fullKey = String.join(" ", words);
    while (words.size() > 1 && LINKS.contains(words.getFirst())) {
      words.removeFirst(); // "de la Côte-des-Neiges"
    }
    String key = String.join(" ", words);
    if (key.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new AddressQuery(
            Integer.parseInt(m.group(1)),
            key,
            fullKey,
            type,
            orientation,
            locality == null || locality.isEmpty() ? null : locality));
  }

  /** Lower case, without accents or apostrophes: {@code "Côte-d'Or"} → {@code "cote d or"}. */
  static String plain(String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replace('\'', ' ')
        .replace('’', ' ');
  }

  private static String words(String text) {
    return text.replaceAll("[^a-z0-9]+", " ").strip();
  }
}
