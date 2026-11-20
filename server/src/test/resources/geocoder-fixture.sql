-- Address points shaped like the city's file (see GeocoderTest).
insert into address (id, number_from, number_to, street_type, street_link, street_name,
  orientation, street_key, geom) values
  (1, 914, 1002, 'rue', null, 'Sherbrooke', 'O', 'sherbrooke',
   st_setsrid(st_makepoint(-73.575328, 45.502714), 4326)),
  (2, 1000, 1000, 'rue', null, 'Sherbrooke', 'E', 'sherbrooke',
   st_setsrid(st_makepoint(-73.565422, 45.520755), 4326)),
  (3, 3869, 3869, 'chemin', 'de la', 'Côte-des-Neiges', null, 'cote des neiges',
   st_setsrid(st_makepoint(-73.590517, 45.495202), 4326)),
  (4, 5664, 5664, 'avenue', null, '1re', null, '1re',
   st_setsrid(st_makepoint(-73.580784, 45.546389), 4326));

-- Same name, different streets: the street type and the official "De" tell them apart.
insert into address (id, number_from, number_to, street_type, street_link, street_name,
  orientation, street_key, geom) values
  (5, 115, 115, 'rue', null, 'Maisonneuve', null, 'maisonneuve',
   st_setsrid(st_makepoint(-73.80, 45.49), 4326)),
  (6, 1455, 1455, 'boulevard', null, 'De Maisonneuve', 'O', 'de maisonneuve',
   st_setsrid(st_makepoint(-73.578, 45.497), 4326));
