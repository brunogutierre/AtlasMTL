package io.github.brunogutierre.atlasmtl.geo;

import tools.jackson.databind.JsonNode;

/**
 * A borough or linked city.
 *
 * @param kind {@code borough} or {@code linked_city}
 */
public record Borough(
    int id,
    String name,
    String kind,
    double areaKm2,
    double longitude,
    double latitude,
    JsonNode geometry) {}
