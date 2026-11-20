package io.github.brunogutierre.atlasmtl.geo;

import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Finds Montréal addresses in the city's address points, without any external service. See
 * docs/adr/0006-local-geocoder.md.
 */
@Service
public class Geocoder {

  private static final Map<String, String> ORIENTATION_NAMES =
      Map.of("E", "Est", "O", "Ouest", "N", "Nord", "S", "Sud");

  private final JdbcClient jdbc;

  public Geocoder(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Geocoded> geocode(String text) {
    return AddressQuery.parse(text).flatMap(this::find);
  }

  /**
   * Ranks candidate points by, in order: street name similarity (with or without its link words;
   * the {@code %} operator uses the trigram index), the same street type, the same orientation, the
   * borough or linked city named after a comma, the number falling in the point's range on the same
   * side of the street, and finally the nearest number.
   */
  Optional<Geocoded> find(AddressQuery query) {
    return jdbc.sql(
            """
            select a.number_from, a.number_to, a.street_type, a.street_link, a.street_name,
              a.orientation, st_x(a.geom) as lon, st_y(a.geom) as lat,
              (select b.name from borough b where st_contains(b.geom, a.geom) limit 1) as borough,
              (:number between a.number_from and a.number_to
                and a.number_from % 2 = :number % 2) as exact
            from address a
            where a.street_key % :key or a.street_key % :fullKey
            order by greatest(similarity(a.street_key, :key), similarity(a.street_key, :fullKey)) desc,
              (a.street_type = cast(:type as text)) desc nulls last,
              (a.orientation is not distinct from cast(:orientation as text)) desc,
              (cast(:locality as text) is not null and exists (select 1 from borough b
                where st_contains(b.geom, a.geom)
                  and word_similarity(cast(:locality as text), unaccent(lower(b.name))) >= 0.6)) desc,
              (:number between a.number_from and a.number_to
                and a.number_from % 2 = :number % 2) desc,
              least(abs(a.number_from - :number), abs(a.number_to - :number)),
              a.number_from
            limit 1
            """)
        .param("number", query.number())
        .param("key", query.streetKey())
        .param("fullKey", query.fullKey())
        .param("type", query.streetType())
        .param("orientation", query.orientation())
        .param("locality", query.locality())
        .query((rs, i) -> {
          boolean exact = rs.getBoolean("exact");
          int number =
              exact
                  ? query.number()
                  : nearest(rs.getInt("number_from"), rs.getInt("number_to"), query.number());
          return new Geocoded(
              format(
                  number,
                  rs.getString("street_type"),
                  rs.getString("street_link"),
                  rs.getString("street_name"),
                  rs.getString("orientation")),
              rs.getDouble("lon"),
              rs.getDouble("lat"),
              exact,
              rs.getString("borough"));
        })
        .optional();
  }

  private static int nearest(int from, int to, int wanted) {
    return Math.abs(from - wanted) <= Math.abs(to - wanted) ? from : to;
  }

  /** {@code 1000 rue Sherbrooke Ouest}, {@code 3869 chemin de la Côte-des-Neiges}. */
  static String format(
      int number,
      String type,
      @org.jspecify.annotations.Nullable String link,
      String name,
      @org.jspecify.annotations.Nullable String orientation) {
    var out = new StringBuilder().append(number).append(' ');
    if (Character.isDigit(name.charAt(0))) {
      // Numbered streets read "16e Avenue".
      out.append(name)
          .append(' ')
          .append(Character.toUpperCase(type.charAt(0)))
          .append(type.substring(1));
    } else {
      out.append(type).append(' ');
      if (link != null) {
        out.append(link).append(link.endsWith("'") ? "" : " ");
      }
      out.append(name);
    }
    if (orientation != null) {
      out.append(' ').append(ORIENTATION_NAMES.getOrDefault(orientation, orientation));
    }
    return out.toString();
  }
}
