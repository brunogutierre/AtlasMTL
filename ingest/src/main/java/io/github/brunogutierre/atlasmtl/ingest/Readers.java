package io.github.brunogutierre.atlasmtl.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MappingIterator;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvReadFeature;
import tools.jackson.dataformat.csv.CsvSchema;

/** Reads the two file formats the portals publish. */
public final class Readers {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final CsvMapper CSV =
      CsvMapper.builder().enable(CsvReadFeature.TRIM_SPACES).build();

  private Readers() {}

  /**
   * A GeoJSON feature: its properties and its geometry, still as JSON.
   *
   * @param geometry the geometry as GeoJSON text, ready for {@code ST_GeomFromGeoJSON}
   */
  public record GeoFeature(JsonNode properties, String geometry) {

    /** A property as text, or {@code null} when it is missing, null or blank. */
    public @org.jspecify.annotations.Nullable String text(String name) {
      JsonNode value = properties.get(name);
      if (value == null || value.isNull()) {
        return null;
      }
      String text = value.asString().strip();
      return text.isEmpty() ? null : text;
    }
  }

  /** The features of a GeoJSON {@code FeatureCollection} that have a geometry. */
  public static List<GeoFeature> geoJson(InputStream in) {
    JsonNode root = JSON.readTree(in);
    var features = new ArrayList<GeoFeature>();
    for (JsonNode feature : root.path("features")) {
      JsonNode geometry = feature.get("geometry");
      if (geometry != null && !geometry.isNull()) {
        features.add(new GeoFeature(feature.path("properties"), JSON.writeValueAsString(geometry)));
      }
    }
    return features;
  }

  /**
   * The rows of a CSV file with a header line, read lazily: the address file has 350,000 rows.
   * Close the stream to release the file.
   */
  public static Stream<Map<String, String>> csv(InputStream in) {
    CsvSchema schema = CsvSchema.emptySchema().withHeader();
    MappingIterator<Map<String, String>> rows =
        CSV.readerForMapOf(String.class).with(schema).readValues(skipBom(in));
    Iterator<Map<String, String>> iterator = rows;
    return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false)
        .onClose(rows::close);
  }

  /** Données Québec files start with a UTF-8 byte order mark, which would stick to the header. */
  private static InputStream skipBom(InputStream in) {
    try {
      var buffered = new java.io.BufferedInputStream(in);
      buffered.mark(3);
      byte[] head = buffered.readNBytes(3);
      if (!(head.length == 3
          && head[0] == (byte) 0xEF
          && head[1] == (byte) 0xBB
          && head[2] == (byte) 0xBF)) {
        buffered.reset();
      }
      return buffered;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
