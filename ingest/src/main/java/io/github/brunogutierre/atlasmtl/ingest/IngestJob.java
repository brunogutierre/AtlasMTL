package io.github.brunogutierre.atlasmtl.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Refreshes every dataset whose file changed since the last run. */
@Component
public class IngestJob {

  private static final Logger log = LoggerFactory.getLogger(IngestJob.class);

  /** Boroughs first: the other datasets don't depend on them, but people read the log in order. */
  static final List<Source> SOURCES =
      List.of(
          Source.BOROUGHS,
          Source.PARKS,
          Source.HEALTH,
          Source.PUBLIC_SCHOOLS,
          Source.PRIVATE_SCHOOLS,
          Source.ADDRESSES);

  private final Ckan ckan;
  private final Loader loader;

  public IngestJob(Ckan ckan, Loader loader) {
    this.ckan = ckan;
    this.loader = loader;
  }

  /**
   * Runs the job.
   *
   * @param only dataset ids to refresh; empty means all of them
   * @param force reload even when the file didn't change
   */
  public void run(Set<String> only, boolean force) {
    for (Source source : SOURCES) {
      if (only.isEmpty() || only.contains(source.id())) {
        refresh(source, force);
      }
    }
  }

  void refresh(Source source, boolean force) {
    Ckan.Download download = ckan.download(source);
    try {
      if (!force && loader.checksum(source).filter(download.checksum()::equals).isPresent()) {
        log.info("{}: unchanged, skipped", source.id());
        return;
      }
      int rows = load(source, download);
      log.info("{}: {} rows loaded from {}", source.id(), rows, download.url());
    } finally {
      try {
        Files.deleteIfExists(download.file());
      } catch (IOException e) {
        log.warn("Can't delete {}", download.file(), e);
      }
    }
  }

  private int load(Source source, Ckan.Download download) {
    try (InputStream in = Files.newInputStream(download.file())) {
      if (source.equals(Source.BOROUGHS)) {
        return loader.boroughs(source, download, Boroughs.normalize(in));
      }
      if (source.equals(Source.ADDRESSES)) {
        try (Stream<Rows.Address> rows = Addresses.normalize(in)) {
          return loader.addresses(source, download, rows);
        }
      }
      return loader.features(source, download, normalize(source, in));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static List<Rows.Feature> normalize(Source source, InputStream in) {
    if (source.equals(Source.PARKS)) {
      return Parks.normalize(in);
    }
    if (source.equals(Source.HEALTH)) {
      return Health.normalize(in);
    }
    if (source.equals(Source.PUBLIC_SCHOOLS)) {
      return Schools.publicSchools(in);
    }
    if (source.equals(Source.PRIVATE_SCHOOLS)) {
      return Schools.privateSchools(in);
    }
    throw new IllegalArgumentException("No normalizer for " + source.id());
  }
}
