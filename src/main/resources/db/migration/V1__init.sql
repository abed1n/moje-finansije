-- Inicijalna sema baze (odgovara JPA modelu aplikacije)

-- Sekvence
CREATE SEQUENCE account_details_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE account_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE budget_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE category_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE currency_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE location_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE profile_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE tag_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE timezone_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE transaction_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE uploaded_file_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE user_seq START WITH 1 INCREMENT BY 1;

-- Korisnici i profili
CREATE TABLE profile (
    id bigint NOT NULL,
    dateofbirth date,
    address character varying(255),
    phone character varying(255),
    CONSTRAINT profile_pkey PRIMARY KEY (id)
);

CREATE TABLE users (
    id bigint NOT NULL,
    createdat timestamp(6) with time zone NOT NULL,
    profile_id bigint,
    email character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    passwordhash character varying(255) NOT NULL,
    role character varying(255) NOT NULL,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT users_email_key UNIQUE (email),
    CONSTRAINT users_profile_id_key UNIQUE (profile_id),
    CONSTRAINT users_role_check CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT fk_users_profile FOREIGN KEY (profile_id) REFERENCES profile (id)
);

-- Racuni
CREATE TABLE accountdetails (
    id bigint NOT NULL,
    openeddate date,
    accountnumber character varying(255),
    bankname character varying(255),
    CONSTRAINT accountdetails_pkey PRIMARY KEY (id)
);

CREATE TABLE account (
    id bigint NOT NULL,
    balance numeric(19,2) NOT NULL,
    currency character varying(3) NOT NULL,
    account_details_id bigint,
    user_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    CONSTRAINT account_pkey PRIMARY KEY (id),
    CONSTRAINT account_account_details_id_key UNIQUE (account_details_id),
    CONSTRAINT account_type_check CHECK (type IN ('CHECKING', 'SAVINGS', 'CASH', 'CREDIT_CARD')),
    CONSTRAINT fk_account_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_account_details FOREIGN KEY (account_details_id) REFERENCES accountdetails (id)
);

-- Kategorije
CREATE TABLE categories (
    id bigint NOT NULL,
    color character varying(7),
    user_id bigint NOT NULL,
    icon character varying(255),
    name character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    CONSTRAINT categories_pkey PRIMARY KEY (id),
    CONSTRAINT categories_type_check CHECK (type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT fk_categories_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Transakcije
CREATE TABLE transactions (
    id bigint NOT NULL,
    amount numeric(19,2) NOT NULL,
    date date NOT NULL,
    account_id bigint NOT NULL,
    category_id bigint,
    createdat timestamp(6) with time zone NOT NULL,
    description character varying(255),
    type character varying(255) NOT NULL,
    CONSTRAINT transactions_pkey PRIMARY KEY (id),
    CONSTRAINT transactions_type_check CHECK (type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT fk_transactions_account FOREIGN KEY (account_id) REFERENCES account (id),
    CONSTRAINT fk_transactions_category FOREIGN KEY (category_id) REFERENCES categories (id)
);

-- Tagovi
CREATE TABLE tag (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    CONSTRAINT tag_pkey PRIMARY KEY (id),
    CONSTRAINT fk_tag_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE transaction_tags (
    tag_id bigint NOT NULL,
    transaction_id bigint NOT NULL,
    CONSTRAINT fk_transaction_tags_tag FOREIGN KEY (tag_id) REFERENCES tag (id),
    CONSTRAINT fk_transaction_tags_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id)
);

-- Prilozi transakcija
CREATE TABLE uploadedfile (
    id bigint NOT NULL,
    size bigint NOT NULL,
    transaction_id bigint NOT NULL,
    uploadedat timestamp(6) with time zone NOT NULL,
    contenttype character varying(255),
    filename character varying(255) NOT NULL,
    storedpath character varying(255) NOT NULL,
    CONSTRAINT uploadedfile_pkey PRIMARY KEY (id),
    CONSTRAINT fk_uploadedfile_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id)
);

-- Budzeti
CREATE TABLE budget (
    id bigint NOT NULL,
    limitamount numeric(19,2) NOT NULL,
    user_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    period character varying(255) NOT NULL,
    CONSTRAINT budget_pkey PRIMARY KEY (id),
    CONSTRAINT budget_period_check CHECK (period IN ('MONTHLY', 'YEARLY')),
    CONSTRAINT fk_budget_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE budget_categories (
    budget_id bigint NOT NULL,
    category_id bigint NOT NULL,
    CONSTRAINT fk_budget_categories_budget FOREIGN KEY (budget_id) REFERENCES budget (id),
    CONSTRAINT fk_budget_categories_category FOREIGN KEY (category_id) REFERENCES categories (id)
);

-- Istorija vanjskih API poziva
CREATE TABLE currencyresponse (
    id bigint NOT NULL,
    converted_value double precision,
    input_value double precision,
    rate double precision NOT NULL,
    user_id bigint,
    date character varying(255),
    from_currency character varying(255),
    source character varying(255),
    to_currency character varying(255),
    CONSTRAINT currencyresponse_pkey PRIMARY KEY (id),
    CONSTRAINT fk_currencyresponse_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE locationresponse (
    id bigint NOT NULL,
    country_area double precision NOT NULL,
    in_eu boolean NOT NULL,
    latitude double precision NOT NULL,
    longitude double precision NOT NULL,
    country_population bigint NOT NULL,
    user_id bigint,
    asn character varying(255),
    city character varying(255),
    continent_code character varying(255),
    country_calling_code character varying(255),
    country_capital character varying(255),
    country_code character varying(255),
    country_code_iso3 character varying(255),
    country_name character varying(255),
    country_tld character varying(255),
    currency character varying(255),
    currency_name character varying(255),
    ip character varying(255),
    languages character varying(255),
    org character varying(255),
    postal character varying(255),
    region character varying(255),
    region_code character varying(255),
    timezone character varying(255),
    utc_offset character varying(255),
    version character varying(255),
    CONSTRAINT locationresponse_pkey PRIMARY KEY (id),
    CONSTRAINT fk_locationresponse_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE timezoneresponse (
    id bigint NOT NULL,
    tz_day integer,
    tz_dst_active boolean,
    tz_hour integer,
    tz_milliseconds integer,
    tz_minute integer,
    tz_month integer,
    tz_seconds integer,
    tz_year integer,
    user_id bigint,
    tz_date character varying(255),
    tz_date_time character varying(255),
    tz_day_of_week character varying(255),
    tz_time character varying(255),
    tz_time_zone character varying(255),
    CONSTRAINT timezoneresponse_pkey PRIMARY KEY (id),
    CONSTRAINT fk_timezoneresponse_user FOREIGN KEY (user_id) REFERENCES users (id)
);
