-- Tokeni za resetovanje lozinke: kratkotrajni, jednokratni. Cuva se samo hash.

CREATE SEQUENCE password_reset_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE password_reset_tokens (
    id bigint NOT NULL,
    tokenhash character varying(64) NOT NULL,
    expiresat timestamp(6) with time zone NOT NULL,
    createdat timestamp(6) with time zone NOT NULL,
    user_id bigint NOT NULL,
    CONSTRAINT password_reset_tokens_pkey PRIMARY KEY (id),
    CONSTRAINT password_reset_tokens_hash_key UNIQUE (tokenhash),
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens (user_id);
