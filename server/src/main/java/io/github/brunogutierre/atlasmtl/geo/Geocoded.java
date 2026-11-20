package io.github.brunogutierre.atlasmtl.geo;

import org.jspecify.annotations.Nullable;

/**
 * Where an address is.
 *
 * @param address the address that was found, which can differ from the one asked for
 * @param exact whether the number falls in the address point's range, on the same side of the
 *     street; otherwise the nearest number on that street was used
 */
public record Geocoded(
    String address, double longitude, double latitude, boolean exact, @Nullable String borough) {}
