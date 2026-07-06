-- Prebacivanje novca izmedju vlastitih racuna

CREATE SEQUENCE transfer_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE transfer (
    id bigint NOT NULL,
    amount numeric(19,2) NOT NULL,
    date date NOT NULL,
    createdat timestamp(6) with time zone NOT NULL,
    from_account_id bigint NOT NULL,
    to_account_id bigint NOT NULL,
    user_id bigint NOT NULL,
    description character varying(255),
    CONSTRAINT transfer_pkey PRIMARY KEY (id),
    CONSTRAINT fk_transfer_from FOREIGN KEY (from_account_id) REFERENCES account (id),
    CONSTRAINT fk_transfer_to FOREIGN KEY (to_account_id) REFERENCES account (id),
    CONSTRAINT fk_transfer_user FOREIGN KEY (user_id) REFERENCES users (id)
);
