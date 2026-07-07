-- Naucena pravila kategorizacije za uvoz bankovnih izvoda

CREATE SEQUENCE rule_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE categoryrule (
    id bigint NOT NULL,
    pattern character varying(255) NOT NULL,
    createdat timestamp(6) with time zone NOT NULL,
    category_id bigint NOT NULL,
    user_id bigint NOT NULL,
    CONSTRAINT categoryrule_pkey PRIMARY KEY (id),
    CONSTRAINT uq_rule_user_pattern UNIQUE (user_id, pattern),
    CONSTRAINT fk_rule_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_rule_user FOREIGN KEY (user_id) REFERENCES users (id)
);
