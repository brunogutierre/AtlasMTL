package io.github.brunogutierre.atlasmtl.api;

import io.github.brunogutierre.atlasmtl.agent.AgentEvent;
import io.github.brunogutierre.atlasmtl.agent.Answers;
import io.github.brunogutierre.atlasmtl.usage.LimitExceeded;
import io.github.brunogutierre.atlasmtl.usage.Limits;
import io.github.brunogutierre.atlasmtl.usage.Visitors;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Answers questions as server-sent events over POST: the question stays out of URLs and logs, and
 * the answer streams as it is written. See docs/adr/0003-sse-agent-events.md.
 */
@RestController
@RequestMapping("/api")
public class AskController {

  private final Answers answers;
  private final Visitors visitors;
  private final Limits limits;

  public AskController(Answers answers, Visitors visitors, Limits limits) {
    this.answers = answers;
    this.visitors = visitors;
    this.limits = limits;
  }

  @PostMapping(path = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<ServerSentEvent<AgentEvent>> ask(
      @RequestBody Question question, HttpServletRequest request) {
    String text = question.text() == null ? "" : question.text().strip();
    if (text.isEmpty()) {
      throw new InvalidQuestionException("Ask a question.");
    }
    if (text.length() > Question.MAX_LENGTH) {
      throw new InvalidQuestionException(
          "Questions are limited to " + Question.MAX_LENGTH + " characters.");
    }
    String visitor = visitors.id(request);
    limits.checkRate(visitor);
    return answers.ask(text, visitor).map(event -> ServerSentEvent.builder(event).build());
  }

  @ExceptionHandler(InvalidQuestionException.class)
  ProblemDetail invalidQuestion(InvalidQuestionException e) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    problem.setTitle("Invalid question");
    return problem;
  }

  /**
   * 429 with a {@code code} the web app turns into a message in the visitor's language, and a
   * {@code Retry-After} header.
   */
  @ExceptionHandler(LimitExceeded.class)
  ResponseEntity<ProblemDetail> limitExceeded(LimitExceeded e) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    problem.setTitle("Limit reached");
    problem.setProperty("code", e.code().name().toLowerCase(Locale.ROOT));
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header("Retry-After", Long.toString(e.retryAfterSeconds()))
        .body(problem);
  }

  /** The question can't be asked as it is; the message is shown to the visitor. */
  static class InvalidQuestionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    InvalidQuestionException(String message) {
      super(message);
    }
  }
}
