package io.github.brunogutierre.atlasmtl.agent;

import io.github.brunogutierre.atlasmtl.agent.ToolResults.Brief;
import io.github.brunogutierre.atlasmtl.geo.Borough;
import io.github.brunogutierre.atlasmtl.geo.Category;
import io.github.brunogutierre.atlasmtl.geo.Found;
import io.github.brunogutierre.atlasmtl.geo.GeoQueries;
import io.github.brunogutierre.atlasmtl.geo.Geocoder;
import io.github.brunogutierre.atlasmtl.geo.Match;
import io.github.brunogutierre.atlasmtl.geo.Near;
import io.github.brunogutierre.atlasmtl.geo.Place;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * The only things the model can do. Each tool checks its arguments, runs one fixed query and
 * answers with a short summary; it never sees or writes SQL. See
 * docs/adr/0001-typed-tools-no-text-to-sql.md.
 */
@Component
public class AtlasTools {

  static final int DEFAULT_LIMIT = 50;

  /** How many places a summary names; the rest are on the map. */
  static final int EXAMPLES = 10;

  private static final Map<Category, String> DESCRIPTIONS =
      Map.of(
          Category.PARK, "Parks and green spaces of the City of Montréal",
          Category.SCHOOL, "Public and private primary, secondary, vocational and adult schools",
          Category.HOSPITAL, "Hospitals and university health institutes",
          Category.CLSC, "CLSCs: local community health and social service centres",
          Category.LONG_TERM_CARE, "CHSLDs: long-term care residences",
          Category.AMBULATORY_CARE, "Other health centres with outpatient services");

  private final GeoQueries geo;
  private final Geocoder geocoder;
  private final ResultStore store;

  public AtlasTools(GeoQueries geo, Geocoder geocoder, ResultStore store) {
    this.geo = geo;
    this.geocoder = geocoder;
    this.store = store;
  }

  @Tool(
      name = "describe_categories",
      description = "Lists the categories of places that can be searched, with how many exist.")
  public List<ToolResults.CategoryInfo> describeCategories() {
    var counts = geo.countByCategory();
    return counts.entrySet().stream()
        .map(e ->
            new ToolResults.CategoryInfo(e.getKey(), DESCRIPTIONS.get(e.getKey()), e.getValue()))
        .toList();
  }

  @Tool(
      name = "find_features",
      description =
          "Finds places of a category in the Montréal agglomeration, optionally inside one borough"
              + " or linked city, and optionally within a radius of a point (get the point from"
              + " geocode). Results are drawn on the map; the answer names a few examples.")
  public Object findFeatures(
      @ToolParam(description = "Kind of place") Category category,
      @ToolParam(required = false, description = "Official borough or linked city name")
          @Nullable String borough,
      @ToolParam(required = false, description = "Longitude of the center, with nearLatitude")
          @Nullable Double nearLongitude,
      @ToolParam(required = false, description = "Latitude of the center")
          @Nullable Double nearLatitude,
      @ToolParam(required = false, description = "Search radius in meters, 1 to 5000")
          @Nullable Integer radiusMeters,
      @ToolParam(
              required = false,
              description = "Only places of at least this many hectares, such as large parks")
          @Nullable Double minAreaHectares,
      @ToolParam(required = false, description = "Most places to return, 1 to 500 (default 50)")
          @Nullable Integer limit) {
    if (minAreaHectares != null
        && (minAreaHectares <= 0 || minAreaHectares > GeoQueries.MAX_HECTARES)) {
      return new ToolResults.Error(
          "minAreaHectares is " + minAreaHectares + ", outside 0 to " + GeoQueries.MAX_HECTARES,
          "Retry with a positive area of at most " + GeoQueries.MAX_HECTARES + " hectares.");
    }
    Borough area = null;
    if (borough != null) {
      area = geo.borough(borough).orElse(null);
      if (area == null) {
        return unknownBorough(borough);
      }
    }
    Near near = null;
    if (nearLongitude != null || nearLatitude != null) {
      if (nearLongitude == null || nearLatitude == null) {
        return new ToolResults.Error(
            "nearLongitude and nearLatitude go together", "Pass both, from geocode.");
      }
      int meters = radiusMeters == null ? 1_000 : radiusMeters;
      if (meters < 1 || meters > GeoQueries.MAX_METERS) {
        return outOfRange("radiusMeters", meters, 1, GeoQueries.MAX_METERS);
      }
      near = new Near(nearLongitude, nearLatitude, meters);
    }
    int max = limit == null ? DEFAULT_LIMIT : limit;
    if (max < 1 || max > GeoQueries.MAX_LIMIT) {
      return outOfRange("limit", max, 1, GeoQueries.MAX_LIMIT);
    }
    Found<Place> found = geo.find(category, area, near, minAreaHectares, max);
    String id = store.put("find_features", found.places());
    return new ToolResults.Places(
        id,
        found.total(),
        found.places().size(),
        found.places().stream().limit(EXAMPLES).map(p -> brief(p, null)).toList());
  }

