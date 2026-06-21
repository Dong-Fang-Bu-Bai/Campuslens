ALTER TABLE check_in
  ADD COLUMN search_record_id BIGINT NULL;

ALTER TABLE check_in
  ADD COLUMN publish_image BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE check_in
  ADD CONSTRAINT fk_check_in_search_record
    FOREIGN KEY (search_record_id) REFERENCES search_record(id);

ALTER TABLE check_in
  ADD CONSTRAINT uk_check_in_search_record UNIQUE (search_record_id);
