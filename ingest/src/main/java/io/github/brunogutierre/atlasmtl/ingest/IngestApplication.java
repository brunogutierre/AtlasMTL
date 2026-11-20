package io.github.brunogutierre.atlasmtl.ingest;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

/**
 * Command-line entry point: {@code ingest [--force] [--only=parks,health]}. Applies the schema
 * migrations, then refreshes the datasets.
 */
@SpringBootApplication
public class IngestApplication {

  public static void main(String[] args) {
    System.exit(SpringApplication.exit(SpringApplication.run(IngestApplication.class, args)));
  }

  @Bean
  Ckan ckan() {
    return new Ckan();
  }

  @Bean
  @Profile("!test")
  ApplicationRunner ingest(IngestJob job) {
    return args -> {
      Set<String> only =
          args.containsOption("only")
              ? args.getOptionValues("only").stream()
                  .flatMap(v -> Arrays.stream(v.split(",")))
                  .map(String::strip)
                  .collect(Collectors.toSet())
              : Set.of();
      job.run(only, args.containsOption("force"));
    };
  }
}
