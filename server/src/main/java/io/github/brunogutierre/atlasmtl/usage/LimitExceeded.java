package io.github.brunogutierre.atlasmtl.usage;

/**
 * A question refused by a limit.
 *
 * @see Limits
 */
public class LimitExceeded extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** Which limit: the web app shows a message for each. */
  public enum Code {
    RATE_LIMITED,
    VISITOR_BUDGET,
    GLOBAL_BUDGET
  }

  private final Code code;
  private final long retryAfterSeconds;

  public LimitExceeded(Code code, String message, long retryAfterSeconds) {
    super(message);
    this.code = code;
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public Code code() {
    return code;
  }

  /** When asking again can succeed. */
  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
