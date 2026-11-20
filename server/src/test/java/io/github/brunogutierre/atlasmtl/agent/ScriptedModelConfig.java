package io.github.brunogutierre.atlasmtl.agent;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the real chat model with {@link ScriptedChatModel} in tests that import it. */
@TestConfiguration(proxyBeanMethods = false)
public class ScriptedModelConfig {

  @Bean
  @Primary
  ScriptedChatModel scriptedChatModel() {
    return new ScriptedChatModel();
  }
}
