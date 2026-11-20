package io.github.brunogutierre.atlasmtl.ingest;

/**
 * An open dataset and how to find its file through the portal's CKAN API: the resource URLs change
 * when a dataset is republished, its name doesn't.
 *
 * @param id the {@code dataset.id} it is loaded as
 * @param portal the CKAN portal's base URL
 * @param packageName the dataset's CKAN name
 * @param format the resource format to download, as CKAN labels it
 * @param resourceName text the resource name must contain, to pick one of several files
 */
public record Source(
    String id,
    String title,
    String publisher,
    String portal,
    String packageName,
    String format,
    String resourceName) {

  static final String MONTREAL = "https://donnees.montreal.ca";
  static final String QUEBEC = "https://www.donneesquebec.ca/recherche";

  public static final Source PARKS =
      new Source(
          "parks",
          "Grands parcs, parcs d'arrondissements et espaces publics",
          "Ville de Montréal",
          MONTREAL,
          "grands-parcs-parcs-d-arrondissements-et-espaces-publics",
          "JSON",
          "");

  public static final Source BOROUGHS =
      new Source(
          "boroughs",
          "Limites administratives de l'agglomération de Montréal",
          "Ville de Montréal",
          MONTREAL,
          "limites-administratives-agglomeration",
          "GeoJSON",
          "WGS");

  public static final Source ADDRESSES =
      new Source(
          "addresses",
          "Adresses ponctuelles",
          "Ville de Montréal",
          MONTREAL,
          "adresses-ponctuelles",
          "CSV",
          "");

  /** Every source is published under this license. */
  public static final String LICENSE = "CC-BY-4.0";

  /** The dataset page people can follow from the attribution. */
  public String pageUrl() {
    return portal + "/dataset/" + packageName;
  }
}
