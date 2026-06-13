ALTER TABLE search_record ADD COLUMN sar_mode BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE search_record ADD COLUMN sar_applied BOOLEAN;
ALTER TABLE search_record ADD COLUMN trust_level VARCHAR(32);
ALTER TABLE search_record ADD COLUMN base_model_version VARCHAR(160);
ALTER TABLE search_record ADD COLUMN index_version VARCHAR(160);
ALTER TABLE search_record ADD COLUMN sar_state_version VARCHAR(160);

ALTER TABLE correction_sample ADD COLUMN dataset_path VARCHAR(700);
ALTER TABLE correction_sample ADD COLUMN published_index_version VARCHAR(160);

CREATE TABLE IF NOT EXISTS index_rebuild_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rebuild_job_id VARCHAR(36) NOT NULL UNIQUE,
  status VARCHAR(50) NOT NULL,
  index_version VARCHAR(160),
  error_message VARCHAR(1000),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
