package io.github.brunogutierre.atlasmtl.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.brunogutierre.atlasmtl.TestDatabase;
import io.github.brunogutierre.atlasmtl.agent.ScriptedChatModel;
import io.github.brunogutierre.atlasmtl.agent.ScriptedChatModel.Turn;
import io.github.brunogutierre.atlasmtl.agent.ScriptedModelConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "atlas.limits.questions-per-minute=2")
@Import({TestDatabase.class, ScriptedModelConfig.class})
class RateLimitTest {

  @LocalServerPort int port;
  @Autowired ScriptedChatModel model;

  @Test
  void refusesAVisitorAskingTooFast() throws Exception {
    model.play(Turn.text("one"), Turn.text("two"), Turn.text("three"));

    assertThat(AskHttp.ask(port, "first").statusCode()).isEqualTo(200);
    assertThat(AskHttp.ask(port, "second").statusCode()).isEqualTo(200);
    var third = AskHttp.ask(port, "third");

    assertThat(third.statusCode()).isEqualTo(429);
    assertThat(third.body()).contains("\"code\":\"rate_limited\"");
    assertThat(third.headers().firstValue("Retry-After"))
        .hasValueSatisfying(seconds -> assertThat(Long.parseLong(seconds)).isBetween(1L, 60L));
  }
}
