package io.github.brunogutierre.atlasmtl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** The AtlasMTL web application: API, agent and, later, the Angular build. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AtlasApplication {

  public static void main(String[] args) {
    SpringApplication.run(AtlasApplication.class, args);
  }
}
