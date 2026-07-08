-- Potvrda email adrese: status i hash tokena za potvrdu.
-- Postojeći nalozi su nastali prije uvođenja verifikacije pa se smatraju potvrđenim.

ALTER TABLE users ADD COLUMN emailverified boolean NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN emailverificationtoken character varying(64);

UPDATE users SET emailverified = true;

CREATE INDEX idx_users_email_verif_token ON users (emailverificationtoken);
