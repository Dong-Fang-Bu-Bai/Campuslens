ALTER TABLE search_record ADD COLUMN job_id VARCHAR(36);
ALTER TABLE search_record ADD COLUMN job_token_hash VARCHAR(64);
ALTER TABLE search_record ADD COLUMN idempotency_key VARCHAR(128);
ALTER TABLE search_record ADD COLUMN file_sha256 VARCHAR(64);
ALTER TABLE search_record ADD COLUMN queued_at DATETIME;
ALTER TABLE search_record ADD COLUMN started_at DATETIME;
ALTER TABLE search_record ADD COLUMN finished_at DATETIME;
ALTER TABLE search_record ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE search_record ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;
ALTER TABLE search_record ADD COLUMN error_code VARCHAR(100);
ALTER TABLE search_record ADD COLUMN lease_until DATETIME;
ALTER TABLE search_record ADD COLUMN worker_id VARCHAR(100);

CREATE UNIQUE INDEX uk_search_record_job_id ON search_record (job_id);
CREATE UNIQUE INDEX uk_search_record_idempotency_key ON search_record (idempotency_key);
CREATE INDEX idx_search_record_job_status ON search_record (status, lease_until);
