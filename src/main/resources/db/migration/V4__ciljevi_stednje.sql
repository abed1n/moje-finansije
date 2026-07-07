-- Ciljevi stednje

CREATE SEQUENCE goal_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE savingsgoal (
    id bigint NOT NULL,
    targetamount numeric(19,2) NOT NULL,
    savedamount numeric(19,2) NOT NULL,
    deadline date,
    createdat timestamp(6) with time zone NOT NULL,
    user_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    CONSTRAINT savingsgoal_pkey PRIMARY KEY (id),
    CONSTRAINT fk_goal_user FOREIGN KEY (user_id) REFERENCES users (id)
);
