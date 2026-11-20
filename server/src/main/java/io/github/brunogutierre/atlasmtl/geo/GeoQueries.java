package io.github.brunogutierre.atlasmtl.geo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The spatial queries behind the agent's tools. Every query is fixed SQL with bound parameters;
 * arguments are checked here as well as in the tools.
 */
@Service
public class GeoQueries {

  public static final int MAX_LIMIT = 500;
  public static final int MAX_METERS = 5_000;

  /** The columns of a {@link Place}, for a feature aliased {@code f} and its point {@code p}. */
  private static final String PLACE =
      """
      f.id, f.category, f.subcategory, coalesce(f.name_en, f.name_fr) as name,
      (select b.name from borough b where st_contains(b.geom, p) limit 1) as borough,
      f.props::text as props, st_x(p) as lon, st_y(p) as lat, st_asgeojson(f.geom, 6) as geometry
      """;

  private final JdbcClient jdbc;
  private final JsonMapper json;

  public GeoQueries(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  /**
   * Places of a category, optionally inside a borough and within a radius of a point (nearest
   * first). Without a point they are sorted by name.
   */
  public Found<Place> find(
      Category category, @Nullable Borough borough, @Nullable Near near, int limit) {
    checkLimit(limit);
    var sql = new StringBuilder("select ").append(PLACE).append(", count(*) over () as total");
    sql.append(" from feature f cross join lateral st_pointonsurface(f.geom) as p");
    sql.append(" where f.category = :category");
    if (borough != null) {
      sql.append(" and st_intersects(f.geom, (select geom from borough where id = :borough))");
    }
    if (near != null) {
      checkMeters(near.meters());
      sql.append(" and st_dwithin(f.geom::geography, ").append(POINT).append(", :meters)");
      sql.append(" order by f.geom::geography <-> ").append(POINT);
    } else {
      sql.append(" order by f.name_fr nulls last, f.id");
    }
    sql.append(" limit :limit");
    var query = jdbc.sql(sql.toString()).param("category", category.id()).param("limit", limit);
    if (borough != null) {
      query = query.param("borough", borough.id());
    }
    if (near != null) {
      query =
          query
              .param("lon", near.longitude())
              .param("lat", near.latitude())
              .param("meters", near.meters());
    }
    var total = new int[1];
    List<Place> places =
        query
            .query((rs, i) -> {
              total[0] = rs.getInt("total");
              return place(rs, "");
            })
            .list();
    return new Found<>(total[0], places);
  }

  /**
   * Places of {@code source} that have a place of {@code target} within {@code meters}, each with
   * its nearest one, closest pairs first.
   */
  public Found<Match> withinDistance(
      Category source, Category target, int meters, @Nullable Borough borough, int limit) {
    checkLimit(limit);
    checkMeters(meters);
    String sql =
        """
        select %s, count(*) over () as total, t.*,
          round(st_distance(f.geom::geography, t.t_geom::geography))::int as meters
        from feature f cross join lateral st_pointonsurface(f.geom) as p
        cross join lateral (
          select t.id as t_id, t.category as t_category, t.subcategory as t_subcategory,
            coalesce(t.name_en, t.name_fr) as t_name,
            (select b.name from borough b where st_contains(b.geom, tp) limit 1) as t_borough,
            t.props::text as t_props, st_x(tp) as t_lon, st_y(tp) as t_lat,
            st_asgeojson(t.geom, 6) as t_geometry, t.geom as t_geom
          from feature t cross join lateral st_pointonsurface(t.geom) as tp
          where t.category = :target
            and st_dwithin(f.geom::geography, t.geom::geography, :meters)
          order by f.geom::geography <-> t.geom::geography
          limit 1) as t
        where f.category = :source %s
        order by meters, f.id
        limit :limit
        """
            .formatted(
                PLACE,
                borough == null
                    ? ""
                    : "and st_intersects(f.geom, (select geom from borough where id = :borough))");
    var query =
        jdbc.sql(sql)
            .param("source", source.id())
            .param("target", target.id())
            .param("meters", meters)
            .param("limit", limit);
    if (borough != null) {
      query = query.param("borough", borough.id());
    }
    var total = new int[1];
    List<Match> matches =
        query
            .query((rs, i) -> {
              total[0] = rs.getInt("total");
              return new Match(place(rs, ""), place(rs, "t_"), rs.getInt("meters"));
            })
            .list();
    return new Found<>(total[0], matches);
  }

  /** Places of a category per borough, with the density per km², most first. */
  public List<BoroughCount> countByBorough(Category category) {
    return jdbc.sql(
            """
            select b.name, b.kind, count(f.id)::int as n,
              st_area(b.geom::geography) / 1e6 as km2
            from borough b
            left join feature f on f.category = :category
              and st_contains(b.geom, st_pointonsurface(f.geom))
            group by b.id
            order by n desc, b.name
            """)
        .param("category", category.id())
        .query((rs, i) -> {
          double km2 = rs.getDouble("km2");
          int count = rs.getInt("n");
          return new BoroughCount(
              rs.getString("name"),
              rs.getString("kind"),
              count,
              Math.round(km2 * 10) / 10.0,
              Math.round(count / km2 * 100) / 100.0);
        })
        .list();
  }

  /** How many places each category has. */
  public Map<Category, Integer> countByCategory() {
    var counts = new java.util.EnumMap<Category, Integer>(Category.class);
    for (Category category : Category.values()) {
      counts.put(category, 0);
    }
    jdbc.sql("select category, count(*)::int as n from feature group by category")
        .query((rs, i) -> {
          counts.put(
              Category.valueOf(rs.getString("category").toUpperCase(java.util.Locale.ROOT)),
              rs.getInt("n"));
          return null;
        })
        .list();
    return counts;
  }

  /**
   * The borough or linked city named like {@code name}, ignoring case and accents, or the most
   * similar name when there is no exact match ("Plateau" finds "Le Plateau-Mont-Royal").
   */
  public Optional<Borough> borough(String name) {
    return jdbc.sql(
            """
            select id, name, kind, st_area(geom::geography) / 1e6 as km2,
              st_x(st_pointonsurface(geom)) as lon, st_y(st_pointonsurface(geom)) as lat,
              st_asgeojson(geom, 6) as geometry
            from borough
            where word_similarity(unaccent(lower(:name)), unaccent(lower(name))) >= 0.5
            order by unaccent(lower(name)) = unaccent(lower(:name)) desc,
              word_similarity(unaccent(lower(:name)), unaccent(lower(name))) desc,
              length(name)
            limit 1
            """)
        .param("name", name)
        .query((rs, i) ->
            new Borough(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("kind"),
                Math.round(rs.getDouble("km2") * 10) / 10.0,
                rs.getDouble("lon"),
                rs.getDouble("lat"),
                json.readTree(rs.getString("geometry"))))
        .optional();
  }

  private static final String POINT = "st_setsrid(st_makepoint(:lon, :lat), 4326)::geography";

  private Place place(ResultSet rs, String prefix) throws SQLException {
    return new Place(
        rs.getLong(prefix + "id"),
        Category.valueOf(rs.getString(prefix + "category").toUpperCase(java.util.Locale.ROOT)),
        rs.getString(prefix + "subcategory"),
        rs.getString(prefix + "name"),
        rs.getString(prefix + "borough"),
        json.readValue(rs.getString(prefix + "props"), new TypeReference<Map<String, Object>>() {}),
        rs.getDouble(prefix + "lon"),
        rs.getDouble(prefix + "lat"),
        json.readTree(rs.getString(prefix + "geometry")));
  }

  private static void checkLimit(int limit) {
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
    }
  }

  private static void checkMeters(int meters) {
    if (meters < 1 || meters > MAX_METERS) {
      throw new IllegalArgumentException("meters must be between 1 and " + MAX_METERS);
    }
  }
}
