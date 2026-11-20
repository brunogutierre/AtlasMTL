-- AtlasMTL schema. See docs/adr/0004-one-feature-table-explicit-sql.md.

create extension if not exists postgis;
create extension if not exists pg_trgm;
create extension if not exists unaccent;

-- One row per source dataset, refreshed by the ingest job.
create table dataset (
  id          text primary key,            -- 'parks', 'schools', ...
  title       text        not null,
  publisher   text        not null,
  source_url  text        not null,        -- the dataset page, for attribution
  license     text        not null,
  fetched_at  timestamptz not null,
  checksum    text        not null         -- of the downloaded file; unchanged files are skipped
);

-- The 19 boroughs and 15 linked cities of the Montréal agglomeration.
create table borough (
  id    integer primary key,               -- CODEID from the source
  name  text not null unique,
  kind  text not null check (kind in ('borough', 'linked_city')),
  geom  geometry(MultiPolygon, 4326) not null
);
create index borough_geom_idx on borough using gist (geom);

-- Every place the tools can return, whatever its source.
create table feature (
  id           bigint generated always as identity primary key,
  dataset_id   text  not null references dataset (id),
  source_id    text  not null,             -- the id in the source file
  category     text  not null,             -- park, school, hospital, clsc, long_term_care
  subcategory  text,
  name_fr      text,
  name_en      text,
  props        jsonb not null default '{}',
  geom         geometry(Geometry, 4326) not null,
  unique (dataset_id, source_id)
);
create index feature_category_idx on feature (category);
create index feature_geom_idx on feature using gist (geom);
-- Distances are measured in meters on the geography type.
create index feature_geog_idx on feature using gist ((geom::geography));

-- Address points for the local geocoder. See docs/adr/0006-local-geocoder.md.
create table address (
  id           bigint  primary key,         -- ID_ADRESSE from the source
  number_from  integer not null,
  number_to    integer not null,
  street_type  text    not null,            -- rue, avenue, boulevard...
  street_link  text,                        -- de, du, des...
  street_name  text    not null,            -- Sherbrooke
  orientation  text check (orientation in ('E', 'O', 'N', 'S')),
  street_key   text    not null,            -- lower case, no accents: 'sherbrooke'
  geom         geometry(Point, 4326) not null,
  check (number_from <= number_to)
);
create index address_street_trgm_idx on address using gin (street_key gin_trgm_ops);
create index address_street_number_idx on address (street_key, number_from);
