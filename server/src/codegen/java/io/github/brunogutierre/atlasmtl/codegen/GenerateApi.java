package io.github.brunogutierre.atlasmtl.codegen;

import io.github.brunogutierre.recordbridge.generator.Generator;
import io.github.brunogutierre.recordbridge.generator.GeneratorConfig;
import io.github.brunogutierre.recordbridge.generator.GeneratorResult;
import io.github.brunogutierre.recordbridge.reader.UnmarkedNullness;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Writes the Angular client of the {@code api} package's controllers, with every type they use. Run
 * by {@code ./gradlew :server:generateTypeScript}.
 *
 * <p>Arguments: the output folder, then the compiled class folders joined with the path separator.
 * The classes are on this program's class path, so Spring and Jackson resolve to the same classes
 * the generator reads them with.
 */
public final class GenerateApi {

  private GenerateApi() {}

  public static void main(String[] args) {
    Path output = Path.of(args[0]);
    List<Path> classes = Arrays.stream(args[1].split(File.pathSeparator)).map(Path::of).toList();
    GeneratorResult result =
        Generator.generate(
            new GeneratorConfig(
                List.of("io.github.brunogutierre.atlasmtl.api"), output, UnmarkedNullness.NON_NULL),
            classes,
            GenerateApi.class.getClassLoader());
    System.out.println(
        "RecordBridge: "
            + result.controllers()
            + " controllers, "
            + result.types()
            + " types → "
            + output);
  }
}
