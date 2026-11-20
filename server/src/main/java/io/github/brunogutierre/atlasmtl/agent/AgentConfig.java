package io.github.brunogutierre.atlasmtl.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JacksonException;

@Configuration(proxyBeanMethods = false)
class AgentConfig {

  private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

  /**
   * What the model (and the visitor's tool trace) reads when a tool throws. Bad arguments are
   * explained so the model can retry; anything else, such as a database error with SQL in its
   * message, is logged here and answered with a generic error.
   */
  @Bean
  ToolExecutionExceptionProcessor toolExecutionExceptionProcessor() {
    return exception -> {
      String tool = exception.getToolDefinition().name();
      Throwable cause = exception.getCause() == null ? exception : exception.getCause();
      if (cause instanceof JacksonException || cause instanceof IllegalArgumentException) {
        return error(
            "Invalid arguments for " + tool + ": " + firstLine(cause.getMessage()),
            "Check the argument names and allowed values in the tool's schema.");
      }
      log.error("Tool {} failed", tool, cause);
      return error("The tool " + tool + " failed", "Try other arguments or a simpler question.");
    };
  }

  private static String error(String error, String hint) {
    return "{\"error\":" + quote(error) + ",\"hint\":" + quote(hint) + "}";
  }

  private static String firstLine(String message) {
    if (message == null) {
      return "unreadable value";
    }
    int end = message.indexOf('\n');
    String line = end < 0 ? message : message.substring(0, end);
    return line.length() > 200 ? line.substring(0, 200) : line;
  }

  private static String quote(String text) {
    return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