  @Tool(
      name = "within_distance",
      description =
          "Finds places of one category that have a place of another category within a distance,"
              + " each with its nearest one, closest first. Example: schools within 1000 meters"
              + " of a hospital.")
  public Object withinDistance(
      @ToolParam(description = "The places to return") Category sourceCategory,
      @ToolParam(description = "The places they must be near") Category targetCategory,
      @ToolParam(description = "Distance in meters, 1 to 5000") int meters,
      @ToolParam(required = false, description = "Official borough or linked city name")
          @Nullable String borough,
      @ToolParam(required = false, description = "Most pairs to return, 1 to 500 (default 50)")
          @Nullable Integer limit) {
    if (meters < 1 || meters > GeoQueries.MAX_METERS) {
      return outOfRange("meters", meters, 1, GeoQueries.MAX_METERS);
    }
    int max = limit == null ? DEFAULT_LIMIT : limit;
    if (max < 1 || max > GeoQueries.MAX_LIMIT) {
      return outOfRange("limit", max, 1, GeoQueries.MAX_LIMIT);
    }
    Borough area = null;
    if (borough != null) {
      area = geo.borough(borough).orElse(null);
      if (area == null) {
        return unknownBorough(borough);
      }
    }
    Found<Match> found = geo.withinDistance(sourceCategory, targetCategory, meters, area, max);
    var places = new LinkedHashMap<Long, Place>();
    for (Match m : found.places()) {
      places.putIfAbsent(m.place().id(), m.place());
      places.putIfAbsent(m.nearest().id(), m.nearest());
    }
    String id = store.put("within_distance", new ArrayList<>(places.values()));
    return new ToolResults.Pairs(
        id,
        found.total(),
        found.places().size(),
        found.places().stream()
            .limit(EXAMPLES)
            .map(m ->
                new ToolResults.Pair(brief(m.place(), null), brief(m.nearest(), null), m.meters()))
            .toList());
  }

  @Tool(
      name = "count_by_borough",
      description =
          "Counts places of a category in each borough and linked city, with the density per"
              + " km², highest count first.")
  public ToolResults.Counts countByBorough(
      @ToolParam(description = "Kind of place") Category category) {
    return new ToolResults.Counts(
        category,
        geo.countByBorough(category).stream()
            .map(c -> new ToolResults.BoroughRow(c.name(), c.kind(), c.count(), c.perKm2()))
            .toList());
  }

  @Tool(
      name = "geocode",
      description =
          "Finds the coordinates of a street address in the Montréal agglomeration, such as"
              + " '1000 rue Sherbrooke Ouest'. It does not know place names like 'Olympic Stadium'.")
  public Object geocode(@ToolParam(description = "A civic number and street") String address) {
    return geocoder
        .geocode(address)
        .<Object>map(g ->
            new ToolResults.Location(
                g.address(), g.longitude(), g.latitude(), g.exact(), g.borough()))
        .orElseGet(() ->
            new ToolResults.Error(
                "No Montréal address matches '" + address + "'",
                "Check the civic number and street name; place names are not supported."));
  }

  @Tool(
      name = "get_borough",
      description =
          "Finds a borough or linked city of the agglomeration by name and gives its kind, area"
              + " and a point inside it.")
  public Object getBorough(@ToolParam(description = "A borough or linked city name") String name) {
    return geo.borough(name)
        .<Object>map(b ->
            new ToolResults.BoroughInfo(
                b.name(), b.kind(), b.areaKm2(), b.longitude(), b.latitude()))
        .orElseGet(() -> unknownBorough(name));
  }

  private static Brief brief(Place p, @Nullable Integer meters) {
    return new Brief(p.name(), p.subcategory(), p.borough(), meters);
  }

  private static ToolResults.Error unknownBorough(String name) {
    return new ToolResults.Error(
        "No borough or linked city is named like '" + name + "'",
        "Use the official name, such as 'Le Plateau-Mont-Royal' or 'Westmount'; count_by_borough"
            + " lists them all.");
  }

  private static ToolResults.Error outOfRange(String argument, int value, int min, int max) {
    return new ToolResults.Error(
        argument + " is " + value + ", outside " + min + " to " + max,
        "Retry with a value from " + min + " to " + max + ".");
  }
}
