-- Cilj stednje moze pratiti stanje racuna (napredak automatski, bez rucnih uplata)

ALTER TABLE savingsgoal ADD COLUMN account_id bigint;
ALTER TABLE savingsgoal ADD CONSTRAINT fk_goal_account FOREIGN KEY (account_id) REFERENCES account (id);
