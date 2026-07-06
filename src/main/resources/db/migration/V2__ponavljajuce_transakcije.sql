-- Ponavljajuce transakcije: mjesecna pravila koja automatski kreiraju transakcije

CREATE SEQUENCE recurring_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE recurringtransaction (
    id bigint NOT NULL,
    amount numeric(19,2) NOT NULL,
    dayofmonth integer NOT NULL,
    active boolean NOT NULL,
    lastrun character varying(7),
    createdat timestamp(6) with time zone NOT NULL,
    account_id bigint NOT NULL,
    category_id bigint,
    user_id bigint NOT NULL,
    description character varying(255),
    type character varying(255) NOT NULL,
    CONSTRAINT recurringtransaction_pkey PRIMARY KEY (id),
    CONSTRAINT recurringtransaction_type_check CHECK (type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT fk_recurring_account FOREIGN KEY (account_id) REFERENCES account (id),
    CONSTRAINT fk_recurring_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_recurring_user FOREIGN KEY (user_id) REFERENCES users (id)
);
