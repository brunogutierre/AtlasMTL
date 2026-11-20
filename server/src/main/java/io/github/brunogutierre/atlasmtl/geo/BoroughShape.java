package io.github.brunogutierre.atlasmtl.geo;

import tools.jackson.databind.JsonNode;

/**
 * A borough or linked city outline for the map, simplified to about 10 m.
 *
 * @param kind {@code borough} or {@code linked_city}
 */
public record BoroughShape(int id, String name, String kind, JsonNode geometry) {}
