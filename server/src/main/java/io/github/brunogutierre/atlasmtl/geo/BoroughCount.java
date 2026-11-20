package io.github.brunogutierre.atlasmtl.geo;

/**
 * How many places of a category a borough has.
 *
 * @param kind {@code borough} or {@code linked_city}
 * @param areaKm2 the borough's area
 * @param perKm2 {@code count / areaKm2}, rounded to two decimals
 */
public record BoroughCount(String name, String kind, int count, double areaKm2, double perKm2) {}
