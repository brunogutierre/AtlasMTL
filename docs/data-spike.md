# Data spike (2026-11-20)

Goal: confirm, before writing code, that every dataset AtlasMTL needs is open, has usable coordinates and can be refreshed automatically.

Each dataset was found through its portal's CKAN API (`package_show`), downloaded and inspected. Counts are from the files as downloaded on 2026-11-20.

## Datasets

| Dataset | Publisher | License | File used | CRS | Updated | Size |
|---|---|---|---|---|---|---|
| [Grands parcs, parcs d'arrondissements et espaces publics](https://donnees.montreal.ca/dataset/grands-parcs-parcs-d-arrondissements-et-espaces-publics) | Ville de Montréal | CC-BY 4.0 | `espace_vert.json` (GeoJSON) | WGS 84 | quarterly | 2,245 polygons |
| [Limites administratives de l'agglomération](https://donnees.montreal.ca/dataset/limites-administratives-agglomeration) | Ville de Montréal | CC-BY 4.0 | GeoJSON (WGS 84 version) | CRS84 | as needed | 34 multipolygons |
| [Adresses ponctuelles](https://donnees.montreal.ca/dataset/adresses-ponctuelles) | Ville de Montréal | CC-BY 4.0 | `adresses.csv` (36 MB) | lat/lon columns | irregular | 345,979 points |
| [Installations du réseau de la santé (M02)](https://www.donneesquebec.ca/recherche/dataset/fichiers-cartographiques-m02-des-installations-et-etablissements) | MSSS | CC-BY 4.0 | `installationscsv.csv` | lat/lon columns | semiannual | 1,590 in Québec, 313 in Montréal |
| [Localisation des établissements d'enseignement](https://www.donneesquebec.ca/recherche/dataset/localisation-des-etablissements-d-enseignement-du-reseau-scolaire-au-quebec) | MEQ / MES | CC-BY 4.0 | `pps_public_ecole.geojson`, `pps_prive_etablissement.geojson` | WGS 84 | weekly | 5,457 public buildings in Québec, 693 in Montréal; 251 private |

All five datasets are CC-BY 4.0, which requires attribution. The app footer and the README will credit each publisher and link each dataset.

## What each dataset gives us

### Parks

- `Polygon` (2,236) and `MultiPolygon` (9) geometries. The file has no `crs` member, so it uses the RFC 7946 default (WGS 84); the coordinates confirm it.
- `Nom` (name), `SUPERFICIE` (area in **hectares**) and `TYPO1` (type):
  - `Parc d'arrondissement`: 1,384
  - `Espace voirie`: 264
  - `En cours de validation`: 270
  - `Autre espace vert`: 184
  - `Grand parc`: 139

  Of these, `Parc d'arrondissement`, `Grand parc` and `Autre espace vert` are parks.
- A large park can be split into several features. Mont-Royal, for example, appears as several pieces. Counts and areas must group by name where that matters.

### Boroughs

- 34 areas: 19 `Arrondissement` (boroughs of the City of Montréal) and 15 `Ville liée` (linked cities such as Westmount).
- Fields: `NOM`, `TYPE`, `ABREV`, `CODEID`.
- `get_borough` and `count_by_borough` cover all 34, with the type shown, because visitors don't know the difference.

### Addresses (geocoder)

- One point per building, with `ADDR_DE`/`ADDR_A` (number range), `GENERIQUE` (`rue`, `avenue`…), `LIEN` (`de`, `du`…), `SPECIFIQUE` (`Sherbrooke`) and `ORIENTATION` (`E`, `O`, `N`, `S`, or `X` for none).
- **137,224 points cover a range of numbers.** "1000 rue Sherbrooke Ouest" (the roadmap's example) has no point of its own. It falls inside the range 914–1002, so the geocoder must match on ranges and the street side (number parity), not on exact numbers.
- It covers the whole agglomeration: in a sample of 6,920 points, 82 % fall in boroughs and 18 % in linked cities.

### Health facilities

- 313 installations with `RSS_CODE = 06` (Montréal health region), all with coordinates.
- Facility type flags (`Oui`/`Non`):
  - `CHSGS` (general hospital mission): 78
  - `CLSC` (local community service centre): 51
  - `CHSLD` (long-term care): 98

  One installation can carry several missions.
- `CHSGS` also marks some installations that aren't hospitals, such as ambulatory care centres. The normalizer (PR 4) maps missions to categories (`hospital`, `clsc`, `long_term_care`) and keeps the raw flags in `props`.

### Schools

- Public schools: 693 buildings with `NOM_REG_ADM = Montréal`.
- Levels come from integer flags `PRIM`/`SEC`: 406 offer primary, 167 secondary, and 38 offer both. `ORDRE_ENS` holds a label, and `TYPE_CS` the language (539 French, 154 English).
- Private schools are a separate file without a region field, so they are filtered spatially with the agglomeration boundary.
- Colleges and universities are separate files. They are left out of v1 and can be added later through the same reader.

## Findings that change the plan

| # | Finding | Consequence |
|---|---|---|
| 1 | donnees.montreal.ca answers `RBAC: access denied` to curl's default User-Agent; any other value works | The ingest sends `User-Agent: atlasmtl-ingest/<version>` |
| 2 | Download URLs embed resource ids that can change when a dataset is republished | The ingest resolves URLs through CKAN `package_show` by dataset name and resource format, not hard-coded links |
| 3 | Every name is French only; MSSS names are upper case | `name_en` stays null for source data. Category labels are translated in the app, and MSSS names are title-cased on ingest |
| 4 | Addresses are often ranges | The geocoder (PR 7) matches number ranges with parity, then falls back to the nearest number on the same street |
| 5 | No population data in scope | The README example "parks per resident" becomes "parks per km²" (borough areas come from the boundaries) |
| 6 | Name check: an unrelated bioinformatics repository on GitHub uses the name "AtlasMTL"; nothing on npm | Kept. The domain is different and the repository description makes the scope clear |

## Not verified yet

- Weekly-run stability of the CKAN API from GitHub Actions runners (checked in PR 5 with the cron workflow).
- Neon free-tier PostGIS limits and Render pricing (checked in PR 20, before deploying).
