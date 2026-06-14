ALTER TABLE search_record ADD COLUMN next_attempt_at DATETIME;
CREATE INDEX idx_search_record_queue_due ON search_record (status, next_attempt_at);
