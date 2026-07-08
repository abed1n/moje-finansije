-- Refresh tokeni: dugotrajni tokeni za obnavljanje kratkotrajnog pristupnog tokena.
-- Cuva se samo hash vrijednosti; token se rotira pri svakom osvjezavanju.

CREATE SEQUENCE refresh_token_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE refresh_tokens (
    id bigint NOT NULL,
    tokenhash character varying(64) NOT NULL,
    expiresat timestamp(6) with time zone NOT NULL,
    createdat timestamp(6) with time zone NOT NULL,
    user_id bigint NOT NULL,
    CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id),
    CONSTRAINT refresh_tokens_hash_key UNIQUE (tokenhash),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
