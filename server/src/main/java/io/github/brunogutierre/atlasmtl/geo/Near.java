package io.github.brunogutierre.atlasmtl.geo;

/** A circle to search in: a point and a radius in meters. */
public record Near(double longitude, double latitude, int meters) {}
