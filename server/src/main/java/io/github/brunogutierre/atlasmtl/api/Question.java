package io.github.brunogutierre.atlasmtl.api;

/** A question in plain English or French. */
public record Question(String text) {

  /** Longer questions cost tokens without helping the tools. */
  public static final int MAX_LENGTH = 500;
}
