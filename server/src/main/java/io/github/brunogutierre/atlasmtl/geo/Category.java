package io.github.brunogutierre.atlasmtl.geo;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

/**
 * The kinds of places the tools can search. On the wire they are the constant names ({@code
 * LONG_TERM_CARE}), which is also what the tool schemas offer the model; in the database they are
 * lower case ({@code long_term_care}).
 */
public enum Category {
  PARK,
  SCHOOL,
  HOSPITAL,
  CLSC,
  LONG_TERM_CARE,
  AMBULATORY_CARE;

  /** The value in {@code feature.category}. */
  public String id() {
    return name().toLowerCase(Locale.ROOT);
  }

  /** Accepts any case, since models sometimes write {@code school} for {@code SCHOOL}. */
  @JsonCreator
  public static Category of(String value) {
    return valueOf(value.strip().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_'));
  }
}
