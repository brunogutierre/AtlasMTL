package io.github.brunogutierre.atlasmtl.agent;

import io.github.brunogutierre.atlasmtl.geo.Category;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What the tools return to the model: short summaries. The full GeoJSON stays in the {@link
 * ResultStore} under {@code resultId}, for the map.
 */
public final class ToolResults {

  private ToolResults() {}

  /** A place in one line. */
  public record Brief(
      @Nullable String name,
      @Nullable String subcategory,
      @Nullable String borough,
      @Nullable Integer meters) {}

  /**
   * @param shown how many places the map shows; {@code examples} lists the first few of them
   */
  public record Places(String resultId, int total, int shown, List<Brief> examples) {}

  public record Pair(Brief place, Brief nearest, int meters) {}

  public record Pairs(String resultId, int total, int shown, List<Pair> examples) {}

  public record BoroughRow(String name, String kind, int count, double perKm2) {}

  public record Counts(Category category, List<BoroughRow> boroughs) {}

  public record CategoryInfo(Category category, String description, int places) {}

  public record Location(
      String address, double longitude, double latitude, boolean exact, @Nullable String borough) {}

  public record BoroughInfo(
      String name, String kind, double areaKm2, double longitude, double latitude) {}

  /** A failed call, worded for the model to recover: what was wrong and what to try instead. */
  public record Error(String error, String hint) {}
}
