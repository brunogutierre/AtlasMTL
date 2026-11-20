package io.github.brunogutierre.atlasmtl.api;

import io.github.brunogutierre.atlasmtl.geo.BoroughShape;
import io.github.brunogutierre.atlasmtl.geo.GeoQueries;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The borough outlines the map draws under every answer. */
@RestController
@RequestMapping("/api")
public class BoroughController {

  private final GeoQueries geo;

  public BoroughController(GeoQueries geo) {
    this.geo = geo;
  }

  /** Cached for a day by browsers: the outlines change only with the weekly ingest, if ever. */
  @GetMapping("/boroughs")
  public ResponseEntity<List<BoroughShape>> boroughs() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
        .body(geo.boroughShapes());
  }
}
