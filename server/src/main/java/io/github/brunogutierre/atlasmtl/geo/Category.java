package io.github.brunogutierre.atlasmtl.geo;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** The kinds of places the tools can search, as stored in {@code feature.category}. */
public enum Category {
  PARK,
  SCHOOL,
  HOSPITAL,
  CLSC,
  LONG_TERM_CARE,
  AMBULATORY_CARE;

  /** The value in the database and on the wire: {@code long_term_care}. */
  @JsonValue
  public String id() {
    return name().toLowerCase(Locale.ROOT);
  }
}
