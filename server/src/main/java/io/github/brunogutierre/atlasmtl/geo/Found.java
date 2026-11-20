package io.github.brunogutierre.atlasmtl.geo;

import java.util.List;

/**
 * Places matching a query, at most the requested number.
 *
 * @param total how many places match in all, which can exceed {@code places.size()}
 */
public record Found<T>(int total, List<T> places) {}
