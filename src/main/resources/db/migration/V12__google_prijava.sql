-- Google prijava: nalozi mogu biti kreirani preko Google-a (bez lozinke).

ALTER TABLE users ALTER COLUMN passwordhash DROP NOT NULL;
ALTER TABLE users ADD COLUMN provider character varying(20) NOT NULL DEFAULT 'LOCAL';
