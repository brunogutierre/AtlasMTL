package io.github.brunogutierre.atlasmtl.ingest;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Replaces a dataset's rows. Each load deletes and inserts inside one transaction: readers keep
 * seeing the previous rows until it commits, and a failure leaves them untouched.
 */
@Component
public class Loader {

  private static final int BATCH = 1_000;

  private final JdbcClient jdbc;
  private final JdbcTemplate template;
  private final JsonMapper json = JsonMapper.builder().build();

  public Loader(JdbcClient jdbc, JdbcTemplate template) {
    this.jdbc = jdbc;
    this.template = template;
  }

  /** The checksum of the dataset's last load, if it was loaded. */
  public Optional<String> checksum(Source source) {
    return jdbc.sql("select checksum from dataset where id = :id")
        .param("id", source.id())
        .query(String.class)
        .optional();
  }

  @Transactional
  public int features(Source source, Ckan.Download download, List<Rows.Feature> features) {
    saveDataset(source, download);
    int previous =
        jdbc.sql("delete from feature where dataset_id = :id").param("id", source.id()).update();
    // ST_MakeValid: a few source polygons self-intersect, which breaks spatial predicates.
    int loaded =
        batches(
            features.stream(),
            """
            insert into feature
              (dataset_id, source_id, category, subcategory, name_fr, name_en, props, geom)
            values (?, ?, ?, ?, ?, ?, ?::jsonb, st_makevalid(st_setsrid(st_geomfromgeojson(?), 4326)))
            """,
            f ->
                new Object[] {
                  source.id(),
                  f.sourceId(),
                  f.category(),
                  f.subcategory(),
                  f.nameFr(),
                  f.nameEn(),
                  json.writeValueAsString(f.props()),
                  f.geometry()
                });
    return checked(source, previous, loaded);
  }

  @Transactional
  public int boroughs(Source source, Ckan.Download download, List<Rows.Borough> boroughs) {
    saveDataset(source, download);
    int previous = jdbc.sql("delete from borough").update();
    int loaded =
        batches(
            boroughs.stream(),
            """
            insert into borough (id, name, kind, geom)
            values (?, ?, ?, st_multi(st_collectionextract(
              st_makevalid(st_setsrid(st_geomfromgeojson(?), 4326)), 3)))
            """,
            b -> new Object[] {b.id(), b.name(), b.kind(), b.geometry()});
    return checked(source, previous, loaded);
  }

  @Transactional
  public int addresses(Source source, Ckan.Download download, Stream<Rows.Address> addresses) {
    saveDataset(source, download);
    int previous = jdbc.sql("delete from address").update();
    int loaded =
        batches(
            addresses,
            """
            insert into address (id, number_from, number_to, street_type, street_link, street_name,
              orientation, street_key, geom)
            values (?, ?, ?, ?, ?, ?, ?, ?, st_setsrid(st_makepoint(?, ?), 4326))
            """,
            a ->
                new Object[] {
                  a.id(),
                  a.numberFrom(),
                  a.numberTo(),
                  a.streetType(),
                  a.streetLink(),
                  a.streetName(),
                  a.orientation(),
                  a.streetKey(),
                  a.longitude(),
                  a.latitude()
                });
    return checked(source, previous, loaded);
  }

  /**
   * Refuses a load that would empty a dataset or shrink it by more than half: that is a source that
   * changed its format, not the city closing half its parks. Throwing rolls the transaction back,
   * so the previous rows stay, and the job fails loudly.
   */
  static int checked(Source source, int previous, int loaded) {
    if (loaded == 0 || loaded < previous / 2) {
      throw new IllegalStateException(
          source.id()
              + ": refusing to replace "
              + previous
              + " rows with "
              + loaded
              + "; check the source file");
    }
    return loaded;
  }

  private void saveDataset(Source source, Ckan.Download download) {
    jdbc.sql(
            """
            insert into dataset (id, title, publisher, source_url, license, fetched_at, checksum)
            values (:id, :title, :publisher, :url, :license, now(), :checksum)
            on conflict (id) do update set title = excluded.title, publisher = excluded.publisher,
              source_url = excluded.source_url, license = excluded.license,
              fetched_at = excluded.fetched_at, checksum = excluded.checksum
            """)
        .param("id", source.id())
        .param("title", source.title())
        .param("publisher", source.publisher())
        .param("url", source.pageUrl())
        .param("license", Source.LICENSE)
        .param("checksum", download.checksum())
        .update();
  }

  /** Inserts in batches, so the 350,000 addresses never sit in memory at once. */
  private <T> int batches(
      Stream<T> rows, String sql, java.util.function.Function<T, Object[]> values) {
    int count = 0;
    Iterator<T> iterator = rows.iterator();
    var batch = new java.util.ArrayList<Object[]>(BATCH);
    while (iterator.hasNext()) {
      batch.add(values.apply(iterator.next()));
      if (batch.size() == BATCH || !iterator.hasNext()) {
        template.batchUpdate(sql, batch);
        count += batch.size();
        batch.clear();
      }
    }
    return count;
  }
}
