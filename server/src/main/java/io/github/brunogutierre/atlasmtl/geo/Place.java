package io.github.brunogutierre.atlasmtl.geo;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * A feature as the tools and the map see it.
 *
 * @param borough the borough or linked city its point falls in
 * @param longitude a point on the feature, for labels and distances
 * @param geometry GeoJSON geometry
 */
public record Place(
    long id,
    Category category,
    @Nullable String subcategory,
    @Nullable String name,
    @Nullable String borough,
    Map<String, Object> props,
    double longitude,
    double latitude,
    JsonNode geometry) {}
