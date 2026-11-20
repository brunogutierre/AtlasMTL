package io.github.brunogutierre.atlasmtl.geo;

/**
 * A place with the nearest place of another category within the requested distance.
 *
 * @param meters the distance between them, rounded to the meter
 */
public record Match(Place place, Place nearest, int meters) {}
