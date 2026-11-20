package io.github.brunogutierre.atlasmtl.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.dao.DataAccessResourceFailureException;

class AgentConfigTest {

  private final ToolDefinition tool =
      ToolDefinition.builder().name("find_features").description("d").inputSchema("{}").build();

  @Test
  void hidesUnexpectedFailuresFromTheModelAndTheVisitor() {
    String answer =
        new AgentConfig()
            .toolExecutionExceptionProcessor()
            .process(
                new ToolExecutionException(
                    tool, new DataAccessResourceFailureException("select * from secret_table")));

    assertThat(answer).contains("The tool find_features failed").doesNotContain("secret_table");
  }

  @Test
  void explainsBadArguments() {
    String answer =
        new AgentConfig()
            .toolExecutionExceptionProcessor()
            .process(new ToolExecutionException(tool, new IllegalArgumentException("limit is 0")));

    assertThat(answer).contains("Invalid arguments for find_features: limit is 0");
  }
}
