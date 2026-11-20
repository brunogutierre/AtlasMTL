-- Two square boroughs and a few places at known distances (see GeoQueriesTest).
insert into dataset (id, title, publisher, source_url, license, fetched_at, checksum)
values ('test', 'Test', 'Tests', 'https://example.org', 'CC-BY-4.0', now(), 'x');

insert into borough (id, name, kind, geom) values
  (1, 'Le Plateau-Mont-Royal', 'borough',
   st_multi(st_makeenvelope(-73.60, 45.51, -73.57, 45.53, 4326))),
  (2, 'Westmount', 'linked_city',
   st_multi(st_makeenvelope(-73.62, 45.47, -73.60, 45.49, 4326)));

insert into feature (dataset_id, source_id, category, subcategory, name_fr, props, geom) values
  ('test', 's1', 'school', 'primary', 'École Alpha', '{"language": "fr"}',
   st_setsrid(st_makepoint(-73.585, 45.520), 4326)),
  ('test', 's2', 'school', 'secondary', 'École Bêta', '{}',
   st_setsrid(st_makepoint(-73.575, 45.525), 4326)),
  ('test', 's3', 'school', 'primary', 'Westmount High', '{}',
   st_setsrid(st_makepoint(-73.61, 45.48), 4326)),
  ('test', 'h1', 'hospital', null, 'Hôpital Test', '{}',
   st_setsrid(st_makepoint(-73.585, 45.525), 4326)),
  ('test', 'p1', 'park', 'borough_park', 'Parc Carré', '{"area_ha": 1.2}',
   st_makeenvelope(-73.590, 45.511, -73.588, 45.513, 4326));
