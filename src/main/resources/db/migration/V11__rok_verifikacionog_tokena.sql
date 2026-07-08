-- Rok važenja verifikacionog tokena (do sada je token vrijedio neograničeno).

ALTER TABLE users ADD COLUMN emailverificationexpires timestamp(6) with time zone;
