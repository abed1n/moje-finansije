-- Datum posljednjeg uvoza izvoda po korisniku (za podsjetnik u centru obavjestenja)

ALTER TABLE users ADD COLUMN lastimportat timestamp(6) with time zone;
